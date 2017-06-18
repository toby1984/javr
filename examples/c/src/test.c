#include <mylib.h>
#include <framebuffer.h>

#define NULL (void*) 0
#define true 1
#define false 0

/*
 * Functions
 */

void sleep_one_sec(void);

static char buffer[256];

/*
 *  Globals
 */    

short receive(char* data[],unsigned short arraySize) 
{
    short recv = uart_receive( &buffer[0], sizeof( buffer ) );
    if ( recv <= 0 ) {
      return recv;  
    }
    // replace all \r\n with 0-bytes
    for ( short i = 0 ; i < recv ; i++ ) 
    {
      char c = buffer[i];
      if ( c < 32 ) {
        buffer[i] = 0;
      }
    }      
    
    char *start = 0;  
    short idx = 0;
    for ( unsigned short i = 0 ; i < recv ; i++ ) 
    {
        unsigned char c = buffer[i];
        if ( c ) {
          if ( c > 64 && c < 91 ) {
            buffer[i] = c + 32; 
          }
          if ( ! start ) {
            start = &buffer[i];              
          }                           
        } else { // c == 0 
            if ( start ) {
              if ( idx == arraySize) {
                  return -5; // buffer overflow 
              }
              data[idx++] = start;
              start = 0;
            }
        }
    }
    return idx;
}

unsigned char equals(char *c1,char *c2) {
      while ( *c1 && *c2 ) 
      {
        if ( *c1++ != *c2++ ) {
            return 0;
        }
      }
      return 1;
}

void send(char *data) {
    unsigned short len = 0;
    for ( char *ptr=data ; *ptr != 0 ; ptr++) {
      len++;  
    }
    uart_send(data,len);  
}

unsigned char send_at_cmd(char *cmd) 
{
    char *temp[5];  
    unsigned char retryCnt = 6;
    
    while ( retryCnt-- > 0 ) 
    {  
      send( cmd );
    
      short cnt = receive(&temp,5);
      if ( cnt > 0 ) 
      {
        for ( int i = 0 ; i < cnt ; i++ ) {
          print("got: ");
          println( temp[i] );
        }
        if ( cnt == 1 && equals("ok",temp[0]) ) 
        {
          return 1;
        }
      } else {
        print("err: ");  
        print_hex( (cnt>>8) & 0xff );
        println_hex( cnt & 0xff );
      }
      framebuffer_update_display();
      sleep_one_sec();        
      sleep_one_sec();       
    }
    return 0;    
}

int main() 
{  
  sleep_one_sec();  
  
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
  
  sleep_one_sec();  
  
  uart_setup();    
  
  println("ready");
  framebuffer_update_display();   
          
  // send( "AT+UART=38400,8,1,0,0\r\n" );  
    
  uart_set38k4();
  
  char errCode=1;
  println("echo off");
  if ( send_at_cmd( "ATE0\r\n" ) ) 
  {
    println("mux on");
    if ( send_at_cmd("AT+CIPMUX=1\r\n") ) 
    {
        println("server on");
        if ( send_at_cmd("AT+CIPSERVER=1") ) {
          errCode = 0;
        } else {
          errCode = 3;
        }
    } else {
      errCode = 2;  
    }
  }
  print("result: ");    
  print_hex( errCode );
  framebuffer_update_display();    
  
  char *temp[5];   
  while ( errCode == 0 ) 
  {
        sleep_one_sec();  
        send("AT+CIPSTATUS\r\n");
        
        short code = receive( &temp , 5 );
        if ( code > 0 ) 
        {
            for ( short i = 0 ; i < code ; i++ ) {
              println( temp[i] );  
            }
        } else {
            println("error");
        }
  }
    
    
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

void sleep_one_sec(void) 
{
  for ( unsigned char i = 5 ; i > 0  ; i--) {
    util_msleep(200);    
  }
}
