#!/bin/bash

# output file is named: <src>.flash.raw 

if [ "$QUIET" != "0" ] ; then
	java -jar /home/tgierke/neon_workspace/javr/target/assembler.jar --ignore-segment-size -f raw "$@" 2>&1 >javr.log
else
	java -jar /home/tgierke/neon_workspace/javr/target/assembler.jar --ignore-segment-size -f raw "$@" 
fi
