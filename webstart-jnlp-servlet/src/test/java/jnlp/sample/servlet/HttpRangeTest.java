package jnlp.sample.servlet;

import junit.framework.TestCase;

/**
 * Unit tests for {@link HttpRange}.
 */
public class HttpRangeTest
        extends TestCase
{

    private static final long CONTENT_LENGTH = 100;

    public void testNoHeaderReturnsNull()
    {
        assertNull( HttpRange.parse( null, CONTENT_LENGTH ) );
    }

    public void testIsByteRange()
    {
        assertTrue( HttpRange.isByteRange( "bytes=0-9" ) );
        assertTrue( HttpRange.isByteRange( "BYTES=0-9" ) );
        assertTrue( HttpRange.isByteRange( " bytes=0-9 " ) );
        assertFalse( HttpRange.isByteRange( null ) );
        assertFalse( HttpRange.isByteRange( "" ) );
        assertFalse( HttpRange.isByteRange( "items=0-9" ) );
        assertFalse( HttpRange.isByteRange( "noequals" ) );
    }

    public void testUnknownUnitIsIgnored()
    {
        assertNull( HttpRange.parse( "items=0-9", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "chunks=5", CONTENT_LENGTH ) );
    }

    public void testEmptyRangeSpecIsRejected()
    {
        assertNull( HttpRange.parse( "bytes=", CONTENT_LENGTH ) );
    }

    public void testStartOnlyRangeReturnsWholeFile()
    {
        HttpRange range = HttpRange.parse( "bytes=0-", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testEmptySuffixSpecIsRejected()
    {
        assertNull( HttpRange.parse( "bytes=-", CONTENT_LENGTH ) );
    }

    public void testWhitespaceInsideSpecIsRejected()
    {
        assertNull( HttpRange.parse( "bytes= 0 - 9", CONTENT_LENGTH ) );
    }

    public void testOverflowingStartIsUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=99999999999999999999999-", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytes=99999999999999999999999-5", CONTENT_LENGTH ) );
    }

    public void testOverflowingEndClampsToContent()
    {
        HttpRange range = HttpRange.parse( "bytes=90-99999999999999999999999", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 90, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testOverflowingSuffixClampsToWholeContent()
    {
        HttpRange range = HttpRange.parse( "bytes=-99999999999999999999999", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testEmptyHeaderIsNotARange()
    {
        assertNull( HttpRange.parse( "", CONTENT_LENGTH ) );
        assertFalse( HttpRange.isByteRange( "" ) );
    }

    public void testBoundariesWithSingleByteContent()
    {
        HttpRange range = HttpRange.parse( "bytes=0-0", 1 );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 0, range.getEnd() );

        // end clamps to the only byte
        HttpRange clamped = HttpRange.parse( "bytes=0-1", 1 );
        assertNotNull( clamped );
        assertEquals( 0, clamped.getStart() );
        assertEquals( 0, clamped.getEnd() );

        // suffix of the whole content
        HttpRange suffix = HttpRange.parse( "bytes=-1", 1 );
        assertNotNull( suffix );
        assertEquals( 0, suffix.getStart() );
        assertEquals( 0, suffix.getEnd() );

        // suffix larger than content
        HttpRange overSuffix = HttpRange.parse( "bytes=-2", 1 );
        assertNotNull( overSuffix );
        assertEquals( 0, overSuffix.getStart() );
        assertEquals( 0, overSuffix.getEnd() );

        // starting at the end is unsatisfiable
        assertNull( HttpRange.parse( "bytes=1-1", 1 ) );
        assertNull( HttpRange.parse( "bytes=1-", 1 ) );
    }

    public void testRangeStartingAtLastByte()
    {
        HttpRange range = HttpRange.parse( "bytes=99-", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 99, range.getStart() );
        assertEquals( 99, range.getEnd() );

        HttpRange exact = HttpRange.parse( "bytes=99-99", CONTENT_LENGTH );
        assertNotNull( exact );
        assertEquals( 99, exact.getStart() );
        assertEquals( 99, exact.getEnd() );
    }

    public void testSuffixExactlyWholeContent()
    {
        HttpRange range = HttpRange.parse( "bytes=-100", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testSuffixLargerThanWholeContent()
    {
        HttpRange range = HttpRange.parse( "bytes=-500", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testLeadingZeros()
    {
        HttpRange range = HttpRange.parse( "bytes=0005-0009", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 5, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testHugeContentLength()
    {
        long huge = Long.MAX_VALUE;
        HttpRange range = HttpRange.parse( "bytes=0-9", huge );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );

        HttpRange open = HttpRange.parse( "bytes=9223372036854775806-", huge );
        assertNotNull( open );
        assertEquals( Long.MAX_VALUE - 1, open.getStart() );
        assertEquals( Long.MAX_VALUE - 1, open.getEnd() );
    }

    public void testSmallContentSuffix()
    {
        HttpRange range = HttpRange.parse( "bytes=-3", 5 );
        assertNotNull( range );
        assertEquals( 2, range.getStart() );
        assertEquals( 4, range.getEnd() );
    }

    public void testEmptyContentSuffix()
    {
        assertNull( HttpRange.parse( "bytes=-1", 0 ) );
        assertNull( HttpRange.parse( "bytes=-1", -1 ) );
    }

    public void testMultipleRangesWithoutSpacesUsesFirst()
    {
        HttpRange range = HttpRange.parse( "bytes=0-9,10-19", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testSuffixFirstInMultipleRanges()
    {
        HttpRange range = HttpRange.parse( "bytes=-5,0-9", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 95, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testTrailingComma()
    {
        HttpRange range = HttpRange.parse( "bytes=0-9,", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testExplicitThenSuffixMultiple()
    {
        HttpRange range = HttpRange.parse( "bytes=0-9,-20", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testParseAllTwoExplicitRanges()
    {
        java.util.List<HttpRange> ranges = HttpRange.parseAll( "bytes=0-9,20-29", CONTENT_LENGTH );
        assertNotNull( ranges );
        assertEquals( 2, ranges.size() );
        assertEquals( 0, ranges.get( 0 ).getStart() );
        assertEquals( 9, ranges.get( 0 ).getEnd() );
        assertEquals( 20, ranges.get( 1 ).getStart() );
        assertEquals( 29, ranges.get( 1 ).getEnd() );
    }

    public void testParseAllSuffixAndExplicit()
    {
        java.util.List<HttpRange> ranges = HttpRange.parseAll( "bytes=-20,0-9", CONTENT_LENGTH );
        assertNotNull( ranges );
        assertEquals( 2, ranges.size() );
        assertEquals( 80, ranges.get( 0 ).getStart() );
        assertEquals( 99, ranges.get( 0 ).getEnd() );
        assertEquals( 0, ranges.get( 1 ).getStart() );
    }

    public void testParseAllSingleRange()
    {
        java.util.List<HttpRange> ranges = HttpRange.parseAll( "bytes=10-19", CONTENT_LENGTH );
        assertNotNull( ranges );
        assertEquals( 1, ranges.size() );
        assertEquals( 10, ranges.get( 0 ).getStart() );
    }

    public void testParseAllUnsatisfiableReturnsNull()
    {
        assertNull( HttpRange.parseAll( "bytes=0-9,5000-6000", CONTENT_LENGTH ) );
        assertNull( HttpRange.parseAll( "bytes=1000-", CONTENT_LENGTH ) );
    }

    public void testParseAllMixedUnitReturnsNull()
    {
        assertNull( HttpRange.parseAll( "bytes=0-9, items=0-9", CONTENT_LENGTH ) );
    }

    public void testParseAllTrailingComma()
    {
        java.util.List<HttpRange> ranges = HttpRange.parseAll( "bytes=0-9,", CONTENT_LENGTH );
        assertNotNull( ranges );
        assertEquals( 1, ranges.size() );
        assertEquals( 0, ranges.get( 0 ).getStart() );
    }

    public void testParseAllSpaceAroundEqualsReturnsNull()
    {
        assertNull( HttpRange.parseAll( "bytes = 0-9", CONTENT_LENGTH ) );
    }

    public void testSecondRangeMalformedIsIgnored()
    {
        HttpRange range = HttpRange.parse( "bytes=0-9,abc-def", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testRangeHeaderWithoutUnit()
    {
        assertNull( HttpRange.parse( "0-9", CONTENT_LENGTH ) );
    }

    public void testNonBytesUnitStillReturnsNullFromParse()
    {
        assertNull( HttpRange.parse( "bytes2=0-9", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytesx=0-9", CONTENT_LENGTH ) );
    }

    public void testGiantEndOnHugeContent()
    {
        long huge = Long.MAX_VALUE;
        HttpRange range = HttpRange.parse( "bytes=5-999999999999999999999", huge );
        assertNotNull( range );
        assertEquals( 5, range.getStart() );
        assertEquals( Long.MAX_VALUE - 1, range.getEnd() );
    }

    public void testGiantSuffixOnHugeContent()
    {
        long huge = Long.MAX_VALUE;
        HttpRange range = HttpRange.parse( "bytes=-5", huge );
        assertNotNull( range );
        assertEquals( Long.MAX_VALUE - 5, range.getStart() );
        assertEquals( Long.MAX_VALUE - 1, range.getEnd() );
    }

    public void testSingleByteRangeMidContent()
    {
        HttpRange range = HttpRange.parse( "bytes=5-5", 6 );
        assertNotNull( range );
        assertEquals( 5, range.getStart() );
        assertEquals( 5, range.getEnd() );
    }

    public void testEndEqualToContentLengthMinusOne()
    {
        HttpRange range = HttpRange.parse( "bytes=0-99", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testStartOnlyRangeOnSingleByteContent()
    {
        HttpRange range = HttpRange.parse( "bytes=0-", 1 );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 0, range.getEnd() );
    }

    public void testTabInHeaderIsRejected()
    {
        assertNull( HttpRange.parse( "bytes=0-9\t10-19", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytes\t=0-9", CONTENT_LENGTH ) );
    }

    public void testZeroSuffixIsAlwaysUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=-0", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytes=-0", 1 ) );
    }

    public void testMixedCaseUnit()
    {
        HttpRange range = HttpRange.parse( "ByTeS=0-9", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
        assertTrue( HttpRange.isByteRange( "bYtEs=0-9" ) );
    }

    public void testNegativeLikeSpecRejected()
    {
        assertNull( HttpRange.parse( "bytes=-5-10", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytes=5--10", CONTENT_LENGTH ) );
    }

    public void testSuffixLeadingZeros()
    {
        HttpRange range = HttpRange.parse( "bytes=-05", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 95, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testStartAndEndLeadingZeros()
    {
        HttpRange range = HttpRange.parse( "bytes=00-09", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testInvariantSweep()
    {
        String[] specs = { "bytes=0-0", "bytes=0-", "bytes=-1", "bytes=5-5", "bytes=0-9", "bytes=10-20",
                           "bytes=-3" };
        for ( long length = 0; length <= 10; length++ )
        {
            for ( String spec : specs )
            {
                HttpRange range = HttpRange.parse( spec, length );
                if ( range == null )
                {
                    continue;
                }
                assertTrue( "start<=end for " + spec + " len " + length, range.getStart() <= range.getEnd() );
                assertTrue( "start>=0 for " + spec + " len " + length, range.getStart() >= 0 );
                assertTrue( "end<len for " + spec + " len " + length, range.getEnd() < length );
                assertEquals( "length for " + spec + " len " + length, range.getEnd() - range.getStart() + 1,
                              range.getLength() );
            }
        }
    }

    public void testSpaceBetweenUnitAndEquals()
    {
        // the unit still parses as "bytes" but the spec is malformed, so it
        // is recognised as a byte range request and then rejected
        assertTrue( HttpRange.isByteRange( "bytes = 0-9" ) );
        assertNull( HttpRange.parse( "bytes = 0-9", CONTENT_LENGTH ) );
    }

    public void testExplicitStartAndEnd()
    {
        HttpRange range = HttpRange.parse( "bytes=10-19", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 10, range.getStart() );
        assertEquals( 19, range.getEnd() );
        assertEquals( 10, range.getLength() );
    }

    public void testOpenEndedRange()
    {
        HttpRange range = HttpRange.parse( "bytes=90-", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 90, range.getStart() );
        assertEquals( 99, range.getEnd() );
        assertEquals( 10, range.getLength() );
    }

    public void testSuffixRange()
    {
        HttpRange range = HttpRange.parse( "bytes=-10", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 90, range.getStart() );
        assertEquals( 99, range.getEnd() );
        assertEquals( 10, range.getLength() );
    }

    public void testSuffixLargerThanContent()
    {
        HttpRange range = HttpRange.parse( "bytes=-1000", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 99, range.getEnd() );
        assertEquals( 100, range.getLength() );
    }

    public void testEndClampedToContent()
    {
        HttpRange range = HttpRange.parse( "bytes=90-1000", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 90, range.getStart() );
        assertEquals( 99, range.getEnd() );
    }

    public void testRangeUnitIgnored()
    {
        assertNotNull( HttpRange.parse( "BYTES=0-9", CONTENT_LENGTH ) );
    }

    public void testMultipleRangesUsesFirst()
    {
        HttpRange range = HttpRange.parse( "bytes=0-9,20-29", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testWhitespaceTolerated()
    {
        HttpRange range = HttpRange.parse( " bytes=0-9 ", CONTENT_LENGTH );
        assertNotNull( range );
        assertEquals( 0, range.getStart() );
        assertEquals( 9, range.getEnd() );
    }

    public void testStartAtEndIsUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=100-", CONTENT_LENGTH ) );
    }

    public void testStartBeyondEndIsUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=200-300", CONTENT_LENGTH ) );
    }

    public void testZeroLengthSuffixIsUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=-0", CONTENT_LENGTH ) );
    }

    public void testEndBeforeStartIsInvalid()
    {
        assertNull( HttpRange.parse( "bytes=20-10", CONTENT_LENGTH ) );
    }

    public void testGarbageHeaderIsInvalid()
    {
        assertNull( HttpRange.parse( "bytes=", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "bytes=abc-def", CONTENT_LENGTH ) );
        assertNull( HttpRange.parse( "chunked", CONTENT_LENGTH ) );
    }

    public void testEmptyContentIsUnsatisfiable()
    {
        assertNull( HttpRange.parse( "bytes=0-9", 0 ) );
        assertNull( HttpRange.parse( "bytes=0-9", -1 ) );
    }
}
