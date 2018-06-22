// 
// Decompiled by Procyon v0.5.30
// 

package org.bukkit.craftbukkit.util;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.Level;
import org.bukkit.Bukkit;
import ru.svarka.Svarka;

public final class Versioning
{
    public static String getBukkitVersion() {
        /*String result = "Unknown-Version";
        final InputStream stream = Bukkit.class.getClassLoader().getResourceAsStream("META-INF/maven/org.bukkit/bukkit/pom.properties");
        final Properties properties = new Properties();
        if (stream != null) {
            try {
                properties.load(stream);
                result = properties.getProperty("version");
            }
            catch (IOException ex) {
                Svarka.bukkitLog.log(Level.ERROR, "Could not get Bukkit version!", ex);
            }
        }
        return result;*/
        return "1.10.2-R0.1-SNAPSHOT"; // return current Bukkit API version used
    }
}
