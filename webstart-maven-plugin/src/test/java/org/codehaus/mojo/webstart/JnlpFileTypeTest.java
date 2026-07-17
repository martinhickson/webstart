package org.codehaus.mojo.webstart;

import junit.framework.TestCase;

/**
 * Unit tests for {@link JnlpFileType}.
 */
public class JnlpFileTypeTest
        extends TestCase
{

    public void testApplicationDefaults()
    {
        assertEquals( "default-jnlp-template.vm", JnlpFileType.application.getDefaultTemplateName() );
        assertTrue( JnlpFileType.application.isRequireMainClass() );
    }

    public void testComponentDefaults()
    {
        assertEquals( "default-jnlp-component-template.vm", JnlpFileType.component.getDefaultTemplateName() );
        assertFalse( JnlpFileType.component.isRequireMainClass() );
    }

    public void testInstallerDefaults()
    {
        assertEquals( "default-jnlp-installer-template.vm", JnlpFileType.installer.getDefaultTemplateName() );
        assertFalse( JnlpFileType.installer.isRequireMainClass() );
    }
}
