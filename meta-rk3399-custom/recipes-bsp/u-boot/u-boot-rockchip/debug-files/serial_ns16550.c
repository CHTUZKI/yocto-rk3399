// SPDX-License-Identifier: GPL-2.0+
/*
 * (C) Copyright 2000
 * Rob Taylor, Flying Pig Systems. robt@flyingpig.com.
 */

#include <config.h>
#include <clock_legacy.h>
#include <ns16550.h>
#include <serial.h>
#include <asm/global_data.h>
#include <linux/compiler.h>

/* DEBUG: Add debug output for serial console */
#define DEBUG_SERIAL 1

#if !CONFIG_IS_ENABLED(NS16550_MIN_FUNCTIONS)

DECLARE_GLOBAL_DATA_PTR;

#if !defined(CONFIG_CONS_INDEX)
#elif (CONFIG_CONS_INDEX < 1) || (CONFIG_CONS_INDEX > 6)
#error	"Invalid console index value."
#endif

#if CONFIG_CONS_INDEX == 1 && !defined(CFG_SYS_NS16550_COM1)
#error	"Console port 1 defined but not configured."
#elif CONFIG_CONS_INDEX == 2 && !defined(CFG_SYS_NS16550_COM2)
#error	"Console port 2 defined but not configured."
#elif CONFIG_CONS_INDEX == 3 && !defined(CFG_SYS_NS16550_COM3)
#error	"Console port 3 defined but not configured."
#elif CONFIG_CONS_INDEX == 4 && !defined(CFG_SYS_NS16550_COM4)
#error	"Console port 4 defined but not configured."
#elif CONFIG_CONS_INDEX == 5 && !defined(CFG_SYS_NS16550_COM5)
#error	"Console port 5 defined but not configured."
#elif CONFIG_CONS_INDEX == 6 && !defined(CFG_SYS_NS16550_COM6)
#error	"Console port 6 defined but not configured."
#endif

/* Note: The port number specified in the functions is 1 based.
 *	 the array is 0 based.
 */
static struct ns16550 *serial_ports[6] = {
#ifdef CFG_SYS_NS16550_COM1
	(struct ns16550 *)CFG_SYS_NS16550_COM1,
#else
	NULL,
#endif
#ifdef CFG_SYS_NS16550_COM2
	(struct ns16550 *)CFG_SYS_NS16550_COM2,
#else
	NULL,
#endif
#ifdef CFG_SYS_NS16550_COM3
	(struct ns16550 *)CFG_SYS_NS16550_COM3,
#else
	NULL,
#endif
#ifdef CFG_SYS_NS16550_COM4
	(struct ns16550 *)CFG_SYS_NS16550_COM4,
#else
	NULL,
#endif
#ifdef CFG_SYS_NS16550_COM5
	(struct ns16550 *)CFG_SYS_NS16550_COM5,
#else
	NULL,
#endif
#ifdef CFG_SYS_NS16550_COM6
	(struct ns16550 *)CFG_SYS_NS16550_COM6
#else
	NULL
#endif
};

#define PORT	serial_ports[port-1]

