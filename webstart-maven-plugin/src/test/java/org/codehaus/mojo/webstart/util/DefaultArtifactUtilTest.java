package org.codehaus.mojo.webstart.util;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Unit tests for {@link DefaultArtifactUtil}.
 */
public class DefaultArtifactUtilTest
        extends TestCase
{

    private DefaultArtifactUtil artifactUtil;

    @Override
    protected void setUp()
            throws Exception
    {
        super.setUp();
        artifactUtil = new DefaultArtifactUtil();
        artifactUtil.enableLogging( new ConsoleLogger( Logger.LEVEL_DISABLED, "DefaultArtifactUtilTest" ) );
    }

    public void testResolveFromReactorFindsMatchingProject()
            throws Exception
    {
        Artifact artifact = artifact( "com.example", "demo", "1.0", null );
        MavenProject match = project( "com.example", "demo" );
        MavenProject other = project( "com.example", "other" );

        MavenProject resolved =
                artifactUtil.resolveFromReactor( artifact, null, java.util.Arrays.asList( other, match ) );
        assertSame( match, resolved );
    }

    public void testResolveFromReactorReturnsNullWhenNotFound()
            throws Exception
    {
        Artifact artifact = artifact( "com.example", "missing", "1.0", null );
        MavenProject resolved =
                artifactUtil.resolveFromReactor( artifact, null, Collections.singletonList( project( "com.example", "demo" ) ) );
        assertNull( resolved );
    }

    public void testArtifactContainsClassWhenPresentInJar()
            throws Exception
    {
        File jar = jarContainingClass( "org/codehaus/mojo/webstart/pack200/Pack200Config.class",
                                       "/org/codehaus/mojo/webstart/pack200/Pack200Config.class" );
        Artifact artifact = artifact( "com.example", "demo", "1.0", null );
        artifact.setFile( jar );

        assertTrue( artifactUtil.artifactContainsClass( artifact, "org.codehaus.mojo.webstart.pack200.Pack200Config" ) );
    }

    public void testArtifactContainsClassWhenMissingFromJar()
            throws Exception
    {
        File jar = jarContainingClass( "org/codehaus/mojo/webstart/pack200/Pack200Config.class",
                                       "/org/codehaus/mojo/webstart/pack200/Pack200Config.class" );
        Artifact artifact = artifact( "com.example", "demo", "1.0", null );
        artifact.setFile( jar );

        assertFalse( artifactUtil.artifactContainsClass( artifact, "com.example.DoesNotExist" ) );
    }

    private static MavenProject project( String groupId, String artifactId )
    {
        MavenProject project = new MavenProject();
        project.setGroupId( groupId );
        project.setArtifactId( artifactId );
        return project;
    }

    private static Artifact artifact( String groupId, String artifactId, String version, String classifier )
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        return new DefaultArtifact( groupId, artifactId, VersionRange.createFromVersion( version ), "compile", "jar",
                                    classifier, handler );
    }

    private static File jarContainingClass( String entryName, String resourcePath )
            throws Exception
    {
        File jar = File.createTempFile( "DefaultArtifactUtilTest-", ".jar" );
        jar.deleteOnExit();
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( jar ) ) )
        {
            JarEntry entry = new JarEntry( entryName );
            out.putNextEntry( entry );
            InputStream input = DefaultArtifactUtilTest.class.getResourceAsStream( resourcePath );
            assertNotNull( "Missing test resource " + resourcePath, input );
            byte[] buffer = new byte[4096];
            int read;
            while ( ( read = input.read( buffer ) ) >= 0 )
            {
                out.write( buffer, 0, read );
            }
            input.close();
            out.closeEntry();
        }
        return jar;
    }
}
