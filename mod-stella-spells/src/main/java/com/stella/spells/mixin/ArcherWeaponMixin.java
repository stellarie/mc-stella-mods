package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.spells.ArcherSkills;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The triangle shot: one shot may loose a barrage instead, and the game charges one arrow for each. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ArcherWeaponMixin {
	@ModifyReturnValue(method = "draw", at = @At("RETURN"))
	private static List<ItemStack> stellaSpells$triangleShot(
		final List<ItemStack> drawn, final ItemStack weapon, final ItemStack ammo, final LivingEntity shooter
	) {
		if (drawn.isEmpty() || !(shooter instanceof ServerPlayer player)) {
			return drawn;
		}
		int arrows = ArcherSkills.barrage(player, player.getRandom());
		if (arrows <= 1) {
			return drawn;
		}
		List<ItemStack> barrage = new ArrayList<>(drawn);
		ItemStack each = drawn.get(0);
		for (int extra = 1; extra < arrows; extra++) {
			barrage.add(each.copy());
		}
		return barrage;
	}
}
