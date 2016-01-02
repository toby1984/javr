## What's this ?

A very crude attempt at an editor & assembler for Atmel ATmega88 microcontrollers... very much work in progress and and horribly broken. 

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

- Write object file(s) to disk
- Add parse error recovery
- Testing...
- Add support for fancy addressing modes
- Add support for string and character literals
- Add support for #xxx directives
- Add expression support
- Add #include directive 
- Add support for ELPM instructions ( encodings differ based on addressing mode )
- Add support for LD instructions ( encodings differ based on addressing mode )
- Add support for LDD instructions ( encodings differ based on addressing mode )
- Add support for LDS instruction ( encodings differ based on size of constant )
- Add support for LPM instructions ( encodings differ based on addressing mode )
- Add support for SPM instructions ( encodings differ based on addressing mode )  
- Add support for ST instructions ( encodings differ based on addressing mode )  
- Also add support for 16-bit STS instructions ( encodings differ based on size of constant )  
