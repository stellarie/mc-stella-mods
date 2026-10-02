package com.stella.rpg;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * Stat growth that another mod grants a mob, for example a village defender's reward for a kill. The totals are saved
 * on the mob as decimals, so half points add up, and a stat reads only the whole points. Another mod grows a stat
 * through the object share {@link #SHARE_KEY}, which needs no compile dependency on this mod: a
 * {@code BiFunction<Mob, Double, String>} that grows one random stat by the amount and returns the stat's key.
 */
public final class MobGrowth {
	public static final String SHARE_KEY = "mod-stella-rpg:mob-stat-grow";
	/** Keyed by `Stat.key()`, so a renamed constant cannot break a save. */
	public static final AttachmentType<Map<String, Double>> ATTACHMENT = AttachmentRegistry.create(
		StellaRpg.id("mob_growth"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.DOUBLE))
	);

	private MobGrowth() {
	}

	public static void register() {
		BiFunction<Mob, Double, String> share = (mob, amount) -> grow(mob, amount, mob.getRandom()).key();
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, share);
	}

	/** Grows one of the six stats, chosen at random, and returns it. */
	public static Stat grow(final Mob mob, final double amount, final RandomSource random) {
		Stat stat = Stat.values()[random.nextInt(Stat.values().length)];
		grow(mob, stat, amount);
		return stat;
	}

	public static void grow(final Mob mob, final Stat stat, final double amount) {
		Map<String, Double> totals = new HashMap<>(mob.getAttachedOrElse(ATTACHMENT, Map.of()));
		totals.merge(stat.key(), amount, Double::sum);
		mob.setAttached(ATTACHMENT, Map.copyOf(totals));
	}

	/** The whole points of growth in a stat. */
	public static int bonus(final Mob mob, final Stat stat) {
		Double total = mob.getAttachedOrElse(ATTACHMENT, Map.of()).get(stat.key());
		return total == null ? 0 : (int) Math.floor(total);
	}
}
