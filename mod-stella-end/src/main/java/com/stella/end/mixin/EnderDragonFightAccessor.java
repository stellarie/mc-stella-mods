package com.stella.end.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla keeps the fight's state and its dragon factory private; the dragon's revival and the egg rule need both. */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
	@Accessor("dragonKilled")
	boolean stellaEnd$isDragonKilled();

	@Accessor("dragonKilled")
	void stellaEnd$setDragonKilled(boolean value);

	@Accessor("hasPreviouslyKilledDragon")
	void stellaEnd$setPreviouslyKilled(boolean value);

	@Invoker("createNewDragon")
	@Nullable EnderDragon stellaEnd$createNewDragon();

	@Invoker("spawnExitPortal")
	void stellaEnd$spawnExitPortal(boolean activated);
}
