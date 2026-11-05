package com.noobfly.containersearcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noobfly.containersearcher.compat.ProtectNet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Talks to the optional "Noobs-Container-Searcher" Paper plugin, which locks chests and lets players share them
// (single players, friends for all chests, groups). Everything here is only UI: the server checks every action.
// The feature stays hidden on servers without the plugin.
public final class ProtectClient {
	public static final String CHANNEL = "noobs_container_searcher:protect";
	private static final int VERSION = 1;

	public record Member(String name, boolean online, boolean leader, boolean me) { }

	public record Group(String name, String leader, boolean meLeader, List<Member> members) { }

	public record Invite(String group, String from) { }

	public record Chest(String status, String owner, boolean mine, boolean manage, boolean access,
		String reason, List<String> shared, String ownerGroup, boolean claimable) {
		public boolean locked() {
			return "locked".equals(status);
		}
	}

	private static boolean helloSent;
	private static boolean available;
	private static Group group;
	private static Invite invite;
	private static List<String> friends = List.of();
	private static List<String> online = List.of();
	private static final Map<BlockPos, Chest> CHESTS = new HashMap<>();
	private static String message;
	private static boolean messageOk;
	private static long messageAt;

	private ProtectClient() {
	}

	public static void init() {
		ProtectNet.register(ProtectClient::receive, ProtectClient::onReady, ProtectClient::reset);
	}

	// ---- state ----

	public static boolean available() {
		return available;
	}

	public static Group group() {
		return group;
	}

	public static Invite invite() {
		return invite;
	}

	public static List<String> friends() {
		return friends;
	}

	public static List<String> online() {
		return online;
	}

	public static Chest chest(BlockPos pos) {
		return pos == null ? null : CHESTS.get(pos);
	}

	// last answer from the server, shown for a few seconds
	public static String message() {
		return message != null && System.currentTimeMillis() - messageAt < 8_000L ? message : null;
	}

	public static boolean messageOk() {
		return messageOk;
	}

	// ---- actions ----

	public static void requestInfo(BlockPos pos) {
		if (pos != null) {
			send(withPos("info", pos));
		}
	}

	public static void lock(BlockPos pos) {
		send(withPos("lock", pos));
	}

	public static void unlock(BlockPos pos) {
		send(withPos("unlock", pos));
	}

	public static void share(BlockPos pos, String player) {
		JsonObject out = withPos("share", pos);
		out.addProperty("player", player);
		send(out);
	}

	public static void unshare(BlockPos pos, String player) {
		JsonObject out = withPos("unshare", pos);
		out.addProperty("player", player);
		send(out);
	}

	public static void trust(String player) {
		JsonObject out = message("trust");
		out.addProperty("player", player);
		send(out);
	}

	public static void untrust(String player) {
		JsonObject out = message("untrust");
		out.addProperty("player", player);
		send(out);
	}

	// create, invite, accept, decline, leave, kick, disband
	public static void group(String op, String arg) {
		JsonObject out = message("group");
		out.addProperty("op", op);
		if (arg != null) {
			out.addProperty("arg", arg);
		}
		send(out);
	}

	// ---- network ----

	// the server announced our channel, so the plugin is there
	private static void onReady() {
		if (helloSent) {
			return;
		}
		helloSent = true;
		JsonObject hello = message("hello");
		hello.addProperty("v", VERSION);
		send(hello);
	}

	private static void reset() {
		helloSent = false;
		available = false;
		group = null;
		invite = null;
		friends = List.of();
		online = List.of();
		CHESTS.clear();
		message = null;
	}

	private static void receive(String json) {
		JsonObject in;
		try {
			in = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		switch (string(in, "t")) {
			case "hello" -> {
				available = true;
				ContainerSearcherClient.LOGGER.info("Chest sharing plugin found on this server, Group tab enabled");
			}
			case "state" -> readState(in);
			case "chest" -> readChest(in);
			case "sync" -> readSync(in);
			case "result" -> {
				message = string(in, "msg");
				messageOk = in.has("ok") && in.get("ok").getAsBoolean();
				messageAt = System.currentTimeMillis();
			}
			default -> { }
		}
	}

	private static void readState(JsonObject in) {
		available = true;
		JsonElement g = in.get("group");
		if (g != null && g.isJsonObject()) {
			JsonObject o = g.getAsJsonObject();
			List<Member> members = new ArrayList<>();
			for (JsonElement e : array(o, "members")) {
				JsonObject m = e.getAsJsonObject();
				members.add(new Member(string(m, "name"), bool(m, "online"), bool(m, "leader"), bool(m, "me")));
			}
			group = new Group(string(o, "name"), string(o, "leader"), bool(o, "me_leader"), List.copyOf(members));
		} else {
			group = null;
		}
		JsonElement i = in.get("invite");
		invite = i != null && i.isJsonObject()
			? new Invite(string(i.getAsJsonObject(), "group"), string(i.getAsJsonObject(), "from"))
			: null;
		friends = strings(in, "friends");
		online = strings(in, "online");
	}

	// ---- container sync with friends ----

	// we just saw this container: tell the plugin, it passes the contents on to friends and group members
	public static void shareContainer(ContainerRecord record) {
		if (!available || !ModSettings.get().shareWithFriends || record.entityUuid != null) {
			return;
		}
		String data = ContainerShare.encode(record);
		if (data == null) {
			return;
		}
		JsonObject out = withPos("sync", new BlockPos(record.x, record.y, record.z));
		out.addProperty("dim", record.dimension);
		out.addProperty("data", data);
		send(out);
	}

	// a friend opened a container we may open too
	private static void readSync(JsonObject in) {
		JsonArray p = array(in, "pos");
		String data = string(in, "data");
		if (p.size() != 3 || data.isEmpty() || string(in, "dim").isEmpty()) {
			return;
		}
		ContainerRecord record = ContainerShare.decode(data);
		if (record == null) {
			return;
		}
		record.dimension = string(in, "dim");
		record.x = p.get(0).getAsInt();
		record.y = p.get(1).getAsInt();
		record.z = p.get(2).getAsInt();
		String by = string(in, "by");
		long sentAt = in.has("ts") && in.get("ts").isJsonPrimitive() ? in.get("ts").getAsLong() : 0L;
		net.minecraft.client.Minecraft.getInstance().execute(() -> ContainerSearcherClient.receiveShared(record, by, sentAt));
	}

	private static void readChest(JsonObject in) {
		JsonArray p = array(in, "pos");
		if (p.size() != 3) {
			return;
		}
		BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
		CHESTS.put(pos, new Chest(string(in, "status"), string(in, "owner"), bool(in, "mine"), bool(in, "manage"),
			bool(in, "access"), string(in, "reason"), strings(in, "shared"), string(in, "owner_group"), bool(in, "claimable")));
	}

	private static void send(JsonObject message) {
		ProtectNet.send(message.toString());
	}

	// ---- json helpers ----

	private static JsonObject message(String type) {
		JsonObject out = new JsonObject();
		out.addProperty("t", type);
		return out;
	}

	private static JsonObject withPos(String type, BlockPos pos) {
		JsonObject out = message(type);
		JsonArray p = new JsonArray();
		p.add(pos.getX());
		p.add(pos.getY());
		p.add(pos.getZ());
		out.add("pos", p);
		return out;
	}

	private static String string(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static boolean bool(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	private static JsonArray array(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	private static List<String> strings(JsonObject in, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : array(in, key)) {
			if (e.isJsonPrimitive()) {
				out.add(e.getAsString());
			}
		}
		return List.copyOf(out);
	}
}
