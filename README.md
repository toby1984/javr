## What's this ?

A crude attempt at an editor & assembler/disassembler for Atmel microcontrollers (although not for the tiny ones)... very much a work in progress.

Basic assembly/disassembly works properly (verified by comparing generated disassembly with avr-objdump output and disassembling and re-compiling large amounts of data that cover every possible opcode). 
Not all preprocessor / assembler directives are implemented yet and the editor is still quite crude (supports background compilation,disassemble,save,load,searching back & forth (using CTRL-k,CTRL-k CTRL-b) and navigating to a specific line number (CTRL-g) nothing else is implemented. I intend to add a rename refactoring, hyperlinking and some other goodies soon though...

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
- local labels ( watch out, not working properly when used in macros) 
  - Just write '.myLabel' instead of 'myLabel:' when declaring them ; referencing local labels works just like with global labels so no 'b' or 'f' suffixes are needed. Note that its illegal to declare a local label that has the same identifier as a global label ; otherwise I would've needed to use the ugly 'b'/'f' suffix solution to resolve the ambiguity)  
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

- .org directive
- local labels in macros

## Known issues

- Syntax coloring is off when using MSDOS-style line endings (won't fix this since it is related to the fact that Swing text components internally convert all EOL sequences to '\n' but my parser uses the 'true' text offsets)
- parsing #define is currently broken when trying to #define stuff like (a+b)/c (gets irritated by the leading parens)
- SRAM address calculations do not consider the I/O mapped area at the start of SRAM

## To do

- Add support for .org directive
- Add parse error recovery
- Still lots of testing needed...
- Add support for #else / #elseif
- Find a way for meaningful error reporting when expanding macros...
