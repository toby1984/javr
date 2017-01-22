
.equ test = 3

#include "m328Pdef.inc"

.equ freq = 16000000 ; Hz
.equ target_freq = 100000 ; Hz

.equ cycles_per_us = freq / 1000000 ; 1 us = 10^-6 s

.equ delay_in_cycles = (freq / target_freq)/2

; I/O pins
#define TRIGGER_PIN 0
#define SCOPE_PIN 2
#define DISPLAY_RESET_PIN 4
#define SUCCESS_LED 6
#define ERROR_LED 7

; Handy macros
#define SCOPE_PIN_ON  sbi PORTD, SCOPE_PIN
#define SCOPE_PIN_OFF cbi PORTD, SCOPE_PIN

#define SUCCESS_LED_ON  sbi PORTD, SUCCESS_LED
#define SUCCESS_LED_OFF cbi PORTD, SUCCESS_LED

#define ERROR_LED_ON  sbi PORTD, ERROR_LED
#define ERROR_LED_OFF cbi PORTD, ERROR_LED

#define DISPLAY_I2C_ADDR %01111000
#define DISPLAY_WIDTH_IN_PIXEL 128
#define DISPLAY_HEIGHT_IN_PIXEL 64

#define GLYPH_WIDTH_IN_BITS 8 
#define GLYPH_HEIGHT_IN_BITS 8

.equ GLYPH_WIDTH_IN_BYTES = GLYPH_WIDTH_IN_BITS/8
.equ BYTES_PER_GLYPH = (GLYPH_WIDTH_IN_BITS*GLYPH_HEIGHT_IN_BITS)/8

.equ BYTES_PER_ROW = DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS

.equ FRAMEBUFFER_SIZE = (DISPLAY_WIDTH_IN_PIXEL*DISPLAY_HEIGHT_IN_PIXEL)/8

.equ PAGE_WIDTH_BITS = 8
.equ PAGE_HEIGHT_BITS = 8

.equ HORIZ_PAGES_PER_ROW = DISPLAY_WIDTH_IN_PIXEL/PAGE_WIDTH_BITS
.equ VERT_PAGES_PER_COLUMN = DISPLAY_HEIGHT_IN_PIXEL/PAGE_HEIGHT_BITS

.equ PAGE_SIZE_IN_BYTES = (PAGE_WIDTH_BITS*PAGE_HEIGHT_BITS)/8

; SSD1306 request types

.equ REQ_SINGLE_COMMAND = 0x80
.equ REQ_COMMAND_STREAM = 0x00
.equ REQ_SINGLE_DATA = 0xc0
.equ REQ_DATA_STREAM = 0x40

.equ RAM_END = 32768

; Display commands

#define CMD_DISPLAY_ON 0
#define CMD_ROW_ADDRESSING_MODE 1
#define CMD_PAGE_ADDRESSING_MODE 2

jmp init  ; RESET
jmp onirq ; INT0 - ext IRQ 0
jmp onirq ; INT1 - ext IRQ 1
jmp onirq ; PCINT0 - pin change IRQ 
jmp onirq ; PCINT1 - pin change IRQ
jmp onirq ; PCINT2 - pin change IRQ
jmp onirq ; WDT - watchdog IRQ
jmp onirq ; TIMER2_COMPA - timer/counter 2 compare match A
jmp onirq ; TIMER2_COMPB - timer/counter 2 compare match B
jmp onirq ; TIMER2_OVF - timer/counter 2 overflow
jmp onirq ; TIMER1_CAPT - timer/counter 1 capture event
jmp onirq ; TIMER1_COMPA 
jmp onirq ; TIMER1_COMPB
jmp onirq ; TIMER1_OVF
jmp onirq ; TIMER0_COMPA
jmp onirq ; TIMER0_COMPB
jmp onirq ; TIMER0_OVF
jmp onirq ; STC - serial transfer complete (SPI)
jmp onirq ; USUART Rx complete
jmp onirq ; USUART Data register empty
jmp onirq ; USUART Tx complete
jmp onirq ; ADC conversion complete
jmp onirq ; EEPROM ready
jmp onirq ; Analog comparator
jmp onirq ; 2-wire interface I2C
jmp onirq ; Store program memory ready

; ========================
; HW init
; ========================
init:
; clear status register
          cli
	eor r1,r1
	out 0x3f,r1
; initialize stack pointer
	ldi r28,LOW(RAM_END)
	ldi r29,HIGH(RAM_END)
	out 0x3d,r28	; SPL 
	out 0x3e,r29	; SPH
; call main program
.again	rcall main
	rcall wait_for_button  
          rjmp again
onirq:
	jmp 0x00
; ==========================
; main program starts here
; ==========================
main:
          rcall reset
          
          rcall wait_for_button            

	ldi r16, CMD_DISPLAY_ON
	rcall send_command
          brcs error	

	rcall clear_dirty_pages ; SCRATCHED: r0,r1,r16,r17,r18,r19,r31:r30

	rcall send_framebuffer
	brcs error

	ldi r16,0
	sts dirtyregions,r16

