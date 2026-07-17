package org.codehaus.mojo.webstart.pack200;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Unit tests for {@link DefaultPack200Tool}.
 */
public class DefaultPack200ToolTest
        extends TestCase
{

    private DefaultPack200Tool tool;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();
        tool = new DefaultPack200Tool();
    }

    public void testPackCreatesPackFileWithCommonsCompress()
            throws Exception
    {
        File jar = createSampleJar( "pack200-roundtrip-" );
        File packFile = new File( jar.getParentFile(), jar.getName() + DefaultPack200Tool.PACK_EXTENSION );

        tool.pack( jar, packFile, Collections.<String, String>emptyMap(), false, true );
        assertTrue( packFile.isFile() );
        assertTrue( packFile.length() > 0 );
    }

    public void testUnpackRestoresJarEntriesWhenSupported()
            throws Exception
    {
        File jar = createSampleJar( "pack200-unpack-" );
        File packFile = new File( jar.getParentFile(), jar.getName() + DefaultPack200Tool.PACK_EXTENSION );
        File unpackedJar = new File( jar.getParentFile(), jar.getName() + "-unpacked.jar" );

        tool.pack( jar, packFile, Collections.<String, String>emptyMap(), false, true );

        try
        {
            tool.unpack( packFile, unpackedJar, Collections.<String, String>emptyMap(), true );
        }
        catch ( Exception e )
        {
            if ( isCommonsCompressUnpackUnsupported( e ) )
            {
                return;
            }
            throw e;
        }

        assertTrue( unpackedJar.isFile() );
        try ( JarFile unpacked = new JarFile( unpackedJar ) )
        {
            assertNotNull( unpacked.getEntry( "sample.txt" ) );
        }
    }

    public void testPackJarUsesGzipExtensionWhenRequested()
            throws Exception
    {
        File jar = createSampleJar( "pack200-gzip-" );

        File packFile = tool.packJar( jar, true, null, true );

        assertTrue( packFile.getName().endsWith( DefaultPack200Tool.PACK_GZ_EXTENSION ) );
        assertTrue( packFile.isFile() );
    }

    public void testPackJarUsesPlainPackExtensionWhenNotGzipped()
            throws Exception
    {
        File jar = createSampleJar( "pack200-plain-" );

        File packFile = tool.packJar( jar, false, null, true );

        assertTrue( packFile.getName().endsWith( DefaultPack200Tool.PACK_EXTENSION ) );
        assertFalse( packFile.getName().endsWith( DefaultPack200Tool.PACK_GZ_EXTENSION ) );
    }

    public void testRepackProducesJarWithEntries()
            throws Exception
    {
        File source = createSampleJar( "pack200-repack-" );
        File destination = new File( source.getParentFile(), source.getName() + "-repacked.jar" );

        try
        {
            tool.repack( source, destination, Collections.<String, String>emptyMap(), true );
        }
        catch ( Exception e )
        {
            if ( isCommonsCompressUnpackUnsupported( e ) )
            {
                return;
            }
            throw e;
        }

        assertTrue( destination.isFile() );
        try ( JarFile repacked = new JarFile( destination ) )
        {
            assertNotNull( repacked.getEntry( "sample.txt" ) );
        }
    }

    public void testPassFilesAreAppliedDuringPack()
            throws Exception
    {
        Map<String, String> props = createPropertyMap( Arrays.asList( "sample.txt" ) );
        assertEquals( "-1", props.get( Pack200Support.SEGMENT_LIMIT ) );
        assertEquals( "sample.txt", props.get( Pack200Support.PASS_FILE_PFX + "0" ) );
    }

    private static boolean isCommonsCompressUnpackUnsupported( Exception e )
    {
        Throwable current = e;
        while ( current != null )
        {
            if ( "java.lang.reflect.InaccessibleObjectException".equals( current.getClass().getName() ) )
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, String> createPropertyMap( List<String> passFiles )
            throws Exception
    {
        java.lang.reflect.Method method =
                DefaultPack200Tool.class.getDeclaredMethod( "createPropertyMap", List.class );
        method.setAccessible( true );
        @SuppressWarnings( "unchecked" )
        Map<String, String> props = (Map<String, String>) method.invoke( tool, passFiles );
        return props;
    }

    private static File createSampleJar( String prefix )
            throws Exception
    {
        File jar = File.createTempFile( prefix, ".jar" );
        jar.deleteOnExit();
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( jar ) ) )
        {
            JarEntry entry = new JarEntry( "sample.txt" );
            out.putNextEntry( entry );
            out.write( "webstart pack200 test".getBytes( "UTF-8" ) );
            out.closeEntry();
        }
        return jar;
    }
}
