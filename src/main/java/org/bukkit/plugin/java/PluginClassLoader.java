package org.bukkit.plugin.java;

import java.io.*;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.RemapperProcessor;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.repo.RuntimeRepo;
import net.md_5.specialsource.transformer.MavenShade;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.commons.lang.Validate;
import org.apache.logging.log4j.Level;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import ru.svarka.SvarkaRemapper;
import ru.svarka.SvarkaUtils;
import ru.svarka.Svarka;

/**
 * A ClassLoader for plugins, to allow shared classes across multiple plugins
 */
final class PluginClassLoader extends URLClassLoader {
    private final JavaPluginLoader loader;
    private final Map<String, Class<?>> classes = new HashMap<String, Class<?>>(); // Svarka - Threadsafe classloading
    private final PluginDescriptionFile description;
    private final File dataFolder;
    private final File file;
    JavaPlugin plugin; // Svarka - remove final
    private JavaPlugin pluginInit;
    private IllegalStateException pluginState;

    // Svarka start
    private JarRemapper remapper;     // class remapper for this plugin, or null
    private RemapperProcessor remapperProcessor; // secondary; for inheritance & remapping reflection
    private boolean debug;            // classloader debugging
    private int remapFlags = -1;
    private static Map<Integer,JarMapping> jarMappings = new HashMap<Integer,JarMapping>();
    private static final int F_GLOBAL_INHERIT   = 1 << 1;
    private static final int F_REMAP_OBCPRE     = 1 << 2;
    private static final int F_REMAP_NMS1102    = 1 << 3;
    private static final int F_REMAP_OBC1102    = 1 << 4;
    private static final int F_REMAP_NMSPRE_MASK= 0xffff0000;  // "unversioned" NMS plugin version
    // This trick bypasses Maven Shade's package rewriting when using String literals [same trick in jline]
    private static final String org_bukkit_craftbukkit = new String(new char[] {'o','r','g','/','b','u','k','k','i','t','/','c','r','a','f','t','b','u','k','k','i','t'});
    // Svarka end