; ============================
; blit sprite from FLASH into framebuffer
; INPUT: r19 - Destination X position (pixel)
; INPUT: r20 - Destination Y position (pixel)
; INPUT: r21 - Sprite width (pixel)
; INPUT: r22 - Sprite height (pixel)
; INPUT: r31:r30 - Ptr to start of sprite in FLASH
; SCRATCHED: r0,r1,r16,r17,r18,r19,r22,r23,r26,r27,r28,r29,r30,r31
; ============================

	ldi r16,10
	sts spritex,r16
	sts spritey,r16
	ldi r16,1
	sts spritedx,r16
	sts spritedy,r16
.loop
	lds r19,spritex 
	lds r16,spritedx
	add r19,r16
	cpi r19,5
	breq invx
	cpi r19,127-16
	breq invx
	rjmp noxborder
.invx
	com r16
.noxborder
	sts spritex,r19
	sts spritedx,r16	

; Y axis
	lds r20,spritey
	lds r16,spritedy
	add r20,r16
	cpi r20,5
	breq invy
	cpi r20,63-16
	breq invy
	rjmp noyborder
.invy
	com r16
.noyborder
	sts spritey,r20
	sts spritedy,r16	
	
	ldi r21, 16
	ldi r22, 16
	ldi r31,HIGH(sprite1)
	ldi r30,LOW(sprite1)
;	rcall blit_sprite

	SCOPE_PIN_OFF

	lds r16,0
	sts cursorx,r16
	sts cursory,r16

	ldi r16,0xff
	sts dirtyregions,r16

;	lds r16,dirtyregions
;	rcall write_dec

; INPUT: r27:r26 - String to print (FLASH)
.check	ldi r27,HIGH(text)
	ldi r26,LOW(text)
	rcall write_flash_string

; write one ASCII glyph into the framebuffer
; INPUT: r16 - character to write
; INPUT: r17 - column (X)
; INPUT: r18 - row (Y)
	ldi r16,'1'
	ldi r17,5
	ldi r18,5
	rcall write_char

	ldi r16,0xff
	sts dirtyregions,r16

	rcall send_framebuffer

	SCOPE_PIN_ON
	brcs error

	rcall clear_dirty_pages ; SCRATCHED: r0,r1,r16,r17,r18,r19,r31:r30

;	rjmp loop

;          SUCCESS_LED_ON
          rjmp back
.error        
	ERROR_LED_ON
.back	rcall wait_for_button  
	ret

; ====
; reset system
; RETURN: Carry clear => succes , Carry set => failure
; =====
reset:
;	cbi DDRD,DISPLAY_RESET_PIN ; set to input
;          sbi PORTD,DISPLAY_RESET_PIN ; Enable pull-up resisstor
	sbi DDRD,DISPLAY_RESET_PIN ; set to output
	sbi DDRD,SCOPE_PIN ; set to output
	sbi DDRD,ERROR_LED ; set to output
	sbi DDRD,SUCCESS_LED ; set to output
	cbi DDRB,TRIGGER_PIN ; set to input

;          setup TWI rate
          lds r16,TWSR
          andi r16,%11111100
          sts TWSR,r16 ; prescaler bits = 00 => factor 1x
          ldi r16,12 ; 400 kHz
;          ldi r16,19 ; 300 kHz
;          ldi r16,32 ; 200 kHz
;          ldi r16,72 ; 100 kHz
          sts TWBR,r16 ; factor

          ERROR_LED_OFF
          SUCCESS_LED_OFF
	SCOPE_PIN_ON

	rcall reset_display
	ret

; ======
; Reset display
; ======
reset_display:
	ldi r16,0
	sts cursorx,r16
	sts cursory,r16

	cbi PORTD, DISPLAY_RESET_PIN
	ldi r16,20
	rcall msleep
	sbi PORTD, DISPLAY_RESET_PIN
	ldi r16,200
	rcall msleep

; mark all regions dirty so display RAM gets written completely
	ldi r16,0xff
	sts dirtyregions,r16

; switch to page addressing mode
	ldi r16,CMD_PAGE_ADDRESSING_MODE
	rcall send_command
	ret
	
; ======
; wait for button press 
; ======
wait_for_button:
	ldi r16,255
	rcall msleep
.wait_released
          sbic PINB , TRIGGER_PIN
          rjmp wait_released
.wait_pressed
          sbis PINB , TRIGGER_PIN
          rjmp wait_pressed
	ldi r16,255
	rcall msleep
          ret

; ====
; send command.
; INPUT: r16 - command table entry index 
; SCRATCHED: r2,r16,r19:r18,r20,r31:r30
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; ====

send_command:
          ldi r31 , HIGH(commands)
          ldi r30 , LOW(commands)
          lsl r16 ; * 4 bytes per table entry ( 16-bit address + 16 bit byte count)
          lsl r16
	eor r2,r2 ; r2 = 0
          add r30,r16
          adc r31,r2
          lpm r16,Z+ ; cmd address low
          lpm r17,Z+ ; cmd address hi
          lpm r28,Z+ ; LOW(number of bytes in cmd )
          lpm r29,Z+ ; HIGH(number of bytes in cmd )
          movw Z,r17:r16 ; Z = r17:r16
          call send_bytes
          ret
          
