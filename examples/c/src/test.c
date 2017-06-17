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

void receive() 
{
    short recv = uart_receive( &buffer[0], sizeof( buffer ) );
    print("status: ");
    print_hex( (recv >> 8) & 0xff);
    println_hex(  recv & 0xff);    
    if ( recv > 0 ) {
      
      for ( short i = 0 ; i < recv ; i++ ) {
        print_hex( buffer[i] );
        print_char(',');
//         print_char( buffer[i] );
      }
    }
    framebuffer_update_display();        
}

void print_buffer(char *buffer,unsigned short len) {
    
  for ( unsigned short i = 0 ; i <len ; i++) { 
    
  }  
}

void send(char *data) {
    unsigned short len = 0;
    for ( char *ptr=data ; *ptr != 0 ; ptr++) {
      len++;  
    }
    uart_send(data,len);  
}

int main() 
{  
    // debug_blink_red(100);  
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
  println("ready");
  framebuffer_update_display();
  
  sleep_one_sec();  
  
  uart_setup();    
   
  println("done");
  framebuffer_update_display();   
      
    // send( "AT+CWMODE=1\r\n" );
    
    uart_set38k4();
    
    send( "AT+GMR\r\n" );  
    
    // send( "AT+UART_CUR=38400,8,1,0,0\r\n" );    

    // uart_set38k4();
    
    receive();
    
    
    
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
