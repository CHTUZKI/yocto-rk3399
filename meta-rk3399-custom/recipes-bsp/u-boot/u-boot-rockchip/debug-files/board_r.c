// SPDX-License-Identifier: GPL-2.0+
/*
 * Copyright (c) 2011 The Chromium OS Authors.
 * (C) Copyright 2002-2006
 * Wolfgang Denk, DENX Software Engineering, wd@denx.de.
 *
 * (C) Copyright 2002
 * Sysgo Real-Time Solutions, GmbH <www.elinos.com>
 * Marius Groeger <mgroeger@sysgo.de>
 */

#include <config.h>
#include <api.h>
#include <bootstage.h>
#include <cpu_func.h>
#include <cyclic.h>
#include <display_options.h>
#include <exports.h>
#ifdef CONFIG_MTD_NOR_FLASH
#include <flash.h>
#endif
#include <hang.h>
#include <image.h>
#include <irq_func.h>
#include <log.h>
#include <net.h>
#include <asm/cache.h>
#include <asm/global_data.h>
#include <u-boot/crc.h>
#include <binman.h>
#include <command.h>
#include <console.h>
#include <dm.h>
#include <env.h>
#include <env_internal.h>
#include <fdtdec.h>
#include <ide.h>
#include <init.h>
#include <initcall.h>
#include <kgdb.h>
#include <irq_func.h>
#include <malloc.h>
#include <mapmem.h>
#include <miiphy.h>
#include <mmc.h>
#include <mux.h>
#include <nand.h>
#include <of_live.h>
#include <onenand_uboot.h>
#include <pvblock.h>
#include <scsi.h>
#include <serial.h>
#include <status_led.h>
#include <stdio_dev.h>
#include <timer.h>
#include <trace.h>
#include <watchdog.h>
#include <xen.h>
#include <asm/sections.h>
#include <dm/root.h>
#include <dm/ofnode.h>
#include <linux/compiler.h>
#include <linux/err.h>
#include <efi_loader.h>
#include <wdt.h>
#include <asm-generic/gpio.h>
#include <efi_loader.h>
#include <relocate.h>

/* DEBUG: Direct serial output for early debugging */
#define DEBUG_SERIAL 1

DECLARE_GLOBAL_DATA_PTR;

ulong monitor_flash_len;

/* Forward declarations for debug functions */
static void debug_serial_puts(const char *s);
static void debug_serial_put_ulong(unsigned long val);

__weak int board_flash_wp_on(void)
{
	/*
	 * Most flashes can't be detected when write protection is enabled,
	 * so provide a way to let U-Boot gracefully ignore write protected
	 * devices.
	 */
	return 0;
}

__weak int cpu_secondary_init_r(void)
{
	return 0;
}

static int initr_trace(void)
{
#ifdef CONFIG_TRACE
	trace_init(gd->trace_buff, CONFIG_TRACE_BUFFER_SIZE);
#endif

	return 0;
}

static int initr_reloc(void)
{
	/* tell others: relocation done */
	gd->flags |= GD_FLG_RELOC | GD_FLG_FULL_MALLOC_INIT;

	return 0;
}

#if defined(CONFIG_ARM) || defined(CONFIG_RISCV)
/*
 * Some of these functions are needed purely because the functions they
 * call return void. If we change them to return 0, these stubs can go away.
 */
static int initr_caches(void)
{
	/* Enable caches */
	enable_caches();
	return 0;
}
#endif

__weak int fixup_cpu(void)
{
	return 0;
}

static int initr_reloc_global_data(void)
{
#ifdef __ARM__
	monitor_flash_len = _end - __image_copy_start;
#elif defined(CONFIG_RISCV)
	monitor_flash_len = (ulong)_end - (ulong)_start;
#elif !defined(CONFIG_SANDBOX) && !defined(CONFIG_NIOS2)
	monitor_flash_len = (ulong)__init_end - gd->relocaddr;
#endif
#if defined(CONFIG_MPC85xx) || defined(CONFIG_MPC86xx)
	/*
	 * The gd->cpu pointer is set to an address in flash before relocation.
	 * We need to update it to point to the same CPU entry in RAM.
	 * TODO: why not just add gd->reloc_ofs?
	 */
	gd->arch.cpu += gd->relocaddr - CONFIG_SYS_MONITOR_BASE;

	/*
	 * If we didn't know the cpu mask & # cores, we can save them of
	 * now rather than 'computing' them constantly
	 */
	fixup_cpu();
#endif
#ifdef CONFIG_SYS_RELOC_GD_ENV_ADDR
	/*
	 * Relocate the early env_addr pointer unless we know it is not inside
	 * the binary. Some systems need this and for the rest, it doesn't hurt.
	 */
	gd->env_addr += gd->reloc_off;
#endif
#ifdef CONFIG_EFI_LOADER
	/*
	 * On the ARM architecture gd is mapped to a fixed register (r9 or x18).
	 * As this register may be overwritten by an EFI payload we save it here
	 * and restore it on every callback entered.
	 */
	efi_save_gd();

	efi_runtime_relocate(gd->relocaddr, NULL);
#endif

	return 0;
}

__weak int arch_initr_trap(void)
{
	return 0;
}

#if defined(CONFIG_SYS_INIT_RAM_LOCK) && defined(CONFIG_E500)
static int initr_unlock_ram_in_cache(void)
{
	unlock_ram_in_cache();	/* it's time to unlock D-cache in e500 */
	return 0;
}
#endif

static int initr_barrier(void)
{
#ifdef CONFIG_PPC
	/* TODO: Can we not use dmb() macros for this? */
	asm("sync ; isync");
#endif
	return 0;
}

static int initr_malloc(void)
{
	ulong start;

#if CONFIG_IS_ENABLED(SYS_MALLOC_F)
	debug("Pre-reloc malloc() used %#lx bytes (%ld KB)\n", gd->malloc_ptr,
	      gd->malloc_ptr / 1024);
#endif
	/* The malloc area is immediately below the monitor copy in DRAM */
	/*
	 * This value MUST match the value of gd->start_addr_sp in board_f.c:
	 * reserve_noncached().
	 */
	start = gd->relocaddr - TOTAL_MALLOC_LEN;
	gd_set_malloc_start(start);
	mem_malloc_init((ulong)map_sysmem(start, TOTAL_MALLOC_LEN),
			TOTAL_MALLOC_LEN);
	return 0;
}

static int initr_of_live(void)
{
	if (CONFIG_IS_ENABLED(OF_LIVE)) {
		int ret;

		bootstage_start(BOOTSTAGE_ID_ACCUM_OF_LIVE, "of_live");
		ret = of_live_build(gd->fdt_blob,
				    (struct device_node **)gd_of_root_ptr());
		bootstage_accum(BOOTSTAGE_ID_ACCUM_OF_LIVE);
		if (ret)
			return ret;
	}

	return 0;
}

#ifdef CONFIG_DM
static int initr_dm(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM] ===== START =====\r\n");
	debug_serial_puts("[INITR_DM] Line: int ret;\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	int ret;

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM] Line: oftree_reset();\r\n");
#endif
	oftree_reset();

	/* Drop the pre-reloc driver model and start a new one */
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM] Line: gd->dm_root = NULL;\r\n");
#endif
	gd->dm_root = NULL;
#ifdef CONFIG_TIMER
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM] Line: gd->timer = NULL;\r\n");
#endif
	gd->timer = NULL;