; ====== send bytes
; Assumption: CLK HI , DATA HI when method is entered
; INPUT: r31:r30 (Z register) start address of bytes to transmit
; INPUT: r29:r28 - number of bytes to transmit
; SCRATCHED: r16,r20,r31:r30
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; ======
send_bytes:
; send START
          rcall send_start
	brcs send_failed
.data_loop
          lpm r16,Z+
          rcall send_byte
          brcs send_failed             
          sbiw r29:r28,1
          brne data_loop          
; transmission successful
	rcall send_stop   
          clc
	ret
.send_failed
	sec
	rcall send_stop       	
          ret

; =====
; send stop
; SCRATCHED: r16,r0,r1,r17,r27:r26
; =============

send_stop:
	ldi r16, (1<<TWINT)|(1<<TWEN)| (1<<TWSTO) ; 1 cycle
	sts TWCR, r16 ; 2 cycles
.wait_tx
	lds r16,TWCR
          andi r16,(1<<TWSTO) 
	brne wait_tx
	ret

; ===================
;  Send Start command and slave address 
; SCRATCHED: r16
; ====================

send_start:
; send START
          ldi r16, (1<<TWINT)|(1<<TWSTA)|(1<<TWEN) 
          sts TWCR, r16
; wait for START transmitted
.wait_start
          lds r16,TWCR
          sbrs r16,TWINT 
          rjmp wait_start

; check for transmission error
          lds r16,TWSR
          andi r16, 0xF8 
	cpi r16, 0x08 ; 0x08 = status code: START transmitted 
	brne send_failed
; transmit address (first byte)
          ldi r16,DISPLAY_I2C_ADDR
          sts TWDR, r16 
          ldi r16, (1<<TWINT) | (1<<TWEN) 
          sts TWCR, r16
; wait for address transmission
.wait_adr
	lds r16,TWCR
	sbrs r16,TWINT 
	rjmp wait_adr
; check status
	lds r16,TWSR
	andi r16, 0xF8 
	cpi r16, 0x18 ; 0x18 = status code: Adress transmitted,ACK received
	brne send_failed
          clc
	ret
.send_failed
	sec
	ret

; =========
; send a single byte
; INPUT: r16 - byte to send
; SCRATCHED: r16
; RETURN: Carry set = transmission error, carry clear = transmission ok
; =========
send_byte:
	sts TWDR, r16 
	ldi r16, (1<<TWINT) | (1<<TWEN) 
	sts TWCR, r16                    
.wait_data
	lds r16,TWCR 
	sbrs r16,TWINT 
	rjmp wait_data
; check transmission
	lds r16,TWSR
	andi r16, 0xF8 
	cpi r16, 0x28 ; 0x28 = status code: data transmitted,ACK received 
	brne send_failed
          clc
	ret
.send_failed
          sec
	ret


; =========
; send a single byte without error checking
; INPUT: r16 - byte to send
; SCRATCHED: r16
; =========
send_byte_fast:
	sts TWDR, r16 
	ldi r16, (1<<TWINT) | (1<<TWEN) 
	sts TWCR, r16                    
.wait_data
	lds r16,TWCR 
	sbrs r16,TWINT 
	rjmp wait_data          
	ret

; ====== send full framebuffer
; SCRATCHED: r0,r1,r16,r29:r28,r31:r30
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; ======
send_framebuffer:
	lds r18,dirtyregions	; load dirty regions bit-mask (bit X = page X)
	ldi r19,7	; number of page we're currently checking
.send_pages
	lsl r18 ; shift bit 7 into carry
	brcc pagenotdirty

	mov r17,r19
	rcall send_page ; SCRATCHED: r0,r1,r16,r17,r31:r30
	brcs error
.pagenotdirty
	dec r19
	brne send_pages

	clc
	ret
.error	
	sec
	ret

; =============================
; INPUT: r17 - no. of page to send (0...7)
; SCRATCHED: r0,r1,r16,r17,r31:r30
; =============================
send_page:
; select page
	ldi r16,0xb0 ; 0xb0 | page no => switch to page x
	or r16,r17
	rcall send_single_command
	brcs error

; select lower start column
	ldi r16,0x00
	rcall send_single_command
; select upper start column
	ldi r16,0x10
	rcall send_single_command

; initiage GDDRAM transfer
	rcall send_start
	brcs error

	ldi r16,REQ_DATA_STREAM
	rcall send_byte
	brcs error

; calculate offset in frame buffer	
	ldi r16,128
	mul r16,r17 ; r1:r0 = 128 * pageNo
	ldi r31,HIGH(framebuffer)
	ldi r30,LOW(framebuffer)
	add r30,r0
	adc r31,r1	 ; + carry
	ldi r17,16 ; 16*8 bytes to transmit
.send_loop
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte_fast	
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte_fast
	ld r16,Z+
	rcall send_byte
	brcs error
	dec r17
	brne send_loop
	rcall send_stop
	clc
	ret
