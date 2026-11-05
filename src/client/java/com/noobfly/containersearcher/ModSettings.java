package com.noobfly.containersearcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noobfly.containersearcher.compat.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = Platform.configDir()
		.resolve("noobs-container-searcher")
		.resolve("settings.json");

	private static volatile ModSettings instance;

	public static final class Location {
		public String server;
		public String dimension;
		public String name;
		public int x;
		public int y;
		public int z;
		public int radius = 100;
		public int color;
		public String item = "minecraft:compass";
	}

	public boolean itemDisplay = true;
	public boolean locationColors = true;
	public boolean trackChanges = true;
	public boolean targetArrows = true;
	public int highlightSeconds = 60;
	// which result buttons show, at least one stays on
	public boolean locateOneButton = true;
	public boolean locateAllButton = true;
	public boolean locateFireworks = false;
	public List<Location> locations = new ArrayList<>();
	public List<String> rerollSelection = new ArrayList<>();
	public List<String> excludedContainerTypes = new ArrayList<>();
	private transient int selectionRevision;

	private ModSettings() {
	}

	public static ModSettings get() {
		ModSettings current = instance;
		if (current == null) {
			synchronized (ModSettings.class) {
				current = instance;
				if (current == null) {
					current = load();
					instance = current;
				}
			}
		}
		return current;
	}

	public static int highlightSeconds() {
		return Math.max(1, Math.min(3600, get().highlightSeconds));
	}

	public static long highlightMillis() {
		return highlightSeconds() * 1000L;
	}

	public void setHighlightSeconds(int seconds) {
		highlightSeconds = Math.max(1, Math.min(3600, seconds));
		save();
	}

	public static boolean locateFireworksEnabled() {
		return get().locateFireworks;
	}

	public void toggleLocateFireworks() {
		locateFireworks = !locateFireworks;
		save();
	}

	public static boolean locateOneEnabled() {
		return get().locateOneButton || !get().locateAllButton;
	}

	public static boolean locateAllEnabled() {
		return get().locateAllButton;
	}

	public void toggleLocateOne() {
		if (locateOneButton && !locateAllButton) {
			return;
		}
		locateOneButton = !locateOneButton;
		save();
	}

	public void toggleLocateAll() {
		if (locateAllButton && !locateOneButton) {
			return;
		}
		locateAllButton = !locateAllButton;
		save();
	}

	public static boolean itemDisplayEnabled() {
		return get().itemDisplay;
	}

	public void setItemDisplay(boolean enabled) {
		itemDisplay = enabled;
		save();
	}

	public static boolean trackChangesEnabled() {
		return get().trackChanges;
	}

	public static boolean targetArrowsEnabled() {
		return get().targetArrows;
	}

	public void setTargetArrows(boolean enabled) {
		targetArrows = enabled;
		save();
	}

	public void setTrackChanges(boolean enabled) {
		trackChanges = enabled;
		save();
	}

	public void setLocationColors(boolean enabled) {
		locationColors = enabled;
		save();
	}

	public synchronized List<Location> locationsFor(String server) {
		List<Location> matches = new ArrayList<>();
		for (Location loc : locations) {
			if (server.equals(loc.server)) {
				matches.add(loc);
			}
		}
		return matches;
	}

	public synchronized void addLocation(Location loc) {
		locations.add(loc);
		save();
	}

	public synchronized void removeLocation(Location loc) {
		if (locations.remove(loc)) {
			save();
		}
	}

	// call after editing a loc's fields in place
	public synchronized void locationChanged() {
		save();
	}

	public synchronized boolean isTypeExcluded(String key) {
		return excludedContainerTypes.contains(key);
	}

	public synchronized void setTypeExcluded(String key, boolean excluded) {
		boolean changed = excluded ? !excludedContainerTypes.contains(key) && excludedContainerTypes.add(key)
			: excludedContainerTypes.remove(key);
		if (changed) {
			save();
		}
	}

	public synchronized List<String> rerollSelection() {
		return rerollSelection == null ? List.of() : List.copyOf(rerollSelection);
	}

	public synchronized int selectionRevision() {
		return selectionRevision;
	}

	public synchronized void setRerollSelection(List<String> entries) {
		rerollSelection = new ArrayList<>(entries);
		selectionRevision++;
		save();
	}

	public synchronized void removeRerollBook(String enchantment, int level) {
		if (rerollSelection != null && rerollSelection.remove(enchantment + "|" + level)) {
			selectionRevision++;
			save();
		}
	}

	private static ModSettings load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ModSettings loaded = GSON.fromJson(reader, ModSettings.class);
				if (loaded != null) {
					if (loaded.rerollSelection == null) {
						loaded.rerollSelection = new ArrayList<>();
					}
					if (loaded.excludedContainerTypes == null) {
						loaded.excludedContainerTypes = new ArrayList<>();
					}
					if (loaded.locations == null) {
						loaded.locations = new ArrayList<>();
					}
					return loaded;
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}
		return new ModSettings();
	}

	private synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
		}
	}
}
