package com.stella.mobs.mixin;

import com.stella.mobs.shield.ShieldZones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hostile mob never enters the level inside a Shield Generator's box. {@code addEntity} sits under
 * {@code addFreshEntity}, {@code addWithUUID}, {@code addDuringTeleport} and every passenger path, so one gate covers
 * natural spawns, spawner blocks, summons, promotions and any other mod's spawn. A mob read back from a chunk does not
 * pass here, so a mob already inside when the generator is placed stays.
 */
@Mixin(ServerLevel.class)
public abstract class ShieldSpawnMixin {
	@Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$refuseHostileInShield(final Entity entity, final CallbackInfoReturnable<Boolean> callback) {
		if (ShieldZones.refusesSpawn(entity)) {
			callback.setReturnValue(false);
		}
	}
}
