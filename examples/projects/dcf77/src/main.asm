#include "m328Pdef.inc"

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

.equ CPU_FREQ = 8000000 ; 8 Mhz

; SPI slave-select output pins on PortB to
.equ SPI_SS_ETHERNET_BIT = 0
.equ SPI_SS_DCF77_BIT = 1
.equ SPI_SS_ETHERNET = 1<<SPI_SS_ETHERNET_BIT
.equ SPI_SS_DCF77 = 1<<SPI_SS_DCF77_BIT

; Number of cycles to wait before assuming no more data will be sent
.equ SPI_READ_TIMEOUT_CYCLES = 1000

; scratch registers
.def scratch0 = r17
.def scratch1 = r18

; ========================
; HW init
; ========================
init:
; clear status register
  eor scratch0,scratch0
  out 0x3f,scratch0
; initialize stack pointer  
  ldi scratch0,0x08
  out 0x3e,scratch0	; SPH = 0x08
  ldi scratch0,0xff  
  out 0x3d,scratch0	; SPL = 0xff
; call main program
  call main
again:
  rjmp again
onirq:
  jmp 0x00

main:
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
  ldi scratch0,(1<<PB3)|(1<<PB5)|SPI_SS_ETHERNET|SPI_SS_DCF77
  out  DDRB,scratch0
; set SPI SS pins HIGH (inactive)
  ldi scratch0, (SPI_SS_ETHERNET|SPI_SS_DCF77)
  out PORTB, scratch0
; Enable SPI, Master, set clock rate fck/4
  ldi scratch0,(1<<SPE)|(1<<MSTR)
  out  SPCR,scratch0
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
; INPUT: r0 - byte to send
; RESULT: r0 - second received byte
;         r1 - 0 -> success, 0xff = timeout during read
;         r2 - first received byte
; SCRATCHED: scratch0, r0,r1,r2
; =======
spi_transmit_eth_1w2r: ; 1 byte written, 2 bytes read
  cbi PORTB, SPI_SS_ETHERNET_BIT ; slave-select low
  call spi_tx_delay
  mov r16, r0
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

; ========
; Receives one byte via SPI
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