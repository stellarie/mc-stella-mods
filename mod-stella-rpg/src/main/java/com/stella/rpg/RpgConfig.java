package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public record RpgConfig(
	Map<Effect, Curve> curves, double critMultiplier, DiceRules dice, Map<String, Map<Stat, Double>> mobStats,
	double mobAmplifier, Map<String, ArmorSet> armorSets, WeaponRolls weaponRolls, double healthScale,
	double mobDiceShare, StatTables tables, XpCurve xpCurve, MagicRules magic
) {
	public static final double DEFAULT_CRIT_MULTIPLIER = 1.5;
	public static final String CRIT_MULTIPLIER_KEY = "crit_multiplier";
	public static final double DEFAULT_HEALTH_SCALE = 15.0;
	public static final String HEALTH_SCALE_KEY = "health_scale";
	public static final String DICE_KEY = "dice";
	public static final String MOB_STATS_KEY = "mob_stats";
	public static final String MOB_AMPLIFIER_KEY = "mob_amplifier";
	/** The old per-level weight table; mob_stats replaced it. */
	public static final String RETIRED_MOB_WEIGHTS_KEY = "mob_weights";
	public static final double DEFAULT_MOB_AMPLIFIER = 0.10;
	public static final String MOB_DICE_SHARE_KEY = "mob_dice_share";
	/** Mobs roll this share of their STR and DEX dice, so early mob hits stay near vanilla. */
	public static final double DEFAULT_MOB_DICE_SHARE = 0.5;
	public static final String ARMOR_SETS_KEY = "armor_sets";
	public static final String WEAPON_ROLLS_KEY = "weapon_rolls";
	public static final String COUNT_WEIGHTS_KEY = "count_weights";
	public static final String AFFIX_CHANCE_KEY = "affix_chance";
	public static final String AFFIXES_KEY = "affixes";
	public static final double DEFAULT_AFFIX_CHANCE = 0.30;
	public static final double MAX_MOB_BASE = StatSheet.CEILING;
	private static final Map<Affix, AffixRange> AFFIXES_WOOD = affixes(3, 8, 2, 4, 15, 30, 1, 2, 1, 3, 0.5, 1);
	private static final Map<Affix, AffixRange> AFFIXES_STONE = affixes(5, 12, 3, 5, 25, 50, 2, 3, 2, 4, 1, 1.5);
	private static final Map<Affix, AffixRange> AFFIXES_GOLD = affixes(6, 14, 3, 6, 30, 60, 2, 4, 2, 5, 1, 2);
	private static final Map<Affix, AffixRange> AFFIXES_IRON = affixes(8, 18, 4, 7, 40, 80, 2, 4, 3, 6, 1, 2);
	private static final Map<Affix, AffixRange> AFFIXES_DIAMOND = affixes(12, 27, 5, 9, 60, 120, 3, 6, 4, 8, 1.5, 2.5);
	private static final Map<Affix, AffixRange> AFFIXES_NETHERITE = affixes(18, 40, 6, 12, 90, 180, 4, 8, 6, 12, 2, 3);
	/** Netherite ranges times 1.5, for the tower's void gear. */
	private static final Map<Affix, AffixRange> AFFIXES_VOID = affixes(27, 60, 9, 18, 135, 270, 6, 12, 9, 18, 3, 4.5);
	public static final Map<String, Map<Stat, Double>> DEFAULT_MOB_STATS = defaultMobStats();
	public static final Map<String, ArmorSet> DEFAULT_ARMOR_SETS = defaultArmorSets();
	public static final WeaponRolls DEFAULT_WEAPON_ROLLS = defaultWeaponRolls();
	public static final List<String> REMOVED_KEYS = List.of("melee_damage", "ranged_damage");

	public record Curve(double max, double half) {
	}

	public record ArmorSet(List<Identifier> items, Map<Stat, Integer> perPiece, double flatPerPiece, Map<Stat, Integer> setBonus) {
	}

	/** An affix range in K units for a flat affix, or in percent for a percent affix. */
	public record AffixRange(double min, double max) {
	}

	public record RollTier(
		List<Identifier> items, List<Identifier> armorItems, int min, int max, Map<Affix, AffixRange> affixes
	) {
	}

	public record WeaponRolls(List<Integer> countWeights, Map<String, RollTier> tiers, double affixChance) {
	}

	/**
	 * The magic model. The spell base is in vanilla units. The resistance logistic runs from 0 at INT 0 to 1 at
	 * {@code resistMaxInt}. Resistance is {@code resistBase + resistSpan x logistic}, at most {@code resistCap}.
	 */
	public record MagicRules(
		double spellBase, double spellGrowth, double matkPerInt, double mobPercentPerPoint, double resistBase,
		double resistSpan, double resistMidpoint, double resistWidth, double resistMaxInt, double resistCap
	) {
		public static final String KEY = "magic";
		public static final MagicRules DEFAULT = new MagicRules(12.0, 0.05, 0.01, 50.0, 0.25, 0.50, 400.0, 120.0, 1024.0, 0.90);

		public Map<String, Double> toJson() {
			Map<String, Double> out = new LinkedHashMap<>();
			out.put("spell_base", this.spellBase);
			out.put("spell_growth", this.spellGrowth);
			out.put("matk_per_int", this.matkPerInt);
			out.put("mob_percent_per_point", this.mobPercentPerPoint);
			out.put("resist_base", this.resistBase);
			out.put("resist_span", this.resistSpan);
			out.put("resist_midpoint", this.resistMidpoint);
			out.put("resist_width", this.resistWidth);
			out.put("resist_max_int", this.resistMaxInt);
			out.put("resist_cap", this.resistCap);
			return out;
		}
	}

	public record DiceRules(int baseCount, int baseSides, int countStep, int sidesStep) {
		public static final DiceRules DEFAULT = new DiceRules(1, 4, 25, 100);
	}

	public enum Effect {
		ATTACK_SPEED("attack_speed", 1.00, 250),
		BOW_DRAW("bow_draw", 0.50, 250),
		DAMAGE_REDUCTION("damage_reduction", 0.60, 300),
		COOLDOWN_REDUCTION("cooldown_reduction", 0.50, 400),
		CRIT_CHANCE("crit_chance", 0.50, 300),
		DEX_MAX_ROLL("dex_max_roll", 0.50, 200),
		CRIT_REDUCTION("crit_reduction", 0.50, 300),
		DEBUFF_RESISTANCE("debuff_resistance", 0.75, 100);

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
		return new RpgConfig(Map.copyOf(curves), DEFAULT_CRIT_MULTIPLIER, DiceRules.DEFAULT, DEFAULT_MOB_STATS,
			DEFAULT_MOB_AMPLIFIER, DEFAULT_ARMOR_SETS, DEFAULT_WEAPON_ROLLS, DEFAULT_HEALTH_SCALE, DEFAULT_MOB_DICE_SHARE,
			StatTables.DEFAULT, XpCurve.DEFAULT, MagicRules.DEFAULT);
	}

	/** Base stats per role, in Stat order: STR, AGI, VIT, DEX, INT, LUK. */
	private static Map<String, Map<Stat, Double>> defaultMobStats() {
		Map<String, Map<Stat, Double>> table = new LinkedHashMap<>();
		table.put("melee", weights(12, 6, 12, 3, 1, 3));
		table.put("ranged", weights(4, 10, 5, 12, 2, 10));
		table.put("magic", weights(2, 4, 6, 6, 14, 4));
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

	private static WeaponRolls defaultWeaponRolls() {
		Map<String, RollTier> tiers = new LinkedHashMap<>();
		tiers.put("wood", tier(1, 2, List.of("wooden_sword", "wooden_axe"), armorPieces("leather"), AFFIXES_WOOD));
		tiers.put("stone", tier(1, 3, List.of("stone_sword", "stone_axe"), withTurtle(armorPieces("chainmail")), AFFIXES_STONE));
		tiers.put("copper", tier(1, 3, List.of("copper_sword", "copper_axe"), List.of(), AFFIXES_STONE));
		tiers.put("gold", tier(2, 4, List.of("golden_sword", "golden_axe"), armorPieces("golden"), AFFIXES_GOLD));
		tiers.put("iron", tier(2, 5, List.of("iron_sword", "iron_axe"), armorPieces("iron"), AFFIXES_IRON));
		tiers.put("diamond", tier(3, 7, List.of("diamond_sword", "diamond_axe"), armorPieces("diamond"), AFFIXES_DIAMOND));
		tiers.put("netherite", tier(4, 10, List.of("netherite_sword", "netherite_axe"), armorPieces("netherite"), AFFIXES_NETHERITE));
		tiers.put("ranged", tier(2, 5, List.of("bow", "crossbow"), List.of(), AFFIXES_IRON));
		tiers.put("heavy", tier(3, 7, List.of("trident", "mace"), List.of(), AFFIXES_DIAMOND));
		tiers.put("void", tier(6, 12, List.of(), List.of(), AFFIXES_VOID));
		return new WeaponRolls(List.of(50, 35, 15), Map.copyOf(tiers), DEFAULT_AFFIX_CHANCE);
	}

	/** Ranges in the order of {@link Affix}: damage, MATK, HP, reduction, each flat then percent. K units. */
	private static Map<Affix, AffixRange> affixes(
		final double damageFlatMin, final double damageFlatMax, final double damagePctMin, final double damagePctMax,
		final double hpFlatMin, final double hpFlatMax, final double hpPctMin, final double hpPctMax,
		final double reductionFlatMin, final double reductionFlatMax, final double reductionPctMin, final double reductionPctMax
	) {
		Map<Affix, AffixRange> ranges = new EnumMap<>(Affix.class);
		ranges.put(Affix.DAMAGE_FLAT, new AffixRange(damageFlatMin, damageFlatMax));
		ranges.put(Affix.DAMAGE_PCT, new AffixRange(damagePctMin, damagePctMax));
		ranges.put(Affix.MATK_FLAT, new AffixRange(damageFlatMin, damageFlatMax));
		ranges.put(Affix.MATK_PCT, new AffixRange(damagePctMin, damagePctMax));
		ranges.put(Affix.HP_FLAT, new AffixRange(hpFlatMin, hpFlatMax));
		ranges.put(Affix.HP_PCT, new AffixRange(hpPctMin, hpPctMax));
		ranges.put(Affix.REDUCTION_FLAT, new AffixRange(reductionFlatMin, reductionFlatMax));
		ranges.put(Affix.REDUCTION_PCT, new AffixRange(reductionPctMin, reductionPctMax));
		ranges.put(Affix.MAGIC_PEN, new AffixRange(damagePctMin, damagePctMax));
		ranges.put(Affix.MAGIC_RES_PCT, new AffixRange(hpPctMin, hpPctMax));
		return Map.copyOf(ranges);
	}


	private static List<String> armorPieces(final String prefix) {
		return List.of(prefix + "_helmet", prefix + "_chestplate", prefix + "_leggings", prefix + "_boots");
	}

	private static List<String> withTurtle(final List<String> pieces) {
		List<String> all = new ArrayList<>(pieces);
		all.add("turtle_helmet");
		return all;
	}

	private static RollTier tier(
		final int min, final int max, final List<String> items, final List<String> armor, final Map<Affix, AffixRange> affixes
	) {
		return new RollTier(vanilla(items), vanilla(armor), min, max, affixes);
	}

	private static List<Identifier> vanilla(final List<String> paths) {
		return paths.stream().map(path -> Identifier.fromNamespaceAndPath("minecraft", path)).toList();
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
