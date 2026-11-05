package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Highlights;
import com.noobfly.containersearcher.compat.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ContainerSearcherClient {
	public static final Logger LOGGER = LoggerFactory.getLogger("noobs-container-searcher");
	private static final ContainerDatabase DATABASE = new ContainerDatabase();
	private static final HighlightManager HIGHLIGHTS = new HighlightManager();
	private static final SearchController SEARCH = new SearchController(DATABASE, HIGHLIGHTS);
	private static final LibrarianRerollController LIBRARIAN_REROLL = new LibrarianRerollController();

	private static final int STALE_CONFIRMATIONS = 3;
	private static final int VILLAGER_STALE_CONFIRMATIONS = 5;
	private static final double VERIFY_RADIUS = 48.0D;
	private static final double VILLAGER_VERIFY_RADIUS = 32.0D;
	// give chunks and entities a moment to load before we trust "container is gone"
	private static final long WORLD_SETTLE_MS = 15_000L;
	private static final double LOOK_REACH = 6.0D;

	private static final Map<String, Integer> STALE_STRIKES = new HashMap<>();

	private static BlockPos pendingContainerPos;
	private static BlockPos pendingDuplicatePos;
	private static BlockPos openContainerPos;
	private static BlockPos openDuplicatePos;
	private static long pendingContainerExpiresAt;
	private static BlockPos lookedContainerPos;
	private static BlockPos lookedDuplicatePos;
	private static long lookedContainerExpiresAt;
	// last real container that was open, used as the chest for the "Group" tab when nothing is looked at
	private static BlockPos lastOpenedContainerPos;
	private static UUID pendingVillagerUuid;
	private static long pendingVillagerExpiresAt;
	private static int lastCapturedContainerId = -1;
	private static int lastCapturedFingerprint;
	private static int staleRecordCheckTicks;
	private static ClientLevel trackedLevel;
	private static long verifyReadyAt;

	public void onInitializeClient() {
		DATABASE.load();
		SEARCH.register();
		Highlights.register(HIGHLIGHTS);
		Platform.registerEvents(this);
		ProtectClient.init();
	}

	// chest the "Group" tab manages: the container under the crosshair, otherwise the last one that was open
	public static BlockPos protectTarget(Minecraft client) {
		if (client.player != null && client.level != null) {
			BlockPos pos = pickedContainerPos(client.player, client.level);
			if (pos != null) {
				return canonicalContainerPos(pos, client.level.getBlockState(pos));
			}
		}
		return lastOpenedContainerPos;
	}

	// right-click on a block
	public InteractionResult onUseBlock(Level world, BlockHitResult hitResult) {
		if (ServerRules.modDisabled()) {
			return InteractionResult.PASS;
		}
		if (LIBRARIAN_REROLL.handleBlockUse(hitResult)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clickedPos = hitResult.getBlockPos().immutable();
		if (isContainerBlock(world, clickedPos)) {
			markPendingContainer(world, clickedPos);
		}
		return InteractionResult.PASS;
	}

	// right-click on an entity (item frames, villagers...)
	public InteractionResult onUseEntity(Entity player, Level world, Entity entity) {
		if (ServerRules.modDisabled()) {
			return InteractionResult.PASS;
		}
		if (LIBRARIAN_REROLL.handleEntityUse(entity)) {
			return InteractionResult.SUCCESS;
		}
		if (entity instanceof Villager) {
			pendingVillagerUuid = entity.getUUID();
			pendingVillagerExpiresAt = System.currentTimeMillis() + 5_000L;
		} else {
			markContainerBehindEntity(player, world, entity);
		}
		return InteractionResult.PASS;
	}

	// runs every client tick
	public void onClientTick(Minecraft client) {
		if (ServerRules.modDisabled()) {
			if (LIBRARIAN_REROLL.state() != LibrarianRerollController.State.IDLE) {
				LIBRARIAN_REROLL.stopByUser();
			}
			HIGHLIGHTS.clear(client);
			SEARCH.tick(client);
			return;
		}
		HIGHLIGHTS.tick(client);
		trackLookedContainer(client);
		captureOpenScreen(client);
		ContainerChanges.tick(client, DATABASE);
		removeStaleRecordsInLoadedChunks(client);
		SEARCH.tick(client);
		LIBRARIAN_REROLL.tick(client);
	}

	// a container screen just closed, so we can save what was inside
	public void onContainerScreenClosed() {
		SearchController.clearActiveItem();
		ContainerChanges.clear();
		openContainerPos = null;
		openDuplicatePos = null;
		pendingVillagerUuid = null;
	}

	private static void removeStaleRecordsInLoadedChunks(Minecraft client) {
		if (!trackLevel(client) || ++staleRecordCheckTicks < 20) {
			return;
		}
		staleRecordCheckTicks = 0;
		// just joined a world/dimension, nothing is loaded yet so don't delete anything
		if (System.currentTimeMillis() < verifyReadyAt) {
			return;
		}

		String server = serverKey(client);
		String dimension = dimensionKey(client);
		List<ContainerRecord> staleRecords = new ArrayList<>();
		Set<String> seenKeys = new HashSet<>();
		for (ContainerRecord record : DATABASE.allForServer(server)) {
			String key = ContainerDatabase.keyOf(record);
			seenKeys.add(key);
			if (!dimension.equals(record.dimension)) {
				STALE_STRIKES.remove(key);
				continue;
			}

			if (record.blockGone) {
				STALE_STRIKES.remove(key);
				continue;
			}
			Boolean missing = record.entityUuid != null && !record.entityUuid.isBlank()
				? isVillagerMissing(client, record)
				: isContainerMissing(client, record);
			// null = can't tell right now (too far, chunk not loaded...)
			if (missing == null || !missing) {
				STALE_STRIKES.remove(key);
				continue;
			}

			int strikes = STALE_STRIKES.merge(key, 1, Integer::sum);
			int required = record.entityUuid != null && !record.entityUuid.isBlank()
				? VILLAGER_STALE_CONFIRMATIONS
				: STALE_CONFIRMATIONS;
			if (strikes >= required) {
				STALE_STRIKES.remove(key);
				if (isEnderChest(record)) {
					markEnderGone(record);
				} else if (!keepSurvivingHalf(client, record)) {
					staleRecords.add(record);
				}
			}
		}
		STALE_STRIKES.keySet().retainAll(seenKeys);

		if (staleRecords.isEmpty()) {
			DATABASE.flush();
			return;
		}
		DATABASE.removeAll(staleRecords);
		for (ContainerRecord record : staleRecords) {
			if (record.entityUuid != null) {
				HIGHLIGHTS.removeEntity(record.entityUuid);
			}
			HIGHLIGHTS.removeAt(new BlockPos(record.x, record.y, record.z));
			BlockPos secondaryPos = secondaryPos(record);
			if (secondaryPos != null) {
				HIGHLIGHTS.removeAt(secondaryPos);
			}
		}
		LOGGER.debug("Yuklu chunklarda artik bulunmayan {} konteyner kaydi silindi", staleRecords.size());
	}

	// the lid moves because of us, not because someone else is rummaging around
	// one half of a double chest vanished: the other half is still a chest, so keep it as a single chest
	// record (items stay, they are compared the next time it is opened) instead of forgetting it
	private static boolean keepSurvivingHalf(Minecraft client, ContainerRecord record) {
		BlockPos secondary = secondaryPos(record);
		if (secondary == null) {
			return false;
		}
		BlockPos primary = new BlockPos(record.x, record.y, record.z);
		if (!isVerifiable(client, primary) || !isVerifiable(client, secondary)) {
			return true;
		}
		boolean primaryLeft = record.containerType.equals(blockId(client, primary));
		boolean secondaryLeft = record.containerType.equals(blockId(client, secondary));
		if (primaryLeft == secondaryLeft) {
			return false;
		}
		BlockPos kept = primaryLeft ? primary : secondary;
		BlockPos gone = primaryLeft ? secondary : primary;
		record.x = kept.getX();
		record.y = kept.getY();
		record.z = kept.getZ();
		record.secondaryX = null;
		record.secondaryY = null;
		record.secondaryZ = null;
		record.halfLost = true;
		DATABASE.put(record, primaryLeft ? null : primary);
		HIGHLIGHTS.removeAt(gone);
		return true;
	}

	static boolean isOwnContainer(BlockPos primary, BlockPos secondary) {
		return isSame(primary, openContainerPos) || isSame(primary, openDuplicatePos)
			|| isSame(primary, pendingContainerPos) || isSame(primary, pendingDuplicatePos)
			|| isSame(secondary, openContainerPos) || isSame(secondary, pendingContainerPos);
	}

	private static boolean isSame(BlockPos left, BlockPos right) {
		return left != null && left.equals(right);
	}

	private static boolean trackLevel(Minecraft client) {
		if (client.level != trackedLevel) {
			DATABASE.flushNow();
			trackedLevel = client.level;
			STALE_STRIKES.clear();
			verifyReadyAt = System.currentTimeMillis() + WORLD_SETTLE_MS;
		}
		return client.level != null && client.player != null;
	}

	private static Boolean isVillagerMissing(Minecraft client, ContainerRecord record) {
		Villager villager = findVillager(client, record.entityUuid);
		if (villager != null) {
			String profession = professionId(villager);
			if (!java.util.Objects.equals(record.villagerProfession, profession)) {
				return true;
			}
			BlockPos currentPos = villager.blockPosition();
			if (record.x != currentPos.getX() || record.y != currentPos.getY() || record.z != currentPos.getZ()) {
				record.x = currentPos.getX();
				record.y = currentPos.getY();
				record.z = currentPos.getZ();
				// villagers never stand still, so don't hit the disk on every step
				DATABASE.putDeferred(record);
			}
			return false;
		}

		BlockPos pos = new BlockPos(record.x, record.y, record.z);
		if (!client.level.hasChunk(record.x >> 4, record.z >> 4)
			|| client.player.blockPosition().distSqr(pos) > VILLAGER_VERIFY_RADIUS * VILLAGER_VERIFY_RADIUS) {
			return null;
		}
		return true;
	}

	private static Boolean isContainerMissing(Minecraft client, ContainerRecord record) {
		if (record.containerType == null || record.containerType.isBlank()) {
			return null;
		}

		BlockPos primaryPos = new BlockPos(record.x, record.y, record.z);
		if (!isVerifiable(client, primaryPos)) {
			return null;
		}
		if (!record.containerType.equals(blockId(client, primaryPos))) {
			return true;
		}

		BlockPos secondaryPos = secondaryPos(record);
		if (secondaryPos == null) {
			return false;
		}
		// a double chest can sit on a chunk border, wait until both halves are checkable
		if (!isVerifiable(client, secondaryPos)) {
			return null;
		}
		BlockState primaryState = client.level.getBlockState(primaryPos);
		BlockPos currentConnection = connectedChestPos(primaryPos, primaryState);
		return !record.containerType.equals(blockId(client, secondaryPos))
			|| !secondaryPos.equals(currentConnection);
	}

	private static boolean isVerifiable(Minecraft client, BlockPos pos) {
		return client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
			&& client.player.blockPosition().distSqr(pos) <= VERIFY_RADIUS * VERIFY_RADIUS;
	}

	private static String blockId(Minecraft client, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
	}

	private static BlockPos secondaryPos(ContainerRecord record) {
		if (record.secondaryX == null || record.secondaryY == null || record.secondaryZ == null) {
			return null;
		}
		return new BlockPos(record.secondaryX, record.secondaryY, record.secondaryZ);
	}

	private static boolean isContainerBlock(Level level, BlockPos pos) {
		if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			return false;
		}
		if (level.getBlockState(pos).getBlock() instanceof EnderChestBlock) {
			return true;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof MenuProvider) {
			return true;
		}
		return blockEntity != null && level.getBlockState(pos).getMenuProvider(level, pos) != null;
	}

	private static void markPendingContainer(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		pendingContainerPos = canonicalContainerPos(pos, state);
		pendingDuplicatePos = pendingContainerPos.equals(pos) ? connectedChestPos(pos, state) : pos;
		pendingContainerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	private static void markContainerBehindEntity(Entity player, Level level, Entity entity) {
		if (!level.isClientSide()) {
			// singleplayer fires this on the server thread too, ignore that one
			return;
		}
		BlockPos looked = pickedContainerPos(player, level);
		if (looked != null) {
			markPendingContainer(level, looked);
			return;
		}
		if (entity instanceof BlockAttachedEntity attached) {
			BlockPos support = attached.getPos().relative(entity.getDirection().getOpposite());
			if (isContainerBlock(level, support)) {
				markPendingContainer(level, support);
			} else if (isContainerBlock(level, attached.getPos())) {
				markPendingContainer(level, attached.getPos());
			}
		}
	}

	private static void trackLookedContainer(Minecraft client) {
		if (client.player == null || client.level == null || Compat.screen(client) != null) {
			return;
		}
		BlockPos pos = pickedContainerPos(client.player, client.level);
		if (pos == null) {
			return;
		}
		BlockState state = client.level.getBlockState(pos);
		lookedContainerPos = canonicalContainerPos(pos, state);
		lookedDuplicatePos = lookedContainerPos.equals(pos) ? connectedChestPos(pos, state) : pos;
		lookedContainerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	private static boolean matchesContainerSize(
		Minecraft client,
		AbstractContainerMenu menu,
		BlockPos primaryPos,
		BlockPos secondaryPos
	) {
		int expected = containerSize(client, primaryPos);
		if (expected <= 0) {
			return true;
		}
		if (secondaryPos != null) {
			int secondarySize = containerSize(client, secondaryPos);
			if (secondarySize > 0) {
				expected += secondarySize;
			}
		}

		int slots = 0;
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				slots++;
			}
		}
		return slots == expected;
	}

	// plugin menus (virtual chests, shops...) open on the same right click, they must not count as the block
	private static boolean isRealContainerMenu(
		Minecraft client,
		AbstractContainerScreen<?> screen,
		AbstractContainerMenu menu,
		BlockPos primaryPos,
		BlockPos secondaryPos
	) {
		return isContainerBlock(client.level, primaryPos)
			&& matchesContainerSize(client, menu, primaryPos, secondaryPos)
			&& matchesContainerTitle(client, screen, primaryPos);
	}

	private static boolean matchesContainerTitle(Minecraft client, AbstractContainerScreen<?> screen, BlockPos pos) {
		BlockState state = client.level.getBlockState(pos);
		if (state.getBlock() instanceof EnderChestBlock) {
			return Component.translatable("container.enderchest").getString().equals(screen.getTitle().getString());
		}
		MenuProvider provider = state.getMenuProvider(client.level, pos);
		if (provider == null) {
			return true;
		}
		return provider.getDisplayName().getString().equals(screen.getTitle().getString());
	}

	private static int containerSize(Minecraft client, BlockPos pos) {
		if (client.level.getBlockState(pos).getBlock() instanceof EnderChestBlock) {
			return 27;
		}
		return client.level.getBlockEntity(pos) instanceof Container container ? container.getContainerSize() : 0;
	}

	private static BlockPos pickedContainerPos(Entity viewer, Level level) {
		HitResult hit = viewer.pick(LOOK_REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = blockHit.getBlockPos().immutable();
		return isContainerBlock(level, pos) ? pos : null;
	}

	private void captureOpenScreen(Minecraft client) {
		if (Compat.screen(client) instanceof MerchantScreen screen) {
			captureOpenVillager(client, screen.getMenu());
			return;
		}
		captureOpenContainer(client);
	}

	private void captureOpenContainer(Minecraft client) {
		if (!(Compat.screen(client) instanceof AbstractContainerScreen<?> screen)
			|| client.player == null
			|| client.level == null) {
			lastCapturedContainerId = -1;
			lastCapturedFingerprint = 0;
			openContainerPos = null;
			openDuplicatePos = null;
			if (System.currentTimeMillis() > pendingContainerExpiresAt) {
				pendingContainerPos = null;
				pendingDuplicatePos = null;
			}
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();
		if (menu == client.player.inventoryMenu) {
			return;
		}
		long now = System.currentTimeMillis();
		// server swapped in another menu while we thought a real chest was open
		if (openContainerPos != null && menu.containerId != lastCapturedContainerId
			&& !isRealContainerMenu(client, screen, menu, openContainerPos, openDuplicatePos)) {
			openContainerPos = null;
			openDuplicatePos = null;
			return;
		}
		if (openContainerPos == null && pendingContainerPos != null && now <= pendingContainerExpiresAt
			&& isRealContainerMenu(client, screen, menu, pendingContainerPos, pendingDuplicatePos)) {
			openContainerPos = pendingContainerPos;
			openDuplicatePos = pendingDuplicatePos;
			pendingContainerPos = null;
			pendingDuplicatePos = null;
		}
		if (openContainerPos == null && lookedContainerPos != null && now <= lookedContainerExpiresAt) {
			if (!isRealContainerMenu(client, screen, menu, lookedContainerPos, lookedDuplicatePos)) {
				return;
			}
			openContainerPos = lookedContainerPos;
			openDuplicatePos = lookedDuplicatePos;
			lookedContainerPos = null;
			lookedDuplicatePos = null;
		}
		if (openContainerPos == null) {
			return;
		}
		lastOpenedContainerPos = openContainerPos;

		Map<String, Integer> contents = new LinkedHashMap<>();
		List<ContainerItemRecord> entries = new ArrayList<>();
		boolean hasContainerSlots = false;
		int fingerprint = 1;
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			hasContainerSlots = true;
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				contents.merge(itemId, stack.getCount(), Integer::sum);
				entries.add(itemRecord(client, stack, itemId));
				fingerprint = 31 * fingerprint + itemId.hashCode();
				fingerprint = 31 * fingerprint + stack.getCount();
				fingerprint = 31 * fingerprint + addShulkerContents(client, stack, contents, entries);
			} else {
				fingerprint *= 31;
			}
		}
		if (!hasContainerSlots) {
			return;
		}
		boolean newSession = menu.containerId != lastCapturedContainerId;
		// the server has not sent the contents yet, we would think everything vanished
		if (newSession && menu.getStateId() == 0) {
			return;
		}
		HIGHLIGHTS.removeAt(openContainerPos);
		if (openDuplicatePos != null) {
			HIGHLIGHTS.removeAt(openDuplicatePos);
		}
		if (menu.containerId == lastCapturedContainerId && fingerprint == lastCapturedFingerprint) {
			return;
		}

		if (newSession) {
			ContainerChanges.begin(client, DATABASE, openContainerPos, openDuplicatePos, contents, entries);
		}
		ContainerRecord record = new ContainerRecord();
		record.server = serverKey(client);
		record.dimension = dimensionKey(client);
		record.x = openContainerPos.getX();
		record.y = openContainerPos.getY();
		record.z = openContainerPos.getZ();
		if (openDuplicatePos != null) {
			record.secondaryX = openDuplicatePos.getX();
			record.secondaryY = openDuplicatePos.getY();
			record.secondaryZ = openDuplicatePos.getZ();
		}
		record.containerType = BuiltInRegistries.BLOCK.getKey(
			client.level.getBlockState(openContainerPos).getBlock()
		).toString();
		record.updatedAt = System.currentTimeMillis();
		record.items = contents;
		record.entries = entries;
		DATABASE.put(record, openDuplicatePos);
		syncOtherEnderChests(record);
		lastCapturedContainerId = menu.containerId;
		lastCapturedFingerprint = fingerprint;
		LOGGER.debug("{} konumundaki konteyner kaydedildi", openContainerPos);
	}

	// every ender chest shows the same inventory, so all the ones we know get the contents we just saw
	private static void syncOtherEnderChests(ContainerRecord current) {
		if (!"minecraft:ender_chest".equals(current.containerType)) {
			return;
		}
		for (ContainerRecord other : DATABASE.allForServer(current.server)) {
			boolean same = other.dimension.equals(current.dimension)
				&& other.x == current.x && other.y == current.y && other.z == current.z;
			if (same || !"minecraft:ender_chest".equals(other.containerType)) {
				continue;
			}
			other.items = current.items;
			other.entries = current.entries;
			other.updatedAt = current.updatedAt;
			DATABASE.put(other, null);
		}
	}

	private static boolean isEnderChest(ContainerRecord record) {
		return "minecraft:ender_chest".equals(record.containerType);
	}

	// the block is gone but the inventory lives on the player, so keep the items and just flag it
	private static void markEnderGone(ContainerRecord record) {
		record.blockGone = true;
		DATABASE.put(record, null);
		HIGHLIGHTS.removeAt(new BlockPos(record.x, record.y, record.z));
	}

	private void captureOpenVillager(Minecraft client, MerchantMenu menu) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (pendingVillagerUuid == null || System.currentTimeMillis() > pendingVillagerExpiresAt) {
			pendingVillagerUuid = null;
			return;
		}
		Villager villager = findVillager(client, pendingVillagerUuid.toString());
		if (villager == null) {
			return;
		}
		String profession = professionId(villager);
		if (profession == null) {
			return;
		}

		Map<String, Integer> contents = new LinkedHashMap<>();
		List<ContainerItemRecord> entries = new ArrayList<>();
		int fingerprint = profession.hashCode();
		for (MerchantOffer offer : menu.getOffers()) {
			ItemStack result = offer.getResult();
			if (result.isEmpty()) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
			contents.merge(itemId, result.getCount(), Integer::sum);
			entries.add(itemRecord(client, result, itemId));
			fingerprint = 31 * fingerprint + result.hashCode();
		}
		if (entries.isEmpty() || menu.containerId == lastCapturedContainerId && fingerprint == lastCapturedFingerprint) {
			return;
		}

		BlockPos pos = villager.blockPosition();
		ContainerRecord record = new ContainerRecord();
		record.server = serverKey(client);
		record.dimension = dimensionKey(client);
		record.x = pos.getX();
		record.y = pos.getY();
		record.z = pos.getZ();
		record.entityUuid = villager.getStringUUID();
		record.villagerProfession = profession;
		record.containerType = "villager:" + profession;
		record.updatedAt = System.currentTimeMillis();
		record.items = contents;
		record.entries = entries;
		DATABASE.put(record);
		lastCapturedContainerId = menu.containerId;
		lastCapturedFingerprint = fingerprint;
		LOGGER.debug("Saved trades of {} villager {}", profession, record.entityUuid);
	}

	private static Villager findVillager(Minecraft client, String uuidText) {
		if (client.level == null || uuidText == null) {
			return null;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Villager villager && uuidText.equals(villager.getStringUUID())) {
				return villager;
			}
		}
		return null;
	}

	private static String professionId(Villager villager) {
		return villager.getVillagerData().profession().unwrapKey()
			.filter(key -> !VillagerProfession.NONE.equals(key))
			.map(key -> key.location().toString())
			.orElse(null);
	}

	// items packed inside a shulker box count as being in this container too, tagged with the box name
	private static int addShulkerContents(Minecraft client, ItemStack box, Map<String, Integer> contents, List<ContainerItemRecord> entries) {
		ItemContainerContents packed = box.get(DataComponents.CONTAINER);
		if (packed == null) {
			return 0;
		}
		String boxName = box.getHoverName().getString();
		int hash = 0;
		NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
		packed.copyInto(slots);
		for (ItemStack inner : slots) {
			if (inner.isEmpty()) {
				continue;
			}
			String innerId = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
			contents.merge(innerId, inner.getCount(), Integer::sum);
			ContainerItemRecord record = itemRecord(client, inner, innerId);
			record.inside = boxName;
			entries.add(record);
			hash = 31 * hash + innerId.hashCode();
			hash = 31 * hash + inner.getCount();
		}
		return hash;
	}

	private static ContainerItemRecord itemRecord(Minecraft client, ItemStack stack, String itemId) {
		ContainerItemRecord record = new ContainerItemRecord();
		record.itemId = itemId;
		record.stackData = encodeStack(client, stack);
		record.count = stack.getCount();
		record.name = stack.getHoverName().getString();
		record.enchanted = stack.isEnchanted();
		record.damageable = stack.isDamageableItem();
		record.damage = stack.getDamageValue();
		record.maxDamage = stack.getMaxDamage();
		StringBuilder searchText = new StringBuilder(itemId).append(' ').append(record.name);
		for (Component line : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(client, stack)) {
			String text = line.getString();
			record.tooltipLines.add(text);
			searchText.append(' ').append(text);
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				String text = line.getString();
				record.loreLines.add(text);
				searchText.append(' ').append(text);
			}
		}
		addEnchantments(record, searchText, stack.get(DataComponents.ENCHANTMENTS));
		addEnchantments(record, searchText, stack.get(DataComponents.STORED_ENCHANTMENTS));
		record.searchText = searchText.toString().toLowerCase(Locale.ROOT);
		return record;
	}

	private static String encodeStack(Minecraft client, ItemStack stack) {
		if (client.level == null) {
			return null;
		}
		return ItemStack.CODEC
			.encodeStart(RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess()), stack)
			.result()
			.map(Tag::toString)
			.orElse(null);
	}

	private static void addEnchantments(ContainerItemRecord record, StringBuilder searchText, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			String id = enchantment.unwrapKey()
				.map(key -> key.location().toString())
				.orElse(enchantment.getRegisteredName());
			String name = Enchantment.getFullname(enchantment, entry.getIntValue()).getString();
			record.enchantments.add(id);
			record.enchantmentNames.add(name);
			searchText.append(' ').append(id).append(' ').append(name);
		}
	}

	private static BlockPos canonicalContainerPos(BlockPos clickedPos, BlockState state) {
		BlockPos connected = connectedChestPos(clickedPos, state);
		if (connected == null) {
			return clickedPos;
		}
		return comparePositions(clickedPos, connected) <= 0 ? clickedPos : connected;
	}

	private static BlockPos connectedChestPos(BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return null;
		}
		return pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
	}

	private static int comparePositions(BlockPos left, BlockPos right) {
		int x = Integer.compare(left.getX(), right.getX());
		if (x != 0) {
			return x;
		}
		int y = Integer.compare(left.getY(), right.getY());
		return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
	}

	public static String dimensionKey(Minecraft client) {
		return client.level.dimension().location().toString();
	}

	// save folder is unique per world, the level name is not (two "New World"s collide)
	private static String worldFolder(Minecraft client) {
		try {
			java.nio.file.Path root = client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
			return root.toAbsolutePath().normalize().getFileName().toString();
		} catch (RuntimeException exception) {
			return client.getSingleplayerServer().getWorldData().getLevelName();
		}
	}

	private static java.lang.reflect.Field zoomSeedField;
	private static boolean zoomSeedLooked;
	private static Object lastKeyLevel;
	private static String lastKeyBase;
	private static String lastKey;
	private static final Set<String> MIGRATED = new HashSet<>();

	// same address can host several worlds (proxies, multiverse), the hashed seed the server sends tells them apart
	private static String withWorldId(Minecraft client, String base) {
		if (client.level == lastKeyLevel && base.equals(lastKeyBase)) {
			return lastKey;
		}
		String key = base;
		try {
			if (!zoomSeedLooked) {
				zoomSeedLooked = true;
				for (java.lang.reflect.Field field : client.level.getBiomeManager().getClass().getDeclaredFields()) {
					if (field.getType() == long.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						zoomSeedField = field;
						break;
					}
				}
			}
			if (zoomSeedField != null) {
				key = base + "#" + Long.toHexString(zoomSeedField.getLong(client.level.getBiomeManager()));
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			key = base;
		}
		if (!key.equals(base) && MIGRATED.add(key)) {
			// data saved before worlds were told apart goes to the first world that connects
			DATABASE.rekey(base, key);
			boolean moved = false;
			for (ModSettings.Location loc : ModSettings.get().locations) {
				if (base.equals(loc.server)) {
					loc.server = key;
					moved = true;
				}
			}
			if (moved) {
				ModSettings.get().locationChanged();
			}
		}
		lastKeyLevel = client.level;
		lastKeyBase = base;
		lastKey = key;
		return key;
	}

	static String serverKey(Minecraft client) {
		if (client.getCurrentServer() != null) {
			// realms hands out a different address and the name can be edited, the world seed (withWorldId) tells realms apart
			String base = client.getCurrentServer().isRealm()
				? "realms"
				: "multiplayer:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
			return client.level == null ? base : withWorldId(client, base);
		}
		if (client.getSingleplayerServer() != null) {
			return "singleplayer:" + worldFolder(client);
		}
		return "singleplayer:unknown";
	}

	public static List<ContainerRecord> knownRecords(Minecraft client) {
		return DATABASE.allForServer(serverKey(client));
	}

	public static boolean isVillagerHighlighted(Entity entity) {
		return entity instanceof Villager && HIGHLIGHTS.isVillagerHighlighted(entity.getStringUUID());
	}

	static LibrarianRerollController librarianReroll() {
		return LIBRARIAN_REROLL;
	}

	static void prepareVillagerCapture(UUID villagerUuid) {
		pendingVillagerUuid = villagerUuid;
		pendingVillagerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	public static void onKeyboardInput(int key, int scancode, int action, int mods) {
		LIBRARIAN_REROLL.onKeyboardInput(key, scancode, action, mods);
	}
}
