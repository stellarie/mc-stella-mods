package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An anvil never wears: use and a fall both ask for its next state, and the answer is the state it has. */
@Mixin(AnvilBlock.class)
public abstract class AnvilNeverBreaksMixin {
	@ModifyReturnValue(method = "damage", at = @At("RETURN"))
	private static @Nullable BlockState stellaEquips$keepAnvil(final @Nullable BlockState next, final BlockState blockState) {
		return blockState;
	}
}
