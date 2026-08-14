/*
 * @(#)DownloadResponse.java	1.8 07/03/15
 * 
 * Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility.
 */

package jnlp.sample.servlet;

import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.MissingResourceException;

/**
 * A class used to encapsulate a file response, and
 * factory methods to create some common types.
 */
abstract public class DownloadResponse
{
    private static final String HEADER_LASTMOD = "Last-Modified";

    private static final String HEADER_JNLP_VERSION = "x-java-jnlp-version-id";

    private static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";

    private static final String HEADER_CONTENT_RANGE = "Content-Range";

    private static final String HEADER_ETAG = "ETag";

    private static final String HEADER_VARY = "Vary";

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    /**
     * Signed WebStart jars must not be re-encoded by intermediaries, which
     * would break their signatures (RFC 7234 section 5.2.1.6).
     */
    private static final String CACHE_CONTROL_NO_TRANSFORM = "no-transform";

    private static final String BYTES_RANGE_UNIT = "bytes";

    private static final String JNLP_ERROR_MIMETYPE = "application/x-java-jnlp-error";

    public static final int STS_00_OK = 0;

    public static final int ERR_10_NO_RESOURCE = 10;

    public static final int ERR_11_NO_VERSION = 11;

    public static final int ERR_20_UNSUP_OS = 20;

    public static final int ERR_21_UNSUP_ARCH = 21;

    public static final int ERR_22_UNSUP_LOCALE = 22;

    public static final int ERR_23_UNSUP_JRE = 23;

    public static final int ERR_99_UNKNOWN = 99;

    // HTTP Compression RFC 2616 : Standard headers
    public static final String CONTENT_ENCODING = "content-encoding";

    // HTTP Compression RFC 2616 : Standard header for HTTP/Pack200 Compression
    public static final String GZIP_ENCODING = "gzip";

    public static final String PACK200_GZIP_ENCODING = "pack200-gzip";

    public DownloadResponse()
    { /* do nothing */ }

    public String toString()
    {
        return getClass().getName();
    }

    /**
     * Computes a strong entity-tag for a representation from its length and
     * last-modified time (RFC 7232 section 2.3).
     *
     * @param length       content length of the representation
     * @param lastModified last-modified time of the representation
     * @return a quoted opaque-tag
     */
    static String computeETag( long length, long lastModified )
    {
        return "\"" + Long.toHexString( length ) + "-" + Long.toHexString( lastModified ) + "\"";
    }

    /**
     * Compares a single entity-tag against another, using weak comparison
     * (ignoring the {@code W/} prefix) as required by RFC 7232 section 2.3.2.
     */
    private static boolean etagEquals( String candidate, String current )
    {
        String left = candidate.trim();
        String right = current.trim();
        if ( left.startsWith( "W/" ) )
        {
            left = left.substring( 2 ).trim();
        }
        if ( right.startsWith( "W/" ) )
        {
            right = right.substring( 2 ).trim();
        }
        return left.equals( right );
    }

