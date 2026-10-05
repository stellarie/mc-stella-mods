package com.stella.equips.unique.mixin.af;

import com.stella.equips.refine.Refine;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.af.ForemanArbalest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Gives Foreman's triple the vanilla Multishot spread. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ForemanSpreadMixin {
	@Redirect(method = "shoot", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processProjectileSpread(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;F)F"))
	private float stella$foremanSpread(final ServerLevel level, final ItemStack weapon, final Entity holder, final float base) {
		float spread = EnchantmentHelper.processProjectileSpread(level, weapon, holder, base);
		UniqueDef def = Uniques.def(weapon);
		return def != null && ForemanArbalest.ID.equals(def.id()) && Refine.level(weapon) >= Uniques.PLUS_15
			? Math.max(spread, ForemanArbalest.TUNING.spreadDegrees()) : spread;
	}
}
