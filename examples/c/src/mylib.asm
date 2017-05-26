#include "m328Pdef.inc"

/*
- An argument is passed either completely in registers or completely in memory.
- To find the register where a function argument is passed, initialize the register number Rn with R26 and follow this procedure:
  - If the argument size is an odd number of bytes, round up the size to the next even number.
  - Subtract the rounded size from the register number Rn.
  - If the new Rn is at least R8 and the size of the object is non-zero, then the low-byte of the argument is passed in Rn. Subsequent bytes of the argument are passed in the subsequent registers, i.e. in increasing register numbers.
  - If the new register number Rn is smaller than R8 or the size of the argument is zero, the argument will be passed in memory.
  - If the current argument is passed in memory, stop the procedure: All subsequent arguments will also be passed in memory.
  - If there are arguments left, goto 1. and proceed with the next argument.
  - Return values with a size of 1 byte up to and including a size of 8 bytes will be returned in registers. Return values whose size is outside that range will be returned in memory.
  - If a return value cannot be returned in registers, the caller will allocate stack space and pass the address as implicit first pointer argument to the callee. The callee will put the return value into the space provided by the caller.
  - If the return value of a function is returned in registers, the same registers are used as if the value was the first parameter of a non-varargs function. For example, an 8-bit value is returned in R24 and an 32-bit value is returned R22...R25.
  - Arguments of varargs functions are passed on the stack. This applies even to the named arguments.

Call-used registers (r18-r27, r30-r31): May be allocated by gcc for local data. You may use them freely in assembler subroutines. Calling C subroutines can clobber any of them - the caller is responsible for saving and restoring.

Call-saved registers (r2-r17, r28-r29): May be allocated by gcc for local data. Calling C subroutines leaves them unchanged. Assembler subroutines are responsible for saving and restoring these registers, if changed. 

r29:r28 (Y pointer) is used as a frame pointer (points to local data on stack) if necessary. The requirement for the callee to save/preserve the contents of these registers even applies in situations
 where the compiler assigns them for argument passing.

r0 - temporary register, can be clobbered by any C code (except interrupt handlers which save it), may be used to remember something for a while within one piece of assembler code

r1 - assumed to be always zero in any C code, may be used to remember something for a while within one piece of assembler code, but must then be cleared after use (clr r1). 
This includes any use of the [f]mul[s[u]] instructions, which return their result in r1:r0. Interrupt handlers save and clear r1 on entry, and restore r1 on exit (in case it was non-zero).

Function call conventions: 

Arguments - Allocated left to right, r25 to r8. All arguments are aligned to start in even-numbered registers (odd-sized arguments, including char, have one free register above them). This allows making better use of the movw instruction on the enhanced core.
If too many, those that don't fit are passed on the stack.

Return values: 

=> 8-bit in r24 (not r25!), 16-bit in r25:r24, up to 32 bits in r22-r25, up to 64 bits in r18-r25. 
=> 8-bit return values are zero/sign-extended to 16 bits by the called function (unsigned char is more efficient than signed char - just clr r25). 
=> Arguments to functions with variable argument lists (printf etc.) are all passed on stack, and char is extended to int.
*/

.equ CPU_FREQUENCY = 16000000 ; 16 Mhz

.equ CYCLES_PER_MS = CPU_FREQUENCY/1000

.equ KEYBOARD_BUFFER_SIZE = 16

.equ DISPLAY_WIDTH_IN_PIXEL = 128
.equ DISPLAY_HEIGHT_IN_PIXEL = 64

.equ GLYPH_WIDTH_IN_BITS = 8 
.equ GLYPH_HEIGHT_IN_BITS = 8

; see sleep_one_ms
.equ loop_count = (CPU_FREQUENCY/1000)/3-3 ; - 3 loop iterations (=9 cycles) for instructions before/after loop

; PINs
.equ PS2_CLK_PIN = 2
.equ PS2_DATA_PIN = 3

.equ IR_DATA_PIN = 2

.equ DISPLAY_RESET_PIN = 4
.equ GREEN_LED_PIN = 6
.equ RED_LED_PIN = 7

#define LCD_ADR %01111000

#define RX_TIMEOUT_STARTBIT 0xff

.equ FRAMEBUFFER_SIZE = 1024
.equ BYTES_PER_ROW = DISPLAY_WIDTH_IN_PIXEL/GLYPH_WIDTH_IN_BITS

.equ BYTES_PER_GLYPH = (GLYPH_WIDTH_IN_BITS*GLYPH_HEIGHT_IN_BITS)/8

; Display commands

.equ CMD_DISPLAY_ON = 0
.equ CMD_ROW_ADDRESSING_MODE = 1
.equ CMD_PAGE_ADDRESSING_MODE = 2

; SSD1306 request types

.equ REQ_SINGLE_COMMAND = 0x80
.equ REQ_COMMAND_STREAM = 0x00
.equ REQ_SINGLE_DATA = 0xc0
.equ REQ_DATA_STREAM = 0x40