#endif
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM] Line: bootstage_start(...);\r\n");
#endif
	bootstage_start(BOOTSTAGE_ID_ACCUM_DM_R, "dm_r");
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM] ===== BEFORE dm_init_and_scan() =====\r\n");
	debug_serial_puts("[INITR_DM] About to initialize device tree (PMIC will be probed here)\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] Line: ret = dm_init_and_scan(false);\r\n");
#endif
	ret = dm_init_and_scan(false);
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM] ===== RETURNED FROM dm_init_and_scan() =====\r\n");
	debug_serial_puts("[INITR_DM] dm_init_and_scan() returned: ");
	debug_serial_put_ulong(ret);
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] CRITICAL: Checking baudrate IMMEDIATELY after dm_init_and_scan()\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown (gd is NULL)");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] Serial device state: ");
	if (gd && gd->cur_serial_dev) {
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
	} else {
		debug_serial_puts("NULL device");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] ===== END RETURNED FROM dm_init_and_scan() =====\r\n");
	debug_serial_puts("[INITR_DM] Line: bootstage_accum(...);\r\n");
	debug_serial_puts("[INITR_DM] CRITICAL: About to call bootstage_accum()\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate BEFORE bootstage_accum: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	bootstage_accum(BOOTSTAGE_ID_ACCUM_DM_R);
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM] CRITICAL: Returned from bootstage_accum()\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate AFTER bootstage_accum: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	if (gd && gd->baudrate != 1500000) {
		debug_serial_puts("[INITR_DM] ERROR: Baudrate changed in bootstage_accum()!\r\n");
		debug_serial_puts("[INITR_DM] Expected 1500000, got ");
		debug_serial_put_ulong(gd->baudrate);
		debug_serial_puts("\r\n");
	}
	debug_serial_puts("[INITR_DM] Serial device state after bootstage_accum: ");
	if (gd && gd->cur_serial_dev) {
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
	} else {
		debug_serial_puts("NULL device");
	}
	debug_serial_puts("\r\n");
#endif
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM] ===== AFTER dm_init_and_scan() =====\r\n");
	debug_serial_puts("[INITR_DM] CRITICAL: Device tree initialization completed (PMIC probed)\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[INITR_DM] Serial device after dm_init_and_scan: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[INITR_DM] WARNING: Serial device is NULL after dm_init_and_scan!\r\n");
	}
	debug_serial_puts("[INITR_DM] ===== END AFTER dm_init_and_scan() =====\r\n");
	debug_serial_puts("[INITR_DM] Line: if (ret) return ret;\r\n");
	debug_serial_puts("[INITR_DM] CRITICAL: Checking ret value before return check\r\n");
	debug_serial_puts("[INITR_DM] ret = ");
	debug_serial_put_ulong(ret);
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	if (ret) {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM] ERROR: ret != 0, returning error\r\n");
		debug_serial_puts("[INITR_DM] Current baudrate before error return: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
#endif
		return ret;
	}

#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM] ===== BEFORE RETURN 0 =====\r\n");
	debug_serial_puts("[INITR_DM] CRITICAL: About to return 0 from initr_dm()\r\n");
	debug_serial_puts("[INITR_DM] This is the LAST point before function returns!\r\n");
	debug_serial_puts("[INITR_DM] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
		if (gd->baudrate != 1500000) {
			debug_serial_puts("\r\n[INITR_DM] ERROR: Baudrate is NOT 1500000! This will cause garbled output!\r\n");
			debug_serial_puts("[INITR_DM] Expected 1500000, got ");
			debug_serial_put_ulong(gd->baudrate);
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts(" (OK)\r\n");
		}
	} else {
		debug_serial_puts("unknown (gd is NULL)\r\n");
	}
	debug_serial_puts("[INITR_DM] Serial device state: ");
	if (gd && gd->cur_serial_dev) {
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
	} else {
		debug_serial_puts("NULL device");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM] Line: return 0;\r\n");
	debug_serial_puts("[INITR_DM] ===== END =====\r\n");
	debug_serial_puts("[INITR_DM] ===== ACTUALLY RETURNING NOW =====\r\n");
#endif
	return 0;
}
#endif

static int initr_dm_devices(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_DM_DEVICES] ===== START =====\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] CRITICAL: initr_dm_devices() called\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] Line: int ret;\r\n");
#endif
	int ret;

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM_DEVICES] Line: if (IS_ENABLED(CONFIG_TIMER_EARLY))\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] Checking CONFIG_TIMER_EARLY...\r\n");
#endif
	if (IS_ENABLED(CONFIG_TIMER_EARLY)) {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] CONFIG_TIMER_EARLY is enabled\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Line: ret = dm_timer_init();\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] About to call dm_timer_init()\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Current baudrate before dm_timer_init: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
#endif
		ret = dm_timer_init();
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] dm_timer_init() returned: ");
		debug_serial_put_ulong(ret);
		debug_serial_puts("\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Current baudrate after dm_timer_init: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Line: if (ret) return ret;\r\n");
#endif
		if (ret) {
#if DEBUG_SERIAL
			debug_serial_puts("[INITR_DM_DEVICES] ERROR: dm_timer_init() failed, returning\r\n");
#endif
			return ret;
		}
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] dm_timer_init() succeeded, continuing...\r\n");
#endif
	} else {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] CONFIG_TIMER_EARLY is NOT enabled, skipping\r\n");
#endif
	}

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM_DEVICES] Line: if (IS_ENABLED(CONFIG_MULTIPLEXER))\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] Checking CONFIG_MULTIPLEXER...\r\n");
#endif
	if (IS_ENABLED(CONFIG_MULTIPLEXER)) {
		/*
		 * Initialize the multiplexer controls to their default state.
		 * This must be done early as other drivers may unknowingly
		 * rely on it.
		 */
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] CONFIG_MULTIPLEXER is enabled\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Line: ret = dm_mux_init();\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] About to call dm_mux_init()\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Current baudrate before dm_mux_init: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
#endif
		ret = dm_mux_init();
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] dm_mux_init() returned: ");
		debug_serial_put_ulong(ret);
		debug_serial_puts("\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Current baudrate after dm_mux_init: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
		debug_serial_puts("[INITR_DM_DEVICES] Line: if (ret) return ret;\r\n");
#endif
		if (ret) {
#if DEBUG_SERIAL
			debug_serial_puts("[INITR_DM_DEVICES] ERROR: dm_mux_init() failed, returning\r\n");
#endif
			return ret;
		}
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] dm_mux_init() succeeded, continuing...\r\n");
#endif
	} else {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_DM_DEVICES] CONFIG_MULTIPLEXER is NOT enabled, skipping\r\n");
#endif
	}

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_DM_DEVICES] Line: return 0;\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] Current baudrate before return: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_DM_DEVICES] ===== END =====\r\n");
#endif
	return 0;
}

static int initr_bootstage(void)
{
	bootstage_mark_name(BOOTSTAGE_ID_START_UBOOT_R, "board_init_r");

	return 0;
}

__weak int power_init_board(void)
{
	return 0;
}

static int debug_before_power_init_board(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE power_init_board() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call power_init_board()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device before power_init: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL before power_init!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END BEFORE power_init_board() =====\r\n");
	return 0;
}

static int debug_after_power_init_board(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER power_init_board() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: Power initialization completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after power_init: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after power_init!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END AFTER power_init_board() =====\r\n");
	return 0;
}