.error
	rcall send_stop
	sec
	ret			
		
; =========
; send single-byte command to display
; INPUT: r16 - command to send
; SCRATCHED: r16
; RESULT: carry clear => transmission ok, carry set => transmission failed
; ============
send_single_command:
	push r17
	mov r17,r16 ; backup
	rcall send_start
	brcs error

	ldi r16,REQ_SINGLE_COMMAND
	rcall send_byte
	brcs error

	mov r16,r17
	rcall send_byte
	brcs error

	rcall send_stop
	pop r17
	clc
	ret	
.error 
	rcall send_stop
	pop r17
	sec
	ret	

; ========
; Write decimal number
; INPUT: r16 - number to write
; SCRATCHED: r0,r1,r16,r19,r20,r26,r27,r31,r30
; ========	
write_dec:
	mov r19,r16 ; backup: r19 = x
	ldi r31, HIGH(stringbuffer)
	ldi r30, LOW(stringbuffer)

; divide by 100
	rcall div10 ; result in r1,r16 scratched
	mov r16,r1
	rcall div10 ; r1 = x / 100
	mov r20,r1 ; backup: r20 = x/100

	ldi r16,'0'
	add r16,r1	

	st Z+,r16

	ldi r16,100
	mul r20,r16 ; (x/100)*100
	sub r19,r0 ; r19 = remainder

; divide by 10
	mov r16,r19  ; r0 = r19 = x
	rcall div10 ; result in r1
	mov r20,r1 ; backup: r20 = x/10

	ldi r16,'0'
	add r16,r1	

	st Z+,r16

	ldi r16,10
	mul r20,r16 ; (x/10)*10
	sub r19,r0 ; r19 = remainder
; remainder
	
	ldi r16,'0'
	add r16,r19

	st Z+,r16

	ldi r16,0
	st Z+,r16 ; write terminating 0 byte
	
	ldi r26,LOW(stringbuffer)
	ldi r27,HIGH(stringbuffer)
	rcall write_sram_string	

	ret
; ====
; Unsigned 8-bit divide by 10
; INPUT: r16 - value to divide
; RETURN: r1 - r0/10
; SCRATCHED: r1,r16
div10:    
	eor r1,r1
.loop	cpi r16,10
	brlo end
	subi r16,10
	inc r1
	rjmp loop
.end	
	ret

; =======
; write string from flash memory
; INPUT: r27:r26 - String to print (FLASH)
; SCRATCHED: r0,r1,r2,r3,r16, r17, r18,r19, r20 , r21, r29,r28,r30 ,r31
; =======
write_flash_string:
	lds r19,cursorx
	lds r20,cursory
	ldi r21,0 ; FLASH
	rcall internal_write_string
	sts cursorx,r19
	sts cursory,r20
	ret

; =======
; write string from SRAM memory
; INPUT: r27:r26 - String to print (FLASH)
; SCRATCHED: r0,r1,r2,r3,r16, r17, r18,r19, r20 , r21, r29,r28,r30 ,r31
; =======
write_sram_string:
	lds r19,cursorx
	lds r20,cursory
	ldi r21,1 ; SRAM
	rcall internal_write_string
	sts cursorx,r19
	sts cursory,r20
	ret
	
; =======
; internal write string
; INPUT: r19 - X
; INPUT: r20 - Y
; INPUT: r21 - 0 => read from flash, != 0 => read from SRAM
; INPUT: r27:r26 - String to print (FLASH)
; SCRATCHED: r0,r1,r2,r3,r16, r17, r18,r19, r20 , r21 , r29,r28,r30 ,r31
; ======
internal_write_string:
.next	movw r31:r30,r27:r26
	tst r21
	brne read_sram
          lpm r16,Z+
	rjmp goon
.read_sram
	ld r16,Z+
.goon
	movw r27:r26,r31:r30
          tst r16
          breq end
	cpi r16,0x0a ; linefeed
	breq newline
	mov r17,r19 ; r17 = x
	mov r18,r20 ; r18 = y
	rcall write_char
	inc r19 ; x+= 1
	cpi r19, 16
	brne next
.newline
	ldi r19,0 ; x = 0
	cpi r20,7
	brne cont
	rcall scroll_up
	rjmp next
.cont
	inc r20 ; y = y + 1
	rjmp next
.end
	ret
	
; ========
; write one ASCII glyph into the framebuffer
; INPUT: r16 - character to write
; INPUT: r17 - column (X)
; INPUT: r18 - row (Y)
; SCRATCHED: r0,r1,r2,r3,r16,r17 r18,r29,r28,r30 ,r31
; =======
write_char:
; map ASCII code to glyph  
; look-up glyph index
	ldi r31,HIGH(charset_mapping)
	ldi r30,LOW(charset_mapping)
	add r30,r16 ; add character
	ldi r16,0
          adc  r31,r16 ; + carry
	lpm r0 , Z ; r0 = glyph index
; multiply glyph index by size of one glyph to calculate charset ROM offset
	ldi r16, BYTES_PER_GLYPH ; 8x8 pixel per glyph
	mul r16,r0	 ; r1:r0 now hold offset of glyph in flash memory
