package net.imprex.orebfuscator;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.async.AsyncFilterManager;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;

import net.imprex.orebfuscator.api.OrebfuscatorService;
import net.imprex.orebfuscator.cache.ChunkCache;
import net.imprex.orebfuscator.config.OrebfuscatorConfig;
import net.imprex.orebfuscator.obfuscation.ObfuscatorSystem;
import net.imprex.orebfuscator.proximityhider.ProximityHider;
import net.imprex.orebfuscator.proximityhider.ProximityListener;
import net.imprex.orebfuscator.proximityhider.ProximityPacketListener;
import net.imprex.orebfuscator.util.HeightAccessor;
import net.imprex.orebfuscator.util.OFCLogger;

public class Orebfuscator extends JavaPlugin implements Listener {

	public static final ThreadGroup THREAD_GROUP = new ThreadGroup("orebfuscator");

	private final Thread mainThread = Thread.currentThread();

	private OrebfuscatorConfig config;
	private UpdateSystem updateSystem;
	private ChunkCache chunkCache;
	private ObfuscatorSystem obfuscatorSystem;
	private ProximityHider proximityHider;
	private ProximityPacketListener proximityPacketListener;

	@Override
	public void onEnable() {
		try {
			// Check if protocolLib is enabled
			if (this.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
				OFCLogger.info("ProtocolLib is not found! Plugin cannot be enabled.");
				return;
			}

			// Check if HeightAccessor can be loaded
			HeightAccessor.ThisMethodIsUsedToInitializeStaticFields();

			// Load configurations
			this.config = new OrebfuscatorConfig(this);

			// Initialize metrics
			new MetricsSystem(this);

			// initialize update system and check for updates
			this.updateSystem = new UpdateSystem(this);

			// Load chunk cache
			this.chunkCache = new ChunkCache(this);

			// Load obfuscater
			this.obfuscatorSystem = new ObfuscatorSystem(this);

			// Load proximity hider
			this.proximityHider = new ProximityHider(this);
			if (this.config.proximityEnabled()) {
				this.proximityHider.start();

				this.proximityPacketListener = new ProximityPacketListener(this);

				this.getServer().getPluginManager().registerEvents(new ProximityListener(this), this);
			}

			// Load packet listener
			this.obfuscatorSystem.registerChunkListener();

			// Store formatted config
			this.config.store();
			
			// initialize service
			Bukkit.getServicesManager().register(
					OrebfuscatorService.class,
					new DefaultOrebfuscatorService(this),
					this, ServicePriority.Normal);
		} catch (Exception e) {
			OFCLogger.log(Level.SEVERE, "An error occurred while enabling plugin");
			OFCLogger.err(e);

			this.getServer().getPluginManager().registerEvent(PluginEnableEvent.class, this, EventPriority.NORMAL,
					this::onEnableFailed, this);
		}
	}

	@Override
	public void onDisable() {
		this.chunkCache.close();

		this.obfuscatorSystem.close();

		if (this.config.proximityEnabled()) {
			this.proximityPacketListener.unregister();
			this.proximityHider.close();
		}

		this.getServer().getScheduler().cancelTasks(this);

		NmsInstance.close();
		this.config = null;
	}

	public static final AtomicInteger SENDING_PACKETS = new AtomicInteger();
	public static final AtomicInteger SKIP_PACKETS = new AtomicInteger();
	public static final AtomicInteger PRE_PACKETS = new AtomicInteger();
	public static final AtomicInteger POST_PACKETS = new AtomicInteger();

	public static final AtomicInteger SIGNAL_SKIP = new AtomicInteger();
	public static final AtomicInteger SIGNAL_SKIP_POST = new AtomicInteger();
	public static final AtomicInteger SIGNAL_SENT = new AtomicInteger();
	public static final AtomicInteger SIGNAL_SENT_POST = new AtomicInteger();

	public static final AtomicInteger ON_POST_PACKET = new AtomicInteger();
	public static final AtomicInteger ON_CANCELLED_PACKET = new AtomicInteger();
	public static final AtomicInteger ON_EXPIRE_PACKET = new AtomicInteger();
	public static final AtomicInteger ON_PROCESSED_PACKET = new AtomicInteger();

	public static final AtomicInteger CACHE_REQUEST = new AtomicInteger();
	public static final AtomicInteger CACHE_HIT_MEMORY = new AtomicInteger();
	public static final AtomicInteger CACHE_HIT_DISK = new AtomicInteger();
	public static final AtomicInteger CACHE_MISS = new AtomicInteger();