static int initr_announce(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_ANNOUNCE] ===== START =====\r\n");
	debug_serial_puts("[INITR_ANNOUNCE] Line: debug(\"Now running in RAM...\");\r\n");
	debug_serial_puts("[INITR_ANNOUNCE] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	debug("Now running in RAM - U-Boot at: %08lx\n", gd->relocaddr);
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_ANNOUNCE] Line: return 0;\r\n");
	debug_serial_puts("[INITR_ANNOUNCE] ===== END =====\r\n");
#endif
	return 0;
}

static int initr_binman(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[INITR_BINMAN] ===== START =====\r\n");
	debug_serial_puts("[INITR_BINMAN] Line: int ret;\r\n");
	debug_serial_puts("[INITR_BINMAN] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	int ret;

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_BINMAN] Line: if (!CONFIG_IS_ENABLED(BINMAN_FDT))\r\n");
#endif
	if (!CONFIG_IS_ENABLED(BINMAN_FDT)) {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_BINMAN] BINMAN_FDT not enabled, returning 0\r\n");
		debug_serial_puts("[INITR_BINMAN] ===== END (early return) =====\r\n");
#endif
		return 0;
	}

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_BINMAN] Line: ret = binman_init();\r\n");
	debug_serial_puts("[INITR_BINMAN] ===== CRITICAL: About to call binman_init() =====\r\n");
	debug_serial_puts("[INITR_BINMAN] This function may output data!\r\n");
	debug_serial_puts("[INITR_BINMAN] Current baudrate before binman_init: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[INITR_BINMAN] Serial device before binman_init: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[INITR_BINMAN] WARNING: Serial device is NULL before binman_init!\r\n");
	}
	
	debug_serial_puts("[INITR_BINMAN] ===== CALLING binman_init() NOW =====\r\n");
#endif
	ret = binman_init();
#if DEBUG_SERIAL
	debug_serial_puts("[INITR_BINMAN] ===== RETURNED FROM binman_init() =====\r\n");
	debug_serial_puts("[INITR_BINMAN] binman_init() returned: ");
	debug_serial_put_ulong(ret);
	debug_serial_puts("\r\n");
	debug_serial_puts("[INITR_BINMAN] Current baudrate after binman_init: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[INITR_BINMAN] Serial device after binman_init: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[INITR_BINMAN] WARNING: Serial device is NULL after binman_init!\r\n");
	}
	
	debug_serial_puts("[INITR_BINMAN] Line: if (ret) printf(...);\r\n");
#endif
	if (ret) {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_BINMAN] ===== ERROR: binman_init failed! =====\r\n");
		debug_serial_puts("[INITR_BINMAN] WARNING: About to call printf()\r\n");
		debug_serial_puts("[INITR_BINMAN] CRITICAL: printf() will use standard serial output!\r\n");
		debug_serial_puts("[INITR_BINMAN] CRITICAL: This may cause garbled output if baudrate is wrong!\r\n");
		debug_serial_puts("[INITR_BINMAN] Current baudrate before printf: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
		
		/* Check serial device state */
		if (gd && gd->cur_serial_dev) {
			debug_serial_puts("[INITR_BINMAN] Serial device before printf: ");
			if (gd->cur_serial_dev->name) {
				debug_serial_puts(gd->cur_serial_dev->name);
			} else {
				debug_serial_puts("NULL name");
			}
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts("[INITR_BINMAN] WARNING: Serial device is NULL before printf!\r\n");
		}
		
		debug_serial_puts("[INITR_BINMAN] ===== CALLING printf() NOW =====\r\n");
#endif
		printf("binman_init failed:%d\n", ret);
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_BINMAN] ===== RETURNED FROM printf() =====\r\n");
		debug_serial_puts("[INITR_BINMAN] printf() completed\r\n");
		debug_serial_puts("[INITR_BINMAN] Current baudrate after printf: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
		
		/* Check serial device state */
		if (gd && gd->cur_serial_dev) {
			debug_serial_puts("[INITR_BINMAN] Serial device after printf: ");
			if (gd->cur_serial_dev->name) {
				debug_serial_puts(gd->cur_serial_dev->name);
			} else {
				debug_serial_puts("NULL name");
			}
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts("[INITR_BINMAN] WARNING: Serial device is NULL after printf!\r\n");
		}
#endif
	} else {
#if DEBUG_SERIAL
		debug_serial_puts("[INITR_BINMAN] binman_init() succeeded (ret=0)\r\n");
#endif
	}

#if DEBUG_SERIAL
	debug_serial_puts("[INITR_BINMAN] Line: return ret;\r\n");
	debug_serial_puts("[INITR_BINMAN] ===== END =====\r\n");
#endif
	return ret;
}

static int debug_before_initr_binman(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE initr_binman() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call initr_binman()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE initr_binman() =====\r\n");
#endif
	return 0;
}

static int debug_after_initr_binman(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_binman() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER initr_binman() =====\r\n");
#endif
	return 0;
}

#if defined(CONFIG_MTD_NOR_FLASH)
__weak int is_flash_available(void)
{
	return 1;
}

static int initr_flash(void)
{
	ulong flash_size = 0;
	struct bd_info *bd = gd->bd;

	if (!is_flash_available())
		return 0;

	puts("Flash: ");

	if (board_flash_wp_on())
		printf("Uninitialized - Write Protect On\n");
	else
		flash_size = flash_init();

	print_size(flash_size, "");
#ifdef CONFIG_SYS_FLASH_CHECKSUM
	/*
	 * Compute and print flash CRC if flashchecksum is set to 'y'
	 *
	 * NOTE: Maybe we should add some schedule()? XXX
	 */
	if (env_get_yesno("flashchecksum") == 1) {
		const uchar *flash_base = (const uchar *)CFG_SYS_FLASH_BASE;

		printf("  CRC: %08X", crc32(0,
					    flash_base,
					    flash_size));
	}
#endif /* CONFIG_SYS_FLASH_CHECKSUM */
	putc('\n');

	/* update start of FLASH memory    */
#ifdef CFG_SYS_FLASH_BASE
	bd->bi_flashstart = CFG_SYS_FLASH_BASE;
#endif
	/* size of FLASH memory (final value) */
	bd->bi_flashsize = flash_size;

#if defined(CONFIG_SYS_UPDATE_FLASH_SIZE)
	/* Make a update of the Memctrl. */
	update_flash_size(flash_size);
#endif

#if defined(CONFIG_OXC) || defined(CONFIG_RMU)
	/* flash mapped at end of memory map */
	bd->bi_flashoffset = CONFIG_TEXT_BASE + flash_size;
#elif CONFIG_SYS_MONITOR_BASE == CFG_SYS_FLASH_BASE
	bd->bi_flashoffset = monitor_flash_len;	/* reserved area for monitor */
#endif
	return 0;
}
#endif

#ifdef CONFIG_CMD_NAND
/* go init the NAND */
static int initr_nand(void)
{
	puts("NAND:  ");
	nand_init();
	printf("%lu MiB\n", nand_size() / 1024);
	return 0;
}
#endif

#if defined(CONFIG_CMD_ONENAND)
/* go init the NAND */
static int initr_onenand(void)
{
	puts("NAND:  ");
	onenand_init();
	return 0;
}
#endif

#ifdef CONFIG_MMC
static int debug_before_initr_mmc(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE initr_mmc() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to initialize MMC\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device before MMC: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL before MMC!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END BEFORE initr_mmc() =====\r\n");
	return 0;
}

