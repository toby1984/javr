#include <ps2.h>
#include <mylib.h>
#include <framebuffer.h>

static enum KeyState keystate=AWAIT_ANY;

static char *mycode = "\nabcdefghijklmnopqrstuvwxyz [(8])9!1\"2'#$4%5&6*+;,-./7=03:><?";

static char keyboard_buffer[128];

static char pressed_keys[16];
static char pressed_keys_count=0;

static char modifiers=0;

/**
 * Converts scancode + modifier mask into mycode (which is an ascii subset)
 * @return mycode or MYCODE_NONE if no mapping exists
 */
char scancode_to_mycode(char scancode,char modifiers) {
  
  switch( scancode ) 
  {
    case 0x5a: return 0; // ENTER , \n
    case 0x1c: return 1; // a
    case 0x32: return 2; // b
    case 0x21: return 3; // c
    case 0x23: return 4; // d 
    case 0x24: return 5; // e
    case 0x2b: return 6; // f
    case 0x34: return 7; // g
    case 0x33: return 8; // h
    case 0x43: return 9; // i
    case 0x3b: return 10; // j 
    case 0x42: return 11; // k
    case 0x4b: return 12; // l
    case 0x3a: return 13; // m
    case 0x31: return 14; // n
    case 0x44: return 15; // o
    case 0x4d: return 16; // p
    case 0x15: return 17; // q
    case 0x2d: return 18; // r
    case 0x1b: return 19; // s
    case 0x2c: return 20; // t
    case 0x3c: return 21; // u
    case 0x2a: return 22; // v
    case 0x1d: return 23; // w
    case 0x22: return 24; // x
    case 0x35: return 25; // y
    case 0x1a: return 26; // z
    case 0x29: return 27; // <WHITESPACE>
    case 0x3e: 
      if ( (modifiers & ALT_RIGHT_MASK ) != 0 ) {
        return 28; // '['       
      }  else if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 29; // '('
      } 
      return 30; // '8'           
    case 0x46:
      if ( (modifiers & ALT_RIGHT_MASK ) != 0 ) {
        return 31; // ']'
      }  else if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 32; // '('
      } 
      return 33; // '9'
    case 0x16:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 34; // '!'
      } 
      return 35; // '1'
    case 0x1e:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 36; // '"'
      } 
      return 37; // '2'
    case 0x5d:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 38; // '''
      } 
      return 39; // '#'
    case 0x2e:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 42; // '%'  
      }
      return 43; // '5'
    case 0x36:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 44; // '&'  
      }
      return 45; // '6'   
    case 0x5b: // TODO: Wrong keycode , find out scancode that maps to '*'
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 46; // '*'
      }
      return 47; // '+'
    case 0x41:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 48; // ';'
      }
      return 49; // ','    
    case 0x4a:
      return 50; // '-'
    case 0x49:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 57; // ':' 57 is ok, I screwed up the ordering
      }            
      return 51; // '.'
    case 0x3d:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 52; // '/'
      }
      return 53; // '7'      
    case 0x45:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 54; // '='
      }
      return 55; // '0'     
    case 0x26:
      return 56; // '3'
      // careful, screw-up above, 57 is already used            
    case 0x61:
      if ( ( modifiers & SHIFT_MASK) != 0 ) {
        return 58; // '>'
      }            
      return 59; // '<'       
    case 0x4e:
      return 60;
    default:
      #ifdef DEBUG_UNMAPPED_SCANCODES            
      linefeed();
      print("unmapped: ");
      println_hex( scancode );
      framebuffer_update_display();
      #endif            
      return MYCODE_NONE;
  }
}

