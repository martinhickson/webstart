package org.codehaus.mojo.webstart.dependency.filenaming;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.File;

/**
 * Unit tests for {@link SimpleDependencyFilenameStrategy} and {@link FullDependencyFilenameStrategy}.
 */
public class DependencyFilenameStrategyTest
        extends TestCase
{

    public void testSimpleStrategyWithoutVersionOrClassifier()
    {
        DependencyFilenameStrategy strategy = new SimpleDependencyFilenameStrategy();
        Artifact artifact = artifact( "com.example", "demo", "1.0", null );

        assertEquals( "demo.jar", strategy.getDependencyFilename( artifact, null, null ) );
        assertEquals( "demo", strategy.getDependencyFileBasename( artifact, null, null ) );
    }

    public void testSimpleStrategyWithVersionInFilename()
    {
        DependencyFilenameStrategy strategy = new SimpleDependencyFilenameStrategy();
        Artifact artifact = artifact( "com.example", "demo", "1.0", "sources" );

        assertEquals( "demo-1.0-sources.jar", strategy.getDependencyFilename( artifact, false, false ) );
    }

    public void testSimpleStrategyWithVersionEnabledUsesDoubleUnderscore()
    {
        DependencyFilenameStrategy strategy = new SimpleDependencyFilenameStrategy();
        Artifact artifact = artifact( "com.example", "demo", "1.0", null );

        assertEquals( "demo__V1.0.jar", strategy.getDependencyFilename( artifact, true, false ) );
    }

    public void testFullStrategyIncludesGroupId()
    {
        DependencyFilenameStrategy strategy = new FullDependencyFilenameStrategy();
        Artifact artifact = artifact( "com.example", "demo", "2.1", null );

        assertEquals( "com.example-demo-2.1.jar", strategy.getDependencyFilename( artifact, false, false ) );
    }

    public void testUniqueVersionsReplaceSnapshotSuffix()
            throws Exception
    {
        File jar = File.createTempFile( "dependency-filename-", ".jar" );
        jar.deleteOnExit();
        assertTrue( jar.setLastModified( 0L ) );

        DependencyFilenameStrategy strategy = new SimpleDependencyFilenameStrategy();
        Artifact artifact = artifact( "com.example", "demo", "1.0-SNAPSHOT", null );
        artifact.setFile( jar );

        assertEquals( "demo-1.0-19700101.000000-0.jar", strategy.getDependencyFilename( artifact, false, true ) );
        assertEquals( "1.0-19700101.000000-0", strategy.getDependencyFileVersion( artifact, true ) );
    }

    private static Artifact artifact( String groupId, String artifactId, String version, String classifier )
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        return new DefaultArtifact( groupId, artifactId, VersionRange.createFromVersion( version ), "compile", "jar",
                                    classifier, handler );
    }
}
