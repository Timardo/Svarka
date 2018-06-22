package org.spigotmc;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import ru.svarka.Svarka;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.List;

public class RestartCommand extends Command
{

    public RestartCommand(String name)
    {
        super( name );
        this.description = "Restarts the server";
        this.usageMessage = "/restart";
        this.setPermission( "bukkit.command.restart" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( testPermission( sender ) )
        {
            FMLCommonHandler.instance().getMinecraftServerInstance().processQueue.add(new Runnable()
            {
                @Override
                public void run()
                {
                    restart();
                }
            } );
        }
        return true;
    }

    public static void restart()
    {
        restart( new File( SpigotConfig.restartScript ) );
    }

    public static void restart(final File script)
    {
        AsyncCatcher.enabled = false; // Disable async catcher incase it interferes with us
        try
        {
            if ( script.isFile() )
            {
                Svarka.spigotLog.info( "Attempting to restart with " + SpigotConfig.restartScript );

                //Deny new logins

                DedicatedServer.allowPlayerLogins = false;

                // Disable Watchdog
                WatchdogThread.doStop();

                // Kick all players
                for ( EntityPlayerMP p : (List<EntityPlayerMP>) FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList() )
                {
                    p.connection.kickPlayerFromServer(SpigotConfig.restartMessage);
                    p.connection.netManager.isChannelOpen();
                }
                // Give the socket a chance to send the packets
                try
                {
                    Thread.sleep( 100 );
                } catch ( InterruptedException ex )
                {
                }
                // Close the socket so we can rebind with the new process
                FMLCommonHandler.instance().getMinecraftServerInstance().getNetworkSystem().terminateEndpoints();

                // Give time for it to kick in
                try
                {
                    Thread.sleep( 100 );
                } catch ( InterruptedException ex )
                {
                }

                // Actually shutdown
                try
                {
                    Bukkit.shutdown();
                } catch ( Throwable t )
                {
                }

                // This will be done AFTER the server has completely halted
                Thread shutdownHook = new Thread()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            String os = System.getProperty( "os.name" ).toLowerCase(java.util.Locale.ENGLISH);
                            if ( os.contains( "win" ) )
                            {
                                Runtime.getRuntime().exec( "cmd /c start " + script.getPath() );
                            } else
                            {
                                Runtime.getRuntime().exec( new String[]
                                {
                                    "sh", script.getPath()
                                } );
                            }
                        } catch ( Exception e )
                        {
                            e.printStackTrace();
                        }
                    }
                };

                shutdownHook.setDaemon( true );
                Runtime.getRuntime().addShutdownHook( shutdownHook );
            } else
            {
            	Svarka.spigotLog.info( "Startup script '" + SpigotConfig.restartScript + "' does not exist! Stopping server." );

                // Actually shutdown
                try
                {
                    FMLCommonHandler.instance().exitJava(0,false);
                } catch ( Throwable t )
                {
                }
            }
            System.exit( 0 );
        } catch ( Exception ex )
        {
            ex.printStackTrace();
        }
    }
}
