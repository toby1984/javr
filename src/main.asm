;
; Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
; http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;

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

spi_init:
; Port B - Set MOSI and SCK output, all others input
; MOSI = PB3 
; SCK =  PB5
; MISO = PB4
; SS = PB2
; DDR = 1 -> OUTPUT PIN
; DDR = 0 -> INPUT PIN
  ldi r17,(1<<PB3)|(1<<PB5)
  out  DDRB,r17
; Enable SPI, Master, set clock rate fck/4
  ldi r17,(1<<SPE)|(1<<MSTR)
  out  SPCR,r17
  ret

spi_transmit:
; Start transmission of data (r16)
  out  SPDR,r16
Wait_Transmit:
; Wait for transmission complete
  in  r16, SPSR
  sbrs r16, SPIF
  rjmp Wait_Transmit
  ret
          