package ru.svarka;

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
                //FMLRelaunchLog.info("Managed to load a deobfuscated Minecraft name- we are in a deobfuscated environment. Skipping runtime deobfuscation");
                deobfuscated = true;
            }
        }
        catch (IOException e1)
        {
        }
        return deobfuscated;
    }
}
