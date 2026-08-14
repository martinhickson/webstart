/*
 * @(#)JnlpDownloadServlet.java	1.10 07/03/15
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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * This Servlet class is an implementation of JNLP Specification's
 * Download Protocols.
 * <p>
 * All requests to this servlet is in the form of HTTP GET commands.
 * The parameters that are needed are:
 * <ul>
 * <li><code>arch</code>,</li>
 * <li><code>os</code>,</li>
 * <li><code>locale</code>,</li>
 * <li><code>version-id</code> or <code>platform-version-id</code>,</li>
 * <li><code>current-version-id</code>,</li>
 * <li><code>known-platforms</code></li>
 * </ul>
 *
 * @version 1.8 01/23/03
 */
public class JnlpDownloadServlet
        extends HttpServlet
{

    // Localization
    private static ResourceBundle _resourceBundle = null;

    // Servlet configuration
    private static final String PARAM_JNLP_EXTENSION = "jnlp-extension";

    private static final String PARAM_JAR_EXTENSION = "jar-extension";

    private static final String PARAM_JNLP_FILE_HANDLER_HOOK = "jnlp-file-handler-hook";

    // Servlet configuration
    private Logger _log = null;

    private JnlpFileHandler _jnlpFileHandler = null;

    private JarDiffHandler _jarDiffHandler = null;

    private ResourceCatalog _resourceCatalog = null;

    /**
     * Initialize servlet
     */
    public void init( ServletConfig config )
            throws ServletException
    {
        super.init( config );

        // Setup logging
        _log = new Logger( config, getResourceBundle() );
        _log.addDebug( "Initializing" );

        // Get extension from Servlet configuration, or use default
        JnlpResource.setDefaultExtensions( config.getInitParameter( PARAM_JNLP_EXTENSION ),
                                           config.getInitParameter( PARAM_JAR_EXTENSION ) );

        JnlpFileHandlerHook hook = createHook( config.getInitParameter(PARAM_JNLP_FILE_HANDLER_HOOK) );
	_jnlpFileHandler = new JnlpFileHandler( config.getServletContext(), hook, _log );
        _jarDiffHandler = new JarDiffHandler( config.getServletContext(), _log );
        _resourceCatalog = new ResourceCatalog( config.getServletContext(), _log );
    }

    /**
     * Creates the instance of the configured {@link JnlpFileHandlerHook}
     * 
     * @param hookClass class
     * @return the post-processor instance, never {@code null}
     */
    private JnlpFileHandlerHook createHook( String hookClass ) {
        if ( hookClass != null )
            try {
                return Class.forName( hookClass ).asSubclass( JnlpFileHandlerHook.class ).newInstance();
            } catch ( InstantiationException | IllegalAccessException | ClassNotFoundException e ) {
                _log.addWarning( "servlet.log.warning.failed-jnlp-file-hook", hookClass, e );
            }
        return JnlpFileHandlerHook.IDENTITY;
    }

    public static synchronized ResourceBundle getResourceBundle()
    {
        if ( _resourceBundle == null )
        {
            _resourceBundle = ResourceBundle.getBundle( "jnlp/sample/servlet/resources/strings" );
        }
        return _resourceBundle;
    }


    public void doHead( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
    {
        handleRequest( request, response, true );
    }

    /**
     * We handle get requests too - eventhough the spec. only requeres POST requests
     */
    public void doGet( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
    {
        handleRequest( request, response, false );
    }

    private void handleRequest( HttpServletRequest request, HttpServletResponse response, boolean isHead )
            throws IOException
    {
        String requestStr = request.getRequestURI();
        if ( request.getQueryString() != null )
        {
            requestStr += "?" + request.getQueryString().trim();
        }

        // Parse HTTP request
        DownloadRequest dreq = new DownloadRequest( getServletContext(), request );
        if ( _log.isInformationalLevel() )
        {
            _log.addInformational( "servlet.log.info.request", requestStr );
            _log.addInformational( "servlet.log.info.useragent", request.getHeader( "User-Agent" ) );
        }
        if ( _log.isDebugLevel() )
        {
            _log.addDebug( dreq.toString() );
        }

        // A malformed If-Modified-Since must be ignored, not fail the request
        // (RFC 7232 section 3.3)
        long ifModifiedSince = -1;
        try
        {
            ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
        }
        catch ( IllegalArgumentException iae )
        {
            // header present but not a valid date - ignore it
        }

        // Check if it is a valid request
        try
        {
            // Check if the request is valid
            validateRequest( dreq );

            // Decide what resource to return
            JnlpResource jnlpres = locateResource( dreq );
            _log.addDebug( "JnlpResource: " + jnlpres );

            if ( _log.isInformationalLevel() )
            {
                _log.addInformational( "servlet.log.info.goodrequest", jnlpres.getPath() );
            }

            DownloadResponse dres;

            // Validators are evaluated against the selected representation, so
            // when Accept-Encoding negotiates a gzip/pack200 variant that
            // variant's validators apply (RFC 7232 section 2.2)
            JnlpResource repRes = ( dreq.getEncoding() == null ) ? jnlpres : resolveEncoding( jnlpres, dreq );
            File repFile = getRealFile( repRes );
            long repLength = ( repFile != null ) ? repFile.length()
                    : repRes.getResource().openConnection().getContentLengthLong();
            String etag = DownloadResponse.computeETag( repLength, repRes.getLastModified() );

            // If-Match: fail closed when the validator does not match
            // (RFC 7232 section 3.1)
            String ifMatch = request.getHeader( "If-Match" );
            if ( ifMatch != null && !DownloadResponse.etagMatches( ifMatch, etag ) )
            {
                _log.addDebug( "If-Match does not match - 412" );
                DownloadResponse.getPreconditionFailedResponse().sendRespond( response );
                return;
            }

            // If-None-Match takes precedence over If-Modified-Since
            // (RFC 7232 section 3.2); applies to both GET and HEAD
            String ifNoneMatch = request.getHeader( "If-None-Match" );
            boolean notModified = false;
            if ( ifNoneMatch != null && DownloadResponse.etagMatches( ifNoneMatch, etag ) )
            {
                notModified = true;
            }
            else if ( ifModifiedSince != -1 && dreq.getRange() == null &&
                    ( ifModifiedSince / 1000 ) >= ( repRes.getLastModified() / 1000 ) )
            {
                // We divide the value returned by getLastModified here by 1000
                // because if protocol is HTTP, last 3 digits will always be 
                // zero.  However, if protocol is JNDI, that's not the case.
                // so we divide the value by 1000 to remove the last 3 digits
                // before comparison
                notModified = true;
            }

            if ( notModified )
            {
                _log.addDebug( "return 304 Not modified" );
                dres = DownloadResponse.getNotModifiedResponse( etag );

            }
            else if ( isHead )
            {
                // HEAD mirrors what a GET would return (RFC 9110 section 9.3.2)
                dres = DownloadResponse.getHeadRequestResponse( repRes.getMimeType(), repRes.getReturnVersionId(),
                                                                repRes.getLastModified(), repLength,
                                                                getContentEncoding( repRes.getPath() ) );

            }
            else
            {

                // Return selected resource
                dres = constructResponse( jnlpres, dreq );
            }

            dres.sendRespond( response );

        }
        catch ( ErrorResponseException ere )
        {
            if ( _log.isInformationalLevel() )
            {
                _log.addInformational( "servlet.log.info.badrequest", requestStr );
            }
            if ( _log.isDebugLevel() )
            {
                _log.addDebug( "Response: " + ere.toString() );
            }
            // Return response from exception
            ere.getDownloadResponse().sendRespond( response );
        }
        catch ( Throwable e )
        {
            _log.addFatal( "servlet.log.fatal.internalerror", e );
            response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
        }
    }

    /**
     * Make sure that it is a valid request. This is also the place to implement the
     * reverse IP lookup
     */
    private void validateRequest( DownloadRequest dreq )
            throws ErrorResponseException
    {
        String path = dreq.getPath();
        if ( path.endsWith( ResourceCatalog.VERSION_XML_FILENAME ) || path.indexOf( "__" ) != -1 )
        {
            throw new ErrorResponseException( DownloadResponse.getNoContentResponse() );
        }
    }

    /**
     * Interprets the download request and convert it into a resource that is
     * part of the Web Archive.
     */
    private JnlpResource locateResource( DownloadRequest dreq )
            throws IOException, ErrorResponseException
    {
        if ( dreq.getVersion() == null )
        {
            return handleBasicDownload( dreq );
        }
        else
        {
            return handleVersionRequest( dreq );
        }
    }

    private JnlpResource handleBasicDownload( DownloadRequest dreq )
            throws ErrorResponseException, IOException
    {
        _log.addDebug( "Basic Protocol lookup" );
        // Do not return directory names for basic protocol
        if ( dreq.getPath() == null || dreq.getPath().endsWith( "/" ) )
        {
            throw new ErrorResponseException( DownloadResponse.getNoContentResponse() );
        }
        // Lookup resource
        JnlpResource jnlpres = new JnlpResource( getServletContext(), dreq.getPath() );
        if ( !jnlpres.exists() )
        {
            throw new ErrorResponseException( DownloadResponse.getNoContentResponse() );
        }
        return jnlpres;
    }

    private JnlpResource handleVersionRequest( DownloadRequest dreq )
            throws IOException, ErrorResponseException
    {
        _log.addDebug( "Version-based/Extension based lookup" );
        return _resourceCatalog.lookupResource( dreq );
    }

    /**
     * Given a DownloadPath and a DownloadRequest, it constructs the data stream to return
     * to the requester
     */
    private DownloadResponse constructResponse( JnlpResource jnlpres, DownloadRequest dreq )
            throws IOException
    {
        String path = jnlpres.getPath();
        if ( jnlpres.isJnlpFile() )
        {
            // It is a JNLP file. It need to be macro-expanded, so it is handled differently
            boolean supportQuery = JarDiffHandler.isJavawsVersion( dreq, "1.5+" );
            _log.addDebug( "SupportQuery in Href: " + supportQuery );

            // only support query string in href for 1.5 and above
            if ( supportQuery )
            {
                return _jnlpFileHandler.getJnlpFileEx( jnlpres, dreq );
            }
            else
            {
                return _jnlpFileHandler.getJnlpFile( jnlpres, dreq );
            }
        }

        // Check if a JARDiff can be returned
        if ( dreq.getCurrentVersionId() != null && jnlpres.isJarFile() )
        {
            DownloadResponse response = _jarDiffHandler.getJarDiffEntry( _resourceCatalog, dreq, jnlpres );
            if ( response != null )
            {
                _log.addInformational( "servlet.log.info.jardiff.response" );
                return response;
            }
        }

        // check and see if we can use pack resource
        JnlpResource jr = resolveEncoding( jnlpres, dreq );

        _log.addDebug( "Real resource returned: " + jr );

        // Prefer the real on-disk file so that range requests can be served
        // with a NIO FileChannel (falls back to a streamed URL otherwise)
        File file = getRealFile( jr );

        // HTTP Range (RFC 7233) - used by JNLP clients to resume interrupted
        // downloads. Only honoured for single ranges on file resources.
        // Range units other than "bytes" must be ignored (RFC 7233 section 2.3).
        String rangeHeader = dreq.getRange();
        boolean honorRange = rangeHeader != null && HttpRange.isByteRange( rangeHeader );
        if ( honorRange )
        {
            long contentLength =
                    ( file != null ) ? file.length() : jr.getResource().openConnection().getContentLengthLong();

            // If-Range (RFC 7233 section 3.2): if the validator does not match
            // the current representation, ignore the Range header and send the
            // full representation instead. The validator applies to the
            // encoding-negotiated variant actually served (jr). Both the date
            // and entity-tag forms are supported.
            long ifRange = dreq.getIfRange();
            String ifRangeHeader = dreq.getIfRangeHeader();
            String currentEtag = DownloadResponse.computeETag( contentLength, jr.getLastModified() );
            boolean ifRangeCurrent;
            if ( ifRangeHeader != null && ifRange == -1 &&
                    ( ifRangeHeader.trim().startsWith( "\"" ) || ifRangeHeader.trim().startsWith( "W/" ) ) )
            {
                // entity-tag form
                ifRangeCurrent = DownloadResponse.etagMatches( ifRangeHeader, currentEtag );
            }
            else if ( ifRange == -1 || jr.getLastModified() == 0 )
            {
                // no usable If-Range (or a malformed date) - honour the range
                ifRangeCurrent = true;
            }
            else
            {
                // date form
                ifRangeCurrent = ifRange / 1000 >= jr.getLastModified() / 1000;
            }
            if ( !ifRangeCurrent )
            {
                _log.addDebug( "If-Range does not match - sending full content" );
                honorRange = false;
            }
        }
        if ( honorRange )
        {
            long contentLength =
                    ( file != null ) ? file.length() : jr.getResource().openConnection().getContentLengthLong();
            java.util.List<HttpRange> ranges = HttpRange.parseAll( rangeHeader, contentLength );
            if ( ranges == null )
            {
                _log.addDebug( "Requested range not satisfiable: " + rangeHeader );
                return DownloadResponse.getUnsatisfiableRangeResponse( jr.getMimeType(), jr.getLastModified(),
                                                                       jr.getReturnVersionId(), contentLength );
            }
            if ( ranges.size() == 1 )
            {
                HttpRange range = ranges.get( 0 );
                if ( file != null )
                {
                    return DownloadResponse.getFileDownloadResponse( file, jr.getMimeType(), jr.getLastModified(),
                                                                     jr.getReturnVersionId(), range );
                }
                return DownloadResponse.getFileDownloadResponse( jr.getResource(), jr.getMimeType(),
                                                                 jr.getLastModified(), jr.getReturnVersionId(), range );
            }
            // multiple satisfiable ranges -> multipart/byteranges (RFC 7233 4.1)
            if ( file != null )
            {
                return DownloadResponse.getMultipartFileDownloadResponse( file, jr.getMimeType(), jr.getLastModified(),
                                                                          jr.getReturnVersionId(), ranges );
            }
            return DownloadResponse.getMultipartFileDownloadResponse( jr.getResource(), jr.getMimeType(),
                                                                      jr.getLastModified(), jr.getReturnVersionId(),
                                                                      ranges );
        }

        // Return WAR file resource
        if ( file != null )
        {
            return DownloadResponse.getFileDownloadResponse( file, jr.getMimeType(), jr.getLastModified(),
                                                             jr.getReturnVersionId() );
        }
        return DownloadResponse.getFileDownloadResponse( jr.getResource(), jr.getMimeType(), jr.getLastModified(),
                                                         jr.getReturnVersionId() );
    }

    /**
     * Re-resolves a resource honouring the request's {@code Accept-Encoding}
     * (e.g. serving the gzip or pack200-gzip variant when available).
     *
     * @param jnlpres the located resource
     * @param dreq    the download request
     * @return the encoding-negotiated resource
     */
    private JnlpResource resolveEncoding( JnlpResource jnlpres, DownloadRequest dreq )
    {
        return new JnlpResource( getServletContext(), jnlpres.getName(), jnlpres.getVersionId(), jnlpres.getOSList(),
                                 jnlpres.getArchList(), jnlpres.getLocaleList(), jnlpres.getPath(),
                                 jnlpres.getReturnVersionId(), dreq.getEncoding() );
    }

    /**
     * Returns the {@code Content-Encoding} value implied by a resource path,
     * or {@code null} for an unencoded resource.
     *
     * @param path resource path
     * @return the content-encoding, or {@code null}
     */
    private String getContentEncoding( String path )
    {
        if ( path.endsWith( ".pack.gz" ) )
        {
            return DownloadResponse.PACK200_GZIP_ENCODING;
        }
        if ( path.endsWith( ".gz" ) )
        {
            return DownloadResponse.GZIP_ENCODING;
        }
        return null;
    }

    /**
     * Resolves a {@link JnlpResource} to a real on-disk file when possible,
     * otherwise returns {@code null}.
     *
     * @param jnlpres the located resource
     * @return the backing file, or {@code null} if the resource is not
     *         file-backed (e.g. packaged inside a WAR archive)
     */
    private File getRealFile( JnlpResource jnlpres )
    {
        String realPath = getServletContext().getRealPath( jnlpres.getPath() );
        if ( realPath != null )
        {
            File file = new File( realPath );
            if ( file.isFile() )
            {
                return file;
            }
        }
        URL resource = jnlpres.getResource();
        if ( resource != null && "file".equals( resource.getProtocol() ) )
        {
            try
            {
                File file = new File( resource.toURI() );
                if ( file.isFile() )
                {
                    return file;
                }
            }
            catch ( URISyntaxException e )
            {
                _log.addDebug( "Invalid file URL for " + jnlpres.getPath() + ": " + e );
            }
        }
        return null;
    }
}


