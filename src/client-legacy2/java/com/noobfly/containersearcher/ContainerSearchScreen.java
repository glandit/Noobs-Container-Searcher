package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.CompatScreen;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;

public final class ContainerSearchScreen extends CompatScreen {
	private enum Tab { SEARCH, REROLL, LOCATIONS, SETTINGS }

	private enum Sort { NAME, DISTANCE }

	private static final int RESULT_HEIGHT = 32;
	private static final int RESULT_STRIDE = 35;
	private static final int ENCHANT_ROW = 16;
	private static final int REROLL_CARD_HEIGHT = 26;
	private static final int REROLL_CARD_STRIDE = 29;
	private static final int HEADER_HEIGHT = 34;
	private static final int FOOTER_HEIGHT = 22;

	private static final int BACKDROP = 0xB0000000;
	private static final int PANEL = 0xFF0C0C0E;
	private static final int HEADER = 0xFF121214;
	private static final int SURFACE = 0xFF17171A;
	private static final int SURFACE_HI = 0xFF202024;
	private static final int INPUT_BG = 0xFF08080A;
	private static final int BORDER = 0xFF28282D;
	private static final int BORDER_HI = 0xFF3A3A41;

	private static final int TEXT_PRIMARY = 0xFFF2F3F5;
	private static final int TEXT_SECONDARY = 0xFFA8A8B0;
	private static final int TEXT_MUTED = 0xFF686870;

	private static final int ACCENT = 0xFF5C5C66;
	private static final int ACCENT_HOVER = 0xFF8A8A96;
	private static final int ACCENT_DARK = 0xFF3A3A42;
	private static final int ACCENT_BG = 0xFF26262B;
	private static final String DISCORD_URL = "https://discord.gg/SSwGQZp4gQ";
	private static final int DISCORD_HOVER = 0xFF5865F2;
	private static final int DANGER = 0xFFED4245;
	private static final int DANGER_HOVER = 0xFFF26669;
	private static final int DANGER_DARK = 0xFFA82E31;
	private static final int DANGER_BG = 0xFF3A1D22;
	private static final int SUCCESS = 0xFF3BA55D;
	private static final int WARNING = 0xFFFAA61A;
	private static final int ENCHANT_PURPLE = 0xFFFF55FF;
	private static final int CONTAINER_CYAN = 0xFF55D6D6;

	private static final int MAX_DISTANCE = 1000;
	private static final int MAX_CUSTOM_DISTANCE = 100000;
	private static final int DURATION_SLIDER_MAX = 300;
	private static final int MAX_DURATION_SECONDS = 3600;
	private static final int MAX_PANEL_WIDTH = 800;
	// below this (in gui pixels) the layout gets cramped, so the screen picks its own gui scale
	private static final int FIT_WIDTH = 680;
	private static final int FIT_HEIGHT = 380;
	private static int userGuiScale = -1;
	private static final int MAX_PANEL_HEIGHT = 480;
	private static final int UNLIMITED_DISTANCE = -1;
	private static final int[] DISTANCE_PRESETS = {100, 250, 500, UNLIMITED_DISTANCE};
	private static final int[] LOCATION_COLORS = {0xFF4CC38A, 0xFFE8836B, 0xFF5AA9F0, 0xFFE0B070, 0xFFC090E0, 0xFFE06A9A, 0xFF55D6D6, 0xFFE8D84C};
	private static final Item[] LOCATION_ICONS = {
		Items.COMPASS, Items.CRAFTING_TABLE, Items.WHEAT, Items.IRON_PICKAXE, Items.ENDER_PEARL, Items.CHEST,
		Items.DIAMOND, Items.BELL, Items.BLAZE_ROD, Items.OAK_SAPLING, Items.SHIELD, Items.BEACON
	};
	private static final int LOCATION_ROW = 26;
	private static final int LOCATION_STRIDE = 29;
	private static final int MAX_LOCATION_RADIUS = 5000;
	private static final int COLOR_COLS = 36;
	private static final int COLOR_ROWS = 6;
	private static final int COLOR_CELL = 8;
	private static final int ICON_CELL = 20;

	private static final String[] ICON_SEARCH = {"..XXXX...", ".X....X..", "X......X.", "X......X.", "X......X.", ".X....X..", "..XXXXXX.", "......XXX", "........X"};
	private static final String[] ICON_X = {"X...X", ".X.X.", "..X..", ".X.X.", "X...X"};
	// even size so it sits exactly in the middle of an even button
	private static final String[] ICON_CLOSE = {"X....X", ".X..X.", "..XX..", "..XX..", ".X..X.", "X....X"};
	private static final String[] ICON_CHECK = {".....X", "....X.", "X..X..", ".XX...", "..X..."};
	private static final String[] ICON_CHEVRON = {"XXXXX", ".XXX.", "..X.."};
	private static final String[] ICON_ARROW = {"..X..", "...X.", "XXXXX", "...X.", "..X.."};
	private static final String[] ICON_PIN = {".XXX.", "XXXXX", "XXXXX", ".XXX.", "..X..", "..X.."};

	private record Hit(int x, int y, int w, int h, Runnable action) {
		private boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private record ScrollArea(int x, int y, int w, int h, DoubleConsumer onScroll) { }

	private record ChipSpec(String label, int count, int dot, boolean active, boolean closable, Runnable click, boolean excluded) {
		private ChipSpec(String label, int count, int dot, boolean active, boolean closable, Runnable click) {
			this(label, count, dot, active, closable, click, false);
		}
	}

	private final SearchController controller;
	private final List<ContainerRecord> records;
	private final LibrarianRerollController reroll;
	private final List<ResultRow> rows = new ArrayList<>();
	private final List<FilterEntry> filters = new ArrayList<>();
	private final List<ContainerTypeEntry> containerTypes = new ArrayList<>();
	private final List<EnchantmentChoice> allChoices = new ArrayList<>();
	private final List<EnchantmentChoice> visibleChoices = new ArrayList<>();
	private final Set<EnchantmentChoice> selectedChoices = new LinkedHashSet<>();
	private final List<Hit> hits = new ArrayList<>();
	private final List<ScrollArea> scrollAreas = new ArrayList<>();
	private final Map<String, String> selectedTypeLabels = new LinkedHashMap<>();

	private Tab tab = Tab.SEARCH;
	private Sort sort = Sort.NAME;
	private EditBox searchBox;
	private EditBox filterSearchBox;
	private EditBox rerollSearchBox;
	private EditBox locationNameBox;
	private double locationScroll;
	private String newLocationItem = "minecraft:compass";
	private boolean pickingIcon;
	private EditBox iconSearchBox;
	private double iconScroll;
	private List<Item> allItems;
	private List<String> allItemNames;
	private List<Item> iconResults = new ArrayList<>();
	private final Map<String, ItemStack> iconStacks = new HashMap<>();
	private ScrollArea pendingScrollArea;
	private double pendingScroll;
	private final Set<ModSettings.Location> selectedLocations = new LinkedHashSet<>();
	private final Map<ModSettings.Location, Integer> locationCounts = new HashMap<>();
	private ModSettings.Location editing;
	private boolean syncingEditor;
	private EditBox radiusBox;
	private EditBox hexBox;
	private String selectedFilter;
	private String selectedFilterLabel = "";
	private final Set<String> selectedContainerTypeKeys = new LinkedHashSet<>();
	private int maxDistance = UNLIMITED_DISTANCE;
	private int resultContainerCount;
	private double resultScroll;
	private double filterScroll;
	private double containerTypeScroll;
	private boolean excludeMode;
	// settings sub-page that lists every saved server, and the server whose data we are looking at instead of the current one
	private boolean serversView;
	private String viewingKey;
	private List<ContainerRecord> ownRecords;
	private long dataBytes = -1;
	// per-frame work cached: decoded stacks (NBT parse) and the hovered row's "locate all" lookup
	private final Map<ContainerItemRecord, ItemStack> stackCache = new java.util.IdentityHashMap<>();
	private List<ModSettings.Location> frameLocations;
	private ResultRow multiRow;
	private boolean multiValue;
	private double serverScroll;
	private final List<ServerSummary> serverSummaries = new ArrayList<>();
	private final Map<String, Set<String>> typeMatchCache = new HashMap<>();
	private double rerollScroll;
	private boolean draggingDistanceSlider;
	private boolean draggingDurationSlider;
	private int durationSliderX;
	private int durationSliderWidth;
	private enum NumberEdit { NONE, DISTANCE, DURATION }
	private NumberEdit numberEdit = NumberEdit.NONE;
	private EditBox numberBox;
	private int sliderX;
	private int sliderWidth;
	private int mx = -1;
	private int my = -1;
	private boolean ctrlDown;

	public ContainerSearchScreen(SearchController controller, List<ContainerRecord> records) {
		super(Component.translatable("screen.noobs_container_searcher.title"));
		this.controller = controller;
		this.records = records;
		ITEM_NAME_CACHE.clear();
		ENCHANTMENT_NAME_CACHE.clear();
		this.reroll = ContainerSearcherClient.librarianReroll();
		rebuild();
	}

	@Override
	protected void init() {
	fitGuiScale();
		searchBox = newBox(Component.translatable("screen.noobs_container_searcher.search"), "screen.noobs_container_searcher.search_hint");
		searchBox.setResponder(value -> {
			resultScroll = 0;
			rebuild();
		});
		filterSearchBox = newBox(Component.translatable("screen.noobs_container_searcher.filter_search"), "screen.noobs_container_searcher.filter_search_hint");
		filterSearchBox.setResponder(value -> {
			filterScroll = 0;
			containerTypeScroll = 0;
			rebuild();
		});
		rerollSearchBox = newBox(Component.translatable("screen.noobs_container_searcher.reroll_search"), "screen.noobs_container_searcher.reroll_search_hint");
		rerollSearchBox.setResponder(value -> {
			rerollScroll = 0;
			rebuildChoices();
		});

		locationNameBox = newBox(Component.translatable("screen.noobs_container_searcher.locations_name"), "screen.noobs_container_searcher.locations_name_hint");
		radiusBox = newBox(Component.translatable("screen.noobs_container_searcher.locations_radius_label"), "screen.noobs_container_searcher.locations_radius_hint");
		radiusBox.setResponder(value -> {
			if (!syncingEditor && editing != null && value.matches("[0-9]{1,4}")) {
				editing.radius = Math.max(1, Math.min(MAX_LOCATION_RADIUS, Integer.parseInt(value)));
				locationEdited();
			}
		});
		hexBox = newBox(Component.translatable("screen.noobs_container_searcher.locations_color_label"), "screen.noobs_container_searcher.locations_hex_hint");
		hexBox.setResponder(value -> {
			String hex = value.startsWith("#") ? value.substring(1) : value;
			if (!syncingEditor && editing != null && hex.matches("[0-9a-fA-F]{6}")) {
				editing.color = 0xFF000000 | Integer.parseInt(hex, 16);
				locationEdited();
			}
		});
		iconSearchBox = newBox(Component.translatable("screen.noobs_container_searcher.locations_icon_label"), "screen.noobs_container_searcher.locations_icon_hint");
		iconSearchBox.setResponder(value -> {
			iconScroll = 0;
			refreshIconResults();
		});

		numberBox = newBox(Component.translatable("screen.noobs_container_searcher.distance_filter"), "screen.noobs_container_searcher.number_hint");
		numberBox.setMaxLength(6);
		numberBox.setVisible(false);
		numberBox.setResponder(value -> {
			if (!value.matches("[0-9]{1,6}")) {
				return;
			}
			int number = Integer.parseInt(value);
			if (numberEdit == NumberEdit.DISTANCE) {
				maxDistance = Math.max(1, Math.min(MAX_CUSTOM_DISTANCE, number));
				resultScroll = 0;
				rebuild();
			} else if (numberEdit == NumberEdit.DURATION) {
				ModSettings.get().setHighlightSeconds(Math.max(1, Math.min(MAX_DURATION_SECONDS, number)));
			}
		});

		loadEnchantments();
		switchTab(Tab.SEARCH);
	}

	// pick the biggest gui scale that still leaves FIT_WIDTH x FIT_HEIGHT, put the player's scale back on close
	private void fitGuiScale() {
		var window = minecraft.getWindow();
		int current = (int) window.getGuiScale();
		if (userGuiScale < 0) {
			userGuiScale = current;
		}
		int fit = Math.max(1, Math.min(window.getWidth() / FIT_WIDTH, window.getHeight() / FIT_HEIGHT));
		if (fit != current) {
			window.setGuiScale(fit);
		}
		width = window.getGuiScaledWidth();
		height = window.getGuiScaledHeight();
	}

	@Override
	public void removed() {
		if (userGuiScale >= 0) {
			minecraft.getWindow().setGuiScale(userGuiScale);
			userGuiScale = -1;
		}
		super.removed();
	}

	private EditBox newBox(Component name, String hintKey) {
		EditBox box = new EditBox(font, 0, 0, 100, 10, name);
		box.setHint(Component.translatable(hintKey));
		box.setBordered(false);
		box.setTextColor(TEXT_PRIMARY);
		box.setMaxLength(128);
		addRenderableWidget(box);
		return box;
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String value = searchBox == null ? "" : searchBox.getValue();
		String filterValue = filterSearchBox == null ? "" : filterSearchBox.getValue();
		String rerollValue = rerollSearchBox == null ? "" : rerollSearchBox.getValue();
		String baseValue = locationNameBox == null ? "" : locationNameBox.getValue();
		super.resize(minecraft, width, height);
		searchBox.setValue(value);
		filterSearchBox.setValue(filterValue);
		rerollSearchBox.setValue(rerollValue);
		locationNameBox.setValue(baseValue);
		rebuild();
	}

	private void switchTab(Tab next) {
		tab = next;
		serversView = false;
		searchBox.setVisible(tab == Tab.SEARCH);
		filterSearchBox.setVisible(tab == Tab.SEARCH);
		rerollSearchBox.setVisible(tab == Tab.REROLL);
		locationNameBox.setVisible(tab == Tab.LOCATIONS);
		closeEditor();
		numberEdit = NumberEdit.NONE;
		numberBox.setVisible(false);
		focusBox(tab == Tab.SEARCH ? searchBox : tab == Tab.REROLL ? rerollSearchBox : null);
	}

	private void focusBox(EditBox target) {
		searchBox.setFocused(target == searchBox);
		filterSearchBox.setFocused(target == filterSearchBox);
		rerollSearchBox.setFocused(target == rerollSearchBox);
		locationNameBox.setFocused(target == locationNameBox);
		radiusBox.setFocused(target == radiusBox);
		hexBox.setFocused(target == hexBox);
		iconSearchBox.setFocused(target == iconSearchBox);
		numberBox.setFocused(target == numberBox);
		setFocused(target);
	}

	@Override
	protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {
		mx = mouseX;
		my = mouseY;
		frameLocations = null;
		hits.clear();
		scrollAreas.clear();
		g.fill(0, 0, width, height, BACKDROP);

		int pw = panelWidth();
		int ph = panelHeight();
		int left = panelLeft();
		int top = panelTop();
		rfill(g, left - 1, top - 1, pw + 2, ph + 2, 0xFF000000);
		rfill(g, left, top, pw, ph, BORDER);
		rfill(g, left + 1, top + 1, pw - 2, ph - 2, PANEL);

		renderHeader(g, left, top, pw);
		int footerY = top + ph - FOOTER_HEIGHT;
		g.fill(left + 1, footerY, left + pw - 1, footerY + 1, BORDER);
		g.fill(left + 1, footerY + 1, left + pw - 1, top + ph - 1, HEADER);

		int bx = left + 12;
		int by = top + HEADER_HEIGHT + 4;
		int bw = pw - 24;
		int bh = footerY - by - 6;
		g.enableScissor(left + 1, top + HEADER_HEIGHT, left + pw - 1, top + ph - 1);
		if (ServerRules.modDisabled()) {
			searchBox.setVisible(false);
			filterSearchBox.setVisible(false);
			rerollSearchBox.setVisible(false);
			locationNameBox.setVisible(false);
			renderDisabledNotice(g, bx, by, bw, bh, footerY, true);
		} else switch (tab) {
			case SEARCH -> renderSearchTab(g, bx, by, bw, bh, footerY);
			case REROLL -> renderRerollTab(g, bx, by, bw, bh, footerY);
			case LOCATIONS -> renderLocationsTab(g, bx, by, bw, bh, footerY);
			case SETTINGS -> renderSettingsTab(g, bx, by, bw, bh, footerY);
		}
		g.disableScissor();
		renderWidgets(mouseX, mouseY, partialTick);
		applySmoothScroll();
	}

