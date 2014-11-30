package indi.isnow.japns;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     * @throws UnsupportedEncodingException 
     */
    public void testApp() throws UnsupportedEncodingException
    {
    	byte[][] ls = new byte[5][];
    	for (int i = 0; i < 5; i++) {
    		ls[i] = ("str" + i).getBytes();
    		System.out.println(("str" + i).getBytes());
    	}
    	
    	for (byte[] b : ls) {
    		System.out.println(new String(b, "UTF-8"));
    	}
    	
    	System.out.println((byte) 257);
    	
    }
    
    /// char a is asc code
    private int charVal(final char a) {
        if ('0' <= a && a <= '9') {
            return (a - '0');
        } else if ('a' <= a && a <= 'f') {
            return (a - 'a') + 10;
        } else if ('A' <= a && a <= 'F') {
            return (a - 'A') + 10;
        } else {
            throw new RuntimeException("Invalid hex character: " + a);
        }
    }
    
    public void trimList(List a, int from, int to){
    	a.remove(2);
    }
}
