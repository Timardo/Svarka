package ru.svarka.configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraftforge.fml.common.FMLCommonHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.svarka.SvarkaCommand;

public class SvarkaConfig extends ConfigBase{

        private final String HEADER = "This is the main configuration file for Svarka.\n";
        public static SvarkaConfig instance;
        /* ======================================================================== */

        public final BoolSetting remapPluginFile = new BoolSetting(this, "plugin-settings.default.remap-plugin-file", false, "Remap the plugin file (dev)");

        /* ======================================================================== */

        public SvarkaConfig(String fileName, String commandName)
        {
            super(fileName, commandName);
            init();
            instance = this;
        }

        public void init()
        {
            for(Field f : this.getClass().getFields())
            {
                if(Modifier.isFinal(f.getModifiers()) && Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()))
                {
                    try
                    {
                        Setting setting = (Setting) f.get(this);
                        if(setting == null) continue;
                        settings.put(setting.path, setting);
                    }
                    catch (ClassCastException e)
                    {

                    }
                    catch(Throwable t)
                    {
                        System.out.println("[Thermos] Failed to initialize a CauldronConfig setting.");
                        t.printStackTrace();
                    }

                }
            }
            load();
        }

        public void addCommands()
        {
            commands.put(this.commandName, new SvarkaCommand());
        }

        public void load()
        {
            try
            {
                config = YamlConfiguration.loadConfiguration(configFile);
                String header = HEADER + "\n";
                for (Setting toggle : settings.values())
                {
                    if (!toggle.description.equals(""))
                        header += "Setting: " + toggle.path + " Default: " + toggle.def + "   # " + toggle.description + "\n";

                    config.addDefault(toggle.path, toggle.def);
                    settings.get(toggle.path).setValue(config.getString(toggle.path));
                }
                config.options().header(header);
                config.options().copyDefaults(true);

                version = getInt("config-version", 1);
                set("config-version", 1);

                // TODO this.saveWorldConfigs();
                this.save();
            }
            catch (Exception ex)
            {
                FMLCommonHandler.instance().getMinecraftServerInstance().getServer().logSevere("Could not load " + this.configFile);
                ex.printStackTrace();
            }
        }
}

