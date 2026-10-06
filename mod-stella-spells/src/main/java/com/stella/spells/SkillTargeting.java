package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Who a skill hits and who it helps. Every skill book reads these two predicates, so one rule holds for the
 * whole mod: a foe is never an ally, and a shot stops at the first block in its way.
 */
public final class SkillTargeting {
	/** One block of slack on a search box, so a body on the edge is still read. */
	private static final double SEARCH_SLACK = 1.0;

	private SkillTargeting() {
	}

	/**
	 * Whether this entity may be helped. A hostile is never one, so a golem another mod tags hostile is
	 * left out even though its kind is friendly. {@link Friends#isFoe} is asked first and last.
	 */
	static boolean isAlly(final LivingEntity caster, final LivingEntity candidate) {
		return candidate != caster && candidate.isAlive() && !Friends.isFoe(candidate)
			&& (candidate instanceof Player || candidate instanceof AbstractGolem || Friends.isFriend(candidate));
	}

	/**
	 * Every ally within this reach of the caster, nearest first. The one predicate above decides, so a new
	 * caller cannot invent its own gentler ally rule.
	 */
	static List<LivingEntity> alliesAround(final ServerPlayer caster, final double range) {
		List<LivingEntity> allies = new ArrayList<>(caster.level().getEntitiesOfClass(
			LivingEntity.class,
			caster.getBoundingBox().inflate(range),
			candidate -> isAlly(caster, candidate)
				&& candidate.position().distanceToSqr(caster.position()) <= range * range
		));
		allies.sort(Comparator.comparingDouble(ally -> ally.position().distanceToSqr(caster.position())));
		return allies;
	}

	/** The nearest foe a ray from the caster's eyes meets through this reach, or null. A block stops it. */
	static Mob rayFoe(final ServerPlayer caster, final Vec3 direction, final double range) {
		EntityHitResult entityHit = rayHit(caster, direction, range);
		return entityHit != null && entityHit.getEntity() instanceof Mob mob ? mob : null;
	}

	/** Every foe on a ray through this reach, nearest first. The search box stops at the first block. */
	static List<Mob> rayFoes(final ServerPlayer caster, final Vec3 direction, final double range) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 end = start.add(direction.scale(blockedReach(level, caster, start, direction, range)));
		List<Mob> crossed = new ArrayList<>();
		for (Mob foe : level.getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().expandTowards(direction.scale(range)).inflate(SEARCH_SLACK),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
		)) {
			if (foe.getBoundingBox().inflate(foe.getPickRadius()).clip(start, end).isPresent()) {
				crossed.add(foe);
			}
		}
		crossed.sort(Comparator.comparingDouble(foe -> foe.position().distanceToSqr(caster.position())));
		return crossed;
	}

	/**
	 * Every foe within this reach whose own direction from the caster sits inside this many degrees of the
	 * ray: what an awakened fan can bend onto, whether or not the straight ray would meet it.
	 */
	static List<Mob> foesOnRay(final ServerPlayer caster, final Vec3 direction, final double range) {
		Vec3 forward = direction.normalize();
		Vec3 eye = caster.getEyePosition();
		List<Mob> near = new ArrayList<>();
		for (Mob foe : caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().inflate(range),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
		)) {
			Vec3 toFoe = foe.getBoundingBox().getCenter().subtract(eye);
			if (toFoe.lengthSqr() > range * range || toFoe.lengthSqr() < 1.0E-6) {
				continue;
			}
			if (forward.dot(toFoe.normalize()) > 0.0) {
				near.add(foe);
			}
		}
		near.sort(Comparator.comparingDouble(foe -> foe.position().distanceToSqr(caster.position())));
		return near;
	}

	/** How far along a direction the caster's eyes reach before a block stops them. */
	static double blockedReach(
		final ServerLevel level, final ServerPlayer caster, final Vec3 start, final Vec3 direction, final double range
	) {
		BlockHitResult blockHit = level.clip(new ClipContext(
			start, start.add(direction.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster
		));
		return blockHit.getType() == HitResult.Type.MISS ? range : start.distanceTo(blockHit.getLocation());
	}

	// The one entity hit a ray meets inside the block-stopped reach, or null for none.
	private static EntityHitResult rayHit(final ServerPlayer caster, final Vec3 direction, final double range) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 end = start.add(direction.scale(range));
		double reach = blockedReach(level, caster, start, direction, range);
		AABB search = caster.getBoundingBox().expandTowards(direction.scale(range)).inflate(SEARCH_SLACK);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			caster, start, end, search,
			candidate -> candidate instanceof Mob mob && mob.isAlive() && Friends.isFoe(mob), reach * reach
		);
		return entityHit != null && entityHit.getEntity() instanceof Mob ? entityHit : null;
	}
}
