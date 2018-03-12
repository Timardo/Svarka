package ru.svarka;

import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spigotmc.RestartCommand;

public class Svarka {
	public static final Logger LOG = LogManager.getLogger("Svarka");
	public static final Logger spigotLog = LogManager.getLogger("Spigot");
	public static final Logger bukkitLog = LogManager.getLogger("Bukkit");
	public static final boolean DEBUG = true;
	private static final String versions = "0.0.3";
	public static void debug(String str) {
		if(DEBUG) {
			LOG.info(str);
		}
	}

	public static String getVersions(){
		return versions;
	}

    public static void restart() {
		RestartCommand.restart();
		LOG.info("Try restart...");
    }
}