; add start address of charset ROM 
	ldi r31,HIGH(charset)
	ldi r30,LOW(charset)
	add r30,r0
	adc r31,r1
; Z now points to start of glyph in font ROM
	ldi r16,8
	mul r17,r16 ;  r1:r0 = X * GLYPH_WIDTH_IN_PIXELS
	movw r3:r2,r1:r0 ; backup result
	ldi r16,128
	mul r18,r16 ; r1:r0 = Y * 128
	add r0,r2
	adc r1,r3
; r1:r0 now hold offset into framebuffer
	ldi r29,HIGH(framebuffer)	
	ldi r28,LOW(framebuffer)
	add r28,r0
	adc r29,r1

;	mov r16,r18
;	rcall mark_page_dirty

; r29:r28 holds pointer into framebuffer where to write glyph data
	ldi r16 , GLYPH_HEIGHT_IN_BITS
.row_loop
	lpm r18,Z+
	st Y+,r18
	dec r16
	brne row_loop
	ret

; ============================
; blit sprite from FLASH into framebuffer
; INPUT: r19 - Destination X position (pixel)
; INPUT: r20 - Destination Y position (pixel)
; INPUT: r21 - Sprite width (pixel)
; INPUT: r22 - Sprite height (pixel)
; INPUT: r31:r30 - Ptr to start of sprite in FLASH
; SCRATCHED:  r0,r1,r16,r17,r18,r19,r20,
; ============================
blit_sprite:
; calculate Y start (top) mask and Y start byte offset
	mov r16,r20 ; use Y start as function argument
	ldi r23,0xff ; initialize Y top mask
	rcall calc_bit_and_byte_offset ; r16 = Y start / 8  , r17 = remainder
	mov r25,r16 ; backup Y start/8 , needed later in framebuffer offset calculation
	breq no_top_mask_needed ; remainder = 0 ?
.create_top_mask
	lsl r23 ; shift-in zero bit
	dec r17
	brne create_top_mask ; all mask bits processed ?
.no_top_mask_needed
; calculate Y end (bottom) mask and Y end byte offset
	mov r16,r22 ; r16 = sprite height
	add r16,r20 ; r16 = Y + sprite height
	ldi r24,0xff ; initial Y bottom mask
	rcall calc_bit_and_byte_offset ; r16 = (Y start + sprite height) / 8  , r17 = remainder
	breq no_bottom_mask_needed
.create_bottom_mask
	lsr r24 ; shift-in zero bit
	dec r17 ; remainder-= 1
	brne create_bottom_mask
.no_bottom_mask_needed
; calculate display pages that will be dirtied
; r25= Y start/8 
; r16=(Y start + sprite height) / 8 
.mark_dirty
	rcall mark_page_dirty
	cp r16,r25
	breq all_marked
	dec r16
	rjmp mark_dirty
.all_marked
; calculate framebuffer start offset
	ldi r18,128 ; CONSTANT: row height, also used later in copy loop when advancing to the next row !!!
	ldi r20,0 ; CONSTANT: zero (also used in copy loop!!)
	mul r25,r18 ; r1:r0 = ( Y start/8 ) * 128 bytes
	add r0,r19 ; + X
	adc r1,r20 ; + carry
	ldi r29, HIGH(framebuffer) 	
	ldi r28, LOW(framebuffer)
	add r28,r0 ; + ( Y start/8 ) * 128 bytes + x 
	adc r29,r1 ; 
; r31:r30 now hold start of first row in framebuffer
	mov r16,r21 ; r16 = column (X) counter
	lsr r22 ; Sprite height /= 2
	lsr r22  ; Sprite height /= 2
	lsr r22  ; Sprite height /= 2
	mov r17,r22 ; r17 = row ( sprite_height / 8 ) counter
	movw X,Y  ; X = Y - backup start of row so we can later just increment it by 16 to advance to the start of the next row
.copy_loop
	lpm r25,Z+ ; load one byte worth of sprite data
	cp r17,r22
	brne not_first_row
; apply top row mask
	and r25,r23
.not_first_row
	tst r17
	brne not_last_row
; apply bottom row mask
	and r25,r24
.not_last_row	
	ld r19, Y ; load one byte from framebuffer
	or r19,r25 ; OR in sprite data
	st Y+,r19
	dec r16 ; x = x - 1
	brne copy_loop
	mov r16,r21 ; reset x column counter
	add r26,r18 ; + 128
	adc r27,r20 ; + 0 + carry
	movw Y,X 
	dec r17 ; decrement row counter
	brne copy_loop
	ret
; ===========================
; Mark page as dirty
; INPUT: r16 - page no (0..7) to mark dirty
; SCRATCHED: r17,r18
; ===========================
mark_page_dirty:
	mov r17,r16
	ldi r18,0x01
.loop	tst r17
	breq end
	lsl r18
	dec r17
	rjmp loop
.end
	lds r17,dirtyregions
	or r17,r18
	sts dirtyregions,r17	
	ret

