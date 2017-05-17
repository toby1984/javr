#include "src/mystuff.h"

#define LCD_ADR 0b01111000

#define DISPLAY_WIDTH_IN_PIXEL 128
#define DISPLAY_HEIGHT_IN_PIXEL 64

#define GLYPH_WIDTH_IN_BITS 8 
#define GLYPH_HEIGHT_IN_BITS 8

#define COLUMNS (DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS)
#define ROWS (DISPLAY_HEIGHT_IN_PIXEL/GLYPH_HEIGHT_IN_BITS) 

void framebuffer_write_string(char *string,int x,int y);
void print_hex(char value);
void print(char* s);

static char hexChars[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
static char cursorx=0;
static char cursory=0;

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
        
        framebuffer_clear();              

        for ( int i = 255 ; i >= 0 ; i--) {
          print_hex( i );       
          framebuffer_update_display();    
          util_msleep(64);
        }         

//         framebuffer_scroll_up();
//         framebuffer_update_display();   
error:
        while (1 ) {
        }
}

void print_hex(char value) 
{
    char buffer[3];
    
    buffer[0] = hexChars[ ( value & 0xf0) >> 4 ];
    buffer[1] = hexChars[ ( value & 0x0f)      ];    
    buffer[2]=0;
    print( &buffer[0] );
}

void print(char* s) {
    framebuffer_write_string(s,cursorx,cursory);
}

void framebuffer_write_string(char *string,int x,int y) {

      int currentX = x;
      int currentY = y;
   
      while ( *string ) 
      {
         framebuffer_write_char( *string++, currentX, currentY);
         if ( ++currentX >= COLUMNS ) {
            currentX=0;
            if ( ++currentY >= ROWS ) {
              framebuffer_scroll_up();
              currentY = ROWS-1;
            }
         }  
      }
      cursorx = currentX;
      cursory = currentY;
}
