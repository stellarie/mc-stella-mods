package com.stella.villages.mixin;

import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** V3: the wave count a raid was built with, which vanilla keeps private. */
@Mixin(Raid.class)
public interface RaidAccessor {
	@Accessor("numGroups")
	int mcStellaVillages$numGroups();
}
