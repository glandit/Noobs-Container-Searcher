# Changelog

## 1.23 (branch truhenschutz)

### Added
- Group tab for the optional chest sync server plugin (groups and friends). Only visible on servers with the plugin.
- Server sync: on servers with the plugin, the contents of your own chests and those of your group and friends end up in your search without opening them. Results from other players' chests show the owner. Needs plugin 1.2.0 or newer.

## 1.22

### Fixed
- Fixed the search window lagging when scrolling, hovering, or changing filters with many containers. Thanks to milesgglazer_ for reporting this bug!

## 1.21

### Fixed
- Fixed container data being lost on Realms after leaving and rejoining. Thanks to waffles3383 for reporting this bug!

## 1.20

### Added
- Added a "Locate all" button to results. "Locate" now highlights only that container, "Locate all" highlights every container holding the same item and says so in chat. Thanks to Tally for suggesting this feature!
- Added two Settings toggles to show either result button or both.
- Added a "Fireworks on locate" setting (off by default). It shows fireworks above located containers while they are highlighted.

## 1.19

### Added
- Added an Include / Exclude switch next to the container type chips. Excluded types are hidden from the results and remembered.
- Added a container icon next to the container name in the results.
- Added support for items inside shulker boxes. They show up in the results as "in <box name>" and point at the container holding the box.
- Added ender chest support. The ender inventory is listed once, on the nearest standing ender chest. Locating it highlights every ender chest you opened. If all of them were broken, the items stay listed with a "(removed)" tag and locating says so in chat.
- Added an "In shulker box" quick filter.
- Added `include:`, `exclude:`, `loc:`, `enchant:`, `inside:` and `is:` to the search box, for example `include:chest,shulker_box` or `exclude:dropper`. The start of a name is enough (`include:che`).
- Added a highlight duration setting (5 to 300 seconds on the slider, up to 3600 typed in). Default is 60 seconds.
- Added typing a custom search distance by clicking the distance value.
- Added the size of the saved data to the Settings tab.
- Added a "View other server data" page in Settings. It lists every server and world with its containers, items and size, and lets you view or delete each one.
- Added a Discord button to the header and a Community card to Settings.

### Fixed
- Fixed the "No container types" text overlapping the footer divider.
- Fixed menus that servers open on a right click (virtual chests, shops, plugin menus) being saved as the chest you clicked.

## 1.18

### Added

- Added support for Minecraft 26.4 snapshots 1 to 5 (Fabric).

## 1.17

### Added

- Added German, Russian and Brazilian Portuguese translations.

- Added server datapack switches. Server owners can turn the whole mod, or just the librarian reroll, off with a datapack (the packs are in the `datapacks` folder of the GitHub repository). When it is off the menu still opens and shows a notice that the server administration disabled it, and commands and searches tell you in chat.

## 1.16

### Added

- Added a "changes since your last visit" panel next to the container GUI. It shows which items were added or removed since you last opened the container, other players seen opening it nearby, and hints when a hopper, dropper or dispenser may have moved items, naming the container they went to or came from. If half of a double chest was broken it tells you, and who broke it when they were nearby. The panel scrolls with the mouse wheel and can be turned off in the Settings tab.

- Added direction arrows. After a search, arrows at the screen edge point to the containers you cannot see yet, with their distance. They can be turned off in the Settings tab.

### Changed

- Moved the "Color results by location" toggle to the Settings tab.

## 1.15

### Added

- Added anonymous usage stats. Every 12 hours the mod sends its version, the Minecraft version, the loader (Fabric or NeoForge) and a random install id, so I know which versions are worth supporting. No IP address or any personal data is stored. You can turn it off with the toggle at the bottom of the Settings tab.

## 1.14

### Added

- Added a Locations tab. Name an area (a base, a farm, a mine...) with a radius, a color and any item in the game as its icon. Search results get that location's color, icon and name, and you can filter by location in Quick filters. The radius is typed in blocks and the editor shows how many containers are inside. Thanks to SpeedYi9 for suggesting this feature!

- Added a "Color results by location" toggle in the Locations tab.

### Changed

- New black theme for the search GUI, smoother list scrolling and a flat Librarian Reroll design.

- The search GUI now picks its own GUI scale so it stays readable at any window size.

- The header now shows the mod icon.

### Fixed

- Fixed saved containers being shared between different singleplayer worlds with the same name. Worlds are now told apart by their save folder (data saved before this is no longer shown). On multiplayer, different worlds behind the same server address are now separated too.

- Fixed container highlight outlines not showing through walls on 1.21.1 - 1.21.10.

- Fixed all enchanted books glowing inside a container when one specific book was selected. Now only that book glows.

## 1.13

### Added

- Added NeoForge support. The same single universal jar now runs on both Fabric and NeoForge, on every supported Minecraft version (1.21.1 - 26.3). NeoForge needs no Fabric API.

