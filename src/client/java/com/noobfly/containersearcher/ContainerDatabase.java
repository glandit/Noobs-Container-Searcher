package com.noobfly.containersearcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.noobfly.containersearcher.compat.Platform;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class ContainerDatabase {
	private static final Type DATA_TYPE = new TypeToken<Map<String, ContainerRecord>>() { }.getType();
	private static final Gson GSON = new GsonBuilder().create();
	private static final Path FILE = Platform.configDir()
		.resolve("noobs-container-searcher")
		.resolve("containers.json");

	private static final long DEFERRED_SAVE_INTERVAL_MS = 30_000L;

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "noobs-container-searcher-writer");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicReference<Map<String, ContainerRecord>> pendingWrite = new AtomicReference<>();
	private final Map<String, ContainerRecord> records = new LinkedHashMap<>();
	private boolean dirty;
	private long lastDeferredSave;

	public ContainerDatabase() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::writePending, "noobs-container-searcher-shutdown"));
	}

	public void load() {
		records.clear();
		if (!Files.exists(FILE)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, ContainerRecord> loaded = GSON.fromJson(reader, DATA_TYPE);
			if (loaded != null) {
				records.putAll(loaded);
			}
		} catch (Exception exception) {
			ContainerSearcherClient.LOGGER.error("Failed to read container database", exception);
			backupUnreadableFile();
		}
	}

	private static void backupUnreadableFile() {
		Path backup = FILE.resolveSibling(FILE.getFileName() + ".corrupt-" + System.currentTimeMillis());
		try {
			Files.move(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
			ContainerSearcherClient.LOGGER.warn("Corrupt database backed up as {}", backup);
		} catch (IOException exception) {
			ContainerSearcherClient.LOGGER.error("Failed to back up corrupt database", exception);
		}
	}

	public void put(ContainerRecord record) {
		records.put(key(record), record);
		save();
	}

	public void putDeferred(ContainerRecord record) {
		records.put(key(record), record);
		dirty = true;
	}

	// like put(record, obsoletePosition), saved later: for the many containers of a server sync
	public void putDeferred(ContainerRecord record, BlockPos obsoletePosition) {
		if (obsoletePosition != null) {
			records.remove(key(record.server, record.dimension,
				obsoletePosition.getX(), obsoletePosition.getY(), obsoletePosition.getZ()));
		}
		putDeferred(record);
	}

	public void flushNow() {
		if (dirty) {
			save();
		}
	}

	public void flush() {
		if (!dirty || System.currentTimeMillis() - lastDeferredSave < DEFERRED_SAVE_INTERVAL_MS) {
			return;
		}
		dirty = false;
		lastDeferredSave = System.currentTimeMillis();
		save();
	}

	public void put(ContainerRecord record, BlockPos obsoletePosition) {
		if (obsoletePosition != null) {
			records.remove(key(
				record.server,
				record.dimension,
				obsoletePosition.getX(),
				obsoletePosition.getY(),
				obsoletePosition.getZ()
			));
		}
		put(record);
	}

	public ContainerRecord find(String server, String dimension, BlockPos pos) {
		return records.get(key(server, dimension, pos.getX(), pos.getY(), pos.getZ()));
	}

	public List<ContainerRecord> allForServer(String server) {
		List<ContainerRecord> matches = new ArrayList<>();
		for (ContainerRecord record : records.values()) {
			if (server.equals(record.server)) {
				matches.add(record);
			}
		}
		return matches;
	}

	public void removeAll(Collection<ContainerRecord> removedRecords) {
		boolean changed = false;
		for (ContainerRecord record : removedRecords) {
			changed |= records.remove(key(record)) != null;
		}
		if (changed) {
			save();
		}
	}

	// moves everything saved under an old server key to a new one (once, when the key format changes)
	public void rekey(String from, String to) {
		List<ContainerRecord> moved = new ArrayList<>();
		for (ContainerRecord record : records.values()) {
			if (from.equals(record.server)) {
				moved.add(record);
			}
		}
		if (moved.isEmpty()) {
			return;
		}
		for (ContainerRecord record : moved) {
			records.remove(key(record));
		}
		for (ContainerRecord record : moved) {
			record.server = to;
			records.put(key(record), record);
		}
		save();
	}

	// every saved server/world key with its containers, in saved order
	public java.util.Map<String, List<ContainerRecord>> allServers() {
		java.util.Map<String, List<ContainerRecord>> grouped = new LinkedHashMap<>();
		for (ContainerRecord record : records.values()) {
			if (record.server != null) {
				grouped.computeIfAbsent(record.server, ignored -> new ArrayList<>()).add(record);
			}
		}
		return grouped;
	}

	public void clearServer(String server) {
		if (records.values().removeIf(record -> server.equals(record.server))) {
			save();
		}
	}

	public List<ContainerRecord> search(
		String server,
		String dimension,
		BlockPos playerPos,
		String itemId,
		boolean global
	) {
		List<ContainerRecord> matches = new ArrayList<>();
		for (ContainerRecord record : records.values()) {
			if (!server.equals(record.server) || !record.items.containsKey(itemId)) {
				continue;
			}
			if (!global) {
				if (!dimension.equals(record.dimension)) {
					continue;
				}
				double dx = record.x + 0.5D - playerPos.getX();
				double dy = record.y + 0.5D - playerPos.getY();
				double dz = record.z + 0.5D - playerPos.getZ();
				if (dx * dx + dy * dy + dz * dz > 100.0D * 100.0D) {
					continue;
				}
			}
			matches.add(record);
		}

		matches.sort((left, right) -> Double.compare(
			distanceSquared(left, playerPos),
			distanceSquared(right, playerPos)
		));
		return matches;
	}

	private void save() {
		dirty = false;
		lastDeferredSave = System.currentTimeMillis();
		pendingWrite.set(new LinkedHashMap<>(records));
		WRITER.execute(this::writePending);
	}

	private synchronized void writePending() {
		Map<String, ContainerRecord> snapshot = pendingWrite.getAndSet(null);
		if (snapshot == null) {
			return;
		}
		try {
			Files.createDirectories(FILE.getParent());
			Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporary)) {
				GSON.toJson(snapshot, DATA_TYPE, writer);
			}
			Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception exception) {
			ContainerSearcherClient.LOGGER.error("Failed to save container database", exception);
		}
	}

	private static String key(String server, String dimension, int x, int y, int z) {
		return server + "|" + dimension + "|" + x + "|" + y + "|" + z;
	}

	public static String keyOf(ContainerRecord record) {
		return key(record);
	}

	private static String key(ContainerRecord record) {
		if (record.entityUuid != null && !record.entityUuid.isBlank()) {
			return record.server + "|" + record.dimension + "|villager|" + record.entityUuid;
		}
		return key(record.server, record.dimension, record.x, record.y, record.z);
	}

	private static double distanceSquared(ContainerRecord record, BlockPos pos) {
		double dx = record.x + 0.5D - pos.getX();
		double dy = record.y + 0.5D - pos.getY();
		double dz = record.z + 0.5D - pos.getZ();
		return dx * dx + dy * dy + dz * dz;
	}
}
