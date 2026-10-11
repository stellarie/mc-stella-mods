package com.stella.tower;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

/**
 * The tests of the older floors (mazes, halls, caverns, stories) keep their floors on the older generator, which the
 * tower keeps for floors built before the biome floors; only the floors from {@link TowerTestSupport#BIOME_FLOORS_FROM}
 * up are biome floors. In play every floor is a biome floor.
 * <p>
 * The test world keeps every floor its runs have climbed, so its tower stands open millions of floors up.
 * No wanderer of such a floor may walk into another test's arena; a wanderer test widens the scan to its
 * own player alone.
 * <p>
 * The tower finds the equips, rpg and spells mods by id and by share, and none of them is in this harness. The colosseum's
 * tests stand in for the three things it reaches: the items the guardians hold and drop, the rpg mod's stat setter, and
 * the spells mod's Blitz Beat. Each stand-in is made only when the real one is absent, and records what it was given.
 */
public final class TowerGameTestInit implements ModInitializer {
	/** What the stand-in stat setter was given, by mob. */
	static final Map<UUID, Map<String, Integer>> STATS = new ConcurrentHashMap<>();
	/** The mobs the stand-in Blitz Beat share was given. */
	static final Set<UUID> BLITZED = ConcurrentHashMap.newKeySet();

	static final Identifier BONE_SHIELD = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "bone_shield");
	static final Identifier SKELETON_CROWN = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "skeleton_crown");
	static final Identifier RUDRA_BOW = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "rudra_bow");

	@Override
	public void onInitialize() {
		TowerWanderers.setScanFilter(player -> false);
		// No ghost squad rises and no merchant appears on a floor by chance: a case that wants one asks for it by floor.
		GhostSquads.pinDefaultRiseChance(0.0);
		TowerMerchants.pinDefaultChance(0.0);
		TowerPlan.drawLegacyFloors(floor -> floor < TowerTestSupport.BIOME_FLOORS_FROM);
		standIn(BONE_SHIELD, false);
		standIn(SKELETON_CROWN, false);
		standIn(RUDRA_BOW, true);
		var shares = FabricLoader.getInstance().getObjectShare();
		if (shares.get(com.stella.tower.colosseum.Guardians.STAT_SHARE) == null) {
			BiConsumer<Mob, Map<?, ?>> stats = (mob, values) -> {
				Map<String, Integer> copy = new java.util.HashMap<>();
				values.forEach((key, value) -> copy.put((String) key, ((Number) value).intValue()));
				STATS.put(mob.getUUID(), copy);
			};
			shares.put(com.stella.tower.colosseum.Guardians.STAT_SHARE, stats);
		}
		if (shares.get(com.stella.tower.colosseum.Guardians.BLITZ_SHARE) == null) {
			Consumer<Mob> blitz = mob -> BLITZED.add(mob.getUUID());
			shares.put(com.stella.tower.colosseum.Guardians.BLITZ_SHARE, blitz);
		}
	}

	private static void standIn(final Identifier id, final boolean bow) {
		if (BuiltInRegistries.ITEM.containsKey(id)) {
			return;
		}
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1);
		Registry.register(BuiltInRegistries.ITEM, id, bow ? new BowItem(properties) : new Item(properties));
	}
}
