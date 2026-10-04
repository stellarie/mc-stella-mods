package com.stella.spells;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Every spell's flat numbers, in vanilla units before any scaling. A spell effect and the tooltip read the
 * same record, so the text on a book cannot drift from what the cast does. A spell with no number still has
 * an {@link #EMPTY} entry, so a new spell that skips this class fails {@code everySpellHasNumbers}.
 */
public record SpellNumbers(
	float damage,
	float heal,
	float allyHeal,
	Timing duration,
	Timing extra,
	int amplifier,
	int extraAmplifier,
	double radius,
	int targets,
	int capTicks,
	boolean control
) {
	/** Which cast duration multiplier a timing follows. */
	public enum Scale {
		OFFENSIVE,
		DEFENSIVE,
		FIXED
	}

	/** A length in ticks and the multiplier that stretches it. */
	public record Timing(int ticks, Scale scale) {
		public static final Timing NONE = new Timing(0, Scale.FIXED);

		/** The ticks a cast with these modifiers lasts. */
		int scaled(final SpellModifiers.CastModifiers modifiers) {
			return this.scaled(this.ticks, modifiers);
		}

		/** The same multiplier applied to another length, as Sapping Hex does for every hostile it weakens. */
		int scaled(final int baseTicks, final SpellModifiers.CastModifiers modifiers) {
			return this.scale == Scale.FIXED ? baseTicks : modifiers.durationTicks(baseTicks, this.scale == Scale.DEFENSIVE);
		}
	}

	private static final Map<String, SpellNumbers> BY_PATH = new LinkedHashMap<>();
	private static final Timing NO_TIMING = Timing.NONE;
	/** A self buff or a party hymn lasts half an hour, and no multiplier stretches it. */
	private static final int BUFF_TICKS = 36_000;

	public static final SpellNumbers EMPTY = builder("").build();
	public static final SpellNumbers SPARK_SHOT = builder("spark_shot").damage(3.0f).extra(120, Scale.OFFENSIVE).build();
	public static final SpellNumbers FROSTBIND = builder("frostbind").duration(160, Scale.OFFENSIVE).amplifier(1).control().build();
	public static final SpellNumbers GALE_STEP = builder("gale_step").duration(40, Scale.OFFENSIVE).build();
	public static final SpellNumbers MENDING_WORD = builder("mending_word").heal(4.0f).build();
	public static final SpellNumbers STONEGUARD = builder("stoneguard").duration(BUFF_TICKS, Scale.FIXED).build();
	public static final SpellNumbers STATIC_LASH = builder("static_lash").damage(3.0f).radius(4.0).targets(2).build();
	public static final SpellNumbers BRIAR_SNARE = builder("briar_snare").duration(160, Scale.OFFENSIVE).amplifier(3).control().build();
	public static final SpellNumbers TIDAL_PUSH = builder("tidal_push").build();
	public static final SpellNumbers OWLS_GAZE = builder("owls_gaze").duration(BUFF_TICKS, Scale.FIXED).build();
	public static final SpellNumbers SLIMEBOUND = builder("slimebound").duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers CHAINSTORM = builder("chainstorm").damage(6.0f).radius(5.0).targets(5).build();
	public static final SpellNumbers RIFTWALK = builder("riftwalk").build();
	public static final SpellNumbers PHOENIX_MANTLE = builder("phoenix_mantle").heal(4.0f).duration(BUFF_TICKS, Scale.FIXED).build();
	public static final SpellNumbers LIFE_BLOOM = builder("life_bloom").heal(8.0f).duration(300, Scale.DEFENSIVE).build();
	public static final SpellNumbers VOID_WELL = builder("void_well").radius(5.0).duration(120, Scale.OFFENSIVE).amplifier(1).control().build();
	public static final SpellNumbers WARDENS_ROAR = builder("wardens_roar").damage(6.0f).radius(5.0).build();
	public static final SpellNumbers AEGIS_OF_THE_DEEP = builder("aegis_of_the_deep").duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers WITHER_SEAL = builder("wither_seal").duration(300, Scale.OFFENSIVE).amplifier(1).build();
	public static final SpellNumbers TIME_LOCK = builder("time_lock").radius(5.0).duration(200, Scale.OFFENSIVE).amplifier(1).control().build();
	public static final SpellNumbers DRAGONFALL = builder("dragonfall").damage(10.0f).radius(3.0).build();
	/** Ice Lance's radius is its reach in blocks. */
	public static final SpellNumbers ICE_LANCE = builder("ice_lance").damage(5.0f).radius(16.0).duration(280, Scale.OFFENSIVE).control().build();
	public static final SpellNumbers SOUL_SIPHON = builder("soul_siphon").damage(4.0f).build();
	public static final SpellNumbers VENOM_DART = builder("venom_dart").damage(2.0f).duration(300, Scale.OFFENSIVE).amplifier(1).build();
	/** Sapping Hex lasts its duration for every hostile it weakens, up to the cap; the extra timing is the caster's Strength. */
	public static final SpellNumbers SAPPING_HEX = builder("sapping_hex").radius(6.0).duration(180, Scale.OFFENSIVE)
		.extra(180, Scale.DEFENSIVE).cap(900).amplifier(1).extraAmplifier(1).build();
	public static final SpellNumbers BLINDING_FLASH = builder("blinding_flash").duration(300, Scale.OFFENSIVE).extra(600, Scale.OFFENSIVE).build();
	public static final SpellNumbers EMBER_BRAND = builder("ember_brand").damage(2.0f).duration(600, Scale.OFFENSIVE).build();
	public static final SpellNumbers QUICKSILVER = builder("quicksilver").duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers TIDE_LUNGS = builder("tide_lungs").duration(BUFF_TICKS, Scale.FIXED).extra(BUFF_TICKS, Scale.FIXED).build();
	public static final SpellNumbers BERSERK = builder("berserk").duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers HASTE_RUNE = builder("haste_rune").duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	/** Shadow Veil's extra timing is how long its breaking blow stops the victim; the cast does not scale it. */
	public static final SpellNumbers SHADOW_VEIL = builder("shadow_veil").duration(BUFF_TICKS, Scale.FIXED).extra(40, Scale.FIXED)
		.extraAmplifier(6).build();
	public static final SpellNumbers SANCTUARY = builder("sanctuary").damage(3.0f).radius(6.0).duration(600, Scale.DEFENSIVE).build();
	public static final SpellNumbers PURIFY = builder("purify").radius(6.0).build();
	public static final SpellNumbers FROST_NOVA = builder("frost_nova").damage(3.0f).radius(5.0).duration(120, Scale.OFFENSIVE).amplifier(3).control().build();
	public static final SpellNumbers EARTHSPIKE = builder("earthspike").damage(4.0f).radius(3.0).build();
	/** Plague Cloud's duration is the cloud's; the extra timing is its Poison, which the cast does not scale. */
	public static final SpellNumbers PLAGUE_CLOUD = builder("plague_cloud").radius(3.0).duration(300, Scale.OFFENSIVE)
		.extra(300, Scale.FIXED).build();
	/** Flame Ring's duration is its aura; the extra timing is the fire it sets on each hostile. */
	public static final SpellNumbers FLAME_RING = builder("flame_ring").damage(3.0f).radius(5.0).duration(6000, Scale.DEFENSIVE)
		.extra(180, Scale.OFFENSIVE).build();
	public static final SpellNumbers GRAVITY = builder("gravity").radius(6.0).duration(320, Scale.OFFENSIVE).amplifier(1).control().build();
	public static final SpellNumbers BULWARK_HYMN = builder("bulwark_hymn").radius(8.0).duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers TAILWIND = builder("tailwind").radius(8.0).duration(BUFF_TICKS, Scale.FIXED).amplifier(1).build();
	public static final SpellNumbers BATTLE_HYMN = builder("battle_hymn").radius(8.0).duration(BUFF_TICKS, Scale.FIXED).build();
	public static final SpellNumbers SUMMON_SKELETON = builder("summon_skeleton").build();

	/** Ticks of this spell's control on the target: half on an elite or a boss. */
	int controlTicks(final LivingEntity target, final int ticks) {
		return this.control && target instanceof Mob mob && MobSpells.isElite(mob) ? ticks / 2 : ticks;
	}

	/** The numbers of the spell with this id, or null when none were declared. */
	public static @Nullable SpellNumbers find(final Identifier spell) {
		return BY_PATH.get(spell.getPath());
	}

	/** The numbers of the spell at this path; an unknown path is a programming error. */
	public static SpellNumbers of(final String path) {
		SpellNumbers numbers = BY_PATH.get(path);
		if (numbers == null) {
			throw new IllegalArgumentException("No numbers declared for spell " + path);
		}
		return numbers;
	}

	/** Every declared spell path, in declaration order. */
	public static java.util.Set<String> paths() {
		return java.util.Collections.unmodifiableSet(BY_PATH.keySet());
	}

	/** True when the spell has no number to show, so its tooltip keeps the static line. */
	public boolean isEmpty() {
		return this.damage == 0.0f && this.heal == 0.0f && this.allyHeal == 0.0f && this.duration.ticks == 0
			&& this.extra.ticks == 0 && this.radius == 0.0 && this.targets == 0 && this.capTicks == 0;
	}

	/** A hostile-facing duration of this many ticks under the cast's multipliers. */
	int durationTicks(final SpellModifiers.CastModifiers modifiers) {
		return this.duration.scaled(modifiers);
	}

	int extraTicks(final SpellModifiers.CastModifiers modifiers) {
		return this.extra.scaled(modifiers);
	}

	double radius(final SpellModifiers.CastModifiers modifiers) {
		return this.radius * modifiers.radiusMultiplier();
	}

	private static Builder builder(final String path) {
		return new Builder(path);
	}

	private static final class Builder {
		private final String path;
		private float damage;
		private float heal;
		private float allyHeal;
		private Timing duration = NO_TIMING;
		private Timing extra = NO_TIMING;
		private int amplifier;
		private int extraAmplifier;
		private double radius;
		private int targets;
		private int capTicks;
		private boolean control;

		Builder(final String path) {
			this.path = path;
		}

		Builder damage(final float value) {
			this.damage = value;
			return this;
		}

		Builder heal(final float value) {
			this.heal = value;
			return this;
		}

		Builder allyHeal(final float value) {
			this.allyHeal = value;
			return this;
		}

		Builder duration(final int ticks, final Scale scale) {
			this.duration = new Timing(ticks, scale);
			return this;
		}

		Builder extra(final int ticks, final Scale scale) {
			this.extra = new Timing(ticks, scale);
			return this;
		}

		Builder amplifier(final int value) {
			this.amplifier = value;
			return this;
		}

		Builder extraAmplifier(final int value) {
			this.extraAmplifier = value;
			return this;
		}

		Builder radius(final double value) {
			this.radius = value;
			return this;
		}

		Builder targets(final int value) {
			this.targets = value;
			return this;
		}

		Builder cap(final int ticks) {
			this.capTicks = ticks;
			return this;
		}

		Builder control() {
			this.control = true;
			return this;
		}

		SpellNumbers build() {
			SpellNumbers numbers = new SpellNumbers(
				this.damage, this.heal, this.allyHeal, this.duration, this.extra, this.amplifier, this.extraAmplifier,
				this.radius, this.targets, this.capTicks, this.control
			);
			if (!this.path.isEmpty()) {
				BY_PATH.put(this.path, numbers);
			}
			return numbers;
		}
	}
}
