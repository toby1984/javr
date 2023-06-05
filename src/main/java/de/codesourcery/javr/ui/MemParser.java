/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public class MemParser 
{
	private static final boolean DEBUG = false;
	private static final Pattern pat = Pattern.compile("^([_0-9]{13}) (.*?) ([0-9]+(\\.[0-9]+)) ([0-9]+) ([0-9]+)$");
	private static final Map<Date,Entry> entries = new HashMap<>();	

	protected static final class Entry implements Comparable<Entry>
	{
		public final Date timestamp;
		public final Map<String,Float> percentage = new HashMap<>();
		public final Map<String,Integer> mem1 = new HashMap<>();
		public final Map<String,Integer> mem2 = new HashMap<>();
		
		@SuppressWarnings("deprecation")
        public Entry(Date timestamp) 
		{
			this.timestamp = new Date(timestamp.getYear(),timestamp.getMonth(),timestamp.getDate());
		}
		
		public Set<String> processNames() {
			return mem1.keySet();
		}
		
		public Integer mem1(String process) {
			return mem1.get( process );
		}
		
		public Integer mem2(String process) {
			return mem2.get( process );
		}		
		
		public static boolean hasGrown(String process,Entry first,Entry last) 
		{
			return last.percentage(process) > first.percentage(process ) || last.mem1(process ) > first.mem1(process) || last.mem2( process ) > first.mem2( process );
		}
		
		public Float percentage(String process) {
			return percentage.get( process );
		}		
		
		public String toString(String process) {
			return timestamp+" - process: "+process+" | perc: "+percentage(process)+" | mem1: "+mem1(process)+" | mem2: "+mem2(process);
		}
		
		@Override
		public int compareTo(Entry o) {
			return this.timestamp.compareTo( o.timestamp );
		}
		
		@Override
		public boolean equals(Object obj) 
		{
			if ( obj instanceof Entry) {
				return this.timestamp.equals( ((Entry) obj).timestamp );
			}
			return false;
		}
		
		public boolean containsProcess(String name) {
			return mem1.containsKey( name );
		}
		
		@Override
		public int hashCode() {
			return timestamp.hashCode();
		}

		public void add(String process, float percentage, int mem1, int mem2) 
		{
			Integer existing = this.mem1.get(process);
			if ( existing == null ) {
				existing = Integer.valueOf(0 );
			}
			existing += mem1;
			this.mem1.put( process , existing );
			
			existing = this.mem2.get(process);
			if ( existing == null ) {
				existing = Integer.valueOf(0 );
			}
			existing += mem2;
			this.mem2.put( process , existing );
			
			Float existingFloat = this.percentage.get(process);
			if ( existingFloat == null ) {
				existingFloat = Float.valueOf(0 );
			}
			if ( percentage > existingFloat) {
				existingFloat = percentage;
			}
			this.percentage.put( process , existingFloat );			
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
		
		
		final File file = new File("/home/tgierke/mem.log");
		
		// 20160115_0947
		// 20160115_0947 kuiserver 0.1 17188 274088
		

		final DateFormat DF = new SimpleDateFormat("yyyyMMdd_HHmm");
		int lineNo=0;
		for ( String line : IOUtils.readLines( new FileInputStream(file) ) ) 
		{
			lineNo++;
			Matcher m = pat.matcher( line );
			if ( m.matches() ) 
			{
				try {
					final Date date = DF.parse( m.group(1) );
					final String process = m.group(2);
					final float percentage = Float.parseFloat( m.group(4) );
					final int mem1 = Integer.parseInt( m.group(5) );
					final int mem2 = Integer.parseInt( m.group(6) );
					
					if ( percentage >= 0.1f ) 
					{
//						System.out.println("LINE: "+line);
//						System.out.println(date+" => "+process+" , perc="+percentage+", mem1="+mem1+" , mem2="+mem2);
						Entry existing = entries.get( date );
						if ( existing==null ) {
							existing = new Entry( date );
							entries.put(date, existing);
						}
						existing.add( process,percentage,mem1,mem2 );
					}
				} catch(Exception e) {
					System.out.println("ERROR ("+e.getMessage()+") on line "+lineNo+" : "+line);
				}
			} else {
				System.out.println("Malformed line "+lineNo+" : "+line);
			}
		}
		
		findGrowing();
	}
	
	private static Set<String> findGrowing() 
	{
		final Set<String> uniqueProcessName = entries.values().stream().map( p -> p.processNames() ).flatMap( s -> s.stream() ).distinct().collect( Collectors.toSet() );
		
		System.out.println("Unique process names: "+uniqueProcessName.size());
		final List<Date> datesAscending = new ArrayList<>( entries.keySet() );
		Collections.sort( datesAscending );
		
		final List<Date> datesDescending = new ArrayList<>( entries.keySet() );
		final Comparator<Date> comp = (a,b) -> a.compareTo( b );
		Collections.sort( datesDescending , comp.reversed() );
		
		System.out.println( uniqueProcessName.stream().collect(Collectors.joining("\n")) );
		final Set<String> result = new HashSet<>();
		uniqueProcessName.forEach( name -> 
		{
			Entry first=datesAscending.stream().map( d -> entries.get(d) ).filter( e -> e.containsProcess( name ) ).findFirst().orElse( null );
			Entry last=datesDescending.stream().map( d -> entries.get(d) ).filter( e -> e.containsProcess( name ) ).findFirst().orElse( null );
			
			if ( DEBUG ) {
				System.out.println( name+" first "+first.timestamp+": mem1="+first.mem1(name)+",mem2="+first.mem2(name));
				System.out.println( name+" last  "+last.timestamp+" : mem1="+last.mem1(name)+",mem2="+last.mem2(name));
			}
			
			if ( Entry.hasGrown( name ,first,last ) )
			{
				System.out.println("\n======\nProcess '"+name+"' grew!");
				System.out.println( first.toString( name ) );
				System.out.println( last.toString( name ) );
				result.add( name );
			} else {
				if ( DEBUG ) {
					System.out.println("NOT GROWN: Process '"+name+"' grew from "+first.percentage( name )+" => "+last.percentage(name));
				}
			}
		});
		return result;
	}
}