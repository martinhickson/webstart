package jnlp.sample.servlet;

import junit.framework.TestCase;

/**
 * Unit tests for {@link JnlpResource#acceptsCoding}.
 */
public class AcceptEncodingTest
        extends TestCase
{

    private static final String GZIP = "gzip";

    public void testNullHeaderDoesNotAccept()
    {
        assertFalse( JnlpResource.acceptsCoding( null, GZIP ) );
    }

    public void testEmptyHeaderDoesNotAccept()
    {
        assertFalse( JnlpResource.acceptsCoding( "", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( " ", GZIP ) );
    }

    public void testExplicitMatchAccepts()
    {
        assertTrue( JnlpResource.acceptsCoding( "gzip", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "br, gzip", GZIP ) );
    }

    public void testCaseInsensitive()
    {
        assertTrue( JnlpResource.acceptsCoding( "GZIP", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "Pack200-Gzip", "pack200-gzip" ) );
    }

    public void testQValueAccepts()
    {
        assertTrue( JnlpResource.acceptsCoding( "gzip;q=0.5", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "gzip;q=1", GZIP ) );
    }

    public void testQZeroRejects()
    {
        assertFalse( JnlpResource.acceptsCoding( "gzip;q=0", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "gzip;q=0.0", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "gzip;q=0, identity", GZIP ) );
    }

    public void testMalformedQRejects()
    {
        assertFalse( JnlpResource.acceptsCoding( "gzip;q=abc", GZIP ) );
    }

    public void testMissingCodingDoesNotAccept()
    {
        assertFalse( JnlpResource.acceptsCoding( "identity", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "br", GZIP ) );
    }

    public void testWildcardDoesNotEnableCoding()
    {
        assertFalse( JnlpResource.acceptsCoding( "*", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "*;q=1", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "*;q=0", GZIP ) );
    }

    public void testPack200GzipDoesNotImplyPlainGzip()
    {
        // pack200-gzip is a distinct content coding (RFC 7231)
        assertFalse( JnlpResource.acceptsCoding( "pack200-gzip", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "pack200-gzip", "pack200-gzip" ) );
    }

    public void testFirstMentionWins()
    {
        // the first mention of a coding decides, mirroring the header order
        assertFalse( JnlpResource.acceptsCoding( "gzip;q=0, gzip;q=1", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "gzip;q=1, gzip;q=0", GZIP ) );
    }

    public void testSpaceBeforeQParam()
    {
        assertFalse( JnlpResource.acceptsCoding( "gzip; q=0", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "gzip ; q=0", GZIP ) );
        assertTrue( JnlpResource.acceptsCoding( "gzip ; q=0.5", GZIP ) );
    }

    public void testTabBeforeQParam()
    {
        assertFalse( JnlpResource.acceptsCoding( "gzip;\tq=0", GZIP ) );
    }

    public void testExtraParamsIgnored()
    {
        assertTrue( JnlpResource.acceptsCoding( "gzip;q=0.5;foo=bar", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "gzip;foo=bar;q=0", GZIP ) );
    }

    public void testHeaderWithTrailingComma()
    {
        assertTrue( JnlpResource.acceptsCoding( "gzip,", GZIP ) );
        assertFalse( JnlpResource.acceptsCoding( "br,", GZIP ) );
    }
}