; =========
; send a single byte with error checking
; INPUT: r24 - byte to send
; SCRATCHED: r24,r25
; RETURN: r24 != 0 => no error, r24 == 0 => error
; =========
i2c_send_byte:
          rcall send_byte
          brcs error
          ldi r24,1       
	ret
.error
          clr r24
          ret

; =========
; send a single byte with error checking
; INPUT: r24 - byte to send
; SCRATCHED: r24
; RETURN: carry set => error , carry clear => no error
; =========
send_byte:
	sts TWDR, r24
	ldi r24, (1<<TWINT) | (1<<TWEN) 
	sts TWCR, r24                    
.wait_data
	lds r24,TWCR 
	sbrs r24,TWINT 
	rjmp wait_data
; check transmission
	lds r24,TWSR
	andi r24, 0xF8 
	cpi r24, 0x28 ; 0x28 = status code: data transmitted,ACK received 
	brne send_failed
          clc
	ret
.send_failed
          sec
	ret

; =========
; Send i2c start command.
;
; INPUT: r24 - device address
; SCRATCHED: r24,r25
; RETURN: r24 == 0 => TX error, r24 != 0 => no errors
; =========
i2c_send_start:
          rcall send_start
          brcs error
          ldi r24,1
	ret
.error
          clr r24
          ret

; ===================
; Send Start command and slave address 
; SCRATCHED: r24
; RETURN: carry set => TX error, carry clear => no errors
; ====================
send_start:
; send START
          ldi r24, (1<<TWINT)|(1<<TWSTA)|(1<<TWEN) 
          sts TWCR, r24
; wait for START transmitted
.wait_start
          lds r24,TWCR
          sbrs r24,TWINT 
          rjmp wait_start

; check for transmission error
          lds r24,TWSR
          andi r24, 0xF8 
	cpi r24, 0x08 ; 0x08 = status code: START transmitted 
	brne send_failed
; transmit address (first byte)
;          ldi r24,LCD_ADR
          lds r24,deviceAdr
          sts TWDR, r24
          ldi r24, (1<<TWINT) | (1<<TWEN) 
          sts TWCR, r24
; wait for address transmission
.wait_adr
	lds r24,TWCR
	sbrs r24,TWINT 
	rjmp wait_adr
; check status
	lds r24,TWSR
	andi r24, 0xF8 
	cpi r24, 0x18 ; 0x18 = status code: Adress transmitted,ACK received
	brne send_failed
          clc
	ret
.send_failed
	sec
	ret

; =====
; send stop
; SCRATCHED: r24
; =============

i2c_send_stop:
	ldi r24, (1<<TWINT)|(1<<TWEN)| (1<<TWSTO) ; 1 cycle
	sts TWCR, r24 ; 2 cycles
.wait_tx
	lds r24,TWCR
          andi r24,(1<<TWSTO) 
	brne wait_tx
	ret
; ====
; Setup MCU for I2C communication
; INPUT: r24 - I2C address of LCD display
; SCRATCHED: r24
; ====
i2c_setup:
;       store device address
        sts deviceAdr,r24
;       setup TWI rate
        lds r24,TWSR
        andi r24,%11111100
        sts TWSR,r24 ; prescaler bits = 00 => factor 1x
       ldi r24,12 ; 400 kHz
;       ldi r24,19 ; 300 kHz
;       ldi r24,32 ; 200 kHz
;        ldi r24,72 ; 100 kHz
        sts TWBR,r24 ; factor

        ldi r24,100
        rcall util_msleep
        ret

; ============
; scroll up one line
; SCRATCHED: r18
; ============
framebuffer_scroll_up:
          push r28
          push r29
	ldi r27 , HIGH(FRAMEBUFFER_SIZE-128) ; X
	ldi r26 , LOW(FRAMEBUFFER_SIZE-128) ; X
	ldi r29, HIGH(framebuffer) ; Y
          	ldi r28, LOW(framebuffer) ; Y

	ldi r31, HIGH(framebuffer+128) ; Z
          ldi r30, LOW(framebuffer+128) ; Z

.loop
	ld r18,Z+
	st Y+,r18
	sbiw r27:r26,1
	brne loop

; clear last line
	ldi r31, HIGH(framebuffer+7*8*BYTES_PER_ROW) ; Z
          ldi r30, LOW(framebuffer+7*8*BYTES_PER_ROW) ; Z
	ldi r27 , HIGH(BYTES_PER_ROW*8) ; X
	ldi r26 , LOW(BYTES_PER_ROW*8)	
	ldi r18,0x00
.clrloop
	st Z+,r18
	sbiw r27:r26,1
	brne clrloop

           ldi r18,0xff ; vertical scrolling dirties all regions of the display
           sts dirtyregions,r18 ;

           pop r29
           pop r28
	ret

; ===============
; Sends all dirty regions to the display
; and prepares for the next redraw
; SCRATCHED: r20,r21,r19,
; RESULT: r24 != 0 => no errors , r24 == 0 => tx error
; ===============
framebuffer_update_display:
	lds r21,dirtyregions
          tst r21
          breq nothingtodo
	lds r20,previousdirtyregions 
	sts previousdirtyregions,r21 ; previousdirtyregions = dirtyregions
	or r20,r21
	sts dirtyregions,r20 ; transmit union of this and the previous frame

	rcall send_framebuffer
	brcs error

          clr r21
          sts dirtyregions,r21
