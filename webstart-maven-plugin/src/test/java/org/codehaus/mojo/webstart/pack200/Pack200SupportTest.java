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
        assertTrue( message.contains( "IcedTea-Web" ) );
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

    public void testRuntimeUnavailableWhenCommonsCompressDisabledAndJdkMissing()
    {
        if ( !Pack200Support.isJdkPack200Available() )
        {
            assertFalse( Pack200Support.isRuntimeAvailable( false ) );
        }
    }

    public void testPackWithJdkThrowsWhenUnavailable()
            throws Exception
    {
        if ( Pack200Support.isJdkPack200Available() )
        {
            return;
        }

        try
        {
            Pack200Support.packWithJdk( null, null, null );
            fail( "Expected IOException when JDK Pack200 is unavailable" );
        }
        catch ( java.io.IOException e )
        {
            assertTrue( e.getMessage().contains( "JDK Pack200 API is not available" ) );
        }
    }

    public void testConstantsAreStable()
    {
        assertEquals( "pack.segment.limit", Pack200Support.SEGMENT_LIMIT );
        assertEquals( "pass.file.", Pack200Support.PASS_FILE_PFX );
    }
}
