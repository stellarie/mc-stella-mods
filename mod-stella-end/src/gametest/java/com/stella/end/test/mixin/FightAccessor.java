package com.stella.end.test.mixin;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Test harness only: puts the vanilla fight back to a world that has never seen a dragon die. */
@Mixin(EnderDragonFight.class)
public interface FightAccessor {
	@Accessor("needsStateScanning")
	void stellaEndTest$setNeedsStateScanning(boolean value);

	@Accessor("dragonKilled")
	void stellaEndTest$setDragonKilled(boolean value);

	@Accessor("hasPreviouslyKilledDragon")
	void stellaEndTest$setPreviouslyKilled(boolean value);

	@Accessor("dragonUUID")
	void stellaEndTest$setDragonUuid(UUID value);

	@Accessor("exitPortalLocation")
	void stellaEndTest$setExitPortal(BlockPos value);

	@Accessor("respawnStage")
	void stellaEndTest$setRespawnStage(DragonRespawnStage value);

	@Accessor("ticksSinceDragonSeen")
	void stellaEndTest$setTicksSinceDragonSeen(int value);

	@Accessor("gateways")
	List<Integer> stellaEndTest$gateways();
}
