package org.codehaus.mojo.webstart.generator;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.mojo.webstart.JnlpConfig;
import org.codehaus.mojo.webstart.JnlpExtension;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;

import java.util.Collections;

/**
 * Unit tests for {@link GeneratorConfig} and {@link ExtensionGeneratorConfig}.
 */
public class GeneratorConfigTest
        extends TestCase
{

    public void testGeneratorConfigDefaults()
    {
        JnlpConfig jnlp = new JnlpConfig();
        GeneratorConfig config =
                new GeneratorConfig( "lib", true, true, false, mainArtifact(), new SimpleDependencyFilenameStrategy(),
                                     Collections.<Artifact>emptyList(), Collections.<JnlpExtension>emptyList(),
                                     "https://example.com/app", jnlp );

        assertEquals( "1.0+", config.getJnlpSpec() );
        assertEquals( "false", config.getOfflineAllowed() );
        assertEquals( "true", config.getAllPermissions() );
        assertEquals( "1.5+", config.getJ2seVersion() );
        assertEquals( "https://example.com/app", config.getJnlpCodeBase() );
        assertFalse( config.hasJnlpExtensions() );
    }

    public void testGeneratorConfigUsesJnlpOverrides()
    {
        JnlpConfig jnlp = new JnlpConfig();
        jnlp.setSpec( "6.0+" );
        jnlp.setOfflineAllowed( "true" );
        jnlp.setAllPermissions( "false" );
        jnlp.setJ2seVersion( "11+" );
        jnlp.setIconHref( "icon.png" );

        GeneratorConfig config =
                new GeneratorConfig( "lib", false, false, false, mainArtifact(), new SimpleDependencyFilenameStrategy(),
                                     Collections.<Artifact>emptyList(), Collections.singletonList( new JnlpExtension() ),
                                     "https://example.com/app", jnlp );

        assertEquals( "6.0+", config.getJnlpSpec() );
        assertEquals( "true", config.getOfflineAllowed() );
        assertEquals( "false", config.getAllPermissions() );
        assertEquals( "11+", config.getJ2seVersion() );
        assertEquals( "icon.png", config.getIconHref() );
        assertTrue( config.hasJnlpExtensions() );
    }

    public void testExtensionGeneratorConfig()
    {
        JnlpExtension extension = new JnlpExtension();
        extension.setSpec( "7.0+" );
        extension.setOfflineAllowed( "true" );
        extension.setAllPermissions( "false" );
        extension.setJ2seVersion( "17+" );
        extension.setIconHref( "ext.png" );

        ExtensionGeneratorConfig config =
                new ExtensionGeneratorConfig( "lib", true, true, true, mainArtifact(),
                                              new SimpleDependencyFilenameStrategy(),
                                              Collections.<JnlpExtension, java.util.List<Artifact>>emptyMap(),
                                              "https://example.com/ext", extension );

        assertEquals( extension, config.getExtension() );
        assertEquals( "7.0+", config.getJnlpSpec() );
        assertEquals( "true", config.getOfflineAllowed() );
        assertEquals( "false", config.getAllPermissions() );
        assertEquals( "17+", config.getJ2seVersion() );
        assertEquals( "ext.png", config.getIconHref() );
        assertEquals( "https://example.com/ext", config.getJnlpCodeBase() );
    }

    private static Artifact mainArtifact()
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        return new DefaultArtifact( "com.example", "demo", VersionRange.createFromVersion( "1.0" ), "compile", "jar",
                                    null, handler );
    }
}
