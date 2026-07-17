package org.codehaus.mojo.webstart.dependency.task;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyConfig;
import org.codehaus.mojo.webstart.dependency.JnlpDependencyGlobalConfig;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;
import org.codehaus.mojo.webstart.sign.SignConfig;
import org.codehaus.mojo.webstart.sign.SignTool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

/**
 * Unit tests for {@link SignTask} validation.
 */
public class SignTaskTest
        extends TestCase
{

    public void testRejectsNullConfig()
    {
        SignTask task = new SignTask();
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

    public void testRejectsWhenSigningDisabled()
            throws Exception
    {
        SignTask task = new SignTask();
        try
        {
            task.check( config( false, false, false ) );
            fail( "Expected IllegalStateException" );
        }
        catch ( IllegalStateException e )
        {
            assertTrue( e.getMessage().contains( "config.isSign is false" ) );
        }
    }

    public void testRejectsSignedJarWhenUnsignNotAllowed()
            throws Exception
    {
        SignTask task = new SignTask();
        injectSignTool( task, signedJarTool( true ) );
        try
        {
            task.check( config( true, false, false ) );
            fail( "Expected IllegalStateException" );
        }
        catch ( IllegalStateException e )
        {
            assertTrue( e.getMessage().contains( "Can't unsign" ) );
        }
    }

    public void testAcceptsUnsignedJarWhenSigningEnabled()
            throws Exception
    {
        SignTask task = new SignTask();
        injectSignTool( task, signedJarTool( false ) );
        task.check( config( true, false, false ) );
    }

    private static JnlpDependencyConfig config( boolean signEnabled, boolean canUnsign, boolean updateManifest )
            throws Exception
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ), "SignTaskTest-" + System.nanoTime() );
        assertTrue( workDir.mkdirs() );
        File finalDir = new File( workDir, "final" );
        assertTrue( finalDir.mkdirs() );

        SignConfig sign = signEnabled ? new SignConfig() : null;
        JnlpDependencyGlobalConfig globalConfig =
                new JnlpDependencyGlobalConfig( SignTaskTest.class.getClassLoader(),
                                                new SimpleDependencyFilenameStrategy(), workDir, finalDir, null, sign,
                                                updateManifest ? Collections.singletonMap( "Built-By", "test" )
                                                        : Collections.<String, String>emptyMap(), false, false, false,
                                                canUnsign );
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

    private static SignTool signedJarTool( final boolean signed )
    {
        return new SignTool()
        {
            public File getKeyStoreFile( String keystore, File workingKeystore, ClassLoader classLoader )
            {
                return null;
            }

            public void generateKey( SignConfig config, File keystoreFile )
            {
            }

            public void sign( SignConfig config, File jarFile, File signedJar )
            {
            }

            public void verify( SignConfig config, File jarFile, boolean certs )
            {
            }

            public boolean isJarSigned( File jarFile )
            {
                return signed;
            }

            public void unsign( File jarFile, boolean verbose )
            {
            }

            public void deleteKeyStore( File keystore, boolean verbose )
            {
            }
        };
    }

    private static void injectSignTool( SignTask task, SignTool signTool )
            throws Exception
    {
        java.lang.reflect.Field field = SignTask.class.getDeclaredField( "signTool" );
        field.setAccessible( true );
        field.set( task, signTool );
    }
}
