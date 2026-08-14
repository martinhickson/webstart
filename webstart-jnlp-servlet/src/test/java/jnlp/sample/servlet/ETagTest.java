package jnlp.sample.servlet;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DownloadResponse} entity-tag helpers.
 */
public class ETagTest
        extends TestCase
{

    private static final String ETAG = "\"1a-2b3c\"";

    public void testComputeEtagIsQuoted()
    {
        String etag = DownloadResponse.computeETag( 1000, 123456789L );
        assertTrue( etag.startsWith( "\"" ) );
        assertTrue( etag.endsWith( "\"" ) );
        assertEquals( 2, etag.split( "-" ).length );
    }

    public void testEtagDiffersOnLengthOrMtime()
    {
        assertFalse( DownloadResponse.computeETag( 1000, 123L )
                .equals( DownloadResponse.computeETag( 1001, 123L ) ) );
        assertFalse( DownloadResponse.computeETag( 1000, 123L )
                .equals( DownloadResponse.computeETag( 1000, 124L ) ) );
    }

    public void testEtagMatchesExact()
    {
        assertTrue( DownloadResponse.etagMatches( ETAG, ETAG ) );
        assertFalse( DownloadResponse.etagMatches( "\"other\"", ETAG ) );
    }

    public void testEtagMatchesWeakComparison()
    {
        assertTrue( DownloadResponse.etagMatches( "W/" + ETAG, ETAG ) );
        assertTrue( DownloadResponse.etagMatches( ETAG, "W/" + ETAG ) );
    }

    public void testEtagMatchesList()
    {
        assertTrue( DownloadResponse.etagMatches( "\"x\", " + ETAG + ", \"y\"", ETAG ) );
        assertFalse( DownloadResponse.etagMatches( "\"x\", \"y\"", ETAG ) );
    }

    public void testEtagMatchesWildcard()
    {
        assertTrue( DownloadResponse.etagMatches( "*", ETAG ) );
    }

    public void testEtagMatchesNullAndEmpty()
    {
        assertFalse( DownloadResponse.etagMatches( null, ETAG ) );
        assertFalse( DownloadResponse.etagMatches( "", ETAG ) );
    }
}
