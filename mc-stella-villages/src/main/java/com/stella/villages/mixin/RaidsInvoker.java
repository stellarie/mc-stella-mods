package com.stella.villages.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** V3: vanilla's raid registry, which only a player's omen can reach otherwise. */
@Mixin(Raids.class)
public interface RaidsInvoker {
	@Invoker("getOrCreateRaid")
	Raid mcStellaVillages$getOrCreateRaid(ServerLevel level, BlockPos center);

	@Invoker("getUniqueId")
	int mcStellaVillages$getUniqueId();

	@Accessor("raidMap")
	Int2ObjectMap<Raid> mcStellaVillages$raidMap();
}