/* Multi serial device functions */
#define DECLARE_ESERIAL_FUNCTIONS(port) \
	static int  eserial##port##_init(void) \
	{ \
#if DEBUG_SERIAL \
		struct ns16550 *debug_port = serial_ports[2]; /* port 3 = ttyS2 */ \
		if (debug_port) { \
			const char *msg = "[ESERIAL" #port "_INIT] ===== START =====\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
			msg = "[ESERIAL" #port "_INIT] Line: int clock_divisor;\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
			msg = "[ESERIAL" #port "_INIT] Current gd->baudrate: "; \
			while (*msg) ns16550_putc(debug_port, *msg++); \
			if (gd) { \
				unsigned long baud = gd->baudrate; \
				char buf[32]; \
				int i = 0; \
				if (baud == 0) { \
					ns16550_putc(debug_port, '0'); \
				} else { \
					while (baud > 0) { \
						buf[i++] = '0' + (baud % 10); \
						baud /= 10; \
					} \
					while (i > 0) \
						ns16550_putc(debug_port, buf[--i]); \
				} \
			} else { \
				msg = "unknown"; \
				while (*msg) ns16550_putc(debug_port, *msg++); \
			} \
			ns16550_putc(debug_port, '\r'); \
			ns16550_putc(debug_port, '\n'); \
		} \
#endif \
		int clock_divisor; \
#if DEBUG_SERIAL \
		if (debug_port) { \
			const char *msg = "[ESERIAL" #port "_INIT] Line: clock_divisor = ns16550_calc_divisor(...);\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
		} \
#endif \
		clock_divisor = ns16550_calc_divisor(serial_ports[port-1], \
				CFG_SYS_NS16550_CLK, gd->baudrate); \
#if DEBUG_SERIAL \
		if (debug_port) { \
			const char *msg = "[ESERIAL" #port "_INIT] clock_divisor calculated: "; \
			while (*msg) ns16550_putc(debug_port, *msg++); \
			unsigned long div = clock_divisor; \
			char buf[32]; \
			int i = 0; \
			if (div == 0) { \
				ns16550_putc(debug_port, '0'); \
			} else { \
				while (div > 0) { \
					buf[i++] = '0' + (div % 10); \
					div /= 10; \
				} \
				while (i > 0) \
					ns16550_putc(debug_port, buf[--i]); \
			} \
			ns16550_putc(debug_port, '\r'); \
			ns16550_putc(debug_port, '\n'); \
			msg = "[ESERIAL" #port "_INIT] Line: ns16550_init(...);\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
		} \
#endif \
		ns16550_init(serial_ports[port - 1], clock_divisor); \
#if DEBUG_SERIAL \
		if (debug_port) { \
			const char *msg = "[ESERIAL" #port "_INIT] ns16550_init() completed\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
			msg = "[ESERIAL" #port "_INIT] Line: return 0;\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
			msg = "[ESERIAL" #port "_INIT] ===== END =====\r\n"; \
			while (*msg) { \
				if (*msg == '\n') ns16550_putc(debug_port, '\r'); \
				ns16550_putc(debug_port, *msg++); \
			} \
		} \
#endif \
		return 0 ; \
	} \
	static void eserial##port##_setbrg(void) \
	{ \
		serial_setbrg_dev(port); \
	} \
	static int  eserial##port##_getc(void) \
	{ \
		return serial_getc_dev(port); \
	} \
	static int  eserial##port##_tstc(void) \
	{ \
		return serial_tstc_dev(port); \
	} \
	static void eserial##port##_putc(const char c) \
	{ \
		serial_putc_dev(port, c); \
	} \
	static void eserial##port##_puts(const char *s) \
	{ \
		serial_puts_dev(port, s); \
	}

/* Serial device descriptor */
#define INIT_ESERIAL_STRUCTURE(port, __name) {	\
	.name	= __name,			\
	.start	= eserial##port##_init,		\
	.stop	= NULL,				\
	.setbrg	= eserial##port##_setbrg,	\
	.getc	= eserial##port##_getc,		\
	.tstc	= eserial##port##_tstc,		\
	.putc	= eserial##port##_putc,		\
	.puts	= eserial##port##_puts,		\
}

/* DEBUG: Helper function for debug output (use port 3 = ttyS2) */
static void _serial_puts_debug(const char *s, const int port)
{
	struct ns16550 *debug_port = serial_ports[port-1];
	if (debug_port) {
		while (*s) {
			if (*s == '\n')
				ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, *s++);
		}
	}
}

static void _serial_putc(const char c, const int port)
{
#if DEBUG_SERIAL
	/* DEBUG: Log every 100th character being sent */
	static int char_count = 0;
	char_count++;
	if (char_count % 100 == 0) {
		struct ns16550 *debug_port = serial_ports[2]; /* port 3 = ttyS2 */
		if (debug_port) {
			const char *prefix = "[SERIAL] port=";
			while (*prefix) ns16550_putc(debug_port, *prefix++);
			/* Simple number output */
			if (port == 1) ns16550_putc(debug_port, '1');
			else if (port == 2) ns16550_putc(debug_port, '2');
			else if (port == 3) ns16550_putc(debug_port, '3');
			const char *suffix = " count=";
			while (*suffix) ns16550_putc(debug_port, *suffix++);
			/* Output count (simplified) */
			int n = char_count;
			char num_buf[16];
			int i = 0;
			do {
				num_buf[i++] = '0' + (n % 10);
				n /= 10;
			} while (n > 0);
			while (i > 0) ns16550_putc(debug_port, num_buf[--i]);
			ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, '\n');
		}
	}
#endif
	if (c == '\n')
		ns16550_putc(PORT, '\r');

	ns16550_putc(PORT, c);
}

static void _serial_puts(const char *s, const int port)
{
	while (*s) {
		_serial_putc(*s++, port);
	}
}

static int _serial_getc(const int port)
{
	return ns16550_getc(PORT);
}

static int _serial_tstc(const int port)
{
	return ns16550_tstc(PORT);
}

