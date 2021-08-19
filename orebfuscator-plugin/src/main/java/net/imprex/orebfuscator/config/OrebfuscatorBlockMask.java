package net.imprex.orebfuscator.config;

import java.util.Map.Entry;

import org.bukkit.Material;

import net.imprex.orebfuscator.NmsInstance;

public class OrebfuscatorBlockMask implements BlockMask {

	private static final OrebfuscatorBlockMask EMPTY_MASK = new OrebfuscatorBlockMask(null, null);

	static OrebfuscatorBlockMask create(OrebfuscatorWorldConfig worldConfig, OrebfuscatorProximityConfig proximityConfig) {
		if ((worldConfig != null && worldConfig.isEnabled()) || (proximityConfig != null && proximityConfig.isEnabled())) {
			return new OrebfuscatorBlockMask(worldConfig, proximityConfig);
		}
		return EMPTY_MASK;
	}

	private final int[] blockMask = new int[NmsInstance.getMaterialSize()];

	private OrebfuscatorBlockMask(OrebfuscatorWorldConfig worldConfig, OrebfuscatorProximityConfig proximityConfig) {
		if (worldConfig != null && worldConfig.isEnabled()) {
			for (Material material : worldConfig.hiddenBlocks()) {
				this.setBlockBits(material, FLAG_OBFUSCATE);
			}
		}
		if (proximityConfig != null && proximityConfig.isEnabled()) {
			for (Entry<Material, Integer> entry : proximityConfig.hiddenBlocks()) {
				this.setBlockBits(entry.getKey(), entry.getValue());
			}
		}
	}

	private void setBlockBits(Material material, int bits) {
		for (int blockId : NmsInstance.getBlockIds(material)) {
			int blockMask = this.blockMask[blockId] | bits;

			if (NmsInstance.isTileEntity(blockId)) {
				blockMask |= FLAG_TILE_ENTITY;
			}

			this.blockMask[blockId] = blockMask;
		}
	}

	@Override
	public int mask(int blockId) {
		return this.blockMask[blockId];
	}

	@Override
	public int mask(int blockId, int y) {
		int blockMask = this.blockMask[blockId];
		if (HideCondition.match(blockMask, y)) {
			blockMask |= FLAG_PROXIMITY;
		}
		return blockMask;
	}
}
