package org.codehaus.mojo.webstart.dependency.task;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyConfig;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyGlobalConfig;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;
import org.codehaus.mojo.webstart.sign.SignConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

/**
 * Unit tests for {@link UnsignTask} and {@link UpdateManifestTask} validation.
 */
public class DependencyTaskValidationTest
        extends TestCase
{

    public void testUnsignTaskRejectsNullConfig()
    {
        UnsignTask task = new UnsignTask();
        try
        {
            task.check( null );
            fail( "Expected NullPointerException" );
        }
        catch ( NullPointerException e )
        {
            assertTrue( e.getMessage().contains( "config can't be null" ) );
        }
    }

    public void testUpdateManifestTaskRejectsDisabledManifestUpdate()
            throws Exception
    {
        UpdateManifestTask task = new UpdateManifestTask();
        try
        {
            task.check( config( false ) );
            fail( "Expected IllegalStateException" );
        }
        catch ( IllegalStateException e )
        {
            assertTrue( e.getMessage().contains( "config.isUpdateManifest is false" ) );
        }
    }

    public void testUpdateManifestTaskAcceptsEnabledManifestUpdate()
            throws Exception
    {
        UpdateManifestTask task = new UpdateManifestTask();
        task.check( config( true ) );
    }

    private static JnlpDependencyConfig config( boolean updateManifest )
            throws Exception
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ), "DepTaskTest-" + System.nanoTime() );
        assertTrue( workDir.mkdirs() );
        File finalDir = new File( workDir, "final" );
        assertTrue( finalDir.mkdirs() );

        JnlpDependencyGlobalConfig globalConfig =
                new JnlpDependencyGlobalConfig( DependencyTaskValidationTest.class.getClassLoader(),
                                                new SimpleDependencyFilenameStrategy(), workDir, finalDir, null,
                                                new SignConfig(),
                                                updateManifest ? Collections.singletonMap( "Built-By", "test" )
                                                        : Collections.<String, String>emptyMap(), false, false, false,
                                                false );
        Artifact artifact = artifact( workDir );
        return new JnlpDependencyConfig( globalConfig, artifact, "demo", false, false );
    }

    private static Artifact artifact( File parent )
            throws Exception
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        DefaultArtifact artifact =
                new DefaultArtifact( "com.example", "demo", VersionRange.createFromVersion( "1.0" ), "compile", "jar",
                                     null, handler );
        File file = new File( parent, "demo.jar" );
        try ( FileOutputStream out = new FileOutputStream( file ) )
        {
            out.write( new byte[] { 1 } );
        }
        artifact.setFile( file );
        return artifact;
    }
}
