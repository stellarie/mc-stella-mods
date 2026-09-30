package com.stella.mobs.mixin;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The archer kit builds a stray's or bogged's arrow through vanilla, which adds the slowness or poison and keeps it protected. */
@Mixin(AbstractSkeleton.class)
public interface AbstractSkeletonArrowInvoker {
	@Invoker("getArrow")
	AbstractArrow stella$getArrow(ItemStack ammo, float power, ItemStack weapon);
}
