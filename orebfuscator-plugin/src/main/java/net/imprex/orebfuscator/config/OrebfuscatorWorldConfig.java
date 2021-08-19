package net.imprex.orebfuscator.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import net.imprex.orebfuscator.NmsInstance;

public class OrebfuscatorWorldConfig extends AbstractConfig implements WorldConfig {

	private final Set<Material> hiddenBlocks = new LinkedHashSet<>();

	OrebfuscatorWorldConfig(ConfigurationSection section) {
		this.enabled = section.getBoolean("enabled", true);
		this.serializeWorlds(section, "worlds");
		this.serializeMaterials(section, "hiddenBlocks");
		this.serializeRandomMaterials(section, "randomBlocks");
	}

	private void serializeMaterials(ConfigurationSection section, String path) {
		for (String materialName : section.getStringList(path)) {
			Optional<Material> optional = NmsInstance.getMaterialByName(materialName);
			if (optional.isPresent()) {
				this.hiddenBlocks.add(optional.get());
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, materialName);
			}
		}

		if (this.hiddenBlocks.isEmpty()) {
			this.failMissingOrEmpty(section, path);
		}
	}

	void deserialize(ConfigurationSection section) {
		section.set("enabled", this.enabled);
		this.deserializeWorlds(section, "worlds");
		this.deserializeMaterials(section, "hiddenBlocks");
		this.deserializeRandomMaterialList(section, "randomBlocks");
	}

	private void deserializeMaterials(ConfigurationSection section, String path) {
		List<String> materialNames = new ArrayList<>();

		for (Material material : this.hiddenBlocks) {
			Optional<String> optional = NmsInstance.getNameByMaterial(material);
			if (optional.isPresent()) {
				materialNames.add(optional.get());
			} else {
				warnInvalidMaterial(section.getCurrentPath(), path, material != null ? material.name() : null);
			}
		}

		section.set(path, materialNames);
	}

	@Override
	public Iterable<Material> hiddenBlocks() {
		return this.hiddenBlocks;
	}
}
