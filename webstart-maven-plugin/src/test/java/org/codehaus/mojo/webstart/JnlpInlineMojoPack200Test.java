package org.codehaus.mojo.webstart;

import junit.framework.TestCase;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.mojo.webstart.pack200.Pack200Config;
import org.codehaus.mojo.webstart.pack200.Pack200Support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Unit tests for {@link AbstractBaseJnlpMojo#isEffectivePack200()} and related Pack200 flags.
 */
public class JnlpInlineMojoPack200Test
        extends TestCase
{

    public void testPack200DisabledWhenNotConfigured()
            throws Exception
    {
        JnlpInlineMojo mojo = newMojo();
        assertFalse( mojo.isPack200() );
        assertFalse( invokeEffectivePack200( mojo ) );
    }

    public void testEffectivePack200WhenCommonsCompressEnabled()
            throws Exception
    {
        JnlpInlineMojo mojo = newMojo();
        setPack200( mojo, enabledConfig( true, true ) );

        assertTrue( mojo.isPack200() );
        assertTrue( mojo.isCommonsCompressEnabled() );
        assertTrue( invokeEffectivePack200( mojo ) );
    }

    public void testEffectivePack200WhenJdkPack200Available()
            throws Exception
    {
        if ( !Pack200Support.isJdkPack200Available() )
        {
            return;
        }

        JnlpInlineMojo mojo = newMojo();
        setPack200( mojo, enabledConfig( true, false ) );

        assertTrue( invokeEffectivePack200( mojo ) );
    }

    public void testEffectivePack200UnavailableWithoutRuntimeSupport()
            throws Exception
    {
        if ( Pack200Support.isJdkPack200Available() )
        {
            return;
        }

        JnlpInlineMojo mojo = newMojo();
        setPack200( mojo, enabledConfig( true, false ) );

        assertFalse( invokeEffectivePack200( mojo ) );
    }

    public void testUnavailableWarningLoggedOnlyOnce()
            throws Exception
    {
        if ( Pack200Support.isJdkPack200Available() )
        {
            return;
        }

        CountingLog log = new CountingLog();
        JnlpInlineMojo mojo = newMojo( log );
        setPack200( mojo, enabledConfig( true, false ) );

        assertFalse( invokeEffectivePack200( mojo ) );
        assertFalse( invokeEffectivePack200( mojo ) );
        assertEquals( 1, log.warnCount );
        assertTrue( log.lastWarnMessage.contains( "Pack200 has been enabled" ) );
    }

    private static JnlpInlineMojo newMojo()
            throws Exception
    {
        return newMojo( new SystemStreamLog() );
    }

    private static JnlpInlineMojo newMojo( Log log )
            throws Exception
    {
        JnlpInlineMojo mojo = new JnlpInlineMojo();
        setField( mojo, "log", log );
        return mojo;
    }

    private static Pack200Config enabledConfig( boolean enabled, boolean commonsCompress )
    {
        Pack200Config config = new Pack200Config();
        config.setEnabled( enabled );
        config.setCommonsCompressEnabled( commonsCompress );
        return config;
    }

    private static void setPack200( JnlpInlineMojo mojo, Pack200Config config )
            throws Exception
    {
        setField( mojo, "pack200", config );
    }

    private static boolean invokeEffectivePack200( JnlpInlineMojo mojo )
            throws Exception
    {
        Method method = AbstractBaseJnlpMojo.class.getDeclaredMethod( "isEffectivePack200" );
        method.setAccessible( true );
        return (Boolean) method.invoke( mojo );
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

    private static final class CountingLog
            implements Log
    {
        private int warnCount;

        private String lastWarnMessage;

        public boolean isDebugEnabled()
        {
            return false;
        }

        public void debug( CharSequence content )
        {
        }

        public void debug( Throwable error )
        {
        }

        public void debug( CharSequence content, Throwable error )
        {
        }

        public boolean isInfoEnabled()
        {
            return false;
        }

        public void info( CharSequence content )
        {
        }

        public void info( Throwable error )
        {
        }

        public void info( CharSequence content, Throwable error )
        {
        }

        public boolean isWarnEnabled()
        {
            return true;
        }

        public void warn( CharSequence content )
        {
            warnCount++;
            lastWarnMessage = content == null ? null : content.toString();
        }

        public void warn( Throwable error )
        {
            warnCount++;
        }

        public void warn( CharSequence content, Throwable error )
        {
            warn( content );
        }

        public boolean isErrorEnabled()
        {
            return false;
        }

        public void error( CharSequence content )
        {
        }

        public void error( Throwable error )
        {
        }

        public void error( CharSequence content, Throwable error )
        {
        }
    }
}
