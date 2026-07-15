package org.codehaus.mojo.webstart.sign;

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

import junit.framework.TestCase;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;
import java.util.Arrays;

/**
 * @author <a href="jerome@coffeebreaks.org">Jerome Lacoste</a>
 * @version $Id$
 */
public class SignConfigTest
        extends TestCase
{

    public void testGetDname()
    {
        SignConfig signConfig = new SignConfig();
        signConfig.setDnameCn( "www.example.com" );
        signConfig.setDnameOu( "None" );
        signConfig.setDnameL( "Seattle" );
        signConfig.setDnameSt( "Washington" );
        signConfig.setDnameO( "ExampleOrg" );
        signConfig.setDnameC( "US" );
        assertEquals( "CN=www.example.com, OU=None, L=Seattle, ST=Washington, O=ExampleOrg, C=US",
                      signConfig.getDname() );
    }

    public void testGetDnameMissing()
    {
        SignConfig signConfig = new SignConfig();
        signConfig.setDnameCn( "www.example.com" );
        signConfig.setDnameL( "Seattle" );
        signConfig.setDnameO( "ExampleOrg" );
        signConfig.setDnameC( "US" );
        assertEquals( "CN=www.example.com, L=Seattle, O=ExampleOrg, C=US", signConfig.getDname() );
    }

    public void testGetDnameWithCommaInOrganization()
    {
        SignConfig signConfig = new SignConfig();
        signConfig.setDnameCn( "www.example.com" );
        signConfig.setDnameOu( "None" );
        signConfig.setDnameL( "Seattle" );
        signConfig.setDnameSt( "Washington" );
        signConfig.setDnameO( "Some Company, Inc." );
        signConfig.setDnameC( "US" );
        assertEquals( "CN=www.example.com, OU=None, L=Seattle, ST=Washington, O=Some Company\\, Inc., C=US",
                      signConfig.getDname() );
    }

    public void testInitPreservesConfiguredArguments()
            throws Exception
    {
        SignConfig signConfig = new SignConfig();
        signConfig.setKeystore( "NONE" );
        signConfig.setArguments( new String[] { "-J-Dazure.keyvault.uri=https://example.vault.azure.net/" } );

        File workDirectory = new File( System.getProperty( "java.io.tmpdir" ), "SignConfigTest-" + System.nanoTime() );
        assertTrue( workDirectory.mkdirs() );

        signConfig.init( workDirectory, false, new NoOpSignTool(), null, getClass().getClassLoader() );

        assertTrue( Arrays.asList( signConfig.getArguments() ).contains(
                "-J-Dazure.keyvault.uri=https://example.vault.azure.net/" ) );
    }

    public void testCreateSignRequestPassesProviderName()
            throws Exception
    {
        SignConfig signConfig = new SignConfig();
        signConfig.setKeystore( "NONE" );
        signConfig.setAlias( "codesign" );
        signConfig.setStorepass( "" );
        signConfig.setKeypass( "" );
        signConfig.setProviderName( "AzureKeyVault" );
        signConfig.setProviderClass( "com.azure.security.keyvault.jca.KeyVaultJcaProvider" );

        File workDirectory = new File( System.getProperty( "java.io.tmpdir" ), "SignConfigTest-" + System.nanoTime() );
        assertTrue( workDirectory.mkdirs() );
        signConfig.init( workDirectory, false, new NoOpSignTool(), new PassthroughSecDispatcher(),
                getClass().getClassLoader() );

        File jar = new File( workDirectory, "unsigned.jar" );
        assertTrue( jar.createNewFile() );
        File signedJar = new File( workDirectory, "signed.jar" );

        JarSignerRequest request = signConfig.createSignRequest( jar, signedJar );
        assertTrue( request instanceof JarSignerSignRequest );
        JarSignerSignRequest signRequest = (JarSignerSignRequest) request;
        assertEquals( "AzureKeyVault", signRequest.getProviderName() );
        assertEquals( "com.azure.security.keyvault.jca.KeyVaultJcaProvider", signRequest.getProviderClass() );
    }

    private static final class NoOpSignTool
            implements SignTool
    {
        public void generateKey( SignConfig config, File keystoreFile )
        {
            throw new UnsupportedOperationException();
        }

        public File getKeyStoreFile( String keystore, File workingKeystore, ClassLoader classLoader )
        {
            return workingKeystore;
        }

        public void sign( SignConfig config, File jarFile, File signedJar )
        {
            throw new UnsupportedOperationException();
        }

        public void verify( SignConfig config, File jarFile, boolean certs )
        {
            throw new UnsupportedOperationException();
        }

        public boolean isJarSigned( File jarFile )
        {
            return false;
        }

        public void unsign( File jarFile, boolean verbose )
        {
            throw new UnsupportedOperationException();
        }

        public void deleteKeyStore( File keystore, boolean verbose )
        {
            throw new UnsupportedOperationException();
        }
    }

    private static final class PassthroughSecDispatcher
            implements SecDispatcher
    {
        public String decrypt( String encoded )
                throws SecDispatcherException
        {
            return encoded;
        }
    }
}
