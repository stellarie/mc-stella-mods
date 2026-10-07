package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.mixin.AbstractSkeletonArrowInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The bow skeleton, stray and bogged release, under this mod's own arrow rules and open to another mod's driver.
 * A skeleton looses the slowing single shot or one arrow of its burst. A stray or a bogged looses the one arrow
 * vanilla builds for it, with its slowness or its poison.
 */
public final class ArcherKit {
	/** A stray's or bogged's arrow is built at full draw, so vanilla's enchantment scaling sees the strongest bow. */
	private static final float FULL_DRAW = 1.0F;
	private static volatile Consumer<String> lineTap = line -> {
	};

	private ArcherKit() {
	}

	/** Test seam: every line this class logs, as text, before it goes to the logger. */
	public static void setLineTap(final Consumer<String> tap) {
		lineTap = tap == null ? line -> {
		} : tap;
	}

	/** A bow skeleton, stray, or bogged with no blocking off hand, and not a stray sniper. */
	public static boolean isKitArcher(final Mob mob) {
		EntityType<?> type = mob.getType();
		if (!isKitType(type)) {
			return false;
		}

		if (!mob.getMainHandItem().is(Items.BOW) || mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return false;
		}

		return type != EntityTypes.STRAY || BossAbilities.copiesOf(mob, StrayKit.SniperGoal.class) == 0;
	}

	private static boolean isKitType(final EntityType<?> type) {
		return type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED;
	}

	/** Arrows in this mob's burst: 3, 5, or 7 for a skeleton; 0 for a stray or a bogged. */
	public static int burstSize(final Mob mob) {
		return mob.getType() == EntityTypes.SKELETON ? SkeletonVolleyGoal.arrowsFor(mob) : 0;
	}

	/** One release along a fixed direction, for a driver that has already chosen where the arrow goes. */
	public static List<UUID> release(
		final Mob mob, final Vec3 direction, final float power, final float uncertainty, final boolean burst
	) {
		return release(mob, arrow -> direction, power, uncertainty, burst);
	}

	/**
	 * Looses one arrow; `aim` reads the spawned arrow, whose height the aim may depend on.
	 * A skeleton burst arrow is plain and strays from the aim by at most `BURST_SPREAD_DEGREES`.
	 * A skeleton single shot is one slowing arrow on the exact aim.
	 * No skeleton arrow burns, even from a bow with Flame.
	 * A stray or bogged always looses one vanilla arrow; `burst` is ignored.
	 * Uses one ammo item. Plays the shoot sound. Returns the spawned arrow id, or none.
	 */
	public static List<UUID> release(
		final Mob mob, final Function<AbstractArrow, Vec3> aim, final float power, final float uncertainty, final boolean burst
	) {
		if (!(mob.level() instanceof ServerLevel level)) {
			skip(mob, "clientSide");
			return List.of();
		}

		// Eligibility is the caller's rule: the burst goal already looses from a bow in either hand or behind a shield.
		if (!isKitType(mob.getType()) || !(mob instanceof AbstractSkeleton skeleton)) {
			skip(mob, "unsupportedType");
			return List.of();
		}

		boolean skeletonBurst = burst && mob.getType() == EntityTypes.SKELETON;
		ItemStack bow = mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
		// Monster.getProjectile reads the off hand first, so a tipped stack tips every arrow.
		ItemStack ammo = mob.getProjectile(bow);
		ItemStack one = ammo.copyWithCount(1);
		AbstractArrow arrow;
		if (mob.getType() == EntityTypes.SKELETON) {
			arrow = new Arrow(level, mob, one, bow);
			if (!skeletonBurst) {
				arrow.addTag(SkeletonVolley.SLOW_TAG);
			}
		} else {
			arrow = ((AbstractSkeletonArrowInvoker) skeleton).stella$getArrow(one, FULL_DRAW, bow);
		}

		Vec3 aimed = aim.apply(arrow);
		Vec3 direction = skeletonBurst ? strayed(aimed, mob.getRandom()) : aimed;
		Projectile.spawnProjectileUsingShoot(arrow, level, one, direction.x, direction.y, direction.z, power, uncertainty);
		if (mob.getType() == EntityTypes.SKELETON) {
			// Flame bows light arrows at spawn.
			arrow.clearFire();
		}

		List<UUID> arrows = new ArrayList<>();
		if (level.getEntity(arrow.getUUID()) == arrow) {
			arrows.add(arrow.getUUID());
		} else {
			skip(mob, "spawnRejected");
		}

		ammo.shrink(arrows.size());
		if (!arrows.isEmpty() && mob instanceof Stray stray && stray.getTarget() != null) {
			StrayKit.afterShot(stray, stray.getTarget());
		}
		mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!arrows.isEmpty() && mob.getType() == EntityTypes.SKELETON) {
			if (skeletonBurst) {
				SkeletonVolley.recorder().burst(mob, arrow.getUUID(), arrow.getDeltaMovement());
			} else {
				SkeletonVolley.recorder().shot(mob, arrow.getUUID(), arrow.getDeltaMovement());
			}
		}

		log(String.format(
			Locale.ROOT,
			"[BEHAVIOR] test=archerKit RELEASE mob=%s type=%s tick=%d arrows=%d burst=%s power=%.2f uncertainty=%.2f",
			mob.getUUID(), EntityType.getKey(mob.getType()), mob.tickCount, arrows.size(), skeletonBurst, power, uncertainty
		));
		return arrows;
	}

	/** Turns a direction by random yaw and pitch. */
	private static Vec3 strayed(final Vec3 direction, final RandomSource random) {
		double flat = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		double yaw = Math.atan2(direction.z, direction.x) + Math.toRadians(offsetDegrees(random));
		double pitch = Math.atan2(direction.y, flat) + Math.toRadians(offsetDegrees(random));
		pitch = Math.max(-Math.PI / 2.0, Math.min(Math.PI / 2.0, pitch));
		double length = direction.length();
		return new Vec3(
			length * Math.cos(pitch) * Math.cos(yaw), length * Math.sin(pitch), length * Math.cos(pitch) * Math.sin(yaw)
		);
	}

	private static double offsetDegrees(final RandomSource random) {
		return (random.nextDouble() * 2.0 - 1.0) * SkeletonVolleyGoal.BURST_SPREAD_DEGREES;
	}

	private static void skip(final Mob mob, final String reason) {
		log(String.format(
			Locale.ROOT, "[BEHAVIOR] test=archerKit SKIP mob=%s type=%s tick=%d reason=%s",
			mob.getUUID(), EntityType.getKey(mob.getType()), mob.tickCount, reason
		));
	}

	private static void log(final String line) {
		lineTap.accept(line);
		StellaMobs.LOGGER.info(line);
	}
}
