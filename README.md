## What's this ?

A crude attempt at an editor & assembler/disassembler for Atmel microcontrollers (although not for the tiny ones)... very much a work in progress.

Basic assembly/disassembly works properly (verified by comparing generated disassembly with avr-objdump output and disassembling and re-compiling large amounts of data that cover every possible opcode). 
Only a tiny fraction of the official AVR assembler directives is implemented (namely .db,.byte,.dw and .equ). The preprocessor currently only supports #define/#ifdef/#ifndef/#message/#info/#error but no #include or other goodies.
Macro expansion is also not implemented yet.
The editor is still very crude and besides background compilation as you type,disassemble,save,load,searching back & forth (using CTRL-k,CTRL-k CTRL-b) and navigating to a specific line number (CTRL-g) nothing else is implemented. I intend to add a rename refactoring, hyperlinking and some other goodies soon though...

<img src="https://raw.githubusercontent.com/toby1984/javr/master/screenshot.png" width="640" height="480" />

## Requirements

- Maven
- JDK >= 1.8

## Building

```
mvn clean package
```

## Running

```
java -jar target/javr.jar
``` 

## What's implemented

Note that for reasons unknown to me the AVR assembler duplicates a lot of preprocessor instructions as assembler directives ... I'm currently only implementing the preprocessor instructions

- full ATmega88 instruction set
- Expression correctly handles nested expressions and operator precedence for the following operators: 
  - arithmetic operators: -(unary+binary),+,/,*
  - bitwise operators: ~,<<,>>,|,&
  - logical operators: !,&&,||,>, <, >= , <= , == , !=
- built-in functions: HIGH(x), LOW(x) that yield the upper/lower 8 bits of a 16-bit word
- Assembler understands X / Y / Z as shorthand notation for r27:r26 / r29:r28 / r31:r30
- Assembler currently will NOT accept just specifying the lower register when an instruction expects a combined / 'compound' register 
  - use the syntax as given in the official Atmel ISA documentation instead
- Preprocessor instructions
  - #include
  - #if
  - #ifdef / #ifndef / #endif
  - #define
  - #message / #info / #warning
  - #pragma [ parsed but ignored ]
- Assembler directives
  - .byte
  - .cseg
  - .db
  - .def
  - .dw / .word
  - .dseg
  - .eseg
  - .equ
  

## What's not implemented


## Known issues

- Syntax coloring is off when using MSDOS-style line endings (won't fix this since it is related to the fact that Swing text components internally convert all EOL sequences to '\n' but my parser uses the 'true' text offsets)

## To do

- Add support for .org directive
- Add parse error recovery
- Still lots of testing needed...
- Add support for #else / #elseif
- Find a way for meaningful error reporting when expanding macros...
