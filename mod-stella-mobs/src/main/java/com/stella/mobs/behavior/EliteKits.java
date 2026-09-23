package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/**
 * The R24 abilities that ride an existing mechanism rather than adding one. The pillager's slow and the
 * stray's slow are both the off hand the weapon reads first, so a tipped stack is the whole payload and no
 * firing code changes. The stray's freeze rides the damage hook keyed on the arrow's owner. The free tier
 * is an effect on an elite and nothing else: vanilla owns the payload, and no goal is written for it.
 */
public final class EliteKits {
	/** Vanilla's own strong slowness: Slowness IV, sixty percent slower, for twenty seconds. */
	public static final int SLOWNESS_AMPLIFIER = 3;
	public static final int SLOWNESS_TICKS = 400;
	public static final int AMMO_STACK = 64;
	/** The full-freeze threshold, so one hit freezes and the two per tick thaw takes about seventy ticks. */
	public static final int FREEZE_ON_HIT = 140;

	private EliteKits() {
	}

	/** The elite's own kit, written on the mark and before the installer, which reads the off hand. */
	public static void applyKit(final Mob mob) {
		if (mob instanceof Skeleton skeleton) {
			// R22: the skeleton's own kit, whose offhand shield the installer then reads.
			if (!SkeletonEliteKit.applied(skeleton)) {
				SkeletonEliteKit.apply(skeleton);
			}

			return;
		}

		if (mob instanceof Stray stray) {
			slowArrows(stray);
			return;
		}

		if (mob instanceof Pillager pillager) {
			slowArrows(pillager);
		}
	}

	/** The free tier goes on every elite, boss or summon included, because it costs no behaviour at all. */
	public static void applyFreeTier(final Mob mob) {
		Holder<MobEffect> free = freeTierEffect(mob);
		if (free == null || mob.hasEffect(free)) {
			return;
		}

		boolean added = mob.addEffect(
			new MobEffectInstance(free, MobEffectInstance.INFINITE_DURATION, 0, false, false), mob
		);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=freeTierElite t={} APPLY type={} added={}",
			mob.level().getGameTime(), mob.getType(), added
		);
	}

	/**
	 * R24: the slow rides the off hand, which is the stack the crossbow and the bow read first, so one
	 * tipped arrow per shot is consumed from this stack and the weapon code is untouched.
	 */
	private static void slowArrows(final Mob mob) {
		ItemStack tipped = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.STRONG_SLOWNESS)
			.copyWithCount(AMMO_STACK);
		mob.setItemSlot(EquipmentSlot.OFFHAND, tipped);
	}

	/**
	 * R24: the freeze is genuine and rides the arrow, not the potion. A melee hit does not freeze, because
	 * the clause is the shot.
	 */
	public static void strayFreeze(
		final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken,
		final boolean blocked
	) {
		if (blocked || !(source.getDirectEntity() instanceof AbstractArrow arrow)) {
			return;
		}

		if (!(arrow.getOwner() instanceof Stray stray) || !Elite.isElite(stray)) {
			return;
		}

		entity.setTicksFrozen(entity.getTicksFrozen() + FREEZE_ON_HIT);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=strayFreeze t={} HIT frozen={} threshold={} fullyFrozen={}",
			stray.level().getGameTime(), entity.getTicksFrozen(), entity.getTicksRequiredToFreeze(),
			entity.isFullyFrozen()
		);
	}

	/**
	 * R24's free tier: oozing, infested and weaving carry their own payload, because vanilla spawns slimes,
	 * silverfish or cobwebs when the holder dies. This is the whole implementation, and no goal exists for
	 * any of the three, so nothing else needs to be looked for. The host is a mob vanilla lets hold the
	 * effect: a slime is immune to oozing and a silverfish to infested, by vanilla's own tag checks.
	 */
	private static @Nullable Holder<MobEffect> freeTierEffect(final Mob mob) {
		if (mob.getType() == EntityTypes.ZOMBIE) {
			return MobEffects.OOZING;
		}

		if (mob.getType() == EntityTypes.SKELETON) {
			return MobEffects.INFESTED;
		}

		if (mob.getType() == EntityTypes.SPIDER || mob.getType() == EntityTypes.CAVE_SPIDER) {
			return MobEffects.WEAVING;
		}

		return null;
	}
}