static int initr_mmc(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [MMC] initr_mmc() START\r\n");
	debug_serial_puts("[BOARD_R] [MMC] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] [MMC] About to call puts(\"MMC:   \")\r\n");
#endif
	puts("MMC:   ");
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [MMC] puts() completed\r\n");
	debug_serial_puts("[BOARD_R] [MMC] About to call mmc_initialize()\r\n");
	debug_serial_puts("[BOARD_R] [MMC] Current baudrate before mmc_initialize: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	mmc_initialize(gd->bd);
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [MMC] mmc_initialize() completed\r\n");
	debug_serial_puts("[BOARD_R] [MMC] Current baudrate after mmc_initialize: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state after MMC init */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] [MMC] Serial device after MMC: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] [MMC] WARNING: Serial device is NULL after MMC!\r\n");
	}
	debug_serial_puts("[BOARD_R] [MMC] initr_mmc() END\r\n");
#endif
	return 0;
}

static int debug_after_initr_mmc(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_mmc() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after MMC: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after MMC!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END AFTER initr_mmc() =====\r\n");
	return 0;
}
#endif

#ifdef CONFIG_PVBLOCK
static int debug_before_initr_pvblock(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE initr_pvblock() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	return 0;
}

static int initr_pvblock(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [PVBLOCK] initr_pvblock() START\r\n");
	debug_serial_puts("[BOARD_R] [PVBLOCK] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	puts("PVBLOCK: ");
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [PVBLOCK] About to call pvblock_init()\r\n");
#endif
	pvblock_init();
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [PVBLOCK] pvblock_init() completed\r\n");
	debug_serial_puts("[BOARD_R] [PVBLOCK] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] [PVBLOCK] initr_pvblock() END\r\n");
#endif
	return 0;
}

static int debug_after_initr_pvblock(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_pvblock() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	return 0;
}
#endif

/*
 * Tell if it's OK to load the environment early in boot.
 *
 * If CONFIG_OF_CONTROL is defined, we'll check with the FDT to see
 * if this is OK (defaulting to saying it's OK).
 *
 * NOTE: Loading the environment early can be a bad idea if security is
 *       important, since no verification is done on the environment.
 *
 * Return: 0 if environment should not be loaded, !=0 if it is ok to load
 */
static int should_load_env(void)
{
	if (IS_ENABLED(CONFIG_OF_CONTROL))
		return ofnode_conf_read_int("load-environment", 1);

	if (IS_ENABLED(CONFIG_DELAY_ENVIRONMENT))
		return 0;

	return 1;
}

static int initr_env(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] [ENV] ===== initr_env() START =====\r\n");
	debug_serial_puts("[BOARD_R] [ENV] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state before env init */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] [ENV] Serial device before env: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] [ENV] WARNING: Serial device is NULL before env!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] [ENV] About to check should_load_env()\r\n");
#endif
	
	/* initialize environment */
	if (should_load_env()) {
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] should_load_env() returned true\r\n");
		debug_serial_puts("[BOARD_R] [ENV] About to call env_relocate()\r\n");
		debug_serial_puts("[BOARD_R] [ENV] Current baudrate before env_relocate: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
#endif
		env_relocate();
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] env_relocate() completed\r\n");
		debug_serial_puts("[BOARD_R] [ENV] Current baudrate after env_relocate: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
		
		/* Check if baudrate changed */
		const char *baudrate_env = env_get("baudrate");
		if (baudrate_env) {
			debug_serial_puts("[BOARD_R] [ENV] baudrate env var: ");
			debug_serial_puts(baudrate_env);
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts("[BOARD_R] [ENV] No baudrate env var\r\n");
		}
		
		/* Check serial device state after env_relocate */
		if (gd && gd->cur_serial_dev) {
			debug_serial_puts("[BOARD_R] [ENV] Serial device after env_relocate: ");
			if (gd->cur_serial_dev->name) {
				debug_serial_puts(gd->cur_serial_dev->name);
			} else {
				debug_serial_puts("NULL name");
			}
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts("[BOARD_R] [ENV] WARNING: Serial device is NULL after env_relocate!\r\n");
		}
#endif
	} else {
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] should_load_env() returned false\r\n");
		debug_serial_puts("[BOARD_R] [ENV] About to call env_set_default()\r\n");
#endif
		env_set_default(NULL, 0);
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] env_set_default() completed\r\n");
#endif
	}

#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] [ENV] About to call env_import_fdt()\r\n");
	debug_serial_puts("[BOARD_R] [ENV] Current baudrate before env_import_fdt: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif
	env_import_fdt();
#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] [ENV] env_import_fdt() completed\r\n");
	debug_serial_puts("[BOARD_R] [ENV] Current baudrate after env_import_fdt: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif

	if (IS_ENABLED(CONFIG_OF_CONTROL)) {
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] About to set fdtcontroladdr\r\n");
#endif
		env_set_hex("fdtcontroladdr",
			    (unsigned long)map_to_sysmem(gd->fdt_blob));
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] fdtcontroladdr set\r\n");
#endif
	}

	#if (IS_ENABLED(CONFIG_SAVE_PREV_BL_INITRAMFS_START_ADDR) || \
						IS_ENABLED(CONFIG_SAVE_PREV_BL_FDT_ADDR))
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] About to call save_prev_bl_data()\r\n");
#endif
		save_prev_bl_data();
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] [ENV] save_prev_bl_data() completed\r\n");
#endif
	#endif

	/* Initialize from environment */
#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] [ENV] About to get loadaddr from env\r\n");
#endif
	image_load_addr = env_get_ulong("loadaddr", 16, image_load_addr);
#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] [ENV] loadaddr retrieved\r\n");
	debug_serial_puts("[BOARD_R] [ENV] Current baudrate at end: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Final serial device check */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] [ENV] Serial device at end: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] [ENV] WARNING: Serial device is NULL at end!\r\n");
	}
	debug_serial_puts("[BOARD_R] [ENV] ===== initr_env() END =====\r\n");
#endif

	return 0;
}

#ifdef CONFIG_SYS_MALLOC_BOOTPARAMS
static int initr_malloc_bootparams(void)
{
	gd->bd->bi_boot_params = (ulong)malloc(CONFIG_SYS_BOOTPARAMS_LEN);
	if (!gd->bd->bi_boot_params) {
		puts("WARNING: Cannot allocate space for boot parameters\n");
		return -ENOMEM;
	}
	return 0;
}
#endif

#if defined(CONFIG_LED_STATUS)
static int initr_status_led(void)
{
#if defined(CONFIG_LED_STATUS_BOOT)
	status_led_set(CONFIG_LED_STATUS_BOOT, CONFIG_LED_STATUS_BLINKING);
#else
	status_led_init();
#endif
	return 0;
}
#endif

#ifdef CONFIG_CMD_NET
static int initr_net(void)
{
	puts("Net:   ");
	eth_initialize();
#if defined(CONFIG_RESET_PHY_R)
	debug("Reset Ethernet PHY\n");
	reset_phy();
#endif
	return 0;
}
#endif

#ifdef CONFIG_POST
static int initr_post(void)
{
	post_run(NULL, POST_RAM | post_bootmode_get(0));
	return 0;
}
#endif

#if defined(CFG_PRAM)
/*
 * Export available size of memory for Linux, taking into account the
 * protected RAM at top of memory
 */
int initr_mem(void)
{
	ulong pram = 0;
	char memsz[32];

	pram = env_get_ulong("pram", 10, CFG_PRAM);
	sprintf(memsz, "%ldk", (long int)((gd->ram_size / 1024) - pram));
	env_set("mem", memsz);

	return 0;
}
#endif

