// SPDX-License-Identifier: GPL-2.0+
/*
 * (C) Copyright 2000-2009
 * Wolfgang Denk, DENX Software Engineering, wd@denx.de.
 */

#include <bootm.h>
#include <command.h>
#include <image.h>
#include <irq_func.h>
#include <lmb.h>
#include <log.h>
#include <mapmem.h>
#include <asm/global_data.h>
#include <linux/kernel.h>
#include <linux/sizes.h>
#include <serial.h>
#include <ns16550.h>
#include <asm/io.h>
#include <string.h>

DECLARE_GLOBAL_DATA_PTR;

/* DEBUG: Direct serial output for early debugging */
#define DEBUG_SERIAL 1
#if DEBUG_SERIAL
/* Use UART2 base address for RK3399 (0xff1a0000) */
#define DEBUG_UART_BASE 0xff1a0000
#define UART_LSR_THRE 0x20
#define UART_THR 0x00
#define UART_LSR 0x14

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

static void debug_serial_put_hex(unsigned long val)
{
	char hex[] = "0123456789abcdef";
	int i;
	for (i = 60; i >= 0; i -= 4) {
		debug_serial_putc(hex[(val >> i) & 0xf]);
	}
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
static void debug_serial_put_hex(unsigned long val) {}
static void debug_serial_put_ulong(unsigned long val) {}
#endif
/*
 * Image booting support
 */
static int booti_start(struct bootm_info *bmi)
{
	struct bootm_headers *images = bmi->images;
	int ret;
	ulong ld;
	ulong relocated_addr;
	ulong image_size;
	uint8_t *temp;
	ulong dest;
	ulong dest_end;
	unsigned long comp_len;
	unsigned long decomp_len;
	int ctype;

#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOOTI] booti_start() called\r\n");
	debug_serial_puts("[BOOTI] Current baudrate: ");
	debug_serial_put_ulong(gd->baudrate);
	debug_serial_puts("\r\n");
	
	/* Check console device */
	const char *stdout_name = env_get("stdout");
	if (stdout_name) {
		debug_serial_puts("[BOOTI] stdout: ");
		debug_serial_puts(stdout_name);
		debug_serial_puts("\r\n");
	}
	const char *stderr_name = env_get("stderr");
	if (stderr_name) {
		debug_serial_puts("[BOOTI] stderr: ");
		debug_serial_puts(stderr_name);
		debug_serial_puts("\r\n");
	}
	const char *stdin_name = env_get("stdin");
	if (stdin_name) {
		debug_serial_puts("[BOOTI] stdin: ");
		debug_serial_puts(stdin_name);
		debug_serial_puts("\r\n");
	}
#endif

	ret = bootm_run_states(bmi, BOOTM_STATE_START);

	/* Setup Linux kernel Image entry point */
	if (!bmi->addr_img) {
		ld = image_load_addr;
		debug("*  kernel: default image load address = 0x%08lx\n",
				image_load_addr);
	} else {
		ld = hextoul(bmi->addr_img, NULL);
		debug("*  kernel: cmdline image address = 0x%08lx\n", ld);
	}

	temp = map_sysmem(ld, 0);
	ctype = image_decomp_type(temp, 2);
	if (ctype > 0) {
		dest = env_get_ulong("kernel_comp_addr_r", 16, 0);
		comp_len = env_get_ulong("kernel_comp_size", 16, 0);
		if (!dest || !comp_len) {
			puts("kernel_comp_addr_r or kernel_comp_size is not provided!\n");
			return -EINVAL;
		}
		if (dest < gd->ram_base || dest > gd->ram_top) {
			puts("kernel_comp_addr_r is outside of DRAM range!\n");
			return -EINVAL;
		}

		debug("kernel image compression type %d size = 0x%08lx address = 0x%08lx\n",
			ctype, comp_len, (ulong)dest);
		decomp_len = comp_len * 10;
		ret = image_decomp(ctype, 0, ld, IH_TYPE_KERNEL,
				 (void *)dest, (void *)ld, comp_len,
				 decomp_len, &dest_end);
		if (ret)
			return ret;
		/* dest_end contains the uncompressed Image size */
		memmove((void *) ld, (void *)dest, dest_end);
	}
	unmap_sysmem((void *)ld);

	ret = booti_setup(ld, &relocated_addr, &image_size, false);
	if (ret)
		return 1;

	/* Handle BOOTM_STATE_LOADOS */
	if (relocated_addr != ld) {
		printf("Moving Image from 0x%lx to 0x%lx, end=%lx\n", ld,
		       relocated_addr, relocated_addr + image_size);
		memmove((void *)relocated_addr, (void *)ld, image_size);
	}

	images->ep = relocated_addr;
	images->os.start = relocated_addr;
	images->os.end = relocated_addr + image_size;

	lmb_reserve(&images->lmb, images->ep, le32_to_cpu(image_size));

	/*
	 * Handle the BOOTM_STATE_FINDOTHER state ourselves as we do not
	 * have a header that provide this informaiton.
	 */
	if (bootm_find_images(image_load_addr, bmi->conf_ramdisk, bmi->conf_fdt,
			      relocated_addr, image_size))
		return 1;

	return 0;
}

int do_booti(struct cmd_tbl *cmdtp, int flag, int argc, char *const argv[])
{
	struct bootm_info bmi;
	int states;
	int ret;
#if DEBUG_SERIAL
	struct serial_device *serial_dev;
	const char *bootargs;
#endif

#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOOTI] ===== do_booti() START =====\r\n");
	debug_serial_puts("[BOOTI] Current baudrate: ");
	debug_serial_put_ulong(gd->baudrate);
	debug_serial_puts("\r\n");
	
	/* Output bootargs before kernel starts */
	bootargs = env_get("bootargs");
	if (bootargs) {
		debug_serial_puts("[BOOTI] bootargs: ");
		debug_serial_puts(bootargs);
		debug_serial_puts("\r\n");
	}
	
	/* Check serial console device */
	serial_dev = gd->cur_serial_dev;
	if (serial_dev) {
		debug_serial_puts("[BOOTI] serial_dev name: ");
		debug_serial_puts(serial_dev->name ? serial_dev->name : "NULL");
		debug_serial_puts("\r\n");
	}
#endif

	/* Consume 'booti' */
	argc--; argv++;

	bootm_init(&bmi);
	if (argc)
		bmi.addr_img = argv[0];
	if (argc > 1)
		bmi.conf_ramdisk = argv[1];
	if (argc > 2)
		bmi.conf_fdt = argv[2];
	bmi.boot_progress = true;
	bmi.cmd_name = "booti";
	/* do not set up argc and argv[] since nothing uses them */

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] About to call booti_start()\r\n");
#endif

	if (booti_start(&bmi))
		return 1;

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] booti_start() completed\r\n");
	debug_serial_puts("[BOOTI] Current baudrate after booti_start: ");
	debug_serial_put_ulong(gd->baudrate);
	debug_serial_puts("\r\n");
	
	/* Check if serial device changed */
	serial_dev = gd->cur_serial_dev;
	if (serial_dev) {
		debug_serial_puts("[BOOTI] serial_dev after booti_start: ");
		debug_serial_puts(serial_dev->name ? serial_dev->name : "NULL");
		debug_serial_puts("\r\n");
	}
