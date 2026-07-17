package org.codehaus.mojo.webstart.dependency;

import junit.framework.TestCase;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;
import org.codehaus.mojo.webstart.pack200.Pack200Config;
import org.codehaus.mojo.webstart.sign.SignConfig;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for {@link JnlpDependencyGlobalConfig}.
 */
public class JnlpDependencyGlobalConfigTest
        extends TestCase
{

    public void testFlagsWithPack200AndSign()
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ), "global-config-" + System.nanoTime() );
        File finalDir = new File( workDir, "final" );
        assertTrue( workDir.mkdirs() );
        assertTrue( finalDir.mkdirs() );

        Pack200Config pack200 = new Pack200Config();
        pack200.setEnabled( true );
        pack200.setPassFiles( Arrays.asList( "META-INF/**" ) );

        SignConfig sign = new SignConfig();
        JnlpDependencyGlobalConfig config =
                new JnlpDependencyGlobalConfig( getClass().getClassLoader(), new SimpleDependencyFilenameStrategy(),
                                                workDir, finalDir, pack200, sign,
                                                Collections.singletonMap( "Built-By", "test" ), true, true, true, true );

        assertTrue( config.isPack200() );
        assertTrue( config.isSign() );
        assertTrue( config.isUpdateManifest() );
        assertTrue( config.isGzip() );
        assertTrue( config.isVerbose() );
        assertTrue( config.isUnsignAlreadySignedJars() );
        assertTrue( config.isCanUnsign() );
        assertEquals( 1, config.getPack200PassFiles().size() );
        assertEquals( "test", config.getUpdateManifestEntries().get( "Built-By" ) );
        assertEquals( workDir, config.getWorkingDirectory() );
        assertEquals( finalDir, config.getFinalDirectory() );
    }

    public void testDisabledPack200AndSign()
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ), "global-config-off-" + System.nanoTime() );
        File finalDir = new File( workDir, "final" );
        assertTrue( workDir.mkdirs() );
        assertTrue( finalDir.mkdirs() );

        JnlpDependencyGlobalConfig config =
                new JnlpDependencyGlobalConfig( getClass().getClassLoader(), new SimpleDependencyFilenameStrategy(),
                                                workDir, finalDir, null, null, Collections.<String, String>emptyMap(),
                                                false, false, false, false );

        assertFalse( config.isPack200() );
        assertFalse( config.isSign() );
        assertFalse( config.isUpdateManifest() );
        assertNull( config.getPack200PassFiles() );
    }
}