### Changed

- The search GUI now has a maximum size, so it no longer stretches over the whole screen at small GUI scales (like GUI size 1).

## 1.12

### Added

- Added support for Minecraft 26.3, in the same single universal jar.
- Added an "Item display" toggle to the Settings tab to turn the floating item preview in front of containers on or off (on by default). The preview now works on every supported version instead of only 26.2.
- Added saving of the selected books in the Librarian Reroll tab. The selection survives closing the GUI or cancelling a reroll by accident, and when a wanted enchantment is obtained only that book is deselected, so the rest stay selected for the next villager.

### Fixed

- Fixed container highlight outlines drifting away from the container when moving the camera on 1.21.1, 1.21.4 and 1.21.8.
- Fixed the search GUI being covered by a blur on 1.21.1 and 1.21.4.
- Fixed "Search held item" stealing the S key (move backward) on 1.21.1 - 1.21.11 by changing its default key to R. If you already used the mod, rebind it in Controls.

## 1.11

### Added

- Added a redesigned search GUI: the sidebar is replaced by a tab bar (Search, Librarian Reroll, Settings) with a cleaner, rounded layout. The reroll page is now a tab in the same screen on every supported version.
- Added a Locate button that appears when hovering a result, plus a distance badge on every result and a durability percentage for damaged items.
- Added a sort button (by name or by distance) and clickable active-filter chips that can be removed one by one or all at once.
- Added quick filter chips, a clickable "Try:" row for search syntax (name:, lore:, durability:<50, enchanted), and distance preset chips next to the slider.
- Added a requirements checklist (lectern and axe in hotbar), a status box, and a selected books list to the Librarian Reroll tab. Enchantments are now shown as cards in a grid.
- Added a saved data summary (containers and items) to the Settings tab.

## 1.10

### Fixed

- Fixed the game freezing when opening the search GUI and when saving a newly opened container or villager. The container database is now written to disk on a background thread instead of blocking the game, and the file is written in a compact format so it is smaller and faster to save.
- Fixed the search GUI doing heavy repeated work on every open and every keystroke in the search box. Item and enchantment names are now resolved once and reused, so opening the GUI and typing a search is much smoother, especially with a large number of saved containers.

## 1.9

### Added

- Added support for 1.21.1, 1.21.4, 1.21.8, 1.21.10, 26.1, and 26.1.2, on top of the existing 1.21.11 and 26.2 support — all from the same single universal jar.

### Note

- The newly added versions (1.21.1, 1.21.4, 1.21.8, 1.21.10, 26.1, 26.1.2) are freshly added and less battle-tested than 1.21.11/26.2. If you run into anything odd on one of them, please report it.

## 1.8

### Changed

- Both 1.21.11 and 26.2 are now supported by a single universal jar. Download one file, and it works on either Minecraft version — no more picking the right jar or ending up with leftover old versions in your mods folder after an update.

### Fixed

- Fixed container highlight outlines not appearing in the world at all in some cases (the item glow inside an opened container still worked, but the box outline around the container itself silently failed to draw). Outlines are now also visible through walls, matching the intended "x-ray" highlight behavior.

## 1.7

### Added

- Added a "Delete All Data" button to the search GUI, to permanently clear all saved container data for the current server.

### Fixed

- Fixed some servers' virtual/plugin menus being wrongly attributed to a real nearby chest and saved as if they were its contents. The search GUI now also verifies the opened menu's title against the block's own container name before recording it.

## 1.4

### Added

- 26.2 support.
- Villager items support.

### Fixed

- Fixed container and villager records deleting themselves over time. Cleanup now waits 15 seconds after joining a world or changing dimension, only verifies records near the player (48 blocks for containers, 32 for villagers), and requires several consecutive confirmations before removing a record.
- Fixed containers behind item frames not being detected when opened through entity interaction.
- Fixed obstructed chests being ignored by container detection.
- Fixed silent data loss when the saved container data file was corrupted; it is now backed up instead of being overwritten.

### Changed

- Gui design.
- Reduced disk writes by batching villager position updates instead of saving every second.
- Added safeguards so an opened menu can no longer be attributed to the wrong container.

## 1.3

### Added

- Added a distance slider to the search GUI.
- Added a separate container type filter section.

### Changed

- Updated the search GUI background to a cleaner dark gray style.
- Made result rows more compact to reduce text overlap.
- Improved result text layout for long item names and item IDs.

## 1.2

- Container search GUI now merges duplicate stacks from the same container into one result row.
- Result item count now shows the total amount for that item in that container.
- Added distance info to GUI result rows.
- Fixed distant/unloaded container outlines rendering incorrectly.
- Updated the search GUI theme to a darker gray style from cyan style.
