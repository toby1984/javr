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

// #define DISPLAY_I2C_ADDR %01111000

; =========
; send a single byte with error checking
; INPUT: r24 - byte to send
; SCRATCHED: r24,r25
; RETURN: r24 - 0 => no error, 1 = error
; =========
i2c_send_byte:
          rcall send_byte
          brcs error
          clr r24         
	ret
.error
          ldi r24,1
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
; RETURN: r24 - 0 => no error, 1 = error
; =========
i2c_send_start:
          rcall send_start
          brcs error
          clr r24         
          eor r24,r24
	ret
.error
          ldi r24,1
          ret

; ===================
;  Send Start command and slave address 
; SCRATCHED: r24,r25
; RETURN: r24 - 0 => no error, 1 = error
; ====================

send_start:
; send START
          ldi r25, (1<<TWINT)|(1<<TWSTA)|(1<<TWEN) 
          sts TWCR, r25
; wait for START transmitted
.wait_start
          lds r25,TWCR
          sbrs r25,TWINT 
          rjmp wait_start

; check for transmission error
          lds r25,TWSR
          andi r25, 0xF8 
	cpi r25, 0x08 ; 0x08 = status code: START transmitted 
	brne send_failed
; transmit address (first byte)
;          ldi r24,DISPLAY_I2C_ADDR
          sts TWDR, r24
          ldi r25, (1<<TWINT) | (1<<TWEN) 
          sts TWCR, r25
; wait for address transmission
.wait_adr
	lds r25,TWCR
	sbrs r25,TWINT 
	rjmp wait_adr
; check status
	lds r25,TWSR
	andi r25, 0xF8 
	cpi r25, 0x18 ; 0x18 = status code: Adress transmitted,ACK received
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