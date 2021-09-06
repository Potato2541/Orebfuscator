package net.imprex.orebfuscator.config;

public interface WorldConfig {

	boolean isEnabled();

	boolean matchesWorldName(String worldName);

	int nextRandomBlockId();

}
