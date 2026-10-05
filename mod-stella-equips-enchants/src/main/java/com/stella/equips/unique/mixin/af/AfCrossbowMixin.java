package com.stella.equips.unique.mixin.af;

import com.stella.equips.refine.Refine;
import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.af.ForemanArbalest;
import com.stella.equips.unique.af.RaiderRepeater;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies reload and ammunition rules through vanilla crossbow shooting. */
@Mixin(CrossbowItem.class)
public abstract class AfCrossbowMixin {
	@Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
	private static void stella$foremanCharge(final ItemStack weapon, final LivingEntity holder,
		final CallbackInfoReturnable<Integer> cir) {
		UniqueDef def = Uniques.def(weapon);
		if (def != null && ForemanArbalest.ID.equals(def.id())) {
			cir.setReturnValue(ForemanArbalest.chargeTicks(cir.getReturnValueI(), Refine.level(weapon)));
		}
	}

	@Redirect(method = "performShooting", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object stella$crossbowLoad(final ItemStack stack, final DataComponentType<Object> component, final Object value,
		final Level level, final LivingEntity shooter, final InteractionHand hand, final ItemStack weapon,
		final float power, final float uncertainty, final LivingEntity target) {
		Object previous = stack.set(component, value);
		if (!(previous instanceof ChargedProjectiles loaded) || loaded.isEmpty() || !(shooter instanceof ServerPlayer holder)) {
			return previous;
		}
		UniqueDef def = Uniques.def(weapon);
		if (def == null) {
			return previous;
		}
		if (RaiderRepeater.ID.equals(def.id()) && RaiderRepeater.retains(holder, weapon)) {
			stack.set(component, loaded);
		}
		if (ForemanArbalest.ID.equals(def.id()) && Refine.level(weapon) >= Uniques.PLUS_15 && loaded.size() == 1) {
			ItemStack ammo = loaded.itemCopies().findFirst().orElseThrow();
			List<ItemStack> bolts = new ArrayList<>();
			for (int i = 0; i < ForemanArbalest.TUNING.bolts(); i++) {
				bolts.add(ammo.copy());
			}
			return ChargedProjectiles.ofNonEmpty(bolts);
		}
		return previous;
	}

	@Inject(method = "shootProjectile", at = @At("TAIL"))
	private void stella$foremanPickup(final LivingEntity shooter, final Projectile projectile, final int index,
		final float power, final float uncertainty, final float angle, final LivingEntity target, final CallbackInfo ci) {
		if (index > 0 && projectile instanceof AbstractArrow arrow) {
			UniqueArrows.Mark mark = UniqueArrows.of(arrow);
			if (mark != null && ForemanArbalest.ID.equals(mark.id()) && mark.refine() >= Uniques.PLUS_15) {
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			}
		}
	}
}
