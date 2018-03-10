package ru.svarka;

import net.minecraftforge.fml.common.FMLCommonHandler;

import org.apache.logging.log4j.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.svarka.configuration.BoolSetting;
import ru.svarka.configuration.ConfigBase;
import ru.svarka.configuration.Setting;

public class SvarkaConfig extends ConfigBase {
    public BoolSetting commandEnable = new BoolSetting(this, "command.enable", true, "Enable Thermos command");

    public SvarkaConfig() {
        super("svarka.yml", "svarka");
        register(commandEnable);
        load();
    }

    private void register(Setting<?> setting) {
        settings.put(setting.path, setting);
    }

    @Override
    public void registerCommands() {
        if (commandEnable.getValue()) {
            super.registerCommands();
            Svarka.debug("Register Command!");
        }
    }

    @Override
    protected void addCommands() {
        commands.put(commandName, new SvarkaCommand());
        Svarka.debug("Add Command!");
    }

    @Override
    protected void load() {
        try {
            config = YamlConfiguration.loadConfiguration(configFile);
            String header = "";
            for (Setting<?> toggle : settings.values()) {
                if (!toggle.description.equals(""))
                    header += "Setting: " + toggle.path + " Default: "
                            + toggle.def + " # " + toggle.description + "\n";

                config.addDefault(toggle.path, toggle.def);
                settings.get(toggle.path).setValue(
                        config.getString(toggle.path));
            }
            config.options().header(header);
            config.options().copyDefaults(true);
            save();
        } catch (Exception ex) {
            Svarka.LOG.log(Level.ERROR, 
                    "Could not load " + this.configFile);
            ex.printStackTrace();
        }
    }
}