;	lds r21,previousdirtyregions
;	rcall clear_pages

          ldi r24,1 ; success
	rjmp return
.error        
	clr r24 ; error
.return
          clr r1 ; C code needs this
.nothingtodo
          ret

; ============================
; Zeros the frame buffer.
; SCRATCHED: r0,r1,r18,r19,r20,r21,r30,r31
; ============================
framebuffer_clear:          
	ldi r21,0xff
           sts dirtyregions,r21
	rcall clear_pages
          clr r1 ; C code needs this
          ret

; ============================
; Clears only the framebuffer regions that are marked dirty
; INPUT: r21 - dirty page mask ( bit 0 = page0 , bit 1 = page1 etc. bit set = page dirty)
; SCRATCHED: r0,r1,r18,r19,r20,r21,r30,r31
; ============================
clear_pages:
	ldi r19,8	; page no
.clr_loop  
	dec r19
	lsl r21
	brcc pagenotdirty
	rcall clear_page
.pagenotdirty
	tst r19
	brne clr_loop	
	ret

; ============================
; Clears only the framebuffer regions that are marked dirty
; INPUT: r19 - page no to clear (0...7)
; SCRATCHED: r0,r1,r18,r20,r30,r31
; ============================
clear_page:
	ldi r18,128
	mul r18,r19
          ldi r31, HIGH(framebuffer) ; Z
          ldi r30, LOW(framebuffer) ; Z
	add r30,r0
	adc r31,r1
	ldi r20,16 ; 16 * 8 bytes to clear = 128 bytes = 1 page
          ldi r18,0x00
.clr_loop  
          st Z+, r18
          st Z+, r18
          st Z+, r18
          st Z+, r18
          st Z+, r18
          st Z+, r18
          st Z+, r18
          st Z+, r18
          dec r20
          brne clr_loop
          ret

; ====== 
; Send full framebuffer
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; SCRATCHED: r18,r19,r20
; ======
send_framebuffer:
	lds r18,dirtyregions	; load dirty regions bit-mask (bit X = page X)
	ldi r19,8	; number of page we're currently checking
.send_pages
	dec r19
	lsl r18 ; shift bit 7 into carry
	brcc pagenotdirty

	mov r20,r19
	rcall send_page ; SCRATCHED: r0,r1,r16,r17,r31:r30
	brcs error
.pagenotdirty
	tst r19
	brne send_pages

	clc
.error
	ret	

; =============================
; INPUT: r20 - no. of page to send (0...7)
; SCRATCHED: r0,r1,r20,r21,r24,r30,r31
; RETURN: carry set => TX error, carry clear => no errors
; =============================
send_page:
	rcall send_start
	brcs error

	ldi r24,REQ_COMMAND_STREAM
	rcall send_byte
	brcs error

; select page
	ldi r24,0xb0 ; 0xb0 | page no => switch to page x
	or r24,r20
	rcall send_byte 
	brcs error

; select lower 4 bits of start column (0..128)
	ldi r24,0x00
	rcall send_byte
	brcs error

; select upper 4 bits of start column (0..128)
	ldi r24,0x10
	rcall send_byte
	brcs error

	rcall i2c_send_stop

; initiate GDDRAM transfer
	rcall send_start
	brcs error

	ldi r24,REQ_DATA_STREAM
	rcall send_byte
	brcs error

; calculate offset in frame buffer	
	ldi r21,128
	mul r21,r20 ; r1:r0 = 128 * pageNo
	ldi r31,HIGH(framebuffer)
	ldi r30,LOW(framebuffer)
	add r30,r0
	adc r31,r1	 ; + carry
	ldi r20,16 ; 16*8 bytes=128 bytes per page to transmit
.send_loop
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte_fast
	ld r24,Z+
	rcall send_byte
	brcs error
	dec r20
	brne send_loop
	rcall i2c_send_stop
	clc
	ret
.error
	rcall i2c_send_stop
	sec
	ret

; =========
; Sends a single byte without any error checking
; INPUT: r24 - byte to send
; SCRATCHED: r24
; =========
send_byte_fast:
	sts TWDR, r24 
	ldi r24, (1<<TWINT) | (1<<TWEN) 
	sts TWCR, r24                   
.wait_data
	lds r24,TWCR 
	sbrs r24,TWINT 
	rjmp wait_data          
	ret

; ========
; write one ASCII glyph into the framebuffer
; INPUT: r24 - char to write
; INPUT: r22 - column (X)
; INPUT: r20 - column (Y)
; SCRATCHED: r0,r1,r18,r19,r24,r28,r29,r30,r31
; =======
framebuffer_write_char:
; map ASCII code to glyph  
; look-up glyph index
           push r28
           push r29
	ldi r31,HIGH(charset_mapping)
	ldi r30,LOW(charset_mapping)
	add r30,r24 ; add character
	ldi r24,0
          adc r31,r24 ; + carry
	lpm r0 , Z ; r0 = glyph index
