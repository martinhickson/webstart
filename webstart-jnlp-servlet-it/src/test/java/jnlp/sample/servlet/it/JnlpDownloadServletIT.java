package jnlp.sample.servlet.it;

import io.undertow.Undertow;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletInfo;
import jnlp.sample.servlet.JnlpDownloadServlet;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Integration tests for {@link JnlpDownloadServlet} on Undertow with Jakarta Servlet 6.
 */
public class JnlpDownloadServletIT
        extends TestCase
{

    /**
     * 3 GiB, chosen to exceed Integer.MAX_VALUE so any int-based content
     * length handling would overflow.
     */
    private static final long HUGE_SIZE = 3L * 1024 * 1024 * 1024;

    private Undertow server;

    private int port;

    private File webRoot;

    /**
     * A second server that serves resources from inside a JAR (the packaged
     * servlet jar), where {@code getRealPath} returns {@code null}. Exercises
     * the streamed (non-NIO) range fallback.
     */
    private Undertow urlServer;

    private int urlPort;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();

        webRoot = Files.createTempDirectory( "webstart-it" ).toFile();
        copyWebappResources( webRoot );

        // sparse multi-GiB file (no physical disk usage) to exercise the
        // long content-length and >2 GiB NIO transfer paths
        try ( RandomAccessFile raf = new RandomAccessFile( new File( webRoot, "huge.bin" ), "rw" ) )
        {
            raf.setLength( HUGE_SIZE );
        }

        ServletInfo servletInfo =
                new ServletInfo( "jnlpDownloadServlet", JnlpDownloadServlet.class ).addMapping( "/*" );

        DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader( getClass().getClassLoader() )
                .setContextPath( "/" )
                .setDeploymentName( "webstart-jnlp-servlet-it" )
                .addServlets( servletInfo )
                .setResourceManager( new FileResourceManager( webRoot ) );

        DeploymentManager manager = Servlets.defaultContainer().addDeployment( deploymentInfo );
        manager.deploy();

        server = Undertow.builder()
                .addHttpListener( 0, "localhost" )
                .setHandler( manager.start() )
                .build();
        server.start();

        port = ( (InetSocketAddress) server.getListenerInfo().get( 0 ).getAddress() ).getPort();

        // URL-fallback server: resources are read from inside the packaged
        // servlet jar so context.getRealPath returns null
        ServletInfo urlServletInfo =
                new ServletInfo( "jnlpDownloadServlet", JnlpDownloadServlet.class ).addMapping( "/*" );
        DeploymentInfo urlDeploymentInfo = Servlets.deployment()
                .setClassLoader( getClass().getClassLoader() )
                .setContextPath( "/" )
                .setDeploymentName( "webstart-url-fallback-it" )
                .addServlets( urlServletInfo )
                .setResourceManager(
                        new ClassPathResourceManager( getClass().getClassLoader(), "jnlp/sample/servlet/resources" ) );
        DeploymentManager urlManager = Servlets.newContainer().addDeployment( urlDeploymentInfo );
        urlManager.deploy();

        urlServer = Undertow.builder()
                .addHttpListener( 0, "localhost" )
                .setHandler( urlManager.start() )
                .build();
        urlServer.start();

        urlPort = ( (InetSocketAddress) urlServer.getListenerInfo().get( 0 ).getAddress() ).getPort();
    }

    @Override
    protected void tearDown()
            throws Exception
    {
        if ( urlServer != null )
        {
            urlServer.stop();
        }
        if ( server != null )
        {
            server.stop();
        }
        if ( webRoot != null )
        {
            deleteRecursively( webRoot );
        }
        super.tearDown();
    }

    /**
     * Copies the webapp test resources from the classpath into the temp web
     * root so the tests can also add or mutate files at runtime.
     */
    private static void copyWebappResources( File target )
            throws Exception
    {
        File srcDir = new File( JnlpDownloadServletIT.class.getResource( "/webapp" ).toURI() );
        File[] files = srcDir.listFiles();
        assertNotNull( "Empty webapp test resources", files );
        for ( File file : files )
        {
            if ( file.isFile() )
            {
                Files.copy( file.toPath(), new File( target, file.getName() ).toPath(),
                            StandardCopyOption.REPLACE_EXISTING );
            }
        }
    }

    private static void deleteRecursively( File dir )
    {
        File[] files = dir.listFiles();
        if ( files != null )
        {
            for ( File file : files )
            {
                if ( file.isDirectory() )
                {
                    deleteRecursively( file );
                }
                else
                {
                    file.delete();
                }
            }
        }
        dir.delete();
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

    public void testHeadOnMultiGiBFileReportsLongContentLength()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/huge.bin" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            // 3221225472 exceeds Integer.MAX_VALUE - must not be truncated
            assertEquals( "3221225472", connection.getHeaderField( "Content-Length" ) );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeBeyondTwoGiBOnMultiGiBFile()
            throws Exception
    {
        long start = 2L * 1024 * 1024 * 1024;
        long end = start + 9;
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin",
                                             "bytes=" + start + "-" + end );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes " + start + "-" + end + "/3221225472", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "10", connection.getHeaderField( "Content-Length" ) );
            assertZeroContent( connection, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeLastByteOnMultiGiBFile()
            throws Exception
    {
        long start = HUGE_SIZE - 1;
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin", "bytes=" + start + "-" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes " + start + "-" + start + "/3221225472",
                          connection.getHeaderField( "Content-Range" ) );
            assertEquals( "1", connection.getHeaderField( "Content-Length" ) );
            assertZeroContent( connection, 1 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnMissingResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/missing.jar", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUnsatisfiableRangeOnGzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.setRequestProperty( "Range", "bytes=500-600" );
        connection.connect();
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */120", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testSuffixRangeOnGzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.setRequestProperty( "Range", "bytes=-500" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-119/120", connection.getHeaderField( "Content-Range" ) );
            assertGzipContent( connection, 0, 120 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testStaleIfRangeAfterResourceChangeServesFullContent()
            throws Exception
    {
        File file = new File( webRoot, "mutate.jar" );
        byte[] original = new byte[1000];
        for ( int i = 0; i < original.length; i++ )
        {
            original[i] = (byte) ( i % 256 );
        }
        Files.write( file.toPath(), original );
        long originalMtime = file.lastModified();

        // The resource is replaced on disk (as a redeploy would do); give it a
        // future mtime so the second-resolution comparison definitely differs.
        byte[] replacement = new byte[500];
        Arrays.fill( replacement, (byte) 0xAB );
        Files.write( file.toPath(), replacement );
        file.setLastModified( originalMtime + 5000 );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/mutate.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", httpDate( originalMtime ) );
        connection.connect();
        try
        {
            // stale If-Range -> the Range header must be ignored and the new
            // full representation served
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Range" ) );
            byte[] body = readAll( connection.getInputStream() );
            assertEquals( replacement.length, body.length );
            for ( byte value : body )
            {
                assertEquals( (byte) 0xAB, value );
            }
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testConcurrentPartialDownloads()
            throws Exception
    {
        int chunks = 8;
        int chunkSize = 100;
        ExecutorService pool = Executors.newFixedThreadPool( chunks );
        try
        {
            List<Future<byte[]>> futures = new ArrayList<>();
            for ( int i = 0; i < chunks; i++ )
            {
                final int start = i * chunkSize;
                futures.add( pool.submit( () -> {
                    HttpURLConnection connection = open(
                            "http://localhost:" + port + "/sample.jar",
                            "bytes=" + start + "-" + ( start + chunkSize - 1 ) );
                    try
                    {
                        assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
                        assertEquals( "bytes " + start + "-" + ( start + chunkSize - 1 ) + "/1000",
                                      connection.getHeaderField( "Content-Range" ) );
                        return readAll( connection.getInputStream() );
                    }
                    finally
                    {
                        connection.disconnect();
                    }
                } ) );
            }

            byte[] reassembled = new byte[chunks * chunkSize];
            for ( int i = 0; i < chunks; i++ )
            {
                byte[] part = futures.get( i ).get( 30, TimeUnit.SECONDS );
                assertEquals( chunkSize, part.length );
                System.arraycopy( part, 0, reassembled, i * chunkSize, chunkSize );
            }

            byte[] expected = new byte[reassembled.length];
            for ( int i = 0; i < expected.length; i++ )
            {
                expected[i] = (byte) ( i % 256 );
            }
            assertTrue( Arrays.equals( expected, reassembled ) );
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    public void testLastModifiedPresentOnPartialContent()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertNotNull( connection.getHeaderField( "Last-Modified" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRepeatedRangeHeaderHonoursFirst()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.addRequestProperty( "Range", "bytes=0-9" );
        connection.addRequestProperty( "Range", "bytes=10-19" );
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

    public void testZeroByteResourceFullDownload()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/empty.jar" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "0", connection.getHeaderField( "Content-Length" ) );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
            assertEquals( 0, readAll( connection.getInputStream() ).length );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testAcceptRangesOnUnsatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=5000-6000" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
            assertEquals( "bytes */1000", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testFullDownloadAfterPartialRange()
            throws Exception
    {
        HttpURLConnection partial = open( "http://localhost:" + port + "/sample.jar", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, partial.getResponseCode() );
            assertContent( partial, 0, 10 );
        }
        finally
        {
            partial.disconnect();
        }

        HttpURLConnection full = open( "http://localhost:" + port + "/sample.jar" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, full.getResponseCode() );
            assertEquals( "1000", full.getHeaderField( "Content-Length" ) );
            assertContent( full, 0, 1000 );
        }
        finally
        {
            full.disconnect();
        }
    }

    public void testHeadOnGzipVariantReportsGzipHeaders()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "120", connection.getHeaderField( "Content-Length" ) );
            assertEquals( "gzip", connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testHeadOnPack200VariantReportsPack200Headers()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.setRequestProperty( "Accept-Encoding", "pack200-gzip" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "200", connection.getHeaderField( "Content-Length" ) );
            assertEquals( "pack200-gzip", connection.getHeaderField( "Content-Encoding" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testHeadWithoutEncodingReportsPlainLength()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "1000", connection.getHeaderField( "Content-Length" ) );
            assertNull( connection.getHeaderField( "Content-Encoding" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testLargePartialTransferOnMultiGiBFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin", "bytes=0-1048575" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-1048575/3221225472", connection.getHeaderField( "Content-Range" ) );
            assertEquals( "1048576", connection.getHeaderField( "Content-Length" ) );
            assertZeroContent( connection, 1024 * 1024 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testFutureIfRangeServesPartial()
            throws Exception
    {
        String future = httpDate( System.currentTimeMillis() + 10000 );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", future );
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

    public void testGzipAcceptEncodingOnFileWithoutGzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/with%20space.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.setRequestProperty( "Range", "bytes=10-19" );
        connection.connect();
        try
        {
            // no with space.jar.gz exists -> the plain file is served
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertNull( connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "bytes 10-19/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testPack200AcceptEncodingOnJnlpStillServesFullJnlp()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/launch.jnlp" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "pack200-gzip" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.connect();
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

    public void testSuffixRangeOnMultiGiBFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin", "bytes=-10" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 3221225462-3221225471/3221225472", connection.getHeaderField( "Content-Range" ) );
            assertZeroContent( connection, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testEndClampedOnMultiGiBFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin", "bytes=3221225470-5000000000" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 3221225470-3221225471/3221225472", connection.getHeaderField( "Content-Range" ) );
            assertZeroContent( connection, 2 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeStartOnMultiGiBFile()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/huge.bin", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/3221225472", connection.getHeaderField( "Content-Range" ) );
            assertZeroContent( connection, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUrlFallbackRangeOnJarResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + urlPort + "/strings.properties", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            byte[] full = readClasspathResource( "/jnlp/sample/servlet/resources/strings.properties" );
            assertEquals( "bytes 0-9/" + full.length, connection.getHeaderField( "Content-Range" ) );
            byte[] body = readAll( connection.getInputStream() );
            assertTrue( Arrays.equals( Arrays.copyOfRange( full, 0, 10 ), body ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUrlFallbackUnsatisfiableRange()
            throws Exception
    {
        byte[] full = readClasspathResource( "/jnlp/sample/servlet/resources/strings.properties" );
        HttpURLConnection connection =
                open( "http://localhost:" + urlPort + "/strings.properties", "bytes=5000-6000" );
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */" + full.length, connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testUrlFallbackFullDownload()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + urlPort + "/strings.properties" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "bytes", connection.getHeaderField( "Accept-Ranges" ) );
            byte[] body = readAll( connection.getInputStream() );
            byte[] full = readClasspathResource( "/jnlp/sample/servlet/resources/strings.properties" );
            assertTrue( Arrays.equals( full, body ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeOnPathWithLiteralPlus()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/plus+minus.jar", "bytes=10-19" );
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

    public void testRangeOnPathWithEncodedPlus()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/plus%2Bminus.jar", "bytes=10-19" );
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

    public void testRangeOnVersionedGzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar?version-id=1.0" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.setRequestProperty( "Range", "bytes=10-19" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "gzip", connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "1_0", connection.getHeaderField( "x-java-jnlp-version-id" ) );
            assertEquals( "bytes 10-19/223", connection.getHeaderField( "Content-Range" ) );
            assertSlice( connection, "/webapp/sample__V1_0.jar.gz", 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeWithBothGzipAndPack200EncodingsPrefersPack200()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "pack200-gzip, gzip" );
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

    public void testJardiffMissingOldVersionFallsBackToRange()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar?current-version-id=0.5" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "User-Agent", "javaws/1.6.0" );
        connection.setRequestProperty( "Range", "bytes=10-19" );
        connection.connect();
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

    public void testJardiffWithoutUserAgentServesFile()
            throws Exception
    {
        HttpURLConnection connection = open(
                "http://localhost:" + port + "/sample.jar?current-version-id=0.5", "bytes=10-19" );
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

    public void testHeadWithGzipOnFileWithoutVariantReportsPlain()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/with%20space.jar" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "1000", connection.getHeaderField( "Content-Length" ) );
            assertNull( connection.getHeaderField( "Content-Encoding" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testZeroSuffixRangeIsUnsatisfiable()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes=-0" );
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

    public void testUnsatisfiableRangeOnPack200Variant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "pack200-gzip" );
        connection.setRequestProperty( "Range", "bytes=500-600" );
        connection.connect();
        try
        {
            assertEquals( 416, connection.getResponseCode() );
            assertEquals( "bytes */200", connection.getHeaderField( "Content-Range" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testFullRangeOnVersionedResource()
            throws Exception
    {
        HttpURLConnection connection = open(
                "http://localhost:" + port + "/sample.jar?version-id=1.0", "bytes=0-199" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-199/200", connection.getHeaderField( "Content-Range" ) );
            assertSlice( connection, "/webapp/sample__V1_0.jar", 0, 200 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testPathTraversalRejected()
            throws Exception
    {
        String[] paths = { "/../etc/passwd", "/..%2F..%2F..%2Fetc%2Fpasswd", "/..%2F..%2Fempty.jar",
                           "/.%2e/%2e%2e/etc/passwd" };
        for ( String path : paths )
        {
            HttpURLConnection connection = open( "http://localhost:" + port + path );
            try
            {
                assertNotServed( "path " + path, connection );
            }
            finally
            {
                connection.disconnect();
            }
        }
    }

    public void testPathTraversalWithRangeRejected()
            throws Exception
    {
        String[] paths = { "/../etc/passwd", "/..%2F..%2F..%2Fetc%2Fpasswd" };
        for ( String path : paths )
        {
            HttpURLConnection connection = open( "http://localhost:" + port + path, "bytes=0-9" );
            try
            {
                assertNotServed( "path " + path, connection );
            }
            finally
            {
                connection.disconnect();
            }
        }
    }

    public void testEtagFormIfRangeIsIgnored()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Range", "\"deadbeef\"" );
        connection.connect();
        try
        {
            // the servlet only understands date-based If-Range; an
            // entity-tag value is treated as absent (RFC 7233 section 3.2)
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 0-9/1000", connection.getHeaderField( "Content-Range" ) );
            assertContent( connection, 0, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testCaseInsensitiveAcceptEncodingValue()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Accept-Encoding", "GZIP" );
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

    public void testFutureIfModifiedSinceReturnsNotModified()
            throws Exception
    {
        String future = httpDate( System.currentTimeMillis() + 60000 );

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "If-Modified-Since", future );
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

    public void testOldIfModifiedSinceWithRangeServesPartial()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar" ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.setRequestProperty( "Range", "bytes=0-9" );
        connection.setRequestProperty( "If-Modified-Since", "Wed, 01 Jan 2020 00:00:00 GMT" );
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

    public void testRangeOnOsArchVersionedResource()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port +
                "/archjar.jar?version-id=1.0&os=linux&arch=amd64", "bytes=10-19" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_PARTIAL, connection.getResponseCode() );
            assertEquals( "bytes 10-19/300", connection.getHeaderField( "Content-Range" ) );
            assertSlice( connection, "/webapp/archjar__V1_0__Olinux__Aamd64.jar", 10, 10 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOsArchVersionedResourceFullDownload()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port +
                "/archjar.jar?version-id=1.0&os=linux&arch=amd64" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "300", connection.getHeaderField( "Content-Length" ) );
            assertSlice( connection, "/webapp/archjar__V1_0__Olinux__Aamd64.jar", 0, 300 );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testOsArchVersionedResourceWithoutOsNoMatch()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/archjar.jar?version-id=1.0" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertTrue( connection.getContentType().contains( "x-java-jnlp-error" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testHeadOnVersionedGzipVariant()
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/sample.jar?version-id=1.0" ).openConnection();
        connection.setRequestMethod( "HEAD" );
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection.connect();
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertEquals( "223", connection.getHeaderField( "Content-Length" ) );
            assertEquals( "gzip", connection.getHeaderField( "Content-Encoding" ) );
            assertEquals( "1_0", connection.getHeaderField( "x-java-jnlp-version-id" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testPlatformVersionIdReturnsJnlpError()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar?platform-version-id=1.0" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_OK, connection.getResponseCode() );
            assertTrue( connection.getContentType().contains( "x-java-jnlp-error" ) );

            String body;
            try ( BufferedReader reader = new BufferedReader(
                    new InputStreamReader( connection.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                body = reader.lines().collect( Collectors.joining( "\n" ) );
            }
            assertTrue( body.startsWith( "10" ) );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testVersionXmlRequestIsBlocked()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/version.xml", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testTrailingSlashOnFileReturnsNotFound()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar/", "bytes=0-9" );
        try
        {
            assertEquals( HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode() );
        }
        finally
        {
            connection.disconnect();
        }
    }

    public void testRangeHeaderWithoutEqualsIsIgnored()
            throws Exception
    {
        HttpURLConnection connection = open( "http://localhost:" + port + "/sample.jar", "bytes 0-9" );
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

    private static String httpDate( long millis )
    {
        return Instant.ofEpochMilli( millis )
                .atZone( ZoneOffset.UTC )
                .format( DateTimeFormatter.RFC_1123_DATE_TIME );
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

    /**
     * Reads the response body and asserts it is all zero bytes of the given
     * length (the multi-GiB test resource is a sparse file).
     */
    private static void assertZeroContent( HttpURLConnection connection, int length )
            throws IOException
    {
        byte[] actual = readAll( connection.getInputStream() );
        assertEquals( length, actual.length );
        for ( byte value : actual )
        {
            assertEquals( 0, value );
        }
    }

    /**
     * Asserts that a request did not serve a 2xx response and that no
     * sensitive content leaked into any error body.
     */
    private static void assertNotServed( String message, HttpURLConnection connection )
            throws IOException
    {
        int code = connection.getResponseCode();
        assertTrue( message + " returned " + code, code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL );
        InputStream errorStream = connection.getErrorStream();
        if ( errorStream != null )
        {
            byte[] body = readAll( errorStream );
            assertFalse( message + " leaked content",
                         new String( body, StandardCharsets.UTF_8 ).contains( "/bin/bash" ) );
        }
    }

    private static byte[] readClasspathResource( String resourcePath )
            throws IOException
    {
        try ( InputStream in = JnlpDownloadServletIT.class.getResourceAsStream( resourcePath ) )
        {
            assertNotNull( "Missing classpath resource: " + resourcePath, in );
            return readAll( in );
        }
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
