#!/bin/bash

BASEDIR=`dirname $0`
JAR="${BASEDIR}/../target/disassembler.jar"

java -jar ${JAR} $1 $1.javr.asm
java -jar ${JAR} --avr-as $1 $1.avr-as.asm
