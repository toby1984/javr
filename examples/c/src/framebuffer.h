#ifndef FRAMEBUFFER_H
#define FRAMEBUFFER_H

#define LCD_ADR 0b01111000

#define DISPLAY_WIDTH_IN_PIXEL 128
#define DISPLAY_HEIGHT_IN_PIXEL 64

#define GLYPH_WIDTH_IN_BITS 8 
#define GLYPH_HEIGHT_IN_BITS 8

#define COLUMNS (DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS)
#define ROWS (DISPLAY_HEIGHT_IN_PIXEL/GLYPH_HEIGHT_IN_BITS) 

void framebuffer_write_string(char *string,char x,char y);

void linefeed(void);

void print_hex(unsigned char value);
void print_hex_nibble(unsigned char value);
void println_hex(unsigned char value);

void print_char(unsigned char c);

void print(char* s);
void println(char* s);

void print_dec(unsigned char value);

void cursor_home(void);

void print_float(float f);

// buffer NEEDS to be at least 7 characters in size
unsigned char sprint_float(float f,char *buffer,unsigned char buffersize);
void println_float(float f);

#endif