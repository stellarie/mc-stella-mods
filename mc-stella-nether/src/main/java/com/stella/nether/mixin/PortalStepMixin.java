package com.stella.nether.mixin;

import com.stella.nether.RaidTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The moment a player steps into a nether portal, it asks which nether, rather than after the portal's wait. */
@Mixin(NetherPortalBlock.class)
public abstract class PortalStepMixin {
	@Inject(method = "entityInside", at = @At("HEAD"))
	private void stellaNether$askOnStep(final BlockState state, final Level level, final BlockPos pos, final Entity entity,
		final InsideBlockEffectApplier effects, final boolean inside, final CallbackInfo ci) {
		if (level instanceof ServerLevel server) {
			RaidTravel.stepIn(server, entity, pos);
		}
	}
}