; multiply glyph index by size of one glyph to calculate charset ROM offset
	ldi r24, BYTES_PER_GLYPH ; 8x8 pixel per glyph
	mul r24,r0	 ; r1:r0 now hold offset of glyph in flash memory
; add start address of charset ROM 
	ldi r31,HIGH(charset)
	ldi r30,LOW(charset)
	add r30,r0
	adc r31,r1
; Z now points to start of glyph in font ROM
	ldi r24,8
	mul r22,r24 ;  r1:r0 = X * GLYPH_WIDTH_IN_PIXELS
	movw r19:r18,r1:r0 ; backup result
	ldi r24,128
	mul r20,r24 ; r1:r0 = Y * 128
	add r0,r18
	adc r1,r19
; r1:r0 now hold offset into framebuffer
	ldi r29,HIGH(framebuffer)	
	ldi r28,LOW(framebuffer)
	add r28,r0
	adc r29,r1

	mov r18,r20
	rcall mark_page_dirty

; r29:r28 holds pointer into framebuffer where to write glyph data
	ldi r24 , GLYPH_HEIGHT_IN_BITS
.row_loop
	lpm r18,Z+
	st Y+,r18
	dec r24
	brne row_loop
          clr r1 ; C code needs this
          pop r29
          pop r28
	ret

; ===========================
; Mark page as dirty
; INPUT: r18 - page no (0..7) to mark dirty
; SCRATCHED: r19,r20
; ===========================
mark_page_dirty:
	mov r19,r18
	ldi r20,0x01
	tst r19
.loop	
	breq end
	lsl r20
	dec r19
	rjmp loop
.end
	lds r19,dirtyregions
	or r19,r20
	sts dirtyregions,r19
	ret

; ==================
; Switch LCD displays on
; SCRATCHED: r18,r22,r23,r24,r26,r27,r30,r31
; RESULT: r24 != 0 => no errors, r24 = 0 => tx errors
; ==================
lcd_display_on:
          ldi r24,CMD_DISPLAY_ON
          rcall send_command
          ret

; ======
; Reset display
; SCRATCHED: r18,r22,r23,r24,r26,r27,r30,r31
; RESULT: r24 = 0 => TX error, r24 != 0 => no errors
; ======
lcd_reset_display:

;	cbi DDRD,^ ; set to input
;          sbi PORTD,DISPLAY_RESET_PIN ; Enable pull-up resisstor
;        cbi DDRB,TRIGGER_PIN ; set to input
	sbi DDRD,DISPLAY_RESET_PIN ; set to output

        cbi PORTD, DISPLAY_RESET_PIN
        ldi r24,20
        rcall util_msleep

        sbi PORTD, DISPLAY_RESET_PIN

        ldi r24,200
        rcall util_msleep

; mark all regions dirty so display RAM gets written completely
	ldi r24,0xff
	sts dirtyregions,r24

          clr r24
          sts previousdirtyregions,r24

; switch to page addressing mode
	ldi r24,CMD_PAGE_ADDRESSING_MODE
	rcall send_command

	ret

; ==== debugging helper

debug_toggle_green_led:
         sbic PIND,GREEN_LED_PIN ; skip if LED is off
         rjmp debug_green_led_off
         rjmp debug_green_led_on

debug_toggle_red_led:
         sbic PIND,RED_LED_PIN ; skip if LED is off
         rjmp debug_red_led_off
         rjmp debug_red_led_on

; ======
; Switch green LED.
; INPUT: r24 - either 0 to disable or anything else to enable
; ======
debug_green_led:
          tst r24
          breq debug_green_led_off
debug_green_led_on:
	sbi DDRD,GREEN_LED_PIN ; set to output
	sbi PORTD,GREEN_LED_PIN
          ret

debug_green_led_off:
	sbi DDRD,GREEN_LED_PIN ; set to output
	cbi PORTD,GREEN_LED_PIN
          ret
                
; ======
; Switch red LED.
; INPUT: r24 - either 0 to disable or anything else to enable
; ======
debug_red_led:
          tst r24
          breq debug_red_led_off
debug_red_led_on:
	sbi DDRD,RED_LED_PIN ; set to output
	sbi PORTD,RED_LED_PIN
          ret

debug_red_led_off:
	sbi DDRD,RED_LED_PIN ; set to output
	cbi PORTD,RED_LED_PIN
          ret

; ======
; Blink red LED.
; INPUT: r24 - number of times to blink
; ======
debug_blink_red:   
.loop    
          rcall debug_red_led_off
          tst r24
          breq back
          rcall debug_red_led_on
          rcall sleep_500_ms
          dec r24
          rjmp loop
.back
          ret

; ======
; Sleep 500 ms.
; SCRATCHED: r24
; ======
sleep_500_ms:
          push r24
          ldi r24, 250
          rcall util_msleep
          ldi r24, 250
          rcall util_msleep
          pop r24
          ret