; ===========================
; Perform division by 8 and return the result and the remainder
; INPUT: r16 - Pixel offset
; RETURN: r16 = Byte offset
; RETURN: r17 = Bit-offset
; RETURN: flags indicate contents of r17
; SCRATCHED: r0,r1,r16,r17,r18
; ===========================
calc_bit_and_byte_offset:
	mov r17,r16
	lsr r16
	lsr r16
	lsr r16     ; r16 = floor(x/8) => Byte Offset
	ldi r18,8
	mul r16,r18 ; r1:r0 = floor(x/8)*8
	sub r17,r0 ; r17 = x - floor(x/8)*8 => Bit offset
	ret
	
; ============
; scroll up one line
; SCRATCHED: r16
; ============
scroll_up:
	push r26
	push r27
	push r28
	push r29
	push r30
	push r31

	ldi r27 , HIGH(FRAMEBUFFER_SIZE-128) ; X
	ldi r26 , LOW(FRAMEBUFFER_SIZE-128)
	ldi r29, HIGH(framebuffer) ; Y
          	ldi r28, LOW(framebuffer) ; Y

	ldi r31, HIGH(framebuffer+128) ; Z
          ldi r30, LOW(framebuffer+128) ; Z

.loop
	ld r16,Z+
	st Y+,r16
	sbiw r27:r26,1
	brne loop

; clear last line
	ldi r31, HIGH(framebuffer+7*8*BYTES_PER_ROW) ; Z
          ldi r30, LOW(framebuffer+7*8*BYTES_PER_ROW) ; Z
	ldi r27 , HIGH(BYTES_PER_ROW*8) ; X
	ldi r26 , LOW(BYTES_PER_ROW*8)	
	ldi r16,0x00
.clrloop
	st Z+,r16
	sbiw r27:r26,1
	brne clrloop
	pop r31
	pop r30
	pop r29
	pop r28
	pop r27
	pop r26
	ret

; ============================
; Clears only the framebuffer regions that are marked dirty
; SCRATCHED: r0,r1,r16,r17,r18,r19,r31:r30
; ============================
clear_dirty_pages:
	lds r19,dirtyregions
	ldi r17,7	; page no
.clr_loop  
	lsl r19
	brcc pagenotdirty
	rcall clear_page
.pagenotdirty
	dec r17
	brne clr_loop	
	ret

; ============================
; Clears only the framebuffer regions that are marked dirty
; INPUT: r17 - page no to clear (0...7)
; SCRATCHED: r0,r1,r16,18,r31:r30
; ============================
clear_page:
	ldi r16,128
	mul r16,r17
           ldi r31, HIGH(framebuffer) ; Z
           ldi r30, LOW(framebuffer) ; Z
	add r30,r0
	adc r31,r1
	 ldi r18,16 ; 16 * 8 bytes to clear = 128 bytes = 1 page
           ldi r16,0x00
.clr_loop  
           st Z+, r16
           st Z+, r16
           st Z+, r16
           st Z+, r16
           st Z+, r16
           st Z+, r16
           st Z+, r16
           st Z+, r16
          	dec r18
           brne clr_loop
	ret

; =======
; Sleep up to 255 millseconds
; INPUT: r16 - time to sleep in ms
; ======= 
msleep:
	push r16
	push r25
	push r24
.loop
	rcall sleep_one_ms
	dec r16
	brne loop
	pop r24
	pop r25
	pop r16
	ret
; =====
; sleep one millisecond
; SCRATCHED: r25:r24
.equ loop_count = 16000/3-3 ; 16000 cycles = 1 ms , - 3 loop iterations (=9 cycles) for instructions before/after loop
sleep_one_ms:
	ldi r24, LOW(loop_count)
	ldi r25, HIGH(loop_count)
	nop
	nop
.loop1
	sbiw r25:r24,1 ; 2 cycles
	brne loop1 ; 1 if condition is false, otherwise 2 
.end	ret ; 4 cles

; =========
; sleep for up to 255 micro seconds
;
; >>>> Must NEVER be called with a value less than 2 us (infinite loop) <<<<

; IN: r18 = number of microseconds to sleep
; SCRATCHED: r0,r1,r17,r27:r26
;
; Total execution time: 
; +1 cycles for caller having to load the R18 register with time to wait
; +4 cycles for CALL invoking this method
; +5 cycles for calculating cycle count
; +4 cycles for RET 
; =========
usleep:
          ldi r17 , cycles_per_us ; 1 cycles
          mul r18 , r17 ; 1 cycle , result is in r1:r0     
          movw r27:r26 , r1:r0 ; 1 cycle
          sbiw r27:r26,14  ; 2 cycles , adjust for cycles spent invoking this method + preparation  
.loop     sbiw r27:r26,4 ; 2 cycles , subtract 4 cycles per loop iteration      
	brpl loop ; 2 cycles, 1 cycle if branch not taken
	ret ; 4 cycles

