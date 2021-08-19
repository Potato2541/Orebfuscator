package net.imprex.orebfuscator.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import net.imprex.orebfuscator.NmsInstance;

public class OrebfuscatorProximityConfig extends AbstractConfig implements ProximityConfig {

	private int distance;
	private int distanceSquared;
	private boolean useFastGazeCheck;

	private int defaultBlockFlags = (HideCondition.MATCH_ALL | BlockMask.FLAG_USE_BLOCK_BELOW);

	private Map<Material, Integer> hiddenBlocks = new LinkedHashMap<>();

	OrebfuscatorProximityConfig(ConfigurationSection section) {
		this.enabled = section.getBoolean("enabled", true);
		this.serializeWorlds(section, "worlds");

		this.distance = section.getInt("distance", 8);
		this.distanceSquared = this.distance * this.distance;
		if (this.distance < 1) {
			this.fail("distance must be higher than zero");
		}
		this.useFastGazeCheck = section.getBoolean("useFastGazeCheck", true);

		int defaultY = section.getInt("defaults.y", 0);
		boolean defaultAbove = section.getBoolean("defaults.above", true);
		this.defaultBlockFlags = HideCondition.create(defaultY, defaultAbove);
		if (section.getBoolean("defaults.useBlockBelow", true)) {
			this.defaultBlockFlags |= BlockMask.FLAG_USE_BLOCK_BELOW;
		}

		this.serializeHiddenBlocks(section, "hiddenBlocks");
		this.serializeRandomMaterials(section, "randomBlocks");
	}

	private void serializeHiddenBlocks(ConfigurationSection section, String path) {
		ConfigurationSection materialSection = section.getConfigurationSection(path);
		if (materialSection == null) {
			return;
		}

		for (String name : materialSection.getKeys(false)) {
			Optional<Material> optional = NmsInstance.getMaterialByName(name);
			if (optional.isPresent()) {
				int blockFlags = this.defaultBlockFlags;

				// parse block specific height condition
				if (materialSection.isInt(name + ".y") && materialSection.isBoolean(name + ".above")) {
					blockFlags = HideCondition.remove(blockFlags);
					blockFlags |= HideCondition.create(materialSection.getInt(name + ".y"),
							materialSection.getBoolean(name + ".above"));
				}

				// parse block specific flags
				if (materialSection.isBoolean(name + ".useBlockBelow")) {
					if (materialSection.getBoolean(name + ".useBlockBelow")) {
						blockFlags |= BlockMask.FLAG_USE_BLOCK_BELOW;
					} else {
						blockFlags &= ~BlockMask.FLAG_USE_BLOCK_BELOW;
					}
				}

				this.hiddenBlocks.put(optional.get(), blockFlags);
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, name);
			}
		}

		if (this.hiddenBlocks.isEmpty()) {
			this.failMissingOrEmpty(section, path);
		}
	}

	protected void deserialize(ConfigurationSection section) {
		section.set("enabled", this.enabled);
		this.deserializeWorlds(section, "worlds");
		section.set("distance", this.distance);
		section.set("useFastGazeCheck", this.useFastGazeCheck);

		section.set("defaults.y", HideCondition.getY(this.defaultBlockFlags));
		section.set("defaults.above", HideCondition.getAbove(this.defaultBlockFlags));
		section.set("defaults.useBlockBelow", BlockMask.isUseBlockBelowBitSet(this.defaultBlockFlags));

		this.deserializeHiddenBlocks(section, "hiddenBlocks");
		this.deserializeRandomMaterialList(section, "randomBlocks");
	}

	private void deserializeHiddenBlocks(ConfigurationSection section, String path) {
		ConfigurationSection parentSection = section.createSection(path);

		for (Material material : this.hiddenBlocks.keySet()) {
			Optional<String> optional = NmsInstance.getNameByMaterial(material);
			if (optional.isPresent()) {
				ConfigurationSection childSection = parentSection.createSection(optional.get());

				int blockFlags = this.hiddenBlocks.get(material);
				if (!HideCondition.equals(blockFlags, this.defaultBlockFlags)) {
					childSection.set("y", HideCondition.getY(blockFlags));
					childSection.set("above", HideCondition.getAbove(blockFlags));
				}

				if (BlockMask.isUseBlockBelowBitSet(blockFlags) != BlockMask.isUseBlockBelowBitSet(this.defaultBlockFlags)) {
					childSection.set("useBlockBelow", BlockMask.isUseBlockBelowBitSet(blockFlags));
				}
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, material != null ? material.name() : null);
			}
		}
	}

	@Override
	public int distance() {
		return this.distance;
	}

	@Override
	public int distanceSquared() {
		return this.distanceSquared;
	}

	@Override
	public boolean useFastGazeCheck() {
		return this.useFastGazeCheck;
	}

	@Override
	public Iterable<Map.Entry<Material, Integer>> hiddenBlocks() {
		return this.hiddenBlocks.entrySet();
	}
}
