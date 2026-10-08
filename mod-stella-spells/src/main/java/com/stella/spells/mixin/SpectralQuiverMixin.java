package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.spells.ArcherSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The spectral quiver: a bow or a crossbow with nothing to fire still draws one arrow of its own. */
@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class SpectralQuiverMixin {
	@ModifyReturnValue(method = "getProjectile", at = @At("RETURN"))
	private ItemStack stellaSpells$spectralQuiver(final ItemStack drawn, final ItemStack weapon) {
		if (!drawn.isEmpty() || !((Object) this instanceof ServerPlayer player)) {
			return drawn;
		}
		return ArcherSkills.ammoFree(player, weapon) ? new ItemStack(net.minecraft.world.item.Items.ARROW) : drawn;
	}
}
