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

	private StaffBolt() {
	}

	/** A staff never lands a melee blow: the attack becomes the bolt, so no crit and no STR damage apply. */
	public static void register() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer caster) || !StaffPower.firesBolt(caster.getMainHandItem())) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			onSwing(caster);
			return net.minecraft.world.InteractionResult.FAIL;
		});
	}

	public static void onSwing(final ServerPlayer player) {
		if (!StaffPower.firesBolt(player.getMainHandItem())) {
			return;
		}
		float charge = player.getAttackStrengthScale(0.0F);
		player.resetAttackStrengthTicker();
		fire(player, charge);
	}

	/** Vanilla's damage share of a melee hit at this charge: 0.2 at none, 1 at full. */
	static double chargeFactor(final float charge) {
		double c = Math.max(0.0, Math.min(1.0, charge));
		return 0.2 + c * c * 0.8;
	}

	/** Fires the bolt; returns the entity hit, or null. */
	public static LivingEntity fire(final ServerPlayer player) {
		return fire(player, 1.0F);
	}

	/** Fires the bolt at a charge; the damage scales by the vanilla charge curve. */
	public static LivingEntity fire(final ServerPlayer player, final float charge) {
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
		SpellBolt.fly(level, player, net.minecraft.resources.Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "staff_bolt"),
			net.minecraft.world.item.Items.SOUL_LANTERN, ParticleTypes.SOUL_FIRE_FLAME, from, end);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
			return null;
		}
		double matk = RpgBridge.matkHit(player, 100.0);
		float amount = Double.isNaN(matk) || matk <= 0 ? (float) FLAT_DAMAGE : RpgBridge.vanilla((float) matk);
		amount *= (float) chargeFactor(charge);
		target.hurtServer(level, SpellCasting.spellSource(player), amount);
		SpellImbue.onStaffHit(player, target);
		return target;
	}

	private static boolean allied(final ServerPlayer player, final Entity e) {
		return e == player || player.isAlliedTo(e) || Friends.isFriend(e)
			|| (e instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player));
	}
}
