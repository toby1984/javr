#include "m328Pdef.inc"
#include "enc28j60.asm"

.equ CPU_FREQ = 16000000 ; 16 Mhz

.equ TIMER0_PRESCALE_FACTOR = 256
    
; NOTE: Prescaler needs to be configured so that 
; a 16 bit counter covers roughly 2.5 - 3 seconds
; as the DCF77 signal denotes the start of a new minute
; by skipping the pulse on the 59th second
; and we need to be able to reliably detect this
; 2 second gap (even if the Arduino's clock is not that stable)
.equ TIMER1_PRESCALE_FACTOR = 1024

.equ TIMER1_TICKS_PER_SECOND = CPU_FREQ / TIMER1_PRESCALE_FACTOR
  
.equ TIMER1_TICKS_2_SECONDS = 2 * TIMER1_TICKS_PER_SECOND
  
.equ TIMER1_THRESHOLD = TIMER1_TICKS_PER_SECOND*0.2
  
.equ TIMER1_2_SECONDS_LOW_THRESHOLD = TIMER1_TICKS_2_SECONDS - TIMER1_THRESHOLD
.equ TIMER1_2_SECONDS_HIGH_THRESHOLD = TIMER1_TICKS_2_SECONDS + TIMER1_THRESHOLD

.equ TIMER1_1_SECOND_LOW_THRESHOLD = TIMER1_TICKS_PER_SECOND - TIMER1_THRESHOLD
.equ TIMER1_1_SECOND_HIGH_THRESHOLD = TIMER1_TICKS_PER_SECOND + TIMER1_THRESHOLD
  
.equ RED_LED_BIT = PB0
.equ GREEN_LED_BIT = PB1  
.equ YELLOW_LED_BIT = PB2
      
; SPI slave-select output pins on PortB to
.equ SPI_SS_ETHERNET_BIT = 0
.equ SPI_SS_ETHERNET = 1<<SPI_SS_ETHERNET_BIT

; Number of cycles to wait before assuming no more data will be sent
.equ SPI_READ_TIMEOUT_CYCLES = 1000
  
; scratch registers
.def scratch0 = r24
.def scratch1 = r25

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
  jmp timer1_overflow ; TIMER1_COMPA
  jmp onirq ; TIMER1_COMPB
  jmp onirq ; TIMER1_OVF
  jmp onirq ; TIMER0_COMPA
  jmp onirq ; TIMER0_COMPB
  jmp timer0_overflow ; TIMER0_OVF
  jmp onirq ; STC - serial transfer complete (SPI)
  jmp onirq ; USUART Rx complete
  jmp onirq ; USUART Data register empty
  jmp onirq ; USUART Tx complete
  jmp onirq; ADC conversion complete
  jmp onirq ; EEPROM ready
  jmp dcf77_acomp_irq ; Analog comparator
  jmp onirq ; 2-wire interface I2C
  jmp onirq ; Store program memory ready

; ========================
; HW init
; ========================
init:
; clear status register
  eor scratch0,scratch0
  out 0x3f,scratch0
; initialize stack pointer  
  ldi scratch0,0x08  
  out 0x3e,scratch0  ; SPH = 0x08
  ldi scratch0,0xff  
  out 0x3d,scratch0  ; SPL = 0xff
    
; switch port B to output for red and green LEDs
  ldi r16,(1<<RED_LED_BIT)|(1<<GREEN_LED_BIT)|(1<<YELLOW_LED_BIT)
  out DDRB,r16

  rcall init_sram
;  call spi_init ; enable SPI master mode 0
;  call eth_init  
  
  rcall dcf77_init

; main loop - wait for IRQ to 
; tell us to send a packet
mainloop:
  lds r16, send_packet
  tst r16
  breq mainloop
  clr r16
  sts send_packet, r16 
; TODO: Send UDP packet
  rcall green_led_toggle
  rjmp mainloop
  
onirq:
  jmp 0x00
  
; ======================
; DCF77 part
; Uses the analog comparator to detect
; rising edge on input pin
; ======================  
  
dcf77_init:  
  cli
  rcall dcf77_setup_acomp    
  rcall setup_timer0  
  rcall setup_timer1  
  rcall restart_timers
  sei
  ret
  
; ======================
; Setup analog converter
; ======================  
dcf77_setup_acomp:
; disable pull-ups
  ldi r16,0
  out DDRD,r16
  ldi r16,0
  out PORTD,r16  
; disable digital input buffers  
  ldi r16,%11
  sts DIDR1,r16
  ; setup analog comparator    
  ldi r16,%0001_1011
  out ACSR,r16
  ret  
  
  .irq 23
dcf77_acomp_irq:
  push r16 ; preserve r16
  in r16,SREG
  push r16 ; preserve SREG
  push r17 ; preserve r17
  push r18 ; preserve r18
  push r19 ; preserve r19
  push r20
;-- START IRQ routine  
  
; read current TIMER0 value  
  in r16, TCNT0
  lds r17, timer0_overflows
  lds r18, timer0_overflows+1
  rcall red_led_on
; divide by 4 as TIMER0 runs at clk/256
; while timer1 runs at clk/1024
  lsr r18
  ror r17  
  ror r16
; sanity check value  
  lsr r18
  brne out_of_range ; value >  0xffff  
  ror r17  
  ror r16  
; if we're not IN_SYNC yet, just wait for a two-second
; gap to be detected
  lds r19, is_in_sync
  tst r19
  brne cont
  rcall red_led_on
  rjmp check_two_seconds_elapsed    
.cont
; TIMER1 is running at clk/1024 so will tick
; 15625 times a second (=every 0,064 ms) and thus 
; cover a time range of 0..4194,304 ms. 
  lds r19, current_second
; DCF77 does not send a pulse on the
; 59th second to indicate the start of 
; a new minute  
  cpi r19, 59
  breq check_two_seconds_elapsed
  
; roughly one second should've elapsed, check it!  
  movw r20:r19, r17:r16
  subi r19,LOW( TIMER1_1_SECOND_HIGH_THRESHOLD )
  sbci r20, HIGH( TIMER1_1_SECOND_HIGH_THRESHOLD )
  brpl out_of_range
  
  movw r20:r19, r17:r16
  subi r19,LOW(TIMER1_1_SECOND_LOW_THRESHOLD )
  sbci r20, HIGH( TIMER1_1_SECOND_LOW_THRESHOLD )
  brlo out_of_range  
  
; ok, increment current second by 1
; (we know we're not at 59 seconds here so no need to check for wrap-around)
  lds r18, current_second
  inc r18
  sts current_second, r18
; use new TIMER1 MAX and leave IRQ handler  
  rjmp update_timer1_max
          
; roughly two seconds should've elapsed, check it!     
.check_two_seconds_elapsed
  movw r20:r19, r17:r16
  subi r19,LOW( TIMER1_2_SECONDS_HIGH_THRESHOLD )
  sbci r20, HIGH( TIMER1_2_SECONDS_HIGH_THRESHOLD )
  brpl out_of_range    

  movw r20:r19, r17:r16
  subi r19,LOW( TIMER1_2_SECONDS_LOW_THRESHOLD )
  sbci r20, HIGH( TIMER1_2_SECONDS_LOW_THRESHOLD )
  brlo out_of_range    
  
; set state to IN_SYNC  
  ldi r18, 0xff
  sts is_in_sync,r18
  sts warmup_done,r18
  rcall red_led_off
; set current_second to 00  
  clr r18
  sts current_second,r18
; do not use the length of this 2 second interval
; to update the TIMER1 MAX value, we'll just 
; use what we already have to avoid increased jitter
  rjmp leave_irq
  
.out_of_range   
  rcall red_led_on
  clr r18
  sts is_in_sync,r18
  rjmp leave_irq

; only update ticks_per_second
; if the last pulse was not received too long ago
; so we don't use a bogus value      
.update_timer1_max
  sts ticks_per_second, r16
  sts ticks_per_second+1,r17
  
.leave_irq
  call yellow_led_toggle
; restart timers in sync  
  rcall restart_timers
; ---  END IRQ routine
  pop r20
  pop r19 ; restore r19
  pop r18 ; restore r18  
  pop r17 ; restore r17
  pop r16 ; restore SREG
  out SREG,r16 
  pop r16 ; restore r16
  reti
    
; ====
; Invoked every time the
; 16-bit timer overflows
; ====   
  
.irq 12 
; TODO: Are the IRQ vector numbers zero-based or one-based ?
  
timer1_overflow:
  push r16
  in r16,SREG
  push r16
;-- START IRQ routine
  rcall green_led_toggle
  ldi r17,0xff ; r17 contains value written to 'send_packet' flag
  lds r16, warmup_done
  tst r16
  breq never_synced_yet
  ldi r16,0xff
.leave_irq
  sts send_packet,r16  
; ---  END IRQ routine  
  pop r16
  out SREG,r16
  pop r16    
  reti
  
.never_synced_yet
  clr r16
  rjmp leave_irq
  
red_led_on:
  sbi PORTB, RED_LED_BIT
  ret
  
green_led_on:
  sbi PORTB, GREEN_LED_BIT
  ret
  
red_led_off:
  cbi PORTB, RED_LED_BIT
  ret
  
red_led_toggle:
  sbi PINB, RED_LED_BIT
  ret   
  
green_led_toggle:
  sbi PINB, GREEN_LED_BIT
  ret      

yellow_led_toggle:
  sbi PINB, YELLOW_LED_BIT
  ret
; ======================
; Setup TIMER0 to count up continously
; (used to measure time between DCF77 pulses).
;
; As we want need to measure times of up two
; 2 seconds (DCF77 transmits no pulse at the 59th second
; to indicate the start of the next minute) we're
; going to use a clk/256 prescaler setting and
; a 24-bit counter here.
; 
; Assuming 16 MHz CPU speed this will be enough
; to cover ~268 seconds (16MHz/256 = 62500 ticks per second)
; ======================

setup_timer0:
; enable overflow IRQ  
  ldi r16,1
  sts TIMSK0,r16   
; setup clk/256 prescaler and start timer
  ldi r16,%100
  out TCCR0B, r16    
  ret 
      
timer0_overflow:
  push r24 ; preserve r24
  in r24,SREG
  push r24 ; preserve SREF
  push r25 ; preserve r25
  
; start of actual IRQ handling
  
; increment 24-bit overflow counter
; (the lowest 8 bits are stored in TCNT0)
  lds r24, timer0_overflows
  lds r25, timer0_overflows+1
  adiw r25:r24,1
  sts timer0_overflows,r24
  sts timer0_overflows+1,r25

; end of IRQ handling
  pop r25 
  pop r24
  out SREG,r24
  pop r24
  reti  
  
; ======================
; Setup 16-bit TIMER1 to count up in CTC mode 4
; and trigger an IRQ when OCR1A is reached.
;
; The actual MAX value to use will be measured by 
; having the analog comparator trigger on a DCF77
; pulse and calculate the elapsed time since the
; previous pulse. If this value is within the expected
; range, the TIMER1 MAX value will be set to it.
;
; Scratched: r16  
; ======================    
setup_timer1:    
     
; Enable IRQ on OCR1A match
  ldi r16,%10
  sts TIMSK1,r16
; set clk/1024 prescaler and CTC mode 4, start timer
  ldi r16,%1101 
  sts TCCR1B,r16  
  ret
   
restart_timers:
; disable prescaler for TIMER0 and TIMER1 (=stop both timers)
  clr r17
  ldi r16,%1000_0001
  out GTCCR, r16
; reset 24-bit TIMER0 value
  out TCNT0,r17   
; clear upper 2 bytes
  sts timer0_overflow,r17
  sts timer0_overflow+1,r17
; load TIMER1 MAX
  lds r16,ticks_per_second+1
  sts OCR1AH,r16
  lds r16,ticks_per_second
  sts OCR1AL,r16   
; reset TIMER1 counter value  
  sts TCNT1H,r17
  sts TCNT1L,r17
; clear any pending OVERFLOW interrupts 
  ldi r16,1
  out TIFR0, r16  
  ldi r16,%10
  out TIFR1, r16 ; Clear TOV1/ Clear pending interrupts  
; re-enable prescaler for TIMER0 and TIMER1 (=start both at the same time)
  out GTCCR, r17
  ret
    
; ======================
; ENC28J60 interfacing
; ======================

eth_init:
; prepare for sending
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
; >>>>>>>>>>>>>>>>>>>>>>>>>> TODO: Implement initialization <<<<<<<<<<<<<<<<<<<<
; ENC28J60 has 8 KB of internal buffer,
; all buffer not setup for receiving is used for transmission
; >>>>>>>>>>>>>>>>>>>>>>>>>> TODO: Implement initialization <<<<<<<<<<<<<<<<<<<<
; done
  sbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select hi  
  ret

; ===================
; >>> Careful, this method does NOT touch slave-select <<<
; Write to control register in current bank
;
; INPUT: r16 - register to write to
;        r17 - value to write        
; SCRATCHED: r16, scratch0
; ===================
eth_write_ctrl_register_no_ss:
  andi r16,%00011111
  ori r16 ,%10100000
  call spi_transmit
  mov r16, r17
  call spi_transmit
  ret

; =============
; >>> Careful, this method does NOT touch slave-select <<<
; Switch to a given memory bank
; INPUT: r2 - Bank to switch to
; RESULT: r1 - 0 -> success, 0xff = timeout during read 
; SCRATCHED: r1,r16, scratch0
; ============
eth_switch_bank_no_ss:
; read ECON1
  ldi r16,ETH_ECON1
  call spi_transmit
  call spi_receive
  tst r1            ; check for timeout error
  brne error
; r0 contains current ECON1 register value
  mov scratch0,r0
  andi scratch0, %111 ; mask everything except the current bank number
  cp scratch0,r2 ; check whether the desired bank is already enabled
  breq success ; yes, nothing to do
; 
  mov r16,r0
  andi r16, %11111000 ; clear bank-select bits
  or r16,r2
  call spi_transmit

.success
  clr r1
  ret  
.error
  ret  


; =======
; ECN28J60 SPI transmit 1 byte, read nothing
; INPUT: r0 - byte to send
; SCRATCHED: scratch0, r0
; =======
spi_transmit_eth_1w0r: ; 1 byte written, zero bytes read
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
  mov r16, r0 ; r16 = r0
  call spi_transmit
  sbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select hi
  ret

; =======
; ECN28J60 SPI transmit 1 byte, read 1 byte
; INPUT: r0 - byte to send
; RESULT: r0 - received byte
;         r1 - 0 -> success, 0xff = timeout during read
; SCRATCHED: scratch0, r0
; =======
spi_transmit_eth_1w1r: ; 1 byte written, 1 byte read
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
  mov r16, r0
  call spi_transmit
  call spi_receive
  sbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select hi  
  ret

; =======
; ECN28J60 SPI transmit 1 byte, read 2 bytes
; INPUT: r16 - byte to send
; RESULT: r0 - second received byte
;         r1 - 0 -> success, 0xff = timeout during read
;         r2 - first received byte
; SCRATCHED: scratch0, r0,r1,r2
; =======
spi_transmit_eth_1w2r: ; 1 byte written, 2 bytes read
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
  call spi_transmit
  call spi_receive
  tst r1
  brne error
  mov r2,r0
  call spi_receive
.error
  sbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select hi  
  ret

; =======
; Writes up to 255 bytes from SRAM to ECN28J60 buffer
; INPUT:
; r0 - number of bytes to write
; r31:r30 (Z) - Pointer to SRAM
; SCRATCHED: r0,scratch0
; =======
spi_transmit_eth_write_buffer:
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
.tx_loop
  LD r16,Z+
  call spi_transmit
  dec r0
  brne tx_loop
  sbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select hi  
  ret

; ======================
; SPI interfacing 
; ======================

; With non-inverted clock polarity (i.e., the clock is at logic low when slave select transitions to logic low):
;
; Mode 0: Clock phase is configured such that data is sampled on the rising edge of the clock pulse and shifted out on the falling edge of ;   the clock pulse. 
; This corresponds to the first blue clock trace in the above diagram. Note that data must be available before the first rising edge of the clock.

; =======
; Init SPI in mode 0
; SCRATCHED: scratch0
; =======
spi_init:
; Port B - Set MOSI and SCK output, all others input
; MOSI = PB3 
; SCK =  PB5
; MISO = PB4
; SS = PB2
; DDR = 1 -> OUTPUT PIN
; DDR = 0 -> INPUT PIN
  in scratch0, DDRB
  ori scratch0,(1<<PB3)|(1<<PB5)|SPI_SS_ETHERNET
  out  DDRB,scratch0
; set SPI SS pins HIGH (inactive)
  ldi scratch0, SPI_SS_ETHERNET
  out PORTB, scratch0
; Enable SPI, Master, set clock rate fck/4
  ldi scratch0,(1<<SPE)|(1<<MSTR)
  out  SPCR,scratch0
  ret

; ========
; Receives one byte via SPI.
;
; RESULT: r0 - byte that was read
;         r1 - 0 -> success, 0xff = timeout during read
; SCRATCHED: scratch0,scratch 1
; ========
spi_receive:
  ldi scratch0, LOW(SPI_READ_TIMEOUT_CYCLES)
  ldi scratch1, HIGH(SPI_READ_TIMEOUT_CYCLES)
  clr r1 ; assume success
.loop
  ; wait for SPIF flag to be set
  in r0,SPSR
  sbrc r0, SPIF
  rjmp leave ; -> SPIF set
  
  dec scratch0
  brne skip1
  dec scratch1
  brne skip2
  com r1 ; timeout error
  ret
.skip2
  ldi scratch0, 0xff
.skip1
  rjmp loop
.leave
; Read received data and return
; the SPIF bit is cleared by first reading the
; SPI Status Register with SPIF set, then accessing the SPI Data Register (SPDR).
  in r0,SPDR
  ret

; =========
; SPI send byte
; INPUT: r16 - data to send
; SCRATCHED: scratch0
; =========
spi_transmit:
  out  SPDR,r16
; wait for completion
.wait
  in  scratch0, SPSR
  sbrs scratch0, SPIF
  rjmp wait
  in scratch0, SPDR ; read data register to clear SPIF flag
  ret

; ====
; Used to delay actual transmission
; after slave-select goes low
; Spec demands at least 120ns delay
; ====
spi_tx_delay:
  nop
  ret
  
init_sram:
  clr scratch0
; clear 'send packet' flag
  sts send_packet, scratch0
; clear 'warmup done' flag
  sts warmup_done, scratch0
; clear current_second  
  sts current_second, scratch0
; clear timer0 overflows
  sts timer0_overflows,scratch0   
  sts timer0_overflows+1,scratch0    
; clear last_pulse_ticks  
  sts last_pulse_ticks,scratch0
  sts last_pulse_ticks+1,scratch0    
; clear tickets_per_second
  sts ticks_per_second,scratch0  
  sts ticks_per_second+1,scratch0   
; reset ticks_per_second
  ldi scratch0, LOW(TIMER1_TICKS_PER_SECOND)
  sts ticks_per_second, scratch0
  ldi scratch0, HIGH(TIMER1_TICKS_PER_SECOND)
  sts ticks_per_second+1, scratch0  
; reset state  
  clr scratch0
  sts is_in_sync, scratch0
  ret
    
; =====
; SRAM data
; =====    
.dseg
  
; 24-bit elapsed TIMER0 time since the last DCF77 rising edge
last_pulse_ticks:
  .byte 3

; MAX value to be used by TIMER1 for
; generating 1-second pulses
ticks_per_second:
  .byte 2
  
; upper 2 byte of 24-bit counter
; (TIMER0 CNT value is the lowest/first byte)
timer0_overflows:
  .byte 2

; flag indicating we achieved synchronization 
; at least once so it's ok to generate 
; UDP packets whenever TIMER1 overflows
warmup_done:
  .byte 1
  
; flag indicating whether we detected a proper DCF77 signal
; (value != zero) or not (value == zero)
is_in_sync:
  .byte 1
  
; incremented each time we receive a DCF77 pulse
; so we know when the 2-second gap at the 59th second
; is going to happen (and we don't flag it as 'signal missing')
current_second:
  .byte 1
  
; flag indicating whether a new UDP time packet
; should be sent (value != 0)
send_packet:
  .byte 1