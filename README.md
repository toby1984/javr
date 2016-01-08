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

## To do

- Add parse error recovery
- Testing...
- Add support for #xxx directives
- Add #include directive 
