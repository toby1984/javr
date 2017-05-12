#!/bin/bash

java -jar /home/tgierke/neon_workspace/javr/target/disassembler.jar $1 $1.javr.asm
java -jar /home/tgierke/neon_workspace/javr/target/disassembler.jar --avr-as $1 $1.avr-as.asm
