CC      = /usr/bin/avr-gcc
LD      = /usr/bin/avr-ld
CFLAGS  = -mmcu=atmega328p -funsigned-char -funsigned-bitfields -fpack-struct -fshort-enums -Wall -Wundef -std=gnu99

.PHONY: clean binary

binary: test
	avr-objcopy -O binary test test.bin

test: test.c
	$(CC) $(OPTS) -o test test.c linkertest.flash.o

clean:
	rm -f test test.o test.bin 2>/dev/null

upload: binary
	/usr/bin/avrdude -F -V -c arduino -p ATMEGA328P -P /dev/ttyACM0 -b 115200 -U flash:w:test
