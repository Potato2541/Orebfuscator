package net.imprex.orebfuscator.obfuscation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.async.AsyncListenerHandler;
import com.comphenix.protocol.async.AsyncMarker;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketPostListener;

import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.chunk.ChunkStruct;
import net.imprex.orebfuscator.proximityhider.ProximityPlayerManager;

public class AsyncChunkListener extends AbstractChunkListener implements PacketPostListener {

	private final AsynchronousManager asynchronousManager;
	private final AsyncListenerHandler asyncListenerHandler;

	private final ProximityPlayerManager proximityManager;

	public AsyncChunkListener(Orebfuscator orebfuscator) {
		super(orebfuscator);

		this.asynchronousManager = ProtocolLibrary.getProtocolManager().getAsynchronousManager();
		this.asyncListenerHandler = this.asynchronousManager.registerAsyncHandler(this);
		this.asyncListenerHandler.start(orebfuscator.getOrebfuscatorConfig().cache().protocolLibThreads());

		this.proximityManager = orebfuscator.getProximityHider().getPlayerManager();
	}

	@Override
	public void unregister() {
		this.asynchronousManager.unregisterAsyncHandler(this.asyncListenerHandler);
	}

	@Override
	protected void skipChunkForProcessing(PacketEvent event) {
		event.getNetworkMarker().addPostListener(this);
		Orebfuscator.SIGNAL_SKIP.incrementAndGet();
		this.asynchronousManager.signalPacketTransmission(event);
		Orebfuscator.SIGNAL_SKIP_POST.incrementAndGet();
	}

	@Override
	protected void preChunkProcessing(PacketEvent event, ChunkStruct struct) {
		event.getNetworkMarker().addPostListener(this);
		event.getAsyncMarker().incrementProcessingDelay();
	}

	@Override
	protected void postChunkProcessing(PacketEvent event, ChunkStruct struct, ObfuscatedChunk chunk) {
		Player player = event.getPlayer();
		this.proximityManager.addAndLockChunk(player, struct.chunkX, struct.chunkZ, chunk.getProximityBlocks());

		Bukkit.getScheduler().runTask(this.plugin, () -> {
			AsyncMarker marker = event.getAsyncMarker();
			if (marker.getQueuedSendingIndex() != marker.getNewSendingIndex() && !marker.hasExpired()) {
				Orebfuscator.ON_EXPIRE_PACKET.incrementAndGet();
			}

			Orebfuscator.SIGNAL_SENT.incrementAndGet();
			this.asynchronousManager.signalPacketTransmission(event);
			Orebfuscator.SIGNAL_SENT_POST.incrementAndGet();
			
			if (event.isCancelled()) {
				Orebfuscator.ON_CANCELLED_PACKET.incrementAndGet();
			}
			if (marker.isProcessed()) {
				Orebfuscator.ON_PROCESSED_PACKET.incrementAndGet();
			}
			
			this.proximityManager.unlockChunk(player, struct.chunkX, struct.chunkZ);
		});
	}

	@Override
	public void onPostEvent(PacketEvent event) {
		Orebfuscator.ON_POST_PACKET.incrementAndGet();
	}
}
