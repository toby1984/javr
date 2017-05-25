CC      = /usr/bin/avr-gcc
LD      = /usr/bin/avr-ld
LSCRIPT = /usr/lib/avr/lib/ldscripts/avr5.x
CFLAGS  = -mmcu=atmega328p -O2 -funsigned-char -funsigned-bitfields -fpack-struct -fshort-enums -Wall -Wundef -std=gnu99 -T /usr/lib/avr/lib/ldscripts/avr5.x -Isrc
# CFLAGS  = -mmcu=atmega328p
OBJS = framebuffer.o  ps2.o  test.o

.PHONY: clean binary

binary: all 
	avr-objcopy -O binary test test.bin
	avr-objcopy -R .eeprom -R .fuse -R .lock -R .signature -O ihex test test.hex

all: $(OBJS)
#	$(CC) $(CFLAGS) -mrelax -o test test.o linkertest.o
	$(CC) $(CFLAGS) -o test test.o framebuffer.o ps2.o mylib.o

%.o: src/%.c
	$(CC) $(CFLAGS) -c $<

clean:
	rm -f test framebuffer.o ps2.o test.o 2>/dev/null

upload: binary
	/usr/bin/avrdude -D -F -V -c arduino -p ATMEGA328P -P /dev/ttyACM0 -b 115200 -U flash:w:test.bin
show_fuses:
	/usr/bin/avrdude -c arduino -p ATMEGA328P -P /dev/ttyACM0 -b 115200 -v -U lfuse:r:-:i
