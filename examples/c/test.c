#include "src/mystuff.h"

#define LCD_ADR 0b01111000

#define DISPLAY_WIDTH_IN_PIXEL 128
#define DISPLAY_HEIGHT_IN_PIXEL 64

#define GLYPH_WIDTH_IN_BITS 8 
#define GLYPH_HEIGHT_IN_BITS 8

#define ROWS (DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS)
#define COLUMNS (DISPLAY_HEIGHT_IN_PIXEL/GLYPH_HEIGHT_IN_BITS) 

void framebuffer_write_string(char *string,int x,int y);

static int cursorx=0;
static int cursory=0;

int main() 
{
        i2c_setup( LCD_ADR );
        
        if ( ! lcd_reset_display() ) {
            debug_blink_red(3);
            goto error;
        }
        
        if ( ! lcd_display_on() ) {
            debug_blink_red(4);
            goto  error;
        }
        
        framebuffer_clear();
        
        //framebuffer_update_display();        
                
        framebuffer_write_char( 'x' , 0 , 0 );        
        framebuffer_write_char( 'X' , 1 , 1 );        
        framebuffer_write_char( 'Z' , 2 , 2 );        
        framebuffer_write_char( 'z' , 3 , 3 );        
        
        // framebuffer_write_string("test",0,0);          
        
        framebuffer_update_display();
    
        debug_green_led(1);                
error:        
forever:        
        while(1) {
        }
}

void framebuffer_write_string(char *string,int x,int y) {

      int currentX = cursorx;
      int currentY = cursory;
   
      while ( *string ) 
      {
         framebuffer_write_char( *string++, currentX, currentY);
         if ( ++currentX == COLUMNS ) {
            currentX=0;
            if ( ++currentY == ROWS ) {
              framebuffer_scroll_up();
              currentY = ROWS-1;
            }
         }  
      }
      cursorx = currentX;
      cursory = currentY;
}