#endif

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] About to disable interrupts\r\n");
#endif
	/*
	 * We are doing the BOOTM_STATE_LOADOS state ourselves, so must
	 * disable interrupts ourselves
	 */
	bootm_disable_interrupts();

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] Interrupts disabled\r\n");
	debug_serial_puts("[BOOTI] Setting OS type and architecture\r\n");
#endif

	bmi.images->os.os = IH_OS_LINUX;
	if (IS_ENABLED(CONFIG_RISCV_SMODE))
		bmi.images->os.arch = IH_ARCH_RISCV;
	else if (IS_ENABLED(CONFIG_ARM64))
		bmi.images->os.arch = IH_ARCH_ARM64;

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] OS type: LINUX, Arch: ");
	if (IS_ENABLED(CONFIG_ARM64))
		debug_serial_puts("ARM64");
	else if (IS_ENABLED(CONFIG_RISCV_SMODE))
		debug_serial_puts("RISCV");
	else
		debug_serial_puts("UNKNOWN");
	debug_serial_puts("\r\n");
#endif

	states = BOOTM_STATE_MEASURE | BOOTM_STATE_OS_PREP |
		BOOTM_STATE_OS_FAKE_GO | BOOTM_STATE_OS_GO;
	if (IS_ENABLED(CONFIG_SYS_BOOT_RAMDISK_HIGH))
		states |= BOOTM_STATE_RAMDISK;

