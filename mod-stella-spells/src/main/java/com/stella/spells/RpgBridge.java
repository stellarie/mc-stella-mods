package com.stella.spells;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.IntUnaryOperator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * The caster's stats from the RPG mod, reached by name when that mod is installed and a plain 1.0 when it is
 * not. It shortens a player spell's cooldown, and its MATK sets what a damage spell or a heal is worth. Its
 * skill numbers set what a skill book is worth, through the school's own stat. A result of NaN means the RPG
 * mod gave no number, and the caller keeps its own flat one.
 */
public final class RpgBridge {
	/** A vanilla flat damage of 1 is this many percent of MATK, and of a skill's stat for a skill book. */
	public static final double SKILL_PERCENT_PER_FLAT = 50.0;
	/** A vanilla flat heal of 1 is this heal coefficient. */
	public static final double HEAL_COEFFICIENT_PER_FLAT = 0.5;
	public static final String HEALTH_SCALE_SHARE = "mod-stella-rpg:health-scale";
	/** The RPG mod's own player curve, an {@code IntUnaryOperator} from a level to what the next one needs. */
	public static final String XP_CURVE_SHARE = "mod-stella-rpg:xp-curve";
	/** What vanilla charges a player for one level, the fallback when the RPG mod publishes no curve. */
	public static final int VANILLA_LEVEL_XP = 100;
	static final String RPG_SPELLS_CLASS = "com.stella.rpg.RpgSpells";
	static final String RPG_SKILLS_CLASS = "com.stella.rpg.RpgSkills";
	static final String ATTACK_PREVIEW_CLASS = "com.stella.rpg.AttackPreview";
	/** The six stat keys the RPG mod knows, summed for a creature that scales with the whole character. */
	private static final String[] STAT_KEYS = {"str", "agi", "vit", "dex", "int", "luk"};

	/** What the RPG mod gives a spell: the calls its {@code RpgSpells} makes. */
	public interface Stats {
		double power(Player player);

		double cooldown(Player player);

		/** Vanilla-unit damage for a skill percent, 150 being 150%; NaN when the RPG mod gives none. */
		default double spellDamage(final LivingEntity caster, final double skillPercent) {
			return Double.NaN;
		}

		/** Vanilla-unit heal for a coefficient; NaN when the RPG mod gives none. */
		default double spellHeal(final LivingEntity caster, final double coefficient) {
			return Double.NaN;
		}

		/** A skill hit with the school's dice and a crit roll, in vanilla units; NaN when the RPG mod gives none. */
		default double skillDamage(final LivingEntity caster, final String statKey, final double skillPercent) {
			return Double.NaN;
		}

		/** The lowest and highest skill hit without a crit; a NaN pair when the RPG mod gives none. */
		default double[] skillDamageRange(final LivingEntity caster, final String statKey, final double skillPercent) {
			return NONE_RANGE;
		}

		/** Level plus two times VIT times the coefficient, in vanilla units; NaN when the RPG mod gives none. */
		default double skillHeal(final LivingEntity caster, final double coefficient) {
			return Double.NaN;
		}

		/** What the school's stat multiplies a skill's duration by; NaN when the RPG mod gives none. */
		default double skillDurationMultiplier(final LivingEntity caster, final String statKey) {
			return Double.NaN;
		}

		/** The item a skill swings: the weapon slot when it holds equipment, else the main hand; null for none. */
		default @Nullable ItemStack skillWeapon(final LivingEntity caster) {
			return null;
		}

		/** What the caster's Intelligence multiplies every spell effect duration by; 1 when the RPG mod gives none. */
		default double durationMultiplier(final LivingEntity caster) {
			return 1.0;
		}

		/** The caster floor ATK in K units: the low end of the Atk range, ranged or melee; NaN when the RPG mod gives none. */
		default double floorAtk(final LivingEntity caster, final boolean ranged) {
			return Double.NaN;
		}

		/** The lowest and highest Atk roll in K units, ranged or melee; a NaN pair when the RPG mod gives none. */
		default double[] atkRange(final LivingEntity caster, final boolean ranged) {
			return NONE_RANGE;
		}

