package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
	/**
	 * The snare's landed duration, ten seconds of Slowness IV: long enough to matter in a fight, short
	 * enough not to be a death sentence. A tipped arrow scales every payload it carries, so the potion
	 * the stack names is only the recipe and the payload is derived from this landed number.
	 */
	public static final int LANDED_SLOWNESS_TICKS = 200;
	public static final int AMMO_STACK = 64;
	/** The full-freeze threshold, so one hit freezes and the two per tick thaw takes about seventy ticks. */
	public static final int FREEZE_ON_HIT = 140;
	/**
	 * The payload the potion carries so {@link #LANDED_SLOWNESS_TICKS} survive the arrow's own scale.
	 * The scale is read from the tipped stack the game registers, never written here, so this product
	 * moves with the game's number rather than with a copy of it.
	 */
	public static final int PAYLOAD_SLOWNESS_TICKS =
		(int) Math.ceil((double) LANDED_SLOWNESS_TICKS / tippedArrowPayloadScale());

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
	 * tipped arrow per shot is consumed from this stack and the weapon code is untouched. The stack names
	 * vanilla's strong slowness and carries its own duration instead, so the snare lands the named number
	 * of ticks rather than whatever the potion happens to be.
	 */
	private static void slowArrows(final Mob mob) {
		ItemStack tipped = tippedArrows(AMMO_STACK);
		mob.setItemSlot(EquipmentSlot.OFFHAND, tipped);
	}

	/** One tipped arrow whose payload lands at {@link #LANDED_SLOWNESS_TICKS}, at the elite's amplifier. */
	public static ItemStack tippedArrows(final int count) {
		ItemStack tipped = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.STRONG_SLOWNESS);
		PotionContents contents = tipped.get(DataComponents.POTION_CONTENTS);

		if (contents != null) {
			tipped.set(
				DataComponents.POTION_CONTENTS,
				contents.withEffectAdded(new MobEffectInstance(
					MobEffects.SLOWNESS, PAYLOAD_SLOWNESS_TICKS, SLOWNESS_AMPLIFIER
				))
			);
		}

		return tipped.copyWithCount(count);
	}

	/** The scale a tipped arrow applies to every payload it carries, read from the item the game ships. */
	private static double tippedArrowPayloadScale() {
		return new ItemStack(Items.TIPPED_ARROW)
			.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
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
