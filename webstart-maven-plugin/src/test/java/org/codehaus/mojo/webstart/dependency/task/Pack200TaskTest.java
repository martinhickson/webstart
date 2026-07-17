package org.codehaus.mojo.webstart.dependency.task;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyConfig;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyGlobalConfig;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;
import org.codehaus.mojo.webstart.pack200.Pack200Config;
import org.codehaus.mojo.webstart.sign.SignConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

/**
 * Unit tests for {@link Pack200Task} and {@link UnPack200Task} validation.
 */
public class Pack200TaskTest
        extends TestCase
{

    public void testPack200TaskRejectsNullConfig()
    {
        Pack200Task task = new Pack200Task();
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

    public void testPack200TaskRejectsDisabledPack200()
            throws Exception
    {
        Pack200Task task = new Pack200Task();
        try
        {
            task.check( config( false ) );
            fail( "Expected IllegalStateException" );
        }
        catch ( IllegalStateException e )
        {
            assertTrue( e.getMessage().contains( "config.isPack200 is false" ) );
        }
    }

    public void testPack200TaskAcceptsEnabledPack200()
            throws Exception
    {
        Pack200Task task = new Pack200Task();
        task.check( config( true ) );
    }

    public void testUnPack200TaskRejectsDisabledPack200()
            throws Exception
    {
        UnPack200Task task = new UnPack200Task();
        try
        {
            task.check( config( false ) );
            fail( "Expected IllegalStateException" );
        }
        catch ( IllegalStateException e )
        {
            assertTrue( e.getMessage().contains( "config.isPack200 is false" ) );
        }
    }

    public void testUnPack200TaskAcceptsEnabledPack200()
            throws Exception
    {
        UnPack200Task task = new UnPack200Task();
        task.check( config( true ) );
    }

    private static JnlpDependencyConfig config( boolean pack200Enabled )
            throws Exception
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ), "Pack200TaskTest-" + System.nanoTime() );
        assertTrue( workDir.mkdirs() );
        File finalDir = new File( workDir, "final" );
        assertTrue( finalDir.mkdirs() );

        Pack200Config pack200 = new Pack200Config();
        pack200.setEnabled( pack200Enabled );

        JnlpDependencyGlobalConfig globalConfig =
                new JnlpDependencyGlobalConfig( Pack200TaskTest.class.getClassLoader(),
                                                new SimpleDependencyFilenameStrategy(), workDir, finalDir, pack200,
                                                new SignConfig(), Collections.<String, String>emptyMap(), false, false,
                                                false, false );
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
