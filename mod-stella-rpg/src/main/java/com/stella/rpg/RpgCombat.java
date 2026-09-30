package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

public final class RpgCombat {
	@FunctionalInterface
	public interface Roller {
		boolean roll(LivingEntity attacker, RandomSource random, double chance);
	}

	@FunctionalInterface
	public interface DiceRoller {
		int roll(LivingEntity attacker, RandomSource random, RpgDice.Dice dice);
	}

	private static final int STATUS_STEP = 10;
	private static final double EFFECT_PERCENT = 0.10;
	private static final double MOB_SPELL_PERCENT = 100.0;
	private static final double MOB_SPELL_BASE = 3.0;
	private static final DiceRoller FAIR_DICE = RpgCombat::fairRoll;
	/** The victim of a critical hit while its hurt call runs, so scaleIncoming can apply LUK. */
	private static final ThreadLocal<LivingEntity> CRIT_VICTIM = new ThreadLocal<>();
	private static volatile DiceRoller diceRoller = FAIR_DICE;
	private static Player swinger;
	private static float swingStrength = 1.0F;

	private static final Roller FAIR = (attacker, random, chance) -> chance > 0.0 && random.nextDouble() < chance;
	private static volatile Roller roller = FAIR;

	private RpgCombat() {
	}

	/** A fair roll where each die is a maximum with the chance DEX gives. */
	static int fairRoll(final LivingEntity attacker, final RandomSource random, final RpgDice.Dice dice) {
		return dice.roll(random, RpgCurve.dexMaxRollChance(StatBonuses.effective(attacker, Stat.DEX)));
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static void setDiceRoller(final DiceRoller newRoller) {
		diceRoller = newRoller == null ? FAIR_DICE : newRoller;
	}

	public static void beginAttack(final Player player, final float strength) {
		swinger = player;
		swingStrength = strength;
	}

	public static void endAttack(final Player player) {
		if (swinger == player) {
			swinger = null;
			swingStrength = 1.0F;
		}
	}

	public static double chargeFactor(final float strength) {
		return 0.2 + strength * strength * 0.8;
	}

	public static float scaleOutgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(source.getEntity() instanceof LivingEntity attacker)
			|| !(attacker instanceof ServerPlayer || attacker instanceof Mob)) {
			return amount;
		}
		if (CRIT_VICTIM.get() == victim) {
			CRIT_VICTIM.remove();
		}
		double scaled = amount;
		Entity direct = source.getDirectEntity();
		if (isMelee(source)) {
			double charge = attacker instanceof Player player && swinger == player ? chargeFactor(swingStrength) : 1.0;
			scaled += dice(attacker, attacker.getMainHandItem(), Stat.STR, false) * charge
				+ statusAtk(StatBonuses.effective(attacker, Stat.STR));
		} else if (source.is(DamageTypeTags.IS_PROJECTILE) && direct instanceof Projectile projectile
			&& projectile.getOwner() == attacker) {
			ItemStack weapon = source.getWeaponItem() != null ? source.getWeaponItem() : attacker.getMainHandItem();
			scaled += dice(attacker, weapon, Stat.DEX, true) + statusAtk(StatBonuses.effective(attacker, Stat.DEX));
		} else if (attacker instanceof Mob && source.is(DamageTypes.INDIRECT_MAGIC)) {
			return (float) RpgSpells.spellDamage(attacker, MOB_SPELL_PERCENT * amount / MOB_SPELL_BASE);
		} else {
			return amount;
		}
		scaled += Affixes.total(attacker, Affix.DAMAGE_FLAT) / RpgScale.k();
		scaled *= damageFactor(attacker);
		double chance = RpgCurve.critChance(StatBonuses.effective(attacker, Stat.LUK));
		if (roller.roll(attacker, attacker.getRandom(), chance)) {
			scaled *= RpgCurve.critMultiplier();
			CRIT_VICTIM.set(victim);
		}
		return (float) scaled;
	}

	/** Ends the critical hit of a victim. The hurt call of that victim runs this when it returns. */
	public static void endHurt(final LivingEntity victim) {
		if (CRIT_VICTIM.get() == victim) {
			CRIT_VICTIM.remove();
		}
	}

	static boolean critPending(final LivingEntity victim) {
		return CRIT_VICTIM.get() == victim;
	}

	/** Ragnarok status ATK, in vanilla units: the stat plus the square of a tenth of it, divided by K. */
	public static double statusAtk(final int stat) {
		int step = stat / STATUS_STEP;
		return (stat + step * step) / RpgScale.k();
	}

	/** Damage affixes and Strength add. Weakness subtracts. The result is never below 0. */
	public static double damageFactor(final LivingEntity attacker) {
		double percent = Affixes.total(attacker, Affix.DAMAGE_PCT) + EFFECT_PERCENT * (level(attacker, MobEffects.STRENGTH)
			- level(attacker, MobEffects.WEAKNESS));
		return Math.max(0.0, 1.0 + percent);
	}

	private static int level(final LivingEntity entity, final Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? 0 : instance.getAmplifier() + 1;
	}

	/** Magic, indirect magic, and the magic tag. The poison tick has its own type and is not in them. */
	static boolean isMagic(final DamageSource source) {
		return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(RpgDamageTypes.MAGIC);
	}

	private static boolean isMelee(final DamageSource source) {
		return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
			|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	/**
	 * The STR or DEX dice plus the weapon dice, times the dice multiplier. Mobs keep a share of the result.
	 * The weapon is the main hand for a melee hit and the projectile's weapon for a shot.
	 */
	private static double dice(final LivingEntity attacker, final ItemStack weapon, final Stat stat, final boolean ranged) {
		int rolled = diceRoller.roll(attacker, attacker.getRandom(), RpgDice.of(StatBonuses.effective(attacker, stat)));
		RpgDice.Dice weaponDice = PhysicalDice.weaponDice(weapon, ranged);
		if (weaponDice != null) {
			rolled += diceRoller.roll(attacker, attacker.getRandom(), weaponDice);
		}
		double total = rolled * PhysicalDice.multiplier(attacker, weapon);
		return attacker instanceof Mob ? total * RpgConfigStore.current().mobDiceShare() : total;
	}

	public static float scaleIncoming(final LivingEntity entity, final DamageSource source, final float damage) {
		if (!(entity instanceof ServerPlayer || entity instanceof Mob)
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		double reduced = damage * RpgCurve.damageTakenMultiplier(StatBonuses.effective(entity, Stat.VIT));
		if (isMagic(source)) {
			reduced *= RpgCurve.magicDamageTakenMultiplier(StatBonuses.effective(entity, Stat.INT));
		}
		if (critPending(entity)) {
			reduced *= RpgCurve.critDamageTakenMultiplier(StatBonuses.effective(entity, Stat.LUK));
		}
		reduced *= 1.0 - Math.min(1.0, Affixes.total(entity, Affix.REDUCTION_PCT));
		double flat = DefenseBonuses.flat(entity) + Affixes.total(entity, Affix.REDUCTION_FLAT) / RpgScale.k();
		return (float) Math.max(0.0, reduced - flat);
	}
}