static int dm_announce(void)
{
	int device_count;
	int uclass_count;

	if (IS_ENABLED(CONFIG_DM)) {
		dm_get_stats(&device_count, &uclass_count);
		printf("Core:  %d devices, %d uclasses", device_count,
		       uclass_count);
		if (CONFIG_IS_ENABLED(OF_REAL))
			printf(", devicetree: %s", fdtdec_get_srcname());
		printf("\n");
		if (IS_ENABLED(CONFIG_OF_HAS_PRIOR_STAGE) &&
		    (gd->fdt_src == FDTSRC_SEPARATE ||
		     gd->fdt_src == FDTSRC_EMBED)) {
			printf("Warning: Unexpected devicetree source (not from a prior stage)");
			printf("Warning: U-Boot may not function properly\n");
		}
		if (IS_ENABLED(CONFIG_OF_TAG_MIGRATE) &&
		    (gd->flags & GD_FLG_OF_TAG_MIGRATE))
			/*
			 * U-Boot will silently fail to work after 2023.07 if
			 * there are old tags present
			 */
			printf("Warning: Device tree includes old 'u-boot,dm-' tags: please fix by 2023.07!\n");
	}

	return 0;
}

#if DEBUG_SERIAL
#include <asm/io.h>
#include <asm/global_data.h>
/* Use UART2 base address for RK3399 (0xff1a0000) */
#define DEBUG_UART_BASE 0xff1a0000
#define UART_LSR_THRE 0x20
#define UART_THR 0x00
#define UART_LSR 0x14

DECLARE_GLOBAL_DATA_PTR;

static inline void debug_serial_putc(char c)
{
	volatile void *uart_base = (volatile void *)DEBUG_UART_BASE;
	
	/* Wait for THR empty */
	while ((readl(uart_base + UART_LSR) & UART_LSR_THRE) == 0)
		;
	
	/* Send character */
	if (c == '\n') {
		writel('\r', uart_base + UART_THR);
		/* Wait for THR empty again */
		while ((readl(uart_base + UART_LSR) & UART_LSR_THRE) == 0)
			;
	}
	writel(c, uart_base + UART_THR);
}

static void debug_serial_puts(const char *s)
{
	while (*s)
		debug_serial_putc(*s++);
}

static void debug_serial_put_ulong(unsigned long val)
{
	char buf[32];
	int i = 0;
	if (val == 0) {
		debug_serial_putc('0');
		return;
	}
	while (val > 0) {
		buf[i++] = '0' + (val % 10);
		val /= 10;
	}
	while (i > 0)
		debug_serial_putc(buf[--i]);
}
#else
static inline void debug_serial_putc(char c) {}
static void debug_serial_puts(const char *s) {}
static void debug_serial_put_ulong(unsigned long val) {}
#endif

