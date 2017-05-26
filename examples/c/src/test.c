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
      
      util_msleep(200);
      unsigned char received = ir_receive( &ir_buffer[0] , 50 );
      if ( received == 0xff || received == 0xfe) {
          print("error: ");
          println_hex( received );
          framebuffer_update_display();      
          debug_blink_red(1);
      } 
      else if ( received != 0 ) 
      {
          linefeed();
          print("pulses: ");
          print_dec( received );
          linefeed();
          if ( received == 32 ) 
          {
            for ( unsigned char i = 0 ; i < received ; i+=8 ) 
            {
                unsigned char decoded = 0;
                for ( unsigned char bit = 0 ; bit < 8 && (i+bit) < received ; bit++) 
                {
                  unsigned char value = ir_buffer[i+bit] & 0xff;                
                  decoded <<= 1;
                  if ( value > 0x05 && value <= 0x11 ) {
                    decoded |= 1;
                  }
                }
                print_hex( decoded );
            }
            linefeed();              
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
