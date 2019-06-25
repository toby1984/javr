#include "m328Pdef.inc"
#include "enc28j60.asm"

.equ CPU_FREQ = 16000000 ; 16 Mhz
.equ PRESCALE_FACTOR = 1024

.equ RECEIVE_TIMEOUT_SECONDS = 3
  
; number of  16-bit TIMER ticks before we 
; assume we got no signal
.equ RECEIVE_TIMEOUT_TICKS = (RECEIVE_TIMEOUT_SECONDS*CPU_FREQ)/PRESCALE_FACTOR
  
.equ RED_LED_BIT = PB0
.equ RED_LED_MASK = 1<<RED_LED_BIT
  
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
  jmp dcf77_timeout_irq ; TIMER1_CAPT - timer/counter 1 capture event
  jmp dcf77_timeout_irq ; TIMER1_COMPA
  jmp dcf77_timeout_irq ; TIMER1_COMPB
  jmp dcf77_timeout_irq ; TIMER1_OVF
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
  eor scratch0,scratch0
  out 0x3f,scratch0
; initialize stack pointer  
  ldi scratch0,0x08  
  out 0x3e,scratch0  ; SPH = 0x08
  ldi scratch0,0xff  
  out 0x3d,scratch0  ; SPL = 0xff
; call main program
  call main
again:
  rjmp again
onirq:
  jmp 0x00

main:
;  call spi_init ; enable SPI master mode 0
;  call eth_init
  ldi r16,1
  out DDRB,r16

  sei  
  call dcf77_init

.loop
  rjmp loop  
  
; ======================
; DCF77 part
; Uses the analog comparator to detect
; rising edge  
; ======================  
  
dcf77_init:  
;  call setup_acomp
; setup 16-bit timer for timeout interrupt generation
  call dcf77_setup_timeout_irq  
  ret
  
; ======================
; Setup analog converter
; ======================  
dcf77_setup_acomp:
; setup analog comparator  
  ldi r16,0
  sts ADCSRB,r16
; TODO: More Comparator setup needed ???  
  ret  
  
; ======================
; Setup Timer 1 for tracking
; "no signal received" timeout.
;
; The timer will run in CTC mode
; (counting upwards until the MAX value is reached
; trigger an IRQ and the restart counting up at zero).
;
; The IRQ handler will just store the fact that a 
; timeout happened in a SRAM location      
; ======================    
dcf77_setup_timeout_irq:    
; clear 'no signal received' timeout
  ldi r16,0
  sts no_signal_timeout,r16
  
; setup upper bound of 16-bit timer to trigger IRQ
  ldi r16,HIGH(RECEIVE_TIMEOUT_TICKS)
  sts OCR1AH,r16
  ldi r16,LOW(RECEIVE_TIMEOUT_TICKS)
  sts OCR1AL,r16
  
; setup 16-bit timer prescaler
  ldi r16,%1101 ; --> clk/1024, WGM12 = 1, WGM13 = 0
  sts TCCR1B,r16  
; clear pending IRQ
  ldi r16,%100
  out TIFR1,r16 ; Clear TOV1/ Clear pending interrupts  
; Enable Timer 1 Overflow interrupt
  ldi r16,%100
  sts TIMSK1,r16 ; TOIE - Timer Overflow Interrupt Enable 
; Reset 16-bit timer counter to zero
  clr r16
  sts TCNT1H,r16
  sts TCNT1L,r16  
  ret
  
; ====
; Invoked every time the
; 16-bit timer overflows
; ====   
  
.irq 13 ; TODO: Are the IRQ vector numbers zero-based or one-based ?
dcf77_timeout_irq:
  push r16
  in r16,SREG
  push r16
;--  
  lds r16,no_signal_timeout
  inc r16
  brne cont
  ldi r16,1
.cont  
  sts no_signal_timeout,r16
  call debug_toggle_red_led
; ---  
  pop r16
  out SREG,r16
  pop r16    
  reti
  
debug_toggle_red_led:
  sbic   PINB, RED_LED_BIT
  cbi    PORTB, RED_LED_BIT

  sbis   PINB, RED_LED_BIT
  sbi    PORTB, RED_LED_BIT
  ret      
  
debug_red_led_on:
  sbi    PORTB, RED_LED_BIT
  ret
  
debug_red_led_off:
  cbi PORTB, RED_LED_BIT
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
  
; =====
; SRAM data
; =====    
.dseg
  
no_signal_timeout: 
  .byte 1  
  