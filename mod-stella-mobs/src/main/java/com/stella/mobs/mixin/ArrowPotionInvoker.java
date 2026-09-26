package com.stella.mobs.mixin;

import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** M2: a volley cloud copies the arrow's own potion, which vanilla keeps private. */
@Mixin(Arrow.class)
public interface ArrowPotionInvoker {
	@Invoker("getPotionContents")
	PotionContents stella$potionContents();
}
