CC      = /usr/bin/avr-gcc
LD      = /usr/bin/avr-ld
LSCRIPT = /usr/lib/avr/lib/ldscripts/avr5.x
# CFLAGS  = -mmcu=atmega328p -O2 -funsigned-char -funsigned-bitfields -fpack-struct -fshort-enums -Wall -Wundef -std=gnu99 -T /usr/lib/avr/lib/ldscripts/avr5.x -Isrc
CFLAGS  = -mmcu=atmega328p -Os -funsigned-char -funsigned-bitfields -fpack-struct -fshort-enums -Wall -Wundef -std=gnu99 -T /usr/lib/avr/lib/ldscripts/avr5.x -Isrc
OBJS = framebuffer.o  ps2.o  test.o
# DEVICE = /dev/ttyACM0
DEVICE = /dev/ttyACM1
SPEED = -B20

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

upload_isp: binary
	/usr/bin/avrdude -e $(SPEED) -F -c stk500v2 -p ATMEGA328 -P /dev/ttyACM0 -U flash:w:test.bin
upload: binary
	/usr/bin/avrdude -D $(SPEED) -F -V -c arduino -p ATMEGA328P -P /dev/ttyACM0 -b 115200 -U flash:w:test.bin
show_fuses:
	/usr/bin/avrdude -F $(SPEED) -c stk500v2 -p ATMEGA328P -P /dev/ttyACM0 -v -U lfuse:r:-:i
download: binary
	/usr/bin/avrdude -F $(SPEED) -c stk500v2 -p ATMEGA328P -P /dev/ttyACM0 -U flash:r:flash.bin:r
# disable clk/8 divider
disable_clockdiv:
	/usr/bin/avrdude -F $(SPEED) -c stk500v2 -p ATMEGA328P -P /dev/ttyACM0 -v -U lfuse:w:0xE2:m 
ext_crystal:
	/usr/bin/avrdude -F $(SPEED) -c stk500v2 -p ATMEGA328P -P /dev/ttyACM0 -v -U lfuse:w:0xFD:m 
