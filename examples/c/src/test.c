#include <mylib.h>
#include <framebuffer.h>

#define NULL (void*) 0
#define true 1
#define false 0

static unsigned short buffer[200];

/*
 * Functions
 */

void sleep_one_sec(void);

/*
 *  Globals
 */    

int main() 
{  
  i2c_setup( LCD_ADR );
  
  if ( ! lcd_reset_display() ) {
    debug_blink_red(2);
    goto error;    
  }
  
  if ( ! lcd_display_on() ) {
    debug_blink_red(3);
    goto error;
  }
  
  ir_setup();
  
  framebuffer_clear();        
  framebuffer_update_display();    
  
  while ( true ) {
      char received = ir_receive( &buffer[0] , 199 );
      if ( received == 0xff || received == 0xfe) {
          print("error: ");
          println_hex( received );
          framebuffer_update_display();      
          debug_blink_red(1);
      } else if ( received > 0 ) {
          println("pulses: ");
          println_hex( received );
          
          unsigned short last = buffer[0];
          char count = 0;
          for ( int i = 1 ; i < received ; i++ ) {
              if ( buffer[i] == last ) {
                count++;
              } else {
                  print_dec( count );
                  print("x");
                  if ( (char) (last & 0xff00) == (char) 0 ) {
                     print_hex( (char) (last & 0xff));
                  } else {
                    print_hex( (char) (last & 0xff) >> 8 );
                    print_hex( (char) (last & 0xff) );
                  }
                  print(",");
                  count = 1;
                  last = buffer[i];
              }
          }
          
          framebuffer_update_display();           
      }
  }
  
  
  error:
  while (1 ) {
  }
}

void sleep_one_sec(void) {
  util_msleep(200);    
  util_msleep(200);   
}
