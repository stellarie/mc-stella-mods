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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The bow skeleton, stray and bogged release, under this mod's own arrow rules and open to another mod's driver.
 * A skeleton looses the slowing single shot or the flaming volley; a stray or a bogged looses the one arrow
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

	/** Arrows in this mob's volley: 6, 12, or 18 for a skeleton; 0 for a stray or a bogged. */
	public static int volleySize(final Mob mob) {
		return mob.getType() == EntityTypes.SKELETON ? SkeletonVolleyGoal.arrowsFor(mob) : 0;
	}

	/** One release along a fixed direction, for a driver that has already chosen where the arrow goes. */
	public static List<UUID> release(
		final Mob mob, final Vec3 direction, final float power, final float uncertainty, final boolean volley
	) {
		return release(mob, arrow -> direction, power, uncertainty, volley);
	}

	/**
	 * Looses one release; `aim` reads the spawned arrow, whose height the aim may depend on.
	 * A skeleton volley fans `volleySize` flaming arrows over `SkeletonVolleyGoal.CONE_DEGREES`.
	 * A skeleton single shot is one slowing arrow.
	 * A stray or bogged always looses one vanilla arrow; `volley` is ignored.
	 * Uses one ammo item per arrow. Plays the shoot sound. Returns the spawned arrow ids.
	 */
	public static List<UUID> release(
		final Mob mob, final Function<AbstractArrow, Vec3> aim, final float power, final float uncertainty, final boolean volley
	) {
		if (!(mob.level() instanceof ServerLevel level)) {
			skip(mob, "clientSide");
			return List.of();
		}

		// Eligibility is the caller's rule: the volley goal already looses from a bow in either hand or behind a shield.
		if (!isKitType(mob.getType()) || !(mob instanceof AbstractSkeleton skeleton)) {
			skip(mob, "unsupportedType");
			return List.of();
		}

		boolean fan = volley && mob.getType() == EntityTypes.SKELETON;
		int count = fan ? volleySize(mob) : 1;
		ItemStack bow = mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
		// Monster.getProjectile reads the off hand first, so a tipped stack tips every arrow.
		ItemStack ammo = mob.getProjectile(bow);
		double turn = Math.toRadians(SkeletonVolleyGoal.spacingDegrees(count));
		List<UUID> arrows = new ArrayList<>();
		List<Vec3> velocities = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			double angle = (i - (count - 1) / 2.0) * turn;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			ItemStack one = ammo.copyWithCount(1);
			AbstractArrow arrow;
			if (mob.getType() == EntityTypes.SKELETON) {
				arrow = new Arrow(level, mob, one, bow);
				if (fan) {
					arrow.addTag(SkeletonVolley.VOLLEY_TAG);
					arrow.igniteForTicks(SkeletonVolleyGoal.FLAME_TICKS);
				} else {
					arrow.addTag(SkeletonVolley.SLOW_TAG);
				}
			} else {
				arrow = ((AbstractSkeletonArrowInvoker) skeleton).stella$getArrow(one, FULL_DRAW, bow);
			}

			// Only the horizontal part turns, so every arrow of the fan keeps the aim's own height.
			Vec3 base = aim.apply(arrow);
			Projectile.spawnProjectileUsingShoot(
				arrow, level, one, base.x * cos - base.z * sin, base.y, base.x * sin + base.z * cos, power, uncertainty
			);
			if (level.getEntity(arrow.getUUID()) == arrow) {
				arrows.add(arrow.getUUID());
				velocities.add(arrow.getDeltaMovement());
			} else {
				skip(mob, "spawnRejected");
			}
		}

		ammo.shrink(count);
		mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
		if (fan) {
			SkeletonVolley.recorder().volley(mob, arrows, velocities);
		} else if (mob.getType() == EntityTypes.SKELETON) {
			SkeletonVolley.recorder().shot(mob, arrows);
		}

		log(String.format(
			Locale.ROOT,
			"[BEHAVIOR] test=archerKit RELEASE mob=%s type=%s tick=%d arrows=%d wanted=%d volley=%s power=%.2f uncertainty=%.2f",
			mob.getUUID(), EntityType.getKey(mob.getType()), mob.tickCount, arrows.size(), count, fan, power, uncertainty
		));
		return arrows;
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
