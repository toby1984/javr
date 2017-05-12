#!/bin/bash

export QUIET="1"

# cleanup from last run
function cleanup() {
    echo "Cleaning up..."
    rm random* avr-as.log javr.log a.out 2>/dev/null
}
cleanup

trap cleanup SIGINT

# create random data
dd if=/dev/urandom of=random bs=1k count=16

# disassemble
./disassemble.sh random
if [ "$?" != "0" ] ; then
        echo "ERROR: Disassembler failed"
        exit 1
fi

# JAVR assemble
./javr_assemble.sh --hide-warnings random.javr.asm
if [ "$?" != "0" ] ; then
        echo "ERROR: javr failed."
        exit 1
fi 

# avr-as assemble
./avr_assemble.sh random.avr-as.asm
if [ "$?" != "0" ] ; then
	echo "ERROR: avr-as failed."
        exit 1
fi	

# compare binary images
MYSUM=`md5sum random.javr.asm.flash.raw | cut -d" " -f1`
GNUSUM=`md5sum random.avr-as.asm.raw | cut -d" " -f1` 

if [ "$MYSUM" != "$GNUSUM" ] ; then
	echo "ERROR: Checksums differ:"
	echo "ERROR: random.javr.asm.flash.raw => $MYSUM" 
	echo "ERROR: random.avr-as.asm.raw     => $GNUSUM"
	exit 1
else
	echo "Test passed."
fi
