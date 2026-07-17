package org.codehaus.mojo.webstart.util;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link BuildReport}.
 */
public class BuildReportTest
        extends TestCase
{

    public void testJdkEnvironmentLinesIncludeSysprops()
            throws Exception
    {
        List<String> lines = invokeJdkEnvironmentLines();
        assertTrue( lines.get( 0 ).startsWith( "java.version: " ) );
        assertTrue( lines.get( 1 ).startsWith( "java.vendor: " ) );
        assertTrue( lines.get( 2 ).startsWith( "java.vm.name: " ) );
        assertFalse( lines.get( 0 ).endsWith( "unknown" ) );
    }

    public void testWorkDirectorySummaryListsJnlpAndSignedJar()
            throws Exception
    {
        File workDir = createTempDir( "webstart-report-" );
        File jnlp = new File( workDir, "launch.jnlp" );
        writeBytes( jnlp, "<jnlp/>".getBytes( "UTF-8" ) );

        File jar = new File( workDir, "demo.jar" );
        writeSignedJar( jar );

        List<String> lines = BuildReport.workDirectorySummaryLines( workDir );
        assertTrue( containsLine( lines, "Build summary" ) );
        assertTrue( containsLine( lines, "launch.jnlp" ) );
        assertTrue( containsLine( lines, "demo.jar" ) );
        assertTrue( containsLine( lines, "signature: unsigned" ) || containsLine( lines, "SHA-256 fingerprint:" ) );
    }

    public void testWorkDirectorySummaryReturnsEmptyForNullDirectory()
    {
        assertTrue( BuildReport.workDirectorySummaryLines( null ).isEmpty() );
    }

    public void testWorkDirectorySummaryHandlesEmptyDirectory()
            throws Exception
    {
        File workDir = createTempDir( "webstart-report-empty-" );

        List<String> lines = BuildReport.workDirectorySummaryLines( workDir );
        assertTrue( containsLine( lines, "Build summary" ) );
        assertTrue( containsLine( lines, "Signed JAR artifacts: (none)" ) );
        assertFalse( containsLine( lines, "JNLP descriptors:" ) );
    }

    public void testSignedJarLinesReportNoneWhenDirectoryHasNoJars()
            throws Exception
    {
        File workDir = createTempDir( "webstart-report-nojars-" );
        writeBytes( new File( workDir, "launch.jnlp" ), "<jnlp/>".getBytes( "UTF-8" ) );

        List<String> lines = BuildReport.signedJarLines( workDir );
        assertEquals( 1, lines.size() );
        assertEquals( "Signed JAR artifacts: (none)", lines.get( 0 ) );
    }

    public void testDistributionArchiveLinesReturnEmptyForMissingFile()
    {
        File missing = new File( System.getProperty( "java.io.tmpdir" ), "webstart-report-missing-" + System.nanoTime() );
        assertTrue( BuildReport.distributionArchiveLines( missing ).isEmpty() );
    }

    public void testDistributionArchiveLinesListZipEntries()
            throws Exception
    {
        File zip = File.createTempFile( "webstart-report-", ".zip" );
        zip.deleteOnExit();
        try ( ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zip ) ) )
        {
            out.putNextEntry( new ZipEntry( "launch.jnlp" ) );
            out.write( "<jnlp/>".getBytes( "UTF-8" ) );
            out.closeEntry();
            out.putNextEntry( new ZipEntry( "lib/demo.jar" ) );
            out.write( new byte[] { 1, 2, 3 } );
            out.closeEntry();
        }

        List<String> lines = BuildReport.distributionArchiveLines( zip );
        assertTrue( containsLine( lines, "Distribution archive:" ) );
        assertTrue( containsLine( lines, "launch.jnlp" ) );
        assertTrue( containsLine( lines, "lib/demo.jar" ) );
        assertTrue( containsLine( lines, "java.version:" ) );
    }

    @SuppressWarnings( "unchecked" )
    private static List<String> invokeJdkEnvironmentLines()
            throws Exception
    {
        java.lang.reflect.Method method = BuildReport.class.getDeclaredMethod( "jdkEnvironmentLines" );
        method.setAccessible( true );
        return (List<String>) method.invoke( null );
    }

    private static boolean containsLine( List<String> lines, String fragment )
    {
        for ( String line : lines )
        {
            if ( line.contains( fragment ) )
            {
                return true;
            }
        }
        return false;
    }

    private static File createTempDir( String prefix )
    {
        File dir = new File( System.getProperty( "java.io.tmpdir" ), prefix + System.nanoTime() );
        assertTrue( dir.mkdirs() );
        return dir;
    }

    private static void writeBytes( File file, byte[] bytes )
            throws Exception
    {
        try ( FileOutputStream out = new FileOutputStream( file ) )
        {
            out.write( bytes );
        }
    }

    private static void writeSignedJar( File jar )
            throws Exception
    {
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( jar ) ) )
        {
            JarEntry entry = new JarEntry( "META-INF/MANIFEST.MF" );
            out.putNextEntry( entry );
            out.write( "Manifest-Version: 1.0\nCreated-By: webstart-it\n".getBytes( "UTF-8" ) );
            out.closeEntry();
        }
    }
}