    PluginClassLoader(final JavaPluginLoader loader, final ClassLoader parent, final PluginDescriptionFile description, final File dataFolder, final File file) throws InvalidPluginException, MalformedURLException {
        super(new URL[] {file.toURI().toURL()}, parent);
        Validate.notNull(loader, "Loader cannot be null");

        this.loader = loader;
        this.description = description;
        this.dataFolder = dataFolder;
        this.file = file;
        // Svarka start
        String pluginName = this.description.getName();
        // configure default remapper settings
        boolean useCustomClassLoader = FMLCommonHandler.instance().getMinecraftServerInstance().getServer().svarkaConfig.getBoolean("plugin-settings.default.custom-class-loader", true);
        boolean reflectFields = true; //TODO
        boolean reflectClass = true;
        boolean pluginInherit = true;
        boolean globalInherit = true;
        boolean allowFuture = true;
        boolean remapOBC1102 = true;
        boolean remapNMS1102 = true;
        boolean remapOBCPre = true;
        Svarka.bukkitLog.info("PluginClassLoader debugging enabled for "+pluginName);
        if(!useCustomClassLoader){
            remapper = null;
            return;
        }
        int flags = 0;
        if(remapNMS1102) flags |= F_REMAP_NMS1102;
        if (remapOBC1102) flags |= F_REMAP_OBC1102;
        if (remapOBCPre) flags |= F_REMAP_OBCPRE;
        if (globalInherit) flags |= F_GLOBAL_INHERIT;
        remapFlags = flags; // used in findClass0
        JarMapping jarMapping = getJarMapping(flags);
        // Load inheritance map
        if ((flags & F_GLOBAL_INHERIT) != 0) {
            if (debug) {
            	Svarka.bukkitLog.info("Enabling global inheritance remapping");
            }
            jarMapping.setInheritanceMap(loader.getGlobalInheritanceMap());
            jarMapping.setFallbackInheritanceProvider(new ClassLoaderProvider(this));
            }
            remapper = new SvarkaRemapper(jarMapping);
        if (pluginInherit || reflectFields || reflectClass) {
            remapperProcessor = new RemapperProcessor(
                    pluginInherit ? loader.getGlobalInheritanceMap() : null,
                    (reflectFields || reflectClass) ? jarMapping : null);
            remapperProcessor.setRemapReflectField(reflectFields);
            remapperProcessor.setRemapReflectClass(reflectClass);
            remapperProcessor.debug = debug;
            } else {
            remapperProcessor = null;
            }

        try {
            Class<?> jarClass;
            try {
                jarClass = Class.forName(description.getMain(), true, this);
            } catch (ClassNotFoundException ex) {
                throw new InvalidPluginException("Cannot find main class `" + description.getMain() + "'", ex);
            }

            Class<? extends JavaPlugin> pluginClass;
            try {
                pluginClass = jarClass.asSubclass(JavaPlugin.class);
            } catch (ClassCastException ex) {
                throw new InvalidPluginException("main class `" + description.getMain() + "' does not extend JavaPlugin", ex);
            }

            plugin = pluginClass.newInstance();
        } catch (IllegalAccessException ex) {
            throw new InvalidPluginException("No public constructor", ex);
        } catch (InstantiationException ex) {
            throw new InvalidPluginException("Abnormal plugin type", ex);
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, true);
    }
    /**
     * Get the "native" obfuscation version, from our Maven shading version.
     */
    public static String getNativeVersion() {
        // see https://github.com/mbax/VanishNoPacket/blob/master/src/main/java/org/kitteh/vanish/compat/NMSManager.java
        if (SvarkaUtils.deobfuscatedEnvironment()) return "v1_10_R1"; // support plugins in deobf environment
        final String packageName = org.bukkit.craftbukkit.CraftServer.class.getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.')  + 1);
    }
    /**
     * Load NMS mappings from CraftBukkit mc-dev to repackaged srgnames for FML runtime deobf
     *
     * @param jarMapping An existing JarMappings instance to load into
     * @param obfVersion CraftBukkit version with internal obfuscation counter identifier
     *                   >=1.4.7 this is the major version + R#. v1_4_R1=1.4.7, v1_5_R1=1.5, v1_5_R2=1.5.1..
     *                   For older versions (including pre-safeguard) it is the full Minecraft version number
     * @throws IOException
     */
    private void loadNmsMappings(JarMapping jarMapping, String obfVersion) throws IOException {
        Map<String, String> relocations = new HashMap<String, String>();
        // mc-dev jar to CB, apply version shading (aka plugin safeguard)
        relocations.put("net.minecraft.server", "net.minecraft.server." + obfVersion);

        // support for running 1.10.2 plugins in Svarka dev
        if (SvarkaUtils.deobfuscatedEnvironment() && obfVersion.equals("v1_10_R1"))
        {
            jarMapping.loadMappings(
                    new BufferedReader(new InputStreamReader(loader.getClass().getClassLoader().getResourceAsStream("mappings/"+obfVersion+"/cb2pkgmcp.srg"))),
                    new MavenShade(relocations),
                    null, false);

            jarMapping.loadMappings(
                    new BufferedReader(new InputStreamReader(loader.getClass().getClassLoader().getResourceAsStream("mappings/"+obfVersion+"/obf2pkgmcp.srg"))),
                    null, // no version relocation for obf
                    null, false);
            // resolve naming conflict in FML/CB
            jarMapping.methods.put("net/minecraft/server/"+obfVersion+"/PlayerConnection/getPlayer ()Lorg/bukkit/craftbukkit/entity/CraftPlayer;", "getPlayerB");
        }
        else
        {
            jarMapping.loadMappings(
                    new BufferedReader(new InputStreamReader(loader.getClass().getClassLoader().getResourceAsStream("mappings/"+obfVersion+"/cb2numpkg.srg"))),
                    new MavenShade(relocations),
                    null, false);

            if (obfVersion.equals("v1_10_R1")) {
                jarMapping.loadMappings(
                        new BufferedReader(new InputStreamReader(loader.getClass().getClassLoader().getResourceAsStream("mappings/"+obfVersion+"/obf2numpkg.srg"))),
                        null, // no version relocation for obf
                        null, false);
            }

            // resolve naming conflict in FML/CB
            jarMapping.methods.put("net/minecraft/server/"+obfVersion+"/PlayerConnection/getPlayer ()Lorg/bukkit/craftbukkit/"+getNativeVersion()+"/entity/CraftPlayer;", "getPlayerB");
        }
        // remap bouncycastle to Forge's included copy, not the vanilla obfuscated copy (not in Svarka), see #133
        //jarMapping.packages.put("net/minecraft/"+obfVersion+"/org/bouncycastle", "org/bouncycastle"); No longer needed
    }

