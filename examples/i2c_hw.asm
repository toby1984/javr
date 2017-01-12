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
          cli
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
          
          ldi r31 , HIGH(cmd1)
          ldi r30 , LOW(cmd1)
          ldi r20 , cmd2-cmd1
	call send_bytes
          brcs error

          ldi r31 , HIGH(cmd2)
          ldi r30 , LOW(cmd2)
          ldi r20 , cmd2_end-cmd2
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
;          setup TWI rate 
          lds r16,TWSR
          andi r16,%11111100
          sts TWSR,r16 ; prescaler bits = 00 => factor 1x
          ldi r16,72
          sts TWBR,r16 ; factor
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
; send START
          ldi r16, (1<<TWINT)|(1<<TWSTA)|(1<<TWEN) 
          sts TWCR, r16
; wait for START transmitted

wait_start: 
          lds r16,TWCR
          sbrs r16,TWINT 
          rjmp wait_start

; check for transmission error
          lds r16,TWSR
          andi r16, 0xF8 
	cpi r16, 0x08 ; 0x08 = status code: START transmitted 
	brne send_failed
; transmit address (first byte)
          lpm r16,Z+
          sts TWDR, r16 
          ldi r16, (1<<TWINT) | (1<<TWEN) 
          sts TWCR, r16
; wait for address transmission
wait_adr: 
	lds r16,TWCR
	sbrs r16,TWINT 
	rjmp wait_adr
; check status
	lds r16,TWSR
	andi r16, 0xF8 
	cpi r16, 0x18 ; 0x18 = status code: Adress transmitted,ACK received
	brne send_failed
data_loop:
          dec r20
          breq end_transmission
          lpm r16,Z+
	sts TWDR, r16 
	ldi r16, (1<<TWINT) | (1<<TWEN) 
	sts TWCR, r16                    
wait_data:
	lds r16,TWCR 
	sbrs r16,TWINT 
	rjmp wait_data
; check transmission
	lds r16,TWSR
	andi r16, 0xF8 
	cpi r16, 0x28 ; 0x28 = status code: data transmitted,ACK received 
	brne send_failed
          rjmp data_loop
; transmission successful
end_transmission:
	ldi r16, (1<<TWINT)|(1<<TWEN)| (1<<TWSTO) 
	sts TWCR, r16         	
          clc
          ret
send_failed:
	sec
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
  
cmd1:     .db %01111000,0xa5
cmd2:     .db %01111000,0xaf
cmd2_end: