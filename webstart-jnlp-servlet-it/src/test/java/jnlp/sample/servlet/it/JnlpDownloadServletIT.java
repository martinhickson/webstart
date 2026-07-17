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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    private static HttpURLConnection open( String url )
            throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL( url ).openConnection();
        connection.setRequestMethod( "GET" );
        connection.connect();
        return connection;
    }
}