	private void renderHeader(Gfx g, int left, int top, int pw) {
		g.fill(left + 1, top + 1, left + pw - 1, top + HEADER_HEIGHT - 1, HEADER);
		g.fill(left + 1, top + HEADER_HEIGHT - 1, left + pw - 1, top + HEADER_HEIGHT, BORDER);
		g.logo(left + 10, top + 6, 22);
		Component bold = title.copy().withStyle(ChatFormatting.BOLD);
		g.text(font, bold, left + 38, top + 13, TEXT_PRIMARY);

		int tx = left + 38 + font.width(bold) + 24;
		tx = tabButton(g, Tab.SEARCH, Component.translatable("screen.noobs_container_searcher.tab_search"), tx, top, false);
		tx = tabButton(g, Tab.REROLL, Component.translatable("screen.noobs_container_searcher.tab_reroll"), tx, top,
			reroll.state() != LibrarianRerollController.State.IDLE);
		tx = tabButton(g, Tab.LOCATIONS, Component.translatable("screen.noobs_container_searcher.tab_locations"), tx, top, false);
		tabButton(g, Tab.SETTINGS, Component.translatable("screen.noobs_container_searcher.tab_settings"), tx, top, false);

		int cx = left + pw - 30;
		int cy = top + 7;
		boolean hover = hovered(cx, cy, 20, 20);
		card(g, cx, cy, 20, 20, hover ? SURFACE_HI : SURFACE, hover ? BORDER_HI : BORDER);
		icon(g, ICON_CLOSE, cx + 7, cy + 7, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
		hit(cx, cy, 20, 20, this::onClose);

		// always visible, the Community card in Settings only shows when the page has room
		int dx = cx - 26;
		boolean discordHover = hovered(dx, cy, 20, 20);
		card(g, dx, cy, 20, 20, discordHover ? SURFACE_HI : SURFACE, discordHover ? DISCORD_HOVER : BORDER);
		g.icon("icon_discord", dx + 2, cy + 2, 16);
		hit(dx, cy, 20, 20, this::openDiscord);
		if (discordHover) {
			g.tooltip(font, List.of(Component.translatable("screen.noobs_container_searcher.discord_tooltip")), mx, my);
		}
	}

	private int tabButton(Gfx g, Tab target, Component label, int x, int top, boolean live) {
		int tw = font.width(label) + 24 + (live ? 8 : 0);
		boolean active = tab == target;
		boolean hover = hovered(x, top + 4, tw, HEADER_HEIGHT - 4);
		if (active) {
			g.fill(x, top + 8, x + tw, top + HEADER_HEIGHT - 1, SURFACE_HI);
		} else if (hover) {
			g.fill(x, top + 10, x + tw, top + HEADER_HEIGHT - 1, SURFACE);
		}
		int textX = x + 12;
		if (live) {
			g.fill(textX, top + 19, textX + 4, top + 23, SUCCESS);
			textX += 8;
		}
		g.text(font, label, textX, top + 16, active ? TEXT_PRIMARY : TEXT_SECONDARY);
		hit(x, top + 4, tw, HEADER_HEIGHT - 4, () -> switchTab(target));
		return x + tw + 2;
	}

	private void footer(Gfx g, int footerY, String left, String right) {
		g.text(font, trim(left, panelWidth() - 40 - font.width(right)), panelLeft() + 12, footerY + 7, TEXT_MUTED);
		g.text(font, right, panelLeft() + panelWidth() - 12 - font.width(right), footerY + 7, TEXT_SECONDARY);
	}

	private void renderSearchTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_search").getString(),
			Component.translatable("screen.noobs_container_searcher.footer_results", rows.size(), resultContainerCount).getString());

		int colW = Math.min(184, bw / 3);
		renderFilterColumn(g, bx, by, colW, bh);
		g.fill(bx + colW + 8, by, bx + colW + 9, by + bh, BORDER);

		int rx = bx + colW + 18;
		int rw = bx + bw - rx;
		searchField(g, searchBox, rx, by, rw, 22, true);

		int ty = by + 28;
		g.text(font, Component.translatable("screen.noobs_container_searcher.try"), rx, ty + 2, TEXT_MUTED);
		int tagX = rx + font.width(Component.translatable("screen.noobs_container_searcher.try")) + 6;
		for (String snippet : new String[] {"name:", "lore:", "durability:<50", "enchanted", "include:", "exclude:"}) {
			if (tagX + font.width(snippet) + 8 > rx + rw) {
				break;
			}
			final String insert = snippet;
			tagX += tag(g, tagX, ty, snippet, TEXT_SECONDARY, SURFACE, 12, () -> {
				String current = searchBox.getValue().trim();
				searchBox.setValue(current.isEmpty() ? insert : current + " " + insert);
				focusBox(searchBox);
			}) + 4;
		}

		renderDistanceRow(g, rx, by + 46, rw);
		renderActiveFilters(g, rx, by + 74, rw);

		int sy = by + 94;
		g.fill(rx, sy + 11, rx + rw, sy + 12, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.results", rows.size()), rx, sy + 2, TEXT_SECONDARY);
		if (viewingKey != null) {
			int bannerX = rx + font.width(Component.translatable("screen.noobs_container_searcher.results", rows.size())) + 12;
			int sortReserve = font.width(Component.translatable("screen.noobs_container_searcher.sort_distance")) + 40;
			String banner = Component.translatable("screen.noobs_container_searcher.servers_viewing", serverDisplayName(viewingKey)).getString() + "  x";
			tag(g, bannerX, sy - 1, trim(banner, Math.max(40, rx + rw - sortReserve - bannerX - 8)), DANGER_HOVER, DANGER_BG, 12, this::endViewing);
		}
		String sortLabel = Component.translatable(sort == Sort.NAME
			? "screen.noobs_container_searcher.sort_name" : "screen.noobs_container_searcher.sort_distance").getString();
		int sortW = font.width(sortLabel) + 22;
		boolean sortHover = hovered(rx + rw - sortW, sy - 2, sortW, 14);
		card(g, rx + rw - sortW, sy - 2, sortW, 14, sortHover ? SURFACE_HI : SURFACE, BORDER);
		g.text(font, sortLabel, rx + rw - sortW + 6, sy + 1, TEXT_SECONDARY);
		icon(g, ICON_CHEVRON, rx + rw - 12, sy + 3, TEXT_MUTED);
		hit(rx + rw - sortW, sy - 2, sortW, 14, () -> {
			sort = sort == Sort.NAME ? Sort.DISTANCE : Sort.NAME;
			resultScroll = 0;
			rebuild();
		});

		int ly = by + 112;
		renderResults(g, rx, ly, rw, Math.max(RESULT_HEIGHT, by + bh - ly));
	}

	private void renderFilterColumn(Gfx g, int x, int by, int colW, int bh) {
		searchField(g, filterSearchBox, x, by, colW, 18, false);

		int y = by + 26;
		caption(g, "screen.noobs_container_searcher.quick_filters", x, y);
		y += 12;
		List<ChipSpec> quick = new ArrayList<>();
		for (FilterEntry filter : filters) {
			if (!filter.id.startsWith("ench:")) {
				quick.add(new ChipSpec(filter.name, filter.count, filterColor(filter), filter.id.equals(selectedFilter), false, () -> toggleFilter(filter)));
			}
		}
		for (ModSettings.Location loc : currentLocations()) {
			quick.add(new ChipSpec(loc.name, locationCount(loc), loc.color, selectedLocations.contains(loc), false, () -> toggleLocation(loc)));
		}
		y += flowChips(g, quick, x, y, colW, true) + 6;

		List<ChipSpec> types = new ArrayList<>();
		for (ContainerTypeEntry type : containerTypes) {
			boolean excluded = ModSettings.get().isTypeExcluded(type.key);
			types.add(new ChipSpec(type.name, type.count, containerColor(type.name), !excluded && selectedContainerTypeKeys.contains(type.key), false, () -> toggleContainerType(type), excluded));
		}
		// the empty notice needs its own row, otherwise it sits on the footer divider
		int typesAreaH = types.isEmpty() ? 12 : Math.min(flowChips(g, types, x, 0, colW, false), 54);
		int typesTitleY = by + bh - typesAreaH - 14;

		caption(g, "screen.noobs_container_searcher.enchantments", x, y);
		y += 12;
		int listH = Math.max(ENCHANT_ROW, typesTitleY - y - 6);
		renderEnchantmentList(g, x, y, colW, listH);

		caption(g, "screen.noobs_container_searcher.container_types", x, typesTitleY);
		String excludeLabel = Component.translatable("screen.noobs_container_searcher.mode_exclude").getString();
		String includeLabel = Component.translatable("screen.noobs_container_searcher.mode_include").getString();
		int excludeW = font.width(excludeLabel) + 8;
		int includeW = font.width(includeLabel) + 8;
		int modeX = x + colW - excludeW - includeW - 2;
		tag(g, modeX, typesTitleY - 3, includeLabel, excludeMode ? TEXT_MUTED : TEXT_PRIMARY, excludeMode ? SURFACE : ACCENT_BG, 12, () -> excludeMode = false);
		tag(g, modeX + includeW + 2, typesTitleY - 3, excludeLabel, excludeMode ? DANGER_HOVER : TEXT_MUTED, excludeMode ? DANGER_BG : SURFACE, 12, () -> excludeMode = true);
		int chipsY = typesTitleY + 12;
		if (types.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_container_types"), x + 2, chipsY + 3, TEXT_MUTED);
			return;
		}
		g.enableScissor(x, chipsY, x + colW, chipsY + typesAreaH);
		int total = flowChips(g, types, x, chipsY - (int) containerTypeScroll, colW, true);
		g.disableScissor();
		containerTypeScroll = clampScroll(containerTypeScroll, total, typesAreaH);
		scrollArea(x, chipsY, colW, typesAreaH, amount -> containerTypeScroll = clampScroll(containerTypeScroll - amount * 18, total, typesAreaH));
	}

	private void renderEnchantmentList(Gfx g, int x, int y, int colW, int listH) {
		List<FilterEntry> enchants = new ArrayList<>();
		for (FilterEntry filter : filters) {
			if (filter.id.startsWith("ench:")) {
				enchants.add(filter);
			}
		}
		int content = enchants.size() * ENCHANT_ROW;
		filterScroll = clampScroll(filterScroll, content, listH);
		boolean bar = content > listH;
		int rowW = colW - (bar ? 6 : 0);
		g.enableScissor(x, y, x + colW, y + listH);
		int start = Math.max(0, (int) (filterScroll / ENCHANT_ROW));
		int offset = y - (int) (filterScroll % ENCHANT_ROW);
		for (int i = start; i < enchants.size(); i++) {
			int ry = offset + (i - start) * ENCHANT_ROW;
			if (ry >= y + listH) {
				break;
			}
			FilterEntry filter = enchants.get(i);
			boolean selected = filter.id.equals(selectedFilter);
			boolean hover = hovered(x, Math.max(ry, y), rowW, ENCHANT_ROW - 1) && my < y + listH;
			rfill(g, x, ry, rowW, ENCHANT_ROW - 1, selected ? ACCENT_BG : hover ? SURFACE_HI : i % 2 == 0 ? SURFACE : PANEL, 1);
			String count = Integer.toString(filter.count);
			int nameX = x + 8;
			if (selected) {
				icon(g, ICON_CHECK, x + 7, ry + 5, ACCENT_HOVER);
				nameX = x + 18;
			}
			g.text(font, trim(filter.name, rowW - (nameX - x) - font.width(count) - 12), nameX, ry + 4, selected ? TEXT_PRIMARY : ENCHANT_PURPLE);
			g.text(font, count, x + rowW - 6 - font.width(count), ry + 4, TEXT_MUTED);
			hitClipped(x, ry, rowW, ENCHANT_ROW - 1, x, y, colW, listH, () -> toggleFilter(filter));
		}
		g.disableScissor();
		if (enchants.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_filters"), x + 2, y + 3, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, x + colW - 3, y, listH, filterScroll, content);
		}
		scrollArea(x, y, colW, listH, amount -> filterScroll = clampScroll(filterScroll - amount * ENCHANT_ROW, content, listH));
	}

	private void renderActiveFilters(Gfx g, int x, int y, int width) {
		boolean any = selectedFilter != null || !selectedContainerTypeKeys.isEmpty() || !selectedLocations.isEmpty();
		String label = Component.translatable("screen.noobs_container_searcher.active").getString();
		g.text(font, label, x, y + 3, TEXT_MUTED);
		int cx = x + font.width(label) + 6;
		if (!any) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.active_none"), cx, y + 3, TEXT_MUTED);
			return;
		}
		if (selectedFilter != null) {
			cx += chip(g, cx, y, selectedFilterLabel, -1, selectedFilter.startsWith("ench:") ? ENCHANT_PURPLE : TEXT_SECONDARY, true, true, () -> {
				selectedFilter = null;
				resultScroll = 0;
				rebuild();
			}) + 4;
		}
		for (ModSettings.Location loc : new ArrayList<>(selectedLocations)) {
			if (cx > x + width - 80) {
				break;
			}
			cx += chip(g, cx, y, loc.name, -1, loc.color, true, true, () -> toggleLocation(loc)) + 4;
		}
		for (Map.Entry<String, String> type : new ArrayList<>(selectedTypeLabels.entrySet())) {
			if (!selectedContainerTypeKeys.contains(type.getKey()) || cx > x + width - 80) {
				continue;
			}
			cx += chip(g, cx, y, type.getValue(), -1, containerColor(type.getValue()), true, true, () -> {
				selectedContainerTypeKeys.remove(type.getKey());
				resultScroll = 0;
				rebuild();
			}) + 4;
		}
		String clear = Component.translatable("screen.noobs_container_searcher.clear_all").getString();
		int clearW = font.width(clear);
		boolean hover = hovered(cx + 4, y, clearW, 14);
		g.text(font, clear, cx + 4, y + 3, hover ? TEXT_PRIMARY : ACCENT_HOVER);
		hit(cx + 4, y, clearW, 14, () -> {
			selectedFilter = null;
			selectedContainerTypeKeys.clear();
			selectedLocations.clear();
			selectedTypeLabels.clear();
			resultScroll = 0;
			rebuild();
		});
	}

	private void toggleFilter(FilterEntry filter) {
		if (filter.id.equals(selectedFilter)) {
			selectedFilter = null;
		} else {
			selectedFilter = filter.id;
			selectedFilterLabel = filter.name;
		}
		resultScroll = 0;
		rebuild();
	}

	private void toggleContainerType(ContainerTypeEntry type) {
		ModSettings settings = ModSettings.get();
		if (settings.isTypeExcluded(type.key)) {
			settings.setTypeExcluded(type.key, false);
		} else if (excludeMode) {
			settings.setTypeExcluded(type.key, true);
			selectedContainerTypeKeys.remove(type.key);
		} else if (!selectedContainerTypeKeys.remove(type.key)) {
			selectedContainerTypeKeys.add(type.key);
			selectedTypeLabels.put(type.key, type.name);
		}
		resultScroll = 0;
		rebuild();
	}

