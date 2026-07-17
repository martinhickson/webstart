package org.codehaus.mojo.webstart.dependency.filenaming;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Unit tests for {@link UniqueVersionsHelper}.
 */
public class UniqueVersionsHelperTest
        extends TestCase
{

    public void testReleaseVersionIsUnchanged()
    {
        Artifact artifact = artifact( "demo", "1.2.3", null );
        assertEquals( "1.2.3", UniqueVersionsHelper.getUniqueVersion( artifact ) );
    }

    public void testSnapshotVersionUsesFileTimestampInUtc()
            throws Exception
    {
        File jar = File.createTempFile( "unique-version-", ".jar" );
        jar.deleteOnExit();
        long timestamp = 1_577_836_800_000L; // 2020-01-01 00:00:00 UTC
        assertTrue( jar.setLastModified( timestamp ) );

        Artifact artifact = artifact( "demo", "2.0-SNAPSHOT", jar );
        assertEquals( "2.0-20200101.000000-0", UniqueVersionsHelper.getUniqueVersion( artifact ) );
    }

    public void testSnapshotPrefixIsPreserved()
            throws Exception
    {
        File jar = File.createTempFile( "unique-version-", ".jar" );
        jar.deleteOnExit();
        writeBytes( jar, new byte[] { 1 } );

        SimpleDateFormat df = new SimpleDateFormat( "yyyyMMdd.HHmmss" );
        df.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
        String expectedSuffix = df.format( new Date( jar.lastModified() ) );

        Artifact artifact = artifact( "demo", "1.5-SNAPSHOT", jar );
        assertEquals( "1.5-" + expectedSuffix + "-0", UniqueVersionsHelper.getUniqueVersion( artifact ) );
    }

    private static Artifact artifact( String artifactId, String version, File file )
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        DefaultArtifact artifact =
                new DefaultArtifact( "test.group", artifactId, VersionRange.createFromVersion( version ), "compile", "jar",
                                     null, handler );
        if ( file != null )
        {
            artifact.setFile( file );
        }
        return artifact;
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
