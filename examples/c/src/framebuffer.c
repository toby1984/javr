#include <mylib.h>
#include <framebuffer.h>

static unsigned char cursorx=0;
static unsigned char cursory=0;

static char hexChars[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

#define UPPER_TO_LOWER 

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

void print_char(unsigned char c) 
{
    if ( c != 0x0d ) 
    {
        if ( c == 0x0a ) {
          linefeed();
        } 
        else 
        {
            if ( c >= 'A' && c <= 'Z' ) {
                c += 32;
            } 
            if ( cursorx >= COLUMNS ) {
              cursorx=0;
              if ( ++cursory >= ROWS ) {
                framebuffer_scroll_up();
                cursory = ROWS-1;
              }
            }
            framebuffer_write_char( c , cursorx++, cursory);              
        }          
    }
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
#ifdef UPPER_TO_LOWER    
    char c = *string++;
    if ( c >= 'A' && c <= 'Z' ) {
      c += 32;  
    }
    framebuffer_write_char( c , currentX++, currentY);
#else
    framebuffer_write_char( *string++, currentX++, currentY);
#endif    
  }
  cursorx = currentX;
  cursory = currentY;
}

void cursor_home(void) {
    cursorx = cursory = 0;
}

void println_float(float f) 
{
    print_float(f);
    linefeed();
}

void print_float(float f) {
    char buffer[8]; // +ddd.ff
    unsigned char len = sprint_float(f,&buffer[0],8);
    buffer[len]=0;    
    print( &buffer[0] );        
}

unsigned char sprint_float(float f,char *buffer,unsigned char buffersize) 
{
    if ( buffersize < 7 ) {
       return 0; 
    }
    buffer[0] = '+';
    buffer[4] = '.';
    buffer[7] = 0;

    int value = f*100;
    if ( f < 0 ) {
        buffer[0] = '-';
        value = -f*100;
    }
    
    int tmp = value / 10000;
    buffer[1] = (char) ('0'+tmp);
    value -= (tmp*10000);
    
    tmp = value / 1000;
    buffer[2] = (char) ('0'+tmp);
    value -= (tmp*1000);
    
    tmp = value / 100;
    buffer[3] = (char) ('0'+tmp);
    value -= (tmp*100);
    
    tmp = value / 10;
    buffer[5] = (char) ('0'+tmp);
    value -= (tmp*10);
    
    buffer[6] = (char) ('0'+value);
    return 7;
}