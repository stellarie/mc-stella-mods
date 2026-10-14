package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.mobs.behavior.ShotClearance;
import com.stella.mobs.behavior.ShotLead;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A bow skeleton, stray, or bogged leads a moving target. Vanilla aims every arrow at where the target stands, so a
 * running player is never hit from 24 blocks. The wrap changes only the direction handed to the shot. The speed, the
 * arc term, and the uncertainty stay vanilla's, and the arc term is read again for the lead point's distance.
 *
 * <p>When another mob stands in the line of fire, the same wrap lifts the aim, and slows the arrow if it must, so
 * the arrow flies over that mob.
 */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonShotLeadMixin {
	private static final float ARC_PER_BLOCK = 0.2F;

	@WrapOperation(
		method = "performRangedAttack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
		)
	)
	private Projectile stella$leadTheShot(
		final Projectile arrow, final ServerLevel level, final ItemStack ammo, final double xd, final double yd, final double zd,
		final float power, final float uncertainty, final Operation<Projectile> original, @Local(argsOnly = true) final LivingEntity target
	) {
		Vec3 lead = ShotLead.offset((AbstractSkeleton) (Object) this, target, power);
		double flatBefore = Math.sqrt(xd * xd + zd * zd);
		double leadX = xd + lead.x;
		double leadZ = zd + lead.z;
		double flatAfter = Math.sqrt(leadX * leadX + leadZ * leadZ);
		double leadY = yd - flatBefore * ARC_PER_BLOCK + lead.y + flatAfter * ARC_PER_BLOCK;
		AbstractSkeleton shooter = (AbstractSkeleton) (Object) this;
		ShotClearance.Shot shot = ShotClearance.raise(shooter, arrow.position(), target, new Vec3(leadX, leadY, leadZ), power);
		return original.call(arrow, level, ammo, shot.aim().x, shot.aim().y, shot.aim().z, (float) shot.power(), uncertainty);
	}
}
