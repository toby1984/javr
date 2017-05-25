
#ifndef PS2_H
#define PS2_H

// #define DEBUG_UNMAPPED_SCANCODES

enum KeyState {
  AWAIT_ANY, // awaiting key sequence
  AWAIT_BREAK_F0,
  AWAIT_BREAK_E0,
  AWAIT_BREAK_E1,
  RECEIVED_E0, // last seen byte was E0
  RECEIVED_E1, // last received byte was F0    
};

#define MYCODE_NONE 0xff

#define SHIFT_LEFT_MASK (1<<0)
#define SHIFT_RIGHT_MASK (1<<1)

#define SHIFT_MASK (SHIFT_LEFT_MASK|SHIFT_RIGHT_MASK)

#define CTRL_LEFT_MASK (1<<2)
#define CTRL_RIGHT_MASK (1<<3)

#define CTRL_MASK (CTRL_LEFT_MASK|CTRL_RIGHT_MASK)

#define ALT_LEFT_MASK (1<<4)
#define ALT_RIGHT_MASK (1<<5)

#define ALT_MASK (ALT_LEFT_MASK|ALT_RIGHT_MASK)

#define SCANCODE_CTRL 0x14
#define SCANCODE_ALT 0x11
#define SCANCODE_SHIFT_LEFT 0x12
#define SCANCODE_SHIFT_RIGHT 0x59

void handle_keybuffer(void);
void key_released(char scancode,char prevbyte);
void key_pressed(char scancode,char prevbyte);
char is_pressed(char scancode);

#endif