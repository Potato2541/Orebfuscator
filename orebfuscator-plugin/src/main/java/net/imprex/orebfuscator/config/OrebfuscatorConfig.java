package net.imprex.orebfuscator.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.Plugin;

import net.imprex.orebfuscator.NmsInstance;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.util.MinecraftVersion;
import net.imprex.orebfuscator.util.OFCLogger;

public class OrebfuscatorConfig implements Config {

	private static final int CONFIG_VERSION = 1;

	private final OrebfuscatorGeneralConfig generalConfig = new OrebfuscatorGeneralConfig();
	private final OrebfuscatorCacheConfig cacheConfig = new OrebfuscatorCacheConfig();

	private final List<OrebfuscatorWorldConfig> worldConfigs = new ArrayList<>();
	private final List<OrebfuscatorProximityConfig> proximityConfigs = new ArrayList<>();

	private final Map<World, OrebfuscatorConfig.WorldEntry> worldToEntry = new WeakHashMap<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	private final Plugin plugin;

	private byte[] hash;

	public OrebfuscatorConfig(Plugin plugin) {
		this.plugin = plugin;

		this.load();
	}

	public void load() {
		this.createConfigIfNotExist();
		this.plugin.reloadConfig();
		this.serialize(this.plugin.getConfig());
	}

	public void store() {
		ConfigurationSection section = this.plugin.getConfig();
		for (String path : section.getKeys(false)) {
			section.set(path, null);
		}

		this.deserialize(section);
		this.plugin.saveConfig();
	}

