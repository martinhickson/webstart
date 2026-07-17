package jnlp.sample.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link VersionID} and {@link VersionString}.
 */
public class VersionIDTest
        extends TestCase
{

    public void testSimpleVersionMatch()
    {
        VersionID left = new VersionID( "1.2.3" );
        VersionID right = new VersionID( "1.2.3" );

        assertTrue( left.isSimpleVersion() );
        assertTrue( left.match( right ) );
        assertTrue( left.equals( right ) );
    }

    public void testPrefixMatch()
    {
        VersionID pattern = new VersionID( "1.2.*" );
        VersionID candidate = new VersionID( "1.2.9" );

        assertFalse( pattern.isSimpleVersion() );
        assertTrue( pattern.match( candidate ) );
        assertFalse( pattern.match( new VersionID( "1.3.0" ) ) );
    }

    public void testGreaterThanMatch()
    {
        VersionID minimum = new VersionID( "1.5+" );
        assertTrue( minimum.match( new VersionID( "1.5.0" ) ) );
        assertTrue( minimum.match( new VersionID( "2.0" ) ) );
        assertFalse( minimum.match( new VersionID( "1.4.9" ) ) );
    }

    public void testCompoundVersion()
    {
        VersionID compound = new VersionID( "1.0+&1.5+" );
        assertTrue( compound.match( new VersionID( "1.6" ) ) );
        assertFalse( compound.match( new VersionID( "1.0" ) ) );
    }

    public void testVersionStringContains()
    {
        VersionString exact = new VersionString( "1.0 2.*" );
        assertTrue( exact.contains( "1.0" ) );
        assertTrue( exact.contains( "2.1" ) );
        assertFalse( exact.contains( "3.0" ) );

        VersionString greater = new VersionString( "1.5+" );
        assertTrue( greater.contains( "1.6" ) );
        assertTrue( greater.contains( "3.0" ) );
        assertFalse( greater.contains( "1.4" ) );
    }

    public void testVersionStringContainsGreaterThan()
    {
        VersionString versions = new VersionString( "1.5+ 2.0" );
        assertTrue( versions.containsGreaterThan( "1.4" ) );
        assertFalse( versions.containsGreaterThan( "2.0" ) );
    }

    public void testStaticContainsHelper()
    {
        assertTrue( VersionString.contains( "1.0 1.5+", "1.6" ) );
        assertFalse( VersionString.contains( "1.0", "2.0" ) );
    }
}
