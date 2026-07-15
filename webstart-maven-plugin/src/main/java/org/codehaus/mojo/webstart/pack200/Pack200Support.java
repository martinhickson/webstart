package org.codehaus.mojo.webstart.pack200;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Pack200 helpers that avoid compile-time references to {@code java.util.jar.Pack200},
 * which was removed from the JDK after Java 13.
 */
public final class Pack200Support
{

    public static final String SEGMENT_LIMIT = "pack.segment.limit";

    public static final String PASS_FILE_PFX = "pass.file.";

    private static final String UNAVAILABLE_WARNING =
            "Pack200 has been enabled in the webstart-maven-plugin configuration, but Pack200 compression "
                    + "is not supported on this JDK and will be skipped for this build. "
                    + "Pack200 remains available for legacy client runtimes via IcedTea-Web; "
                    + "to use Pack200 compression on modern JDKs enable pack200.commonsCompressEnabled, "
                    + "or set pack200.enabled to false.";

    private static final Boolean JDK_PACK200_AVAILABLE = detectJdkPack200();

    private Pack200Support()
    {
    }

    public static String getUnavailableWarningMessage()
    {
        return UNAVAILABLE_WARNING;
    }

    public static boolean isJdkPack200Available()
    {
        return JDK_PACK200_AVAILABLE;
    }

    public static boolean isRuntimeAvailable( boolean commonsCompressEnabled )
    {
        return commonsCompressEnabled || isJdkPack200Available();
    }

    public static void packWithJdk( JarFile jar, OutputStream out, Map<String, String> props )
            throws IOException
    {
        if ( !isJdkPack200Available() )
        {
            throw new IOException( "JDK Pack200 API is not available on this Java version" );
        }
        try
        {
            Class<?> pack200Class = Class.forName( "java.util.jar.Pack200" );
            Object packer = pack200Class.getMethod( "newPacker" ).invoke( null );
            @SuppressWarnings( "unchecked" )
            Map<String, String> packerProps = (Map<String, String>) packer.getClass().getMethod( "properties" ).invoke( packer );
            packerProps.putAll( props );
            packer.getClass().getMethod( "pack", JarFile.class, OutputStream.class ).invoke( packer, jar, out );
        }
        catch ( ReflectiveOperationException e )
        {
            throw new IOException( "Could not invoke JDK Pack200 packer", e );
        }
    }

    public static void unpackWithJdk( InputStream in, JarOutputStream out, Map<String, String> props )
            throws IOException
    {
        if ( !isJdkPack200Available() )
        {
            throw new IOException( "JDK Pack200 API is not available on this Java version" );
        }
        try
        {
            Class<?> pack200Class = Class.forName( "java.util.jar.Pack200" );
            Object unpacker = pack200Class.getMethod( "newUnpacker" ).invoke( null );
            @SuppressWarnings( "unchecked" )
            Map<String, String> unpackerProps = (Map<String, String>) unpacker.getClass().getMethod( "properties" ).invoke( unpacker );
            unpackerProps.putAll( props );
            unpacker.getClass().getMethod( "unpack", InputStream.class, JarOutputStream.class ).invoke( unpacker, in, out );
        }
        catch ( ReflectiveOperationException e )
        {
            throw new IOException( "Could not invoke JDK Pack200 unpacker", e );
        }
    }

    private static Boolean detectJdkPack200()
    {
        try
        {
            Class.forName( "java.util.jar.Pack200" );
            return Boolean.TRUE;
        }
        catch ( ClassNotFoundException e )
        {
            return Boolean.FALSE;
        }
    }
}
