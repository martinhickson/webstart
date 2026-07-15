package org.codehaus.mojo.webstart.util;

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

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Formatted build summaries for signing and packaging output.
 */
public final class BuildReport
{

    static final String PREFIX = "[webstart] ";

    private BuildReport()
    {
    }

    public static void logWorkDirectorySummary( Log log, File workDirectory )
    {
        logLines( log, workDirectorySummaryLines( workDirectory ) );
    }

    public static void logWorkDirectorySummary( Logger log, File workDirectory )
    {
        logLines( log, workDirectorySummaryLines( workDirectory ) );
    }

    public static void logSignedJars( Log log, File directory )
    {
        logLines( log, signedJarLines( directory ) );
    }

    public static void logJarSignature( Log log, File jar )
    {
        logLines( log, jarSignatureLines( jar ) );
    }

    public static void logDistributionArchive( Log log, File archive )
    {
        logLines( log, distributionArchiveLines( archive ) );
    }

    public static void logDistributionArchive( Logger log, File archive )
    {
        logLines( log, distributionArchiveLines( archive ) );
    }

    static List<String> workDirectorySummaryLines( File workDirectory )
    {
        List<String> lines = new ArrayList<>();
        if ( workDirectory == null || !workDirectory.isDirectory() )
        {
            return lines;
        }

        lines.add( "" );
        lines.add( "Build summary" );
        lines.addAll( jdkEnvironmentLines() );
        lines.add( "JNLP bundle directory: " + workDirectory.getAbsolutePath() );

        File[] jnlpFiles = workDirectory.listFiles( ( dir, name ) -> name.endsWith( ".jnlp" ) );
        if ( jnlpFiles != null && jnlpFiles.length > 0 )
        {
            Arrays.sort( jnlpFiles, Comparator.comparing( File::getName ) );
            lines.add( "JNLP descriptors:" );
            for ( File jnlp : jnlpFiles )
            {
                lines.add( "  " + jnlp.getName() + " (" + formatSize( jnlp.length() ) + ")" );
            }
        }

        lines.addAll( signedJarLines( workDirectory ) );
        return lines;
    }

    static List<String> signedJarLines( File directory )
    {
        List<String> lines = new ArrayList<>();
        File[] jars = directory.listFiles( ( dir, name ) -> name.endsWith( ".jar" ) );
        if ( jars == null || jars.length == 0 )
        {
            lines.add( "Signed JAR artifacts: (none)" );
            return lines;
        }

        Arrays.sort( jars, Comparator.comparing( File::getName ) );
        lines.add( "Signed JAR artifacts (" + jars.length + "):" );
        for ( File jar : jars )
        {
            lines.addAll( jarSignatureLines( jar ) );
        }
        return lines;
    }

    static List<String> jarSignatureLines( File jar )
    {
        List<String> lines = new ArrayList<>();
        lines.add( "  " + jar.getName() + " (" + formatSize( jar.length() ) + ")" );
        for ( String line : describeJarCertificates( jar ) )
        {
            lines.add( "    " + line );
        }
        return lines;
    }

    static List<String> distributionArchiveLines( File archive )
    {
        List<String> lines = new ArrayList<>();
        if ( archive == null || !archive.isFile() )
        {
            return lines;
        }

        lines.add( "" );
        lines.add( "Distribution archive: " + archive.getAbsolutePath() + " (" + formatSize( archive.length() ) + ")" );
        lines.addAll( jdkEnvironmentLines() );

        try ( ZipFile zipFile = new ZipFile( archive ) )
        {
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zipFile.entries();
            while ( en.hasMoreElements() )
            {
                entries.add( en.nextElement() );
            }
            entries.sort( Comparator.comparing( ZipEntry::getName ) );

            lines.add( "Archive contents (" + entries.size() + " entries):" );
            for ( ZipEntry entry : entries )
            {
                if ( entry.isDirectory() )
                {
                    lines.add( "  " + entry.getName() );
                }
                else
                {
                    lines.add( "  " + entry.getName() + " (" + formatSize( entry.getSize() ) + ")" );
                }
            }
        }
        catch ( IOException e )
        {
            lines.add( "Could not read archive contents: " + e.getMessage() );
        }
        return lines;
    }

