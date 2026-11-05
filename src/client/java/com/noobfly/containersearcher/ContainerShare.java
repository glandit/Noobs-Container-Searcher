package com.noobfly.containersearcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// Packs a saved container for the chest sharing plugin ("sync" messages) and unpacks what friends send:
// JSON, gzip, base64. The plugin only relays it; it can't read it.
final class ContainerShare {
	// the plugin accepts at most this many characters per message
	static final int MAX_ENCODED = 28_000;
	// what we are willing to unpack from someone else (a double chest is far below this)
	private static final int MAX_DECODED = 512 * 1024;
	private static final Gson GSON = new GsonBuilder().create();

	private ContainerShare() {
	}

	// null when even the slimmest version is too big
	static String encode(ContainerRecord record) {
		for (int level = 0; level < 3; level++) {
			String data = pack(copyOf(record, level));
			if (data != null && data.length() <= MAX_ENCODED) {
				return data;
			}
		}
		return null;
	}

	// level 0 everything, 1 without tooltips, 2 also without the item data used for icons
	private static ContainerRecord copyOf(ContainerRecord record, int level) {
		ContainerRecord copy = GSON.fromJson(GSON.toJson(record), ContainerRecord.class);
		copy.server = null; // the receiver puts it under its own server key
		copy.events = new ArrayList<>();
		copy.sharedBy = null;
		if (level >= 1) {
			for (ContainerItemRecord item : copy.entries) {
				item.tooltipLines = new ArrayList<>();
			}
		}
		if (level >= 2) {
			for (ContainerItemRecord item : copy.entries) {
				item.stackData = null;
			}
		}
		return copy;
	}

	private static String pack(ContainerRecord record) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(GSON.toJson(record).getBytes(StandardCharsets.UTF_8));
			}
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (IOException exception) {
			return null;
		}
	}

	// null for anything that isn't a well-formed, reasonably sized record
	static ContainerRecord decode(String data) {
		try {
			byte[] packed = Base64.getDecoder().decode(data);
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
				byte[] json = gzip.readNBytes(MAX_DECODED + 1);
				if (json.length > MAX_DECODED) {
					return null;
				}
				ContainerRecord record = GSON.fromJson(new String(json, StandardCharsets.UTF_8), ContainerRecord.class);
				if (record == null || record.items == null || record.entries == null || record.containerType == null) {
					return null;
				}
				return record;
			}
		} catch (IOException | RuntimeException exception) {
			return null;
		}
	}
}
