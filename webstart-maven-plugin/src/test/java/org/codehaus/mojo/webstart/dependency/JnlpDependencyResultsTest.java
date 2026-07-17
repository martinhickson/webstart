package org.codehaus.mojo.webstart.dependency;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.mojo.webstart.dependency.filenaming.SimpleDependencyFilenameStrategy;
import org.codehaus.mojo.webstart.pack200.Pack200Config;
import org.codehaus.mojo.webstart.sign.SignConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

/**
 * Unit tests for {@link JnlpDependencyResults} and {@link JnlpDependencyRequest}.
 */
public class JnlpDependencyResultsTest
        extends TestCase
{

    public void testEmptyResultsAreNotInError()
    {
        JnlpDependencyResults results = new JnlpDependencyResults();
        assertFalse( results.isError() );
        assertEquals( 0, results.getNbRequestsProcessed() );
        assertEquals( 0, results.getNbRequestsUptodate() );
        assertTrue( results.getResults().isEmpty() );
    }

    public void testErrorDetection()
            throws Exception
    {
        JnlpDependencyResults results = new JnlpDependencyResults();
        JnlpDependencyRequest successRequest = newRequest( "success", false );
        JnlpDependencyRequest failureRequest = newRequest( "failure", false );

        JnlpDependencyResult failure = new JnlpDependencyResult( failureRequest );
        failure.setError( new RuntimeException( "boom" ) );

        results.registerResult( successRequest, new JnlpDependencyResult( successRequest ) );
        results.registerResult( failureRequest, failure );

        assertTrue( results.isError() );
        assertEquals( 1, results.getResultsWithError().length );
    }

    public void testProcessedAndUptodateCounts()
            throws Exception
    {
        JnlpDependencyResults results = new JnlpDependencyResults();
        JnlpDependencyRequest processed = newRequest( "processed", false );
        JnlpDependencyRequest uptodate = newRequest( "uptodate", true );

        results.registerResult( processed, new JnlpDependencyResult( processed ) );
        results.registerResult( uptodate, new JnlpDependencyResult( uptodate ) );

        assertEquals( 1, results.getNbRequestsProcessed() );
        assertEquals( 1, results.getNbRequestsUptodate() );
    }

    public void testFinalFileIncludesPack200AndGzipSuffixes()
            throws Exception
    {
        File workDir = tempDir( "JnlpDependencyRequestTest-" );
        File finalDir = new File( workDir, "final" );
        assertTrue( finalDir.mkdirs() );

        Pack200Config pack200 = new Pack200Config();
        pack200.setEnabled( true );

        JnlpDependencyGlobalConfig globalConfig =
                new JnlpDependencyGlobalConfig( getClass().getClassLoader(), new SimpleDependencyFilenameStrategy(),
                                                workDir, finalDir, pack200, new SignConfig(),
                                                Collections.<String, String>emptyMap(), true, false, false, false );
        Artifact artifact = artifact( workDir, "demo", 1000L );
        JnlpDependencyConfig config = new JnlpDependencyConfig( globalConfig, artifact, "demo", false, false );
        JnlpDependencyRequest request = new JnlpDependencyRequest( config );

        assertTrue( request.getFinalFile().getName().endsWith( ".jar.pack.gz" ) );
    }

    public void testRequestIsUptodateWhenOutputsAreNewer()
            throws Exception
    {
        JnlpDependencyRequest request = newRequest( "uptodate-check", true );
        assertTrue( request.isUptodate() );
    }

    public void testResultsMapIsUnmodifiable()
            throws Exception
    {
        JnlpDependencyResults results = new JnlpDependencyResults();
        JnlpDependencyRequest request = newRequest( "immutable", false );
        results.registerResult( request, new JnlpDependencyResult( request ) );

        try
        {
            results.getResults().put( request, new JnlpDependencyResult( request ) );
            fail( "Expected UnsupportedOperationException" );
        }
        catch ( UnsupportedOperationException e )
        {
            // expected
        }
    }

    private static JnlpDependencyRequest newRequest( String name, boolean uptodate )
            throws Exception
    {
        File workDir = tempDir( "JnlpDependencyResultsTest-" + name + "-" );
        File finalDir = new File( workDir, "final" );
        assertTrue( finalDir.mkdirs() );

        long sourceTime = 1_000L;
        long outputTime = 5_000L;
        Artifact artifact = artifact( workDir, name, sourceTime );

        SimpleDependencyFilenameStrategy strategy = new SimpleDependencyFilenameStrategy();
        JnlpDependencyGlobalConfig globalConfig =
                new JnlpDependencyGlobalConfig( JnlpDependencyResultsTest.class.getClassLoader(), strategy, workDir,
                                                finalDir, new Pack200Config(), new SignConfig(),
                                                Collections.<String, String>emptyMap(), false, false, false, false );
        JnlpDependencyConfig config = new JnlpDependencyConfig( globalConfig, artifact, name, false, false );

        if ( uptodate )
        {
            File originalFile =
                    new File( config.getWorkingDirectory(), strategy.getDependencyFilename( artifact, false, false ) );
            File finalFile =
                    new File( config.getFinalDirectory(), strategy.getDependencyFilename( artifact, false, false ) );
            touch( originalFile, outputTime );
            touch( finalFile, outputTime );
        }

        return new JnlpDependencyRequest( config );
    }

    private static File tempDir( String prefix )
    {
        File dir = new File( System.getProperty( "java.io.tmpdir" ), prefix + System.nanoTime() );
        assertTrue( dir.mkdirs() );
        return dir;
    }

    private static Artifact artifact( File parent, String name, long lastModified )
            throws Exception
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        DefaultArtifact artifact =
                new DefaultArtifact( "com.example", name, VersionRange.createFromVersion( "1.0" ), "compile", "jar",
                                     null, handler );
        File file = new File( parent, name + ".jar" );
        writeBytes( file, new byte[] { 1 } );
        assertTrue( file.setLastModified( lastModified ) );
        artifact.setFile( file );
        return artifact;
    }

    private static void touch( File file, long lastModified )
            throws Exception
    {
        File parent = file.getParentFile();
        if ( !parent.exists() )
        {
            assertTrue( parent.mkdirs() );
        }
        writeBytes( file, new byte[] { 1 } );
        assertTrue( file.setLastModified( lastModified ) );
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
