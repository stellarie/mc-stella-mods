package com.stella.nether.mixin;

import com.stella.nether.EndChest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The last eye completes the frame, but the portal stays shut until the sealed chest nearby holds a Blaze Core. */
@Mixin(EnderEyeItem.class)
public abstract class EnderEyeSealMixin {
	@Redirect(method = "useOn", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/pattern/BlockPattern;find(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/pattern/BlockPattern$BlockPatternMatch;"))
	private BlockPattern.BlockPatternMatch stellaNether$sealed(final BlockPattern shape, final LevelReader level, final BlockPos pos, final UseOnContext context) {
		BlockPattern.BlockPatternMatch match = shape.find(level, pos);
		if (match != null && level instanceof ServerLevel server && !EndChest.opens(server, pos)) {
			EndChest.refused(server, pos, context.getPlayer());
			return null;
		}
		return match;
	}
}
