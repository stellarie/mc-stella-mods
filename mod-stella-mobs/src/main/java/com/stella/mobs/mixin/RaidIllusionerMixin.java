package com.stella.mobs.mixin;

import com.stella.mobs.behavior.IllusionerRaidHook;
import com.stella.mobs.behavior.IllusionerRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M9: after vanilla spawns a wave, an Illusioner may join it. The tail keeps the wave itself untouched. */
@Mixin(Raid.class)
public class RaidIllusionerMixin implements IllusionerRaidHook {
	@Inject(method = "spawnGroup", at = @At("TAIL"))
	private void stella$illusionerJoins(final ServerLevel level, final BlockPos pos, final CallbackInfo info) {
		IllusionerRaids.afterWave((Raid) (Object) this, level, pos);
	}
}
