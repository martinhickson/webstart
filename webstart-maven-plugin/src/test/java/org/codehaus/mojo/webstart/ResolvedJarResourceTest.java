package org.codehaus.mojo.webstart;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Unit tests for {@link ResolvedJarResource}.
 */
public class ResolvedJarResourceTest
        extends TestCase
{

    public void testRejectsNullArtifact()
    {
        try
        {
            new ResolvedJarResource( (Artifact) null );
            fail( "Expected IllegalArgumentException" );
        }
        catch ( IllegalArgumentException e )
        {
            assertTrue( e.getMessage().contains( "artifact must not be null" ) );
        }
    }

    public void testRejectsNullJarResource()
    {
        try
        {
            new ResolvedJarResource( null, artifact() );
            fail( "Expected IllegalArgumentException" );
        }
        catch ( IllegalArgumentException e )
        {
            assertTrue( e.getMessage().contains( "jarResource must not be null" ) );
        }
    }

    public void testUsesArtifactCoordinates()
            throws Exception
    {
        Artifact artifact = artifact();
        ResolvedJarResource resource = new ResolvedJarResource( artifact );

        assertEquals( "demo", resource.getArtifactId() );
        assertEquals( "com.example", resource.getGroupId() );
        assertEquals( "1.0", resource.getVersion() );
        assertEquals( "jar", resource.getType() );
    }

    private static Artifact artifact()
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        return new DefaultArtifact( "com.example", "demo", VersionRange.createFromVersion( "1.0" ), "compile", "jar",
                                    null, handler );
    }
}
