package org.codehaus.mojo.webstart.pack200;

import junit.framework.TestCase;

public class Pack200SupportTest
        extends TestCase
{

    public void testCommonsCompressAlwaysAvailableWhenEnabled()
    {
        assertTrue( Pack200Support.isRuntimeAvailable( true ) );
    }

    public void testUnavailableWarningIsInformative()
    {
        String message = Pack200Support.getUnavailableWarningMessage();
        assertTrue( message.contains( "Pack200 has been enabled" ) );
        assertTrue( message.contains( "commonsCompressEnabled" ) );
    }

    public void testJdkAvailabilityMatchesRuntime()
    {
        boolean jdkAvailable = Pack200Support.isJdkPack200Available();
        try
        {
            Class.forName( "java.util.jar.Pack200" );
            assertTrue( jdkAvailable );
        }
        catch ( ClassNotFoundException e )
        {
            assertFalse( jdkAvailable );
        }
    }
}
