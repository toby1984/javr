package de.codesourcery.javr.ui.panels;

import de.codesourcery.javr.assembler.parser.TextRegion;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.swing.text.Style;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
                return this.style == ( (Region) object ).style && sameRange( (Region) object);
            }
            return false;
        }

        public Region[] intersect(Region other)
        {
            if ( other.contains( this.start, this.end ) ) {
                return EMPTY;
            }
            // case 3.)
            if ( this.sameRange( other ) ) {
                return EMPTY;
            }

            Region first;
            Region second;
            if ( this.start == other.start )
            {
                // case 4.)
                if ( length() < other.length() ) {
                    return EMPTY;
                }
                // case 5.)
                return new Region[] { new Region( other.end, this.end, this.style ) };
            }
            if ( this.start < other.start ) {
                first = this;
                second = other;
            } else {
                first = other;
                second = this;
            }

            // case 0.)
            if ( first.end <= second.start )
            {
                return EMPTY; // no intersection at all
            }
            /*
             * case 0.)
             *        |-----|
             *  |-----|
             *
             * case 1.)
             *    |--|
             *  |-----|
             *
             * case 2.)
             *      |----|
             *  |-----|
             *
             * case 3.)
             *  |-----|
             *  |-----|
             *
             * case 4.)
             *  |------|
             *  |----------|
             *
             *  case 5.)
             *  |----------|
             *  |------|
             */

            if ( second.end >= first.end )
            {
                // case 2
                return new Region[]{new Region( first.start, second.start, this.style )};
            }

            // case 1
            return new Region[]{new Region( first.start, second.start, this.style ),
                    new Region( second.end, first.end, this.style )};
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

    public void mergeAdjacentRegionsWithSameStyle()
    {
        int size = regions.size();
        for ( int left = 0 ; left < size ; left++ )
        {
            final Region mergeTarget = regions.get(left);
            while ( (left+1) < size )
            {
                final Region candidate = regions.get(left+1);
                if ( mergeTarget.end == candidate.start && mergeTarget.style == candidate.style )
                {
                    mergeTarget.merge( candidate );
                    regions.remove(left+1);
                    size--;
                } else {
                    break;
                }
            }
        }
    }

    private static final class BackwardsIterator implements Iterator<Region> {

        private final List<Region> list;
        private int ptr;

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
        public Region next()
        {
            return list.get(ptr--);
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
            // found match, inspect surroundings
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
                final Region r = new Region( region, style );

                // multiple regions intersect
                final List<Region> replacements = new ArrayList<>();
                for ( int i = left ; i <= right ; i++ )
                {
                    final Region toIntersect = regions.get(i);
                    final Region[] intersection = toIntersect.intersect( r );
                    for (int j = 0, len = intersection.length; j < len; j++)
                    {
                        replacements.add( intersection[j] );
                    }
                }
                insertRegion( r, replacements );

                for ( int count = (right-left)+1 ; count > 0 ; count-- ) {
                    regions.remove(left);
                }
                for ( int i = replacements.size()-1 ; i >= 0 ; i-- ) {
                    regions.add( left, replacements.get(i) );
                }
            }
            else
            {
                // exactly one region intersected
                final Region toSplit = regions.get( idx );
                if ( toSplit.sameRange( region.start(), region.end() ) )
                {
                    toSplit.style = style;
                    return;
                }
                if ( toSplit.contains( region.start(), region.end() ) && toSplit.style == style ) {
                    return;
                }

                final Region r = new Region( region, style );

                // retain only the non-overlapping parts
                final Region[] split = toSplit.intersect( r );
                final List<Region> splitList = new ArrayList<>( split.length );
                for (int i = 0, splitLength = split.length; i < splitLength; i++)
                {
                    splitList.add( split[i] );
                }
                regions.remove( left );
                insertRegion( r, splitList );

                for ( int i = splitList.size()-1 ; i >= 0 ; i-- ) {
                    regions.add( left, splitList.get(i));
                }
            }
        }
        else
        {
            // no match found
            insertRegion( new Region( region, style ) );
        }
    }

    private void insertRegion(Region toInsert)
    {
        insertRegion(toInsert,regions);
    }

    private static void insertRegion(Region toInsert,List<Region> regions)
    {
        if ( regions.isEmpty() ) {
            regions.add(toInsert);
            return;
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
                throw new IllegalArgumentException( "List already contains a region with start "+
                        toInsert.start+": "+regions );
            }
        }
        if ( toInsert.start < midVal.start ) {
            regions.add( mid,toInsert);
        } else {
            regions.add( mid+1,toInsert);
        }
    }

    private int findOverlappingRegion(int start, int end)
    {
        int low = 0;
        int high = regions.size() - 1;

        while (low <= high) {
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