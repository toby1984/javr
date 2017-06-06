#include <mylib.h>
#include <framebuffer.h>

#define NULL (void*) 0
#define true 1
#define false 0

static unsigned short ir_buffer[200];

/*
 * Functions
 */

void sleep_one_sec(void);

/*
 *  Globals
 */    

int main() 
{  
    // debug_blink_red(100);
  
  i2c_setup( LCD_ADR );
  
  if ( ! lcd_reset_display() ) {
    debug_blink_red(2);
    goto error;    
  }
  
  if ( ! lcd_display_on() ) {
    debug_blink_red(3);
    goto error;
  }
  
  framebuffer_clear();        
  println("ready");
  framebuffer_update_display();    
  
  /*
  adc_setup( ADC_TEMP );
  
  while ( 1 ) {
    unsigned short value = adc_read();
    print_hex( (value >> 8 ) & 0xff );
    print_hex( value & 0xff );
    print( "..." );
    framebuffer_update_display();       
    sleep_one_sec();
  }
  debug_blink_red(250);
  debug_red_led_on();
  debug_green_led_on();
  */
  error:
  while (1 ) {
  }
}

void sleep_one_sec(void) {
  util_msleep(200);    
  util_msleep(200);   
  util_msleep(200);    
  util_msleep(200);   
  util_msleep(200);    
}
