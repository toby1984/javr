#include "src/mystuff.h"

#define LCD_ADR 0b01111000

#define DISPLAY_WIDTH_IN_PIXEL 128
#define DISPLAY_HEIGHT_IN_PIXEL 64

#define GLYPH_WIDTH_IN_BITS 8 
#define GLYPH_HEIGHT_IN_BITS 8

#define COLUMNS (DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS)
#define ROWS (DISPLAY_HEIGHT_IN_PIXEL/GLYPH_HEIGHT_IN_BITS) 

void framebuffer_write_string(char *string,int x,int y);

void linefeed(void);

void print_hex(char value);
void println_hex(char value);

void print(char* s);
void println(char* s);

void print_dec(char value);
    
static char hexChars[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
static char cursorx=0;
static char cursory=0;
static char keyboard_buffer[128];

int main() 
{
        char last_error;
        char bytes_read;
        
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
        framebuffer_update_display();    

        ps2_reset();
        framebuffer_clear();  
        println("waiting...");        
        framebuffer_update_display();          
        while ( 1 ) 
        {
            last_error = ps2_get_last_error();
            if ( last_error !=  0 ) {
                    print("error: ");                       
                    println_hex( last_error );
                    framebuffer_update_display();                  
            }
            
            bytes_read = ps2_keybuffer_read( &keyboard_buffer[0] , sizeof(keyboard_buffer) );
                     
            if ( bytes_read > 0 ) {
                    print("received ");     
                     for ( char ptr = 0 ; ptr < bytes_read ; ptr++) {
                       println_hex( keyboard_buffer[ptr] );
                     }
                    framebuffer_update_display();                    
            }                
        }        
error:
        while (1 ) {
        }
}

void print_dec(char value) {
          
    char buffer[4];
    int ptr = 0;
    
    if ( value >= 100 ) {
        buffer[ptr++] = '0' + (value/100);
        value -= (value/100)*100;
    }
    if ( value >= 10 ) {
        buffer[ptr++] = '0' + (value/10);
        value -= (value/10)*10;
    }    
    buffer[ptr++] = '0' + value;
    buffer[ptr++] = 0;
    
    print( &buffer[0] );    
}

/*
 * Writes a byte value as hexadecimal string to the display.
 */
void print_hex(char value) 
{
    char buffer[3];
    
    buffer[0] = hexChars[ ( value & 0xf0) >> 4 ];
    buffer[1] = hexChars[ ( value & 0x0f)      ];    
    buffer[2]=0;
    print( &buffer[0] );
}

/*
 * Writes a byte value as hexadecimal string to the display.
 */
void println_hex(char value)  {
    print_hex(value);
    linefeed();
}

void linefeed() 
{    
    cursorx=0;
    if ( ++cursory >= ROWS ) {
        framebuffer_scroll_up();
        cursory = ROWS-1;
    }    
}


void print(char* s) {
    framebuffer_write_string(s,cursorx,cursory);
}

void println(char* s) {
    framebuffer_write_string(s,cursorx,cursory);
    linefeed();
}

void framebuffer_write_string(char *string,int x,int y) {

      int currentX = x;
      int currentY = y;
   
      while ( *string ) 
      {
         if ( currentX >= COLUMNS ) {
            currentX=0;
            if ( ++currentY >= ROWS ) {
              framebuffer_scroll_up();
              currentY = ROWS-1;
            }
         }            
         framebuffer_write_char( *string++, currentX++, currentY);
      }
      cursorx = currentX;
      cursory = currentY;
}
