package com.stella.mobs.mixin;

import com.stella.mobs.behavior.BossAbilities;
import com.stella.mobs.behavior.StrayKit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stray slips away from the spot its arrow left. A stray that keeps the vanilla bow shoots through
 * {@code AbstractSkeleton.performRangedAttack}, which is the one stray shot this mod does not already own:
 * the elite bow has its own goal, and the skeleton volley serves skeletons alone.
 */
@Mixin(AbstractSkeleton.class)
public abstract class StrayShotBlinkMixin {
	@Inject(method = "performRangedAttack", at = @At("TAIL"))
	private void stella$slipAwayAfterTheShot(final LivingEntity target, final float pullVelocity, final CallbackInfo ci) {
		if ((Object) this instanceof Stray stray && stray.isHolding(net.minecraft.world.item.Items.BOW)
			&& BossAbilities.copiesOf(stray, StrayKit.SniperGoal.class) == 0) {
			// The elite bow slips for its own shot; a stray that kept both goals must not slip twice.
			StrayKit.afterShot(stray, target);
		}
	}
}
