package com.noobfly.containersearcher;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContainerRecord {
	public String server;
	public String dimension;
	public int x;
	public int y;
	public int z;
	public Integer secondaryX;
	public Integer secondaryY;
	public Integer secondaryZ;
	public String containerType;
	public String entityUuid;
	public String villagerProfession;
	public long updatedAt;
	public Map<String, Integer> items = new LinkedHashMap<>();
	public java.util.List<ContainerItemRecord> entries = new java.util.ArrayList<>();
	// openings we saw from a distance since the last time the player looked inside
	public java.util.List<ContainerEvent> events = new java.util.ArrayList<>();
	// the other half of this double chest was broken, so the saved items still include that half
	public boolean halfLost;
	// ender chest block was broken, the items are still the player's own
	public boolean blockGone;
	// name of the friend whose visit this record was synced from, null when we saw the container ourselves
	public String sharedBy;
}
