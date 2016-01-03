## What's this ?

A very crude attempt at an editor & assembler for Atmel ATmega88 microcontrollers... very much work in progress and and horribly broken. 

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

- Write object file(s) to disk
- Add parse error recovery
- Testing...
- Add support for string and character literals
- Add support for #xxx directives
- Add expression support
- Add #include directive 
