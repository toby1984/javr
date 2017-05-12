#!/bin/bash

BASEDIR=`dirname $0`

# cleanup from last run
function cleanup() {
    echo "Cleaning up..."
    rm random* avr-as.log javr.log a.out 2>/dev/null
}
cleanup

trap cleanup SIGINT

KBYTES="0"
while true ; do
    echo "-------------------------"
    echo "---- KBytes: $KBYTES"
    echo "-------------------------"
    if ! test_roundtrip.sh ; then
      exit 1
    fi
    KBYTES=`expr $KBYTES + 16`
done 
