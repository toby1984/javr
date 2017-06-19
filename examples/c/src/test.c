#include <mylib.h>
#include <framebuffer.h>

#define NULL (void*) 0
#define true 1
#define false 0

/*
 * Functions
 */

void sleep_one_sec(void);
void sleep_seconds(unsigned char secs);

unsigned char strcpy(char *src,char*dst);
void serve_request(void);

static char buffer[256];
static char buffer2[50];

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

unsigned char starts_with_ignore_case(char *string, char *expected) {
  
      while ( *string ) 
      {
        if ( *expected == 0 ) {
          return 1;
        }
        char c1 = *string++;
        char c2 = *expected++;
        if ( c1 >= 'A' && c1 <= 'Z' ) {
          c1 += 32;  
        }
        if ( c2 >= 'A' && c2 <= 'Z' ) {
          c2 += 32;  
        }        
        if ( c1 != c2 ) {
            return 0;
        }
      }
      return 0;   
}

void send(char *data) {
    unsigned short len = 0;
    for ( char *ptr=data ; *ptr != 0 ; ptr++) {
      len++;  
    }
    uart_send(data,len);  
}



unsigned char send_at_cmd_retry(char *cmd,unsigned char retryCnt) 
{
    char *temp[5];  
    
    while ( retryCnt-- > 0 ) 
    {        
      send( cmd );
    
      short cnt = receive(&temp,5);
      if ( cnt > 0 ) 
      {
        for ( int i = 0 ; i < cnt ; i++ ) {
          print("got>");
          print( temp[i] );
          println("<");
          if ( equals("ok",temp[i]) ) {
            return 1;  
          }           
          framebuffer_update_display();          
        }
      } else {
        print("err: ");  
        print_hex( (cnt>>8) & 0xff );
        println_hex( cnt & 0xff );
      }
      framebuffer_update_display();
      sleep_one_sec();        
    }
    return 0;    
}

unsigned char send_at_cmd(char *cmd)  {
   return send_at_cmd_retry(cmd,6); 
}

unsigned char send_at_cmds(char *cmds[],unsigned char count) 
{
    for ( unsigned char i= 0 ; i < count ; i++) 
    {
      print("send: ");
      println( cmds[i] );
      linefeed();
      if ( ! send_at_cmd( cmds[i] ) )  {
        return 0;
      }
      framebuffer_update_display();         
    }
    return 1;  
}

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
  framebuffer_update_display();   
  
  sleep_seconds(3);  
  
  uart_setup();    
  
  println("ready");
  framebuffer_update_display();   
          
  send( "AT+UART=38400,8,1,0,0\r\n" );  
    
  sleep_one_sec();  
  sleep_one_sec();  
  
  uart_set38k4();
  
  char *cmds1[] = { "ATE0\r\n",
                    "AT+CWMODE_CUR=1\r\n" };
                   
  unsigned char errCode=send_at_cmds( cmds1 , 2 ); 
  
  send_at_cmd_retry("AT+CWJAP=\"Black hole 24\",\"2l33t2sp3ak\"\r\n",1);
  
  sleep_one_sec();  
  sleep_one_sec();  
  sleep_one_sec();  
  
  char *cmds2[] = { "AT+CIPMUX=1\r\n",
                    "AT+CIPSERVER=1,1001\r\n"
                    // ,"AT+SLEEP=1\r\n"
  };
                 
  errCode=send_at_cmds( cmds2 , 2 ); 
                                
  print("result: ");    
  print_hex( errCode );
  framebuffer_update_display();    
  
  char *temp[5];   
outer:
  while ( errCode ) 
  {
        framebuffer_update_display();      
        sleep_one_sec();  
        send("AT+CIPSTATUS\r\n");
        
        short code = receive( &temp , 5 );
        if ( code > 0 ) 
        {
            for ( short i = 0 ; i < code ; i++ ) {
              if ( starts_with_ignore_case( temp[i] , "+CIPSTATUS:0" ) ) 
              {                
                println("connected!");                
                serve_request();
                goto outer;  
              } 
              println( temp[i] );
            }
            println("no con");
        } else {
            println("error");
        }
  }
  
  error:
  while (1 ) {
  }
}

unsigned char strcpy(char *src,char*dst) 
{
  unsigned char len = 0;
  while ( *src ) {
    *dst++ = *src++;  
    len++;
  }  
  return len;
}

void serve_request() 
{
    char *ptr = &buffer2[0];
    
    // read temperature
    short value = si7021_read_temperature();
    float tmp = ((175.72*value)/65536)-46.85;    
    
    // append temperature to buffer
    ptr += sprint_float( tmp , ptr , sizeof(buffer2) );
    ptr += strcpy( " c\n" , ptr );
    
    // read humidity
    value = si7021_read_humidity();
    tmp = ((125.0*value)/65536)-6;    
    
    // append humidity to buffer
    ptr += sprint_float( tmp , ptr , sizeof(buffer2) );
    ptr += strcpy(" %\n",ptr);
    
    // tell ESP8266 to send data
    send("AT+CIPSENDEX=0,20\r\n");    
    sleep_seconds(3);     
    uart_send(&buffer2[0],20);     
    
    // close connection afterwards
    sleep_seconds(3);
    send("AT+CIPCLOSE=0\r\n");
    sleep_one_sec();     
}

void sleep_seconds(unsigned char secs) 
{
  for ( unsigned char i = secs ; i > 0 ; i-- ) {
    sleep_one_sec();
  }
}
  

void sleep_one_sec(void) 
{
  for ( unsigned char i = 5 ; i > 0  ; i--) {
    util_msleep(200);    
  }
}
