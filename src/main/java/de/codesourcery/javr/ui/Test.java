package de.codesourcery.javr.ui;

public class Test
{

    public static void main(String[] args)
    {
        int counter = 1;
        System.out.println( "Simulated: " + simulate(counter)+" cycles");
    }

    public static int simulate(int counter)
    {
        int cycles = 0;
        int vl = counter & 0xff;
        int vm = (counter >>> 8) & 0xff;
        int vh = (counter >>> 16) & 0xff;

        cycles += 9;

        /*
sleep:
      push r16 ; 2 cycles
      push r17 ; 2 cycles
      push r18 ; 2 cycles

      ldi r16,LOW(BLINK_INTERVAL) ; 1 cycle
      ldi r17, LOW(BLINK_INTERVAL>>8) ; 1 cycle
      ldi r18, LOW(BLINK_INTERVAL>>16) ; 1 cycle

      ; TOTAL: 9
.loop1
      tst r16 ; 1 cycle
      breq next1 ; true -> 2 cycles, otherwise 1
      ; TOTAL: 12
.fastloop
      dec r16 ; 1 cycle
      brne fastloop ; true -> 2 cycles, otherwise 1+
      ; TOTAL: 14
  .next1
      tst r17 ; 1 cycle
      breq next2
      ; TOTAL: 17
      dec r17 ; 1 cycle
      brne fastloop
.next2
      tst r18 ; 1 cycle
      breq back
      ; TOTAL: 20
      dec r18 ; 1 cycle
      brne fastloop
.back
      pop r18 ; 2 cycles
      pop r17 ; 2 cycles
      pop r16 ; 2 cycles
      ret ; 4 cycles for 16-bit pc, 5 cycles for 22-bit PC (AtMega)
      TOTAL: 30
         */
        // --

        cycles++; // TST
        if ( vh > 0 ) {
            cycles++; // fall-through
        } else {
            cycles+=2; // breq next1
        }
        for ( int outer = vh ; outer > 0 ; outer-- ) {
            cycles++; // DEC
            if ( vm > 0 ) {
                cycles++; // fall-through
            } else {
                cycles+=2;
            }
            for ( int mid = vm ; mid > 0 ; mid-- )
            {
                cycles++;
                if ( vl > 0 ) {
                    cycles++; // fall-through
                } else {
                    cycles+=2;
                }
                for ( int low = vl ; low > 0 ; low-- ) {
                    if ( (low-1) > 0 ) {
                        cycles+=3;
                    } else {
                        cycles+=2;
                    }
                }
                vl = 0xff;
                if ( (mid-1) > 0 ) {
                    cycles+=3;
                } else {
                    cycles+=2;
                }
            }
            vm = 0xff;
            if ( (outer-1) > 0 ) {
                cycles+=3;
            } else {
                cycles+=2;
            }
        }
        // ---
        cycles += 10;
        return cycles;
    }

    private static int doLoop(int start) {
        int cycles = 0;
        do
        {
            start--;
            cycles++;
            if ( start == 0 )
            {
                cycles++;
                break;
            }
            cycles += 2;
        } while ( true );
        return cycles;
    }
}
