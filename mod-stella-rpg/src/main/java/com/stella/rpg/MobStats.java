package com.stella.rpg;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/** A mob's stat: its role's base, amplified by its level, plus any growth another mod granted it. Another mod can fix a stat outright through {@link MobStatOverride}. */
public final class MobStats {
	public static final String MELEE = "melee";
	public static final int MINIMUM = 1;

	public static final int MIN_TOTAL = 6;
	public static final double MAIN_SHARE = 0.4;
	private static final String THREAT_KEY = "threat";
	/** Stat points rolled once per mob, with the threat they were rolled at. */
	public static final AttachmentType<Map<String, Integer>> ROLL = AttachmentRegistry.create(
		StellaRpg.id("mob_roll"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
	);

	private static volatile boolean randomized = true;

	private MobStats() {
	}

	/** A test turns the roll off so suites can pin exact numbers. */
	public static void setRandomized(final boolean on) {
		randomized = on;
	}

	/** Total about the threat (plus or minus 10%), main stat 40%, the rest spread at random. */
	public static Map<Stat, Integer> roll(final String role, final int threat, final RandomSource random) {
		int total = Math.max(MIN_TOTAL, (int) Math.round(Math.max(0, threat) * (0.9 + 0.2 * random.nextDouble())));
		Stat main = switch (role) {
			case "ranged" -> Stat.DEX;
			case "magic" -> Stat.INT;
			default -> Stat.STR;
		};
		Map<Stat, Integer> points = new java.util.EnumMap<>(Stat.class);
		for (Stat stat : Stat.values()) {
			points.put(stat, 0);
		}
		int mainPoints = (int) Math.round(total * MAIN_SHARE);
		points.put(main, mainPoints);
		int cap = Math.max(1, mainPoints - 1);
		java.util.List<Stat> others = new java.util.ArrayList<>(java.util.List.of(Stat.values()));
		others.remove(main);
		for (int left = total - mainPoints; left > 0; left--) {
			Stat pick = others.get(random.nextInt(others.size()));
			points.merge(pick, 1, Integer::sum);
			if (points.get(pick) >= cap) {
				others.remove(pick);
				if (others.isEmpty()) {
					points.merge(main, left - 1, Integer::sum);
					break;
				}
			}
		}
		return points;
	}

	private static int rolled(final Mob mob, final Stat stat) {
		int threat = Math.max(0, MobsBridge.threat(mob));
		Map<String, Integer> saved = mob.getAttached(ROLL);
		if (saved == null || saved.getOrDefault(THREAT_KEY, -1) != threat) {
			Map<String, Integer> fresh = new HashMap<>();
			roll(MobsBridge.role(mob), threat, mob.getRandom()).forEach((key, value) -> fresh.put(key.key(), value));
			fresh.put(THREAT_KEY, threat);
			saved = Map.copyOf(fresh);
			mob.setAttached(ROLL, saved);
		}
		return saved.getOrDefault(stat.key(), 0);
	}

	public static int base(final Mob mob, final Stat stat) {
		int base = MobStatOverride.value(mob, stat).orElseGet(() -> randomized ? rolled(mob, stat) :
			base(RpgConfigStore.current(), MobsBridge.role(mob), stat, MobsBridge.threat(mob)));
		return (int) Math.min(StatSheet.CEILING, (long) base + MobGrowth.bonus(mob, stat));
	}

	public static int base(final RpgConfig config, final String role, final Stat stat, final int threat) {
		Map<String, Map<Stat, Double>> table = config.mobStats();
		Map<Stat, Double> row = table.get(role);
		if (row == null) {
			row = table.get(MELEE);
		}
		long scaled = Math.round(row.get(stat) * (1.0 + config.mobAmplifier() * Math.max(0, threat)));
		return (int) Math.max(MINIMUM, Math.min(StatSheet.CEILING, scaled));
	}
}
