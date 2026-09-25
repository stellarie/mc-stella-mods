package com.stella.spells;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SpellCasting {
	private static final double RANGE = 16.0;
	private static final float DAMAGE = 3.0f;
	private static final float BURN_SECONDS = 2.0f;

	private SpellCasting() {
	}

	public static CastResult cast(final ServerPlayer caster, final SpellbookItem book, final InteractionHand hand) {
		ItemStack stack = caster.getItemInHand(hand);
		Mob target = findTarget(caster);

		if (caster.getCooldowns().isOnCooldown(stack)) {
			log(caster, CastResult.COOLDOWN, target);
			return CastResult.COOLDOWN;
		}

		if (target == null || target.isInvulnerable()) {
			log(caster, CastResult.NO_TARGET, target);
			return CastResult.NO_TARGET;
		}

		target.hurt(caster.level().damageSources().magic(), DAMAGE);
		target.igniteForSeconds(BURN_SECONDS);
		caster.getCooldowns().addCooldown(stack, SpellbookItem.BASE_COOLDOWN_TICKS);
		log(caster, CastResult.CAST, target);
		return CastResult.CAST;
	}

	private static Mob findTarget(final ServerPlayer caster) {
		ServerLevel level = caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		Vec3 end = start.add(look.scale(RANGE));
		BlockHitResult blockHit = level.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)
		);
		double maxDistance = blockHit.getType() == HitResult.Type.MISS
			? RANGE
			: start.distanceTo(blockHit.getLocation());
		double maxDistanceSquared = maxDistance * maxDistance;
		AABB search = caster.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
		Mob nearest = null;
		double nearestDistanceSquared = maxDistanceSquared;

		for (Mob mob : level.getEntitiesOfClass(
			Mob.class,
			search,
			candidate -> candidate.isAlive() && candidate instanceof Enemy
		)) {
			Optional<Vec3> hit = mob.getBoundingBox().inflate(mob.getPickRadius()).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}

			double distanceSquared = start.distanceToSqr(hit.orElseThrow());
			if (distanceSquared < nearestDistanceSquared) {
				nearest = mob;
				nearestDistanceSquared = distanceSquared;
			}
		}

		return nearest;
	}

	private static void log(final ServerPlayer caster, final CastResult result, final Mob target) {
		Spellbooks.LOGGER.info(
			"[SPELL] spell_id={} result={} target={} cooldown_ticks={} book_enchants=[] head_enchants=[] chest_enchants=[] caster={}",
			Spellbooks.SPARK_SHOT_ID,
			result,
			target == null ? "none" : target.getUUID(),
			SpellbookItem.BASE_COOLDOWN_TICKS,
			caster.getUUID()
		);
	}

	public enum CastResult {
		CAST,
		NO_TARGET,
		COOLDOWN
	}
}