void handle_keybuffer(void) 
{        
  char last_error = ps2_get_last_error();
  if ( last_error !=  0 ) {
    print("error: ");                       
    println_hex( last_error );
    framebuffer_update_display();                  
  }
  
  char bytes_read = ps2_keybuffer_read( &keyboard_buffer[0] , sizeof(keyboard_buffer) );
  
  enum KeyState newstate = AWAIT_ANY;
  for ( char i = 0 ; i < bytes_read ; i++ ) 
  {
    char current = keyboard_buffer[i];
    switch( keystate ) 
    {
      case AWAIT_ANY:
        if ( current == 0xf0 ) { // BREAK initiated
          newstate = AWAIT_BREAK_F0;
        } else if ( current == 0xe0 ) {
          newstate = RECEIVED_E0;
        } else if ( current == 0xe1 ) {
          newstate = RECEIVED_E1;
        } else {
          // MAKE received
          key_pressed( current , 0 );
          newstate = AWAIT_ANY;
        }
        break;
      case RECEIVED_E0: // we previously received 0xF0
        if ( current == 0xf0 ) {
          newstate = AWAIT_BREAK_E0;   
        } else {
          key_pressed( current , 0xe0 );
          newstate = AWAIT_ANY; 
        }          
        break;
      case RECEIVED_E1: // we previously received 0xF0                
        if ( current == 0xf0 ) {
          newstate = AWAIT_BREAK_E1;   
        } else {
          key_pressed( current , 0xe1 );
          newstate = AWAIT_ANY; 
        }  
        break;
      case AWAIT_BREAK_F0:
        key_released( current, 0xf0 );
        newstate = AWAIT_ANY;
      case AWAIT_BREAK_E0:
        key_released( current, 0xE0 );
        newstate = AWAIT_ANY;
      case AWAIT_BREAK_E1:
        key_released( current, 0xE1 );
        newstate = AWAIT_ANY;        
        break;        
    }
    keystate = newstate;            
  }
}

char is_pressed(char scancode) 
{   
  for ( char i = 0 ; i < pressed_keys_count ; i++ ) 
  {
    if ( pressed_keys[i] == scancode) {
      return 1;
    }
  }
  return 0;
}

char get_modifiers(char scancode, char prevbyte) 
{
  if ( scancode == SCANCODE_ALT ) 
  {
    return prevbyte == 0xe0 ? ALT_RIGHT_MASK : ALT_LEFT_MASK;
  } 
  if ( scancode == SCANCODE_CTRL ) 
  {
    return prevbyte == 0xe0 ? CTRL_RIGHT_MASK: CTRL_LEFT_MASK;
  }   
  if ( scancode == SCANCODE_SHIFT_LEFT ) {
    return SHIFT_LEFT_MASK;
  } 
  if ( scancode == SCANCODE_SHIFT_RIGHT ) {
    return SHIFT_RIGHT_MASK;
  }
  return 0;
}

void key_pressed(char scancode,char prevbyte) {
  
  char mask = get_modifiers( scancode , prevbyte );
  if ( mask != 0 ) 
  {
    modifiers |= mask;
    return;
  }
  char translated = scancode_to_mycode( scancode , modifiers );
  if ( translated == MYCODE_NONE ) { // Ignore anything we don't care about
    return;
  }
  if ( pressed_keys_count < sizeof( pressed_keys ) )
  {
    for ( char i = 0 ; i < pressed_keys_count ; i++ ) 
    {
      if ( pressed_keys[i] == translated) {
        return;
      }
    }
    pressed_keys[ pressed_keys_count++ ] = translated;
  }
}

void key_released(char scancode,char prevbyte) {
  
  char mask = get_modifiers( scancode , prevbyte );
  if ( mask != 0 ) 
  {
    modifiers &= ~mask;
    return;
  }
  
  char translated = scancode_to_mycode( scancode , modifiers );
  if ( translated == MYCODE_NONE ) {
    return; // ignore anything we don't care about
  }
  for ( char i = 0 ; i < pressed_keys_count ; i++ ) 
  {
    if ( pressed_keys[i] == translated ) 
    {
      // shift right                
      for ( char j = i+1  ; j < pressed_keys_count ; i++,j++) 
      {
        pressed_keys[i] = pressed_keys[j];
      }
      pressed_keys_count--;                
      return;
    }
  }
}