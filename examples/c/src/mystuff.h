/*
 * I2C 
 */
extern void  i2c_setup(char deviceAdr);
extern char  i2c_send_byte(char value);
extern void  i2c_send_stop(void);
extern char  i2c_send_start(void);

/*
 * LCD display
 */ 
extern char lcd_display_on(void);
extern char lcd_reset_display(void);

/*
 * framebuffer 
 */
extern char framebuffer_update_display(void);
extern void framebuffer_write_char(char character,char xpos, char ypos);
extern void framebuffer_scroll_up(void);
extern void framebuffer_clear(void);

/*
 * Utility methods
 */
extern void util_msleep(char millis);
extern void debug_green_led(char enable);
extern void debug_red_led(char enable);
extern void debug_blink_red(char count);