	public static final AtomicInteger OBFUSCATE_REQUEST = new AtomicInteger();
	public static final AtomicInteger OBFUSCATE_DONE = new AtomicInteger();

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		System.out.println("ofc threads: \n\t\t" + 
				Thread.getAllStackTraces().keySet().stream()
				.map(Thread::getName)
				.collect(Collectors.joining(",\n\t\t")));

		System.out.println("ofc sending packet: " + SENDING_PACKETS.get());
		System.out.println("ofc skip packet: " + SKIP_PACKETS.get());
		System.out.println("ofc pre packet: " + PRE_PACKETS.get());
		System.out.println("ofc post packet: " + POST_PACKETS.get());
		System.out.println("ofc out packet: " + (SKIP_PACKETS.get() + POST_PACKETS.get()));
		
		System.out.println();

		System.out.println("ofc signal skip: " + SIGNAL_SKIP.get());
		System.out.println("ofc signal skip post: " + SIGNAL_SKIP_POST.get());
		System.out.println("ofc signal sent: " + SIGNAL_SENT.get());
		System.out.println("ofc signal sent post: " + SIGNAL_SENT_POST.get());

		System.out.println();
		
		System.out.println("ofc on post packet: " + ON_POST_PACKET.get());
		System.out.println("ofc on cancelled packet: " + ON_CANCELLED_PACKET.get());
		System.out.println("ofc on expire packet: " + ON_EXPIRE_PACKET.get());
		System.out.println("ofc on processed packet: " + ON_PROCESSED_PACKET.get());
		
		System.out.println();

		System.out.println("ofc cache request: " + CACHE_REQUEST.get());
		System.out.println("ofc cache hit memory: " + CACHE_HIT_MEMORY.get());
		System.out.println("ofc cache hit disk: " + CACHE_HIT_DISK.get());
		System.out.println("ofc cache miss: " + CACHE_MISS.get());
		System.out.println("ofc cache done: " + (CACHE_HIT_MEMORY.get() + CACHE_HIT_DISK.get() + CACHE_MISS.get()));
		
		System.out.println();

		System.out.println("ofc obfuscate request: " + OBFUSCATE_REQUEST.get());
		System.out.println("ofc obfuscate done: " + OBFUSCATE_DONE.get());
		
		if (sender instanceof Player) {
			Player player = (Player) sender;
			AsynchronousManager asynchronousManager = ProtocolLibrary.getProtocolManager().getAsynchronousManager();
			if (asynchronousManager instanceof AsyncFilterManager) {
				AsyncFilterManager manager = (AsyncFilterManager) asynchronousManager;

				Object client = manager.getSendingQueue(PacketEvent.fromClient(new Object(), null, player), false);
				Object server = manager.getSendingQueue(PacketEvent.fromServer(new Object(), null, player), false);

				if (client != null) {
					FieldAccessor clientField = Accessors.getFieldAccessor(client.getClass().getSuperclass(), PriorityBlockingQueue.class, true);
					PriorityBlockingQueue clientQueue = (PriorityBlockingQueue) clientField.get(client);
					System.out.println("ofc client: " + clientQueue.size());
				}

				if (server != null) {
					FieldAccessor serverField = Accessors.getFieldAccessor(server.getClass().getSuperclass(), PriorityBlockingQueue.class, true);
					PriorityBlockingQueue serverQueue = (PriorityBlockingQueue) serverField.get(server);
					System.out.println("ofc server: " + serverQueue.size());
				}
			}
		}

		return true;
	}

	public void onEnableFailed(Listener listener, Event event) {
		PluginEnableEvent enableEvent = (PluginEnableEvent) event;

		if (enableEvent.getPlugin() == this) {
			HandlerList.unregisterAll(listener);
			Bukkit.getPluginManager().disablePlugin(this);
		}
	}

	public boolean isMainThread() {
		return Thread.currentThread() == this.mainThread;
	}

	public OrebfuscatorConfig getOrebfuscatorConfig() {
		return this.config;
	}

	public UpdateSystem getUpdateSystem() {
		return updateSystem;
	}

	public ChunkCache getChunkCache() {
		return this.chunkCache;
	}

	public ObfuscatorSystem getObfuscatorSystem() {
		return obfuscatorSystem;
	}

	public ProximityHider getProximityHider() {
		return this.proximityHider;
	}

	public ProximityPacketListener getProximityPacketListener() {
		return this.proximityPacketListener;
	}
}