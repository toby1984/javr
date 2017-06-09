#include <mylib.h>
#include <framebuffer.h>

#define NULL (void*) 0
#define true 1
#define false 0

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
  
    char data[8] = { 'A', 'T', '+' , 'G' , 'M' , 'R' , 13 ,  10 };
    uart_setup();
    uart_send( &data[0] , 8 );
    
  
//   i2c_setup( LCD_ADR );
//   
//   if ( ! lcd_reset_display() ) {
//     debug_blink_red(2);
//     goto error;    
//   }
//   
//   if ( ! lcd_display_on() ) {
//     debug_blink_red(3);
//     goto error;
//   }
//   
//   framebuffer_clear();        
//   println("ready");
//   framebuffer_update_display();    
//   
//   while ( 1 ) 
//   {
//     framebuffer_clear();  
//     cursor_home();
//     
//     short value = si7021_read_temperature();
//     float tmp = ((175.72*value)/65536)-46.85;    
//     print_float( tmp );
//     println( " c" );
// 
//     value = si7021_read_humidity();
//     tmp = ((125.0*value)/65536)-6;    
//     print_float( tmp );
//     println( " %" );
//     
//     framebuffer_update_display();       
//     sleep_one_sec();
//     sleep_one_sec();
//   }
  
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
