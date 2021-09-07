package net.imprex.orebfuscator.config;

import java.util.Map;

import org.bukkit.Material;

public interface ProximityConfig extends WorldConfig {

	int distance();

	boolean useFastGazeCheck();

	Iterable<Map.Entry<Material, Integer>> hiddenBlocks();
}
