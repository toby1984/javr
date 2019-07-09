package de.codesourcery.javr.ui.panels;

import de.codesourcery.javr.assembler.parser.TextRegion;
import junit.framework.TestCase;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import javax.swing.text.AttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;

public class ShadowDOMTest extends TestCase
{
    private ShadowDOM dom;

    private List<ShadowDOM.Region> regions;

    // Note: for performance reasons, ShadowDOM uses '==' and not 'equals(Object)' to
    //       compare styles so we need to make sure each Style instance is created only once
    private final Map<Character, Style> styles = new HashMap<>();

    @Before
    @Override
    protected void setUp()
    {
        dom = new ShadowDOM();
    }

    @Test
    public void testCase1a()
    {
        parse("");
        parse("aaaaaa");
        parse("aaabba");
        assertEquals( "aaabba" , apply() );
        assertEquals(3 , dom.regions.size() );
        assertRegion(0,3,styles.get('a'), dom.regions.get(0) );
        assertRegion(3,5,styles.get('b'), dom.regions.get(1) );
        assertRegion(5,6,styles.get('a'), dom.regions.get(2) );
    }

    @Test
    public void testCase1b()
    {
        parse("");
        parse("aaabba");
        parse("aaaaaa");
        assertEquals( "aaaaaa" , apply() );
        assertEquals(1 , dom.regions.size() );
        assertRegion(0,6,styles.get('a'), dom.regions.get(0) );
    }

    @Test
    public void testEdgeCase1()
    {
        parse("");
        parse("a");
        parse("b");
        assertEquals( "b" , apply() );
        assertEquals(1 , dom.regions.size() );
        assertRegion(0,1,styles.get('b'), dom.regions.get(0) );
    }

    @Test
    public void testEdgeCase2a()
    {
        parse("");
        parse("a");
        parse("ab");
        assertEquals( "ab" , apply() );
        assertEquals(2 , dom.regions.size() );
        assertRegion(0,1,styles.get('a'), dom.regions.get(0) );
        assertRegion(1,2,styles.get('b'), dom.regions.get(1) );
    }

    @Test
    public void testEdgeCase2b()
    {
        parse("");
        parse("ab");
        parse("a");
        assertEquals( "ab" , apply() );
        assertEquals(2 , dom.regions.size() );
        assertRegion(0,1,styles.get('a'), dom.regions.get(0) );
        assertRegion(1,2,styles.get('b'), dom.regions.get(1) );
    }

    @Test
    public void testEdgeCase3a()
    {
        parse("");
        parse("a");
        parse("bb");
        assertEquals( "bb" , apply() );
        assertEquals(1 , dom.regions.size() );
        assertRegion(0,2,styles.get('b'), dom.regions.get(0) );
    }

    @Test
    public void testEdgeCase3b()
    {
        parse("");
        parse("bb");
        parse("a");
        assertEquals( "ab" , apply() );
        assertEquals(2 , dom.regions.size() );
        assertRegion(0,1,styles.get('a'), dom.regions.get(0) );
        assertRegion( 1, 2, styles.get( 'b' ), dom.regions.get( 1 ) );
    }

    private String apply() {

        StringBuilder buffer = new StringBuilder();

        final StyledDocument doc = createMock(StyledDocument.class);
        doc.setCharacterAttributes( anyInt(),anyInt(),isA( AttributeSet.class),anyBoolean() );
        expectLastCall().andAnswer( (IAnswer<Void>) () ->
        {
            final int start = (Integer) getCurrentArguments()[0];
            final int len = (Integer) getCurrentArguments()[1];
            final AttributeSet set = (AttributeSet) getCurrentArguments()[2];

            while ( buffer.length() < (start+len) ) {
                buffer.append(" ");
            }
            final char c = set.toString().charAt(0);
            for ( int i = 0 ; i < len ; i++ ) {
                buffer.setCharAt( start+i, c );
            }
            return null;
        }).anyTimes();
        replay( doc );
        dom.mergeAdjacentRegionsWithSameStyle();
        dom.applyDelta( doc, new ShadowDOM() );
        return buffer.toString();
    }

    private void parse(String text)
    {
        final Function<Character,Style> supp = c ->
        {
            final Style s = createMock(Character.toString(c),Style.class);
            expect( s.getName() ).andAnswer( () -> Character.toString( c ) ).anyTimes();
            replay(s);
            return s;
        };
        final int len = text.length();
        for (int ptr1 = 0; ptr1 < len; )
        {
            char previous=text.charAt(ptr1);
            int ptr2 = ptr1 + 1;
            while ( ptr2 < len && text.charAt( ptr2 ) == previous )
            {
                ptr2++;
            }
            final Style style = styles.computeIfAbsent( previous, supp );
            final TextRegion r = new TextRegion( ptr1, ptr2 - ptr1, 0, 0 );
            System.out.println("Adding region "+r.start()+"-"+r.end()+" ("+style.getName()+")");
            dom.setCharacterAttributes( r,style );
            ptr1=ptr2;
        }
        System.out.println("-----------------");
        System.out.println( dom );
    }

    private void assertRegion(int start, int end, Style style, ShadowDOM.Region toCheck) {
        assertEquals(start,toCheck.start);
        assertEquals(end,toCheck.end);
        assertSame(style,toCheck.style);
    }
}
