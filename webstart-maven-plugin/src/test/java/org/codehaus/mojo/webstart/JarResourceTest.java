package org.codehaus.mojo.webstart;

import junit.framework.TestCase;

/**
 * Unit tests for {@link JarResource}.
 */
public class JarResourceTest
        extends TestCase
{

    public void testMandatoryFieldRequiresCoordinates()
    {
        JarResource resource = new JarResource();
        assertFalse( resource.isMandatoryField() );

        resource = new JarResource();
        setField( resource, "groupId", "com.example" );
        setField( resource, "artifactId", "demo" );
        setField( resource, "version", "1.0" );
        assertTrue( resource.isMandatoryField() );
    }

    public void testDefaultsAndToString()
    {
        JarResource resource = new JarResource();
        setField( resource, "groupId", "com.example" );
        setField( resource, "artifactId", "demo" );
        setField( resource, "version", "1.0" );
        setField( resource, "mainClass", "com.example.Main" );

        assertTrue( resource.isOutputJarVersion() );
        assertTrue( resource.isIncludeInJnlp() );
        assertTrue( resource.toString().contains( "com.example" ) );
    }

    private static void setField( JarResource resource, String fieldName, String value )
    {
        try
        {
            java.lang.reflect.Field field = JarResource.class.getDeclaredField( fieldName );
            field.setAccessible( true );
            field.set( resource, value );
        }
        catch ( ReflectiveOperationException e )
        {
            throw new RuntimeException( e );
        }
    }
}