	private void renderResults(Gfx g, int x, int y, int width, int height) {
		int content = rows.size() * RESULT_STRIDE;
		resultScroll = clampScroll(resultScroll, content, height);
		boolean bar = content > height;
		int rowW = width - (bar ? 8 : 0);
		g.enableScissor(x, y, x + width, y + height);
		int start = Math.max(0, (int) (resultScroll / RESULT_STRIDE));
		int offset = y - (int) (resultScroll % RESULT_STRIDE);
		for (int i = start; i < rows.size(); i++) {
			int ry = offset + (i - start) * RESULT_STRIDE;
			if (ry >= y + height) {
				break;
			}
			renderResultRow(g, rows.get(i), x, ry, rowW, y, height);
		}
		g.disableScissor();
		if (rows.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), x + 4, y + 6, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, x + width - 3, y, height, resultScroll, content);
		}
		scrollArea(x, y, width, height, amount -> resultScroll = clampScroll(resultScroll - amount * RESULT_STRIDE, content, height));
	}

	private void renderResultRow(Gfx g, ResultRow row, int x, int y, int w, int viewY, int viewH) {
		boolean inView = my >= viewY && my < viewY + viewH;
		boolean hover = inView && hovered(x, y, w, RESULT_HEIGHT);
		card(g, x, y, w, RESULT_HEIGHT, hover ? SURFACE_HI : SURFACE, hover ? ACCENT : BORDER);
		ModSettings.Location loc = locationFor(row.record);
		if (loc != null) {
			g.enableScissor(x, y, x + 3, y + RESULT_HEIGHT);
			rfill(g, x, y, w, RESULT_HEIGHT, loc.color);
			g.disableScissor();
		}

		g.fill(x + 5, y + 4, x + 25, y + 24, INPUT_BG);
		g.outline(x + 5, y + 4, 20, 20, row.item.enchanted ? BORDER_HI : BORDER);
		Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(row.itemId));
		ItemStack stack = ItemStack.EMPTY;
		if (item != null) {
			stack = stackCache.computeIfAbsent(row.item, rec -> displayStack(item, rec));
			g.fakeItem(stack, x + 7, y + 6);
			g.itemDecorations(font, stack, x + 7, y + 6, "");
			if (inView && hovered(x + 5, y + 4, 20, 20)) {
				g.tooltip(font, tooltip(stack), mx, my);
			}
		}

		int rightX = x + w - 8;
		int oneX = 0;
		int oneW = 0;
		int allX = 0;
		int allW = 0;
		if (viewingKey != null) {
			// another server's data: nothing here can be located in this world
		} else if (hover) {
			// settings decide which buttons exist; "all" is skipped when it would match "locate"
			boolean wantOne = ModSettings.locateOneEnabled();
			if (multiRow != row) {
				multiRow = row;
				multiValue = recordsFor(row).size() > singleRecordFor(row).size();
			}
			boolean multi = multiValue;
			if (ModSettings.locateAllEnabled() && !row.record.blockGone && (multi || !wantOne)) {
				String all = Component.translatable("screen.noobs_container_searcher.locate_all").getString();
				allW = font.width(all) + 28;
				allX = rightX - allW;
				drawButton(g, allX, y + 4, allW, 20, all, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, ICON_ARROW);
				rightX -= allW + 4;
			}
			if (wantOne || allW == 0) {
				String locate = Component.translatable("screen.noobs_container_searcher.locate").getString();
				oneW = font.width(locate) + 28;
				oneX = rightX - oneW;
				drawButton(g, oneX, y + 4, oneW, 20, locate, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, ICON_ARROW);
				rightX -= oneW + 8;
			} else {
				rightX -= 4;
			}
		} else {
			double distance = distanceTo(row.record);
			if (distance != -2) {
				String badge;
				int fg;
				int bg;
				if (distance < 0) {
					badge = Component.translatable("screen.noobs_container_searcher.other_dimension").getString();
					fg = 0xFFFFB3B3;
					bg = DANGER_BG;
				} else {
					badge = Component.translatable("screen.noobs_container_searcher.distance_short", Math.round(distance)).getString();
					boolean near = distance < 100;
					fg = near ? 0xFF8CE6A8 : 0xFFFFD98C;
					bg = near ? 0xFF1C3326 : 0xFF3A2F18;
				}
				int bw = font.width(badge) + 8;
				tag(g, rightX - bw, y + 8, badge, fg, bg, 12, null);
				rightX -= bw + 8;
			}
			if (loc != null) {
				String label = trim(loc.name, 90);
				int bw = font.width(label) + 8;
				g.fakeItem(locationIcon(loc), rightX - bw - 18, y + 6);
				tag(g, rightX - bw, y + 8, label, loc.color, (loc.color & 0xFFFFFF) | 0x40000000, 12, null);
				rightX -= bw + 8;
				rightX -= 18;
			}
		}

		int countW = row.count > 1 ? font.width("x" + row.count) + 5 : 0;
		int nameMax = Math.max(40, rightX - x - 36 - countW);
		String name = trim(row.name, nameMax);
		g.text(font, name, x + 32, y + 4, itemNameColor(stack, row.item));
		int afterName = x + 32 + font.width(name) + 5;
		if (row.count > 1) {
			g.text(font, "x" + row.count, afterName, y + 4, TEXT_SECONDARY);
			afterName += countW;
		}
		if (row.item.inside != null && afterName + 20 < rightX) {
			String inside = trim(Component.translatable("screen.noobs_container_searcher.inside_box", row.item.inside).getString(), rightX - afterName - 4);
			g.text(font, inside, afterName, y + 4, containerColor("shulker"));
		}

		int cx = x + 32;
		ItemStack containerIcon = containerIcon(row.record.containerType);
		if (!containerIcon.isEmpty()) {
			g.fakeItem(containerIcon, cx, y + 13);
			cx += 19;
		}
		String container = containerName(row.record.containerType).getString();
		int whereMax = Math.max(40, rightX - cx - 8);
		String containerText = trim(container, whereMax);
		g.text(font, containerText, cx, y + 17, containerColor(container));
		cx += font.width(containerText);
		if (row.record.blockGone) {
			String gone = " " + Component.translatable("screen.noobs_container_searcher.ender_gone_tag").getString();
			g.text(font, gone, cx, y + 17, DANGER_HOVER);
			cx += font.width(gone);
		}
		String coords = trim("  " + row.record.x + " " + row.record.y + " " + row.record.z, Math.max(0, whereMax - font.width(containerText)));
		g.text(font, coords, cx, y + 17, TEXT_SECONDARY);
		cx += font.width(coords) + 8;
		String dimension = shortDimension(row.record.dimension);
		String more = dimension + durabilitySuffix(row.item);
		if (cx + font.width(more) < rightX) {
			g.text(font, more, cx, y + 17, TEXT_MUTED);
		}
		// plain row click follows whichever button is on (the single one wins when both are)
		boolean rowAll = !ModSettings.locateOneEnabled();
		hitClipped(x, y, w, RESULT_HEIGHT, x, viewY, w, viewH, () -> locateRow(row, rowAll));
		// later hits win, so the buttons go after the row itself
		if (oneW > 0) {
			hitClipped(oneX, y + 4, oneW, 20, x, viewY, w, viewH, () -> locateRow(row, false));
		}
		if (allW > 0) {
			hitClipped(allX, y + 4, allW, 20, x, viewY, w, viewH, () -> locateRow(row, true));
		}
	}

	private void locateRow(ResultRow row, boolean all) {
		if (viewingKey != null) {
			return;
		}
		if (row.record.blockGone) {
			if (minecraft != null && minecraft.player != null) {
				Compat.message(minecraft.player, Component.translatable("message.noobs_container_searcher.ender_gone").withStyle(ChatFormatting.YELLOW));
			}
			onClose();
			return;
		}
		List<ContainerRecord> targets = all ? recordsFor(row) : singleRecordFor(row);
		controller.focusItem(row.itemId, targets, selectedFilter, row.item, all);
		onClose();
	}

	// just the clicked container (every ender chest for the ender inventory, they share it)
	private List<ContainerRecord> singleRecordFor(ResultRow row) {
		if (isEnderChest(row.record)) {
			List<ContainerRecord> enders = new ArrayList<>();
			for (ContainerRecord other : records) {
				if (isEnderChest(other) && !other.blockGone) {
					enders.add(other);
				}
			}
			return enders;
		}
		return List.of(row.record);
	}

	// chest/shulker/etc. show their own block item, villagers show the spawn egg
	private static ItemStack containerIcon(String storedType) {
		if (storedType == null || storedType.isBlank()) {
			return ItemStack.EMPTY;
		}
		String id = storedType.startsWith("villager:") ? "minecraft:villager_spawn_egg" : storedType;
		ResourceLocation identifier = ResourceLocation.tryParse(id);
		if (identifier == null) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(identifier);
		return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	private static String durabilitySuffix(ContainerItemRecord item) {
		return item.damageable && item.maxDamage > 0 ? "  " + durabilityPercent(item) + "%" : "";
	}

	private List<ModSettings.Location> currentLocations() {
		if (viewingKey != null || minecraft == null || minecraft.level == null) {
			return List.of();
		}
		// serverKey is not free and every result row asks, so look it up once per frame (reset in renderContent/rebuild)
		if (frameLocations == null) {
			frameLocations = ModSettings.get().locationsFor(ContainerSearcherClient.serverKey(minecraft));
		}
		return frameLocations;
	}

	// closest loc whose radius covers the container, null if none (or coloring is off)
	private ModSettings.Location locationFor(ContainerRecord record) {
		if (viewingKey != null || !ModSettings.get().locationColors) {
			return null;
		}
		ModSettings.Location best = null;
		double bestDist = Double.MAX_VALUE;
		for (ModSettings.Location loc : currentLocations()) {
			if (!loc.dimension.equals(record.dimension)) {
				continue;
			}
			double dx = record.x - loc.x;
			double dy = record.y - loc.y;
			double dz = record.z - loc.z;
			double dist = dx * dx + dy * dy + dz * dz;
			if (dist <= (double) loc.radius * loc.radius && dist < bestDist) {
				best = loc;
				bestDist = dist;
			}
		}
		return best;
	}

	private static boolean isEnderChest(ContainerRecord record) {
		return "minecraft:ender_chest".equals(record.containerType);
	}

	// a standing ender chest in this dimension, nearest first; then any standing one; a broken one only as a last resort
	private ContainerRecord enderRepresentative() {
		ContainerRecord best = null;
		double bestScore = Double.MAX_VALUE;
		for (ContainerRecord record : records) {
			if (!isEnderChest(record)) {
				continue;
			}
			double distance = distanceTo(record);
			double score = (record.blockGone ? 2e12 : 0) + (distance < 0 ? 1e12 : distance);
			if (score < bestScore) {
				bestScore = score;
				best = record;
			}
		}
		return best;
	}

	private double distanceTo(ContainerRecord record) {
		if (viewingKey != null) {
			return -2;
		}
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return -2;
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return -1;
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private void renderDistanceRow(Gfx g, int x, int y, int width) {
		String label = Component.translatable("screen.noobs_container_searcher.distance_filter").getString();
		g.text(font, label, x, y + 6, TEXT_SECONDARY);

		int cx = x + width;
		for (int i = DISTANCE_PRESETS.length - 1; i >= 0; i--) {
			int preset = DISTANCE_PRESETS[i];
			String text = preset == UNLIMITED_DISTANCE
				? Component.translatable("screen.noobs_container_searcher.distance_any").getString()
				: Integer.toString(preset);
			int cw = font.width(text) + 14;
			cx -= cw;
			boolean active = maxDistance == preset;
			boolean hover = hovered(cx, y, cw, 20);
			card(g, cx, y, cw, 20, active ? ACCENT : hover ? SURFACE_HI : SURFACE, active ? ACCENT_HOVER : BORDER);
			g.centeredText(font, Component.literal(text), cx + cw / 2, y + 6, active ? TEXT_PRIMARY : TEXT_SECONDARY);
			hit(cx, y, cw, 20, () -> {
				maxDistance = preset;
				resultScroll = 0;
				rebuild();
			});
			cx -= 3;
		}

		int pillW = distancePillWidth();
		sliderX = x + font.width(label) + 10;
		sliderWidth = Math.max(40, cx - sliderX - pillW - 8);
		int trackY = y + 10;
		g.fill(sliderX, trackY - 2, sliderX + sliderWidth, trackY + 2, INPUT_BG);
		g.outline(sliderX, trackY - 2, sliderWidth, 4, BORDER);
		int knob = distanceKnobX();
		g.fill(sliderX + 1, trackY - 1, knob, trackY + 1, ACCENT);
		boolean hover = hovered(sliderX, y + 2, sliderWidth, 16) || draggingDistanceSlider;
		rfill(g, knob - 3, trackY - 6, 7, 12, INPUT_BG, 1);
		rfill(g, knob - 2, trackY - 5, 5, 10, hover ? TEXT_PRIMARY : ACCENT_HOVER, 1);
		hit(sliderX - 3, y + 2, sliderWidth + 7, 16, () -> {
			draggingDistanceSlider = true;
			updateDistanceSlider(mx);
		});

		numberPill(g, sliderX + sliderWidth + 8, y + 2, pillW, NumberEdit.DISTANCE, distanceValueText(),
			maxDistance == UNLIMITED_DISTANCE ? "" : Integer.toString(maxDistance));
	}

	private String distanceValueText() {
		return maxDistance == UNLIMITED_DISTANCE
			? Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString()
			: Component.translatable("screen.noobs_container_searcher.distance_value", maxDistance).getString();
	}

	private int distancePillWidth() {
		String longest = Component.translatable("screen.noobs_container_searcher.distance_value", MAX_CUSTOM_DISTANCE).getString();
		String unlimited = Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString();
		return Math.max(font.width(longest), font.width(unlimited)) + 14;
	}

	private int distanceKnobX() {
		if (maxDistance == UNLIMITED_DISTANCE) {
			return sliderX + sliderWidth;
		}
		double ratio = Math.max(0, Math.min(1, maxDistance / (double) MAX_DISTANCE));
		return sliderX + (int) Math.round(ratio * sliderWidth);
	}

	private void updateDistanceSlider(double mouseX) {
		double ratio = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
		int newDistance = ratio >= 0.97D ? UNLIMITED_DISTANCE : Math.max(25, (int) Math.round(ratio * MAX_DISTANCE / 25.0D) * 25);
		if (newDistance != maxDistance) {
			maxDistance = newDistance;
			resultScroll = 0;
			rebuild();
		}
	}

	private void renderDisabledNotice(Gfx g, int bx, int by, int bw, int bh, int footerY, boolean wholeMod) {
		footer(g, footerY, "", "");
		int cx = bx + bw / 2;
		int size = 40;
		int top = by + Math.max(8, (bh - size - 62) / 2);
		int left = cx - size / 2;
		for (int i = 0; i < size; i += 4) {
			g.fill(left + i, top + i, left + i + 6, top + i + 6, DANGER);
			g.fill(left + size - 4 - i, top + i, left + size + 2 - i, top + i + 6, DANGER);
		}
		String[] lines = {
			Component.translatable("screen.noobs_container_searcher.disabled_title").getString(),
			Component.translatable(wholeMod
				? "screen.noobs_container_searcher.disabled_mod_text"
				: "screen.noobs_container_searcher.disabled_reroll_text").getString(),
			Component.translatable("screen.noobs_container_searcher.disabled_contact").getString()
		};
		int y = top + size + 16;
		for (int i = 0; i < lines.length; i++) {
			String line = trim(lines[i], bw - 24);
			g.text(font, line, cx - font.width(line) / 2, y, i == 0 ? DANGER_HOVER : i == 1 ? TEXT_PRIMARY : TEXT_MUTED);
			y += i == 0 ? 16 : 13;
		}
	}

	private void renderRerollTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		if (ServerRules.rerollDisabled()) {
			rerollSearchBox.setVisible(false);
			renderDisabledNotice(g, bx, by, bw, bh, footerY, false);
			return;
		}
		if (seenSelectionRevision != ModSettings.get().selectionRevision()) {
			syncSelection();
		}
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_reroll").getString(),
			Component.translatable("screen.noobs_container_searcher.reroll_book_count", visibleChoices.size()).getString());

		int sideW = Math.min(190, bw / 3);
		int sideX = bx + bw - sideW;
		int listW = sideX - bx - 14;

		searchField(g, rerollSearchBox, bx, by, Math.min(300, listW), 22, true);
		String multi = Component.translatable("screen.noobs_container_searcher.reroll_multi_hint").getString();
		g.text(font, multi, bx + listW - font.width(multi), by + 7, TEXT_MUTED);

		int gy = by + 30;
		int gh = by + bh - gy;
		int cols = Math.max(1, Math.min(3, (listW + 5) / 160));
		int cardW = (listW - (cols - 1) * 5) / cols;
		int content = ((visibleChoices.size() + cols - 1) / cols) * REROLL_CARD_STRIDE;
		rerollScroll = clampScroll(rerollScroll, content, gh);
		boolean bar = content > gh;
		g.enableScissor(bx, gy, bx + listW + 6, gy + gh);
		for (int i = 0; i < visibleChoices.size(); i++) {
			int row = i / cols;
			int cardY = gy + row * REROLL_CARD_STRIDE - (int) rerollScroll;
			if (cardY + REROLL_CARD_HEIGHT < gy) {
				continue;
			}
			if (cardY >= gy + gh) {
				break;
			}
			int cardX = bx + (i % cols) * (cardW + 5);
			EnchantmentChoice choice = visibleChoices.get(i);
			boolean selected = selectedChoices.contains(choice);
			boolean hover = my >= gy && my < gy + gh && hovered(cardX, cardY, cardW, REROLL_CARD_HEIGHT);
			card(g, cardX, cardY, cardW, REROLL_CARD_HEIGHT, selected ? ACCENT_BG : hover ? SURFACE_HI : SURFACE, selected ? ACCENT : BORDER);
			g.text(font, trim(choice.name, cardW - 30), cardX + 8, cardY + 4, selected ? ENCHANT_PURPLE : TEXT_PRIMARY);
			g.text(font, trim(choice.id.getPath(), cardW - 22), cardX + 8, cardY + 14, TEXT_MUTED);
			if (selected) {
				icon(g, ICON_CHECK, cardX + cardW - 12, cardY + 5, ACCENT_HOVER);
			}
			hitClipped(cardX, cardY, cardW, REROLL_CARD_HEIGHT, bx, gy, listW, gh, () -> {
				if (ctrlDown) {
					if (!selectedChoices.remove(choice)) {
						selectedChoices.add(choice);
					}
				} else if (selectedChoices.size() == 1 && selected) {
					selectedChoices.clear();
				} else {
					selectedChoices.clear();
					selectedChoices.add(choice);
				}
				persistSelection();
			});
		}
		g.disableScissor();
		if (visibleChoices.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), bx + 4, gy + 6, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, bx + listW + 4, gy, gh, rerollScroll, content);
		}
		scrollArea(bx, gy, listW + 6, gh, amount -> rerollScroll = clampScroll(rerollScroll - amount * REROLL_CARD_STRIDE, content, gh));

		renderRerollSide(g, sideX, by, sideW, bh);
	}

	private void renderRerollSide(Gfx g, int x, int by, int w, int bh) {
		g.fill(x - 8, by, x - 7, by + bh, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.selected_books_title").withStyle(ChatFormatting.BOLD), x, by + 2, TEXT_PRIMARY);

		int y = by + 18;
		if (selectedChoices.isEmpty()) {
			g.text(font, trim(Component.translatable("screen.noobs_container_searcher.no_book_selected").getString(), w), x, y + 3, TEXT_MUTED);
			y += 18;
		}
		int shown = 0;
		for (EnchantmentChoice choice : new ArrayList<>(selectedChoices)) {
			if (shown == 4) {
				g.text(font, Component.translatable("screen.noobs_container_searcher.more", selectedChoices.size() - 4), x, y + 2, TEXT_MUTED);
				y += 14;
				break;
			}
			card(g, x, y, w, 18, SURFACE, BORDER);
			g.fill(x + 5, y + 5, x + 7, y + 13, ENCHANT_PURPLE);
			g.text(font, trim(choice.name, w - 30), x + 12, y + 5, TEXT_PRIMARY);
			boolean xHover = hovered(x + w - 16, y, 16, 18);
			icon(g, ICON_X, x + w - 12, y + 8, xHover ? TEXT_PRIMARY : TEXT_MUTED);
			hit(x + w - 16, y, 16, 18, () -> {
				selectedChoices.remove(choice);
				persistSelection();
			});
			y += 21;
			shown++;
		}
		if (selectedChoices.size() > 1) {
			String clear = Component.translatable("screen.noobs_container_searcher.clear_all").getString();
			boolean hover = hovered(x, y, font.width(clear), 12);
			g.text(font, clear, x, y + 2, hover ? TEXT_PRIMARY : ACCENT_HOVER);
			hit(x, y, font.width(clear), 12, () -> {
				selectedChoices.clear();
				persistSelection();
			});
			y += 16;
		}
		y += 6;
		caption(g, "screen.noobs_container_searcher.requirements", x, y);
		y += 12;
		y = requirement(g, x, y, w, "screen.noobs_container_searcher.req_lectern", hotbarHas(Items.LECTERN));
		y = requirement(g, x, y, w, "screen.noobs_container_searcher.req_axe", hotbarHas(Items.DIAMOND_AXE) || hotbarHas(Items.NETHERITE_AXE));
		y += 6;
		card(g, x, y, w, 36, SURFACE, BORDER);
		caption(g, "screen.noobs_container_searcher.status", x + 8, y + 6);
		g.text(font, trim(rerollStatusLine().getString(), w - 16), x + 8, y + 19,
			reroll.state() == LibrarianRerollController.State.IDLE ? TEXT_PRIMARY : SUCCESS);

		boolean canStart = !selectedChoices.isEmpty();
		drawButton(g, x, by + bh - 52, w, 22, Component.translatable("screen.noobs_container_searcher.select_villager").getString(),
			ACCENT, ACCENT_HOVER, ACCENT_DARK, canStart, ICON_PIN);
		if (canStart) {
			hit(x, by + bh - 52, w, 22, this::beginSelection);
		}
		boolean running = reroll.state() != LibrarianRerollController.State.IDLE;
		drawButton(g, x, by + bh - 26, w, 22, Component.translatable("screen.noobs_container_searcher.stop_reroll").getString(),
			DANGER, DANGER_HOVER, DANGER_DARK, running, null);
		if (running) {
			hit(x, by + bh - 26, w, 22, reroll::stopByUser);
		}
	}

	private int requirement(Gfx g, int x, int y, int w, String key, boolean ok) {
		icon(g, ok ? ICON_CHECK : ICON_X, x, y + 1, ok ? SUCCESS : DANGER);
		g.text(font, trim(Component.translatable(key).getString(), w - 12), x + 11, y, TEXT_SECONDARY);
		return y + 12;
	}

	private boolean hotbarHas(Item item) {
		if (minecraft == null || minecraft.player == null) {
			return false;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (minecraft.player.getInventory().getItem(slot).getItem() == item) {
				return true;
			}
		}
		return false;
	}

	private Component rerollStatusLine() {
		return switch (reroll.state()) {
			case IDLE -> Component.translatable("screen.noobs_container_searcher.reroll_status_idle");
			case SELECT_VILLAGER -> Component.translatable("screen.noobs_container_searcher.status_select_villager");
			case SELECT_LECTERN_POSITION -> Component.translatable("screen.noobs_container_searcher.status_select_block");
			case SUCCESS -> Component.translatable("screen.noobs_container_searcher.reroll_status_success");
			default -> Component.translatable("screen.noobs_container_searcher.reroll_status_running", reroll.attempts());
		};
	}

	private void renderLocationsTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		List<ModSettings.Location> locations = currentLocations();
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_locations").getString(),
			Component.translatable("screen.noobs_container_searcher.locations_count", locations.size()).getString());
		int cw = Math.min(560, bw);

		int ay = by + 4;
		String add = Component.translatable("screen.noobs_container_searcher.locations_add").getString();
		int addW = font.width(add) + 28;
		boolean iconHover = hovered(bx, ay, 22, 22);
		card(g, bx, ay, 22, 22, pickingIcon ? ACCENT_BG : iconHover ? SURFACE_HI : SURFACE, pickingIcon ? ACCENT : BORDER);
		g.fakeItem(iconStack(newLocationItem), bx + 3, ay + 3);
		hit(bx, ay, 22, 22, () -> {
			if (pickingIcon) {
				closePicker();
			} else {
				openPicker();
			}
		});
		int fieldX = bx + 28;
		int fieldW = cw - addW - 6 - 28;
		card(g, fieldX, ay, fieldW, 22, INPUT_BG, locationNameBox.isFocused() ? ACCENT : BORDER);
		locationNameBox.setX(fieldX + 8);
		locationNameBox.setY(ay + 7);
		locationNameBox.setWidth(fieldW - 16);
		locationNameBox.setHeight(10);
		hit(fieldX, ay, fieldW, 22, () -> focusBox(locationNameBox));
		drawButton(g, bx + cw - addW, ay, addW, 22, add, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hit(bx + cw - addW, ay, addW, 22, this::addLocation);

		int iy = ay + 22 + 6;
		int ly = iy + 4;
		int lh = by + bh - ly;
		// overlays replace the list instead of drawing over it, text would bleed through otherwise
		if (pickingIcon) {
			renderIconPicker(g, bx, iy, cw, by + bh - iy);
			return;
		}
		if (editing != null) {
			renderLocationEditor(g, bx, iy, cw, by + bh - iy);
			return;
		}
		if (locations.isEmpty()) {
			g.text(font, trim(Component.translatable("screen.noobs_container_searcher.locations_empty").getString(), cw), bx + 4, ly + 4, TEXT_MUTED);
			return;
		}
		int content = locations.size() * LOCATION_STRIDE;
		locationScroll = clampScroll(locationScroll, content, lh);
		boolean bar = content > lh;
		int rowW = cw - (bar ? 8 : 0);
		g.enableScissor(bx, ly, bx + cw, ly + lh);
		for (int i = 0; i < locations.size(); i++) {
			int ry = ly + i * LOCATION_STRIDE - (int) locationScroll;
			if (ry + LOCATION_ROW < ly || ry >= ly + lh) {
				continue;
			}
			renderLocationRow(g, locations.get(i), bx, ry, rowW, ly, lh);
		}
		g.disableScissor();
		if (bar) {
			scrollbar(g, bx + cw - 3, ly, lh, locationScroll, content);
		}
		scrollArea(bx, ly, cw, lh, amount -> locationScroll = clampScroll(locationScroll - amount * LOCATION_STRIDE, content, lh));
	}

	private void renderLocationRow(Gfx g, ModSettings.Location loc, int x, int y, int w, int viewY, int viewH) {
		boolean open = loc == editing;
		card(g, x, y, w, LOCATION_ROW, open ? SURFACE_HI : SURFACE, open ? BORDER_HI : BORDER);
		rfill(g, x + 8, y + 8, 10, 10, loc.color, 1);
		hitClipped(x + 6, y + 6, 14, 14, x, viewY, w, viewH, () -> openEditor(loc));
		g.fakeItem(locationIcon(loc), x + 22, y + 5);
		hitClipped(x + 22, y + 5, 16, 16, x, viewY, w, viewH, () -> openEditor(loc));

		int rightX = x + w - 8;
		rightX = smallButton(g, rightX - 16, y + 5, "x", x, viewY, w, viewH, () -> {
			if (loc == editing) {
				closeEditor();
			}
			selectedLocations.remove(loc);
			ModSettings.get().removeLocation(loc);
			locationCounts.clear();
			rebuild();
		}) - 6;
		String radius = Component.translatable("screen.noobs_container_searcher.locations_radius", loc.radius).getString();
		int radiusW = font.width(radius) + 12;
		boolean radiusHover = hovered(rightX - radiusW, y + 4, radiusW, 18) && my >= viewY && my < viewY + viewH;
		card(g, rightX - radiusW, y + 4, radiusW, 18, radiusHover ? SURFACE_HI : INPUT_BG, radiusHover ? BORDER_HI : BORDER);
		g.centeredText(font, Component.literal(radius), rightX - radiusW / 2, y + 9, TEXT_PRIMARY);
		hitClipped(rightX - radiusW, y + 4, radiusW, 18, x, viewY, w, viewH, () -> openEditor(loc));
		rightX -= radiusW + 8;

		g.text(font, trim(loc.name, Math.max(40, rightX - x - 46)), x + 44, y + 4, TEXT_PRIMARY);
		String where = loc.x + " " + loc.y + " " + loc.z + "  " + shortDimension(loc.dimension) + "  "
			+ Component.translatable("screen.noobs_container_searcher.locations_inside", locationCount(loc)).getString();
		g.text(font, trim(where, Math.max(40, rightX - x - 46)), x + 44, y + 15, TEXT_MUTED);
	}

	// wheel input is eased over a few frames instead of jumping a whole row
	private void applySmoothScroll() {
		if (pendingScrollArea == null) {
			return;
		}
		double step = Math.abs(pendingScroll) < 0.02D ? pendingScroll : pendingScroll * 0.3D;
		pendingScroll -= step;
		for (ScrollArea area : scrollAreas) {
			if (area.x == pendingScrollArea.x && area.y == pendingScrollArea.y && area.w == pendingScrollArea.w) {
				area.onScroll.accept(step);
				break;
			}
		}
		if (pendingScroll == 0) {
			pendingScrollArea = null;
		}
	}

	private void renderLocationEditor(Gfx g, int x, int y, int w, int h) {
		ModSettings.Location loc = editing;
		hit(x, y, w, h, () -> { });
		card(g, x, y, w, h, SURFACE_HI, BORDER_HI);
		g.text(font, trim(loc.name, w - 100), x + 12, y + 10, TEXT_PRIMARY);
		String done = Component.translatable("screen.noobs_container_searcher.locations_done").getString();
		int doneW = font.width(done) + 24;
		drawButton(g, x + w - doneW - 10, y + 6, doneW, 18, done, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hit(x + w - doneW - 10, y + 6, doneW, 18, this::closeEditor);

		int fy = y + 32;
		g.text(font, Component.translatable("screen.noobs_container_searcher.locations_radius_label"), x + 12, fy + 6, TEXT_SECONDARY);
		int boxX = x + 12 + 110;
		card(g, boxX, fy, 56, 20, INPUT_BG, radiusBox.isFocused() ? ACCENT_HOVER : BORDER);
		radiusBox.setX(boxX + 6);
		radiusBox.setY(fy + 6);
		radiusBox.setWidth(44);
		radiusBox.setHeight(10);
		hit(boxX, fy, 56, 20, () -> focusBox(radiusBox));
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.locations_inside", locationCount(loc)).getString(), w - 190), boxX + 66, fy + 6, SUCCESS);

		int cy = fy + 28;
		g.text(font, Component.translatable("screen.noobs_container_searcher.locations_color_label"), x + 12, cy + 6, TEXT_SECONDARY);
		card(g, boxX, cy, 70, 20, INPUT_BG, hexBox.isFocused() ? ACCENT_HOVER : BORDER);
		hexBox.setX(boxX + 6);
		hexBox.setY(cy + 6);
		hexBox.setWidth(58);
		hexBox.setHeight(10);
		hit(boxX, cy, 70, 20, () -> focusBox(hexBox));
		rfill(g, boxX + 78, cy, 20, 20, loc.color, 1);
		g.outline(boxX + 78, cy, 20, 20, BORDER_HI);

		int gx = x + 12;
		int gy = cy + 28;
		for (int row = 0; row < COLOR_ROWS; row++) {
			for (int col = 0; col < COLOR_COLS; col++) {
				g.fill(gx + col * COLOR_CELL, gy + row * COLOR_CELL, gx + (col + 1) * COLOR_CELL, gy + (row + 1) * COLOR_CELL, 0xFF000000 | gridColor(row, col));
			}
		}
		g.outline(gx - 1, gy - 1, COLOR_COLS * COLOR_CELL + 2, COLOR_ROWS * COLOR_CELL + 2, BORDER);
		hit(gx, gy, COLOR_COLS * COLOR_CELL, COLOR_ROWS * COLOR_CELL, () -> {
			int col = Math.max(0, Math.min(COLOR_COLS - 1, (mx - gx) / COLOR_CELL));
			int row = Math.max(0, Math.min(COLOR_ROWS - 1, (my - gy) / COLOR_CELL));
			loc.color = 0xFF000000 | gridColor(row, col);
			syncEditor();
			locationEdited();
		});

		int sy = gy + COLOR_ROWS * COLOR_CELL + 10;
		g.text(font, Component.translatable("screen.noobs_container_searcher.locations_icon_label"), x + 12, sy + 6, TEXT_SECONDARY);
		int searchW = w - (boxX - x) - 12;
		card(g, boxX, sy, searchW, 20, INPUT_BG, iconSearchBox.isFocused() ? ACCENT_HOVER : BORDER);
		iconSearchBox.setX(boxX + 6);
		iconSearchBox.setY(sy + 6);
		iconSearchBox.setWidth(searchW - 12);
		iconSearchBox.setHeight(10);
		hit(boxX, sy, searchW, 20, () -> focusBox(iconSearchBox));

		renderIconGrid(g, gx, sy + 28, w - 24, Math.max(ICON_CELL, y + h - 8 - (sy + 28)), x + w - 8, loc.item, id -> {
			loc.item = id;
			ModSettings.get().locationChanged();
		});
	}

	private void renderIconGrid(Gfx g, int gx, int listY, int listW, int listH, int barX, String current, java.util.function.Consumer<String> pick) {
		int cols = Math.max(1, listW / ICON_CELL);
		int content = ((iconResults.size() + cols - 1) / cols) * ICON_CELL;
		iconScroll = clampScroll(iconScroll, content, listH);
		ItemStack hoveredStack = null;
		g.enableScissor(gx, listY, gx + listW, listY + listH);
		int first = Math.max(0, (int) (iconScroll / ICON_CELL)) * cols;
		for (int i = first; i < iconResults.size(); i++) {
			int cx = gx + (i % cols) * ICON_CELL;
			int cellY = listY + (i / cols) * ICON_CELL - (int) iconScroll;
			if (cellY >= listY + listH) {
				break;
			}
			Item item = iconResults.get(i);
			String id = itemId(item);
			boolean picked = id.equals(current);
			boolean cellHover = hovered(cx, cellY, ICON_CELL, ICON_CELL) && my >= listY && my < listY + listH;
			if (picked || cellHover) {
				card(g, cx, cellY, ICON_CELL, ICON_CELL, picked ? ACCENT_BG : SURFACE, picked ? ACCENT_HOVER : BORDER);
			}
			ItemStack stack = new ItemStack(item);
			g.fakeItem(stack, cx + 2, cellY + 2);
			if (cellHover) {
				hoveredStack = stack;
			}
			hitClipped(cx, cellY, ICON_CELL, ICON_CELL, gx, listY, listW, listH, () -> pick.accept(id));
		}
		g.disableScissor();
		if (iconResults.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), gx + 2, listY + 4, TEXT_MUTED);
		}
		if (content > listH) {
			scrollbar(g, barX, listY, listH, iconScroll, content);
		}
		scrollArea(gx, listY, listW + 8, listH, amount -> iconScroll = clampScroll(iconScroll - amount * ICON_CELL, content, listH));
		if (hoveredStack != null) {
			g.tooltip(font, tooltip(hoveredStack), mx, my);
		}
	}

	// icon chooser for a brand new location, same grid as the editor
	private void renderIconPicker(Gfx g, int x, int y, int w, int h) {
		hit(x, y, w, h, () -> { });
		card(g, x, y, w, h, SURFACE_HI, BORDER_HI);
		g.text(font, Component.translatable("screen.noobs_container_searcher.locations_icon_label"), x + 12, y + 10, TEXT_PRIMARY);
		String done = Component.translatable("screen.noobs_container_searcher.locations_done").getString();
		int doneW = font.width(done) + 24;
		drawButton(g, x + w - doneW - 10, y + 6, doneW, 18, done, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hit(x + w - doneW - 10, y + 6, doneW, 18, this::closePicker);
		int sy = y + 32;
		card(g, x + 12, sy, w - 24, 20, INPUT_BG, iconSearchBox.isFocused() ? ACCENT_HOVER : BORDER);
		iconSearchBox.setX(x + 18);
		iconSearchBox.setY(sy + 6);
		iconSearchBox.setWidth(w - 36);
		iconSearchBox.setHeight(10);
		hit(x + 12, sy, w - 24, 20, () -> focusBox(iconSearchBox));
		renderIconGrid(g, x + 12, sy + 28, w - 24, Math.max(ICON_CELL, y + h - 8 - (sy + 28)), x + w - 8, newLocationItem, id -> {
			newLocationItem = id;
			closePicker();
		});
	}

	private void openPicker() {
		closeEditor();
		pickingIcon = true;
		iconSearchBox.setValue("");
		iconScroll = 0;
		iconSearchBox.setVisible(true);
		focusBox(iconSearchBox);
	}

	private void closePicker() {
		pickingIcon = false;
		iconSearchBox.setVisible(false);
		focusBox(null);
	}

	private void refreshIconResults() {
		if (allItems == null) {
			allItems = new ArrayList<>();
			allItemNames = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR) {
					allItems.add(item);
					allItemNames.add((itemId(item) + " " + new ItemStack(item).getHoverName().getString()).toLowerCase(Locale.ROOT));
				}
			}
		}
		String query = iconSearchBox == null ? "" : iconSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
		iconResults = new ArrayList<>();
		for (int i = 0; i < allItems.size(); i++) {
			if (query.isEmpty() || allItemNames.get(i).contains(query)) {
				iconResults.add(allItems.get(i));
			}
		}
	}

	private static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private ItemStack iconStack(String id) {
		return iconStacks.computeIfAbsent(id == null ? "minecraft:compass" : id, key -> {
			try {
				Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(key));
				return new ItemStack(item == null || item == Items.AIR ? Items.COMPASS : item);
			} catch (RuntimeException exception) {
				return new ItemStack(Items.COMPASS);
			}
		});
	}


	// hue across, then lighter to darker rows, last row is grayscale
	private static int gridColor(int row, int col) {
		if (row == COLOR_ROWS - 1) {
			int v = Math.round(255f * (COLOR_COLS - 1 - col) / (COLOR_COLS - 1));
			return (v << 16) | (v << 8) | v;
		}
		float[][] levels = {{0.35f, 1f}, {0.65f, 1f}, {1f, 1f}, {1f, 0.75f}, {1f, 0.5f}};
		return hsv(col / (float) COLOR_COLS, levels[row][0], levels[row][1]);
	}

	private static int hsv(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int sector = (int) hh;
		float f = hh - sector;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r;
		float gr;
		float b;
		switch (sector) {
			case 0 -> { r = v; gr = t; b = p; }
			case 1 -> { r = q; gr = v; b = p; }
			case 2 -> { r = p; gr = v; b = t; }
			case 3 -> { r = p; gr = q; b = v; }
			case 4 -> { r = t; gr = p; b = v; }
			default -> { r = v; gr = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(gr * 255) << 8) | Math.round(b * 255);
	}

	private void openEditor(ModSettings.Location loc) {
		pickingIcon = false;
		editing = loc;
		syncEditor();
		radiusBox.setVisible(true);
		hexBox.setVisible(true);
		iconSearchBox.setValue("");
		iconScroll = 0;
		iconSearchBox.setVisible(true);
		focusBox(null);
	}

	private void closeEditor() {
		editing = null;
		pickingIcon = false;
		if (radiusBox != null) {
			radiusBox.setVisible(false);
			hexBox.setVisible(false);
			iconSearchBox.setVisible(false);
		}
	}

	private void syncEditor() {
		syncingEditor = true;
		radiusBox.setValue(Integer.toString(editing.radius));
		hexBox.setValue(String.format("#%06X", editing.color & 0xFFFFFF));
		syncingEditor = false;
	}

	private void locationEdited() {
		ModSettings.get().locationChanged();
		locationCounts.clear();
		if (!selectedLocations.isEmpty()) {
			resultScroll = 0;
			rebuild();
		}
	}

	private static boolean inside(ModSettings.Location loc, ContainerRecord record) {
		if (!loc.dimension.equals(record.dimension)) {
			return false;
		}
		double dx = record.x - loc.x;
		double dy = record.y - loc.y;
		double dz = record.z - loc.z;
		return dx * dx + dy * dy + dz * dz <= (double) loc.radius * loc.radius;
	}

	private int locationCount(ModSettings.Location loc) {
		return locationCounts.computeIfAbsent(loc, key -> {
			int count = 0;
			for (ContainerRecord record : records) {
				if (inside(key, record)) {
					count++;
				}
			}
			return count;
		});
	}

	private boolean passesLocation(ContainerRecord record) {
		if (selectedLocations.isEmpty()) {
			return true;
		}
		for (ModSettings.Location loc : selectedLocations) {
			if (inside(loc, record)) {
				return true;
			}
		}
		return false;
	}

	private void toggleLocation(ModSettings.Location loc) {
		if (!selectedLocations.remove(loc)) {
			selectedLocations.add(loc);
		}
		resultScroll = 0;
		rebuild();
	}


	// returns the left edge so rows can chain buttons
	private int smallButton(Gfx g, int bx, int by, String label, int vx, int vy, int vw, int vh, Runnable action) {
		boolean hover = hovered(bx, by, 16, 16) && my >= vy && my < vy + vh;
		card(g, bx, by, 16, 16, hover ? SURFACE_HI : INPUT_BG, hover ? ACCENT : BORDER);
		g.centeredText(font, Component.literal(label), bx + 8, by + 4, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
		hitClipped(bx, by, 16, 16, vx, vy, vw, vh, action);
		return bx;
	}

	private ItemStack locationIcon(ModSettings.Location loc) {
		return iconStack(loc.item);
	}

	private void addLocation() {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return;
		}
		int count = currentLocations().size();
		ModSettings.Location loc = new ModSettings.Location();
		loc.server = ContainerSearcherClient.serverKey(minecraft);
		loc.dimension = ContainerSearcherClient.dimensionKey(minecraft);
		String typed = locationNameBox.getValue().trim();
		loc.name = typed.isEmpty()
			? Component.translatable("screen.noobs_container_searcher.locations_default_name", count + 1).getString()
			: typed;
		loc.x = (int) Math.floor(minecraft.player.getX());
		loc.y = (int) Math.floor(minecraft.player.getY());
		loc.z = (int) Math.floor(minecraft.player.getZ());
		loc.color = LOCATION_COLORS[count % LOCATION_COLORS.length];
		loc.item = newLocationItem;
		ModSettings.get().addLocation(loc);
		locationCounts.clear();
		locationNameBox.setValue("");
		openEditor(loc);
	}

	private void renderSettingsTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		if (serversView) {
			renderServersView(g, bx, by, bw, bh, footerY);
			return;
		}
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_settings").getString(), "");
		int cw = Math.min(640, bw);

		card(g, bx, by + 4, cw, 72, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.settings_distance_title").withStyle(ChatFormatting.BOLD), bx + 14, by + 14, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_distance_hint").getString(), cw - 28), bx + 14, by + 26, TEXT_MUTED);
		renderDistanceRow(g, bx + 14, by + 46, cw - 28);

		int yh = by + 4 + 72 + 8;
		renderDurationCard(g, bx, yh, cw);

		// the toggles sit two per row so the whole page still fits
		int half = (cw - 8) / 2;
		int rx = bx + half + 8;
		int yd = yh + 52 + 8;
		toggleCard(g, bx, yd, half, "screen.noobs_container_searcher.settings_item_display_title",
			"screen.noobs_container_searcher.settings_item_display_hint", ModSettings.itemDisplayEnabled(),
			() -> ModSettings.get().setItemDisplay(!ModSettings.itemDisplayEnabled()));
		toggleCard(g, rx, yd, half, "screen.noobs_container_searcher.locations_color_title",
			"screen.noobs_container_searcher.locations_color_hint", ModSettings.get().locationColors,
			() -> ModSettings.get().setLocationColors(!ModSettings.get().locationColors));

		int yt = yd + 38 + 8;
		toggleCard(g, bx, yt, half, "screen.noobs_container_searcher.settings_changes_title",
			"screen.noobs_container_searcher.settings_changes_hint", ModSettings.trackChangesEnabled(),
			() -> ModSettings.get().setTrackChanges(!ModSettings.trackChangesEnabled()));
		toggleCard(g, rx, yt, half, "screen.noobs_container_searcher.settings_arrows_title",
			"screen.noobs_container_searcher.settings_arrows_hint", ModSettings.targetArrowsEnabled(),
			() -> ModSettings.get().setTargetArrows(!ModSettings.targetArrowsEnabled()));

		int yl = yt + 38 + 8;
		toggleCard(g, bx, yl, half, "screen.noobs_container_searcher.settings_locate_one_title",
			"screen.noobs_container_searcher.settings_locate_one_hint", ModSettings.locateOneEnabled(),
			() -> ModSettings.get().toggleLocateOne());
		toggleCard(g, rx, yl, half, "screen.noobs_container_searcher.settings_locate_all_title",
			"screen.noobs_container_searcher.settings_locate_all_hint", ModSettings.locateAllEnabled(),
			() -> ModSettings.get().toggleLocateAll());

		int yf = yl + 38 + 8;
		toggleCard(g, bx, yf, cw, "screen.noobs_container_searcher.settings_fireworks_title",
			"screen.noobs_container_searcher.settings_fireworks_hint", ModSettings.locateFireworksEnabled(),
			() -> ModSettings.get().toggleLocateFireworks());

		int y2 = yf + 38 + 8;
		card(g, bx, y2, cw, 62, SURFACE, BORDER);
		Component clearTitle = Component.translatable("screen.noobs_container_searcher.clear_data_title").withStyle(ChatFormatting.BOLD);
		if (viewingKey != null) {
			clearTitle = clearTitle.copy().append(Component.literal("  " + serverDisplayName(viewingKey)).withStyle(ChatFormatting.RESET));
		}
		g.text(font, clearTitle, bx + 14, y2 + 9, TEXT_PRIMARY);
		int items = 0;
		for (ContainerRecord record : records) {
			items += itemCount(record);
		}
		if (dataBytes < 0) {
			dataBytes = jsonBytes(records);
		}
		String summary = Component.translatable("screen.noobs_container_searcher.settings_data_summary", records.size(), items).getString()
			+ " | " + Component.translatable("screen.noobs_container_searcher.settings_data_size", formatBytes(dataBytes)).getString();
		g.text(font, trim(summary, cw - 28), bx + 14, y2 + 21, TEXT_MUTED);
		String delete = Component.translatable("screen.noobs_container_searcher.clear_data").getString();
		int dw = font.width(delete) + 28;
		drawButton(g, bx + 14, y2 + 34, dw, 20, delete, DANGER, DANGER_HOVER, DANGER_DARK, true, null);
		hit(bx + 14, y2 + 34, dw, 20, this::confirmClearData);
		String others = Component.translatable("screen.noobs_container_searcher.servers_button").getString();
		int ow = font.width(others) + 28;
		drawButton(g, bx + 14 + dw + 8, y2 + 34, ow, 20, others, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hit(bx + 14 + dw + 8, y2 + 34, ow, 20, this::openServersView);
		String irreversible = Component.translatable("screen.noobs_container_searcher.settings_irreversible").getString();
		g.text(font, irreversible, bx + cw - 14 - font.width(irreversible), y2 + 9, TEXT_MUTED);

		int y3 = y2 + 62 + 8;
		if (y3 + 26 <= by + bh) {
			boolean statsOn = Analytics.isEnabled();
			card(g, bx, y3, cw, 26, SURFACE, BORDER);
			g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_stats_hint").getString(), cw - 28 - 40), bx + 14, y3 + 9, TEXT_MUTED);
			int sx = bx + cw - 14 - 24;
			int sy = y3 + 8;
			boolean statsHover = hovered(sx, sy, 24, 10);
			rfill(g, sx, sy, 24, 10, statsOn ? (statsHover ? ACCENT_HOVER : ACCENT) : (statsHover ? BORDER_HI : BORDER), 1);
			rfill(g, statsOn ? sx + 14 : sx + 2, sy + 2, 8, 6, TEXT_PRIMARY, 1);
			hit(sx, sy, 24, 10, () -> Analytics.setEnabled(!Analytics.isEnabled()));
		}

		int y4 = y3 + 26 + 8;
		if (y4 + 38 <= by + bh) {
			renderSupportCard(g, bx, y4, cw);
		}
	}

	private void renderSupportCard(Gfx g, int x, int y, int w) {
		card(g, x, y, w, 38, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.support_title").withStyle(ChatFormatting.BOLD), x + 14, y + 8, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.support_hint").getString(), w - 28 - 120), x + 14, y + 21, TEXT_MUTED);
		String label = Component.translatable("screen.noobs_container_searcher.discord_button").getString();
		int bw = 16 + font.width(label) + 22;
		int bx = x + w - 14 - bw;
		boolean hover = hovered(bx, y + 9, bw, 20);
		card(g, bx, y + 9, bw, 20, hover ? SURFACE_HI : SURFACE, hover ? DISCORD_HOVER : BORDER_HI);
		g.icon("icon_discord", bx + 6, y + 11, 16);
		g.text(font, label, bx + 26, y + 15, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
		hit(bx, y + 9, bw, 20, this::openDiscord);
	}

	private record ServerSummary(String key, String name, boolean multiplayer, boolean current, int containers, int items, long bytes) { }

	// "multiplayer:ip#hash" -> "ip #abcd", "singleplayer:folder" -> "folder"
	private static String serverDisplayName(String key) {
		String name = key;
		String world = "";
		int hash = name.indexOf('#');
		if (hash >= 0) {
			String id = name.substring(hash + 1);
			world = " #" + id.substring(Math.max(0, id.length() - 4));
			name = name.substring(0, hash);
		}
		return name.substring(name.indexOf(':') + 1) + world;
	}

	private static long jsonBytes(List<ContainerRecord> list) {
		return new com.google.gson.Gson().toJson(list).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		double kb = bytes / 1024.0D;
		if (kb < 1024) {
			return String.format(Locale.ROOT, kb < 10 ? "%.1f KB" : "%.0f KB", kb);
		}
		double mb = kb / 1024.0D;
		if (mb < 1024) {
			return String.format(Locale.ROOT, mb < 10 ? "%.2f MB" : "%.1f MB", mb);
		}
		return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0D);
	}

	private String currentServerKey() {
		return minecraft != null && minecraft.level != null ? ContainerSearcherClient.serverKey(minecraft) : "";
	}

	private void openDiscord() {
		Compat.openLink(this, DISCORD_URL);
	}

	private void openServersView() {
		serversView = true;
		serverScroll = 0;
		loadServerSummaries();
	}

	private void loadServerSummaries() {
		serverSummaries.clear();
		String current = currentServerKey();
		for (Map.Entry<String, List<ContainerRecord>> entry : controller.allServerData().entrySet()) {
			List<ContainerRecord> list = entry.getValue();
			int items = 0;
			for (ContainerRecord record : list) {
				items += itemCount(record);
			}
			String key = entry.getKey();
			serverSummaries.add(new ServerSummary(key, serverDisplayName(key), key.startsWith("multiplayer:"), key.equals(current), list.size(), items, jsonBytes(list)));
		}
		serverSummaries.sort(Comparator.comparing((ServerSummary summary) -> !summary.current())
			.thenComparing(summary -> summary.name().toLowerCase(Locale.ROOT)));
	}

	private void renderServersView(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		long total = 0;
		for (ServerSummary summary : serverSummaries) {
			total += summary.bytes();
		}
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.servers_total", formatBytes(total), serverSummaries.size()).getString(), "");
		int cw = Math.min(640, bw);
		g.text(font, Component.translatable("screen.noobs_container_searcher.servers_title").withStyle(ChatFormatting.BOLD), bx, by + 4, TEXT_PRIMARY);
		String back = Component.translatable("screen.noobs_container_searcher.servers_back").getString();
		int backW = font.width(back) + 28;
		drawButton(g, bx + cw - backW, by - 2, backW, 20, back, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hit(bx + cw - backW, by - 2, backW, 20, () -> serversView = false);

		int listY = by + 24;
		int listH = Math.max(34, by + bh - listY);
		int stride = 34;
		int content = serverSummaries.size() * stride;
		serverScroll = clampScroll(serverScroll, content, listH);
		g.enableScissor(bx, listY, bx + cw, listY + listH);
		int rowY = listY - (int) serverScroll;
		for (ServerSummary summary : serverSummaries) {
			if (rowY + 30 >= listY && rowY <= listY + listH) {
				renderServerRow(g, summary, bx, rowY, cw - (content > listH ? 8 : 0), listY, listH);
			}
			rowY += stride;
		}
		g.disableScissor();
		if (serverSummaries.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.servers_empty"), bx + 4, listY + 6, TEXT_MUTED);
		}
		if (content > listH) {
			scrollbar(g, bx + cw - 3, listY, listH, serverScroll, content);
		}
		scrollArea(bx, listY, cw, listH, amount -> serverScroll = clampScroll(serverScroll - amount * stride, content, listH));
	}

	private void renderServerRow(Gfx g, ServerSummary summary, int x, int y, int w, int viewY, int viewH) {
		card(g, x, y, w, 30, SURFACE, summary.key().equals(viewingKey) ? ACCENT : BORDER);
		String delete = Component.translatable("screen.noobs_container_searcher.servers_delete").getString();
		String view = Component.translatable("screen.noobs_container_searcher.servers_view").getString();
		int deleteW = font.width(delete) + 20;
		int viewW = font.width(view) + 20;
		int deleteX = x + w - 8 - deleteW;
		drawButton(g, deleteX, y + 5, deleteW, 20, delete, DANGER, DANGER_HOVER, DANGER_DARK, true, null);
		hitClipped(deleteX, y + 5, deleteW, 20, x, viewY, w, viewH, () -> confirmDeleteServer(summary));
		int viewX = deleteX - 6 - viewW;
		drawButton(g, viewX, y + 5, viewW, 20, view, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, null);
		hitClipped(viewX, y + 5, viewW, 20, x, viewY, w, viewH, () -> viewServer(summary));

		int textMax = viewX - x - 20;
		String name = trim(summary.name(), textMax);
		g.text(font, name, x + 8, y + 5, TEXT_PRIMARY);
		if (summary.current()) {
			String mark = Component.translatable("screen.noobs_container_searcher.servers_current").getString();
			g.text(font, mark, x + 8 + font.width(name) + 6, y + 5, SUCCESS);
		}
		String kind = Component.translatable(summary.multiplayer() ? "screen.noobs_container_searcher.server_kind_multi" : "screen.noobs_container_searcher.server_kind_single").getString();
		String stats = kind + "  " + Component.translatable("screen.noobs_container_searcher.servers_stats", summary.containers(), summary.items(), formatBytes(summary.bytes())).getString();
		g.text(font, trim(stats, textMax), x + 8, y + 17, TEXT_MUTED);
	}

	private void viewServer(ServerSummary summary) {
		if (summary.current()) {
			endViewing();
		} else {
			if (viewingKey == null) {
				ownRecords = new ArrayList<>(records);
			}
			viewingKey = summary.key();
			records.clear();
			records.addAll(controller.allServerData().getOrDefault(summary.key(), List.of()));
		}
		dataBytes = -1;
		resultScroll = 0;
		rebuild();
		switchTab(Tab.SEARCH);
	}

	private void endViewing() {
		if (viewingKey == null) {
			return;
		}
		records.clear();
		records.addAll(ownRecords);
		viewingKey = null;
		ownRecords = null;
		dataBytes = -1;
		resultScroll = 0;
		rebuild();
	}

	private void confirmDeleteServer(ServerSummary summary) {
		Compat.setScreen(minecraft, new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					deleteServerData(summary);
				}
				Compat.setScreen(minecraft, this);
			},
			Component.translatable("screen.noobs_container_searcher.servers_delete_title"),
			Component.translatable("screen.noobs_container_searcher.servers_delete_message", summary.name())
		));
	}

	private void deleteServerData(ServerSummary summary) {
		if (summary.current()) {
			controller.clearServerData(minecraft);
			if (viewingKey == null) {
				records.clear();
			} else {
				ownRecords.clear();
			}
		} else {
			controller.clearServerKey(summary.key());
			if (summary.key().equals(viewingKey)) {
				endViewing();
			}
		}
		dataBytes = -1;
		resultScroll = 0;
		rebuild();
		loadServerSummaries();
	}

	private void renderDurationCard(Gfx g, int x, int y, int w) {
		card(g, x, y, w, 52, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.settings_duration_title").withStyle(ChatFormatting.BOLD), x + 14, y + 7, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_duration_hint").getString(), w - 28), x + 14, y + 18, TEXT_MUTED);

		int rowY = y + 30;
		int pillW = font.width(durationText(MAX_DURATION_SECONDS)) + 14;
		durationSliderX = x + 14;
		durationSliderWidth = Math.max(40, w - 28 - pillW - 8);
		int trackY = rowY + 8;
		g.fill(durationSliderX, trackY - 2, durationSliderX + durationSliderWidth, trackY + 2, INPUT_BG);
		g.outline(durationSliderX, trackY - 2, durationSliderWidth, 4, BORDER);
		double ratio = Math.min(1.0D, ModSettings.highlightSeconds() / (double) DURATION_SLIDER_MAX);
		int knob = durationSliderX + (int) Math.round(ratio * durationSliderWidth);
		g.fill(durationSliderX + 1, trackY - 1, knob, trackY + 1, ACCENT);
		boolean hover = hovered(durationSliderX, rowY, durationSliderWidth, 16) || draggingDurationSlider;
		rfill(g, knob - 3, trackY - 6, 7, 12, INPUT_BG, 1);
		rfill(g, knob - 2, trackY - 5, 5, 10, hover ? TEXT_PRIMARY : ACCENT_HOVER, 1);
		hit(durationSliderX - 3, rowY, durationSliderWidth + 7, 16, () -> {
			draggingDurationSlider = true;
			updateDurationSlider(mx);
		});
		numberPill(g, durationSliderX + durationSliderWidth + 8, rowY, pillW, NumberEdit.DURATION,
			durationText(ModSettings.highlightSeconds()), Integer.toString(ModSettings.highlightSeconds()));
	}

	private static String durationText(int seconds) {
		return Component.translatable("screen.noobs_container_searcher.duration_value", seconds).getString();
	}

	private void updateDurationSlider(double mouseX) {
		double ratio = Math.max(0, Math.min(1, (mouseX - durationSliderX) / durationSliderWidth));
		ModSettings.get().setHighlightSeconds(Math.max(5, (int) Math.round(ratio * DURATION_SLIDER_MAX / 5.0D) * 5));
	}

	// value pill: click it to type your own number instead of using the slider
	private void numberPill(Gfx g, int x, int y, int w, NumberEdit target, String shown, String current) {
		if (numberEdit == target) {
			card(g, x, y, w, 16, INPUT_BG, ACCENT);
			numberBox.setX(x + 6);
			numberBox.setY(y + 4);
			numberBox.setWidth(w - 12);
			numberBox.setHeight(10);
			numberBox.setVisible(true);
			return;
		}
		boolean hover = hovered(x, y, w, 16);
		rfill(g, x, y, w, 16, hover ? ACCENT : ACCENT_BG, 1);
		g.centeredText(font, Component.literal(shown), x + w / 2, y + 4, TEXT_PRIMARY);
		hit(x, y, w, 16, () -> startNumberEdit(target, current));
	}

	private void startNumberEdit(NumberEdit target, String current) {
		numberEdit = NumberEdit.NONE;
		numberBox.setValue(current);
		numberEdit = target;
		numberBox.setVisible(true);
		focusBox(numberBox);
	}

	private void closeNumberEdit() {
		if (numberEdit == NumberEdit.NONE) {
			return;
		}
		numberEdit = NumberEdit.NONE;
		numberBox.setVisible(false);
		focusBox(null);
	}

	private void toggleCard(Gfx g, int bx, int y, int cw, String titleKey, String hintKey, boolean on, Runnable action) {
		card(g, bx, y, cw, 38, SURFACE, BORDER);
		g.text(font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), bx + 14, y + 8, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable(hintKey).getString(), cw - 28 - 44), bx + 14, y + 21, TEXT_MUTED);
		int tx = bx + cw - 14 - 30;
		int ty = y + 11;
		boolean hover = hovered(tx, ty, 30, 16);
		rfill(g, tx, ty, 30, 16, on ? (hover ? ACCENT_HOVER : ACCENT) : (hover ? BORDER_HI : BORDER), 1);
		rfill(g, on ? tx + 16 : tx + 2, ty + 2, 12, 12, TEXT_PRIMARY, 1);
		hit(tx, ty, 30, 16, action);
	}

	private void caption(Gfx g, String key, int x, int y) {
		g.text(font, Component.translatable(key).getString().toUpperCase(Locale.ROOT), x, y, TEXT_MUTED);
	}

	private boolean hovered(int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void hit(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	private void hitClipped(int x, int y, int w, int h, int vx, int vy, int vw, int vh, Runnable action) {
		int x1 = Math.max(x, vx);
		int y1 = Math.max(y, vy);
		int x2 = Math.min(x + w, vx + vw);
		int y2 = Math.min(y + h, vy + vh);
		if (x2 > x1 && y2 > y1) {
			hit(x1, y1, x2 - x1, y2 - y1, action);
		}
	}

	private void scrollArea(int x, int y, int w, int h, DoubleConsumer onScroll) {
		scrollAreas.add(new ScrollArea(x, y, w, h, onScroll));
	}

	private static void rfill(Gfx g, int x, int y, int w, int h, int color) {
		rfill(g, x, y, w, h, color, 2);
	}

	private static void rfill(Gfx g, int x, int y, int w, int h, int color, int radius) {
		if (radius >= 2) {
			g.fill(x + 2, y, x + w - 2, y + h, color);
			g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
			g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
			g.fill(x, y + 2, x + 1, y + h - 2, color);
			g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
		} else {
			g.fill(x + 1, y, x + w - 1, y + h, color);
			g.fill(x, y + 1, x + w, y + h - 1, color);
		}
	}

	private static void card(Gfx g, int x, int y, int w, int h, int fill, int border) {
		rfill(g, x, y, w, h, border);
		rfill(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	private static void icon(Gfx g, String[] bitmap, int x, int y, int color) {
		for (int row = 0; row < bitmap.length; row++) {
			String line = bitmap[row];
			int run = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == 'X';
				if (on && run < 0) {
					run = col;
				} else if (!on && run >= 0) {
					g.fill(x + run, y + row, x + col, y + row + 1, color);
					run = -1;
				}
			}
		}
	}

	private void drawButton(Gfx g, int x, int y, int w, int h, String label, int loc, int hover, int dark, boolean enabled, String[] icon) {
		if (!enabled) {
			card(g, x, y, w, h, SURFACE, BORDER);
			g.centeredText(font, Component.literal(label), x + w / 2, y + (h - 2 - 8) / 2 + 1, TEXT_MUTED);
			return;
		}
		boolean isHover = hovered(x, y, w, h);
		rfill(g, x, y, w, h, dark);
		rfill(g, x, y, w, h - 2, isHover ? hover : loc);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x30FFFFFF);
		int textW = font.width(label) + (icon == null ? 0 : icon[0].length() + 4);
		int tx = x + (w - textW) / 2;
		if (icon != null) {
			icon(g, icon, tx, y + (h - 2 - icon.length) / 2 + 1, 0xFFFFFFFF);
			tx += icon[0].length() + 4;
		}
		g.text(font, label, tx, y + (h - 2 - 8) / 2 + 1, 0xFFFFFFFF);
	}

	private int tag(Gfx g, int x, int y, String label, int fg, int bg, int h, Runnable click) {
		int w = font.width(label) + 8;
		boolean hover = click != null && hovered(x, y, w, h);
		rfill(g, x, y, w, h, hover ? SURFACE_HI : bg, 1);
		g.text(font, label, x + 4, y + (h - 8) / 2 + 1, hover ? TEXT_PRIMARY : fg);
		if (click != null) {
			hit(x, y, w, h, click);
		}
		return w;
	}

	private String chipLabel(ChipSpec spec) {
		return trim(spec.label, 120);
	}

	private int chipWidth(ChipSpec spec) {
		return font.width(chipLabel(spec)) + 12 + (spec.count >= 0 ? font.width(Integer.toString(spec.count)) + 4 : 0) + (spec.closable ? 8 : 0);
	}

	private int chip(Gfx g, int x, int y, String label, int count, int dot, boolean active, boolean closable, Runnable click) {
		return drawChip(g, new ChipSpec(label, count, dot, active, closable, click), x, y);
	}

	private int drawChip(Gfx g, ChipSpec spec, int x, int y) {
		int w = chipWidth(spec);
		boolean hover = hovered(x, y, w, 14);
		card(g, x, y, w, 14, spec.excluded ? DANGER_BG : spec.active ? ACCENT_BG : hover ? SURFACE_HI : SURFACE, spec.excluded ? DANGER_DARK : spec.active ? ACCENT : BORDER);
		int cx = x + 6;
		String label = chipLabel(spec);
		g.text(font, label, cx, y + 3, spec.excluded ? DANGER_HOVER : spec.active ? TEXT_PRIMARY : spec.dot != 0 ? spec.dot : TEXT_SECONDARY);
		if (spec.excluded) {
			g.fill(cx, y + 7, cx + font.width(label), y + 8, DANGER_HOVER);
		}
		cx += font.width(label);
		if (spec.count >= 0) {
			g.text(font, Integer.toString(spec.count), cx + 4, y + 3, TEXT_MUTED);
		}
		if (spec.closable) {
			icon(g, ICON_CLOSE, x + w - 11, y + 4, ACCENT_HOVER);
		}
		hit(x, y, w, 14, spec.click);
		return w;
	}

	private int flowChips(Gfx g, List<ChipSpec> specs, int x, int y, int maxWidth, boolean draw) {
		int cx = x;
		int cy = y;
		for (ChipSpec spec : specs) {
			int w = chipWidth(spec);
			if (cx > x && cx + w > x + maxWidth) {
				cx = x;
				cy += 18;
			}
			if (draw) {
				drawChip(g, spec, cx, cy);
			}
			cx += w + 4;
		}
		return specs.isEmpty() ? 0 : cy - y + 14;
	}

	private void searchField(Gfx g, EditBox box, int x, int y, int w, int h, boolean clearable) {
		boolean focused = box.isFocused();
		card(g, x, y, w, h, INPUT_BG, focused ? ACCENT : BORDER);
		icon(g, ICON_SEARCH, x + 7, y + (h - 9) / 2, focused ? ACCENT_HOVER : TEXT_MUTED);
		boolean showClear = clearable && !box.getValue().isEmpty();
		box.setX(x + 22);
		box.setY(y + (h - 8) / 2);
		box.setWidth(w - 22 - (showClear ? 18 : 8));
		box.setHeight(10);
		hit(x, y, w, h, () -> focusBox(box));
		if (showClear) {
			boolean hover = hovered(x + w - 16, y, 16, h);
			icon(g, ICON_CLOSE, x + w - 13, y + h / 2 - 3, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
			hit(x + w - 16, y, 16, h, () -> {
				box.setValue("");
				focusBox(box);
			});
		}
	}

	private void scrollbar(Gfx g, int x, int y, int h, double scroll, int content) {
		g.fill(x, y, x + 3, y + h, 0xFF15161C);
		int thumb = Math.max(12, (int) (h * (h / (double) content)));
		int range = Math.max(1, content - h);
		int thumbY = y + (int) ((h - thumb) * (scroll / range));
		g.fill(x, thumbY, x + 3, thumbY + thumb, BORDER_HI);
	}

	private static int containerColor(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("shulker")) {
			return 0xFFC090E0;
		}
		if (lower.contains("barrel")) {
			return 0xFFD09060;
		}
		if (lower.contains("chest")) {
			return 0xFFE0B070;
		}
		if (lower.contains("villager")) {
			return 0xFF90D0A0;
		}
		return 0xFF9AA0B0;
	}

	private boolean click(double mouseX, double mouseY, int button, boolean ctrl) {
		if (!Compat.isLeftButton(button)) {
			return false;
		}
		ctrlDown = ctrl;
		closeNumberEdit();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mouseX, mouseY)) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	private boolean drag(double mouseX) {
		if (draggingDistanceSlider) {
			updateDistanceSlider(mouseX);
			return true;
		}
		if (draggingDurationSlider) {
			updateDurationSlider(mouseX);
			return true;
		}
		return false;
	}

	private void release() {
		draggingDistanceSlider = false;
		draggingDurationSlider = false;
	}

	private boolean editKey(java.util.function.Predicate<EditBox> press) {
		if (numberEdit != NumberEdit.NONE && press.test(numberBox)) {
			return true;
		}
		if (tab == Tab.SEARCH && (press.test(searchBox) || press.test(filterSearchBox))) {
			return true;
		}
		if (tab == Tab.LOCATIONS && (press.test(locationNameBox) || press.test(radiusBox) || press.test(hexBox) || press.test(iconSearchBox))) {
			return true;
		}
		return tab == Tab.REROLL && press.test(rerollSearchBox);
	}

	// <<INPUT>>
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return click(mouseX, mouseY, button, hasControlDown());
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return drag(mouseX) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return editKey(box -> box.keyPressed(keyCode, scanCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
	}
	// <</INPUT>>

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		for (ScrollArea area : scrollAreas) {
			if (mouseX >= area.x && mouseY >= area.y && mouseX < area.x + area.w && mouseY < area.y + area.h) {
				if (pendingScrollArea == null || pendingScrollArea.x != area.x || pendingScrollArea.y != area.y || pendingScrollArea.w != area.w) {
					pendingScroll = 0;
				}
				pendingScrollArea = area;
				pendingScroll += verticalAmount;
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void onClose() {
		Compat.setScreen(minecraft, null);
	}

	// deletes what the list is showing: this server, or the other server we are currently viewing
	private void confirmClearData() {
		String currentKey = currentServerKey();
		String key = viewingKey != null ? viewingKey : currentKey;
		confirmDeleteServer(new ServerSummary(key, serverDisplayName(key), key.startsWith("multiplayer:"), key.equals(currentKey), 0, 0, 0));
	}

	private void beginSelection() {
		if (selectedChoices.isEmpty()) {
			return;
		}
		reroll.beginSelection(selectedChoices.stream()
			.map(choice -> new LibrarianRerollController.TargetBook(choice.id, choice.level))
			.toList());
	}

	private void loadEnchantments() {
		allChoices.clear();
		if (minecraft == null || minecraft.level == null) {
			return;
		}
		var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (var entry : registry.entrySet()) {
			Holder<Enchantment> holder = registry.wrapAsHolder(entry.getValue());
			if (!holder.is(EnchantmentTags.TRADEABLE)) {
				continue;
			}
			ResourceLocation id = entry.getKey().location();
			for (int level = entry.getValue().getMinLevel(); level <= entry.getValue().getMaxLevel(); level++) {
				String name = Enchantment.getFullname(holder, level).getString();
				allChoices.add(new EnchantmentChoice(id, level, name));
			}
		}
		allChoices.sort(Comparator.comparing(choice -> normalizedSearch(choice.name)));
		rebuildChoices();
		syncSelection();
	}

	private int seenSelectionRevision = -1;

	private void persistSelection() {
		List<String> entries = new ArrayList<>();
		for (EnchantmentChoice choice : selectedChoices) {
			entries.add(choice.id + "|" + choice.level);
		}
		ModSettings settings = ModSettings.get();
		settings.setRerollSelection(entries);
		seenSelectionRevision = settings.selectionRevision();
	}

	private void syncSelection() {
		ModSettings settings = ModSettings.get();
		seenSelectionRevision = settings.selectionRevision();
		Set<String> saved = new java.util.HashSet<>(settings.rerollSelection());
		selectedChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (saved.contains(choice.id + "|" + choice.level)) {
				selectedChoices.add(choice);
			}
		}
	}

	private void rebuildChoices() {
		String query = rerollSearchBox == null ? "" : normalizedSearch(rerollSearchBox.getValue());
		visibleChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (query.isEmpty() || normalizedSearch(choice.name + " " + choice.id).contains(query)) {
				visibleChoices.add(choice);
			}
		}
	}

	private void rebuild() {
		typeMatchCache.clear();
		multiRow = null;
		frameLocations = null;
		rows.clear();
		filters.clear();
		containerTypes.clear();
		QuerySplit split = splitQuery(searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT));
		String query = split.itemQuery();
		String filterQuery = filterSearchBox == null ? "" : normalizedSearch(filterSearchBox.getValue());
		Map<String, FilterEntry> filterMap = new LinkedHashMap<>();
		Map<String, ContainerTypeEntry> containerTypeMap = new LinkedHashMap<>();
		addFixedFilters(filterMap);

		Map<String, ResultRow> rowMap = new LinkedHashMap<>();
		ContainerRecord ender = enderRepresentative();
		for (ContainerRecord record : records) {
			// all ender chests share one inventory, list it once on the best one
			if (isEnderChest(record) && record != ender) {
				continue;
			}
			countContainerType(containerTypeMap, record);
			if (!passesDistance(record) || !passesContainerType(record, containerTypeMap) || !passesLocation(record)
				|| !passesRecordQuery(record, split)) {
				continue;
			}
			for (ContainerItemRecord item : entries(record)) {
				countFixedFilters(filterMap, item);
				for (int i = 0; i < item.enchantments.size(); i++) {
					String id = item.enchantments.get(i);
					String fallback = i < item.enchantmentNames.size() ? item.enchantmentNames.get(i) : id;
					String name = localizedEnchantmentName(id);
					filterMap.computeIfAbsent("ench:" + id, ignored -> new FilterEntry("ench:" + id, name, id + " " + fallback + " " + name)).count += item.count;
				}

				if (selectedFilter != null && !passesFilter(item, selectedFilter)) {
					continue;
				}
				if (!query.isEmpty() && !matches(item, query)) {
					continue;
				}
				String key = rowKey(record, item);
				ResultRow row = rowMap.get(key);
				if (row == null) {
					rowMap.put(key, new ResultRow(record, item));
				} else {
					row.add(item);
				}
			}
		}
		rows.addAll(rowMap.values());

		filters.addAll(filterMap.values());
		filters.removeIf(filter -> filter.count <= 0);
		if (!filterQuery.isEmpty()) {
			filters.removeIf(filter -> !filter.matches(filterQuery));
		}
		containerTypes.addAll(containerTypeMap.values());
		containerTypes.removeIf(type -> type.count <= 0);
		if (!filterQuery.isEmpty()) {
			containerTypes.removeIf(type -> !type.matches(filterQuery));
		}
		filters.sort(Comparator
			.comparingInt((FilterEntry filter) -> filter.order)
			.thenComparing(filter -> filter.name.toLowerCase(Locale.ROOT)));
		containerTypes.sort(Comparator.comparing(type -> type.name.toLowerCase(Locale.ROOT)));
		Comparator<ResultRow> byName = Comparator
			.comparing((ResultRow row) -> row.lowerName)
			.thenComparing(row -> row.record.dimension)
			.thenComparingInt(row -> row.record.x)
			.thenComparingInt(row -> row.record.y)
			.thenComparingInt(row -> row.record.z);
		if (sort == Sort.DISTANCE) {
			for (ResultRow row : rows) {
				double distance = distanceTo(row.record);
				row.sortDistance = distance < 0 ? Double.MAX_VALUE : distance;
			}
		}
		rows.sort(sort == Sort.DISTANCE
			? Comparator.comparingDouble((ResultRow row) -> row.sortDistance).thenComparing(byName)
			: byName);
		Set<String> containerIds = new LinkedHashSet<>();
		for (ResultRow row : rows) {
			containerIds.add(recordIdentity(row.record));
		}
		resultContainerCount = containerIds.size();
	}

	private List<ContainerRecord> recordsFor(ResultRow row) {
		Map<String, ContainerRecord> matches = new HashMap<>();
		for (ResultRow other : rows) {
			if (other.variant.equals(row.variant)
				&& isVillager(other.record) == isVillager(row.record)
				&& (selectedFilter == null || passesFilter(other.item, selectedFilter))) {
				matches.put(recordIdentity(other.record), other.record);
			}
		}
		// the ender inventory is listed once, but every standing ender chest we know should light up and get named in chat
		if (isEnderChest(row.record) && !row.record.blockGone) {
			for (ContainerRecord other : records) {
				if (isEnderChest(other) && !other.blockGone) {
					matches.put(recordIdentity(other), other);
				}
			}
		}
		return new ArrayList<>(matches.values());
	}

	private static String rowKey(ContainerRecord record, ContainerItemRecord item) {
		return recordIdentity(record) + "|" + (item.inside == null ? "" : item.inside) + "|" + itemVariantKey(item);
	}

	private static String itemVariantKey(ContainerItemRecord item) {
		if (item.stackData != null && !item.stackData.isBlank()) {
			return item.stackData;
		}
		return item.itemId + "|" + item.name + "|" + item.damage + "|" + item.enchantments + "|" + item.enchantmentNames;
	}

	private static String recordIdentity(ContainerRecord record) {
		return record.entityUuid == null
			? record.server + "|" + record.dimension + "|" + record.x + "|" + record.y + "|" + record.z
			: record.server + "|" + record.dimension + "|villager|" + record.entityUuid;
	}

	private static boolean isVillager(ContainerRecord record) {
		return record.entityUuid != null && !record.entityUuid.isBlank();
	}

	private static List<ContainerItemRecord> entries(ContainerRecord record) {
		if (record.entries != null && !record.entries.isEmpty()) {
			return record.entries;
		}
		List<ContainerItemRecord> fallback = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : record.items.entrySet()) {
			ContainerItemRecord item = new ContainerItemRecord();
			item.itemId = entry.getKey();
			item.count = entry.getValue();
			item.name = itemName(entry.getKey());
			item.searchText = (item.itemId + " " + item.name).toLowerCase(Locale.ROOT);
			fallback.add(item);
		}
		return fallback;
	}

	private static boolean matches(ContainerItemRecord item, String query) {
		for (String token : query.split("\\s+")) {
			if (!token.isBlank() && !matchesToken(item, token)) {
				return false;
			}
		}
		return true;
	}

	// search box words that are about the container itself (include:, exclude:, loc:), the rest is matched per item
	private record QuerySplit(String itemQuery, List<String> include, List<String> exclude, List<String> locations) { }

	private static QuerySplit splitQuery(String query) {
		List<String> include = new ArrayList<>();
		List<String> exclude = new ArrayList<>();
		List<String> locations = new ArrayList<>();
		StringBuilder rest = new StringBuilder();
		for (String token : query.split("\\s+")) {
			int colon = token.indexOf(':');
			String key = colon < 0 ? "" : token.substring(0, colon);
			List<String> target = switch (key) {
				case "include", "type", "tur", "tür" -> include;
				case "exclude", "ex", "not", "haric", "hariç" -> exclude;
				case "loc", "location", "konum" -> locations;
				default -> null;
			};
			if (target == null) {
				rest.append(token).append(' ');
				continue;
			}
			for (String value : token.substring(colon + 1).split(",")) {
				if (!value.isBlank()) {
					target.add(value.trim().replace(' ', '_'));
				}
			}
		}
		return new QuerySplit(rest.toString().trim(), include, exclude, locations);
	}

	private boolean passesRecordQuery(ContainerRecord record, QuerySplit split) {
		if (!split.include().isEmpty() && split.include().stream().noneMatch(value -> matchesContainerTypeName(record, value))) {
			return false;
		}
		if (split.exclude().stream().anyMatch(value -> matchesContainerTypeName(record, value))) {
			return false;
		}
		if (!split.locations().isEmpty()) {
			ModSettings.Location loc = locationFor(record);
			String name = loc == null ? "" : loc.name.toLowerCase(Locale.ROOT).replace(' ', '_');
			return split.locations().stream().anyMatch(name::contains);
		}
		return true;
	}

	// a full name ("chest", "shulker_box" for any colour) wins; otherwise a start of a name or of one of its
	// words counts ("che" finds chests, "dro" droppers), and as a last try any part of the id or shown name
	private boolean matchesContainerTypeName(ContainerRecord record, String value) {
		if (record.containerType == null) {
			return false;
		}
		return typesFor(value).contains(record.containerType);
	}

	private Set<String> typesFor(String value) {
		return typeMatchCache.computeIfAbsent(value, this::findTypes);
	}

	private Set<String> findTypes(String value) {
		Set<String> ids = new LinkedHashSet<>();
		for (ContainerRecord known : records) {
			if (known.containerType != null) {
				ids.add(known.containerType);
			}
		}
		Set<String> exact = new LinkedHashSet<>();
		Set<String> prefix = new LinkedHashSet<>();
		Set<String> inside = new LinkedHashSet<>();
		for (String stored : ids) {
			String id = stored.toLowerCase(Locale.ROOT);
			String path = id.substring(id.lastIndexOf(':') + 1);
			String shown = containerName(stored).getString().toLowerCase(Locale.ROOT).replace(' ', '_');
			if (path.equals(value) || shown.equals(value)
				|| (value.equals("shulker") || value.equals("shulker_box")) && path.contains("shulker_box")) {
				exact.add(stored);
			}
			boolean starts = path.startsWith(value) || shown.startsWith(value);
			for (String word : path.split("_")) {
				starts |= word.startsWith(value);
			}
			if (starts) {
				prefix.add(stored);
			}
			if (id.contains(value) || shown.contains(value)) {
				inside.add(stored);
			}
		}
		return !exact.isEmpty() ? exact : !prefix.isEmpty() ? prefix : inside;
	}

	private static boolean matchesToken(ContainerItemRecord item, String token) {
		String currentName = localizedItemName(item).toLowerCase(Locale.ROOT);
		if (token.startsWith("is:")) {
			String flag = switch (token.substring(3)) {
				case "enchanted" -> "flag:enchanted";
				case "unenchanted" -> "flag:unenchanted";
				case "lore" -> "flag:lore";
				case "damaged" -> "flag:damaged";
				case "full", "full_durability" -> "flag:full_durability";
				case "low", "low_durability" -> "flag:low_durability";
				case "shulker", "in_shulker" -> "flag:in_shulker";
				default -> null;
			};
			return flag != null && passesFilter(item, flag);
		}
		if (token.startsWith("enchant:") || token.startsWith("buyu:")) {
			String value = token.substring(token.indexOf(':') + 1).replace('_', ' ');
			for (int i = 0; i < item.enchantments.size(); i++) {
				String id = item.enchantments.get(i).toLowerCase(Locale.ROOT).replace('_', ' ');
				String shown = i < item.enchantmentNames.size() ? item.enchantmentNames.get(i).toLowerCase(Locale.ROOT) : "";
				if (id.contains(value) || shown.contains(value)) {
					return true;
				}
			}
			return false;
		}
		if (token.startsWith("inside:") || token.startsWith("icinde:")) {
			String value = token.substring(token.indexOf(':') + 1).replace('_', ' ');
			return item.inside != null && item.inside.toLowerCase(Locale.ROOT).contains(value);
		}
		if (token.equals("enchanted") || token.equals("buyulu") || token.equals("büyülü")) {
			return item.enchanted;
		}
		if (token.equals("unenchant") || token.equals("unenchanted") || token.equals("buyusuz") || token.equals("büyüsüz")) {
			return !item.enchanted;
		}
		if (token.startsWith("name:") || token.startsWith("isim:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return currentName.contains(value);
		}
		if (token.startsWith("lore:") || token.startsWith("aciklama:") || token.startsWith("açıklama:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return loreText(item).contains(value);
		}
		if (token.startsWith("durability:") || token.startsWith("dur:") || token.startsWith("dayaniklilik:") || token.startsWith("dayanıklılık:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return matchesDurability(item, value);
		}
		return item.searchText != null && item.searchText.contains(token)
			|| item.itemId.toLowerCase(Locale.ROOT).contains(token)
			|| currentName.contains(token);
	}

	private static void addFixedFilters(Map<String, FilterEntry> filters) {
		filters.put("flag:enchanted", new FilterEntry("flag:enchanted", Component.translatable("screen.noobs_container_searcher.filter_enchanted").getString(), 0));
		filters.put("flag:unenchanted", new FilterEntry("flag:unenchanted", Component.translatable("screen.noobs_container_searcher.filter_unenchanted").getString(), 0));
		filters.put("flag:lore", new FilterEntry("flag:lore", Component.translatable("screen.noobs_container_searcher.filter_lore").getString(), 0));
		filters.put("flag:damaged", new FilterEntry("flag:damaged", Component.translatable("screen.noobs_container_searcher.filter_damaged").getString(), 0));
		filters.put("flag:full_durability", new FilterEntry("flag:full_durability", Component.translatable("screen.noobs_container_searcher.filter_full_durability").getString(), 0));
		filters.put("flag:low_durability", new FilterEntry("flag:low_durability", Component.translatable("screen.noobs_container_searcher.filter_low_durability").getString(), 0));
		filters.put("flag:in_shulker", new FilterEntry("flag:in_shulker", Component.translatable("screen.noobs_container_searcher.filter_in_shulker").getString(), 0));
	}

	private static void countContainerType(Map<String, ContainerTypeEntry> types, ContainerRecord record) {
		if (record.containerType == null || record.containerType.isBlank()) {
			return;
		}
		ContainerTypeEntry entry = types.computeIfAbsent(containerTypeKey(record.containerType),
			ignored -> new ContainerTypeEntry(containerName(record.containerType).getString()));
		// only the first record of each type extends the search text (was re-normalized for every record)
		if (entry.typeIds.add(record.containerType)) {
			entry.searchText = normalizedSearch(entry.searchText + " " + record.containerType);
		}
		entry.count += itemCount(record);
	}

	private static void countFixedFilters(Map<String, FilterEntry> filters, ContainerItemRecord item) {
		if (item.enchanted) {
			filters.get("flag:enchanted").count += item.count;
		} else {
			filters.get("flag:unenchanted").count += item.count;
		}
		if (!loreText(item).isBlank()) {
			filters.get("flag:lore").count += item.count;
		}
		if (item.damageable && item.damage > 0) {
			filters.get("flag:damaged").count += item.count;
		}
		if (item.damageable && item.damage == 0) {
			filters.get("flag:full_durability").count += item.count;
		}
		if (item.damageable && durabilityPercent(item) <= 25) {
			filters.get("flag:low_durability").count += item.count;
		}
		if (item.inside != null) {
			filters.get("flag:in_shulker").count += item.count;
		}
	}

	private static boolean passesFilter(ContainerItemRecord item, String filter) {
		return switch (filter) {
			case "flag:enchanted" -> item.enchanted;
			case "flag:unenchanted" -> !item.enchanted;
			case "flag:lore" -> !loreText(item).isBlank();
			case "flag:damaged" -> item.damageable && item.damage > 0;
			case "flag:full_durability" -> item.damageable && item.damage == 0;
			case "flag:low_durability" -> item.damageable && durabilityPercent(item) <= 25;
			case "flag:in_shulker" -> item.inside != null;
			default -> filter.startsWith("ench:") && item.enchantments.contains(filter.substring("ench:".length()));
		};
	}

	private static boolean matchesDurability(ContainerItemRecord item, String expression) {
		if (!item.damageable || item.maxDamage <= 0) {
			return false;
		}
		int percent = durabilityPercent(item);
		try {
			if (expression.startsWith("<=")) {
				return percent <= Integer.parseInt(expression.substring(2).replace("%", ""));
			}
			if (expression.startsWith(">=")) {
				return percent >= Integer.parseInt(expression.substring(2).replace("%", ""));
			}
			if (expression.startsWith("<")) {
				return percent < Integer.parseInt(expression.substring(1).replace("%", ""));
			}
			if (expression.startsWith(">")) {
				return percent > Integer.parseInt(expression.substring(1).replace("%", ""));
			}
			return percent == Integer.parseInt(expression.replace("%", ""));
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static int durabilityPercent(ContainerItemRecord item) {
		if (!item.damageable || item.maxDamage <= 0) {
			return 100;
		}
		int remaining = Math.max(0, item.maxDamage - item.damage);
		return Math.round(remaining * 100.0F / item.maxDamage);
	}

	private static String loreText(ContainerItemRecord item) {
		if (item.loreLines == null || item.loreLines.isEmpty()) {
			return "";
		}
		return String.join(" ", item.loreLines).toLowerCase(Locale.ROOT);
	}

	private static String itemDetail(ContainerItemRecord item) {
		StringBuilder detail = new StringBuilder();
		if (item.damageable && item.maxDamage > 0) {
			detail.append(" | ").append(durabilityPercent(item)).append("%");
		}
		return detail.toString();
	}

	private String distanceDetail(ContainerRecord record) {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return "";
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return " | " + Component.translatable("screen.noobs_container_searcher.different_dimension").getString();
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return " | " + Component.translatable(
			"screen.noobs_container_searcher.distance",
			Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))
		).getString();
	}

	private static int filterColor(FilterEntry filter) {
		if (filter.id.startsWith("ench:")) {
			return ENCHANT_PURPLE;
		}
		return switch (filter.id) {
			case "flag:enchanted" -> ENCHANT_PURPLE;
			case "flag:unenchanted" -> TEXT_SECONDARY;
			case "flag:lore" -> WARNING;
			case "flag:damaged" -> DANGER;
			case "flag:full_durability" -> SUCCESS;
			case "flag:low_durability" -> WARNING;
			case "flag:in_shulker" -> containerColor("shulker");
			default -> TEXT_PRIMARY;
		};
	}

	private static Component containerName(String storedType) {
		if (storedType != null && storedType.startsWith("villager:")) {
			String profession = storedType.substring("villager:".length());
			ResourceLocation id = ResourceLocation.tryParse(profession);
			String path = id == null ? profession : id.getPath();
			return Component.translatable(
				"screen.noobs_container_searcher.villager_type",
				Component.translatable("entity.minecraft.villager." + path)
			);
		}
		ResourceLocation blockId = ResourceLocation.tryParse(storedType);
		if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
			return BuiltInRegistries.BLOCK.getValue(blockId).getName();
		}
		return Component.literal(storedType);
	}

	private static String containerTypeKey(String storedType) {
		if (storedType == null || storedType.isBlank()) {
			return "";
		}
		return normalizedSearch(containerName(storedType).getString());
	}

	private static int itemCount(ContainerRecord record) {
		int count = 0;
		for (int value : record.items.values()) {
			count += value;
		}
		return count;
	}

	private static int itemNameColor(ItemStack stack, ContainerItemRecord item) {
		if (item.enchanted || stack.hasFoil()) {
			return ENCHANT_PURPLE;
		}
		if (!stack.isEmpty()) {
			Rarity rarity = stack.getRarity();
			return switch (rarity) {
				case UNCOMMON -> 0xFFFFFF55;
				case RARE -> 0xFF55FFFF;
				case EPIC -> 0xFFFF55FF;
				default -> TEXT_PRIMARY;
			};
		}
		return item.enchanted ? ENCHANT_PURPLE : TEXT_PRIMARY;
	}

	private static ItemStack displayStack(Item item, ContainerItemRecord record) {
		ItemStack decoded = decodeStack(record);
		if (!decoded.isEmpty()) {
			return decoded;
		}

		ItemStack stack = new ItemStack(item, Math.min(record.count, 99));
		if (record.enchanted) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (record.damageable && record.maxDamage > 0) {
			stack.setDamageValue(record.damage);
		}
		if (record.loreLines != null && !record.loreLines.isEmpty()) {
			List<Component> lore = new ArrayList<>();
			for (String line : record.loreLines) {
				lore.add(Component.literal(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		return stack;
	}

	private List<Component> tooltip(ItemStack stack) {
		return Screen.getTooltipFromItem(minecraft, stack);
	}

	private static ItemStack decodeStack(ContainerItemRecord record) {
		Minecraft client = Minecraft.getInstance();
		if (record.stackData == null || record.stackData.isBlank() || client.level == null) {
			return ItemStack.EMPTY;
		}
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess());
			Tag tag = TagParser.create(ops).parseFully(record.stackData);
			return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
		} catch (Exception ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static String itemName(String itemId) {
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			return item.getName(new ItemStack(item)).getString();
		}
		return itemId;
	}

	private static final Map<ContainerItemRecord, String> ITEM_NAME_CACHE = new java.util.IdentityHashMap<>();
	private static final Map<String, String> ENCHANTMENT_NAME_CACHE = new HashMap<>();

	private static String localizedItemName(ContainerItemRecord record) {
		String cached = ITEM_NAME_CACHE.get(record);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = decodeStack(record);
		String name = !stack.isEmpty() ? stack.getHoverName().getString() : itemName(record.itemId);
		ITEM_NAME_CACHE.put(record, name);
		return name;
	}

	private static String localizedEnchantmentName(String enchantmentId) {
		return ENCHANTMENT_NAME_CACHE.computeIfAbsent(enchantmentId, ContainerSearchScreen::computeEnchantmentName);
	}

	private static String computeEnchantmentName(String enchantmentId) {
		ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
		if (id == null) {
			return enchantmentId;
		}
		String key = "enchantment." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? enchantmentId : translated;
	}

	private String trim(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("..."))) + "...";
	}

	private static String shortDimension(String dimension) {
		ResourceLocation id = ResourceLocation.tryParse(dimension);
		if (id == null) {
			return dimension;
		}
		String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? id.getPath() : translated;
	}


	private boolean passesDistance(ContainerRecord record) {
		if (viewingKey != null || maxDistance == UNLIMITED_DISTANCE || minecraft == null || minecraft.player == null || minecraft.level == null) {
			return true;
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return false;
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return dx * dx + dy * dy + dz * dz <= maxDistance * (double) maxDistance;
	}

	private int panelLeft() {
		return width / 2 - panelWidth() / 2;
	}

	private int panelTop() {
		return height / 2 - panelHeight() / 2;
	}

	private int panelWidth() {
		return Math.min(Math.min(width - 8, MAX_PANEL_WIDTH), Math.max((int) Math.round(width * 0.85D), 520));
	}

	private int panelHeight() {
		return Math.min(Math.min(height - 8, MAX_PANEL_HEIGHT), Math.max((int) Math.round(height * 0.85D), 300));
	}

	private boolean passesContainerType(ContainerRecord record, Map<String, ContainerTypeEntry> containerTypeMap) {
		if (record.containerType != null && ModSettings.get().isTypeExcluded(containerTypeKey(record.containerType))) {
			return false;
		}
		if (selectedContainerTypeKeys.isEmpty()) {
			return true;
		}
		ContainerTypeEntry entry = containerTypeMap.get(containerTypeKey(record.containerType));
		return entry != null && selectedContainerTypeKeys.contains(entry.key);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	private static double clampScroll(double value, int contentHeight, int viewHeight) {
		return Math.max(0, Math.min(value, Math.max(0, contentHeight - viewHeight)));
	}

	private static String normalizedSearch(String value) {
		String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.replace('ı', 'i');
	}

	private static final class ResultRow {
		private final ContainerRecord record;
		private final ContainerItemRecord item;
		private final String itemId;
		private final String name;
		private final String lowerName;
		private final String variant;
		private double sortDistance;
		private int count;

		private ResultRow(ContainerRecord record, ContainerItemRecord item) {
			this.record = record;
			this.item = item;
			this.itemId = item.itemId;
			this.name = localizedItemName(item);
			this.lowerName = this.name.toLowerCase(Locale.ROOT);
			this.variant = itemVariantKey(item);
			this.count = item.count;
		}

		private void add(ContainerItemRecord item) {
			count += item.count;
		}
	}

	private static final class FilterEntry {
		private final String id;
		private final String name;
		private final String searchText;
		private final int order;
		private int count;

		private FilterEntry(String id, String name) {
			this(id, name, id + " " + name, 1);
		}

		private FilterEntry(String id, String name, String searchText) {
			this(id, name, searchText, 1);
		}

		private FilterEntry(String id, String name, int order) {
			this(id, name, id + " " + name, order);
		}

		private FilterEntry(String id, String name, String searchText, int order) {
			this.id = id;
			this.name = name;
			this.searchText = normalizedSearch(searchText);
			this.order = order;
		}

		private boolean matches(String query) {
			return searchText.contains(query);
		}
	}

	private static final class ContainerTypeEntry {
		private final String key;
		private final String name;
		private final Set<String> typeIds = new LinkedHashSet<>();
		private String searchText;
		private int count;

		private ContainerTypeEntry(String name) {
			this.key = normalizedSearch(name);
			this.name = name;
			this.searchText = this.key;
		}

		private boolean matches(String query) {
			return searchText.contains(query);
		}
	}

	private record EnchantmentChoice(ResourceLocation id, int level, String name) { }
}