static void _serial_setbrg(const int port)
{
	int clock_divisor;

#if DEBUG_SERIAL
	/* DEBUG: Output setbrg info directly using ns16550_putc */
	struct ns16550 *debug_port = serial_ports[2]; /* port 3 = ttyS2 */
	if (debug_port) {
		const char *msg = "[SERIAL] ===== setbrg() CRITICAL =====\r\n";
		while (*msg) {
			if (*msg == '\n') ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, *msg++);
		}
		
		msg = "[SERIAL] setbrg called for port=";
		while (*msg) ns16550_putc(debug_port, *msg++);
		if (port >= 1 && port <= 6) {
			char port_char = '0' + port;
			ns16550_putc(debug_port, port_char);
		}
		
		msg = " current_baudrate=";
		while (*msg) ns16550_putc(debug_port, *msg++);
		
		/* Print baudrate value */
		if (gd) {
			unsigned long baud = gd->baudrate;
			char buf[32];
			int i = 0;
			if (baud == 0) {
				ns16550_putc(debug_port, '0');
			} else {
				while (baud > 0) {
					buf[i++] = '0' + (baud % 10);
					baud /= 10;
				}
				while (i > 0)
					ns16550_putc(debug_port, buf[--i]);
			}
		} else {
			msg = "unknown";
			while (*msg) ns16550_putc(debug_port, *msg++);
		}
		
		msg = " CLK=";
		while (*msg) ns16550_putc(debug_port, *msg++);
		
		/* Print CLK value */
		unsigned long clk = CFG_SYS_NS16550_CLK;
		char buf[32];
		int i = 0;
		if (clk == 0) {
			ns16550_putc(debug_port, '0');
		} else {
			while (clk > 0) {
				buf[i++] = '0' + (clk % 10);
				clk /= 10;
			}
			while (i > 0)
				ns16550_putc(debug_port, buf[--i]);
		}
		
		ns16550_putc(debug_port, '\r');
		ns16550_putc(debug_port, '\n');
	}
#endif

	clock_divisor = ns16550_calc_divisor(PORT, CFG_SYS_NS16550_CLK,
					     gd->baudrate);
	
#if DEBUG_SERIAL
	if (debug_port) {
		const char *msg = "[SERIAL] divisor calculated: ";
		while (*msg) ns16550_putc(debug_port, *msg++);
		
		/* Print divisor value */
		char buf[32];
		int i = 0;
		unsigned long div = clock_divisor;
		if (div == 0) {
			ns16550_putc(debug_port, '0');
		} else {
			while (div > 0) {
				buf[i++] = '0' + (div % 10);
				div /= 10;
			}
			while (i > 0)
				ns16550_putc(debug_port, buf[--i]);
		}
		ns16550_putc(debug_port, '\r');
		ns16550_putc(debug_port, '\n');
		
		msg = "[SERIAL] About to call ns16550_reinit()\r\n";
		while (*msg) {
			if (*msg == '\n') ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, *msg++);
		}
	}
#endif

	ns16550_reinit(PORT, clock_divisor);

#if DEBUG_SERIAL
	if (debug_port) {
		const char *msg = "[SERIAL] ns16550_reinit() completed\r\n";
		while (*msg) {
			if (*msg == '\n') ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, *msg++);
		}
		msg = "[SERIAL] ===== setbrg() END =====\r\n";
		while (*msg) {
			if (*msg == '\n') ns16550_putc(debug_port, '\r');
			ns16550_putc(debug_port, *msg++);
		}
	}
#endif
}

static inline void
serial_putc_dev(unsigned int dev_index,const char c)
{
	_serial_putc(c,dev_index);
}

static inline void
serial_puts_dev(unsigned int dev_index,const char *s)
{
	_serial_puts(s,dev_index);
}

static inline int
serial_getc_dev(unsigned int dev_index)
{
	return _serial_getc(dev_index);
}

static inline int
serial_tstc_dev(unsigned int dev_index)
{
	return _serial_tstc(dev_index);
}

static inline void
serial_setbrg_dev(unsigned int dev_index)
{
	_serial_setbrg(dev_index);
}

#if defined(CFG_SYS_NS16550_COM1)
DECLARE_ESERIAL_FUNCTIONS(1);
struct serial_device eserial1_device =
	INIT_ESERIAL_STRUCTURE(1, "eserial0");
#endif
#if defined(CFG_SYS_NS16550_COM2)
DECLARE_ESERIAL_FUNCTIONS(2);
struct serial_device eserial2_device =
	INIT_ESERIAL_STRUCTURE(2, "eserial1");
#endif
#if defined(CFG_SYS_NS16550_COM3)
DECLARE_ESERIAL_FUNCTIONS(3);
struct serial_device eserial3_device =
	INIT_ESERIAL_STRUCTURE(3, "eserial2");
