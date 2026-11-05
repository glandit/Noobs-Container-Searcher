package com.noobfly.containersearcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Puts the chest contents the server plugin sends into the normal container database, so the search finds them
// without opening the chests. Synced entries are marked with serverOwner; a full sync removes synced entries the
// server no longer sends (chest broken, left the group, friend removed), but never what the player saw themselves.
final class ServerSync {
	// decoded item NBT per stack, more is not a real item
	private static final long MAX_ITEM_BYTES = 2L * 1024 * 1024;

	private static Set<String> receivedKeys;
	private static String syncServer;

	private ServerSync() {
	}

	static void begin() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		syncServer = ContainerSearcherClient.serverKey(client);
		receivedKeys = new HashSet<>();
	}

	static void containers(JsonArray list) {
		Minecraft client = Minecraft.getInstance();
		for (JsonElement element : list) {
			if (element.isJsonObject()) {
				ContainerRecord record = apply(client, element.getAsJsonObject());
				if (record != null && receivedKeys != null) {
					receivedKeys.add(ContainerDatabase.keyOf(record));
				}
			}
		}
	}

	static void end() {
		if (receivedKeys == null || syncServer == null) {
			return;
		}
		ContainerDatabase database = ContainerSearcherClient.database();
		List<ContainerRecord> stale = new ArrayList<>();
		for (ContainerRecord record : database.allForServer(syncServer)) {
			if (record.serverOwner != null && !receivedKeys.contains(ContainerDatabase.keyOf(record))) {
				stale.add(record);
			}
		}
		database.removeAll(stale);
		for (ContainerRecord record : stale) {
			ContainerSearcherClient.forgetHighlight(record);
		}
		database.flushNow();
		ContainerSearcherClient.LOGGER.info("Server sent {} chests for the search, removed {} old ones", receivedKeys.size(), stale.size());
		receivedKeys = null;
		syncServer = null;
	}

	static void reset() {
		receivedKeys = null;
		syncServer = null;
	}

	static void container(JsonObject in) {
		if (apply(Minecraft.getInstance(), in) != null) {
			ContainerSearcherClient.database().flush();
		}
	}

	static void gone(JsonObject in) {
		Minecraft client = Minecraft.getInstance();
		BlockPos pos = pos(in, "pos");
		String dimension = string(in, "dim");
		if (client.level == null || pos == null || dimension == null) {
			return;
		}
		ContainerDatabase database = ContainerSearcherClient.database();
		ContainerRecord record = database.find(ContainerSearcherClient.serverKey(client), dimension, pos);
		if (record != null && record.serverOwner != null) {
			database.removeAll(List.of(record));
			ContainerSearcherClient.forgetHighlight(record);
		}
	}

	// how many synced chests are ours and how many come from the group and friends, for the Group tab
	static int[] counts() {
		Minecraft client = Minecraft.getInstance();
		int mine = 0;
		int others = 0;
		if (client.level != null) {
			for (ContainerRecord record : ContainerSearcherClient.knownRecords(client)) {
				if (record.serverOwner == null) {
					continue;
				}
				if (record.serverMine) {
					mine++;
				} else {
					others++;
				}
			}
		}
		return new int[] {mine, others};
	}

	private static ContainerRecord apply(Minecraft client, JsonObject in) {
		BlockPos pos = pos(in, "pos");
		String dimension = string(in, "dim");
		String type = string(in, "type");
		if (client.level == null || pos == null || dimension == null || type == null) {
			return null;
		}
		ContainerRecord record = new ContainerRecord();
		record.server = ContainerSearcherClient.serverKey(client);
		record.dimension = dimension;
		record.x = pos.getX();
		record.y = pos.getY();
		record.z = pos.getZ();
		BlockPos second = pos(in, "pos2");
		if (second != null) {
			record.secondaryX = second.getX();
			record.secondaryY = second.getY();
			record.secondaryZ = second.getZ();
		}
		record.containerType = type;
		record.updatedAt = in.has("at") ? in.get("at").getAsLong() : System.currentTimeMillis();
		String owner = string(in, "owner");
		record.serverOwner = owner == null ? "?" : owner;
		record.serverMine = in.has("mine") && in.get("mine").getAsBoolean();

		Map<String, Integer> contents = new LinkedHashMap<>();
		List<ContainerItemRecord> entries = new ArrayList<>();
		JsonElement items = in.get("items");
		if (items != null && items.isJsonArray()) {
			for (JsonElement element : items.getAsJsonArray()) {
				if (element.isJsonObject()) {
					addItem(client, element.getAsJsonObject(), contents, entries);
				}
			}
		}
		record.items = contents;
		record.entries = entries;
		ContainerSearcherClient.database().putDeferred(record, second);
		return record;
	}

	private static void addItem(Minecraft client, JsonObject in, Map<String, Integer> contents, List<ContainerItemRecord> entries) {
		ItemStack stack = decode(client, string(in, "d"));
		if (stack != null && !stack.isEmpty()) {
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			contents.merge(itemId, stack.getCount(), Integer::sum);
			entries.add(ContainerSearcherClient.itemRecord(client, stack, itemId));
			ContainerSearcherClient.addShulkerContents(client, stack, contents, entries);
			return;
		}
		// the server runs another Minecraft version or sent no data: at least the item id and count
		String itemId = string(in, "id");
		if (itemId == null) {
			return;
		}
		int count = in.has("n") ? Math.max(1, in.get("n").getAsInt()) : 1;
		contents.merge(itemId, count, Integer::sum);
		ContainerItemRecord entry = new ContainerItemRecord();
		entry.itemId = itemId;
		entry.count = count;
		entry.name = itemId;
		entry.searchText = itemId.toLowerCase(Locale.ROOT);
		entries.add(entry);
	}

	// Paper's ItemStack#serializeAsBytes: gzip NBT of the vanilla item codec plus a DataVersion
	private static ItemStack decode(Minecraft client, String data) {
		if (data == null || data.isEmpty() || client.level == null) {
			return null;
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(data);
			CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_ITEM_BYTES));
			tag.remove("DataVersion");
			return ItemStack.CODEC
				.parse(RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess()), tag)
				.result()
				.orElse(null);
		} catch (Exception exception) {
			return null;
		}
	}

	private static BlockPos pos(JsonObject in, String key) {
		JsonElement element = in.get(key);
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
			return null;
		}
		try {
			JsonArray p = element.getAsJsonArray();
			return new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static String string(JsonObject in, String key) {
		JsonElement element = in.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}
}
