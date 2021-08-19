package net.imprex.orebfuscator.config;

import java.util.Map;

import org.bukkit.Material;

public interface ProximityConfig {

	boolean isEnabled();

	int distance();

	int distanceSquared();

	boolean useFastGazeCheck();

	Iterable<Map.Entry<Material, Integer>> hiddenBlocks();

	int nextRandomBlockId();
}
