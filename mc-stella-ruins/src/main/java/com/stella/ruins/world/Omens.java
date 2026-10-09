package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Rolling the omens of a run, telling the players, and the weakness pulse of the Hexed omen. */
public final class Omens {
	/** The most omens one run carries. */
	public static final int MAX = 2;
	/** The chance of a first omen at tier 0. */
	public static final double BASE_CHANCE = 0.25;
	/** What each tier adds to that chance. */
	public static final double TIER_CHANCE = 0.15;
	/** The most the first chance can reach. */
	public static final double CHANCE_CAP = 0.9;
	/** The second omen comes at this share of the first chance. */
	public static final double SECOND_SHARE = 0.5;
	public static final int HEX_TICKS = 100;

	/** Picks the omens of one run. A test replaces it. */
	@FunctionalInterface
	public interface Roller {
		List<Omen> roll(RandomSource random, int tier);
	}

	private static volatile Roller roller = Omens::rollDefault;

	private Omens() {
	}

	/** Replaces the roller and returns the old one, so a test can restore it in a finally block. */
	public static Roller replaceRoller(final Roller replacement) {
		Roller old = roller;
		roller = replacement;
		return old;
	}

	public static List<Omen> roll(final RandomSource random, final int tier) {
		return roller.roll(random, tier);
	}

	/** The chance of a first omen at a tier. */
	public static double firstChance(final int tier) {
		return Math.min(CHANCE_CAP, BASE_CHANCE + TIER_CHANCE * Math.max(0, tier));
	}

	static List<Omen> rollDefault(final RandomSource random, final int tier) {
		List<Omen> pool = new ArrayList<>(List.of(Omen.values()));
		List<Omen> chosen = new ArrayList<>();
		double chance = firstChance(tier);
		while (chosen.size() < MAX && random.nextDouble() < chance) {
			chosen.add(pool.remove(random.nextInt(pool.size())));
			chance *= SECOND_SHARE;
		}
		return chosen;
	}

	public static List<Omen> of(final List<String> ids) {
		return ids.stream().map(Omen::byId).filter(Objects::nonNull).toList();
	}

	public static List<String> ids(final List<Omen> omens) {
		return omens.stream().map(Omen::id).toList();
	}

	/** The action bar line that names the omens of a run. */
	public static Component notice(final List<Omen> omens) {
		Component names = Component.empty();
		for (int i = 0; i < omens.size(); i++) {
			if (i > 0) {
				names = Component.empty().append(names).append(Component.literal(", "));
			}
			names = Component.empty().append(names).append(Component.translatable("omen.mod-stella-ruins." + omens.get(i).id()));
		}
		return Component.translatable("message.mod-stella-ruins.omen", names);
	}

	public static void announce(final List<ServerPlayer> players, final List<Omen> omens) {
		if (omens.isEmpty()) {
			return;
		}
		Component line = notice(omens);
		for (ServerPlayer player : players) {
			player.sendOverlayMessage(line);
		}
	}

	/** The Hexed omen: players inside the radius feel weak while the run lasts. */
	public static void pulse(final List<ServerPlayer> players, final List<Omen> omens) {
		if (omens.contains(Omen.HEXED)) {
			for (ServerPlayer player : players) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HEX_TICKS, 0, true, true));
			}
		}
	}
}
