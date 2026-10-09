package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.spells.ArcherSkills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The spectral quiver: a bow or a crossbow with nothing to fire still draws one arrow of its own. */
@Mixin(Player.class)
public abstract class SpectralQuiverMixin {
	// Both sides must agree: the client's use() refuses a draw it sees no projectile for.
	@ModifyReturnValue(method = "getProjectile", at = @At("RETURN"))
	private ItemStack stellaSpells$spectralQuiver(final ItemStack drawn, final ItemStack weapon) {
		Player player = (Player) (Object) this;
		if (!drawn.isEmpty() || !ArcherSkills.ammoFree(player, weapon)) {
			return drawn;
		}
		ItemStack arrow = new ItemStack(Items.ARROW);
		// Intangible makes the loosed arrow creative-only to pick up, so the quiver mints no arrows.
		arrow.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
		return arrow;
	}
}
