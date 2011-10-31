package org.codehaus.mojo.webstart;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.webstart.sign.SignTool;

import java.io.File;

/**
 * Unsigns a JAR, removing signatures.
 * <p/>
 * This code will hopefully be moved into the jar plugin when stable enough.
 *
 * @author <a href="mailto:jerome@coffeebreaks.org">Jerome Lacoste</a>
 * @author <a href="mailto:andrius@pivotcapital.com">Andrius Šabanas</a>
 * @version $Id$
 * @goal unsign
 * @phase package
 * @requiresProject
 */
public class JarUnsignMojo
    extends AbstractMojo
{
    /**
     * Set this to <code>true</code> to disable signing.
     * Useful to speed up build process in development environment.
     *
     * @parameter expression="${maven.jar.unsign.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * The directory location used for temporary storage of files used by this mojo.
     *
     * @parameter expression="${tempdir}" default-value="${basedir}"
     * @required
     */
    private File tempDirectory;

    /**
     * Path of the jar to unsign. When specified, the finalName is ignored.
     *
     * @parameter alias="jarpath"
     * default-value="${project.build.directory}/${project.build.finalName}.${project.packaging}"
     */
    private File jarPath;

    /**
     * Enable verbose mode.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * JarSigner tool.
     *
     * @component role="org.codehaus.mojo.webstart.sign.SignTool"
     * @required
     * @readonly
     */
    private SignTool signTool;

//    /**
//     * @component
//     */
//    private MavenProjectHelper projectHelper;
//
//    /**
//     * To look up Archiver/UnArchiver implementations
//     *
//     * @component
//     * @required
//     */
//    protected ArchiverManager archiverManager;

//    private static final String[] EXT_ARRAY = { "DSA", "RSA", "SF" };
//
//    private FileFilter removeSignatureFileFilter = new FileFilter()
//    {
//        private final List extToRemove = Arrays.asList( EXT_ARRAY );
//
//        public boolean accept( File file )
//        {
//            String extension = FileUtils.getExtension( file.getAbsolutePath() );
//            return ( extToRemove.contains( extension ) );
//        }
//    };

    public void execute()
        throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info( "Skipping JAR unsigning for file: " + jarPath.getAbsolutePath() );
            return;
        }

        signTool.unsign( this.jarPath, this.tempDirectory, verbose );

//        File jarFile = this.jarPath;
//        File tempDirParent = this.tempDirectory;
//
//        String archiveExt = FileUtils.getExtension( jarFile.getAbsolutePath() ).toLowerCase();
//
//        // create temp dir
//        File tempDir = new File( tempDirParent, jarFile.getName() );
//
//        if ( !tempDir.mkdirs() )
//        {
//            throw new MojoExecutionException( "Error creating temporary directory: " + tempDir );
//        }
//        // FIXME we probably want to be more security conservative here.
//        // it's very easy to guess where the directory will be and possible
//        // to access/change its contents before the file is rejared..
//
//        // extract jar into temporary directory
//        try
//        {
//            UnArchiver unArchiver = this.archiverManager.getUnArchiver( archiveExt );
//            unArchiver.setSourceFile( jarFile );
//            unArchiver.setDestDirectory( tempDir );
//            unArchiver.extract();
//        }
//        catch ( ArchiverException ex )
//        {
//            throw new MojoExecutionException( "Error unpacking file: " + jarFile + "to: " + tempDir, ex );
//        }
//        catch ( NoSuchArchiverException ex )
//        {
//            throw new MojoExecutionException( "Error acquiring unarchiver for extension: " + archiveExt, ex );
//        }
//
//        // create and check META-INF directory
//        File metaInf = new File( tempDir, "META-INF" );
//        if ( !metaInf.isDirectory() )
//        {
//            verboseLog( "META-INT dir not found : nothing to do for file: " + jarPath.getAbsolutePath() );
//            return;
//        }
//
//        // filter signature files and remove them
//        File[] filesToRemove = metaInf.listFiles( this.removeSignatureFileFilter );
//        if ( filesToRemove.length == 0 )
//        {
//            verboseLog(
//                "no files match " + toString( EXT_ARRAY ) + " : nothing to do for file: " + jarPath.getAbsolutePath() );
//            return;
//        }
//        for ( int i = 0; i < filesToRemove.length; i++ )
//        {
//            if ( !filesToRemove[i].delete() )
//            {
//                throw new MojoExecutionException( "Error removing signature file: " + filesToRemove[i] );
//            }
//            verboseLog( "remove file :" + filesToRemove[i] );
//        }
//
//        // recreate archive
//        try
//        {
//            JarArchiver jarArchiver = (JarArchiver) this.archiverManager.getArchiver( "jar" );
//            jarArchiver.setUpdateMode( false );
//            jarArchiver.addDirectory( tempDir );
//            jarArchiver.setDestFile( jarFile );
//            jarArchiver.createArchive();
//
//        }
//        catch ( ArchiverException ex )
//        {
//            throw new MojoExecutionException( "Error packing directory: " + tempDir + "to: " + jarFile, ex );
//        }
//        catch ( IOException ex )
//        {
//            throw new MojoExecutionException( "Error packing directory: " + tempDir + "to: " + jarFile, ex );
//        }
//        catch ( NoSuchArchiverException ex )
//        {
//            throw new MojoExecutionException( "Error acquiring archiver for extension: jar", ex );
//        }
//
//        try
//        {
//            FileUtils.deleteDirectory( tempDir );
//        }
//        catch ( IOException ex )
//        {
//            throw new MojoExecutionException( "Error cleaning up temporary directory file: " + tempDir, ex );
//        }
    }

//    /**
//     * Log as info when verbose or info is enabled, as debug otherwise.
//     * @param msg message to log
//     */
//    protected void verboseLog( String msg )
//    {
//        infoOrDebug( isVerbose() || getLog().isInfoEnabled(), msg );
//    }
//
//    /**
//     * Log a message as info or debug.
//     *
//     * @param info if set to true, log as info(), otherwise as debug()
//     * @param msg message to log
//     */
//    private void infoOrDebug( boolean info, String msg )
//    {
//        if ( info )
//        {
//            getLog().info( msg );
//        }
//        else
//        {
//            getLog().debug( msg );
//        }
//    }


    public void setTempDir( File tempDirectory )
    {
        this.tempDirectory = tempDirectory;
    }

    public void setJarPath( File jarPath )
    {
        this.jarPath = jarPath;
    }

    public void setVerbose( boolean verbose )
    {
        this.verbose = verbose;
    }

    public void setSignTool( SignTool signTool )
    {
        this.signTool = signTool;
    }

//    public boolean isVerbose()
//    {
//        return verbose;
//    }

//    public void setArchiverManager( ArchiverManager archiverManager )
//    {
//        this.archiverManager = archiverManager;
//    }

//    public String toString( String[] items )
//    {
//        StringBuffer back = new StringBuffer( "{" );
//        for ( int i = 0; i < items.length; i++ )
//        {
//            if ( i != 0 )
//            {
//                back.append( ", " );
//            }
//            back.append( '"' ).append( items[i] ).append( '"' );
//        }
//        back.append( "}" );
//        return back.toString();
//    }

}