#endif
#if defined(CFG_SYS_NS16550_COM4)
DECLARE_ESERIAL_FUNCTIONS(4);
struct serial_device eserial4_device =
	INIT_ESERIAL_STRUCTURE(4, "eserial3");
#endif
#if defined(CFG_SYS_NS16550_COM5)
DECLARE_ESERIAL_FUNCTIONS(5);
struct serial_device eserial5_device =
	INIT_ESERIAL_STRUCTURE(5, "eserial4");
#endif
#if defined(CFG_SYS_NS16550_COM6)
DECLARE_ESERIAL_FUNCTIONS(6);
struct serial_device eserial6_device =
	INIT_ESERIAL_STRUCTURE(6, "eserial5");
#endif

__weak struct serial_device *default_serial_console(void)
{
#if CONFIG_CONS_INDEX == 1
	return &eserial1_device;
#elif CONFIG_CONS_INDEX == 2
	return &eserial2_device;
#elif CONFIG_CONS_INDEX == 3
	return &eserial3_device;
#elif CONFIG_CONS_INDEX == 4
	return &eserial4_device;
#elif CONFIG_CONS_INDEX == 5
	return &eserial5_device;
#elif CONFIG_CONS_INDEX == 6
	return &eserial6_device;
#else
#error "Bad CONFIG_CONS_INDEX."
#endif
}

void ns16550_serial_initialize(void)
{
#if DEBUG_SERIAL
	/* Use direct ns16550_putc for early debug before serial is registered */
	if (serial_ports[2]) { /* port 3 = ttyS2 */
		const char *msg = "[NS16550_INIT] ===== START =====\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
		msg = "[NS16550_INIT] Line: void ns16550_serial_initialize(void)\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
		msg = "[NS16550_INIT] Current baudrate: ";
		while (*msg) ns16550_putc(serial_ports[2], *msg++);
		if (gd) {
			unsigned long baud = gd->baudrate;
			char buf[32];
			int i = 0;
			if (baud == 0) {
				ns16550_putc(serial_ports[2], '0');
			} else {
				while (baud > 0) {
					buf[i++] = '0' + (baud % 10);
					baud /= 10;
				}
				while (i > 0)
					ns16550_putc(serial_ports[2], buf[--i]);
			}
		} else {
			msg = "unknown";
			while (*msg) ns16550_putc(serial_ports[2], *msg++);
		}
		ns16550_putc(serial_ports[2], '\r');
		ns16550_putc(serial_ports[2], '\n');
	}
#endif

#if defined(CFG_SYS_NS16550_COM1)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial1_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial1_device);
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] eserial1_device registered\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
#endif
#if defined(CFG_SYS_NS16550_COM2)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial2_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial2_device);
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] eserial2_device registered\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
#endif
#if defined(CFG_SYS_NS16550_COM3)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial3_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial3_device);
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] eserial3_device registered (ttyS2)\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
		msg = "[NS16550_INIT] Current baudrate after register: ";
		while (*msg) ns16550_putc(serial_ports[2], *msg++);
		if (gd) {
			unsigned long baud = gd->baudrate;
			char buf[32];
			int i = 0;
			if (baud == 0) {
				ns16550_putc(serial_ports[2], '0');
			} else {
				while (baud > 0) {
					buf[i++] = '0' + (baud % 10);
					baud /= 10;
				}
				while (i > 0)
					ns16550_putc(serial_ports[2], buf[--i]);
			}
		} else {
			msg = "unknown";
			while (*msg) ns16550_putc(serial_ports[2], *msg++);
		}
		ns16550_putc(serial_ports[2], '\r');
		ns16550_putc(serial_ports[2], '\n');
	}
#endif
#endif
#if defined(CFG_SYS_NS16550_COM4)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial4_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial4_device);
#endif
#if defined(CFG_SYS_NS16550_COM5)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial5_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial5_device);
#endif
#if defined(CFG_SYS_NS16550_COM6)
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] Line: serial_register(&eserial6_device);\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
	serial_register(&eserial6_device);
#endif
#if DEBUG_SERIAL
	if (serial_ports[2]) {
		const char *msg = "[NS16550_INIT] ===== END =====\r\n";
		while (*msg) {
			if (*msg == '\n')
				ns16550_putc(serial_ports[2], '\r');
			ns16550_putc(serial_ports[2], *msg++);
		}
	}
#endif
}

#endif /* !CONFIG_IS_ENABLED(NS16550_MIN_FUNCTIONS) */