#if DEBUG_SERIAL
static int debug_after_initr_dm(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_dm() (separate debug) =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: This function is called IMMEDIATELY after initr_dm() returns!\r\n");
	debug_serial_puts("[BOARD_R] If serial is garbled here, the problem happened in initr_dm()!\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
		if (gd->baudrate != 1500000) {
			debug_serial_puts("\r\n[BOARD_R] ERROR: Baudrate is NOT 1500000! Serial is broken!\r\n");
			debug_serial_puts("[BOARD_R] Expected 1500000, got ");
			debug_serial_put_ulong(gd->baudrate);
			debug_serial_puts("\r\n");
		} else {
			debug_serial_puts(" (OK)\r\n");
		}
	} else {
		debug_serial_puts("unknown (gd is NULL)\r\n");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after initr_dm: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after initr_dm!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END AFTER initr_dm() =====\r\n");
	
	/* CRITICAL: After this point, we need to ensure baudrate stays 1500000 */
	/* Some functions between initr_dm() and serial_initialize() might change it */
	debug_serial_puts("[BOARD_R] CRITICAL CHECK: Verifying baudrate is 1500000\r\n");
	if (gd) {
		if (gd->baudrate != 1500000) {
			debug_serial_puts("[BOARD_R] ERROR: Baudrate changed after initr_dm()!\r\n");
			debug_serial_puts("[BOARD_R] Expected 1500000, got ");
			debug_serial_put_ulong(gd->baudrate);
			debug_serial_puts("\r\n");
			debug_serial_puts("[BOARD_R] CRITICAL: Fixing baudrate to 1500000...\r\n");
			gd->baudrate = 1500000;
			debug_serial_puts("[BOARD_R] gd->baudrate fixed to 1500000\r\n");
			debug_serial_puts("[BOARD_R] WARNING: If serial is garbled, baudrate was wrong!\r\n");
		} else {
			debug_serial_puts("[BOARD_R] OK: Baudrate is correct (1500000)\r\n");
		}
	} else {
		debug_serial_puts("[BOARD_R] WARNING: gd is NULL, cannot check baudrate!\r\n");
	}
	
	/* CRITICAL DEBUG: Force output multiple times to ensure it's visible */
	debug_serial_puts("[BOARD_R] ===== FORCE OUTPUT TEST =====\r\n");
	debug_serial_puts("[BOARD_R] ===== FORCE OUTPUT TEST =====\r\n");
	debug_serial_puts("[BOARD_R] ===== FORCE OUTPUT TEST =====\r\n");
	debug_serial_puts("[BOARD_R] About to return from debug_after_initr_dm()\r\n");
	debug_serial_puts("[BOARD_R] Next function in init sequence will be called\r\n");
	debug_serial_puts("[BOARD_R] ===== END debug_after_initr_dm() =====\r\n");
	
	/* CRITICAL: Add extra debug output to track execution flow */
	debug_serial_puts("[BOARD_R] ===== TRACE: After debug_after_initr_dm() returns\r\n");
	debug_serial_puts("[BOARD_R] ===== TRACE: Next in sequence: init_addr_map or board_init or set_cpu_clk_info\r\n");
	debug_serial_puts("[BOARD_R] ===== TRACE: Checking which functions will be called...\r\n");
	
	return 0;
}

static int debug_before_init_addr_map(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE init_addr_map() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call init_addr_map()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE init_addr_map() =====\r\n");
	return 0;
}

static int debug_after_init_addr_map(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER init_addr_map() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER init_addr_map() =====\r\n");
	return 0;
}

static int debug_before_board_init(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE board_init() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call board_init()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE board_init() =====\r\n");
	return 0;
}

static int debug_after_board_init(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER board_init() =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER board_init() =====\r\n");
	return 0;
}

static int debug_before_set_cpu_clk_info(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE set_cpu_clk_info() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call set_cpu_clk_info()\r\n");
	debug_serial_puts("[BOARD_R] This may trigger clock reconfiguration!\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE set_cpu_clk_info() =====\r\n");
	return 0;
}

static int debug_after_set_cpu_clk_info(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER set_cpu_clk_info() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: set_cpu_clk_info() completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER set_cpu_clk_info() =====\r\n");
	return 0;
}

#if DEBUG_SERIAL
static int debug_before_efi_memory_init(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE efi_memory_init() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call efi_memory_init()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE efi_memory_init() =====\r\n");
	return 0;
}

static int debug_after_efi_memory_init(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER efi_memory_init() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: efi_memory_init() completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER efi_memory_init() =====\r\n");
	return 0;
}

static int debug_before_arch_fsp_init_r(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE arch_fsp_init_r() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call arch_fsp_init_r()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE arch_fsp_init_r() =====\r\n");
	return 0;
}

static int debug_after_arch_fsp_init_r(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER arch_fsp_init_r() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: arch_fsp_init_r() completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END AFTER arch_fsp_init_r() =====\r\n");
	return 0;
}

static int debug_before_initr_dm_devices(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE initr_dm_devices() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call initr_dm_devices()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE initr_dm_devices() =====\r\n");
	return 0;
}

static int debug_before_stdio_init_tables(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE stdio_init_tables() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to call stdio_init_tables()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE stdio_init_tables() =====\r\n");
	return 0;
}

/* Generic debug function to track execution flow between init steps */
static int debug_trace_init_step(const char *step_name)
{
	debug_serial_puts("\r\n[BOARD_R] ===== TRACE: ");
	debug_serial_puts(step_name);
	debug_serial_puts(" =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END TRACE: ");
	debug_serial_puts(step_name);
	debug_serial_puts(" =====\r\n");
	return 0;
}

static int debug_trace_after_initr_dm(void)
{
	return debug_trace_init_step("After initr_dm, before next step");
}

static int debug_trace_after_init_addr_map(void)
{
	return debug_trace_init_step("After init_addr_map, before board_init");
}

static int debug_trace_after_board_init(void)
{
	return debug_trace_init_step("After board_init, before set_cpu_clk_info");
}

static int debug_trace_after_set_cpu_clk_info(void)
{
	return debug_trace_init_step("After set_cpu_clk_info, before efi_memory_init");
}

static int debug_trace_after_efi_memory_init(void)
{
	return debug_trace_init_step("After efi_memory_init, before initr_binman");
}

static int debug_trace_after_initr_binman(void)
{
	return debug_trace_init_step("After initr_binman, before arch_fsp_init_r");
}

static int debug_trace_after_arch_fsp_init_r(void)
{
	return debug_trace_init_step("After arch_fsp_init_r, before initr_dm_devices");
}

static int debug_trace_after_initr_dm_devices_step(void)
{
	return debug_trace_init_step("After initr_dm_devices, before stdio_init_tables");
}
#endif

static int debug_after_initr_dm_devices(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_dm_devices() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: initr_dm_devices() completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after initr_dm_devices: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after initr_dm_devices!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END AFTER initr_dm_devices() =====\r\n");
	return 0;
}

static int debug_after_stdio_init_tables(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER stdio_init_tables() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: stdio_init_tables() completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after stdio_init_tables: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after stdio_init_tables!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END AFTER stdio_init_tables() =====\r\n");
	return 0;
}

static int debug_before_serial_initialize(void)
{
	debug_serial_puts("\r\n[BOARD_R] Before serial_initialize()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* CRITICAL FIX: Ensure baudrate is 1500000 before serial_initialize */
	/* This ensures serial ports are initialized with correct baudrate */
	if (gd && gd->baudrate != 1500000) {
		debug_serial_puts("[BOARD_R] WARNING: Baudrate is not 1500000 before serial_initialize! Fixing...\r\n");
		gd->baudrate = 1500000;
		debug_serial_puts("[BOARD_R] gd->baudrate set to 1500000\r\n");
	}
	
	return 0;
}

static int debug_after_serial_initialize(void)
{
	debug_serial_puts("\r\n[BOARD_R] After serial_initialize()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* CRITICAL FIX: Ensure baudrate is 1500000 after serial_initialize */
	/* This prevents garbled output when serial is reinitialized */
	if (gd && gd->baudrate != 1500000) {
		debug_serial_puts("[BOARD_R] WARNING: Baudrate changed! Fixing to 1500000\r\n");
		gd->baudrate = 1500000;
		/* Reconfigure serial with correct baudrate */
		if (gd->cur_serial_dev) {
			struct udevice *dev = gd->cur_serial_dev;
			const struct dm_serial_ops *ops = serial_get_ops(dev);
			if (ops && ops->setbrg) {
				ops->setbrg(dev, gd->baudrate);
				debug_serial_puts("[BOARD_R] Serial baudrate reconfigured to 1500000\r\n");
			}
		}
	}
	
	return 0;
}

static int debug_before_initr_env(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE initr_env() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to initialize environment\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device before env: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL before env!\r\n");
	}
	
	/* Check if we're about to load from storage */
	debug_serial_puts("[BOARD_R] About to check should_load_env()\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE initr_env() =====\r\n");
	return 0;
}

static int debug_after_initr_env(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER initr_env() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: Environment initialization completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after env: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL after env!\r\n");
	}
	
	/* Check critical environment variables */
	const char *baudrate_env = env_get("baudrate");
	if (baudrate_env) {
		debug_serial_puts("[BOARD_R] baudrate env var: ");
		debug_serial_puts(baudrate_env);
		debug_serial_puts("\r\n");
		
		/* CRITICAL FIX: Ensure environment baudrate is 1500000 */
		/* This prevents garbled output from wrong baudrate in env */
		unsigned long env_baudrate = simple_strtoul(baudrate_env, NULL, 10);
		if (env_baudrate != 1500000) {
			debug_serial_puts("[BOARD_R] WARNING: Env baudrate is not 1500000! Fixing...\r\n");
			env_set("baudrate", "1500000");
			/* Update gd->baudrate as well */
			if (gd) {
				gd->baudrate = 1500000;
				debug_serial_puts("[BOARD_R] gd->baudrate updated to 1500000\r\n");
			}
		}
	} else {
		/* Set default baudrate if not in environment */
		debug_serial_puts("[BOARD_R] No baudrate in env, setting to 1500000\r\n");
		env_set("baudrate", "1500000");
		if (gd) {
			gd->baudrate = 1500000;
		}
	}
	
	const char *stdin_env = env_get("stdin");
	if (stdin_env) {
		debug_serial_puts("[BOARD_R] stdin env var: ");
		debug_serial_puts(stdin_env);
		debug_serial_puts("\r\n");
	}
	
	const char *stdout_env = env_get("stdout");
	if (stdout_env) {
		debug_serial_puts("[BOARD_R] stdout env var: ");
		debug_serial_puts(stdout_env);
		debug_serial_puts("\r\n");
	}
	
	const char *stderr_env = env_get("stderr");
	if (stderr_env) {
		debug_serial_puts("[BOARD_R] stderr env var: ");
		debug_serial_puts(stderr_env);
		debug_serial_puts("\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END AFTER initr_env() =====\r\n");
	return 0;
}

static int debug_before_stdio_add_devices(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE stdio_add_devices() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to add stdio devices\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device before stdio_add: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL before stdio_add!\r\n");
	}
	
	/* Check environment variables that might affect stdio */
	const char *stdin_env = env_get("stdin");
	const char *stdout_env = env_get("stdout");
	const char *stderr_env = env_get("stderr");
	
	if (stdin_env) {
		debug_serial_puts("[BOARD_R] stdin env: ");
		debug_serial_puts(stdin_env);
		debug_serial_puts("\r\n");
	}
	if (stdout_env) {
		debug_serial_puts("[BOARD_R] stdout env: ");
		debug_serial_puts(stdout_env);
		debug_serial_puts("\r\n");
	}
	if (stderr_env) {
		debug_serial_puts("[BOARD_R] stderr env: ");
		debug_serial_puts(stderr_env);
		debug_serial_puts("\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END BEFORE stdio_add_devices() =====\r\n");
	return 0;
}

static int debug_after_stdio_add_devices(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER stdio_add_devices() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: stdio devices added\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state - this is critical! */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after stdio_add: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] ERROR: Serial device is NULL after stdio_add!\r\n");
	}
	debug_serial_puts("[BOARD_R] ===== END AFTER stdio_add_devices() =====\r\n");
	return 0;
}

