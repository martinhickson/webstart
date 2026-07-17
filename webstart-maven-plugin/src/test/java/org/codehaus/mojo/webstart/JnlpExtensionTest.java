package org.codehaus.mojo.webstart;

import junit.framework.TestCase;

import java.util.Collections;

/**
 * Unit tests for {@link JnlpExtension}.
 */
public class JnlpExtensionTest
        extends TestCase
{

    public void testExtensionFields()
    {
        JnlpExtension extension = new JnlpExtension();
        extension.setName( "demo-ext" );
        extension.setTitle( "Demo Extension" );
        extension.setVendor( "Example Corp" );
        extension.setHomepage( "https://example.com" );
        extension.setDescription( "Optional libraries" );
        extension.setIncludes( Collections.singletonList( "lib/*.jar" ) );
        extension.setSpec( "1.5+" );

        assertEquals( "demo-ext", extension.getName() );
        assertEquals( "Demo Extension", extension.getTitle() );
        assertEquals( "Example Corp", extension.getVendor() );
        assertEquals( "https://example.com", extension.getHomepage() );
        assertEquals( "Optional libraries", extension.getDescription() );
        assertEquals( 1, extension.getIncludes().size() );
        assertEquals( "1.5+", extension.getSpec() );
    }
}