; =================================================
; Random number generator
; OUTPUT: r4
; SCRATCHED: r31:r30
; =================================================
rand:
	push r5
	push r6
	push r7

	ldi r31,HIGH(rnd)
	ldi r30,LOW(rnd)

	ld r4,Z+
	ld r5,Z+
	ld r6,Z+
	ld r7,Z

	lsl	r4
	rol	r5
	rol	r6
	rol	r7
	sbrs	r7,7
	rjmp return
	ldi	r24,0xB5
	eor	r4,r24
	ldi	r24,0x95
	eor	r5,r24
	ldi	r24,0xAA
	eor	r6,r24
	ldi	r24,0x20
	eor	r7,r24

.return
	ldi r31,HIGH(rnd)
	ldi r30,LOW(rnd)

	st Z,r7
	st -Z,r6
	st -Z,r5
	st Z,r4

	pop r7
	pop r6
	pop r5
	ret

commands: .dw cmd1,2
          .dw cmd2,3
          .dw cmd3,3
cmd1:     .db REQ_SINGLE_COMMAND,0xaf ; switch display on
cmd2:     .db REQ_COMMAND_STREAM,0x20, %00 ; set horizontal addressing mode (00), vertical = (01),page = 10
cmd3:     .db REQ_COMMAND_STREAM,0x20, %10 ; set horizontal addressing mode (00), vertical = (01),page = 10)

text: .db "text1",0

sprite1:
; data organization: 8 bits per column columns
    .db 0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff
    .db 0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff ; '0'
