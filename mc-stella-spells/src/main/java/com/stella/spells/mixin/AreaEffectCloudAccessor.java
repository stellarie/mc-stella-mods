package com.stella.spells.mixin;

import java.util.Map;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {
	@Accessor("victims")
	Map<Entity, Integer> stellaSpells$victims();
}