		/** The sum of the caster six stats, STR AGI VIT DEX INT LUK; NaN when the RPG mod gives none. */
		default double statTotal(final LivingEntity caster) {
			return Double.NaN;
		}

		/** The lowest MATK roll in K units; NaN when the RPG mod gives none. */
		default double matkMin(final LivingEntity caster) {
			return Double.NaN;
		}

		/** The highest MATK roll in K units; NaN when the RPG mod gives none. */
		default double matkMax(final LivingEntity caster) {
			return Double.NaN;
		}
	}

	/** The skill-book numbers of the RPG mod's {@code RpgSkills}; a missing handle answers NaN, meaning "no number". */
	private interface Skills {
		double damage(LivingEntity caster, String statKey, double skillPercent);

		double[] range(LivingEntity caster, String statKey, double skillPercent);

		double heal(LivingEntity caster, double coefficient);

		double duration(LivingEntity caster, String statKey);

		@Nullable ItemStack weapon(LivingEntity caster);

		@Nullable DamageSource source(LivingEntity caster);

		/** The low end of the Atk range, ranged from DEX or melee from STR, in K units; NaN when unknown. */
		double floorAtk(LivingEntity caster, boolean ranged);

		/** The low and the high end of the Atk range, ranged from DEX or melee from STR, in K units; NaNs when unknown. */
		double[] atkRange(LivingEntity caster, boolean ranged);

		/** The sum of the six stats; NaN when unknown. */
		double statTotal(LivingEntity caster);
	}

	/** A bridge with no skill numbers: every question answers NaN, so each caller keeps its own flat number. */
	private static final Skills NO_SKILLS = new Skills() {
		@Override
		public double damage(final LivingEntity caster, final String statKey, final double skillPercent) {
			return Double.NaN;
		}

		@Override
		public double[] range(final LivingEntity caster, final String statKey, final double skillPercent) {
			return NONE_RANGE;
		}

		@Override
		public double heal(final LivingEntity caster, final double coefficient) {
			return Double.NaN;
		}

		@Override
		public double duration(final LivingEntity caster, final String statKey) {
			return Double.NaN;
		}

		@Override
		public ItemStack weapon(final LivingEntity caster) {
			return null;
		}

		@Override
		public DamageSource source(final LivingEntity caster) {
			return null;
		}

		@Override
		public double floorAtk(final LivingEntity caster, final boolean ranged) {
			return Double.NaN;
		}

		@Override
		public double[] atkRange(final LivingEntity caster, final boolean ranged) {
			return NONE_RANGE;
		}

		@Override
		public double statTotal(final LivingEntity caster) {
			return Double.NaN;
		}
	};

	/** The RPG mod, its spell numbers and its skill numbers together; the bridge is present only when both bind. */
	private record Bound(Stats spells, Skills skills) implements Stats {
		@Override
		public double power(final Player player) {
			return this.spells.power(player);
		}

		@Override
		public double cooldown(final Player player) {
			return this.spells.cooldown(player);
		}

		@Override
		public double spellDamage(final LivingEntity caster, final double skillPercent) {
			return this.spells.spellDamage(caster, skillPercent);
		}

		@Override
		public double spellHeal(final LivingEntity caster, final double coefficient) {
			return this.spells.spellHeal(caster, coefficient);
		}

		@Override
		public double skillDamage(final LivingEntity caster, final String statKey, final double skillPercent) {
			return this.skills.damage(caster, statKey, skillPercent);
		}

		@Override
		public double[] skillDamageRange(final LivingEntity caster, final String statKey, final double skillPercent) {
			return this.skills.range(caster, statKey, skillPercent);
		}

		@Override
		public double skillHeal(final LivingEntity caster, final double coefficient) {
			return this.skills.heal(caster, coefficient);
		}

		@Override
		public double skillDurationMultiplier(final LivingEntity caster, final String statKey) {
			return this.skills.duration(caster, statKey);
		}

		@Override
		public ItemStack skillWeapon(final LivingEntity caster) {
			return this.skills.weapon(caster);
		}

		@Override
		public double durationMultiplier(final LivingEntity caster) {
			return this.spells.durationMultiplier(caster);
		}