#if DEBUG_SERIAL
	debug_serial_puts("[BOOTI] Boot states: ");
	debug_serial_put_hex(states);
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOOTI] Kernel entry point: ");
	debug_serial_put_hex(bmi.images->ep);
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOOTI] Kernel start: ");
	debug_serial_put_hex(bmi.images->os.start);
	debug_serial_puts("\r\n");
	debug_serial_puts("[BOOTI] Kernel end: ");
	debug_serial_put_hex(bmi.images->os.end);
	debug_serial_puts("\r\n");
	if (bmi.images->ft_addr) {
		debug_serial_puts("[BOOTI] FDT address: ");
		debug_serial_put_hex(bmi.images->ft_addr);
		debug_serial_puts("\r\n");
	}
	if (bmi.images->rd_start) {
		debug_serial_puts("[BOOTI] Initrd start: ");
		debug_serial_put_hex(bmi.images->rd_start);
		debug_serial_puts("\r\n");
		debug_serial_puts("[BOOTI] Initrd end: ");
		debug_serial_put_hex(bmi.images->rd_end);
		debug_serial_puts("\r\n");
	}
	debug_serial_puts("[BOOTI] Final baudrate before kernel: ");
	debug_serial_put_ulong(gd->baudrate);
	debug_serial_puts("\r\n");
	
	/* Final check of serial device */
	serial_dev = gd->cur_serial_dev;
	if (serial_dev) {
		debug_serial_puts("[BOOTI] Final serial_dev: ");
		debug_serial_puts(serial_dev->name ? serial_dev->name : "NULL");
		debug_serial_puts("\r\n");
	}
	
	/* Output final bootargs */
	bootargs = env_get("bootargs");
	if (bootargs) {
		debug_serial_puts("[BOOTI] Final bootargs: ");
		debug_serial_puts(bootargs);
		debug_serial_puts("\r\n");
	} else {
		debug_serial_puts("[BOOTI] WARNING: bootargs is NULL!\r\n");
	}
	
	/* Check earlycon in bootargs */
	if (bootargs && strstr(bootargs, "earlycon")) {
		debug_serial_puts("[BOOTI] earlycon found in bootargs\r\n");
	} else {
		debug_serial_puts("[BOOTI] WARNING: earlycon NOT found in bootargs!\r\n");
	}
	
	debug_serial_puts("[BOOTI] About to call bootm_run_states() - STARTING KERNEL\r\n");
	debug_serial_puts("[BOOTI] ===== TRANSFERRING TO KERNEL =====\r\n");
	/* Flush all output before kernel starts */
	for (int i = 0; i < 10; i++) {
		debug_serial_putc(' ');
	}
	debug_serial_puts("\r\n");
#endif

	ret = bootm_run_states(&bmi, states);

	return ret;
}

U_BOOT_LONGHELP(booti,
	"[addr [initrd[:size]] [fdt]]\n"
	"    - boot Linux flat or compressed 'Image' stored at 'addr'\n"
	"\tThe argument 'initrd' is optional and specifies the address\n"
	"\tof an initrd in memory. The optional parameter ':size' allows\n"
	"\tspecifying the size of a RAW initrd.\n"
	"\tCurrently only booting from gz, bz2, lzma and lz4 compression\n"
	"\ttypes are supported. In order to boot from any of these compressed\n"
	"\timages, user have to set kernel_comp_addr_r and kernel_comp_size environment\n"
	"\tvariables beforehand.\n"
#if defined(CONFIG_OF_LIBFDT)
	"\tSince booting a Linux kernel requires a flat device-tree, a\n"
	"\tthird argument providing the address of the device-tree blob\n"
	"\tis required. To boot a kernel with a device-tree blob but\n"
	"\twithout an initrd image, use a '-' for the initrd argument.\n"
#endif
	);

U_BOOT_CMD(
	booti,	CONFIG_SYS_MAXARGS,	1,	do_booti,
	"boot Linux kernel 'Image' format from memory", booti_help_text
);
