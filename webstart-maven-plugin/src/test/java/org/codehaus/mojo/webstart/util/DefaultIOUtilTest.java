package org.codehaus.mojo.webstart.util;

import junit.framework.TestCase;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Unit tests for {@link DefaultIOUtil}.
 */
public class DefaultIOUtilTest
        extends TestCase
{

    private DefaultIOUtil ioUtil;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();
        ioUtil = new DefaultIOUtil();
        ioUtil.enableLogging( new ConsoleLogger( Logger.LEVEL_DISABLED, "DefaultIOUtilTest" ) );
    }

    public void testShouldCopyWhenTargetMissing()
            throws Exception
    {
        File source = createTempFile( "source-", ".txt", "hello".getBytes( "UTF-8" ) );
        File target = new File( source.getParentFile(), "missing-target.txt" );

        assertTrue( ioUtil.shouldCopyFile( source, target ) );
    }

    public void testShouldNotCopyWhenTargetIsNewer()
            throws Exception
    {
        File source = createTempFile( "source-", ".txt", "hello".getBytes( "UTF-8" ) );
        File target = createTempFile( "target-", ".txt", "world".getBytes( "UTF-8" ) );
        assertTrue( target.setLastModified( source.lastModified() + 60_000L ) );

        assertFalse( ioUtil.shouldCopyFile( source, target ) );
    }

    public void testCopyFileToDirectoryIfNecessaryCopiesNewFile()
            throws Exception
    {
        File source = createTempFile( "source-", ".txt", "payload".getBytes( "UTF-8" ) );
        File targetDirectory = new File( System.getProperty( "java.io.tmpdir" ), "DefaultIOUtilTest-" + System.nanoTime() );
        assertTrue( targetDirectory.mkdirs() );

        assertTrue( ioUtil.copyFileToDirectoryIfNecessary( source, targetDirectory ) );

        File copied = new File( targetDirectory, source.getName() );
        assertTrue( copied.isFile() );
        assertTrue( copied.length() > 0 );
    }

    public void testMakeDirectoryIfNecessaryCreatesMissingDirectory()
            throws Exception
    {
        File directory = new File( System.getProperty( "java.io.tmpdir" ), "DefaultIOUtilTest-" + System.nanoTime() );
        assertFalse( directory.exists() );

        ioUtil.makeDirectoryIfNecessary( directory );

        assertTrue( directory.isDirectory() );
    }

    public void testCopyFileToDirectoryIfNecessaryRejectsNullSource()
            throws Exception
    {
        File targetDirectory = new File( System.getProperty( "java.io.tmpdir" ), "DefaultIOUtilTest-" + System.nanoTime() );
        assertTrue( targetDirectory.mkdirs() );

        try
        {
            ioUtil.copyFileToDirectoryIfNecessary( null, targetDirectory );
            fail( "Expected IllegalArgumentException" );
        }
        catch ( IllegalArgumentException e )
        {
            assertTrue( e.getMessage().contains( "sourceFile is null" ) );
        }
    }

    public void testCopyFileCreatesTargetFile()
            throws Exception
    {
        File source = createTempFile( "source-", ".txt", "copy-me".getBytes( "UTF-8" ) );
        File target = new File( source.getParentFile(), "copy-target-" + System.nanoTime() + ".txt" );

        ioUtil.copyFile( source, target );

        assertTrue( target.isFile() );
        assertEquals( source.length(), target.length() );
    }

    public void testRemoveDirectoryDeletesExistingDirectory()
            throws Exception
    {
        File directory = new File( System.getProperty( "java.io.tmpdir" ), "DefaultIOUtilTest-" + System.nanoTime() );
        assertTrue( directory.mkdirs() );
        File child = new File( directory, "child.txt" );
        writeBytes( child, "x".getBytes( "UTF-8" ) );

        ioUtil.removeDirectory( directory );

        assertFalse( directory.exists() );
    }

    private static File createTempFile( String prefix, String suffix, byte[] bytes )
            throws Exception
    {
        File file = File.createTempFile( prefix, suffix );
        file.deleteOnExit();
        writeBytes( file, bytes );
        return file;
    }

    private static void writeBytes( File file, byte[] bytes )
            throws Exception
    {
        try ( FileOutputStream out = new FileOutputStream( file ) )
        {
            out.write( bytes );
        }
    }
}