    private JarMapping getJarMapping(int flags) {
        JarMapping jarMapping = jarMappings.get(flags);

        if (jarMapping != null) {
            if (debug) {
            	Svarka.bukkitLog.info("Mapping reused for "+Integer.toHexString(flags));
            }
            return jarMapping;
        }

        jarMapping = new JarMapping();
        try {
            jarMapping.packages.put("com/google/common", "guava20/com/google/common");
            jarMapping.packages.put(org_bukkit_craftbukkit + "/libs/com/google/gson", "com/google/gson"); // Handle Gson being in a "normal" place
            // Bukkit moves these packages to nms while we keep them in root so we must relocate them for plugins that rely on them
            jarMapping.packages.put("net/minecraft/util/io", "io");
            jarMapping.packages.put("net/minecraft/util/com", "com");
            jarMapping.packages.put("net/minecraft/util/gnu", "gnu");
            jarMapping.packages.put("net/minecraft/util/org", "org");

            if ((flags & F_REMAP_NMS1102) != 0) {
                loadNmsMappings(jarMapping, "v1_10_R1");
            }


            if ((flags & F_REMAP_OBC1102) != 0) {
                if (SvarkaUtils.deobfuscatedEnvironment())
                    jarMapping.packages.put(org_bukkit_craftbukkit+"/v1_10_R1", org_bukkit_craftbukkit);
                else jarMapping.packages.put(org_bukkit_craftbukkit+"/v1_10_R1", org_bukkit_craftbukkit+"/"+getNativeVersion());
            }


            if ((flags & F_REMAP_OBCPRE) != 0) {
                // enabling unversioned obc not currently compatible with versioned obc plugins (overmapped) -
                // admins should enable remap-obc-pre on a per-plugin basis, as needed
                // then map unversioned to current version
                jarMapping.packages.put(org_bukkit_craftbukkit+"/libs/org/objectweb/asm", "org/objectweb/asm"); // ?
                jarMapping.packages.put(org_bukkit_craftbukkit, org_bukkit_craftbukkit+"/"+getNativeVersion());
            }

            if ((flags & F_REMAP_NMSPRE_MASK) != 0) {
                String obfVersion;
                switch (flags & F_REMAP_NMSPRE_MASK)
                {
                    case 0x11020000: obfVersion = "v1_10_R1"; break;
                    default: throw new IllegalArgumentException("Invalid unversioned mapping flags: "+Integer.toHexString(flags & F_REMAP_NMSPRE_MASK)+" in "+Integer.toHexString(flags));
                }

                jarMapping.loadMappings(
                        new BufferedReader(new InputStreamReader(loader.getClass().getClassLoader().getResourceAsStream("mappings/" + obfVersion + "/cb2numpkg.srg"))),
                        null, // no version relocation!
                        null, false);
            }

            Svarka.bukkitLog.info("Mapping loaded "+jarMapping.packages.size()+" packages, "+jarMapping.classes.size()+" classes, "+jarMapping.fields.size()+" fields, "+jarMapping.methods.size()+" methods, flags "+Integer.toHexString(flags));

            JarMapping currentJarMapping = jarMappings.putIfAbsent(flags, jarMapping);
            return currentJarMapping == null ? jarMapping : currentJarMapping;
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
        // Svarka start - remap any calls for classes with packaged nms version
        if (name.startsWith("net.minecraft."))
        {
            JarMapping jarMapping = this.getJarMapping(remapFlags); // grab from SpecialSource
            String remappedClass = jarMapping.classes.get(name.replaceAll("\\.", "\\/")); // get remapped pkgmcp class name
            Class<?> clazz = ((net.minecraft.launchwrapper.LaunchClassLoader)FMLCommonHandler.instance().getMinecraftServerInstance().getServer().getClass().getClassLoader()).findClass(remappedClass);
            return clazz;
        }
        if (name.startsWith("org.bukkit.")) {
            if (debug) {
            	Svarka.bukkitLog.info("Unexpected plugin findClass on OBC/NMS: name="+name+", checkGlobal="+checkGlobal+"; returning not found");
            }
            throw new ClassNotFoundException(name);
        }
        // custom loader, if enabled, threadsafety
        synchronized (name.intern()) {
            Class<?> result = classes.get(name);

            if (result == null) {
                if (checkGlobal) {
                    result = loader.getClassByName(name); // Don't warn on deprecation, but maintain overridability
                }

                if (result == null) {
                    if (remapper == null) {
                        result = super.findClass(name);
                    } else {
                        result = remappedFindClass(name);
                    }

                    if (result != null) {
                        loader.setClass(name, result);
                    }
                }
                if (result != null) {
                    loader.setClass(name, result);
                    Class<?> old = classes.putIfAbsent(name, result);
                    if (old != null && old != result) {
                    	Svarka.bukkitLog.log(Level.ERROR, "Defined class " + name + " twice as different classes, " + result + " and " + old);
                        result = old;
                    }
                }
            }

            classes.put(name, result);
            return result;
        }
        // Svarka end
    }
    private Class<?> remappedFindClass(String name) throws ClassNotFoundException {
        Class<?> result = null;

        try {
            // Load the resource to the name
            String path = name.replace('.', '/').concat(".class");
            URL url = this.findResource(path);
            if (url != null) {
                InputStream stream = url.openStream();
                if (stream != null) {
                    byte[] bytecode = null;

                    // Reflection remap and inheritance extract
                    if (remapperProcessor != null) {
                        // add to inheritance map
                        bytecode = remapperProcessor.process(stream);
                        if (bytecode == null) stream = url.openStream();
                    }

                    /*if (bytecode == null) {
                        bytecode = Streams.readAll(stream);
                    }*/

                    // Remap the classes
                    byte[] remappedBytecode = remapper.remapClassFile(bytecode, RuntimeRepo.getInstance());

                    if (debug) {
                        File file = new File("remapped-plugin-classes/"+name+".class");
                        file.getParentFile().mkdirs();
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream(file);
                            fileOutputStream.write(remappedBytecode);
                            fileOutputStream.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }

                    // Define (create) the class using the modified byte code
                    // The top-child class loader is used for this to prevent access violations
                    // Set the codesource to the jar, not within the jar, for compatibility with
                    // plugins that do new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))
                    // instead of using getResourceAsStream - see https://github.com/MinecraftPortCentral/Cauldron-Plus/issues/75
                    JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection(); // parses only
                    URL jarURL = jarURLConnection.getJarFileURL();
                    CodeSource codeSource = new CodeSource(jarURL, new CodeSigner[0]);

                    result = this.defineClass(name, remappedBytecode, 0, remappedBytecode.length, codeSource);
                    if (result != null) {
                        // Resolve it - sets the class loader of the class
                        this.resolveClass(result);
                    }
                }
            }
        } catch (Throwable t) {
            if (debug) {
            	Svarka.bukkitLog.info("remappedFindClass("+name+") exception: "+t);
                t.printStackTrace();
            }
            throw new ClassNotFoundException("Failed to remap class "+name, t);
        }

        return result;
    }
    // Svarka end

    Set<String> getClasses() {
        return classes.keySet();
    }

    synchronized void initialize(JavaPlugin javaPlugin) {
        Validate.notNull(javaPlugin, "Initializing plugin cannot be null");
        Validate.isTrue(javaPlugin.getClass().getClassLoader() == this, "Cannot initialize plugin outside of this class loader");
        if (this.plugin != null || this.pluginInit != null) {
            throw new IllegalArgumentException("Plugin already initialized!", pluginState);
        }

        pluginState = new IllegalStateException("Initial initialization");
        this.pluginInit = javaPlugin;

        javaPlugin.init(loader, loader.server, description, dataFolder, file, this);
    }
}
