package org.spigotmc;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import ru.svarka.Svarka;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

public class WatchdogThread extends Thread
{

    private static WatchdogThread instance;
    private final long timeoutTime;
    private final boolean restart;
    private volatile long lastTick;
    private volatile boolean stopping;

    private WatchdogThread(long timeoutTime, boolean restart)
    {
        super( "Spigot Watchdog Thread" );
        this.timeoutTime = timeoutTime;
        this.restart = restart;
    }

    public static void doStart(int timeoutTime, boolean restart)
    {
        if ( instance == null )
        {
            instance = new WatchdogThread( timeoutTime * 1000L, restart );
            instance.start();
        }
    }

    public static void tick()
    {
        instance.lastTick = System.currentTimeMillis();
    }

    public static void doStop()
    {
        if ( instance != null )
        {
            instance.stopping = true;
        }
    }

    @Override
    public void run()
    {
        while ( !stopping )
        {
            //
            if ( lastTick != 0 && System.currentTimeMillis() > lastTick + timeoutTime )
            {
                Logger log = (Logger) Svarka.spigotLog;
                log.log( Level.ERROR, "The server has stopped responding!" );
                log.log( Level.ERROR, "Please report this to http://www.spigotmc.org/" );
                log.log( Level.ERROR, "Be sure to include ALL relevant console errors and Minecraft crash reports" );
                log.log( Level.ERROR, "Spigot version: " + Bukkit.getServer().getVersion() );
                //
                if(net.minecraft.world.World.haveWeSilencedAPhysicsCrash)
                {
                    log.log( Level.ERROR, "------------------------------" );
                    log.log( Level.ERROR, "During the run of the server, a physics stackoverflow was supressed" );
                    log.log( Level.ERROR, "near " + net.minecraft.world.World.blockLocation);
                }
                //
                log.log( Level.ERROR, "------------------------------" );
                log.log( Level.ERROR, "Server thread dump (Look for plugins here before reporting to Spigot!):" );
                dumpThread( ManagementFactory.getThreadMXBean().getThreadInfo( FMLCommonHandler.instance().getMinecraftServerInstance().getServer().primaryThread.getId(), Integer.MAX_VALUE ), log );
                log.log( Level.ERROR, "------------------------------" );
                //
                log.log( Level.ERROR, "Entire Thread Dump:" );
                ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
                for ( ThreadInfo thread : threads )
                {
                    dumpThread( thread, log );
                }
                log.log( Level.ERROR, "------------------------------" );

                if ( restart )
                {
                    RestartCommand.restart();
                }
                break;
            }

            try
            {
                sleep( 10000 );
            } catch ( InterruptedException ex )
            {
                interrupt();
            }
        }
    }

    private static void dumpThread(ThreadInfo thread, Logger log)
    {
        log.log( Level.ERROR, "------------------------------" );
        //
        log.log( Level.ERROR, "Current Thread: " + thread.getThreadName() );
        log.log( Level.ERROR, "\tPID: " + thread.getThreadId()
                + " | Suspended: " + thread.isSuspended()
                + " | Native: " + thread.isInNative()
                + " | State: " + thread.getThreadState() );
        if ( thread.getLockedMonitors().length != 0 )
        {
            log.log( Level.ERROR, "\tThread is waiting on monitor(s):" );
            for ( MonitorInfo monitor : thread.getLockedMonitors() )
            {
                log.log( Level.ERROR, "\t\tLocked on:" + monitor.getLockedStackFrame() );
            }
        }
        log.log( Level.ERROR, "\tStack:" );
        //
        for ( StackTraceElement stack : thread.getStackTrace() )
        {
            log.log( Level.ERROR, "\t\t" + stack );
        }
    }
}
