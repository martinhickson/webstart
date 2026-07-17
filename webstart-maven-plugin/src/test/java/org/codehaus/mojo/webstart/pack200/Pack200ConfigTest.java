package org.codehaus.mojo.webstart.pack200;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link Pack200Config}.
 */
public class Pack200ConfigTest
        extends TestCase
{

    public void testDefaultsAreDisabled()
    {
        Pack200Config config = new Pack200Config();
        assertFalse( config.isEnabled() );
        assertFalse( config.isCommonsCompressEnabled() );
        assertNull( config.getPassFiles() );
    }

    public void testSettersAndGetters()
    {
        Pack200Config config = new Pack200Config();
        List<String> passFiles = Arrays.asList( "META-INF/", "resources/" );

        config.setEnabled( true );
        config.setCommonsCompressEnabled( true );
        config.setPassFiles( passFiles );

        assertTrue( config.isEnabled() );
        assertTrue( config.isCommonsCompressEnabled() );
        assertEquals( passFiles, config.getPassFiles() );
    }
}
