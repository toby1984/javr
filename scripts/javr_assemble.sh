#!/bin/bash

BASEDIR=`dirname $0`
JAR="${BASEDIR}/../target/assembler.jar"

# output file is named: <src>.flash.raw 

if [ "$QUIET" != "0" ] ; then
	java -jar ${JAR} --ignore-segment-size -f raw "$@" 2>&1 >javr.log
else
	java -jar ${JAR} --ignore-segment-size -f raw "$@" 
fi
