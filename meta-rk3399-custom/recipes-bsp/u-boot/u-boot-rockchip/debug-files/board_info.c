// SPDX-License-Identifier: GPL-2.0+

#include <dm.h>
#include <init.h>
#include <sysinfo.h>
#include <asm/global_data.h>
#include <asm/io.h>
#include <linux/libfdt.h>
#include <linux/compiler.h>

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

int __weak checkboard(void)
{
	return 0;
}

static const struct to_show {
	const char *name;
	enum sysinfo_id id;
} to_show[] = {
	{ "Manufacturer", SYSINFO_ID_BOARD_MANUFACTURER},
	{ "Prior-stage version", SYSINFO_ID_PRIOR_STAGE_VERSION },
	{ "Prior-stage date", SYSINFO_ID_PRIOR_STAGE_DATE },
	{ /* sentinel */ }
};

static int try_sysinfo(void)
{
	struct udevice *dev;
	char str[80];
	int ret;

	/* This might provide more detail */
	ret = sysinfo_get(&dev);
	if (ret)
		return ret;

	ret = sysinfo_detect(dev);
	if (ret)
		return ret;

	ret = sysinfo_get_str(dev, SYSINFO_ID_BOARD_MODEL, sizeof(str), str);
	if (ret)
		return ret;

#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARDINFO] About to print Model from sysinfo\r\n");
	debug_serial_puts("[BOARDINFO] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif

	printf("Model: %s\n", str);

#if DEBUG_SERIAL
	debug_serial_puts("[BOARDINFO] Model printed from sysinfo\r\n");
#endif

	if (IS_ENABLED(CONFIG_SYSINFO_EXTRA)) {
		const struct to_show *item;

		for (item = to_show; item->id; item++) {
			ret = sysinfo_get_str(dev, item->id, sizeof(str), str);
			if (!ret)
				printf("%s: %s\n", item->name, str);
		}
	}

	return 0;
}

int show_board_info(void)
{
#if DEBUG_SERIAL
	debug_serial_puts("\r\n[BOARDINFO] show_board_info() called\r\n");
	debug_serial_puts("[BOARDINFO] Current baudrate: ");
	if (gd) {
		debug_serial_put_ulong(gd->baudrate);
	} else {
		debug_serial_puts("unknown");
	}
	debug_serial_puts("\r\n");
#endif

	if (IS_ENABLED(CONFIG_OF_CONTROL)) {
		int ret = -ENOSYS;

		if (IS_ENABLED(CONFIG_SYSINFO))
			ret = try_sysinfo();

		/* Fail back to the main 'model' if available */
		if (ret) {
			const char *model;

#if DEBUG_SERIAL
			debug_serial_puts("[BOARDINFO] Trying to get Model from device tree\r\n");
#endif

			model = fdt_getprop(gd->fdt_blob, 0, "model", NULL);
			if (model) {
#if DEBUG_SERIAL
				debug_serial_puts("[BOARDINFO] About to print Model from device tree\r\n");
				debug_serial_puts("[BOARDINFO] Current baudrate: ");
				if (gd) {
					debug_serial_put_ulong(gd->baudrate);
				} else {
					debug_serial_puts("unknown");
				}
				debug_serial_puts("\r\n");
#endif
				printf("Model: %s\n", model);
#if DEBUG_SERIAL
				debug_serial_puts("[BOARDINFO] Model printed from device tree\r\n");
#endif
			}
		}
	}

	return checkboard();
}
