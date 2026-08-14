package jnlp.sample.servlet.it;

import io.undertow.Undertow;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletInfo;
import jnlp.sample.servlet.JnlpDownloadServlet;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Integration tests for {@link JnlpDownloadServlet} on Undertow with Jakarta Servlet 6.
 */
public class JnlpDownloadServletIT
        extends TestCase
{

    private Undertow server;

    private int port;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();

        ServletInfo servletInfo =
                new ServletInfo( "jnlpDownloadServlet", JnlpDownloadServlet.class ).addMapping( "/*" );

        DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader( getClass().getClassLoader() )
                .setContextPath( "/" )
                .setDeploymentName( "webstart-jnlp-servlet-it" )
                .addServlets( servletInfo )
                .setResourceManager( new ClassPathResourceManager( getClass().getClassLoader(), "webapp" ) );

        DeploymentManager manager = Servlets.defaultContainer().addDeployment( deploymentInfo );
        manager.deploy();

        server = Undertow.builder()
                .addHttpListener( 0, "localhost" )
                .setHandler( manager.start() )
                .build();
        server.start();

        port = ( (InetSocketAddress) server.getListenerInfo().get( 0 ).getAddress() ).getPort();
    }

    @Override
    protected void tearDown()
            throws Exception
    {
        if ( server != null )
        {
            server.stop();
        }
        super.tearDown();
    }

    public void testServesStaticJnlpFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/launch.jnlp" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertTrue( connection.getContentType().contains( "application/x-java-jnlp-file" ) );

            String body;
            try ( BufferedReader reader = new BufferedReader(
                    new InputStreamReader( connection.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                body = reader.lines().collect( Collectors.joining( "\n" ) );
            }

            assertTrue( body.contains( "<jnlp" ) );
            assertTrue( body.contains( "WebStart Servlet IT" ) );
            assertTrue( body.contains( "application-desc" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMissingResourceReturnsNotFound()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/missing.jnlp" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testFullDownloadHasAcceptRangesHeader()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testExplicitRangeReturnsPartialContent()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 10-19/1000", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "10", connection.getHeaderField( "Content-Length" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOpenEndedRangeReturnsTail()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=990-" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 990-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 990, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeReturnsLastBytes()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=-10" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 990-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 990, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUnsatisfiableRangeReturnsRequestedRangeNotSatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=5000-6000" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSingleByteRange()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=0-0" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-0/1000", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "1", connection.getHeaderField( "Content-Length" ) );
            assertContent( connection, 0, 1 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testLastByteRange()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=999-999" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 999-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 999, 1 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeStartingAtEndIsUnsatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=1000-" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMultipleRangesHonoursFirst()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=0-9,20-29" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUnknownRangeUnitIsIgnored()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "items=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testEmptyRangeSpecIsUnsatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnEmptyResourceIsUnsatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/empty.jar", "bytes=0-9" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */0", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnJnlpIsIgnored()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/launch.jnlp", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Range" ) );

            String body;
            try ( BufferedReader reader = new BufferedReader(
                    new InputStreamReader( connection.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                body = reader.lines().collect( Collectors.joining( "\n" ) );
            }

            assertTrue( body.contains( "<jnlp" ) );
            assertTrue( body.contains( "WebStart Servlet IT" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOpenEndedRangeFromStart()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=0-" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testCaseInsensitiveRangeUnit()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "BYTES=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMalformedByteRangeIsRejected()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=abc-def" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testEndBeforeStartIsRejected()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=500-499" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testEndClampedToContentLength()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=990-2000" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 990-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 990, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testFirstUnsatisfiableRangeRejectsRequest()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=1000-,0-9" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeSingleByte()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=-1" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 999-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 999, 1 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOversizedSuffixRangeClampsToWholeContent()
            throws Exception
    {
        HttpURLConnection connection =
                open( "http://localhost:" + port + "/sample.jar", "bytes=-99999999999999999999999" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMatchingIfModifiedSinceReturnsNotModified()
            throws Exception
    {
        String lastModified = getLastModified( "http://localhost:" + port + "/sample.jar" );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "If-Modified-Since", lastModified );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_MODIFIED, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOverridesMatchingIfModifiedSince()
            throws Exception
    {
        String lastModified = getLastModified( "http://localhost:" + port + "/sample.jar" );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Modified-Since", lastModified );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeWithQueryString()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar?x=1&y=two", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 10-19/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnGzipVariant()
            throws Exception
    {
        // 100-byte payload, gzip-compressed to 120 bytes
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.setRequestProperty( "Range", "bytes=10-19" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "gzip", connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "bytes 10-19/120", connection.getHeaderField( "Content-Range" ) );
            assertGzipContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeLargerThanFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=-5000" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeExactlyWholeFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=-1000" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-999/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOpenEndedRangeOnEmptyResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/empty.jar", "bytes=0-" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */0", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnDirectoryDefaultFile()
            throws Exception
    {
        // "/" resolves to launch.jnlp, which is always served in full
        HttpURLConnection connection = open( "http://localhost:" + port + "/", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Range" ) );

            String body;
            try ( BufferedReader reader = new BufferedReader(
                    new InputStreamReader( connection.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                body = reader.lines().collect( Collectors.joining( "\n" ) );
            }
            assertTrue( body.contains( "<jnlp" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnEncodedPath()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/with%20space.jar", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 10-19/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testResumeStyleSequentialRanges()
            throws Exception
    {
        HttpURLConnection first = open( "http://localhost:" + port + "/sample.jar", "bytes=0-499" );
        byte[] head;
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, first.getResponseCode() );
            assertEquals( "bytes 0-499/1000", first.getHeaderField( "Content-Range" ) );
            head = readAll( first.getInputStream() );
        }
        finally
        {
            first.disconnect();
        }

        HttpURLConnection second = open( "http://localhost:" + port + "/sample.jar", "bytes=500-999" );
        byte[] tail;
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, second.getResponseCode() );
            assertEquals( "bytes 500-999/1000", second.getHeaderField( "Content-Range" ) );
            tail = readAll( second.getInputStream() );
        }
        finally
        {
            second.disconnect();
        }

        byte[] reassembled = new byte[1000];
        System.arraycopy( head, 0, reassembled, 0, head.length );
        System.arraycopy( tail, 0, reassembled, head.length, tail.length );

        byte[] expected = new byte[1000];
        for ( int i = 0; i < expected.length; i++ )
        {
            expected[i] = (byte) ( i % 256 );
        }
        assertTrue( Arrays.equals( expected, reassembled ) );
    }

    public void testRangeOnPack200GzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "pack200-gzip" );
        connection.setRequestProperty( "Range", "bytes=10-19" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "pack200-gzip", connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "bytes 10-19/200", connection.getHeaderField( "Content-Range" ) );
            assertSlice( connection, "/webapp/sample.jar.pack.gz", 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnVersionedResource()
            throws Exception
    {
        HttpURLConnection connection = open(
                "http://localhost:" + port + "/sample.jar?version-id=1.0", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "1_0", connection.getHeaderField( "x-java-jnlp-version-id" ) );
            assertEquals( "bytes 10-19/200", connection.getHeaderField( "Content-Range" ) );
            assertSlice( connection, "/webapp/sample__V1_0.jar", 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMixedUnitMultipleRangesUsesBytesFirst()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=0-9, items=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testIfRangeMatchingServesPartial()
            throws Exception
    {
        String lastModified = getLastModified( "http://localhost:" + port + "/sample.jar" );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", lastModified );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testIfRangeNotMatchingServesFullContent()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", "Wed, 01 Jan 2020 00:00:00 GMT" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testMalformedIfRangeIsIgnored()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", "not-a-date" );
        connection.connect();
        try
        {
            // malformed If-Range must be ignored -> the Range is honoured
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testIfRangeWithoutRangeIsIgnored()
            throws Exception
    {
        String lastModified = getLastModified( "http://localhost:" + port + "/sample.jar" );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "If-Range", lastModified );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertContent( connection, 0, 1000 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testDirectRequestToVersionedFileIsBlocked()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample__V1_0.jar" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeOnEmptyResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/empty.jar", "bytes=-1" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */0", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnUnicodeEncodedPath()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/caf%C3%A9.jar", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 10-19/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSingleByteRangeOnEmptyResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/empty.jar", "bytes=0-0" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */0", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUnsatisfiableRangeOnVersionedResource()
            throws Exception
    {
        HttpURLConnection connection = open(
                "http://localhost:" + port + "/sample.jar?version-id=1.0", "bytes=500-600" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */200", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "1_0", connection.getHeaderField( "x-java-jnlp-version-id" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeWithMatchingIfRangeAndIfModifiedSince()
            throws Exception
    {
        String lastModified = getLastModified( "http://localhost:" + port + "/sample.jar" );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", lastModified );
        connection.setRequestProperty( "If-Modified-Since", lastModified );
        connection.connect();
        try
        {
            // Range wins over both matching conditional headers
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testHeadRequestIgnoresRange()
            throws Exception
    {
        HttpURLConnection connection =
                (HttpURLConnection) new URL( "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "1000", connection.getHeaderField( "Content-Length" ) );
            assertNull( connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static String getLastModified( String url )
            throws Exception
    {
        HttpURLConnection connection = open( url );
        try
        {
            String lastModified = connection.getHeaderField( "Last-Modified" );
            assertNotNull( "Missing Last-Modified header", lastModified );
            return lastModified;
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open( String url )
            throws Exception
    {
        return open( url, null );
    }

    private static HttpURLConnection open( String url, String range )
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL( url ).openConnection();
        connection.setRequestMethod( "GET" );
        if ( range != null )
        {
            connection.setRequestProperty( "Range", range );
        }
        connection.connect();
        return connection;
    }

    /**
     * Reads the response body and asserts that it equals the expected slice of
     * the 1000-byte sample resource (byte value = index % 256).
     */
    private static void assertContent( HttpURLConnection connection, int start, int length )
            throws IOException
    {
        byte[] actual = readAll( connection.getInputStream() );
        byte[] expected = new byte[length];
        for ( int i = 0; i < length; i++ )
        {
            expected[i] = (byte) ( ( start + i ) % 256 );
        }
        assertEquals( length, actual.length );
        assertTrue( "Unexpected body", Arrays.equals( expected, actual ) );
    }

    /**
     * Reads the raw (compressed) response body and asserts it equals the
     * expected slice of the 120-byte gzip resource.
     */
    private static void assertGzipContent( HttpURLConnection connection, int start, int length )
            throws IOException
    {
        assertSlice( connection, "/webapp/sample.jar.gz", start, length );
    }

    /**
     * Reads the response body and asserts it equals the given slice of a
     * classpath test resource.
     */
    private static void assertSlice( HttpURLConnection connection, String resourcePath, int start, int length )
            throws IOException
    {
        byte[] actual = readAll( connection.getInputStream() );
        byte[] full;
        try ( InputStream in = JnlpDownloadServletIT.class.getResourceAsStream( resourcePath ) )
        {
            assertNotNull( "Missing test resource: " + resourcePath, in );
            full = readAll( in );
        }
        assertEquals( length, actual.length );
        byte[] expected = Arrays.copyOfRange( full, start, start + length );
        assertTrue( "Unexpected body", Arrays.equals( expected, actual ) );
    }

    private static byte[] readAll( InputStream in )
            throws IOException
    {
        byte[] buffer = new byte[8192];
        int offset = 0;
        int read;
        while ( ( read = in.read( buffer, offset, buffer.length - offset ) ) != -1 )
        {
            offset += read;
            if ( offset == buffer.length )
            {
                buffer = Arrays.copyOf( buffer, buffer.length * 2 );
            }
        }
        return Arrays.copyOf( buffer, offset );
    }
}