		@Override
		public double floorAtk(final LivingEntity caster, final boolean ranged) {
			return this.skills.floorAtk(caster, ranged);
		}

		@Override
		public double[] atkRange(final LivingEntity caster, final boolean ranged) {
			return this.skills.atkRange(caster, ranged);
		}

		@Override
		public double statTotal(final LivingEntity caster) {
			return this.skills.statTotal(caster);
		}

		@Override
		public double matkMin(final LivingEntity caster) {
			return this.spells.matkMin(caster);
		}

		@Override
		public double matkMax(final LivingEntity caster) {
			return this.spells.matkMax(caster);
		}
	}

	/** The empty range a bridge without the RPG mod's skill numbers answers with. */
	private static final double[] NONE_RANGE = {Double.NaN, Double.NaN};

	/** Where a hotbar keeps the ready weapon; the RPG mod's own weapon slot, read without depending on that mod. */
	private static final String WEAPON_SLOT_CLASS = "com.stella.rpg.WeaponSlot";
	private static final int FALLBACK_WEAPON_SLOT = 35;

	private static final Stats NONE = new Stats() {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}
	};

	private static volatile Stats stats;

	private RpgBridge() {
	}

	/** The test seam: a stand-in for the RPG mod; null restores the lookup. */
	static void setStats(final Stats stub) {
		stats = stub;
	}

	private static Stats stats() {
		Stats found = stats;
		if (found == null) {
			found = bind();
			stats = found;
		}
		return found;
	}

	static Stats find() {
		return find(RPG_SPELLS_CLASS);
	}

	/** The bridge bound to a stand-in of the RPG mod's spell shape, for a harness that lacks the mod. */
	static Stats find(final String className) {
		try {
			Class<?> rpg = Class.forName(className, true, RpgBridge.class.getClassLoader());
			return new Bound(bindSpells(rpg), NO_SKILLS);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	/** The bridge bound to stand-ins of the RPG skill and attack-preview classes, for a harness that lacks the mod. */
	static Stats findAttack(final String skillsClass, final String previewClass) {
		try {
			ClassLoader loader = RpgBridge.class.getClassLoader();
			return new Bound(NONE, bindSkills(Class.forName(skillsClass, true, loader), Class.forName(previewClass, true, loader)));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	/** The sum of the caster six stats, or NaN when the RPG mod gives none. */
	static double statTotal(final LivingEntity caster) {
		return stats().statTotal(caster);
	}

	/** The caster floor ATK in K units, or NaN when the RPG mod gives none. */
	static double floorAtk(final LivingEntity caster, final boolean ranged) {
		return stats().floorAtk(caster, ranged);
	}

	/** The average of the lowest and the highest Atk roll of the caster in K units, or NaN when the RPG mod gives none. */
	static double averageAtk(final LivingEntity caster, final boolean ranged) {
		double[] range = stats().atkRange(caster, ranged);
		return range == null || range.length < 2 || !Double.isFinite(range[0]) || !Double.isFinite(range[1])
			? Double.NaN : (range[0] + range[1]) / 2.0;
	}

	/** The bridge bound to the RPG mod itself: its spell numbers and its skill numbers, both by name. */
	private static Stats bind() {
		try {
			Class<?> spells = Class.forName(RPG_SPELLS_CLASS, true, RpgBridge.class.getClassLoader());
			Class<?> skills = Class.forName(RPG_SKILLS_CLASS, true, RpgBridge.class.getClassLoader());
			return new Bound(bindSpells(spells), bindSkills(skills, attackPreview()));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	// The RPG attack preview, or null for an older RPG mod that lacks it.
	private static @Nullable Class<?> attackPreview() {
		try {
			return Class.forName(ATTACK_PREVIEW_CLASS, true, RpgBridge.class.getClassLoader());
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	/** {@code RpgSpells}: power and cooldown are required, the rest are read when the class has them. */
	private static Stats bindSpells(final Class<?> rpg) throws ReflectiveOperationException {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		MethodHandle power = lookup.findStatic(rpg, "power", MethodType.methodType(double.class, Player.class));
		MethodHandle cooldown = lookup.findStatic(rpg, "cooldown", MethodType.methodType(double.class, Player.class));
		MethodHandle damage = spellDamageHandle(lookup, rpg);
		MethodHandle heal = spellHealHandle(lookup, rpg);
		MethodHandle matkMin = matkHandle(lookup, rpg, "matkMin");
		MethodHandle matkMax = matkHandle(lookup, rpg, "matkMax");
		MethodHandle duration = matkHandle(lookup, rpg, "durationMultiplier");
		return new Stats() {
			@Override
			public double power(final Player player) {
				try {
					return (double) power.invokeExact(player);
				} catch (Throwable e) {
					return 1.0;
				}
			}

			@Override
			public double cooldown(final Player player) {
				try {
					return (double) cooldown.invokeExact(player);
				} catch (Throwable e) {
					return 1.0;
				}
			}

			@Override
			public double spellDamage(final LivingEntity caster, final double skillPercent) {
				try {
					return damage == null ? Double.NaN : (double) damage.invokeExact(caster, skillPercent);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double spellHeal(final LivingEntity caster, final double coefficient) {
				try {
					return heal == null ? Double.NaN : (double) heal.invokeExact(caster, coefficient);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double durationMultiplier(final LivingEntity caster) {
				try {
					return duration == null ? 1.0 : (double) duration.invokeExact(caster);
				} catch (Throwable e) {
					return 1.0;
				}
			}

			@Override
			public double matkMin(final LivingEntity caster) {
				try {
					return matkMin == null ? Double.NaN : (double) matkMin.invokeExact(caster);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double matkMax(final LivingEntity caster) {
				try {
					return matkMax == null ? Double.NaN : (double) matkMax.invokeExact(caster);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}
		};
	}

	/** {@code RpgSkills}: every handle is read on its own, so an older RPG mod still binds for what it has. */
	private static Skills bindSkills(final Class<?> skills, final @Nullable Class<?> preview) {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		MethodHandle stat = skillStatHandle(lookup, skills, "stat");
		MethodHandle melee = preview == null ? null : staticHandle(lookup, preview, "range", double[].class, LivingEntity.class, int.class);
		MethodHandle ranged = preview == null ? null : staticHandle(lookup, preview, "ranged", double[].class, LivingEntity.class, int.class);
		MethodHandle damage = skillDamageHandle(lookup, skills, "physicalDamage");
		MethodHandle range = skillRangeHandle(lookup, skills, "physicalRange");
		MethodHandle heal = skillHealHandle(lookup, skills, "skillHeal");
		MethodHandle duration = skillStatHandle(lookup, skills, "durationMultiplier");
		MethodHandle weapon = skillWeaponHandle(lookup, skills, "weapon");
		MethodHandle source = skillSourceHandle(lookup, skills, "skillSource");
		return new Skills() {
			@Override
			public double damage(final LivingEntity caster, final String statKey, final double skillPercent) {
				try {
					return damage == null ? Double.NaN : (double) damage.invokeExact(caster, statKey, skillPercent);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double[] range(final LivingEntity caster, final String statKey, final double skillPercent) {
				try {
					return range == null ? NONE_RANGE : (double[]) range.invokeExact(caster, statKey, skillPercent);
				} catch (Throwable e) {
					return NONE_RANGE;
				}
			}

			@Override
			public double heal(final LivingEntity caster, final double coefficient) {
				try {
					return heal == null ? Double.NaN : (double) heal.invokeExact(caster, coefficient);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double duration(final LivingEntity caster, final String statKey) {
				try {
					return duration == null ? Double.NaN : (double) duration.invokeExact(caster, statKey);
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public ItemStack weapon(final LivingEntity caster) {
				try {
					return weapon == null ? null : (ItemStack) weapon.invokeExact(caster);
				} catch (Throwable e) {
					return null;
				}
			}

			@Override
			public DamageSource source(final LivingEntity caster) {
				try {
					return source == null ? null : (DamageSource) source.invokeExact(caster);
				} catch (Throwable e) {
					return null;
				}
			}

			@Override
			public double statTotal(final LivingEntity caster) {
				if (stat == null) {
					return Double.NaN;
				}
				try {
					double total = 0.0;
					for (String key : STAT_KEYS) {
						total += (double) stat.invokeExact(caster, key);
					}
					return total;
				} catch (Throwable e) {
					return Double.NaN;
				}
			}

			@Override
			public double floorAtk(final LivingEntity caster, final boolean farShot) {
				double[] range = this.atkRange(caster, farShot);
				return range[0];
			}

			@Override
			public double[] atkRange(final LivingEntity caster, final boolean farShot) {
				MethodHandle preview = farShot ? ranged : melee;
				if (stat == null || preview == null) {
					return NONE_RANGE;
				}
				try {
					String key = farShot ? School.ARCHER.statKey() : School.FIGHTER.statKey();
					int points = (int) Math.round((double) stat.invokeExact(caster, key));
					double[] range = (double[]) preview.invokeExact(caster, points);
					return range == null || range.length < 2 ? NONE_RANGE : range;
				} catch (Throwable e) {
					return NONE_RANGE;
				}
			}
		};
	}

	// An older RPG mod may lack any of these: null is fine.
	private static MethodHandle spellDamageHandle(final MethodHandles.Lookup lookup, final Class<?> owner) {
		return staticHandle(lookup, owner, "spellDamage", double.class, LivingEntity.class, double.class);
	}

	private static MethodHandle spellHealHandle(final MethodHandles.Lookup lookup, final Class<?> owner) {
		return staticHandle(lookup, owner, "spellHeal", double.class, LivingEntity.class, double.class);
	}

	private static MethodHandle matkHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, double.class, LivingEntity.class);
	}

	private static MethodHandle skillDamageHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, double.class, LivingEntity.class, String.class, double.class);
	}

	private static MethodHandle skillRangeHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, double[].class, LivingEntity.class, String.class, double.class);
	}

	private static MethodHandle skillHealHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, double.class, LivingEntity.class, double.class);
	}

	private static MethodHandle skillStatHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, double.class, LivingEntity.class, String.class);
	}

	private static MethodHandle skillWeaponHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, ItemStack.class, LivingEntity.class);
	}

	private static MethodHandle skillSourceHandle(final MethodHandles.Lookup lookup, final Class<?> owner, final String name) {
		return staticHandle(lookup, owner, name, DamageSource.class, LivingEntity.class);
	}

	private static MethodHandle staticHandle(
		final MethodHandles.Lookup lookup, final Class<?> owner, final String name,
		final Class<?> returns, final Class<?>... arguments
	) {
		try {
			return lookup.findStatic(owner, name, MethodType.methodType(returns, arguments));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Whether the RPG mod, or a stand-in for it, is answering. */
	static boolean present() {
		return stats() != NONE;
	}

	static SpellModifiers.CastModifiers grow(final Player caster, final SpellModifiers.CastModifiers base) {
		return grow(caster, "", base);
	}

	/** A mage cast's modifiers once the caster's Intelligence has had its say: power by its multiplier, cooldown by its own. */
	static SpellModifiers.CastModifiers grow(final Player caster, final String spellId, final SpellModifiers.CastModifiers base) {
		return grow(caster, spellId, base, School.MAGE);
	}

	/**
	 * A cast's modifiers once the caster's stats have had their say. Every book, skill or spell, rests by the one
	 * cooldown multiplier of Intelligence plus Dexterity, applied to the cooldown the book refine cuts left. A mage
	 * book reads Intelligence for its duration; a skill book reads its own school's stat.
	 */
	static SpellModifiers.CastModifiers grow(
		final Player caster, final String spellId, final SpellModifiers.CastModifiers base, final School school
	) {
		return StaffPower.grow(caster, spellId, growStats(caster, spellId, base, school));
	}

	private static SpellModifiers.CastModifiers growStats(
		final Player caster, final String spellId, final SpellModifiers.CastModifiers base, final School school
	) {
		Stats rpg = stats();
		double power = rpg.power(caster);
		// Fixed-cooldown spells keep their base cooldown.
		double cooldown = SpellbookCatalog.FIXED_COOLDOWN.contains(spellId) ? 1.0 : rpg.cooldown(caster);
		double duration = durationMultiplier(caster, rpg, school);
		if (!positive(power) || !positive(cooldown) || (power == 1.0 && cooldown == 1.0 && duration == 1.0)) {
			return base;
		}
		// Durations only: the cooldown keeps its own multiplier.
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * power, base.radiusMultiplier(), base.durationMultiplier() * duration,
			base.defensiveDurationMultiplier() * duration, base.targetRangeBlocks(), Math.max(20, (int) Math.round(base.cooldownTicks() * cooldown)),
			base.awakened(), base.refineLevel()
		);
	}

	private static double durationMultiplier(final Player caster, final Stats rpg, final School school) {
		if (school == School.MAGE) {
			return rpg.durationMultiplier(caster);
		}
		double skill = rpg.skillDurationMultiplier(caster, school.statKey());
		return positive(skill) ? skill : 1.0;
	}

	/** The caster's duration multiplier from the RPG mod; 1 when it gives none or a number that is not usable. */
	static double durationMultiplier(final LivingEntity caster) {
		double multiplier = stats().durationMultiplier(caster);
		return positive(multiplier) ? multiplier : 1.0;
	}

	/** The lowest MATK roll of the caster in K units, or NaN when the RPG mod gives none. */
	static double matkMin(final LivingEntity caster) {
		return stats().matkMin(caster);
	}

	/** The highest MATK roll of the caster in K units, or NaN when the RPG mod gives none. */
	static double matkMax(final LivingEntity caster) {
		return stats().matkMax(caster);
	}

	/** The RPG health scale K, read on each call so a late share counts; 1 when the RPG mod is absent. */
	static double k() {
		Object shared = FabricLoader.getInstance().getObjectShare().get(HEALTH_SCALE_SHARE);
		return shared instanceof Double scale && positive(scale) ? scale : 1.0;
	}

	/** What vanilla charges a player of this level for the next one. */
	public static int vanillaXpNeededFor(final int level) {
		return VANILLA_LEVEL_XP * (level + 1);
	}

	/**
	 * The experience a player of this level needs for the next one: the RPG mod's own curve when it
	 * publishes one, vanilla's hundred a level otherwise. Read on each call, so a config reload counts.
	 */
	public static int xpNeededFor(final int level) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(XP_CURVE_SHARE);
		if (shared instanceof IntUnaryOperator curve) {
			int needed = curve.applyAsInt(Math.max(0, level));
			if (needed > 0) {
				return needed;
			}
		}
		return vanillaXpNeededFor(level);
	}

	/** Real health points as the vanilla number that {@code heal} or {@code hurtServer} scales back by K. */
	static float vanilla(final float realHealth) {
		return (float) (realHealth / k());
	}

	/** The RPG mod's own damage for a skill percent, or NaN when it gives none. */
	static double spellDamage(final LivingEntity caster, final double skillPercent) {
		return stats().spellDamage(caster, skillPercent);
	}

	/** A damage spell's hit: the caster's MATK at the flat number x 50 percent, else the flat number; times the cast's power once. */
	static float damage(final LivingEntity caster, final float flat, final double power) {
		return (float) (usable(stats().spellDamage(caster, flat * SKILL_PERCENT_PER_FLAT), flat) * power);
	}

	/** A heal: the caster's level and Intelligence at half the flat number, else the flat number; times the cast's power once. */
	static float heal(final LivingEntity caster, final float flat, final double power) {
		return (float) (usable(stats().spellHeal(caster, flat * HEAL_COEFFICIENT_PER_FLAT), flat) * power);
	}

	/**
	 * A skill hit: the caster's own school stat at the flat number x 50 percent, else the flat number; times the
	 * cast's power once. A mage spell keeps the MATK path, so a fighter book never reads Intelligence.
	 */
	static float skillDamage(final LivingEntity caster, final School school, final float flat, final double power) {
		if (school == School.MAGE) {
			return damage(caster, flat, power);
		}
		double fromRpg = stats().skillDamage(caster, school.statKey(), flat * SKILL_PERCENT_PER_FLAT);
		return (float) (usable(fromRpg, flat) * power);
	}

	/** The lowest and highest skill hit for a tooltip, in vanilla units; the flat number times power when the bridge gives none. */
	static double[] skillDamageRange(final LivingEntity caster, final School school, final float flat, final double power) {
		if (flat <= 0.0F) {
			return new double[] {0.0, 0.0};
		}
		if (school == School.MAGE) {
			return new double[] {damage(caster, flat, power), damage(caster, flat, power)};
		}
		double[] rolls = stats().skillDamageRange(caster, school.statKey(), flat * SKILL_PERCENT_PER_FLAT);
		if (rolls == null || rolls.length < 2 || !Double.isFinite(rolls[0]) || !Double.isFinite(rolls[1])) {
			double flatHit = flat * power;
			return new double[] {flatHit, flatHit};
		}
		return new double[] {rolls[0] * power, rolls[1] * power};
	}

	/** A skill heal: the caster's level and VIT, else the flat number; times the cast's power once. */
	static float skillHeal(final LivingEntity caster, final School school, final float flat, final double power) {
		if (school == School.MAGE) {
			return heal(caster, flat, power);
		}
		double fromRpg = stats().skillHeal(caster, flat * HEAL_COEFFICIENT_PER_FLAT);
		return (float) (usable(fromRpg, flat) * power);
	}

	/** What a skill swings: the weapon slot when it holds equipment, else the main hand; empty for none. */
	static ItemStack skillWeapon(final LivingEntity caster) {
		ItemStack fromRpg = stats().skillWeapon(caster);
		return fromRpg == null ? fallbackWeapon(caster) : fromRpg;
	}

	// Without the RPG mod there is no weapon slot to read, so the main hand answers, or the hotbar's last slot.
	private static ItemStack fallbackWeapon(final LivingEntity caster) {
		ItemStack held = caster.getMainHandItem();
		if (equipment(held)) {
			return held;
		}
		if (!(caster instanceof Player player)) {
			return ItemStack.EMPTY;
		}
		ItemStack slotted = player.getInventory().getItem(weaponSlot());
		return equipment(slotted) ? slotted : ItemStack.EMPTY;
	}

	// A book or a scroll is held to be cast, never swung: the RPG mod's own reading of equipment, mirrored.
	private static boolean equipment(final ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof SpellbookItem || stack.getItem() instanceof SpellScrollItem) {
			return false;
		}
		if (stack.has(DataComponents.WEAPON) || stack.getItem() instanceof ProjectileWeaponItem) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.modifiers().stream()
			.anyMatch(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND));
	}

	/** The RPG mod's weapon slot when that mod is installed, and its documented index when it is not. */
	private static int weaponSlot() {
		try {
			Class<?> slot = Class.forName(WEAPON_SLOT_CLASS, true, RpgBridge.class.getClassLoader());
			return slot.getField("SLOT").getInt(null);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return FALLBACK_WEAPON_SLOT;
		}
	}

	/** The skill source last handed out on this thread, so a hit it carried is told from a blow of the caster own. */
	private static final ThreadLocal<java.lang.ref.WeakReference<DamageSource>> LAST_SKILL_SOURCE = new ThreadLocal<>();

	/** A skill's damage source, from the RPG mod when it publishes one and a plain player attack otherwise. */
	static DamageSource skillSource(final LivingEntity caster) {
		DamageSource fromRpg = stats() instanceof Bound bound ? bound.skills().source(caster) : null;
		DamageSource source = fromRpg != null ? fromRpg : caster instanceof Player
			? caster.level().damageSources().playerAttack((Player) caster)
			: caster.level().damageSources().mobAttack(caster);
		LAST_SKILL_SOURCE.set(new java.lang.ref.WeakReference<>(source));
		return source;
	}

	/** True when this damage source is the one a skill hit has just been given. */
	static boolean isSkillSource(final DamageSource source) {
		java.lang.ref.WeakReference<DamageSource> last = LAST_SKILL_SOURCE.get();
		return last != null && last.get() == source;
	}

	private static double usable(final double fromRpg, final double flat) {
		return Double.isFinite(fromRpg) && fromRpg >= 0.0 ? fromRpg : flat;
	}

	private static boolean positive(final double value) {
		return value > 0.0 && Double.isFinite(value);
	}
}
