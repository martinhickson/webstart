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

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link AbstractJnlpMojo#checkDependencies()} without the legacy Maven 2.x plugin-testing harness.
 */
public class JnlpInlineMojoDependenciesTest
        extends TestCase
{

    public void testFailWhenSomeDependenciesDoNotExist()
            throws Exception
    {
        JnlpInlineMojo mojo = newMojo( sampleArtifacts() );

        AbstractJnlpMojo.Dependencies deps = new AbstractJnlpMojo.Dependencies();
        List<String> includes = new ArrayList<>();
        includes.add( "tatatata" );
        includes.add( "titititi" );
        List<String> excludes = new ArrayList<>();
        excludes.add( "commons-lang:commons-lang" );
        excludes.add( "totototo" );
        deps.setIncludes( includes );
        deps.setExcludes( excludes );
        setField( mojo, "dependencies", deps );

        assertTrue( "dependencies not null", mojo.getDependencies() != null );
        assertEquals( "2 includes", 2, mojo.getDependencies().getIncludes().size() );
        assertEquals( "2 excludes", 2, mojo.getDependencies().getExcludes().size() );

        try
        {
            mojo.checkDependencies();
            fail( "Should have detected invalid webstart <dependencies>" );
        }
        catch ( MojoExecutionException e )
        {
            assertTrue( e.getMessage().contains( "incorrect" ) );
        }
    }

    public void testAllDependenciesExist()
            throws Exception
    {
        JnlpInlineMojo mojo = newMojo( sampleArtifacts() );

        AbstractJnlpMojo.Dependencies deps = new AbstractJnlpMojo.Dependencies();
        List<String> excludes = new ArrayList<>();
        excludes.add( "commons-lang:commons-lang" );
        deps.setExcludes( excludes );
        setField( mojo, "dependencies", deps );

        assertTrue( "dependencies not null", mojo.getDependencies() != null );
        assertNull( "no include", mojo.getDependencies().getIncludes() );
        assertEquals( "1 exclude", 1, mojo.getDependencies().getExcludes().size() );

        mojo.checkDependencies();
    }

    private static JnlpInlineMojo newMojo( Set<Artifact> artifacts )
            throws Exception
    {
        MavenProject project = new MavenProject();
        project.setArtifactId( "webstart-test" );
        project.setArtifacts( artifacts );

        JnlpInlineMojo mojo = new JnlpInlineMojo();
        setField( mojo, "project", project );
        setField( mojo, "log", new SystemStreamLog() );
        return mojo;
    }

    private static Set<Artifact> sampleArtifacts()
    {
        DefaultArtifactHandler handler = new DefaultArtifactHandler( "jar" );
        Artifact commonsIo =
                new DefaultArtifact( "commons-io", "commons-io", VersionRange.createFromVersion( "1.2" ), "compile",
                                     "jar", null, handler );
        Artifact commonsLang =
                new DefaultArtifact( "commons-lang", "commons-lang", VersionRange.createFromVersion( "2.1" ), "compile",
                                     "jar", null, handler );

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add( commonsIo );
        artifacts.add( commonsLang );
        return artifacts;
    }

    private static void setField( Object target, String name, Object value )
            throws Exception
    {
        Field field = findField( target.getClass(), name );
        field.setAccessible( true );
        field.set( target, value );
    }

    private static Field findField( Class<?> type, String name )
            throws NoSuchFieldException
    {
        Class<?> current = type;
        while ( current != null )
        {
            try
            {
                return current.getDeclaredField( name );
            }
            catch ( NoSuchFieldException e )
            {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException( name );
    }
}
