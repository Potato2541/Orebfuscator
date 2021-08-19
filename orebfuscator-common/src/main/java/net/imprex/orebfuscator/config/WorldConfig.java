package net.imprex.orebfuscator.config;

import org.bukkit.Material;

public interface WorldConfig {

	boolean isEnabled();

	Iterable<Material> hiddenBlocks();

	int nextRandomBlockId();
}
