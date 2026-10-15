package com.stella.spells;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A staff swing fires an instant magic bolt along the look line.
 * The punch packet comes only for a swing at air, so a melee hit never also fires a bolt.
 */
public final class StaffBolt {
	static final double RANGE = 24.0;
	static final double FLAT_DAMAGE = 4.0;
	private static final double STEP = 0.5;
	private static final double READY = 0.9;

	private StaffBolt() {
	}

	public static void onSwing(final ServerPlayer player) {
		if (!StaffPower.isStaff(player.getMainHandItem())) {
			return;
		}
		if (player.getAttackStrengthScale(0.0F) < READY) {
			return;
		}
		player.resetAttackStrengthTicker();
		fire(player);
	}

	/** Fires the bolt; returns the entity hit, or null. */
	public static LivingEntity fire(final ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 to = from.add(look.scale(RANGE));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end,
			player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && !allied(player, e), 0.3F);
		if (hit != null) {
			end = hit.getLocation();
		}
		double length = end.subtract(from).length();
		for (double d = 1.0; d < length; d += STEP) {
			Vec3 p = from.add(look.scale(d));
			level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.1);
		}
		if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
			return null;
		}
		double matk = RpgBridge.matkHit(player, 100.0);
		float amount = (float) (Double.isNaN(matk) || matk <= 0 ? FLAT_DAMAGE : matk);
		target.hurtServer(level, SpellCasting.spellSource(player), amount);
		return target;
	}

	private static boolean allied(final ServerPlayer player, final Entity e) {
		return e == player || player.isAlliedTo(e) || Friends.isFriend(e)
			|| (e instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player));
	}
}
