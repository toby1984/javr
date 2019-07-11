package de.codesourcery.javr.ui.panels;

import de.codesourcery.javr.assembler.parser.TextRegion;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.swing.text.Style;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ShadowDOM
{
    private static final Logger LOG = LogManager.getLogger( ShadowDOM.class.getName() );

    private static final Region[] EMPTY = new Region[0];

    final List<Region> regions = new ArrayList<>();

    public static final class Region
    {
        public int start;
        public int end;
        public Style style;

        public Region(TextRegion region,Style style)
        {
            this( region.start(), region.end(), style );
        }

        public Region(int start, int end,Style style)
        {
            if ( end<=start )
            {
                throw new IllegalArgumentException( "start needs to be greater than end , start: "+start+", end: "+end );
            }
            this.start = start;
            this.end = end;
            this.style = style;
        }

        public boolean canBeMerged(Region other)
        {
            return Objects.equals(this.style,other.style) &&( this.end == other.start || this.start == other.end );
        }

        public boolean contains(int start,int end)
        {
            return this.start <= start && end <= this.end;
        }

        @Override
        public String toString()
        {
            return start+" - "+end+" ("+style.getName()+")";
        }

        public int length() {
            return end-start;
        }

        public boolean overlaps(int otherStart,int otherEnd)
        {
            if ( otherStart >= this.start && otherStart < this.end ) {
                return true;
            }

            if ( this.start >= otherStart && this.start < otherEnd ) {
                return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object object)
        {
            if ( object instanceof Region) {
                return Objects.equals(this.style , ( (Region) object ).style ) && sameRange( (Region) object);
            }
            return false;
        }

        public Region[] intersect(Region other)
        {
            /*
             * Case 0:
             *  |------| this  [X]
             *  |------| other
             *
             * Case 1:
             *  |------| this  [X]
             *  |-----------| other
             *
             *     |------| this [X]
             *  |-----------| other
             *
             *       |------| this [X]
             *  |-----------| other
             *
             * Case 2:
             *  |--------| this [X]
             *  |---| other
             *
             *  |--------| this  [X]
             *     |---| other
             *
             *  |--------| this [x]
             *       |---| other
             *
             * case 3:
             *
             * |------| this
             *    |------| other
             *
             * case 4
             *    |------| this
             * |------|    other
             */
            if ( this.start == other.start && this.end == other.end ) {
                // case 0
                return EMPTY;
            }

            if ( this.start == other.start ) {
                // case 1a or case 2a
                if ( this.end < other.end ) {
                    // case 1a
                    return EMPTY;
                }
                // case 2a (this.end > other.end )
                return new Region[]{ new Region(other.end,this.end,this.style)};
            }
            if ( this.start > other.start && this.end < other.end ) {
                // case 1b
                return EMPTY;
            }
            if ( this.start > other.start && this.end == other.end ) {
                // case 1c
                return EMPTY;
            }
            if ( this.start < other.start && this.end > other.end ) {
                // case 2b
                return new Region[] { new Region(this.start,other.start,this.style), new Region(other.end,this.end,this.style)};
            }
            if ( this.start < other.start && this.end == other.end ) {
                // case 2c
                return new Region[]{ new Region(this.start,other.start,this.style) };
            }
            if ( this.start < other.start && other.start < this.end && other.end > this.end ) {
                // case 3
                return new Region[] { new Region(this.start, other.start, this.style) };
            }
            if ( this.start > other.start && this.start < other.end && this.end > other.end ) {
                // case 4
                return new Region[] { new Region(other.end, this.end, this.style) };
            }
            /*
             *  this = 2764 - 2768 (labelStyle)
             *  other = 2765 - 2774 (labelStyle)
             *   2764                   2768
             *  |-----------------------|
             *
             *      |----------------------|
             *  2765                      2774
             */
            throw new RuntimeException("Unreachable code reached: this = "+this+" | other = "+other);
        }

        public boolean sameRange(Region other) {
            return sameRange(other.start,other.end);
        }

        public boolean sameRange(int otherStart,int otherEnd) {
            return this.start == otherStart && this.end == otherEnd;
        }

        public void merge(Region other)
        {
            final int newStart = Math.min(this.start,other.start);
            final int newEnd = Math.max( this.end, other.end );
            this.start = newStart;
            this.end = newEnd;
        }
    }

    private static final class BackwardsIterator implements Iterator<Region> {

        private final List<Region> list;
        public int ptr;
        private int previousIdx=-1;

        private BackwardsIterator(List<Region> list)
        {
            this.list = list;
            ptr = list.isEmpty() ? -1 : list.size()-1;
        }

        @Override
        public boolean hasNext()
        {
            return ptr >= 0;
        }

        @Override
        public void remove()
        {
            if ( previousIdx == -1 ) {
                throw new IllegalStateException( "You need to call next() first" );
            }
            list.remove( previousIdx );
        }

        @Override
        public Region next()
        {
            previousIdx = ptr;
            return list.get( ptr-- );
        }
    }

    public void applyDelta(StyledDocument document, ShadowDOM previousDOM)
    {
        final int originalCount = regions.size();
        LOG.info("applyDelta(): "+regions.size()+" possibly dirty regions.");

        int left = 0;
        int right = regions.size()-1;

        Iterator<Region> it1 = regions.iterator();
        Iterator<Region> it2 = previousDOM.regions.iterator();

        // move left boundary
        while ( it1.hasNext() && it2.hasNext() && it1.next().equals( it2.next() ) )
        {
            left++;
        }

        // move right boundary
        it1 = new BackwardsIterator( this.regions );
        it2 = new BackwardsIterator( previousDOM.regions );

        while ( it1.hasNext() && it2.hasNext() && it1.next().equals( it2.next() ) && right >= left)
        {
            right--;
        }

        final int deltaSize = (right-left)+1;
        LOG.info("applyDelta(): Updating "+deltaSize+" regions (out of "+originalCount+" total)");
        for ( int i = left ; i <= right ; i++)
        {
            final Region r = regions.get(i);
            document.setCharacterAttributes( r.start, r.length(), r.style, true );
        }
    }

    public void setCharacterAttributes(TextRegion region, Style style)
    {
        try {
            setCharacterAttributes2(region,style);
        } catch(RuntimeException e) {
            throw e;
        }
    }

    private void setCharacterAttributes2(TextRegion region, Style style)
    {
        final int idx = findOverlappingRegion( region.start(), region.end() );
        if ( idx != -1 )
        {
            // found match, look for overlap with regions left and right of this one
            int left = idx;
            int right = idx;
            while ( left > 0 && regions.get(left-1).overlaps( region.start(), region.end() ) ) {
                left--;
            }
            while ( (right+1) < regions.size() && regions.get(right+1).overlaps( region.start(), region.end() ) ) {
                right++;
            }
            if ( left != right )
            {
                // region overlaps with multiple other regions
                final Region newRegion = new Region( region, style );

                final List<Region> replacements = new ArrayList<>();
                for ( int i = left ; i <= right ; i++ )
                {
                    final Region toIntersect = regions.get(i);
                    final Region[] intersection = toIntersect.intersect( newRegion );
                    for (int j = 0, len = intersection.length; j < len; j++)
                    {
                        replacements.add( intersection[j] );
                    }
                }
                insertRegion( newRegion, replacements );

                for ( int count = (right-left)+1 ; count > 0 ; count-- ) {
                    regions.remove(left);
                }
                for ( int i = replacements.size()-1 ; i >= 0 ; i-- ) {
                    regions.add( left, replacements.get(i) );
                }
                tryMergeLeft( left );
                tryMergeRight( left+replacements.size() );
            }
            else
            {
                // overlaps with exactly one region
                final Region toSplit = regions.get( idx );
                if ( toSplit.sameRange( region.start(), region.end() ) )
                {
                    toSplit.style = style;
                    return;
                }
                if ( toSplit.contains( region.start(), region.end() ) && Objects.equals(toSplit.style,style ) ) {
                    // overlapping region fully contains the new region AND has the same style -> do nothing
                    return;
                }

                final Region newRegion = new Region( region, style );

                // retain only the non-overlapping parts
                final Region[] split = toSplit.intersect( newRegion );
                final List<Region> splitList = new ArrayList<>( split.length );
                for (int i = 0, splitLength = split.length; i < splitLength; i++)
                {
                    splitList.add( split[i] );
                }
                regions.remove( left );
                insertRegion( newRegion, splitList );

                for ( int i = splitList.size()-1 ; i >= 0 ; i-- ) {
                    regions.add( left, splitList.get(i));
                }
                tryMergeLeft(left);
                tryMergeRight(left+splitList.size());
            }
        }
        else
        {
            // no match found
            final int insertPos = insertRegion( new Region( region, style ) );
            tryMergeLeft(insertPos);
            tryMergeRight(insertPos);
        }
    }

    private void tryMergeLeft(int startIdx)
    {
        for ( int currentIdx = startIdx ; currentIdx > 0 && currentIdx < regions.size() ; )
        {
            final Region current = regions.get(currentIdx);
            final Region left = regions.get(currentIdx-1);
            if ( left.canBeMerged( current ) )
            {
                left.merge( current );
                regions.remove(currentIdx);
            } else {
                currentIdx--;
            }
        }
    }

    private void tryMergeRight(int startIdx)
    {
        for ( int currentIdx = startIdx ; (currentIdx+1) < regions.size() ; )
        {
            final Region current = regions.get(currentIdx);
            final Region right = regions.get(currentIdx+1);
            if ( right.canBeMerged( current ) )
            {
                right.merge(current);
                regions.remove(currentIdx);
            } else {
                currentIdx++;
            }
        }
    }

    private int insertRegion(Region toInsert)
    {
        return insertRegion(toInsert,regions);
    }

    private static int insertRegion(Region toInsert,List<Region> regions)
    {
        if ( regions.isEmpty() )
        {
            regions.add(toInsert);
            return 0;
        }
        int low = 0;
        int high = regions.size() - 1;

        int mid = (low + high) >>> 1;
        Region midVal = regions.get(mid);
        while (low <= high)
        {
            mid = (low + high) >>> 1;
            midVal = regions.get(mid);

            if (midVal.start < toInsert.start)
            {
                low = mid + 1;
            }
            else if (midVal.start > toInsert.start)
            {
                high = mid - 1;
            } else {
                throw new IllegalArgumentException( "List already contains a region with start offset "+
                        toInsert.start+": "+regions );
            }
        }
        if ( toInsert.start < midVal.start ) {
            regions.add( mid,toInsert);
            return mid;
        }
        regions.add( mid+1,toInsert);
        return mid+1;
    }

    private int findOverlappingRegion(int start, int end)
    {
        int low = 0;
        int high = regions.size() - 1;

        while (low <= high)
        {
            int mid = (low + high) >>> 1;
            Region midVal = regions.get(mid);

            if ( midVal.overlaps( start, end ) ) {
                return mid;
            }

            if (midVal.start < start)
            {
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }
        return -1;
    }

    public void truncate(int maxOffset)
    {
        if ( regions.isEmpty() || regions.get( regions.size() - 1 ).end <= maxOffset )
        {
            return;
        }
        regions.removeIf( region -> region.start >= maxOffset );

        if ( ! regions.isEmpty() )
        {
            // truncate remaining region if necessary
            final Region last = regions.get( regions.size() - 1 );
            if ( last.end > maxOffset )
            {
                last.end = maxOffset;
            }
        }
    }

    public void clear() {
        regions.clear();
    }

    public void setTo(ShadowDOM other)
    {
        this.regions.clear();
        this.regions.addAll( other.regions );
    }

    @Override
    public String toString()
    {
        return regions.toString();
    }
}