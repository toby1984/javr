#include "m328Pdef.inc"

.equ freq = 16000000
.equ target_freq = 100000

.equ cycles_per_us = freq / 1000000

.equ delay_in_cycles = (freq / target_freq)/2

.equ loop_count = (delay_in_cycles-4)/5 ; 3 cycles for register loads + 1 cycle for brne not taken

.equ loop_count_low = LOW(loop_count)
.equ loop_count_middle = HIGH(loop_count)
.equ loop_count_high = (loop_count & 0xff0000) >> 16

.equ TX_PIN = 1
.equ CLK_PIN = 2

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
	call start
	cli
forever: 
	rjmp forever
onirq:
	jmp 0x00
; ==========================
; main program starts here
; ==========================
start:
          call reset
          ldi r31 , HIGH(data)
          ldi r30 , LOW(data)
	call send_bytes
          ret

; ====
; reset bus
; =====
reset:
	sbi DDRB , CLK_PIN ; set to output
	sbi DDRB , TX_PIN ; set to output
	sbi PORTB , CLK_PIN ; CLK HI
	sbi PORTB , TX_PIN ; DATA HI
	ret

; ====== send bytes
; Assumption: CLK HI , DATA HI when method is entered
; INPUT: r31:r30 (Z register) start address of bytes to transmit
; INPUT: r20 - number of bytes to transmit
; SCRATCHED: r0,r1,r16,r17,r18
; RETURN: Carry clear => byte acknowledged by receiver , Carry set => Byte not acknowledged
; ======
send_bytes:
          cbi PORTB , TX_PIN ; DATA LOW
          ldi r18 , 4 ; wait 4 us
          call usleep          
send_loop:     lpm r2 , Z+
          call send_byte
; CLK = HI , DATA = LOW
          brcs tx_failed
          dec r20
          brne send_loop
          clc ; => transmission successful
	rjmp stop
tx_failed:
          sec ; => transmission failed
; send STOP sequence
stop:
	sbi PORTB , TX_PIN ; DATA HIGH
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
          ldi r19,7 ; number of bits to transmit
bitbang:
	cbi PORTB , CLK_PIN ; CLK LOW
	ldi r18, 2 ; wait 2 us
          call usleep
	lsl r2 ; load bit #7 into carry
	brcc transmit0
; transmit 1-bit	
	sbi PORTB , TX_PIN ; DATA HIGH
	rjmp cont
transmit0:
          cbi PORTB , TX_PIN ; DATA LOW               
cont:
          ldi r18 , 3 ; wait 3 us while holding clk lo
          call usleep  
	sbi PORTB , CLK_PIN ; CLK HI
          ldi r18 , 4 ; wait 4 us while holding clk hi
          call usleep           
          dec r19     ; decrement bit counter
          brne bitbang ; => more bits to send
; ACK phase starts , CLK is HI here
	cbi PORTB , CLK_PIN ; CLK LOW
	cbi PORTB , TX_PIN ; DATA LOW (release data line )

          ldi r18 , 5 ; wait 5 us while holding clk lo
          call usleep  	

	sbi PORTB , CLK_PIN ; CLK HIGH

          ldi r18 , 2 ; wait 2 us while holding clk HIGH
          call usleep  	

	call sample_data ; sample data line
	brcs ack_received ; carry set => line is HIGH

          ldi r18 , 3 ; wait 3 us while holding clk HIGH
          call usleep  	

	call sample_data ; sample data line
	brcc nack_received ; carry not set => line is still low
ack_received:
	clc
	cbi PORTB , TX_PIN ; DATA LOW
          ret 
nack_received:
          sec
	cbi PORTB , TX_PIN ; DATA LOW
          ret
          
; =========
; sleep for half a i2c clock cycle
; =========

sleep:
	ldi r18,loop_count_low ; 1 cycles
	ldi r24,loop_count_middle ; 1 cycles
	ldi r25,loop_count_high ; 1 cycles
loop2:
	subi r18,1 ; 1 cycles
	sbci r24,0 ; 1 cycles
	sbci r25,0 ; 1 cycles
	brne loop2 ; 2 cycles, 1 cycle if branch not taken
          ret
; =========
; sleep for up to 255 micro seconds
;
; >>>> Must not be called with a value less than 2 us <<<<

; IN: r18 = number of microseconds to sleep
; SCRATCHED: r0,r1,r16,r17,r18
;
; Total execution time: 
; +1 cycles for loading R18 register 
; +4 cycles for CALL invoking this method
; +6 cycles for calculating cycle count
; +4 cycles for RET 
; =========
usleep:
          ldi r17 , cycles_per_us ; 1 cycle
          mul r18 , r17 ; 1 cycle , result is in r1:r0     
          movw r18:r17 , r1:r0 ; 1 cycle
          subi r17 , 16 ; 1 cycle , adjust for cycles spent invoking this method + preparation
          sbci r18 , 0 ; 1 cycle     
; divide by 4 cycles (=time a single loop iteration takes)
          lsr r18 ; 1 cycle
          ror r17 ; 1 cycle
usleep2:  subi r17,1 ; 1 cycle
          sbci r18,0 ; 1 cycle          
	brne usleep2 ; 2 cycles, 1 cycle if branch not taken
	ret ; 4 cycles

; ======
; Sample status of CLK line and set carry bit accordingly (Carry set = CLK HIGH)
; duration: 7 cycles
; ======
sample_clock:
          clc ; 1 cycle , clear carry 
          cbi DDRB , CLK_PIN ; 2 cycles , switch CLK pin to INPUT
          sbic PINB , CLK_PIN ; 1/2 cycles , skip next insn if bit is clear
          sec ; 1 cycle
          sbi DDRB , CLK_PIN ; 2 cycles , switch CLK pin to OUTPUT
          ret ; 4 cycles   

; ======
; Sample status of DATA line and set carry bit accordingly (Carry set = DATA HIGH)
; duration: 7 cycles
; ======
sample_data:
          clc ; 1 cycle , clear carry 
          cbi DDRB , TX_PIN ; 2 cycles , switch TX_PIN pin to INPUT
          sbic PINB , TX_PIN ; 1/2 cycles , skip next insn if bit is clear
          sec ; 1 cycle
          sbi DDRB , TX_PIN ; 2 cycles , switch TX_PIN pin to OUTPUT
          ret ; 4 cycles  

data:     .db 01,02 