; ====
; send command.
; INPUT: r24 - command table entry index 
; SCRATCHED: r22,r23,r24,r26,r27,r30,r31
; RETURN: r24 != 0 => no errors, r24 == 0 => tx error
; ====
send_command:
          ldi r31 , HIGH(commands)
          ldi r30 , LOW(commands)
          lsl r24 ; * 4 bytes per table entry ( 16-bit address + 16 bit byte count)
          lsl r24
          add r30,r24
	clr r18
          adc r31,r18 ; +carry
; Z now contains ptr to start of cmd entry in table
          lpm r22,Z+ ; cmd address low
          lpm r23,Z+ ; cmd address hi
          lpm r26,Z+ ; +LOW(number of bytes in cmd )
          lpm r27,Z+ ; +HIGH(number of bytes in cmd )
          movw Z,r23:r22 ; Z = r17:r16
          call send_bytes
          ldi r24,1
          brcc no_errors
          clr r24
.no_errors
          ret

; ====== send bytes
; Assumption: CLK HI , DATA HI when method is entered
; INPUT: r31:r30 (Z register) start address of bytes to transmit
; INPUT: r27:r26 - number of bytes to transmit
; SCRATCHED: r22,r23,r24,r30,r31
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; ======
send_bytes:
; send START
          rcall send_start ; clobbers r24
	brcs send_failed
.data_loop
          lpm r24,Z+
          rcall send_byte
          brcs send_failed           
          sbiw r27:r26,1 ; decrement byte counter
          brne data_loop          
; transmission successful
	rcall i2c_send_stop ; clobbers r24  
          clc
	ret
.send_failed	
	rcall i2c_send_stop  ; clobbers r24      	
          sec
          ret

; =======
; Sleep up to 255 millseconds
; INPUT: r24 - time to sleep in ms
; ======= 
util_msleep:
	push r24
	push r26
	push r27
.loop
	rcall sleep_one_ms
	dec r24
	brne loop
	pop r27
	pop r26
	pop r24
	ret
; =====
; sleep one millisecond
; SCRATCHED: r26,r27
sleep_one_ms:
	ldi r26, LOW(loop_count) ; 1 cycle
	ldi r27, HIGH(loop_count) ; 1 cycle
	tst r27 ; waste 2 cycle cycles so 
	tst r27 ; that cycles spend outside the loop (9 cycles) are a multiple of the loop duration (3 cycles)
.loop1
	sbiw r27:r26,1 ; 2 cycles
	brne loop1 ; 1 if condition is false, otherwise 2 
	ret ; 4 cycles

; =====
; Resets the PS/2 configuration
; SCRATCHED: nothing
; =====
ps2_reset:
          cli ; disable interrupts
	cbi DDRD,PS2_CLK_PIN ; set to input
	cbi DDRD,PS2_DATA_PIN ; set to input

  	ldi r24,0
	sts keybuffer_ptr,r24
	sts keybuffer_lost,r24
	sts keybuffer_error,r24	         
; trigger interrupt when PS2_DATA_PIN goes low
          rcall ps2_enable_irq
          ret

; =====
; Enable PS/2 interrupt
; =====
ps2_enable_irq:
	cli
	push r24
; setup INT1 external interrupt
          lds r24,EICRA
          andi r24,%11110011
	ori r24,%1000 ; falling edge on INT1 triggers interrupt
	sts EICRA,r24
; clear pending interrupts
	sbi EIFR,INTF1
; enable IRQ         
	sbi EIMSK,INT1
	pop r24
	sei
          ret

; =====
; Disable PS/2 interrupt
; =====
ps2_disable_irq:
	cli
	cbi EIMSK,INT1
	sbi EIFR,INTF1 ; clear pending interrupts that will have accumulated in the meantime
          ret

; ======
; Read one byte from the PS/2 interface.
; RESULT: r24 - Byte read if carry clear (=success), error code if carry set (=rx error)
; SCRATCHED: r18,r20,r21,r24,r26,r27
; ======
ps2_read_byte:
; wait for data line to go low
          ldi r26,0xff
          ldi r27,0xff
.wait_data_low
          sbis PIND,PS2_DATA_PIN ; skip if bit set
          rjmp ps2_irq_read_byte ; => bit clear
          sbiw r27:r26,1
          brne wait_data_low
	ldi r24,RX_TIMEOUT_STARTBIT
	sec
          ret
; ======
; Called by IRQ routine after PS/2 DATA line went low.
; RESULT: r24 - Byte read , carry set => RX error, carry clear => success
; SCRATCHED: r18,r20,r21,r24,r26,r27
; ======
ps2_irq_read_byte:
; read start bit (this one is always zero)
         rcall ps2_read_bit ; scratches r20,r26,r27
         tst r20
         breq timeout_error_start ; timeout
         brcs start_bit_error ; error, start bit should be a zero bit
; read 8 data bits
         ldi r18,8 ; bit count
         ldi r24,0x00 ; received byte
.data_loop
          rcall ps2_read_bit ; scratches r20,r26,r27
          tst r20
          breq timeout_error_data
          ror r24 ; shift-in carry bit
          dec r18
          brne data_loop
