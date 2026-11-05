package com.noobfly.containersearcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noobfly.containersearcher.compat.ProtectNet;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Talks to the optional "Noobs-Container-Searcher" Paper plugin. The server remembers what is in every chest that
// has an owner and sends it here, so the search finds it without opening the chests: our own chests, the ones of
// our group and of players who added us as a friend. Groups and friends are managed in the Group tab.
// The feature stays hidden on servers without the plugin.
public final class ProtectClient {
	public static final String CHANNEL = "noobs_container_searcher:protect";
	private static final int VERSION = 2;
	// long messages come in pieces: "#frag <id> <index> <count>\n<piece>"
	private static final String FRAGMENT = "#frag ";
	private static final int MAX_FRAGMENTS = 256;
	// all unfinished pieces together, a real chest message is far smaller
	private static final int MAX_BUFFERED_CHARS = 8_000_000;

	public record Member(String name, boolean online, boolean leader, boolean me) { }

	public record Group(String name, String leader, boolean meLeader, List<Member> members) { }

	public record Invite(String group, String from) { }

	private static boolean helloSent;
	private static boolean available;
	private static boolean syncing;
	private static Group group;
	private static Invite invite;
	private static List<String> friends = List.of();
	private static List<String> friendOf = List.of();
	private static List<String> online = List.of();
	private static String message;
	private static boolean messageOk;
	private static long messageAt;
	private static final Map<String, String[]> PIECES = new HashMap<>();
	private static int bufferedChars;

	private ProtectClient() {
	}

	public static void init() {
		ProtectNet.register(ProtectClient::receive, ProtectClient::onReady, ProtectClient::reset);
	}

	// ---- state ----

	public static boolean available() {
		return available;
	}

	// true while the server is sending all chests
	public static boolean syncing() {
		return syncing;
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

	// players who added us as a friend: we see their chests
	public static List<String> friendOf() {
		return friendOf;
	}

	public static List<String> online() {
		return online;
	}

	// last answer from the server, shown for a few seconds
	public static String message() {
		return message != null && System.currentTimeMillis() - messageAt < 8_000L ? message : null;
	}

	public static boolean messageOk() {
		return messageOk;
	}

	// ---- actions ----

	// ask for all chests again
	public static void requestSync() {
		send(message("sync"));
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
		syncing = false;
		group = null;
		invite = null;
		friends = List.of();
		friendOf = List.of();
		online = List.of();
		message = null;
		dropPieces();
		ServerSync.reset();
	}

	private static void receive(String text) {
		Minecraft.getInstance().execute(() -> {
			String json = text.startsWith(FRAGMENT) ? joinPieces(text) : text;
			if (json != null) {
				handle(json);
			}
		});
	}

	// returns the whole message once the last piece is in
	private static String joinPieces(String piece) {
		int newline = piece.indexOf('\n');
		if (newline < 0) {
			return null;
		}
		String[] head = piece.substring(FRAGMENT.length(), newline).split(" ");
		if (head.length != 3) {
			return null;
		}
		int index;
		int count;
		try {
			index = Integer.parseInt(head[1]);
			count = Integer.parseInt(head[2]);
		} catch (NumberFormatException exception) {
			return null;
		}
		if (count < 1 || count > MAX_FRAGMENTS || index < 0 || index >= count) {
			return null;
		}
		if (!PIECES.containsKey(head[0]) && PIECES.size() >= 8) {
			dropPieces(); // pieces of messages that never finished
		}
		String[] pieces = PIECES.computeIfAbsent(head[0], ignored -> new String[count]);
		if (pieces.length != count || pieces[index] != null) {
			dropPieces(head[0]);
			return null;
		}
		String part = piece.substring(newline + 1);
		if (bufferedChars + part.length() > MAX_BUFFERED_CHARS) {
			dropPieces();
			return null;
		}
		pieces[index] = part;
		bufferedChars += part.length();
		for (String done : pieces) {
			if (done == null) {
				return null;
			}
		}
		dropPieces(head[0]);
		return String.join("", pieces);
	}

	private static void dropPieces(String id) {
		String[] pieces = PIECES.remove(id);
		if (pieces != null) {
			for (String part : pieces) {
				if (part != null) {
					bufferedChars -= part.length();
				}
			}
		}
	}

	private static void dropPieces() {
		PIECES.clear();
		bufferedChars = 0;
	}

	private static void handle(String json) {
		JsonObject in;
		try {
			in = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		try {
			switch (string(in, "t")) {
				case "hello" -> {
					available = true;
					ContainerSearcherClient.LOGGER.info("Chest sync plugin found on this server, Group tab enabled");
				}
				case "state" -> readState(in);
				case "result" -> {
					message = string(in, "msg");
					messageOk = in.has("ok") && in.get("ok").getAsBoolean();
					messageAt = System.currentTimeMillis();
				}
				case "sync_begin" -> {
					syncing = true;
					ServerSync.begin();
				}
				case "containers" -> ServerSync.containers(array(in, "list"));
				case "sync_end" -> {
					ServerSync.end();
					syncing = false;
				}
				case "container" -> ServerSync.container(in);
				case "container_gone" -> ServerSync.gone(in);
				default -> { }
			}
		} catch (RuntimeException e) {
			ContainerSearcherClient.LOGGER.warn("Bad message from the chest sync plugin", e);
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
		friendOf = strings(in, "friend_of");
		online = strings(in, "online");
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
