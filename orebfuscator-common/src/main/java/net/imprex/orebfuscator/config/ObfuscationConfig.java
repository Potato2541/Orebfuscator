package net.imprex.orebfuscator.config;

import org.bukkit.Material;

public interface ObfuscationConfig extends WorldConfig {

	Iterable<Material> hiddenBlocks();
}
