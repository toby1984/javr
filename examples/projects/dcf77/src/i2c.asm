#include "m328Pdef.inc"

.equ freq = 16000000 ; Hz
.equ target_freq = 100000 ; Hz

.equ cycles_per_us = freq / 1000000 ; 1 us = 10^-6 s

.equ delay_in_cycles = (freq / target_freq)/2

#define ERROR_LED 7
#define SUCCESS_LED 6
#define TX_PIN 2
#define CLK_PIN 4
#define TRIGGER_PIN 0

#define CLK_HI sbi PORTD , CLK_PIN

#define CLK_LO cbi PORTD , CLK_PIN

#define DATA_HI sbi PORTD , TX_PIN
#define DATA_LO cbi PORTD , TX_PIN

#define SUCCESS_LED_ON  sbi PORTD, SUCCESS_LED
#define SUCCESS_LED_OFF cbi PORTD, SUCCESS_LED

#define ERROR_LED_ON  sbi PORTD, ERROR_LED
#define ERROR_LED_OFF cbi PORTD, ERROR_LED

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
	eor r1,r1
	out 0x3f,r1
; initialize stack pointer
	ldi r28,0xff
	ldi r29,0x08
	out 0x3e,r29	; SPH = 0x08
	out 0x3d,r28	; SPL = 0xff
; call main program
again:
	call main
	call wait_for_button  
          rjmp again
onirq:
	jmp 0x00
; ==========================
; main program starts here
; ==========================
main:
         call reset
          
         call wait_for_button  
          
          ldi r31 , HIGH(data)
          ldi r30 , LOW(data)
          ldi r20 , data_end-data
	call send_bytes
          brcs error
          SUCCESS_LED_ON
          ret
error:          
	ERROR_LED_ON
	ret
; ====
; reset bus
; =====
reset:
	sbi DDRD,CLK_PIN ; set to output
	sbi DDRD,TX_PIN ; set to output
	sbi DDRD,ERROR_LED ; set to output
	sbi DDRD,SUCCESS_LED ; set to output
	cbi DDRB,TRIGGER_PIN ; set to input
          ERROR_LED_OFF
          SUCCESS_LED_OFF
	CLK_HI 
	DATA_HI
	ret

; ======
; wait for button press 
; ======
wait_for_button:
          ldi r18,255
          call usleep
wait_released:
          sbic PINB , TRIGGER_PIN
          rjmp wait_released
wait_pressed:
          sbis PINB , TRIGGER_PIN
          rjmp wait_pressed
          ret

; ====== send bytes
; Assumption: CLK HI , DATA HI when method is entered
; INPUT: r31:r30 (Z register) start address of bytes to transmit
; INPUT: r20 - number of bytes to transmit
; SCRATCHED: r0,r1,r16,r17,r18
; RETURN: Carry clear => transmission successful , Carry set => Transmission failed
; ======
send_bytes:
; generate start sequence
          DATA_LO
          ldi r18 , 4 ; wait 4 us
          call usleep          
send_loop:     lpm r2 , Z+
          call send_byte
; CLK = HI , DATA = LOW
          brcs tx_error
          dec r20
	brne send_loop          
	rcall send_stop
          clc
          ret
tx_error:
          rcall send_stop
          sec
          ret
; =======
; send STOP sequence
; =======
send_stop:
	DATA_HI
          ldi r18, 5 ; wait 5 us
          call usleep	
          ret
; ====================
; Send the byte in r2
; INPUT: r2 - byte to send
; RETURN: Carry clear => byte acknowledged by receiver , Carry set => Byte not acknowledged
; SCRATCHED: r0,r1,r4,r16,r17,r18,r19
; ====================
send_byte:
          ldi r19,8 ; number of bits to transmit
bitbang:
	CLK_LO
	ldi r18, 2 ; wait 2 us
          call usleep
	lsl r2 ; load bit #7 into carry
	brcc transmit0
; transmit 1-bit	
	DATA_HI
	jmp cont
transmit0:
          DATA_LO            
cont:
          ldi r18 , 3 ; wait 3 us while holding clk lo
          call usleep  
	CLK_HI
          ldi r18 , 4 ; wait 4 us while holding clk hi
          call usleep           
          dec r19     ; decrement bit counter
          brne bitbang ; => more bits to send
; ACK phase starts , CLK is HI here
	CLK_LO
	DATA_LO

          ldi r18 , 5 ; wait 5 us while holding clk lo
          call usleep  	

	CLK_HI

          ldi r18 , 2 ; wait 2 us while holding clk HIGH
          call usleep  	

	call sample_data ; sample data line
	brcs ok1 ; carry set => line is HIGH

          ldi r18 , 3 ; wait 3 us while holding clk HIGH
          call usleep  	

	call sample_data ; sample data line
	brcs ok2 ; carry set => data line is HIGH
          sec ; no ACK received
          ret
ok1:
          ldi r18 , 3 ; wait 3 us while holding clk HIGH
          call usleep  	
ok2:	
          clc
          ret 
          
; =========
; sleep for up to 255 micro seconds
;
; >>>> Must NEVER be called with a value less than 2 us (infinite loop) <<<<

; IN: r18 = number of microseconds to sleep
; SCRATCHED: r0,r1,r16,r17,r18
;
; Total execution time: 
; +1 cycles for caller having to load the R18 register with time to wait
; +4 cycles for CALL invoking this method
; +5 cycles for calculating cycle count
; +4 cycles for RET 
; =========
usleep:
          ldi r17 , cycles_per_us ; 1 cycle
          mul r18 , r17 ; 1 cycle , result is in r1:r0     
          movw r27:r26 , r1:r0 ; 1 cycle
          sbiw r27:r26,14  ; 2 cycles , adjust for cycles spent invoking this method + preparation  
usleep2:  sbiw r27:r26,4 ; 2 cycles , subtract 4 cycles per loop iteration      
	brpl usleep2 ; 2 cycles, 1 cycle if branch not taken
exit:
	ret ; 4 cycles

; ======
; Sample status of CLK line and set carry bit accordingly (Carry set = CLK HIGH)
; duration: 7 cycles
; ======
sample_clock:
          clc ; 1 cycle , clear carry 
          cbi DDRD , CLK_PIN ; 2 cycles , switch CLK pin to INPUT
          sbic PIND , CLK_PIN ; 1/2 cycles , skip next insn if bit is clear
          sec ; 1 cycle
          sbi DDRD , CLK_PIN ; 2 cycles , switch CLK pin to OUTPUT
          ret ; 4 cycles   

; ======
; Sample status of DATA line and set carry bit accordingly (Carry set = DATA HIGH)
; duration: 7 cycles
; ======
sample_data:
          clc ; 1 cycle , clear carry 
          cbi DDRD , TX_PIN ; 2 cycles , switch TX_PIN pin to INPUT
          sbic PIND , TX_PIN ; 1/2 cycles , skip next insn if bit is clear
          sec ; 1 cycle
          sbi DDRD , TX_PIN ; 2 cycles , switch TX_PIN pin to OUTPUT
          ret ; 4 cycles  

data:     .db %11101111,%11001100
data_end: