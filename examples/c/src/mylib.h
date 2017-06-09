#ifndef MYLIB_H
#define MYLIB_H
/*
 * I2C 
 */
extern void  i2c_setup(unsigned char deviceAdr);
extern char  i2c_send_byte(unsigned char value);
extern void  i2c_send_stop(void);
extern char  i2c_send_start(void);
extern void  i2c_set_slave_address(unsigned char deviceAdr);

/*
 *  SI7021
 */
extern short si7021_read_temperature(void);
extern short si7021_read_humidity(void);
extern unsigned char si7021_reset(void);

/*
 * LCD display
 */ 
extern char lcd_display_on(void);
extern char lcd_reset_display(void);

/*
 * framebuffer 
 */
extern char framebuffer_update_display(void);
extern void framebuffer_write_char(unsigned char character,char xpos, char ypos);
extern void framebuffer_scroll_up(void);
extern void framebuffer_clear(void);
extern void framebuffer_set_pixel(unsigned char x,unsigned char y);

/*
 * Utility functions
 */
extern void util_msleep(unsigned char millis);
extern void debug_green_led(unsigned char enable);
extern void debug_red_led(unsigned char enable);
extern void debug_blink_red(unsigned char count);
extern void debug_green_led_on(void);
extern void debug_green_led_off(void);
extern void debug_red_led_on(void);
extern void debug_red_led_off(void);

extern void debug_toggle_green_led(void);
extern void debug_toggle_red_led(void);

/*
 * PS/2 functions
 */
extern void ps2_reset(void);
extern char ps2_keybuffer_read(char *buffer,char bufsize);
extern char ps2_get_overflow_counter(void);
extern char ps2_get_last_error(void);
extern char ps2_write_byte(char cmd);

/*
 * IR functions
 */
extern void ir_setup(void);
extern unsigned char ir_receive(unsigned short *buffer,unsigned char bufferSize);

/*
 * ADC functions
 */
extern void adc_setup(unsigned char adcInput);
extern unsigned short adc_read(void);

/*
 * UART functions
 */
extern void uart_setup(void);
extern void uart_send(char *buffer,char bufsize);
extern char uart_receive(char *buffer,char bufsize);

#define ADC_0 0 // 0000 (0) - ADC0
#define ADC_1 1 // 0000 (0) - ADC0
#define ADC_2 2 // 0001 (1) - ADC1
#define ADC_3 3 // 0010 (2) - ADC2
#define ADC_4 4 // 0011 (3) - ADC3
#define ADC_5 5 // 0100 (4) - ADC4
#define ADC_6 6 // 0101 (5) - ADC5
#define ADC_7 7 // 0110 (6) - ADC6
#define ADC_TEMP 8 // 1000 (8) - Temperature sensor
#define ADC_11V 14 // 1110 (14) - 1.1V (Vbg)
#define ADC_GND 15 // 1111 (15) - 0V (GND)

#endif