; read parity bit
          ldi r21,0 ;=> temp parity bit storage
          rcall ps2_read_bit
          tst r20
          breq timeout_error_parity
          brcc parity_not_set
          ldi r21,1 ; parity bit set ; The parity bit is set if there is an even number of 1's in the data bits and reset (0) if there is an odd number of 1's in the data bits
.parity_not_set
; check parity
          rcall ps2_calc_parity ; scratches r18,r19,r20
          cp r21,r18
          brne parity_error
; read stop bit (always 1)
          rcall ps2_read_bit ; scratches r20,r26,r27
          tst r20
          breq timeout_error_stop
          brcc stop_bit_error    
	clc
          ret

.timeout_error_start
          ldi r24,RX_TIMEOUT_STARTBIT
	rjmp error_return

.parity_error
          ldi r24,0xfe
          rjmp error_return

.start_bit_error
          ldi r24,0xfd
          rjmp error_return

.stop_bit_error
          ldi r24,0xfc
          rjmp error_return

.timeout_error_stop
          ldi r24,0xfa
	rjmp error_return

.timeout_error_data
          ldi r24,0xf9
	rjmp error_return

.timeout_error_parity
          ldi r24,0xf8
	rjmp error_return

.buffer_overflow_error
          ldi r24,0xfb
.error_return
	sts keybuffer_error,r24
	sec
          ret

; ==== Calculate odd parity
; INPUT: r24 - byte to calculate parity for
; RESULT: r18 - parity
; SCRATCHED: r18,r19,r20
; =====
; The parity bit is set if there is an even number of 1's in the data bits and reset (0) if there is an odd number of 1's in the data bits
ps2_calc_parity:
           mov r19,r24
           clr r1
           ldi r18,0xff
           ldi r20,8
.calc_loop
           rol r19
           sbc r18,r1
           dec r20
           brne calc_loop
           andi r18,1
           ret
; =====
; Read one bit from PS/2.
; RESULT: Carry set = 1-bit , Carry clear = 0-bit
; RESULT: r20 - not zero => ok , zero => timeout         
; SCRATCHED: r20,r26,r27
; =====
ps2_read_bit:
          ldi r26,0xff
          ldi r27,0xff
.wait_clk_low
          sbis PIND,PS2_CLK_PIN ; skip if bit set
          rjmp clk_low ; => bit clear
          sbiw r27:r26,1
          brne wait_clk_low
          rjmp timeout       
.clk_low
; now read data line (abuse r20 for storage)        
          sbic PIND,PS2_DATA_PIN
          rjmp one_bit
.zero_bit ldi r20,0  
          rjmp cont
.one_bit  ldi r20,1
.cont
          ldi r26,0xff
          ldi r27,0xff
.wait_clk_hi
          sbic PIND,PS2_CLK_PIN ; skip if bit clear
          rjmp return
          sbiw r27:r26,1
          brne wait_clk_hi
          rjmp timeout  
.return          
          clc ; carry indicates received bit
          tst r20
          breq received_zero_bit
          sec ; received one bit
.received_zero_bit
          ldi r20,1 ; => success
          ret
.timeout
	clr r20 ; => failure
          ret

; ====================
; wait for data line to go low
; SCRATCHED: r26,r27
; RESULT: Carry clear => success , Carry set => timeout
; ===================
ps2_wait_data_low:
          ldi r26,0xff
          ldi r27,0xff
.loop
          sbis PIND,PS2_DATA_PIN ; skip if bit set
          rjmp data_low ; => bit clear
          sbiw r27:r26,1
          brne loop
          sec
	ret
.data_low
          clc
          ret

; ==== EXTINT1 IRQ routine
.irq 2
extint1_irq:
          push r0
	in r0, SREG
	push r0 ; push flags
        	push r1
	push r18
	push r19
	push r20
	push r21
	push r22
	push r23
	push r24
	push r25
	push r26
	push r27
	push r30
	push r31
	
; --- START: actual IRQ routine

          rcall ps2_irq_read_byte
; byte is in r24 
; carry set = RX error, carry clear = success
	brcs error
	rcall ps2_keybuffer_write
.error
; --- END: actual IRQ routine

	sbi EIFR,INTF1 ; clear pending interrupts that will have accumulated in the meantime

	pop r31
	pop r30
	pop r27
	pop r26
	pop r25
	pop r24
	pop r23
	pop r22
	pop r21
	pop r20
	pop r19
	pop r18
	pop r1
;pop flags
          pop r0
          out SREG,r0 ; 
          pop r0 ; restore r0
          reti

; ===== 
; write a byte to the keybuffer
; INPUT: r24 - byte to write
; SCRATCHED: r0,r1,r18,r30,r31
; =====
ps2_keybuffer_write:
	ldi r31,HIGH(keybuffer)
	ldi r30,LOW(keybuffer)
	lds r18,keybuffer_ptr
	cpi r18,KEYBOARD_BUFFER_SIZE
	breq buffer_full
	mov r0,r18
	clr r1
	add r30,r0
	adc r31,r1
	st Z,r24
	inc r18
	sts keybuffer_ptr,r18
	ret

