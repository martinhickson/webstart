package org.codehaus.mojo.webstart.util;

import junit.framework.TestCase;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Unit tests for {@link DefaultJarUtil}.
 */
public class DefaultJarUtilTest
        extends TestCase
{

    private DefaultJarUtil jarUtil;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();
        jarUtil = new DefaultJarUtil();
        DefaultIOUtil ioUtil = new DefaultIOUtil();
        ioUtil.enableLogging( new ConsoleLogger( Logger.LEVEL_DISABLED, "DefaultJarUtilTest" ) );
        setField( jarUtil, "ioUtil", ioUtil );
    }

    public void testUpdateManifestEntriesAddsCustomAttributes()
            throws Exception
    {
        File jar = createJarWithManifest( "Initial-Value" );
        Map<String, String> entries = new HashMap<>();
        entries.put( "Application-Name", "WebStart Demo" );
        entries.put( "Trusted-Only", "true" );

        jarUtil.updateManifestEntries( jar, entries );

        try ( JarFile updated = new JarFile( jar ) )
        {
            Attributes attributes = updated.getManifest().getMainAttributes();
            assertEquals( "WebStart Demo", attributes.getValue( "Application-Name" ) );
            assertEquals( "true", attributes.getValue( "Trusted-Only" ) );
            assertEquals( "Initial-Value", attributes.getValue( "X-Existing" ) );
        }
    }

    public void testUpdateManifestEntriesCreatesManifestWhenMissing()
            throws Exception
    {
        File jar = createEmptyJar();
        Map<String, String> entries = new HashMap<>();
        entries.put( "Created-By", "webstart-maven-plugin-test" );

        jarUtil.updateManifestEntries( jar, entries );

        try ( JarFile updated = new JarFile( jar ) )
        {
            Manifest manifest = updated.getManifest();
            assertNotNull( manifest );
            assertEquals( "webstart-maven-plugin-test", manifest.getMainAttributes().getValue( "Created-By" ) );
            assertEquals( "1.0", manifest.getMainAttributes().getValue( Attributes.Name.MANIFEST_VERSION ) );
        }
    }

    private static File createJarWithManifest( String existingValue )
            throws Exception
    {
        File jar = File.createTempFile( "DefaultJarUtilTest-", ".jar" );
        jar.deleteOnExit();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue( Attributes.Name.MANIFEST_VERSION.toString(), "1.0" );
        manifest.getMainAttributes().putValue( "X-Existing", existingValue );

        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( jar ), manifest ) )
        {
            out.putNextEntry( new java.util.jar.JarEntry( "data.txt" ) );
            out.write( "payload".getBytes( "UTF-8" ) );
            out.closeEntry();
        }
        return jar;
    }

    private static File createEmptyJar()
            throws Exception
    {
        File jar = File.createTempFile( "DefaultJarUtilTest-empty-", ".jar" );
        jar.deleteOnExit();
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( jar ) ) )
        {
            out.putNextEntry( new java.util.jar.JarEntry( "data.txt" ) );
            out.write( "payload".getBytes( "UTF-8" ) );
            out.closeEntry();
        }
        return jar;
    }

    private static void setField( Object target, String name, Object value )
            throws Exception
    {
        java.lang.reflect.Field field = target.getClass().getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
