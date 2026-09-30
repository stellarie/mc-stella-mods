package com.stella.rpg;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public record RpgConfig(
	Map<Effect, Curve> curves, double critMultiplier, DiceRules dice, Map<String, Map<Stat, Double>> mobWeights,
	Map<String, ArmorSet> armorSets
) {
	public static final double DEFAULT_CRIT_MULTIPLIER = 1.5;
	public static final String CRIT_MULTIPLIER_KEY = "crit_multiplier";
	public static final String DICE_KEY = "dice";
	public static final String MOB_WEIGHTS_KEY = "mob_weights";
	public static final String ARMOR_SETS_KEY = "armor_sets";
	public static final double MAX_MOB_WEIGHT = 100.0;
	public static final Map<String, Map<Stat, Double>> DEFAULT_MOB_WEIGHTS = defaultMobWeights();
	public static final Map<String, ArmorSet> DEFAULT_ARMOR_SETS = defaultArmorSets();
	public static final List<String> REMOVED_KEYS = List.of("melee_damage", "ranged_damage");

	public record Curve(double max, double half) {
	}

	public record ArmorSet(List<Identifier> items, Map<Stat, Integer> perPiece, double flatPerPiece, Map<Stat, Integer> setBonus) {
	}

	public record DiceRules(int baseCount, int baseSides, int countStep, int sidesStep) {
		public static final DiceRules DEFAULT = new DiceRules(1, 4, 50, 100);
	}

	public enum Effect {
		ATTACK_SPEED("attack_speed", 1.00, 250),
		BOW_DRAW("bow_draw", 0.50, 250),
		DAMAGE_REDUCTION("damage_reduction", 0.60, 300),
		SPELL_POWER("spell_power", 1.50, 200),
		COOLDOWN_REDUCTION("cooldown_reduction", 0.40, 300),
		CRIT_CHANCE("crit_chance", 0.50, 300);

		private final String key;
		private final Curve defaultCurve;

		Effect(final String key, final double max, final double half) {
			this.key = key;
			this.defaultCurve = new Curve(max, half);
		}

		public String key() {
			return this.key;
		}

		public Curve defaultCurve() {
			return this.defaultCurve;
		}
	}

	public static RpgConfig defaults() {
		Map<Effect, Curve> curves = new EnumMap<>(Effect.class);
		for (Effect effect : Effect.values()) {
			curves.put(effect, effect.defaultCurve());
		}
		return new RpgConfig(Map.copyOf(curves), DEFAULT_CRIT_MULTIPLIER, DiceRules.DEFAULT, DEFAULT_MOB_WEIGHTS,
			DEFAULT_ARMOR_SETS);
	}

	private static Map<String, Map<Stat, Double>> defaultMobWeights() {
		Map<String, Map<Stat, Double>> table = new LinkedHashMap<>();
		table.put("melee", weights(1.00, 0.50, 1.00, 0.25, 0.25, 0.50));
		table.put("ranged", weights(0.25, 1.00, 0.50, 1.00, 0.25, 0.50));
		table.put("magic", weights(0.25, 0.50, 0.50, 0.25, 1.00, 0.50));
		return Map.copyOf(table);
	}

	private static Map<String, ArmorSet> defaultArmorSets() {
		Map<String, ArmorSet> table = new LinkedHashMap<>();
		table.put("leather", armorSet("leather", 0.10, bonus(Stat.AGI, 1), bonus(Stat.AGI, 3, Stat.DEX, 2)));
		table.put("chainmail", armorSet("chainmail", 0.20, bonus(Stat.DEX, 1), bonus(Stat.DEX, 3, Stat.AGI, 2)));
		table.put("iron", armorSet("iron", 0.25, bonus(Stat.VIT, 1), bonus(Stat.VIT, 3, Stat.STR, 2)));
		table.put("gold", armorSet("golden", 0.10, bonus(Stat.LUK, 1), bonus(Stat.LUK, 3, Stat.INT, 2)));
		table.put("diamond", armorSet("diamond", 0.40, bonus(Stat.VIT, 2), bonus(Stat.VIT, 4, Stat.STR, 4)));
		table.put("netherite", armorSet("netherite", 0.50, bonus(Stat.STR, 2, Stat.VIT, 2),
			bonus(Stat.STR, 6, Stat.VIT, 6, Stat.LUK, 2)));
		return Map.copyOf(table);
	}

	private static ArmorSet armorSet(
		final String prefix, final double flat, final Map<Stat, Integer> perPiece, final Map<Stat, Integer> setBonus
	) {
		List<Identifier> items = List.of("helmet", "chestplate", "leggings", "boots").stream()
			.map(piece -> Identifier.fromNamespaceAndPath("minecraft", prefix + "_" + piece)).toList();
		return new ArmorSet(items, perPiece, flat, setBonus);
	}

	private static Map<Stat, Integer> bonus(final Object... pairs) {
		Map<Stat, Integer> map = new EnumMap<>(Stat.class);
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Stat) pairs[i], (Integer) pairs[i + 1]);
		}
		return Map.copyOf(map);
	}

	private static Map<Stat, Double> weights(final double... values) {
		Map<Stat, Double> row = new EnumMap<>(Stat.class);
		for (Stat stat : Stat.values()) {
			row.put(stat, values[stat.ordinal()]);
		}
		return Map.copyOf(row);
	}

	public Curve curve(final Effect effect) {
		return this.curves.get(effect);
	}
}
