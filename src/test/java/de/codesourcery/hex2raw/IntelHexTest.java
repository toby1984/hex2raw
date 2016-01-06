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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.io.IOUtils;

import de.codesourcery.hex2raw.IntelHex.SwappingInputStream;
import de.codesourcery.hex2raw.IntelHex.SwappingOutputStream;
import junit.framework.TestCase;

public class IntelHexTest extends TestCase {
    
    public void testSwappingOutputStreamOddNumberOfBytes() throws IOException 
    {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        SwappingOutputStream out = new SwappingOutputStream( delegate );
        
        out.write( "012345678".getBytes() );
        out.close();
        final String actual = new String( delegate.toByteArray() );
        assertEquals( "103254768" , actual );
    }
    
    public void testSwappingOutputStreamEvenNumberOfBytes() throws IOException 
    {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        SwappingOutputStream out = new SwappingOutputStream( delegate );
        
        out.write( "01234567".getBytes() );
        out.close();
        final String actual = new String( delegate.toByteArray() );
        assertEquals( "10325476" , actual );
    }    
    
    public void testSwappingOutputStreamOneByte() throws IOException 
    {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        SwappingOutputStream out = new SwappingOutputStream( delegate );
        
        out.write( "3".getBytes() );
        out.close();
        final String actual = new String( delegate.toByteArray() );
        assertEquals( "3" , actual );
    }     
    
    public void testSwappingOutputStreamNoBytes() throws IOException 
    {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        SwappingOutputStream out = new SwappingOutputStream( delegate );
        
        out.write( "".getBytes() );
        out.close();
        assertEquals( 0 , delegate.toByteArray().length );
    }
    
    public void testSwappingInputStreamStreamNoBytes() throws IOException 
    {
        final ByteArrayInputStream delegate = new ByteArrayInputStream( "".getBytes() );
        SwappingInputStream in = new SwappingInputStream( delegate );
     
        final byte[] actual = IOUtils.toByteArray( in );
        assertEquals(  0, actual.length );
    }   
    
    public void testSwappingInputStreamStreamOneByte() throws IOException 
    {
        final ByteArrayInputStream delegate = new ByteArrayInputStream( "x".getBytes() );
        final SwappingInputStream in = new SwappingInputStream( delegate );
        final byte[] actual = IOUtils.toByteArray( in );
        assertEquals(  "x" , new String( actual) );
    }    
    
    public void testSwappingInputStreamStreamTwoByte() throws IOException 
    {
        final ByteArrayInputStream delegate = new ByteArrayInputStream( "ab".getBytes() );
        final SwappingInputStream in = new SwappingInputStream( delegate );
        final byte[] actual = IOUtils.toByteArray( in );
        assertEquals(  "ba" , new String( actual) );
    }   
    
    public void testSwappingInputStreamStreamThreeByte() throws IOException 
    {
        final ByteArrayInputStream delegate = new ByteArrayInputStream( "abc".getBytes() );
        final SwappingInputStream in = new SwappingInputStream( delegate );
        final byte[] actual = IOUtils.toByteArray( in );
        assertEquals(  "bac" , new String( actual) );
    }    
    
    public void testRoundtrips() throws IOException {
        
        final Random rnd = new Random();
        
        for ( int i = 0 ; i < 100 ; i++ ) 
        {
            final int len = 1024 + rnd.nextInt( 2048 );
            final byte[] data = new byte[ len ];
            rnd.nextBytes( data );
            assertRoundTripWorks( data );
        }
    }
    
    private void assertRoundTripWorks(byte[] expected) throws IOException {
        
        final ByteArrayOutputStream hexOut = new ByteArrayOutputStream();
        try 
        {
            new IntelHex().rawToHex( expected , expected.length , hexOut , 0 );
        } finally {
            hexOut.close();
        }
        
        final ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        try ( ByteArrayInputStream hexIn = new ByteArrayInputStream( hexOut.toByteArray() ) ) 
        { 
            new IntelHex().hexToRaw( hexIn, rawOut );
        } finally {
            rawOut.close();
        }
        final byte[] actual = rawOut.toByteArray();
        
        assertEquals( expected.length , actual.length );
        assertTrue( "data does not match" , Arrays.equals( actual,expected) );
    }
}
