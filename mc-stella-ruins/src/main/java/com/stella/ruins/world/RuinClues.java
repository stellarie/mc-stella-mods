package com.stella.ruins.world;

import com.mojang.datafixers.util.Pair;
import com.stella.ruins.StellaRuins;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Clues in ordinary chests, and the maps they turn into. */
public final class RuinClues {
	/** How far a clue looks, in chunks. */
	public static final int SEARCH_CHUNKS = 100;
	/** The map zoom: 2 covers 512 blocks across, like a vanilla explorer map. */
	public static final byte MAP_SCALE = 2;

	private RuinClues() {
	}

	public static void register() {
		// A data pack that replaces a vanilla chest still carries clues: the pool rides every minecraft:chests/* table.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.map(key -> key.identifier())
			.filter(RuinClues::isChestTable)
			.ifPresent(id -> {
				ItemStack clue = roll(context.getRandom());
				if (!clue.isEmpty()) {
					drops.add(clue);
				}
			}));
	}

	/** True for every vanilla chest table, the container loot the clues ride in. */
	public static boolean isChestTable(final Identifier table) {
		return "minecraft".equals(table.getNamespace()) && table.getPath().startsWith("chests/");
	}

	/** One container's draw: a clue at the configured chance, to a site picked by weight, else an empty stack. */
	public static ItemStack roll(final RandomSource random) {
		double chance = RuinConfig.get().clueChance();
		if (chance <= 0.0 || random.nextDouble() >= chance) {
			return ItemStack.EMPTY;
		}
		List<RuinSite> sites = RuinSites.all().stream().filter(site -> site.clueWeight() > 0).toList();
		int total = sites.stream().mapToInt(RuinSite::clueWeight).sum();
		if (total == 0) {
			return ItemStack.EMPTY;
		}
		int pick = random.nextInt(total);
		for (RuinSite site : sites) {
			pick -= site.clueWeight();
			if (pick < 0) {
				return RuinClueItem.of(site.id());
			}
		}
		return ItemStack.EMPTY;
	}

	/** Every registered structure that names this site. */
	public static HolderSet<Structure> structuresOf(final ServerLevel level, final String siteId) {
		List<Holder<Structure>> holders = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements()
			.filter(holder -> holder.value() instanceof RuinStructure structure && structure.site().equals(siteId))
			.map(holder -> (Holder<Structure>) holder)
			.toList();
		return HolderSet.direct(holders);
	}

	/** Finds the nearest site of a kind. The default asks the chunk generator; tests may replace it. */
	@FunctionalInterface
	public interface Locator {
		Optional<BlockPos> find(ServerLevel level, String siteId, BlockPos from);
	}

	private static volatile Locator locator = RuinClues::searchStructures;

	/** Replaces the locator and returns the old one, so a test can restore it in a finally block. */
	public static Locator replaceLocator(final Locator replacement) {
		Locator old = locator;
		locator = replacement;
		return old;
	}

	/** The nearest start of a site within {@link #SEARCH_CHUNKS} chunks, or nothing. */
	public static Optional<BlockPos> locate(final ServerLevel level, final String siteId, final BlockPos from) {
		return locator.find(level, siteId, from);
	}

	private static Optional<BlockPos> searchStructures(final ServerLevel level, final String siteId, final BlockPos from) {
		HolderSet<Structure> structures = structuresOf(level, siteId);
		if (structures.size() == 0) {
			return Optional.empty();
		}
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, structures, from, SEARCH_CHUNKS, false);
		return found == null ? Optional.empty() : Optional.of(found.getFirst());
	}

	/** A filled map centred on the target, with a target mark and the site's name. */
	public static ItemStack mapTo(final ServerLevel level, final String siteId, final BlockPos target) {
		ItemStack map = MapItem.create(level, target.getX(), target.getZ(), MAP_SCALE, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.TARGET_X);
		Component site = Component.translatableWithFallback("site.mod-stella-ruins." + siteId, siteId);
		map.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.mod-stella-ruins.ruin_clue.map", "Map to %s", site));
		StellaRuins.LOGGER.debug("[RUINS] map to {} at {}", siteId, target.toShortString());
		return map;
	}
}
