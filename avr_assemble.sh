#!/bin/bash

#
# compile
#

if [ "$QUIET" != "0" ] ; then
        echo "avr-as: Compiling $1 ..." 2>&1 >avr-as.log
	avr-as -m atmega328p $1 -o $1.o 2>&1 >avr-as.log
else
        echo "avr-as: Compiling $1 ..."
	avr-as -m atmega328p $1 -o $1.o
fi

if [ "$?" != "0" ] ; then
  echo "Failed to compile."
  exit 1
fi

#
# link
#
OUT=`dirname $1`/a.out


if [ "$QUIET" != "0" ] ; then
       echo "avr-as: Linking $1.o into $OUT" 2>&1 >avr-as.log
       avr-ld -m avr5 $1.o -o $OUT 2>&1 >avr-as.log
else
       echo "avr-as: Linking $1.o into $OUT"
       avr-ld -m avr5 $1.o -o $OUT 
fi

if [ "$?" != "0" ] ; then
  echo "Failed to link."
  exit 1
fi

#
# Dump .text segment
#
if [ "$QUIET" != "0" ] ; then
       echo "avr-as: Extracting .text section to $1.raw" 2>&1 >avr-as.log
       avr-objcopy -O binary --only-section=.text $OUT $1.raw 2>&1 >avr-as.log
else
       echo "avr-as: Extracting .text section to $1.raw"
       avr-objcopy -O binary --only-section=.text $OUT $1.raw
fi

if [ "$?" != "0" ] ; then 
  echo "Failed to extract .text section from ELF file."
  exit 1
fi
