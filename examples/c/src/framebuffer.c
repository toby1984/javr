#include <mylib.h>
#include <framebuffer.h>

static unsigned char cursorx=0;
static unsigned char cursory=0;

static char hexChars[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

/*
 * Writes a byte value as decimal string to the display.
 */
void print_dec(unsigned char value) {
  
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
void print_hex(unsigned char value) 
{
  char buffer[3];
  
  buffer[0] = hexChars[ ( value & 0xf0) >> 4 ];
  buffer[1] = hexChars[ ( value & 0x0f)      ];    
  buffer[2]=0;
  print( &buffer[0] );
}

void print_hex_nibble(unsigned char value) 
{
  char buffer[2];
  
  buffer[0] = hexChars[ ( value & 0x0f) ];
  buffer[1]=0;
  print( &buffer[0] );
}

/*
 * Writes a byte value as hexadecimal string to the display.
 */
void println_hex(unsigned char value)  {
  print_hex(value);
  linefeed();
}

/*
 * Outputs a line feed.
 */
void linefeed() 
{    
  cursorx=0;
  if ( ++cursory >= ROWS ) {
    framebuffer_scroll_up();
    cursory = ROWS-1;
  }    
}

/*
 * Writes a NULL-terminated string to the display.
 */
void print(char* s) {
  framebuffer_write_string(s,cursorx,cursory);
}

/*
 * Writes a NULL-terminated string to the display and
 * issues a linefeed afterwards.
 */
void println(char* s) {
  framebuffer_write_string(s,cursorx,cursory);
  linefeed();
}

/*
 * Writes a NULL-terminated string at a given (column,row) position.
 */
void framebuffer_write_string(char *string,char x,char y) {
  
  char currentX = x;
  char currentY = y;
  
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