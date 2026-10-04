package com.stella.equips.unique;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The rank of a mob for the unique drop chance: normal 1, elite 3, Nemesis 5, boss 10 and summoned 0. The larger
 * value wins, and summoned overrides every other. The rank reads the two sibling shares by key, so a missing sibling
 * mod leaves every mob normal or a fixed boss type.
 */
public final class UniqueRank {
	public static final String MOB_RANK_SHARE = "mod-stella-mobs:mob-rank";
	public static final String SOVEREIGN_SHARE = "mod-stella-nether:sovereign";
	public static final String NEMESIS_TAG = "stella_nemesis";
	public static final String SUMMONED_TAG = "mod-stella-spells.summoned";
	public static final int NORMAL = 1;
	public static final int ELITE = 3;
	public static final int NEMESIS = 5;
	public static final int BOSS = 10;
	public static final int SUMMONED = 0;
	/** Vanilla bosses that need no share to be known as bosses. */
	public static final Set<Identifier> FIXED_BOSSES = Set.of(
		Identifier.parse("minecraft:wither"), Identifier.parse("minecraft:ender_dragon"),
		Identifier.parse("minecraft:warden"), Identifier.parse("minecraft:elder_guardian"));

	private UniqueRank() {
	}

	public static int of(final LivingEntity entity) {
		String shared = sharedRank(entity);
		if ("summoned".equals(shared) || entity.entityTags().contains(SUMMONED_TAG)) {
			return SUMMONED;
		}
		int rank = NORMAL;
		if ("boss".equals(shared) || FIXED_BOSSES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) || isSovereign(entity)) {
			rank = Math.max(rank, BOSS);
		}
		if (entity.entityTags().contains(NEMESIS_TAG)) {
			rank = Math.max(rank, NEMESIS);
		}
		if ("elite".equals(shared)) {
			rank = Math.max(rank, ELITE);
		}
		return rank;
	}

	/** The value of the mobs share for an entity, or null when the share is absent, broken, or says nothing. */
	@SuppressWarnings("unchecked")
	private static @Nullable String sharedRank(final Entity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(MOB_RANK_SHARE);
		if (!(shared instanceof Function<?, ?> function)) {
			return null;
		}
		try {
			return ((Function<Entity, ?>) function).apply(entity) instanceof String value ? value : null;
		} catch (RuntimeException failure) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean isSovereign(final Entity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SOVEREIGN_SHARE);
		if (!(shared instanceof Predicate<?> predicate)) {
			return false;
		}
		try {
			return ((Predicate<Entity>) predicate).test(entity);
		} catch (RuntimeException failure) {
			return false;
		}
	}

	/** Whether the nether share names this entity the Sovereign. */
	public static boolean sovereign(final Entity entity) {
		return isSovereign(entity);
	}
}