    /**
     * Evaluates an {@code If-None-Match}/{@code If-Match} or entity-tag
     * {@code If-Range} header against the current entity-tag.
     *
     * @param header       the header value, or {@code null}
     * @param currentEtag  the entity-tag of the current representation
     * @return {@code true} when any listed tag (or {@code *}) matches
     */
    static boolean etagMatches( String header, String currentEtag )
    {
        if ( header == null )
        {
            return false;
        }
        String trimmed = header.trim();
        if ( trimmed.equals( "*" ) )
        {
            return true;
        }
        for ( String tag : trimmed.split( "," ) )
        {
            if ( etagEquals( tag, currentEtag ) )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Post information to an HttpResponse
     *
     * @param response TODO
     * @throws IOException TODO
     */
    abstract void sendRespond( HttpServletResponse response )
            throws IOException;

    /**
     * Factory methods for error responses
     *
     * @return TODO
     */
    static DownloadResponse getNotFoundResponse()
    {
        return new NotFoundResponse();
    }

    static DownloadResponse getNoContentResponse()
    {
        return new NotFoundResponse();
    }

    static DownloadResponse getJnlpErrorResponse( int jnlpErrorCode )
    {
        return new JnlpErrorResponse( jnlpErrorCode );
    }

    /**
     * Factory method for file download responses
     */

    static DownloadResponse getNotModifiedResponse( String etag )
    {
        return new NotModifiedResponse( etag );
    }

    /**
     * Factory method for a 412 Precondition Failed response (RFC 7232
     * section 4.2)
     */
    static DownloadResponse getPreconditionFailedResponse()
    {
        return new PreconditionFailedResponse();
    }

    static DownloadResponse getHeadRequestResponse( String mimeType, String versionId, long lastModified,
                                                    long contentLength, String contentEncoding )
    {
        return new HeadRequestResponse( mimeType, versionId, lastModified, contentLength, contentEncoding );
    }

    static DownloadResponse getFileDownloadResponse( byte[] content, String mimeType, long timestamp, String versionId )
    {
        return new ByteArrayFileDownloadResponse( content, mimeType, versionId, timestamp );
    }

    static DownloadResponse getFileDownloadResponse( URL resource, String mimeType, long timestamp, String versionId )
    {
        return new ResourceFileDownloadResponse( resource, mimeType, versionId, timestamp );
    }

    static DownloadResponse getFileDownloadResponse( File file, String mimeType, long timestamp, String versionId )
    {
        return new DiskFileDownloadResponse( file, mimeType, versionId, timestamp );
    }

    /**
     * Factory method for a single-byte-range file download response (HTTP 206)
     *
     * @param resource  URL of the resource
     * @param mimeType  mime-type of the resource
     * @param timestamp last modified timestamp of the resource
     * @param versionId JNLP version-id of the resource
     * @param range     the requested byte range (inclusive)
     */
    static DownloadResponse getFileDownloadResponse( URL resource, String mimeType, long timestamp, String versionId,
                                                     HttpRange range )
    {
        return new ResourceFileDownloadResponse( resource, mimeType, versionId, timestamp, range );
    }

    /**
     * Factory method for a single-byte-range file download response (HTTP 206).
     * Disk-backed content is streamed with a {@link FileChannel}.
     *
     * @param file      file to serve
     * @param mimeType  mime-type of the file
     * @param timestamp last modified timestamp of the file
     * @param versionId JNLP version-id of the file
     * @param range     the requested byte range (inclusive)
     */
    static DownloadResponse getFileDownloadResponse( File file, String mimeType, long timestamp, String versionId,
                                                     HttpRange range )
    {
        return new DiskFileDownloadResponse( file, mimeType, versionId, timestamp, range );
    }

    /**
     * Factory method for an unsatisfiable range response (HTTP 416)
     *
     * @param mimeType      mime-type of the resource
     * @param timestamp     last modified timestamp of the resource
     * @param versionId     JNLP version-id of the resource
     * @param contentLength total length of the representation
     */
    static DownloadResponse getUnsatisfiableRangeResponse( String mimeType, long timestamp, String versionId,
                                                           long contentLength )
    {
        return new UnsatisfiableRangeResponse( mimeType, versionId, timestamp, contentLength );
    }

    /**
     * Factory method for a multi-range {@code multipart/byteranges} response
     * (HTTP 206, RFC 7233 section 4.1). Disk-backed content is streamed with
     * a {@link FileChannel}.
     *
     * @param file      file to serve
     * @param mimeType  mime-type of the resource
     * @param timestamp last modified timestamp of the resource
     * @param versionId JNLP version-id of the resource
     * @param ranges    the satisfiable ranges (in request order)
     */
    static DownloadResponse getMultipartFileDownloadResponse( File file, String mimeType, long timestamp,
                                                              String versionId, List<HttpRange> ranges )
    {
        return new MultipartFileDownloadResponse( file, null, mimeType, versionId, timestamp, ranges );
    }

    /**
     * Factory method for a multi-range {@code multipart/byteranges} response
     * (HTTP 206, RFC 7233 section 4.1) backed by a URL stream.
     *
     * @param resource  URL of the resource
     * @param mimeType  mime-type of the resource
     * @param timestamp last modified timestamp of the resource
     * @param versionId JNLP version-id of the resource
     * @param ranges    the satisfiable ranges (in request order)
     */
    static DownloadResponse getMultipartFileDownloadResponse( URL resource, String mimeType, long timestamp,
                                                              String versionId, List<HttpRange> ranges )
    {
        return new MultipartFileDownloadResponse( null, resource, mimeType, versionId, timestamp, ranges );
    }

    //
    // Private classes implementing the various types
    //

    static private class NotModifiedResponse
            extends DownloadResponse
    {
        private String _etag;

        NotModifiedResponse( String etag )
        {
            _etag = etag;
        }

        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            if ( _etag != null )
            {
                response.setHeader( HEADER_ETAG, _etag );
            }
            response.sendError( HttpServletResponse.SC_NOT_MODIFIED );
        }
    }

    static private class PreconditionFailedResponse
            extends DownloadResponse
    {
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            response.sendError( HttpServletResponse.SC_PRECONDITION_FAILED );
        }
    }

