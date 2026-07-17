package org.codehaus.mojo.webstart;

import junit.framework.TestCase;

import java.io.File;
import java.util.Collections;

/**
 * Unit tests for {@link JnlpConfig}.
 */
public class JnlpConfigTest
        extends TestCase
{

    public void testGettersAndSetters()
    {
        JnlpConfig config = new JnlpConfig();
        config.setInputTemplateResourcePath( "templates/app.vm" );
        config.setInputTemplate( "custom.vm" );
        config.setOutputFile( "launch.jnlp" );
        config.setSpec( "1.5+" );
        config.setVersion( "1.0" );
        config.setJ2seVersion( "11+" );
        config.setAllPermissions( "true" );
        config.setOfflineAllowed( "false" );
        config.setHref( "app.jnlp" );
        config.setMainClass( "com.example.Main" );
        config.setIconHref( "icon.png" );
        config.setProperties( Collections.singletonMap( "key", "value" ) );
        config.setResources( new File( "resources" ) );
        config.setType( JnlpFileType.component );

        assertEquals( "templates/app.vm", config.getInputTemplateResourcePath() );
        assertEquals( "custom.vm", config.getInputTemplate() );
        assertEquals( "launch.jnlp", config.getOutputFile() );
        assertEquals( "1.5+", config.getSpec() );
        assertEquals( "1.0", config.getVersion() );
        assertEquals( "11+", config.getJ2seVersion() );
        assertEquals( "true", config.getAllPermissions() );
        assertEquals( "false", config.getOfflineAllowed() );
        assertEquals( "app.jnlp", config.getHref() );
        assertEquals( "com.example.Main", config.getMainClass() );
        assertEquals( "icon.png", config.getIconHref() );
        assertEquals( "value", config.getProperties().get( "key" ) );
        assertEquals( new File( "resources" ), config.getResources() );
        assertEquals( JnlpFileType.component, config.getType() );
    }

    public void testSetTypeFromString()
    {
        JnlpConfig config = new JnlpConfig();
        config.setType( "installer" );
        assertEquals( JnlpFileType.installer, config.getType() );
    }
}
