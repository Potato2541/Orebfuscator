package net.imprex.orebfuscator.cache;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.obfuscation.ObfuscatedChunk;
import net.imprex.orebfuscator.util.ChunkPosition;

public class ChunkCache {

	private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

	public static final byte[] hash(byte[] configHash, byte[] chunkData) {
		Hasher hasher = HASH_FUNCTION.newHasher();
		hasher.putBytes(configHash);
		hasher.putBytes(chunkData);
		return hasher.hash().asBytes();
	}

	private final Orebfuscator orebfuscator;
	private final CacheConfig cacheConfig;

	private final Cache<ChunkPosition, ObfuscatedChunk> cache;
	private final AsyncChunkSerializer serializer;

	private final ExecutorService cacheExecutor;

	public ChunkCache(Orebfuscator orebfuscator) {
		this.orebfuscator = orebfuscator;
		this.cacheConfig = orebfuscator.getOrebfuscatorConfig().cache();

		this.cacheExecutor = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
				pool -> {
			        ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			        worker.setName("ofc-cache-pool-" + worker.getPoolIndex());
			        return worker;
				}, null, true);

		this.cache = CacheBuilder.newBuilder().maximumSize(this.cacheConfig.maximumSize())
				.expireAfterAccess(this.cacheConfig.expireAfterAccess(), TimeUnit.MILLISECONDS)
				.removalListener(this::onRemoval).build();

		this.serializer = new AsyncChunkSerializer(orebfuscator);

		if (this.cacheConfig.enabled() && this.cacheConfig.deleteRegionFilesAfterAccess() > 0) {
			Bukkit.getScheduler().runTaskTimerAsynchronously(orebfuscator, new CacheCleanTask(orebfuscator), 0,
					3_600_000L);
		}
	}

	private void onRemoval(RemovalNotification<ChunkPosition, ObfuscatedChunk> notification) {
		if (notification.wasEvicted()) {
			this.serializer.write(notification.getKey(), notification.getValue());
		}
	}

	public CompletableFuture<ObfuscatedChunk> get(ChunkCacheRequest request) {
		Orebfuscator.CACHE_REQUEST.incrementAndGet();
		CompletableFuture<ObfuscatedChunk> future = new CompletableFuture<>();
		this.cacheExecutor.execute(() -> {
			ChunkPosition key = request.getKey();

			ObfuscatedChunk cacheChunk = this.cache.getIfPresent(key);
			if (request.isValid(cacheChunk)) {
				Orebfuscator.CACHE_HIT_MEMORY.incrementAndGet();
				future.complete(cacheChunk);
				return;
			}

			// check if disk cache entry is present and valid
			this.serializer.read(key).thenAcceptAsync(diskChunk -> {
				if (request.isValid(diskChunk)) {
					this.cache.put(key, diskChunk);
					Orebfuscator.CACHE_HIT_MEMORY.incrementAndGet();
					future.complete(diskChunk);
					return;
				}

				// create new entry no valid ones found
				request.obfuscate().thenAcceptAsync(chunk -> {
					this.cache.put(key, Objects.requireNonNull(chunk));
					future.complete(chunk);
				}, this.cacheExecutor);
			}, this.cacheExecutor);
		});
		return future;
	}

	public void invalidate(ChunkPosition key) {
		if (this.orebfuscator.isMainThread()) {
			this.cacheExecutor.execute(() -> {
				this.invalidate(key);
			});
		} else {
			this.cache.invalidate(key);
			this.serializer.write(key, null);
		}
	}

	public void close() {
		this.cache.asMap().entrySet().removeIf(entry -> {
			this.serializer.write(entry.getKey(), entry.getValue());
			return true;
		});
		this.cacheExecutor.shutdown();
		this.serializer.close();
	}
}
