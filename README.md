## What's this ?

A crude attempt at an editor & assembler/disassembler for Atmel microcontrollers (although not for the tiny ones)... very much a work in progress.
Assembly/disassembly works pretty good now (verified by dog fooding and comparing generated disassembly with avr-objdump output and disassembling and re-compiling large amounts of data that cover every possible opcode). 
Not all preprocessor / assembler directives are implemented yet and the editor is still quite crude 

The editor currently supports 

- background compilation
- disassemble,save,load
- searching back & forth (using CTRL-k,CTRL-k CTRL-b) 
- navigating to a specific line number (CTRL-g)
- navigating to your last edit 
- CTRL-clicking on identifiers to jump to their definition
- CTRL-space auto-completion of identifiers

I intend to add a rename refactoring and more goodies though (auto-indent and block editing are sorely needed as well as support for multi-line macros...)

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
- output to RAW / Intel HEX / ELF files (ELF output is very much a work-in-progress and lacks proper symbol output and most importantly, relocation info)
- local labels ( watch out, currently not working properly when used in macros) 
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
  - .def /.undef
  - .dw / .word
  - .dseg
  - .eseg
  - .equ
  

## What's not implemented

- .org directive
- local labels in macros

## Known differences to 'official' AVR assembler syntax

- The assembler internally treats all label addresses as byte addresses and unlike the AVR assembler doesn't need special voodoo when a label is within the .cseg segment
 
The following code 

    .cseg
    
    ldi ZH,HIGH( label << 1 )
    ldi ZL,LOW( label << 1 )
    lpm r16,Z

    label: .db 0x01,0x02,0x03,0x04
    
needs to be written as

    .cseg
   
    ldi ZH,HIGH( label )
    ldi ZL,LOW( label )
    lpm r16,Z 

    label: .db 0x01,0x02,0x03,0x04

I don't know why the AVR people chose to make things more complicated , the ISA documentation clearly states that LPM treats the Z register as holding a _BYTE_ address.

- You can do forward/backward references to local labels without having to add suffixes like 'f' or 'b' to the name , so stuff like the following compiles fine:

    main:
     cpi r17,0x20
     breq skip
     ldi r16,0x0f
    .loop dec r16
     brne loop
    .skip

## Known issues

- Syntax coloring is off when using MSDOS-style line endings (won't fix this since it is related to the fact that Swing text components internally convert all EOL sequences to '\n' but my parser uses the 'true' text offsets)
- parsing #define is currently broken when trying to #define stuff like (a+b)/c (gets irritated by the leading parens)
- parsing string literals currently swallows whitespace (0x20) chars in the strings
- parsing of .def is quite picky when it comes to whitespace ( '.def x = r16' works but '.def x=r16' doesnt ...)
- Parser throws NPE because of NULL ICompilationContext when certain tokens are present at the end of the top-level file. This is because the PreprocessingLexer prematurely pops the last context while the parser is not yet done parsing. Work-around is to just add some more code to the very end of the top-level file.

## To do

- Improve ELF support (symbols are currently all tagged as functions) to be able to link against C programs written using avr-gcc) 
- Compile source files individually (output ELF relocatable) and add linking step to reduce compilation times and allow C programs to be linked against the output
- Add support for .org directive
- Better parse error recovery
- Add support for #else / #elseif
- Find a way for meaningful error reporting when expanding macros...