static int debug_before_console_init_r(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE console_init_r() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: About to fully initialize console\r\n");
	debug_serial_puts("[BOARD_R] This is where console might be reconfigured!\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device before console_init_r: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL before console_init_r!\r\n");
	}
	
	/* Check baudrate environment variable */
	const char *baudrate_env = env_get("baudrate");
	if (baudrate_env) {
		debug_serial_puts("[BOARD_R] baudrate env var before console_init_r: ");
		debug_serial_puts(baudrate_env);
		debug_serial_puts("\r\n");
	}
	
	debug_serial_puts("[BOARD_R] ===== END BEFORE console_init_r() =====\r\n");
	return 0;
}

static int debug_after_console_init_r(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== AFTER console_init_r() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: Console fully initialized\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state - this is VERY critical! */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after console_init_r: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] ERROR: Serial device is NULL after console_init_r!\r\n");
	}
	
	/* Check if baudrate changed */
	const char *baudrate_env = env_get("baudrate");
	if (baudrate_env) {
		debug_serial_puts("[BOARD_R] baudrate env var after console_init_r: ");
		debug_serial_puts(baudrate_env);
		debug_serial_puts("\r\n");
	}
	
	/* CRITICAL FIX: Ensure baudrate is 1500000 after console_init_r */
	/* This prevents garbled output when console is fully initialized */
	if (gd && gd->baudrate != 1500000) {
		debug_serial_puts("[BOARD_R] WARNING: Baudrate changed after console_init_r! Fixing to 1500000\r\n");
		gd->baudrate = 1500000;
		/* Reconfigure serial with correct baudrate */
		if (gd->cur_serial_dev) {
			struct udevice *dev = gd->cur_serial_dev;
			const struct dm_serial_ops *ops = serial_get_ops(dev);
			if (ops && ops->setbrg) {
				ops->setbrg(dev, gd->baudrate);
				debug_serial_puts("[BOARD_R] Serial baudrate reconfigured to 1500000\r\n");
			}
		}
		/* Also update environment variable if it exists */
		if (baudrate_env) {
			env_set("baudrate", "1500000");
			debug_serial_puts("[BOARD_R] Environment baudrate updated to 1500000\r\n");
		}
	}
	
	debug_serial_puts("[BOARD_R] ===== END AFTER console_init_r() =====\r\n");
	return 0;
}

static int debug_after_board_late_init(void)
{
	debug_serial_puts("\r\n[BOARD_R] After board_late_init()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	return 0;
}

static int debug_before_run_main_loop(void)
{
	debug_serial_puts("\r\n[BOARD_R] ===== BEFORE run_main_loop() =====\r\n");
	debug_serial_puts("[BOARD_R] CRITICAL: All init functions completed\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL!\r\n");
	}
	
	/* Check bootcmd */
	const char *bootcmd = env_get("bootcmd");
	if (bootcmd) {
		debug_serial_puts("[BOARD_R] bootcmd exists: ");
		debug_serial_puts(bootcmd);
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: bootcmd is NULL!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] About to call run_main_loop()\r\n");
	debug_serial_puts("[BOARD_R] ===== END BEFORE run_main_loop =====\r\n");
	return 0;
}
#endif

static int run_main_loop(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARD_R] ===== run_main_loop() START =====\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOARD_R] WARNING: Serial device is NULL!\r\n");
	}
	
	debug_serial_puts("[BOARD_R] About to call sandbox_main_loop_init() or event_notify_null()\r\n");
#endif

#ifdef CONFIG_SANDBOX
	sandbox_main_loop_init();
#endif

#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] About to call event_notify_null(EVT_MAIN_LOOP)\r\n");
#endif
	event_notify_null(EVT_MAIN_LOOP);

#if DEBUG_SERIAL
	debug_serial_puts("[BOARD_R] After event_notify_null, before main_loop()\r\n");
	debug_serial_puts("[BOARD_R] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	
	/* Check serial device state again */
	if (gd && gd->cur_serial_dev) {
		debug_serial_puts("[BOARD_R] Serial device after event: ");
		if (gd->cur_serial_dev->name) {
			debug_serial_puts(gd->cur_serial_dev->name);
		} else {
			debug_serial_puts("NULL name");
		}
		debug_serial_puts("\r\n");
	}
	
	debug_serial_puts("[BOARD_R] About to enter main_loop() infinite loop\r\n");
	debug_serial_puts("[BOARD_R] ===== ENTERING main_loop() =====\r\n");
#endif

	/* main_loop() can return to retry autoboot, if so just run it again */
	for (;;) {
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] Loop iteration: calling main_loop()\r\n");
		debug_serial_puts("[BOARD_R] Current baudrate: ");
		if (gd) {
			debug_serial_put_ulong(gd->baudrate);
		} else {
			debug_serial_puts("unknown");
		}
		debug_serial_puts("\r\n");
#endif
		main_loop();
#if DEBUG_SERIAL
		debug_serial_puts("[BOARD_R] main_loop() returned, looping again\r\n");
#endif
	}
	return 0;
}

/*
 * Over time we hope to remove these functions with code fragments and
 * stub functions, and instead call the relevant function directly.
 *
 * We also hope to remove most of the driver-related init and do it if/when
 * the driver is later used.
 *
 * TODO: perhaps reset the watchdog in the initcall function after each call?
 */
