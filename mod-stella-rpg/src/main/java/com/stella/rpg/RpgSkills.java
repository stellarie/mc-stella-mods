package com.stella.rpg;

import com.stella.rpg.job.JobItems;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Skill book numbers other mods read. {@code mod-stella-spells} finds this class by name, so
 * the class name and every public method signature are a public contract.
 * Every result is in vanilla units, so the health scale multiplies it once.
 */
public final class RpgSkills {
	private static final double PERCENT = 100.0;
	private static final double ARROW_BASE = 2.0;
	private static final double ARROW_PER_POWER = 0.5;
	private static final double HEAL_PER_VIT = 2.0;

	private RpgSkills() {
	}

	/** The effective value of the stat with this key. 0 for an unknown key. */
	public static double stat(final LivingEntity entity, final String key) {
		return Stat.byKey(key).map(stat -> (double) StatBonuses.effective(entity, stat)).orElse(0.0);
	}

	/** The item in the weapon slot when it is equipment, else the main hand when it is equipment, else empty. */
	public static ItemStack weapon(final LivingEntity entity) {
		if (entity instanceof Player player) {
			ItemStack slotted = player.getInventory().getItem(WeaponSlot.SLOT);
			if (WeaponSlot.isEquipment(slotted)) {
				return slotted;
			}
		}
		ItemStack held = entity.getMainHandItem();
		return WeaponSlot.isEquipment(held) ? held : ItemStack.EMPTY;
	}

	/** A hit at the average of each die, without a crit. */
	public static double physicalHit(final LivingEntity caster, final String key, final double skillPercent) {
		Dice dice = dice(caster, key);
		return hit(caster, key, skillPercent, (dice.min + dice.max) / 2.0 * dice.multiplier);
	}

	/** The lowest and the highest hit, without a crit. */
	public static double[] physicalRange(final LivingEntity caster, final String key, final double skillPercent) {
		Dice dice = dice(caster, key);
		return new double[] {
			hit(caster, key, skillPercent, dice.min * dice.multiplier),
			hit(caster, key, skillPercent, dice.max * dice.multiplier)
		};
	}

	/** A hit with rolled dice and a LUK crit roll. */
	public static double physicalDamage(final LivingEntity caster, final String key, final double skillPercent) {
		Dice dice = dice(caster, key);
		int rolled = RpgCombat.rollDice(caster, dice.stat);
		if (dice.weapon != null) {
			rolled += RpgCombat.rollDice(caster, dice.weapon);
		}
		double hit = hit(caster, key, skillPercent, rolled * dice.multiplier);
		return RpgCombat.rollCrit(caster) ? hit * CritBonuses.critMultiplier(caster) : hit;
	}

	/** Level plus two times VIT, times the coefficient. */
	public static double skillHeal(final LivingEntity caster, final double coefficient) {
		double vit = StatBonuses.effective(caster, Stat.VIT);
		return (level(caster) + HEAL_PER_VIT * vit) * coefficient / RpgScale.k() * JobItems.healingFactor(caster);
	}

	/** The effect duration multiplier. It has the cap of the INT one. */
	public static double durationMultiplier(final LivingEntity caster, final String key) {
		return HpModel.durationMultiplier((int) stat(caster, key));
	}

	/** A physical source that names the caster. Def and armor apply. The outgoing scaling does not. */
	public static DamageSource skillSource(final LivingEntity caster) {
		return new DamageSource(caster.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RpgDamageTypes.SKILL),
			caster, caster);
	}

	private static double hit(final LivingEntity caster, final String key, final double skillPercent, final double dice) {
		double status = RpgCombat.statusAtk((int) stat(caster, key));
		double flat = Affixes.total(caster, Affix.DAMAGE_FLAT) / RpgScale.k();
		return skillPercent / PERCENT * (attack(caster, key) + dice + status + flat) * RpgCombat.damageFactor(caster);
	}

	private static double attack(final LivingEntity caster, final String key) {
		ItemStack weapon = weapon(caster);
		if (isRanged(key)) {
			int power = weapon.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(
				caster.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), weapon);
			return ARROW_BASE + ARROW_PER_POWER * power;
		}
		double base = caster.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		return AttackPreview.withEnchantments(weapon, weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND));
	}

	private static boolean isRanged(final String key) {
		return Stat.byKey(key).filter(stat -> stat == Stat.DEX).isPresent();
	}

	private static Dice dice(final LivingEntity caster, final String key) {
		ItemStack weapon = weapon(caster);
		RpgDice.Dice statDice = RpgDice.of((int) stat(caster, key));
		RpgDice.Dice weaponDice = PhysicalDice.weaponDice(weapon, isRanged(key));
		int min = statDice.min() + (weaponDice == null ? 0 : weaponDice.min());
		int max = statDice.max() + (weaponDice == null ? 0 : weaponDice.max());
		return new Dice(statDice, weaponDice, min, max, PhysicalDice.multiplier(caster, weapon));
	}

	private static int level(final LivingEntity caster) {
		if (caster instanceof Player player) {
			return player.experienceLevel;
		}
		return caster instanceof Mob mob ? MobsBridge.threat(mob) : 0;
	}

	private record Dice(RpgDice.Dice stat, RpgDice.Dice weapon, int min, int max, double multiplier) {
	}
}