; buffer already at capacity
.buffer_full
	lds r18,keybuffer_lost
	cpi r18,0xff
	breq cnt_overflow ; do not overflow to zero, otherwise caller would not realize the loss
	inc r18
	sts keybuffer_lost,r18
.cnt_overflow
	ret

; ===== 
; Read keyboard buffer.
;
; Side effects:
;
; - resets last error flags
; - resets buffer overflow counter
;
; INPUT: r25:r24 - ptr to SRAM where to store keyboard buffer contents
; INPUT: r22 - size of destination area
; RESULT: r24 - number of bytes READ
; SCRATCHED: r18,r19,r26,r27,r30,r31
; ======================== DATA ======================
ps2_keybuffer_read:
	cli ; disable IRQs while we're reading from the buffer
	ldi r31,HIGH(keybuffer)
	ldi r30,LOW(keybuffer)
	movw r27:r26,r25:r24
	lds r18,keybuffer_ptr
	mov r24,r18 ; copy buffer size to result register
	tst r18
	breq buffer_empty
.copy_loop
	ld r19,Z+		
	st r27:r26+,r19
	dec r18
	brne copy_loop
.buffer_empty
	sts keybuffer_ptr,r18
	sts keybuffer_lost,r18
	sts keybuffer_error,r18
	sei ; re-enable IRQs
	ret

; ==== 
; Send a single byte command and receives the response from the
; keyboard controller.
; INPUT: r24 - byte to send to keyboard controller
; RESULT: r24 - 0 on error, 1 on success
; ====
ps2_write_byte:
; calculate parity to send later
	rcall ps2_calc_parity ; SCRATCHED: r18,r19,r20
; parity bit was returned in r18
	rcall ps2_disable_irq ; disable interrupts so we don't interfere with keyboard reads

          ldi r31,HIGH(8*1600/4) ; looked good with 16*1600/4
	ldi r30,LOW(8*1600/4)
.delay1
	sbiw r31:r30,1
	brne delay1

; LOW: To sink current, set the data direction pin DDxn to 1 (output). Then set the bit in the output PORTxn register to 0.
; HIGH: To change this to a high-impedance open-drain, set the data direction pin DDxn to 0 (input), while leaving the PORTxn bit 0.
; So instead of toggling the PORT pin, you are toggling the data directionpin.
	
; set clock low
	sbi DDRD,PS2_CLK_PIN 
	cbi PORTD,PS2_CLK_PIN

          ldi r31,HIGH(16*1600/4)
	ldi r30,LOW(16*1600/4)
.delay2
	sbiw r31:r30,1
	brne delay2

; set clock high
	cbi DDRD,PS2_CLK_PIN

; *** transmission starts here ***

; set data low
	sbi DDRD,PS2_DATA_PIN
          cbi PORTD,PS2_DATA_PIN

; send 8 bits (MSB first)
	ldi r19,8
.send_loop
	ror r24
          rcall ps2_write_bit
	dec r19
	brne send_loop
; send parity bit
	ror r18 ; shift parity bit into carry
	rcall ps2_write_bit
; send stop bit (1)
	cbi DDRD,PS2_DATA_PIN ; set data to input
.wait_data_low
	sbic PIND,PS2_DATA_PIN
	rjmp wait_data_low
.wait_clk_low
	sbic PIND,PS2_CLK_PIN
	rjmp wait_clk_low	
; wait for CLK and DATA to go high again
.wait_data_high
	sbic PIND,PS2_DATA_PIN
	rjmp wait_clk_hi
	rjmp wait_data_high
.wait_clk_hi
	sbis PIND,PS2_CLK_PIN
	rjmp wait_clk_hi
; now read the response
	cbi DDRD,PS2_DATA_PIN ; set data to input
	rcall ps2_read_byte ; SCRATCHED: r18,r20,r21,r24,r26,r27
	brcs error
	cpi r24,0xfa ; PS/2 SUCCESS
	brne error
	ldi r24,1
	rjmp back 
.error
	clr r24
.back
	rcall ps2_enable_irq
	ret
; ======
; Write bit in carry.
; INPUT: carry bit
; ======
; LOW: To sink current, set the data direction pin DDxn to 1 (output). Then set the bit in the output PORTxn register to 0.
; HIGH: To change this to a high-impedance open-drain, set the data direction pin DDxn to 0 (input), while leaving the PORTxn bit 0.
; So instead of toggling the PORT pin, you are toggling the data directionpin.
ps2_write_bit:
; wait for clock to be low
.wait_low sbic PIND,PS2_CLK_PIN
          rjmp wait_low
; clock is now low, load bit
	brcc send_0_bit
; send 1 bit
	cbi DDRD,PS2_DATA_PIN
	rjmp wait_high
.send_0_bit
	sbi DDRD,PS2_DATA_PIN
.wait_high sbis PIND,PS2_CLK_PIN
          rjmp wait_high
	ret
; ====
; Returns the last PS/2 error and resets all error flags.
; RESULT: r24 - last error code
; ====
ps2_get_last_error:
	cli
	lds r24,keybuffer_error
	clr r1
	sts keybuffer_error,r1
	sei
	ret