charset:
    .db 0x00,0x3e,0x7f,0x41,0x4d,0x4f,0x2e,0x00 ; '@' (offset 0)
    .db 0x00,0x7c,0x7e,0x0b,0x0b,0x7e,0x7c,0x00 ; 'a' (offset 8)
    .db 0x00,0x7f,0x7f,0x49,0x49,0x7f,0x36,0x00 ; 'b' (offset 16)
    .db 0x00,0x3e,0x7f,0x41,0x41,0x63,0x22,0x00 ; 'c' (offset 24)
    .db 0x00,0x7f,0x7f,0x41,0x63,0x3e,0x1c,0x00 ; 'd' (offset 32)
    .db 0x00,0x7f,0x7f,0x49,0x49,0x41,0x41,0x00 ; 'e' (offset 40)
    .db 0x00,0x7f,0x7f,0x09,0x09,0x01,0x01,0x00 ; 'f' (offset 48)
    .db 0x00,0x3e,0x7f,0x41,0x49,0x7b,0x3a,0x00 ; 'g' (offset 56)
    .db 0x00,0x7f,0x7f,0x08,0x08,0x7f,0x7f,0x00 ; 'h' (offset 64)
    .db 0x00,0x00,0x41,0x7f,0x7f,0x41,0x00,0x00 ; 'i' (offset 72)
    .db 0x00,0x20,0x60,0x41,0x7f,0x3f,0x01,0x00 ; 'j' (offset 80)
    .db 0x00,0x7f,0x7f,0x1c,0x36,0x63,0x41,0x00 ; 'k' (offset 88)
    .db 0x00,0x7f,0x7f,0x40,0x40,0x40,0x40,0x00 ; 'l' (offset 96)
    .db 0x00,0x7f,0x7f,0x06,0x0c,0x06,0x7f,0x7f ; 'm' (offset 104)
    .db 0x00,0x7f,0x7f,0x0e,0x1c,0x7f,0x7f,0x00 ; 'n' (offset 112)
    .db 0x00,0x3e,0x7f,0x41,0x41,0x7f,0x3e,0x00 ; 'o' (offset 120)
    .db 0x00,0x7f,0x7f,0x09,0x09,0x0f,0x06,0x00 ; 'p' (offset 128)
    .db 0x00,0x1e,0x3f,0x21,0x61,0x7f,0x5e,0x00 ; 'q' (offset 136)
    .db 0x00,0x7f,0x7f,0x19,0x39,0x6f,0x46,0x00 ; 'r' (offset 144)
    .db 0x00,0x26,0x6f,0x49,0x49,0x7b,0x32,0x00 ; 's' (offset 152)
    .db 0x00,0x01,0x01,0x7f,0x7f,0x01,0x01,0x00 ; 't' (offset 160)
    .db 0x00,0x3f,0x7f,0x40,0x40,0x7f,0x3f,0x00 ; 'u' (offset 168)
    .db 0x00,0x1f,0x3f,0x60,0x60,0x3f,0x1f,0x00 ; 'v' (offset 176)
    .db 0x00,0x7f,0x7f,0x30,0x18,0x30,0x7f,0x7f ; 'w' (offset 184)
    .db 0x00,0x63,0x77,0x1c,0x1c,0x77,0x63,0x00 ; 'x' (offset 192)
    .db 0x00,0x07,0x0f,0x10,0x78,0x0f,0x07,0x00 ; 'y' (offset 200)
    .db 0x00,0x61,0x71,0x59,0x4d,0x47,0x43,0x00 ; 'z' (offset 208)
    .db 0x00,0x00,0x7f,0x7f,0x41,0x41,0x00,0x00 ; '[' (offset 216)
    .db 0x00,0x00,0x41,0x41,0x7f,0x7f,0x00,0x00 ; ']' (offset 224)
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00 ; ' ' (offset 232)
    .db 0x00,0x00,0x00,0x4f,0x4f,0x00,0x00,0x00 ; '!' (offset 240)
    .db 0x00,0x07,0x07,0x00,0x00,0x07,0x07,0x00 ; '"' (offset 248)
    .db 0x14,0x7f,0x7f,0x14,0x14,0x7f,0x7f,0x14 ; '#' (offset 256)
    .db 0x00,0x24,0x2e,0x6b,0x6b,0x3a,0x12,0x00 ; '$' (offset 264)
    .db 0x00,0x63,0x33,0x18,0x0c,0x66,0x63,0x00 ; '%' (offset 272)
    .db 0x00,0x32,0x7f,0x4d,0x4d,0x77,0x72,0x50 ; '&' (offset 280)
    .db 0x00,0x00,0x00,0x04,0x06,0x03,0x01,0x00 ; ''' (offset 288)
    .db 0x00,0x00,0x1c,0x3e,0x63,0x41,0x00,0x00 ; '(' (offset 296)
    .db 0x00,0x00,0x41,0x63,0x3e,0x1c,0x00,0x00 ; ')' (offset 304)
    .db 0x08,0x2a,0x3e,0x1c,0x1c,0x3e,0x2a,0x08 ; '*' (offset 312)
    .db 0x00,0x08,0x08,0x3e,0x3e,0x08,0x08,0x00 ; '+' (offset 320)
    .db 0x00,0x00,0x80,0xe0,0x60,0x00,0x00,0x00 ; ',' (offset 328)
    .db 0x00,0x08,0x08,0x08,0x08,0x08,0x08,0x00 ; '-' (offset 336)
    .db 0x00,0x00,0x00,0x60,0x60,0x00,0x00,0x00 ; '.' (offset 344)
    .db 0x00,0x40,0x60,0x30,0x18,0x0c,0x06,0x02 ; '/' (offset 352)
    .db 0x00,0x3e,0x7f,0x49,0x45,0x7f,0x3e,0x00 ; '0' (offset 360)
    .db 0x00,0x40,0x44,0x7f,0x7f,0x40,0x40,0x00 ; '1' (offset 368)
    .db 0x00,0x62,0x73,0x51,0x49,0x4f,0x46,0x00 ; '2' (offset 376)
    .db 0x00,0x22,0x63,0x49,0x49,0x7f,0x36,0x00 ; '3' (offset 384)
    .db 0x00,0x18,0x18,0x14,0x16,0x7f,0x7f,0x10 ; '4' (offset 392)
    .db 0x00,0x27,0x67,0x45,0x45,0x7d,0x39,0x00 ; '5' (offset 400)
    .db 0x00,0x3e,0x7f,0x49,0x49,0x7b,0x32,0x00 ; '6' (offset 408)
    .db 0x00,0x03,0x03,0x79,0x7d,0x07,0x03,0x00 ; '7' (offset 416)
    .db 0x00,0x36,0x7f,0x49,0x49,0x7f,0x36,0x00 ; '8' (offset 424)
    .db 0x00,0x26,0x6f,0x49,0x49,0x7f,0x3e,0x00 ; '9' (offset 432)
    .db 0x00,0x00,0x00,0x24,0x24,0x00,0x00,0x00 ; ':' (offset 440)
    .db 0x00,0x00,0x80,0xe4,0x64,0x00,0x00,0x00 ; ';' (offset 448)
    .db 0x00,0x08,0x1c,0x36,0x63,0x41,0x41,0x00 ; '<' (offset 456)
    .db 0x00,0x14,0x14,0x14,0x14,0x14,0x14,0x00 ; '=' (offset 464)
    .db 0x00,0x41,0x41,0x63,0x36,0x1c,0x08,0x00 ; '>' (offset 472)
    .db 0x00,0x02,0x03,0x51,0x59,0x0f,0x06,0x00 ; '?' (offset 480)
charset_mapping:
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x1d,0x1e,0x1f,0x20,0x21,0x22,0x23,0x24
    .db 0x25,0x26,0x27,0x28,0x29,0x2a,0x2b,0x2c
    .db 0x2d,0x2e,0x2f,0x30,0x31,0x32,0x33,0x34
    .db 0x35,0x36,0x37,0x38,0x39,0x3a,0x3b,0x3c
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x1b,0x00,0x1c,0x00,0x00
    .db 0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07
    .db 0x08,0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f
    .db 0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17
    .db 0x18,0x19,0x1a,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
    .db 0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00

.dseg
; current cursor position
cursorx: .byte 1
cursory: .byte 1

; dirty-page tracking (0 bit per display page, bit 0 = page0)
; only dirty pages are transmitted to display controller
dirtyregions: .byte 1

; buffer used by write_dec (3 digits + 0 byte)
stringbuffer: .byte 4
rnd: .byte 4
; sprite coordinates
spritex: .byte 1
spritey: .byte 1
spritedx: .byte 1
spritedy: .byte 1
.byte 1

framebuffer: .byte 1024
