// SPDX-License-Identifier: (GPL-2.0+ OR MIT)
/*
 * (C) Copyright 2019 Amarula Solutions(India)
 * Author: Jagan Teki <jagan@amarulasolutions.com>
 */

#include <env.h>
#include <init.h>
#include <asm/arch-rockchip/clock.h>
#include <asm/arch-rockchip/cru.h>
#include <asm/arch-rockchip/hardware.h>
#include <asm/io.h>
#include <asm/global_data.h>
#include <linux/err.h>

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

char *get_reset_cause(void)
{
	struct rockchip_cru *cru = rockchip_get_cru();
	char *cause = NULL;

	if (IS_ERR(cru))
		return cause;

	switch (cru->glb_rst_st) {
	case GLB_POR_RST:
		cause = "POR";
		break;
	case FST_GLB_RST_ST:
	case SND_GLB_RST_ST:
		cause = "RST";
		break;
	case FST_GLB_TSADC_RST_ST:
	case SND_GLB_TSADC_RST_ST:
		cause = "THERMAL";
		break;
	case FST_GLB_WDT_RST_ST:
	case SND_GLB_WDT_RST_ST:
		cause = "WDOG";
		break;
	default:
		cause = "unknown reset";
	}

	return cause;
}

#if IS_ENABLED(CONFIG_DISPLAY_CPUINFO)
int print_cpuinfo(void)
{
	char *cause = get_reset_cause();

#if DEBUG_SERIAL
	debug_serial_puts("\r\n[CPUINFO] print_cpuinfo() called\r\n");
	debug_serial_puts("[CPUINFO] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[CPUINFO] About to print SoC info\r\n");
#endif

	printf("SoC: Rockchip %s\n", CONFIG_SYS_SOC);
	printf("Reset cause: %s\n", cause);

#if DEBUG_SERIAL
	debug_serial_puts("[CPUINFO] SoC and Reset cause printed\r\n");
	debug_serial_puts("[CPUINFO] Current baudrate after printf: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
	debug_serial_puts("[CPUINFO] About to set reset_reason env\r\n");
#endif

	/**
	 * reset_reason env is used by rk3288, due to special use case
	 * to figure it the boot behavior. so keep this as it is.
	 */
	env_set("reset_reason", cause);

#if DEBUG_SERIAL
	debug_serial_puts("[CPUINFO] print_cpuinfo() completed\r\n");
#endif

	/* TODO print operating temparature and clock */

	return 0;
}
#endif
