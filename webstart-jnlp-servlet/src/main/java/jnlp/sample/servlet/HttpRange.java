package jnlp.sample.servlet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single HTTP byte range (RFC 7233) requested by a client and parsed from
 * the {@code Range} request header.
 * <p>
 * The supported forms are {@code bytes=start-end}, {@code bytes=start-} and
 * {@code bytes=-suffix}. When a request carries multiple ranges only the
 * first one is honoured.
 */
public final class HttpRange
{
    private static final Pattern RANGE_SPEC = Pattern.compile( "^bytes=(\\d*)-(\\d*)$", Pattern.CASE_INSENSITIVE );

    private final long _start;

    private final long _end;

    private HttpRange( long start, long end )
    {
        _start = start;
        _end = end;
    }

    /**
     * Checks whether the header selects the {@code bytes} range unit
     * (case-insensitive). An origin server must ignore a {@code Range} header
     * that uses a range unit it does not understand (RFC 7233 section 2.3).
     *
     * @param header the raw {@code Range} header value, or {@code null}
     * @return {@code true} if the header is a byte range request
     */
    public static boolean isByteRange( String header )
    {
        if ( header == null )
        {
            return false;
        }
        String spec = header.trim();
        int eq = spec.indexOf( '=' );
        if ( eq == -1 )
        {
            return false;
        }
        return spec.substring( 0, eq ).trim().equalsIgnoreCase( "bytes" );
    }

    /**
     * Parses a {@code Range} header into a single satisfiable byte range.
     *
     * @param header        the raw {@code Range} header value, or {@code null}
     * @param contentLength total length of the selected representation, or
     *                      {@code -1}/{@code 0} if unknown
     * @return the parsed range, or {@code null} if the header is absent,
     *         malformed or unsatisfiable
     */
    public static HttpRange parse( String header, long contentLength )
    {
        if ( header == null || contentLength <= 0 )
        {
            return null;
        }
        String spec = header.trim();
        int comma = spec.indexOf( ',' );
        if ( comma != -1 )
        {
            spec = spec.substring( 0, comma ).trim();
        }
        Matcher matcher = RANGE_SPEC.matcher( spec );
        if ( !matcher.matches() )
        {
            return null;
        }
        String startSpec = matcher.group( 1 );
        String endSpec = matcher.group( 2 );

        if ( startSpec.isEmpty() )
        {
            // Suffix form: "bytes=-N" requests the last N bytes
            if ( endSpec.isEmpty() )
            {
                return null;
            }
            long suffix = toLong( endSpec );
            if ( suffix <= 0 )
            {
                return null;
            }
            long start = Math.max( contentLength - suffix, 0 );
            return new HttpRange( start, contentLength - 1 );
        }

        long start = toLong( startSpec );
        if ( start < 0 || start >= contentLength )
        {
            return null;
        }
        long end;
        if ( endSpec.isEmpty() )
        {
            end = contentLength - 1;
        }
        else
        {
            end = toLong( endSpec );
            if ( end < start )
            {
                return null;
            }
            end = Math.min( end, contentLength - 1 );
        }
        return new HttpRange( start, end );
    }

    private static long toLong( String value )
    {
        try
        {
            return Long.parseLong( value );
        }
        catch ( NumberFormatException nfe )
        {
            // value is non-empty (enforced by the caller) so this must be an
            // overflowing number; treat it as a very large offset
            return Long.MAX_VALUE;
        }
    }

    public long getStart()
    {
        return _start;
    }

    public long getEnd()
    {
        return _end;
    }

    public long getLength()
    {
        return _end - _start + 1;
    }

    public String toString()
    {
        return "bytes=" + _start + "-" + _end;
    }
}
