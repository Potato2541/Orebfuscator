package net.imprex.orebfuscator.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import net.imprex.orebfuscator.NmsInstance;
import net.imprex.orebfuscator.util.OFCLogger;
import net.imprex.orebfuscator.util.WeightedRandom;

public class AbstractConfig {

	protected boolean enabled = false;

	protected final List<WorldMatcher> worldMatchers = new ArrayList<>();

	protected final Map<Material, Integer> randomMaterials = new LinkedHashMap<>();
	protected final WeightedRandom<Integer> weightedMaterials = new WeightedRandom<>();

	protected static void warnInvalidMaterial(String section, String path, String name) {
		OFCLogger.warn(String.format("config section '%s.%s' contains unknown block '%s'", section, path, name));
	}

	protected final void failMissingOrEmpty(ConfigurationSection section, String missingSection) {
		this.fail(String.format("config section '%s.%s' is missing or empty", section.getCurrentPath(), missingSection));
	}

	protected final void fail(String message) {
		this.enabled = false;
		OFCLogger.warn(message);
	}

	protected void serializeWorlds(ConfigurationSection section, String path) {
		section.getStringList(path).stream().map(WorldMatcher::parseMatcher).forEach(worldMatchers::add);

		if (this.worldMatchers.isEmpty()) {
			this.failMissingOrEmpty(section, path);
		}
	}

	protected void deserializeWorlds(ConfigurationSection section, String path) {
		section.set(path, worldMatchers.stream().map(WorldMatcher::deserialize).collect(Collectors.toList()));
	}

	protected void serializeRandomMaterials(ConfigurationSection section, String path) {
		ConfigurationSection materialSection = section.getConfigurationSection(path);
		if (materialSection == null) {
			return;
		}

		for (String name : materialSection.getKeys(false)) {
			Optional<Material> optional = NmsInstance.getMaterialByName(name);
			if (optional.isPresent()) {
				int weight = materialSection.getInt(name, 1);
				this.randomMaterials.put(optional.get(), weight);

				NmsInstance.getFirstBlockId(optional.get()).ifPresent(blockId -> {
					this.weightedMaterials.add(weight, blockId);
				});
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, name);
			}
		}

		if (this.randomMaterials.isEmpty()) {
			this.failMissingOrEmpty(section, path);
		}
	}

	protected void deserializeRandomMaterialList(ConfigurationSection section, String path) {
		ConfigurationSection materialSection = section.createSection(path);

		for (Material material : this.randomMaterials.keySet()) {
			Optional<String> optional = NmsInstance.getNameByMaterial(material);
			if (optional.isPresent()) {
				materialSection.set(optional.get(), this.randomMaterials.get(material));
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, material != null ? material.name() : null);
			}
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean matchesWorldName(String worldName) {
		for (WorldMatcher matcher : this.worldMatchers) {
			if (matcher.test(worldName)) {
				return true;
			}
		}
		return false;
	}

	public int nextRandomBlockId() {
		return this.weightedMaterials.next();
	}
}