	private void createConfigIfNotExist() {
		Path dataFolder = this.plugin.getDataFolder().toPath();
		Path path = dataFolder.resolve("config.yml");

		if (Files.notExists(path)) {
			try {
				String configVersion = MinecraftVersion.getMajorVersion() + "." + MinecraftVersion.getMinorVersion();

				if (Files.notExists(dataFolder)) {
					Files.createDirectories(dataFolder);
				}

				Files.copy(Orebfuscator.class.getResourceAsStream("/resources/config-" + configVersion + ".yml"), path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		this.hash = this.calculateHash(path);
	}

	private byte[] calculateHash(Path path) {
		try {
			MessageDigest md5Digest = MessageDigest.getInstance("MD5");
			md5Digest.update(MinecraftVersion.getNmsVersion().getBytes(StandardCharsets.UTF_8));
			return md5Digest.digest(Files.readAllBytes(path));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return new byte[0];
	}

	private void serialize(ConfigurationSection section) {
		if (section.getInt("version", -1) != CONFIG_VERSION) {
			throw new RuntimeException("config is not up to date, please delete your config");
		}

		this.worldConfigs.clear();
		this.proximityConfigs.clear();
		this.worldToEntry.clear();

		ConfigurationSection generalSection = section.getConfigurationSection("general");
		if (generalSection != null) {
			this.generalConfig.serialize(generalSection);
		} else {
			OFCLogger.warn("config section 'general' is missing, using default one");
		}

		ConfigurationSection cacheSection = section.getConfigurationSection("cache");
		if (cacheSection != null) {
			this.cacheConfig.serialize(cacheSection);
		} else {
			OFCLogger.warn("config section 'cache' is missing, using default one");
		}

		NmsInstance.close();
		NmsInstance.initialize(this);

		ConfigParser.serializeSectionList(section, "world").stream()
				.map(OrebfuscatorWorldConfig::new)
				.forEach(this.worldConfigs::add);
		if (this.worldConfigs.isEmpty()) {
			OFCLogger.warn("config section 'world' is missing or empty");
		}

		ConfigParser.serializeSectionList(section, "proximity").stream()
				.map(OrebfuscatorProximityConfig::new)
				.forEach(this.proximityConfigs::add);
		if (this.proximityConfigs.isEmpty()) {
			OFCLogger.warn("config section 'proximity' is missing or empty");
		}

		for (World world : Bukkit.getWorlds()) {
			this.worldToEntry.put(world, new WorldEntry(world));
		}
	}

	private void deserialize(ConfigurationSection section) {
		section.set("version", CONFIG_VERSION);

		this.generalConfig.deserialize(section.createSection("general"));
		this.cacheConfig.deserialize(section.createSection("cache"));

		List<ConfigurationSection> worldSectionList = new ArrayList<>();
		for (OrebfuscatorWorldConfig worldConfig : this.worldConfigs) {
			ConfigurationSection worldSection = new MemoryConfiguration();
			worldConfig.deserialize(worldSection);
			worldSectionList.add(worldSection);
		}
		section.set("world", worldSectionList);

		List<ConfigurationSection> proximitySectionList = new ArrayList<>();
		for (OrebfuscatorProximityConfig proximityConfig : this.proximityConfigs) {
			ConfigurationSection proximitySection = new MemoryConfiguration();
			proximityConfig.deserialize(proximitySection);
			proximitySectionList.add(proximitySection);
		}
		section.set("proximity", proximitySectionList);
	}

	private WorldEntry getWorldEntry(World world) {
		this.lock.readLock().lock();
		try {
			WorldEntry worldEntry = this.worldToEntry.get(Objects.requireNonNull(world));
			if (worldEntry != null) {
				return worldEntry;
			}
		} finally {
			this.lock.readLock().unlock();
		}

		WorldEntry worldEntry = new WorldEntry(world);
		this.lock.writeLock().lock();
		try {
			this.worldToEntry.putIfAbsent(world, worldEntry);
			return this.worldToEntry.get(world);
		} finally {
			this.lock.writeLock().unlock();
		}
	}

	@Override
	public GeneralConfig general() {
		return this.generalConfig;
	}

	@Override
	public CacheConfig cache() {
		return this.cacheConfig;
	}

	@Override
	public BlockMask blockMask(World world) {
		return this.getWorldEntry(world).blockMask;
	}

	@Override
	public boolean needsObfuscation(World world) {
		WorldEntry worldEntry = this.getWorldEntry(world);
		WorldConfig worldConfig = worldEntry.worldConfig;
		ProximityConfig proximityConfig = worldEntry.proximityConfig;
		return worldConfig != null && worldConfig.isEnabled() || proximityConfig != null && proximityConfig.isEnabled();
	}

	@Override
	public OrebfuscatorWorldConfig world(World world) {
		return this.getWorldEntry(world).worldConfig;
	}

	@Override
	public boolean proximityEnabled() {
		for (ProximityConfig proximityConfig : this.proximityConfigs) {
			if (proximityConfig.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ProximityConfig proximity(World world) {
		return this.getWorldEntry(world).proximityConfig;
	}

	@Override
	public byte[] hash() {
		return hash;
	}

	public boolean usesFastGaze() {
		for (ProximityConfig config : this.proximityConfigs) {
			if (config.useFastGazeCheck()) {
				return true;
			}
		}
		return false;
	}

	private class WorldEntry {

		private final OrebfuscatorWorldConfig worldConfig;
		private final OrebfuscatorProximityConfig proximityConfig;
		private final OrebfuscatorBlockMask blockMask;

		public WorldEntry(World world) {
			String worldName = world.getName();

			this.worldConfig = findConfig(worldConfigs.stream(), worldName, "world");
			this.proximityConfig = findConfig(proximityConfigs.stream(), worldName, "proximity");

			this.blockMask = OrebfuscatorBlockMask.create(worldConfig, proximityConfig);
		}

		private <T extends AbstractConfig> T findConfig(Stream<? extends T> configs, String worldName, String configName) {
			List<T> matchingConfigs = configs
					.filter(config -> config.matchesWorldName(worldName))
					.collect(Collectors.toList());

			if (matchingConfigs.size() > 1) {
				OFCLogger.warn(String.format("world '%s' has more than one %s config choosing first one", worldName, configName));
			}

			return matchingConfigs.size() > 0 ? matchingConfigs.get(0) : null;
		}
	}
}
