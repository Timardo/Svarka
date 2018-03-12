package ru.svarka;

import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.io.IOException;

public class SvarkaUtils {
    private static boolean deobfuscated = false;

    public static boolean deobfuscatedEnvironment()
    {
        try
        {
            // Are we in a 'decompiled' environment?
            byte[] bs = ((net.minecraft.launchwrapper.LaunchClassLoader)SvarkaUtils.class.getClassLoader()).getClassBytes("net.minecraft.world.World");
            if (bs != null)
            {
                //Svara.LOG.info("Managed to load a deobfuscated Minecraft name- we are in a deobfuscated environment. Skipping runtime deobfuscation");
                deobfuscated = true;
            }
        }
        catch (IOException e1)
        {
        }
        return deobfuscated;
    }

    public static boolean migrateWorlds(String worldType, String oldName, String newName, String worldName) {
        boolean result = true;
        File newWorld = new File(new File(newName), worldName);
        File oldWorld = new File(new File(oldName), worldName);

        if ((!newWorld.isDirectory()) && (oldWorld.isDirectory()))
        {
            FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("---- Migration of old " + worldType + " folder required ----");
            FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("Svarka has moved back to using the Forge World structure, your " + worldType + " folder will be moved to a new location in order to operate correctly.");
            FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Cauldron in the future.");
            FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("Attempting to move " + oldWorld + " to " + newWorld + "...");

            if (newWorld.exists())
            {
                FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logSevere("A file or folder already exists at " + newWorld + "!");
                FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("---- Migration of old " + worldType + " folder failed ----");
                result = false;
            }
            else if (newWorld.getParentFile().mkdirs() || newWorld.getParentFile().exists())
            {
                FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);

                // Migrate world data
                try
                {
                    com.google.common.io.Files.move(oldWorld, newWorld);
                }
                catch (IOException exception)
                {
                    FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logSevere("Unable to move world data.");
                    exception.printStackTrace();
                    result = false;
                }
                try
                {
                    com.google.common.io.Files.copy(new File(oldWorld.getParent(), "level.dat"), new File(newWorld, "level.dat"));
                }
                catch (IOException exception)
                {
                    FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logSevere("Unable to migrate world level.dat.");
                }

                FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logInfo("---- Migration of old " + worldType + " folder complete ----");
            }
            else result = false;
        }
        return result;
    }
}
