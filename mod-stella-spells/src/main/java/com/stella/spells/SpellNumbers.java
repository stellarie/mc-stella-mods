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

	// ---- fighter books: damage is flat, and the bridge turns it into the caster's Strength percent. ----
	/** Cleave's own reach is its radius; its duration is the bleed it leaves. */
	public static final SpellNumbers CLEAVE = builder("cleave").damage(4.0f).radius(4.0).duration(300, Scale.DEFENSIVE).build();
	/** Leap Slam's extra is the Slowness its awakened landing leaves. */
	public static final SpellNumbers LEAP_SLAM = builder("leap_slam").damage(3.0f).radius(3.0).extra(120, Scale.DEFENSIVE).amplifier(1).build();
	/** Whirlwind's duration is its own pulse gap, read by the book rather than by a cast. */
	public static final SpellNumbers WHIRLWIND = builder("whirlwind").damage(2.0f).radius(3.0).duration(20, Scale.FIXED).build();
	/** Shield Bash's radius is the wake behind the target its awakened cast staggers. */
	public static final SpellNumbers SHIELD_BASH = builder("shield_bash").damage(2.0f).radius(2.0).duration(40, Scale.DEFENSIVE).build();
	/** Charge's radius is the width of the path it hits along. */
	public static final SpellNumbers CHARGE = builder("charge").damage(2.0f).radius(1.5).duration(40, Scale.DEFENSIVE).build();
	public static final SpellNumbers EXECUTE = builder("execute").damage(6.0f).build();
	public static final SpellNumbers WAR_CRY = builder("war_cry").radius(8.0).duration(200, Scale.DEFENSIVE).amplifier(0).build();
	/** Counter Stance's extra is how long its answer staggers the attacker. */
	public static final SpellNumbers COUNTER_STANCE = builder("counter_stance").damage(4.0f).duration(30, Scale.FIXED).extra(40, Scale.DEFENSIVE).build();
	public static final SpellNumbers IRON_SKIN = builder("iron_skin").duration(BUFF_TICKS, Scale.FIXED).amplifier(0).build();
	/** Earthsplitter's radius is the length of the broken ground; its width is {@code EARTHSPLITTER_WIDTH}. */
	public static final SpellNumbers EARTHSPLITTER = builder("earthsplitter").damage(3.0f).radius(8.0).build();

	// ---- archer books: a ray reads its reach from the radius, and its damage once. ----
	/** The amplifier that stops a body where it stands, shared by every root the mod applies. */
	public static final int SLOWNESS_ROOT_AMPLIFIER = 6;
	/** Grappling Hook's radius is how far its arrow may fly; Multishot's old fan number is gone with the fan. */
	public static final SpellNumbers MULTISHOT = builder("multishot").damage(2.0f).radius(ArcherSkills.RAPID_FIRE_RANGE).build();
	public static final SpellNumbers PIERCING_SHOT = builder("piercing_shot").damage(4.0f).radius(32.0).build();
	/** Arrow Rain's radius is its ground; its extra is the splash an Explosive Arrow leaves. */
	public static final SpellNumbers ARROW_RAIN = builder("arrow_rain").damage(1.5f).radius(ArcherSkills.ARROW_RAIN_RADIUS).build();
	/** Double Strafe's damage is per hit; it lands twice. */
	public static final SpellNumbers RAPID_FIRE = builder("rapid_fire").damage(2.0f).radius(ArcherSkills.RAPID_FIRE_RANGE).build();
	/** Blitz Beat's damage is per falcon strike. */
	public static final SpellNumbers HUNTERS_MARK = builder("hunters_mark").damage(1.5f).build();
	/** Ankle Snare: the duration is the root, the extra is how long the trap waits, and the radius is its reach. */
	public static final SpellNumbers ANKLE_SNARE = builder("ankle_snare").radius(ArcherSkills.ANKLE_SNARE_RADIUS)
		.duration(ArcherSkills.ANKLE_SNARE_ROOT_TICKS, Scale.OFFENSIVE).extra(ArcherSkills.ANKLE_SNARE_LIFE_TICKS, Scale.FIXED)
		.amplifier(SLOWNESS_ROOT_AMPLIFIER).control().build();
	/** Arrow Repel's damage is its one heavy shot. */
	public static final SpellNumbers ARROW_REPEL = builder("arrow_repel").damage(3.0f).radius(ArcherSkills.RAPID_FIRE_RANGE).build();
	/** The next-shot books: the duration is how long the offer stands, and the extra is what the shot leaves. */
	public static final SpellNumbers AIMED_BOLT = builder("aimed_bolt").damage(3.0f)
		.duration(ArcherSkills.SHOT_OFFER_TICKS, Scale.FIXED).extra(40, Scale.FIXED)
		.extraAmplifier(SLOWNESS_ROOT_AMPLIFIER).build();
	public static final SpellNumbers TRIANGLE_SHOT = builder("triangle_shot")
		.duration(ArcherSkills.TRIANGLE_SHOT_OFFER_TICKS, Scale.FIXED).build();
	/** Explosive Arrow's extra timing carries its splash damage, the one number left after damage and radius. */
	public static final SpellNumbers EXPLOSIVE_ARROW = builder("explosive_arrow").damage(3.0f)
		.radius(ArcherSkills.EXPLOSIVE_SPLASH_RADIUS).extra(2, Scale.FIXED)
		.duration(ArcherSkills.SHOT_OFFER_TICKS, Scale.FIXED).build();
	/** Disengage's extra is how long its slow lasts; the radius is the ground it leaves behind. */
	public static final SpellNumbers DISENGAGE = builder("disengage").duration(ArcherSkills.WIND_WALK_TICKS, Scale.FIXED).build();
	/** Snare Trap's own number is the half hour of aim it grants, and its amplifier the awakened mark. */
	public static final SpellNumbers SNARE_TRAP = builder("snare_trap")
		.duration(ArcherSkills.CONCENTRATE_TICKS, Scale.FIXED).amplifier(0).build();
	public static final SpellNumbers EAGLE_EYE = builder("eagle_eye").duration(ArcherSkills.EAGLE_EYE_TICKS, Scale.FIXED).build();
	/** Elemental Quiver's own number is the half hour it lasts, and its amplifier the awakened mark. */
	public static final SpellNumbers ELEMENTAL_QUIVER = builder("elemental_quiver")
		.duration(ArcherSkills.SPECTRAL_QUIVER_TICKS, Scale.FIXED).amplifier(0).build();

	// The priest books: the heals and the holy hits read VIT through RpgBridge.skillHeal.
	public static final SpellNumbers HOLY_LIGHT = builder("holy_light").heal(PriestSkills.HOLY_LIGHT_HEAL).build();
	public static final SpellNumbers PRAYER_OF_MENDING = builder("prayer_of_mending").heal(PriestSkills.PRAYER_HEAL)
		.extra(PriestSkills.PRAYER_AWAKENED_JUMPS, Scale.FIXED).targets(PriestSkills.PRAYER_JUMPS).build();
	public static final SpellNumbers RENEW = builder("renew").heal(PriestSkills.RENEW_HEAL)
		.duration(PriestSkills.RENEW_TICKS, Scale.DEFENSIVE).extra(PriestSkills.RENEW_PERIOD_TICKS, Scale.FIXED).build();
	public static final SpellNumbers DIVINE_SHIELD = builder("divine_shield").allyHeal(PriestSkills.DIVINE_SHIELD_WARD)
		.duration(PriestSkills.DIVINE_SHIELD_TICKS, Scale.FIXED).radius(PriestSkills.DIVINE_SHIELD_AWAKENED_RADIUS).build();
	public static final SpellNumbers HOLY_ARMS = builder("holy_arms").damage(PriestSkills.HOLY_ARMS_BONUS)
		.duration(PriestSkills.HOLY_ARMS_TICKS, Scale.FIXED).radius(PriestSkills.HOLY_ARMS_RADIUS).build();
	public static final SpellNumbers BLESSING_OF_FORTITUDE = builder("blessing_of_fortitude")
		.duration(PriestSkills.FORTITUDE_TICKS, Scale.FIXED).radius(PriestSkills.FORTITUDE_RADIUS).build();
	public static final SpellNumbers SMITE = builder("smite").damage(PriestSkills.SMITE_DAMAGE)
		.radius(PriestSkills.SMITE_CHAIN_RADIUS).targets(PriestSkills.SMITE_CHAIN_TARGETS).build();
	public static final SpellNumbers RESURRECTION_WARD = builder("resurrection_ward")
		.duration(PriestSkills.RESURRECTION_WARD_TICKS, Scale.FIXED).build();
	public static final SpellNumbers CONSECRATE = builder("consecrate").heal(PriestSkills.CONSECRATE_HEAL)
		.damage(PriestSkills.CONSECRATE_DAMAGE).radius(PriestSkills.CONSECRATE_RADIUS)
		.duration(PriestSkills.CONSECRATE_TICKS, Scale.DEFENSIVE).extra(PriestSkills.CONSECRATE_PERIOD_TICKS, Scale.FIXED).build();
	public static final SpellNumbers GUARDIAN_ANGEL = builder("guardian_angel").allyHeal(PriestSkills.GUARDIAN_SHARE)
		.duration(PriestSkills.GUARDIAN_ANGEL_TICKS, Scale.DEFENSIVE).build();
	/** The vow's own length is the half hour it grants, and no multiplier stretches it. */
	public static final SpellNumbers UNBROKEN_VOW = builder("unbroken_vow")
		.duration(UnbrokenVow.VOW_TICKS, Scale.FIXED).build();
	public static final SpellNumbers STEADFAST_PRAYER = builder("steadfast_prayer")
		.duration(SteadfastPrayer.PRAYER_TICKS, Scale.FIXED).build();

	/** Ticks of this spell's control on the target: half on an elite or a boss. */
	int controlTicks(final LivingEntity target, final int ticks) {
		return this.control ? controlTicksFor(target, ticks) : ticks;
	}

	/** Control of any kind lasts half as long on an elite or a boss. */
	static int controlTicksFor(final LivingEntity target, final int ticks) {
		return target instanceof Mob mob && MobSpells.isElite(mob) ? ticks / 2 : ticks;
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
