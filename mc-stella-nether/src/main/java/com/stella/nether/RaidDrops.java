package com.stella.nether;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A way home: any mob slain in the raid nether leaves a Scroll of Return one time in a hundred, and the
 * Sovereign leaves one for each player in the raid, and two more. The scroll is the spells mod's.
 */
public final class RaidDrops {
	public static final double SCROLL_CHANCE = 0.01;
	/** The Sovereign's scrolls beyond one a player. */
	public static final int SOVEREIGN_EXTRA_SCROLLS = 2;

	private RaidDrops() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && mob.level() instanceof ServerLevel level && level.dimension().equals(RaidTravel.RAID)
				&& !BlazeSovereign.isSovereign(mob) && scrollDrops(mob.getRandom())) {
				ItemEntity dropped = mob.spawnAtLocation(level, new ItemStack(scroll()));
				if (dropped != null) {
					DROPPED.put(mob.getUUID(), dropped);
				}
			}
		});
	}

	/** The chance in force; the shipped chance, but for a test proving a drop lands. */
	private static double chance = SCROLL_CHANCE;
	/** Every scroll a raid mob has left, by the mob, for as long as the server runs. */
	private static final java.util.Map<java.util.UUID, ItemEntity> DROPPED = new java.util.concurrent.ConcurrentHashMap<>();

	public static boolean scrollDrops(final RandomSource random) {
		return random.nextDouble() < chance;
	}

	/** Test hook: sets the chance, and returns the one it replaced so the test can put it back. */
	static double setChance(final double next) {
		double previous = chance;
		chance = next;
		return previous;
	}

	public static @Nullable ItemEntity droppedBy(final java.util.UUID mob) {
		return DROPPED.get(mob);
	}

	/** The spells mod's Scroll of Return. */
	public static Item scroll() {
		return BuiltInRegistries.ITEM.getValue(com.stella.spells.Spellbooks.SCROLL_OF_RETURN_ID);
	}

	/** The Sovereign's scrolls, one a player in the raid and two more. */
	static @Nullable ItemEntity sovereignScrolls(final ServerLevel level, final Mob sovereign) {
		int count = level.players().size() + SOVEREIGN_EXTRA_SCROLLS;
		return sovereign.spawnAtLocation(level, new ItemStack(scroll(), count));
	}
}