    static List<String> describeJarCertificates( File jar )
    {
        List<String> lines = new ArrayList<>();
        try ( JarFile jarFile = new JarFile( jar, true ) )
        {
            CodeSigner[] signers = findCodeSigners( jarFile );
            if ( signers == null || signers.length == 0 )
            {
                lines.add( "signature: unsigned" );
                return lines;
            }

            lines.add( "signature: verified (JAR signed)" );
            int certIndex = 1;
            for ( CodeSigner signer : signers )
            {
                for ( Certificate certificate : signer.getSignerCertPath().getCertificates() )
                {
                    if ( certificate instanceof X509Certificate )
                    {
                        X509Certificate x509 = (X509Certificate) certificate;
                        lines.add( "signer certificate #" + certIndex + ": "
                                + x509.getSubjectX500Principal().getName() );
                        lines.add( "SHA-256 fingerprint: " + fingerprint( x509, "SHA-256" ) );
                        certIndex++;
                    }
                }
            }
        }
        catch ( IOException e )
        {
            lines.add( "signature: could not read JAR (" + e.getMessage() + ")" );
        }
        return lines;
    }

    private static CodeSigner[] findCodeSigners( JarFile jarFile ) throws IOException
    {
        Enumeration<JarEntry> entries = jarFile.entries();
        while ( entries.hasMoreElements() )
        {
            JarEntry entry = entries.nextElement();
            if ( !entry.isDirectory() )
            {
                try ( InputStream inputStream = jarFile.getInputStream( entry ) )
                {
                    byte[] buffer = new byte[8192];
                    while ( inputStream.read( buffer ) != -1 )
                    {
                        // read fully so signature metadata is available
                    }
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if ( signers != null && signers.length > 0 )
                {
                    return signers;
                }
            }
        }
        return null;
    }

    private static String fingerprint( X509Certificate certificate, String algorithm )
            throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance( algorithm );
            byte[] hash = digest.digest( certificate.getEncoded() );
            StringBuilder sb = new StringBuilder();
            for ( int i = 0; i < hash.length; i++ )
            {
                if ( i > 0 )
                {
                    sb.append( ':' );
                }
                sb.append( String.format( "%02X", hash[i] ) );
            }
            return sb.toString();
        }
        catch ( Exception e )
        {
            throw new IOException( "Could not compute " + algorithm + " fingerprint", e );
        }
    }

    private static List<String> jdkEnvironmentLines()
    {
        List<String> lines = new ArrayList<>();
        lines.add( "java.version: " + System.getProperty( "java.version", "unknown" ) );
        lines.add( "java.vendor: " + System.getProperty( "java.vendor", "unknown" ) );
        lines.add( "java.vm.name: " + System.getProperty( "java.vm.name", "unknown" ) );
        return lines;
    }

    private static String formatSize( long bytes )
    {
        if ( bytes < 1024 )
        {
            return bytes + " B";
        }
        if ( bytes < 1024 * 1024 )
        {
            return String.format( "%.1f KB", bytes / 1024.0 );
        }
        return String.format( "%.1f MB", bytes / ( 1024.0 * 1024.0 ) );
    }

    private static void logLines( Log log, List<String> lines )
    {
        for ( String line : lines )
        {
            if ( line.startsWith( "Could not read archive contents:" ) )
            {
                log.warn( PREFIX + line );
            }
            else
            {
                log.info( PREFIX + line );
            }
        }
    }

    private static void logLines( Logger log, List<String> lines )
    {
        for ( String line : lines )
        {
            if ( line.startsWith( "Could not read archive contents:" ) )
            {
                log.warn( PREFIX + line );
            }
            else
            {
                log.info( PREFIX + line );
            }
        }
    }
}
