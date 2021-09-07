package net.imprex.orebfuscator.chunk;

import net.imprex.orebfuscator.util.MinecraftVersion;

public final class ChunkCapabilities {

	// hasDynamicHeight >= 1.17
	// hasSimpleVarBitBuffer >= 1.16
	// hasBlockCount >= 1.14
	// hasDirectPaletteZeroLength < 1.13
	// hasLight < 1.14

	private static boolean hasDynamicHeight = MinecraftVersion.getMinorVersion() >= 17;
	private static boolean hasSimpleVarBitBuffer = MinecraftVersion.getMinorVersion() >= 16;
	private static boolean hasBlockCount = MinecraftVersion.getMinorVersion() >= 14;
	private static boolean hasDirectPaletteZeroLength = MinecraftVersion.getMinorVersion() < 13;
	private static boolean hasLightArray = MinecraftVersion.getMinorVersion() < 14;

	private ChunkCapabilities() {
	}

	public static boolean hasDynamicHeight() {
		return hasDynamicHeight;
	}

	public static boolean hasSimpleVarBitBuffer() {
		return hasSimpleVarBitBuffer;
	}

	public static boolean hasBlockCount() {
		return hasBlockCount;
	}

	public static boolean hasDirectPaletteZeroLength() {
		return hasDirectPaletteZeroLength;
	}

	public static boolean hasLightArray() {
		return hasLightArray;
	}

	public static int getExtraBytes(ChunkStruct chunkStruct) {
		int extraBytes = ChunkCapabilities.hasLightArray() ? 2048 : 0;
		if (chunkStruct.isOverworld) {
			extraBytes *= 2;
		}
		return extraBytes;
	}

	public static VarBitBuffer createVarBitBuffer(int bitsPerEntry, int size) {
		if (hasSimpleVarBitBuffer) {
			return new SimpleVarBitBuffer(bitsPerEntry, size);
		}
		return new CompactVarBitBuffer(bitsPerEntry, size);
	}
}
