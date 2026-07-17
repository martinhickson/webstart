package org.codehaus.mojo.webstart;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for {@link JnlpFile}.
 */
public class JnlpFileTest
        extends TestCase
{

    public void testPublicGettersAndSetters()
    {
        JnlpFile jnlpFile = new JnlpFile();
        jnlpFile.setInputTemplateResourcePath( "templates/launch.vm" );
        jnlpFile.setInputTemplate( "input.vm" );
        jnlpFile.setMainClass( "com.example.Main" );
        jnlpFile.setProperties( Collections.singletonMap( "env", "prod" ) );
        jnlpFile.setArguments( Arrays.asList( "-Xmx512m", "-Ddebug=true" ) );

        assertEquals( "templates/launch.vm", jnlpFile.getInputTemplateResourcePath() );
        assertEquals( "input.vm", jnlpFile.getInputTemplate() );
        assertEquals( "prod", jnlpFile.getProperties().get( "env" ) );
        assertEquals( 2, jnlpFile.getArguments().size() );
    }
}
