package com.stella.learn.gym;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * How tough a bot is this episode: maximum health 20 to 60, armor 0 to 20, armor toughness 0 to 12, a Protection level 0
 * to IV on its armor, and in one episode of ten Resistance or absorption. The numbers come from the seed alone, through
 * SplitMix64, so `training/stella_train/bodies.py` computes the same ones and the eval reports can group by toughness.
 */
public record Body(int maxHealth, int armor, int toughness, int protection, Special special) {
	public enum Special {
		NONE,
		RESISTANCE,
		ABSORPTION
	}

	private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
	private static final int EFFECT_TICKS = 1_000_000;
	private static final int RESISTANCE_LEVEL = 1;
	private static final int ABSORPTION_LEVEL = 1;

	/** The bounds one stage draws from. `specialPermille` is the share of episodes with Resistance or absorption, in thousandths. */
	public record Range(int healthMin, int healthMax, int armorMax, int toughnessMax, int protectionMax, int specialPermille) {
		public static final Range FULL = new Range(20, 60, 20, 12, 4, 100);

		/** An all-zero range on the wire means the trainer named none, and the full range applies. */
		public static Range orFull(final Range asked) {
			return asked.healthMax() <= 0 ? FULL : asked;
		}
	}

	public static Body draw(final long seed) {
		return draw(seed, Range.FULL);
	}

	public static Body draw(final long seed, final Range range) {
		long[] state = {seed + GOLDEN_GAMMA};
		int maxHealth = range.healthMin() + (int) (next(state) * (range.healthMax() - range.healthMin() + 1));
		int armor = (int) (next(state) * (range.armorMax() + 1));
		int toughness = (int) (next(state) * (range.toughnessMax() + 1));
		int protection = (int) (next(state) * (range.protectionMax() + 1));
		double roll = next(state);
		double which = next(state);
		Special special = roll < range.specialPermille() / 1000.0 ? (which < 0.5 ? Special.RESISTANCE : Special.ABSORPTION) : Special.NONE;
		return new Body(maxHealth, armor, toughness, protection, special);
	}

	private static double next(final long[] state) {
		state[0] += GOLDEN_GAMMA;
		long z = state[0];
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}

	/** Sets the attributes after the armor is on, so the totals are this body's, not the gear's plus this body's. */
	public void apply(final ServerPlayer player) {
		var health = player.getAttribute(Attributes.MAX_HEALTH);
		health.setBaseValue(maxHealth);
		player.setHealth(maxHealth);
		var armorAttribute = player.getAttribute(Attributes.ARMOR);
		var toughnessAttribute = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
		armorAttribute.setBaseValue(0.0);
		toughnessAttribute.setBaseValue(0.0);
		armorAttribute.setBaseValue(Math.max(0.0, armor - player.getAttributeValue(Attributes.ARMOR)));
		toughnessAttribute.setBaseValue(Math.max(0.0, toughness - player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		switch (special) {
			case RESISTANCE -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EFFECT_TICKS, RESISTANCE_LEVEL, false, false));
			case ABSORPTION -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, EFFECT_TICKS, ABSORPTION_LEVEL, false, false));
			default -> {
			}
		}
	}

	public String detail() {
		return "maxHealth=" + maxHealth + " armor=" + armor + " toughness=" + toughness + " protection=" + protection + " special=" + special;
	}
}