    static private class NotFoundResponse
            extends DownloadResponse
    {
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            response.sendError( HttpServletResponse.SC_NOT_FOUND );
        }
    }

    static private class NoContentResponse
            extends DownloadResponse
    {
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            response.sendError( HttpServletResponse.SC_NO_CONTENT );
        }
    }

    static private class UnsatisfiableRangeResponse
            extends DownloadResponse
    {
        private String _mimeType;

        private String _versionId;

        private long _lastModified;

        private long _contentLength;

        UnsatisfiableRangeResponse( String mimeType, String versionId, long lastModified, long contentLength )
        {
            _mimeType = mimeType;
            _versionId = versionId;
            _lastModified = lastModified;
            _contentLength = contentLength;
        }

        /**
         * Post information to an HttpResponse
         */
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            response.setStatus( HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE );
            response.setContentType( _mimeType );
            response.setHeader( HEADER_ACCEPT_RANGES, BYTES_RANGE_UNIT );
            response.setHeader( HEADER_CONTENT_RANGE, BYTES_RANGE_UNIT + " */" + _contentLength );
            response.setHeader( HEADER_ETAG, computeETag( _contentLength, _lastModified ) );
            response.setHeader( HEADER_VARY, "Accept-Encoding" );
            response.setHeader( HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_TRANSFORM );
            if ( _versionId != null )
            {
                response.setHeader( HEADER_JNLP_VERSION, _versionId );
            }
            if ( _lastModified != 0 )
            {
                response.setDateHeader( HEADER_LASTMOD, _lastModified );
            }
        }
    }

    static private class HeadRequestResponse
            extends DownloadResponse
    {
        private String _mimeType;

        private String _versionId;

        private long _lastModified;

        private long _contentLength;

        private String _contentEncoding;

        HeadRequestResponse( String mimeType, String versionId, long lastModified, long contentLength,
                             String contentEncoding )
        {
            _mimeType = mimeType;
            _versionId = versionId;
            _lastModified = lastModified;
            _contentLength = contentLength;
            _contentEncoding = contentEncoding;
        }

        /**
         * Post information to an HttpResponse
         */
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            // Set header information
            response.setContentType( _mimeType );
            response.setContentLengthLong( _contentLength );
            response.setHeader( HEADER_ACCEPT_RANGES, BYTES_RANGE_UNIT );
            response.setHeader( HEADER_ETAG, computeETag( _contentLength, _lastModified ) );
            response.setHeader( HEADER_VARY, "Accept-Encoding" );
            response.setHeader( HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_TRANSFORM );
            if ( _versionId != null )
            {
                response.setHeader( HEADER_JNLP_VERSION, _versionId );
            }
            if ( _lastModified != 0 )
            {
                response.setDateHeader( HEADER_LASTMOD, _lastModified );
            }
            if ( _contentEncoding != null )
            {
                response.setHeader( CONTENT_ENCODING, _contentEncoding );
            }
            response.setStatus( HttpServletResponse.SC_OK );
        }
    }

    static public class JnlpErrorResponse
            extends DownloadResponse
    {
        private String _message;

        public JnlpErrorResponse( int jnlpErrorCode )
        {
            String msg = Integer.toString( jnlpErrorCode );
            String dsc = "No description";
            try
            {
                dsc = JnlpDownloadServlet.getResourceBundle().getString( "servlet.jnlp.err." + msg );
            }
            catch ( MissingResourceException mre )
            { /* ignore */}
            _message = msg + " " + dsc;
        }

        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            response.setContentType( JNLP_ERROR_MIMETYPE );
            PrintWriter pw = response.getWriter();
            pw.println( _message );
        }

        ;

        public String toString()
        {
            return super.toString() + "[" + _message + "]";
        }
    }

    static private abstract class FileDownloadResponse
            extends DownloadResponse
    {
        private String _mimeType;

        private String _versionId;

        private long _lastModified;

        private String _fileName;

        private HttpRange _range;

        FileDownloadResponse( String mimeType, String versionId, long lastModified )
        {
            this( mimeType, versionId, lastModified, null, null );
        }

        FileDownloadResponse( String mimeType, String versionId, long lastModified, String fileName )
        {
            this( mimeType, versionId, lastModified, fileName, null );
        }

        FileDownloadResponse( String mimeType, String versionId, long lastModified, String fileName, HttpRange range )
        {
            _mimeType = mimeType;
            _versionId = versionId;
            _lastModified = lastModified;
            _fileName = fileName;
            _range = range;
        }


        /**
         * Information about response
         */
        String getMimeType()
        {
            return _mimeType;
        }

        String getVersionId()
        {
            return _versionId;
        }

        long getLastModified()
        {
            return _lastModified;
        }

        HttpRange getRange()
        {
            return _range;
        }

        abstract long getContentLength()
                throws IOException;

        abstract InputStream getContent()
                throws IOException;

        /**
         * Post information to an HttpResponse
         */
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            long length = getContentLength();

            // Set header information
            response.setContentType( getMimeType() );
            response.setHeader( HEADER_ACCEPT_RANGES, BYTES_RANGE_UNIT );
            response.setHeader( HEADER_ETAG, computeETag( length, getLastModified() ) );
            response.setHeader( HEADER_VARY, "Accept-Encoding" );
            response.setHeader( HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_TRANSFORM );
            if ( getVersionId() != null )
            {
                response.setHeader( HEADER_JNLP_VERSION, getVersionId() );
            }
            if ( getLastModified() != 0 )
            {
                response.setDateHeader( HEADER_LASTMOD, getLastModified() );
            }
            if ( _fileName != null )
            {

                if ( _fileName.endsWith( ".pack.gz" ) )
                {
                    response.setHeader( CONTENT_ENCODING, PACK200_GZIP_ENCODING );
                }
                else if ( _fileName.endsWith( ".gz" ) )
                {
                    response.setHeader( CONTENT_ENCODING, GZIP_ENCODING );
                }
                else
                {
                    response.setHeader( CONTENT_ENCODING, null );
                }
            }

            if ( _range != null )
            {
                // HTTP 206 Partial Content (RFC 7233)
                response.setStatus( HttpServletResponse.SC_PARTIAL_CONTENT );
                response.setHeader( HEADER_CONTENT_RANGE,
                                    BYTES_RANGE_UNIT + " " + _range.getStart() + "-" + _range.getEnd() + "/" + length );
                response.setContentLengthLong( _range.getLength() );
            }
            else
            {
                response.setContentLengthLong( length );
            }

            sendContent( response );
        }

        /**
         * Streams the response body. Subclasses may override this to provide a
         * more efficient transfer mechanism (e.g. NIO).
         */
        void sendContent( HttpServletResponse response )
                throws IOException
        {
            InputStream in = getContent();
            OutputStream out = response.getOutputStream();
            try
            {
                long skip = ( _range == null ) ? 0 : _range.getStart();
                long remaining = ( _range == null ) ? Long.MAX_VALUE : _range.getLength();
                while ( skip > 0 )
                {
                    long skipped = in.skip( skip );
                    if ( skipped <= 0 )
                    {
                        break;
                    }
                    skip -= skipped;
                }
                byte[] bytes = new byte[32 * 1024];
                int read;
                while ( remaining > 0 && ( read = in.read( bytes, 0, (int) Math.min( remaining, bytes.length ) ) ) != -1 )
                {
                    out.write( bytes, 0, read );
                    remaining -= read;
                }
            }
            finally
            {
                if ( in != null )
                {
                    in.close();
                }
            }
        }

        protected String getArgString()
        {
            long length = 0;
            try
            {
                length = getContentLength();
            }
            catch ( IOException ioe )
            { /* ignore */ }
            return "Mimetype=" + getMimeType() + " VersionId=" + getVersionId() + " Timestamp=" +
                    new Date( getLastModified() ) + " Length=" + length;
        }
    }

    static private class ByteArrayFileDownloadResponse
            extends FileDownloadResponse
    {
        private byte[] _content;

        ByteArrayFileDownloadResponse( byte[] content, String mimeType, String versionId, long lastModified )
        {
            super( mimeType, versionId, lastModified );
            _content = content;
        }

        long getContentLength()
        {
            return _content.length;
        }

        InputStream getContent()
        {
            return new ByteArrayInputStream( _content );
        }

        public String toString()
        {
            return super.toString() + "[ " + getArgString() + "]";
        }
    }

    static private class ResourceFileDownloadResponse
            extends FileDownloadResponse
    {
        URL _url;

        ResourceFileDownloadResponse( URL url, String mimeType, String versionId, long lastModified )
        {
            super( mimeType, versionId, lastModified, url.toString() );
            _url = url;
        }

        ResourceFileDownloadResponse( URL url, String mimeType, String versionId, long lastModified, HttpRange range )
        {
            super( mimeType, versionId, lastModified, url.toString(), range );
            _url = url;
        }

        long getContentLength()
                throws IOException
        {
            return _url.openConnection().getContentLengthLong();
        }

        InputStream getContent()
                throws IOException
        {
            return _url.openConnection().getInputStream();
        }

        public String toString()
        {
            return super.toString() + "[ " + getArgString() + "]";
        }
    }

    static private class DiskFileDownloadResponse
            extends FileDownloadResponse
    {
        private File _file;

        DiskFileDownloadResponse( File file, String mimeType, String versionId, long lastModified )
        {
            super( mimeType, versionId, lastModified, file.getName() );
            _file = file;
        }

        DiskFileDownloadResponse( File file, String mimeType, String versionId, long lastModified, HttpRange range )
        {
            super( mimeType, versionId, lastModified, file.getName(), range );
            _file = file;
        }

        long getContentLength()
                throws IOException
        {
            return _file.length();
        }

        InputStream getContent()
                throws IOException
        {
            return new BufferedInputStream( new FileInputStream( _file ) );
        }

        void sendContent( HttpServletResponse response )
                throws IOException
        {
            HttpRange range = getRange();
            if ( range == null )
            {
                // no range requested - fall back to plain streamed copy
                super.sendContent( response );
                return;
            }

            // Serve the requested range straight from a FileChannel (java.nio)
            OutputStream out = response.getOutputStream();
            WritableByteChannel target = Channels.newChannel( out );
            try ( FileChannel channel = FileChannel.open( _file.toPath(), StandardOpenOption.READ ) )
            {
                long position = range.getStart();
                long remaining = range.getLength();
                while ( remaining > 0 )
                {
                    long written = channel.transferTo( position, remaining, target );
                    if ( written <= 0 )
                    {
                        break;
                    }
                    position += written;
                    remaining -= written;
                }
            }
        }

        public String toString()
        {
            return super.toString() + "[ " + getArgString() + "]";
        }
    }

    static private class MultipartFileDownloadResponse
            extends DownloadResponse
    {
        private File _file;

        private URL _resource;

        private String _mimeType;

        private String _versionId;

        private long _lastModified;

        private List<HttpRange> _ranges;

        MultipartFileDownloadResponse( File file, URL resource, String mimeType, String versionId, long lastModified,
                                       List<HttpRange> ranges )
        {
            _file = file;
            _resource = resource;
            _mimeType = mimeType;
            _versionId = versionId;
            _lastModified = lastModified;
            _ranges = ranges;
        }

        private long getTotalLength()
                throws IOException
        {
            if ( _file != null )
            {
                return _file.length();
            }
            return _resource.openConnection().getContentLengthLong();
        }

        /**
         * Writes a single range's bytes to the given output.
         */
        private void writeRange( HttpRange range, OutputStream out )
                throws IOException
        {
            if ( _file != null )
            {
                WritableByteChannel target = Channels.newChannel( out );
                try ( FileChannel channel = FileChannel.open( _file.toPath(), StandardOpenOption.READ ) )
                {
                    long position = range.getStart();
                    long remaining = range.getLength();
                    while ( remaining > 0 )
                    {
                        long written = channel.transferTo( position, remaining, target );
                        if ( written <= 0 )
                        {
                            break;
                        }
                        position += written;
                        remaining -= written;
                    }
                }
                return;
            }
            try ( InputStream in = _resource.openConnection().getInputStream() )
            {
                long skip = range.getStart();
                while ( skip > 0 )
                {
                    long skipped = in.skip( skip );
                    if ( skipped <= 0 )
                    {
                        break;
                    }
                    skip -= skipped;
                }
                byte[] bytes = new byte[32 * 1024];
                long remaining = range.getLength();
                int read;
                while ( remaining > 0 && ( read = in.read( bytes, 0, (int) Math.min( remaining, bytes.length ) ) ) != -1 )
                {
                    out.write( bytes, 0, read );
                    remaining -= read;
                }
            }
        }

        /**
         * Post information to an HttpResponse
         */
        public void sendRespond( HttpServletResponse response )
                throws IOException
        {
            long totalLength = getTotalLength();
            String boundary = "jnlp-" + Long.toHexString( System.nanoTime() ) + "-" + Long.toHexString(
                    new java.util.Random().nextLong() );

            response.setStatus( HttpServletResponse.SC_PARTIAL_CONTENT );
            response.setContentType( "multipart/byteranges; boundary=" + boundary );
            response.setHeader( HEADER_ACCEPT_RANGES, BYTES_RANGE_UNIT );
            response.setHeader( HEADER_ETAG, computeETag( totalLength, _lastModified ) );
            response.setHeader( HEADER_VARY, "Accept-Encoding" );
            response.setHeader( HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_TRANSFORM );
            if ( _versionId != null )
            {
                response.setHeader( HEADER_JNLP_VERSION, _versionId );
            }
            if ( _lastModified != 0 )
            {
                response.setDateHeader( HEADER_LASTMOD, _lastModified );
            }

            // compute the total response length up front
            long bodyLength = 0;
            for ( HttpRange range : _ranges )
            {
                bodyLength += 2 + boundary.length() + 2; // "--boundary\r\n"
                bodyLength += partHeader( range, totalLength ).length();
                bodyLength += range.getLength();
                bodyLength += 2; // "\r\n"
            }
            bodyLength += 2 + boundary.length() + 4; // "--boundary--\r\n"
            response.setContentLengthLong( bodyLength );

            OutputStream out = response.getOutputStream();
            for ( HttpRange range : _ranges )
            {
                out.write( ( "--" + boundary + "\r\n" ).getBytes( "UTF-8" ) );
                out.write( partHeader( range, totalLength ).getBytes( "UTF-8" ) );
                writeRange( range, out );
                out.write( ( "\r\n" ).getBytes( "UTF-8" ) );
            }
            out.write( ( "--" + boundary + "--\r\n" ).getBytes( "UTF-8" ) );
        }

        private String partHeader( HttpRange range, long totalLength )
        {
            return "Content-Type: " + _mimeType + "\r\n" + "Content-Range: " + BYTES_RANGE_UNIT + " " +
                    range.getStart() + "-" + range.getEnd() + "/" + totalLength + "\r\n\r\n";
        }
    }
}