; ====
; Returns the number of bytes lost due to
; keyboard buffer overflows and resets this counter.
; RESULT: r24 - number of bytes lost due to buffer overflow
; ====
ps2_get_overflow_counter:
	cli
	lds r24,keybuffer_lost
	clr r1
	sts keybuffer_lost,r1
	sei
	ret

; ===========================
; Set pixel at (x,y)
; INPUT: r24 - x
; INPUT: r22 = y
; SCRATCHED: r0,r18,r19,r30,r31
; ===========================
.def px = r24
.def py = r22
.def tmp = r18
.def tmp2 = r19
framebuffer_set_pixel:
        ldi r31,HIGH(framebuffer)
        ldi r30,LOW(framebuffer)

        mov r0, py
        lsr r0
        lsr r0
        lsr r0 ; y / 8
        mov tmp2,r0 ; remember y/ 8
        ldi tmp,128
        mul tmp,r0 ; r1:r0 = (y/8)*128
        add r30,r0
        adc r31,r1 ; + (y/8)*128       
        ldi tmp,0
        add r30,px ; + px
        adc r31,tmp ; + carry
; Z now points to byte in framebuffer
; calculate remainder
        ldi tmp,8
        mul tmp2,tmp ; r1:r0 = (y/8)*8, yields 8-bit value
        mov tmp2,py
; do not put anything that changes the Z flag after the 
; following sub instruction, the loop exit condition below needs it unaltered
        sub tmp2,r0 ; tmp = py - (py/8)*8, cannot overflow/underflow
.def remainder = r19
.def mask = r18
        ldi mask, 0x01
.loop
        breq cont ; on the first loop iteration this checks the Z flag set by the sub
        lsl mask
        dec remainder
        rjmp loop	
.cont
        ld r0,Z              
        or r0,mask
        st Z,r0
        clr r1 ; C code needs this
        ret

; =============
; Setup for reading IR signals.
; =============
ir_setup:
       cbi DDRD,IR_DATA_PIN ; set to input
       ret

; =============
; Read IR signals.
; INPUT: r25:r24 - ptr to buffer where pulses should be stored
; INPUT; r22 - buffer size
; RESULT: r24 - number of pulses received , 0xff = timeout, 0xfe = buffer overrun
; =============
.def bytes_received = r21
.def timeout_value = X
.def buffer_size = r22
ir_receive:
	movw r31:r30,r25:r24
	clr bytes_received
	ldi r25,0xff
	ldi r24,0xff
; skip start bit 
.wait_low1
	sbic PIND,IR_DATA_PIN ; 2 
	rjmp wait_low1
.wait_hi1
	sbis PIND,IR_DATA_PIN ; 2 
	rjmp wait_hi1
; regular loop
.wait_low2
	sbic PIND,IR_DATA_PIN ; 2 
	rjmp wait_low2
.wait_hi2
	sbis PIND,IR_DATA_PIN ; 2 
	rjmp wait_hi2	

	movw X,r25:r24
.measure_pulse
	sbis PIND,IR_DATA_PIN ; 2 cycles on skip
	rjmp pulse_done ; 2 cycles
	sbiw X,1 ; 2 cycles
	brne measure_pulse ; 2 cycles
	tst bytes_received ; at least one pulse received ?
	breq timeout ; => no, timeout
	rjmp ok ; end of transmission
.pulse_done
	cp bytes_received,buffer_size
	breq buffer_full
	adiw X,1
	com r27
	com r26
	st Z+,r27
	st Z+,r26
	inc bytes_received ; increment byte counter
	rjmp wait_hi2
	
.buffer_full
	ldi bytes_received,0xfe
	rjmp ok	
.timeout
	ldi bytes_received,0xff	
.ok	
	mov r24,bytes_received
	ret	
	

commands: .dw cmd1,2
          .dw cmd2,3
          .dw cmd3,3
          
cmd1:     .db REQ_SINGLE_COMMAND,0xaf ; switch display on
cmd2:     .db REQ_COMMAND_STREAM,0x20, %00 ; set horizontal addressing mode (00), vertical = (01),page = 10
cmd3:     .db REQ_COMMAND_STREAM,0x20, %10 ; set horizontal addressing mode (00), vertical = (01),page = 10)

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

framebuffer: .byte FRAMEBUFFER_SIZE

; stores bytes received from the PS/2 keyboard
keybuffer: .byte KEYBOARD_BUFFER_SIZE

; offset relative to start of key buffer 
; where the next received byte will be stored
keybuffer_ptr: .byte 1

; number of bytes that were lost
; because the key buffer ran out of space
keybuffer_lost: .byte 1

; most recent error since the last time
; the buffer was read using ps2_keybuffer_read()
keybuffer_error: .byte 1

; dirty-page tracking (0 bit per display page, bit 0 = page0 , bit set = page dirty)
; only dirty pages are transmitted to display controller
dirtyregions: .byte 1
previousdirtyregions: .byte 1

deviceAdr: .byte 1
