/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.hex2raw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main 
{
    private static boolean DEBUG;
    
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final Pattern LINE_PATTERN = Pattern.compile("^\\:[0-9a-fA-F]+$");    

    protected static enum RecordType {
        DATA(0),
        END_OF_FILE(1),
        EXTENDED_SEGMENT_ADDRESS(2),
        START_SEGMENT_ADDRESS(3),
        EXTENDED_LINEAR_ADDRESS(4),
        START_LINEAR_ADDRESS(5);

        public final int id;

        private RecordType(int id) 
        {
            this.id = id;
        }

        public static RecordType fromID(int id) {
            return Stream.of( values() ).filter( v -> v.id == id ).findFirst().orElseThrow( () -> new RuntimeException("Unknown record type: "+id));
        }
    }

    protected static final class Line 
    {
        public RecordType type;
        public int len;
        public int loadOffset;
        public byte[] data;
        public int checksum;

        @Override
        public String toString() {
            return type+" , len="+len+", loadOffset=0x"+Integer.toHexString( loadOffset );
        }

        public byte[] getRawData() 
        {
            if ( type == RecordType.DATA ) {
                return data;
            }
            return EMPTY_ARRAY;
        }

        public static Line parse(String input,int lineNo) 
        {
            if ( input == null || ! LINE_PATTERN.matcher(input).matches() ) {
                throw new RuntimeException("Syntax error on line "+lineNo);
            }

            final String stripped = input.substring(1);
            if ( (stripped.length() &1 ) != 0 ) {
                throw new RuntimeException("Odd number of digits on line "+lineNo);
            }
            final String[] substrings = new String[ stripped.length()/2];
            for ( int start = 0 , i=0 , end = stripped.length() ; start < end ; start += 2 , i++ ) {
                substrings[i] = stripped.substring( start , start+2 );
            }
            //            Stream.of(substrings).forEach( s -> System.out.println("GOT: "+s) );

            final Line result = new Line();
            result.len = parseByte( substrings[0] );
            result.loadOffset = parseByte( substrings[1] ) << 8 | parseByte( substrings[2] );
            result.type = RecordType.fromID( parseByte( substrings[3] ) );
            result.data = parseByteArray( substrings , 4 , result.len );
            result.checksum = parseByte( substrings[ 4 + result.len  ] );
            final int expectedLen = (4+result.len+1 );
            if ( substrings.length > expectedLen ) {
                throw new RuntimeException("Line "+lineNo+"contains extra characters, expected "+expectedLen+" bytes but got "+substrings.length);
            }
            if ( ! result.hasValidChecksum() ) {
                throw new RuntimeException("Checksum error on line "+lineNo);
            }
            return result;
        }

        public boolean hasValidChecksum() {

            int expected = 0;

            expected += type.id;
            expected += len;
            expected += loadOffset;
            for ( int i = 0 ; i < data.length ; i++ ) 
            {
                expected += (data[i] & 0xff);
            }
            expected &= 0xff;
            expected = (expected^0xff) + 1;
            if ( checksum == (expected & 0xff) ) {
                return true;
            }
            System.err.println("Got "+Integer.toHexString( checksum )+" but expected "+Integer.toHexString( expected & 0xff ) ); 
            return false;
        }

        private static int parseByte(String input) 
        {
            if ( input == null || input.length() != 2 ) {
                throw new RuntimeException("Unexpected input: "+input);
            }
            return Integer.parseInt( input , 16 );
        }

        private static byte[] parseByteArray(String[] data,int offset,int bytesToRead) 
        {
            if ( offset+bytesToRead > data.length ) {
                throw new RuntimeException("Cannot read "+bytesToRead+" bytes with offset "+offset+" from "+data.length+" values");
            }
            final byte[] result = new byte[ bytesToRead ];
            for ( int i = 0 ; i < bytesToRead ; i++ ) 
            {
                result[i] = (byte) parseByte( data[ offset + i] );
            }
            return result;
        }        
    }

    public static void main(String[] args) throws IOException
    {
        if ( args.length < 1 || args.length > 2 ) {
            throw new RuntimeException("Invalid command line.\n\nUsage: <input file> [output file]");
        }
        final File inFile = new File( args[0] );
        if ( ! inFile.exists() || ! inFile.isFile() || ! inFile.canRead() ) {
            throw new RuntimeException("Input file "+inFile.getAbsolutePath()+" does not exist,is no regular file or cannot be read");
        }
        final File outFile;
        if ( args.length == 2 ) {
            outFile = new File( args[2] );
        } else {
            final int idx = inFile.getName().lastIndexOf('.');
            if ( idx != -1 )
            {
                final String stripped = inFile.getName().substring( 0 , idx );
                outFile = new File( inFile.getParentFile() , stripped+".raw" );
            } else {
                outFile = new File( inFile.getAbsolutePath()+".raw" );
            }
        }
        System.out.println("Reading: "+inFile.getAbsolutePath()+"\nWriting: "+outFile.getAbsolutePath());
        try ( InputStream in = new FileInputStream(inFile) ; OutputStream out = new FileOutputStream(outFile) ) {
            new Main().convert( in , out );
        }
    }

    public void convert(InputStream in,OutputStream out) throws IOException 
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line=null;
        int lineNo = 1;
        int byteCount = 0;
        while ( ( line = reader.readLine() ) != null ) 
        {
            if ( DEBUG ) {
                System.out.println("Parsing line "+lineNo+": "+line);
            }
            final Line parsed = Line.parse( line , lineNo++ );
            System.out.println( parsed );
            final byte[] rawData = parsed.getRawData();
            byteCount += rawData.length;
            out.write( rawData );
        }
        System.out.println("Wrote "+byteCount+" raw bytes.");
    }

    public static void setDebug(boolean debug) {
        Main.DEBUG = debug;
    }
}