static init_fnc_t init_sequence_r[] = {
	initr_trace,
	initr_reloc,
	event_init,
	/* TODO: could x86/PPC have this also perhaps? */
#if defined(CONFIG_ARM) || defined(CONFIG_RISCV)
	initr_caches,
	/* Note: For Freescale LS2 SoCs, new MMU table is created in DDR.
	 *	 A temporary mapping of IFC high region is since removed,
	 *	 so environmental variables in NOR flash is not available
	 *	 until board_init() is called below to remap IFC to high
	 *	 region.
	 */
#endif
	initr_reloc_global_data,
#if defined(CONFIG_SYS_INIT_RAM_LOCK) && defined(CONFIG_E500)
	initr_unlock_ram_in_cache,
#endif
	initr_barrier,
	initr_malloc,
	log_init,
	initr_bootstage,	/* Needs malloc() but has its own timer */
#if defined(CONFIG_CONSOLE_RECORD)
	console_record_init,
#endif
#ifdef CONFIG_SYS_NONCACHED_MEMORY
	noncached_init,
#endif
	initr_of_live,
#ifdef CONFIG_DM
	initr_dm,
#if DEBUG_SERIAL
	debug_after_initr_dm,
	debug_trace_after_initr_dm,
#endif
#endif
#ifdef CONFIG_ADDR_MAP
#if DEBUG_SERIAL
	debug_before_init_addr_map,
#endif
	init_addr_map,
#if DEBUG_SERIAL
	debug_after_init_addr_map,
	debug_trace_after_init_addr_map,
#endif
#endif
#if defined(CONFIG_ARM) || defined(CONFIG_RISCV) || defined(CONFIG_SANDBOX)
#if DEBUG_SERIAL
	debug_before_board_init,
#endif
	board_init,	/* Setup chipselects */
#if DEBUG_SERIAL
	debug_after_board_init,
	debug_trace_after_board_init,
#endif
#endif
	/*
	 * TODO: printing of the clock inforamtion of the board is now
	 * implemented as part of bdinfo command. Currently only support for
	 * davinci SOC's is added. Remove this check once all the board
	 * implement this.
	 */
#ifdef CONFIG_CLOCKS
#if DEBUG_SERIAL
	debug_before_set_cpu_clk_info,
#endif
	set_cpu_clk_info, /* Setup clock information */
#if DEBUG_SERIAL
	debug_after_set_cpu_clk_info,
	debug_trace_after_set_cpu_clk_info,
#endif
#endif
#ifdef CONFIG_EFI_LOADER
#if DEBUG_SERIAL
	debug_before_efi_memory_init,
#endif
	efi_memory_init,
#if DEBUG_SERIAL
	debug_after_efi_memory_init,
	debug_trace_after_efi_memory_init,
#endif
#endif
#if DEBUG_SERIAL
	debug_before_initr_binman,
#endif
	initr_binman,
#if DEBUG_SERIAL
	debug_after_initr_binman,
	debug_trace_after_initr_binman,
#endif
#ifdef CONFIG_FSP_VERSION2
#if DEBUG_SERIAL
	debug_before_arch_fsp_init_r,
#endif
	arch_fsp_init_r,
#if DEBUG_SERIAL
	debug_after_arch_fsp_init_r,
	debug_trace_after_arch_fsp_init_r,
#endif
#endif
#if DEBUG_SERIAL
	debug_before_initr_dm_devices,
#endif
	initr_dm_devices,
#if DEBUG_SERIAL
	debug_after_initr_dm_devices,
	debug_trace_after_initr_dm_devices_step,
#endif
#if DEBUG_SERIAL
	debug_before_stdio_init_tables,
#endif
	stdio_init_tables,
#if DEBUG_SERIAL
	debug_after_stdio_init_tables,
#endif
#if DEBUG_SERIAL
	debug_before_serial_initialize,
#endif
	serial_initialize,
#if DEBUG_SERIAL
	debug_after_serial_initialize,
#endif
	initr_announce,
	dm_announce,
#if CONFIG_IS_ENABLED(WDT)
	initr_watchdog,
#endif
	INIT_FUNC_WATCHDOG_RESET
	arch_initr_trap,
#if defined(CONFIG_BOARD_EARLY_INIT_R)
	board_early_init_r,
#endif
	INIT_FUNC_WATCHDOG_RESET
#ifdef CONFIG_POST
	post_output_backlog,
#endif
	INIT_FUNC_WATCHDOG_RESET
#if defined(CONFIG_PCI_INIT_R) && defined(CONFIG_SYS_EARLY_PCI_INIT)
	/*
	 * Do early PCI configuration _before_ the flash gets initialised,
	 * because PCU resources are crucial for flash access on some boards.
	 */
	pci_init,
#endif
#ifdef CONFIG_ARCH_EARLY_INIT_R
	arch_early_init_r,
#endif
	debug_before_power_init_board,
	power_init_board,
	debug_after_power_init_board,
#ifdef CONFIG_MTD_NOR_FLASH
	initr_flash,
#endif
	INIT_FUNC_WATCHDOG_RESET
#if defined(CONFIG_PPC) || defined(CONFIG_M68K) || defined(CONFIG_X86)
	/* initialize higher level parts of CPU like time base and timers */
	cpu_init_r,
#endif
#ifdef CONFIG_EFI_LOADER
	efi_init_early,
#endif
#ifdef CONFIG_CMD_NAND
	initr_nand,
#endif
#ifdef CONFIG_CMD_ONENAND
	initr_onenand,
#endif
#ifdef CONFIG_MMC
	debug_before_initr_mmc,
	initr_mmc,
	debug_after_initr_mmc,
#endif
#ifdef CONFIG_XEN
	xen_init,
#endif
#ifdef CONFIG_PVBLOCK
	debug_before_initr_pvblock,
	initr_pvblock,
	debug_after_initr_pvblock,
#endif
	initr_env,
#ifdef CONFIG_SYS_MALLOC_BOOTPARAMS
	initr_malloc_bootparams,
#endif
	INIT_FUNC_WATCHDOG_RESET
	cpu_secondary_init_r,
#if defined(CONFIG_ID_EEPROM)
	mac_read_from_eeprom,
#endif
	INITCALL_EVENT(EVT_SETTINGS_R),
	INIT_FUNC_WATCHDOG_RESET
#if defined(CONFIG_PCI_INIT_R) && !defined(CONFIG_SYS_EARLY_PCI_INIT)
	/*
	 * Do pci configuration
	 */
	pci_init,
#endif
#if DEBUG_SERIAL
	debug_before_stdio_add_devices,
#endif
	stdio_add_devices,
#if DEBUG_SERIAL
	debug_after_stdio_add_devices,
#endif
	jumptable_init,
#ifdef CONFIG_API
	api_init,
#endif
#if DEBUG_SERIAL
	debug_before_console_init_r,
#endif
	console_init_r,		/* fully init console as a device */
#if DEBUG_SERIAL
	debug_after_console_init_r,
#endif
#ifdef CONFIG_DISPLAY_BOARDINFO_LATE
	console_announce_r,
	show_board_info,
#endif
#ifdef CONFIG_ARCH_MISC_INIT
	arch_misc_init,		/* miscellaneous arch-dependent init */
#endif
#ifdef CONFIG_MISC_INIT_R
	misc_init_r,		/* miscellaneous platform-dependent init */
#endif
	INIT_FUNC_WATCHDOG_RESET
#ifdef CONFIG_CMD_KGDB
	kgdb_init,
#endif
	interrupt_init,
#if defined(CONFIG_MICROBLAZE) || defined(CONFIG_M68K)
	timer_init,		/* initialize timer */
#endif
#if defined(CONFIG_LED_STATUS)
	initr_status_led,
#endif
	/* PPC has a udelay(20) here dating from 2002. Why? */
#ifdef CONFIG_BOARD_LATE_INIT
	board_late_init,
#endif
#if DEBUG_SERIAL
	debug_after_board_late_init,
#endif
#ifdef CONFIG_BITBANGMII
	bb_miiphy_init,
#endif
#ifdef CONFIG_PCI_ENDPOINT
	pci_ep_init,
#endif
#ifdef CONFIG_CMD_NET
	INIT_FUNC_WATCHDOG_RESET
	initr_net,
#endif
#ifdef CONFIG_POST
	initr_post,
#endif
	INIT_FUNC_WATCHDOG_RESET
	INITCALL_EVENT(EVT_LAST_STAGE_INIT),
#if defined(CFG_PRAM)
	initr_mem,
#endif
#if DEBUG_SERIAL
	debug_before_run_main_loop,
#endif
	run_main_loop,
};

void board_init_r(gd_t *new_gd, ulong dest_addr)
{
	/*
	 * The pre-relocation drivers may be using memory that has now gone
	 * away. Mark serial as unavailable - this will fall back to the debug
	 * UART if available.
	 *
	 * Do the same with log drivers since the memory may not be available.
	 */
	gd->flags &= ~(GD_FLG_SERIAL_READY | GD_FLG_LOG_READY);

	/*
	 * Set up the new global data pointer. So far only x86 does this
	 * here.
	 * TODO(sjg@chromium.org): Consider doing this for all archs, or
	 * dropping the new_gd parameter.
	 */
	if (CONFIG_IS_ENABLED(X86_64) && !IS_ENABLED(CONFIG_EFI_APP))
		arch_setup_gd(new_gd);

#if !defined(CONFIG_X86) && !defined(CONFIG_ARM) && !defined(CONFIG_ARM64)
	gd = new_gd;
#endif
	gd->flags &= ~GD_FLG_LOG_READY;

	if (initcall_run_list(init_sequence_r))
		hang();

	/* NOTREACHED - run_main_loop() does not return */
	hang();
}
