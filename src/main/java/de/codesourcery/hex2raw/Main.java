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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main 
{
    protected static final String RECORD_START = ":";
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final Pattern LINE_PATTERN = Pattern.compile("^\\:[0-9a-fA-F]+$");
    
    private boolean debugMode =false;
    private boolean verboseMode=false;

    public static enum RecordType {
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
    
    protected final boolean isDebug() {
        return debugMode;
    }
    
    protected final boolean isVerbose() {
        return debugMode || verboseMode;
    }

    public static final class Line 
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
        
        public void writeTo(OutputStream out) throws IOException 
        {
            // Startcode    Anzahl der Bytes    Adresse     Typ     Datenfeld   Prüfsumme
            this.len = data.length;
            checksum = calcChecksum();
            out.write( RECORD_START.getBytes() );
            out.write( toHexString( data.length , 1 ).getBytes() );
            out.write( toHexString( loadOffset , 2 ).getBytes() );
            out.write( toHexString( type.id , 1 ).getBytes() );
            for ( int i = 0 ; i < data.length ; i++ ) 
            {
                out.write( byteToHex( data[i] ).getBytes() );
            }
            out.write( toHexString( checksum , 1 ).getBytes() );
            out.write( 0x0d ); // linefeed
            out.write( 0x0a ); // linefeed
        }
        
        private static String toHexString(int value,int lengthInBytes) 
        {
            final StringBuilder buffer = new StringBuilder();
            switch( lengthInBytes ) 
            {
                case 1:
                    buffer.append( byteToHex( value) );
                    break;
                case 2:
                    buffer.append( byteToHex( value >> 8 ) );
                    buffer.append( byteToHex( value ) );
                    break;
                case 3:
                    buffer.append( byteToHex( value >> 16 ) );
                    buffer.append( byteToHex( value >> 8 ) );
                    buffer.append( byteToHex( value ) );
                    break;                    
                case 4:
                    buffer.append( byteToHex( value >> 24 ) );
                    buffer.append( byteToHex( value >> 16 ) );
                    buffer.append( byteToHex( value >> 8 ) );
                    buffer.append( byteToHex( value ) );                    
                    break;
                default:
                    throw new IllegalArgumentException("Value out of range: "+lengthInBytes);                    
            }
            return buffer.toString();
        }
        
        private static String byteToHex(int value) 
        {
            final int hi = (value >> 4) & 0x0f;
            final int lo = (value & 0x0f);
            return nibbleToHex( hi ) + nibbleToHex( lo );
            
        }
        private static String nibbleToHex(int value) 
        {
            final char c = (char) (value <= 9 ? '0'+value : 'A'+(value-10));
            return Character.toString( c );
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

        public boolean hasValidChecksum() 
        {
            final int expected = calcChecksum();
            if ( checksum == (expected & 0xff) ) {
                return true;
            }
            System.err.println("Got "+Integer.toHexString( checksum )+" but expected "+Integer.toHexString( expected ) ); 
            return false;
        }
        
        private int calcChecksum() 
        {
            int expected = 0;

            expected += (type.id & 0xff);
            expected += (len >> 8) & 0xff;
            expected += ( len & 0xff);
            expected += (loadOffset >> 8 ) & 0xff;
            expected += ( loadOffset & 0xff );
            for ( int i = 0 ; i < data.length ; i++ ) 
            {
                int v = data[i];
                expected += (v & 0xff);
            }
            expected = ~expected;
            expected += 1;
            return expected & 0xff;
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
    
    public static void main(String[] arguments) throws IOException
    {
        final List<String> ops = new ArrayList<>( Arrays.asList( arguments ) );
        
        final boolean debugEnabled = ops.stream().anyMatch( s -> s.equals("-d" ) );
        final boolean verboseEnabled = ops.stream().anyMatch( s -> s.equals("-v" ) ) || debugEnabled;
        
        boolean rawToHex = false;
        int startingAddress = 0;
        for ( int i = 0 ; i < ops.size() ; i++ ) 
        {
            if ( ops.get(i).equals("-r" ) ) 
            {
                if ((i+1) >= ops.size() ) {
                    throw new RuntimeException("'-r' option requires an address");
                }
                rawToHex = true;
                String sAdr = ops.get(i+1);
                int radix = 10;
                if ( sAdr.startsWith("0x") ) {
                    radix = 16;
                    sAdr = sAdr.substring(2);
                } else if ( sAdr.startsWith("$") ) {
                    radix = 16;
                    sAdr = sAdr.substring(1);
                }
                startingAddress = Integer.parseInt( sAdr , radix );
                if ( verboseEnabled ) 
                {
                    System.out.println("Using starting address: "+sAdr);
                }
                ops.remove( i );
                ops.remove( i );
                break;
            }
        }
        
        ops.removeIf( s -> s.equals( "-d" ) );
        ops.removeIf( s -> s.equals( "-v" ) );
        
        if ( ops.size() < 1 || ops.size() > 2 ) {
            throw new RuntimeException("Invalid command line: "+ops+".\n\nUsage: [-d] [-r <address>] [-v] <input file> [output file]\n\n"
                    + "-d => enable debug output\n"
                    + "-v => enable verbose output\n"
                    + "-r <starting address> => Convert raw-to-hex (instead of hex-to-raw which is the default)\n");
        }
        
        final String suffix = rawToHex ? ".hex" : ".raw";
        
        final File inFile = new File( ops.get(0) );
        if ( ! inFile.exists() || ! inFile.isFile() || ! inFile.canRead() ) {
            throw new RuntimeException("Input file "+inFile.getAbsolutePath()+" does not exist,is no regular file or cannot be read");
        }
        final File outFile;
        if ( ops.size() == 2 ) {
            outFile = new File( ops.get(1) );
        } else {
            final int idx = inFile.getName().lastIndexOf('.');
            if ( idx != -1 )
            {
                final String stripped = inFile.getName().substring( 0 , idx );
                outFile = new File( inFile.getParentFile() , stripped+suffix );
            } else {
                outFile = new File( inFile.getAbsolutePath()+suffix );
            }
        }
        
        if ( verboseEnabled ) {
            System.out.println("Reading: "+inFile.getAbsolutePath()+"\nWriting: "+outFile.getAbsolutePath());
        }
        
        final Main converter = new Main();
        converter.debugMode = debugEnabled;
        converter.verboseMode = verboseEnabled;
        
        try ( InputStream in = new FileInputStream(inFile) ; OutputStream out = new FileOutputStream(outFile) ) 
        {
            if ( rawToHex ) 
            {
                if ( verboseEnabled ) {
                    System.out.println("Converting RAW -> HEX");
                }
                converter.rawToHex( in , out , startingAddress );
            } 
            else 
            {
                if ( verboseEnabled ) {
                    System.out.println("Converting HEX -> RAW");
                }                
                converter.hexToRaw( in , out );
            }
        }
    }
    
    private void rawToHex(InputStream in, OutputStream out,int startingAddress) throws IOException 
    {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] tmp = new byte[1024];
        int len = 0;
        while ( ( len = in.read( tmp ) ) != -1 ) 
        {
            buffer.write( tmp , 0 , len );
        }
        final byte[] data = buffer.toByteArray();
        rawToHex(  data , data.length , out , startingAddress );
    }

    public void rawToHex(byte[] data,int len, OutputStream out,int address) throws IOException 
    {
        final int bytesPerLine = 16;
        
        Line line = new Line();
        line.loadOffset = 0;
        line.data = new byte[] { (byte) ((address >> 8) & 0xff) , (byte) address }; // big-endian
        line.type = RecordType.EXTENDED_SEGMENT_ADDRESS;
        line.writeTo( out );
        
        int ptr = 0;
        while ( ptr < len ) 
        {
            line.loadOffset = ptr;
            final int l = (len-ptr) >= bytesPerLine ? bytesPerLine : (len-ptr);
            line.data = Arrays.copyOfRange( data , ptr , ptr +l); 
            line.type = RecordType.DATA;
            line.writeTo( out );
            
            ptr += bytesPerLine;
        }
        
        line.loadOffset = 0;
        line.data = new byte[0];
        line.type = RecordType.END_OF_FILE;
        line.writeTo( out );        
    }
    
    public interface LineConsumer 
    {
        public void visit(Line line) throws IOException;
    }
    
    public void parseHex(InputStream in, LineConsumer visitor) throws IOException 
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line=null;
        int lineNo = 1;
        while ( ( line = reader.readLine() ) != null ) 
        {
            if ( isDebug()) {
                System.out.println("Parsing line "+lineNo+": "+line);
            }
            final Line parsed = Line.parse( line , lineNo++ );
            visitor.visit( parsed );
        }
    }

    public void hexToRaw(InputStream in,OutputStream out) throws IOException 
    {
        final int[] byteCount = new int[]{0};
        final LineConsumer visitor = parsed -> 
        {
            if ( isDebug() ) {
                System.out.println( parsed );
            }
            final byte[] rawData = parsed.getRawData();
            byteCount[0] += rawData.length;
            out.write( rawData );            
        };
        
        parseHex( in , visitor );
        
        if ( isVerbose() ) {
            System.out.println("Wrote "+byteCount[0]+" raw bytes.");
        }
    }

    public void setDebug(boolean debug) {
        debugMode = debug;
    }
}