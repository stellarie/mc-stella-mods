package com.stella.mobs.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WitherBoss.class)
public interface WitherBossInvoker {
	/** Looses a skull from one of the three heads toward a point, with the vanilla head geometry and sound. */
	@Invoker("performRangedAttack")
	void stellaMobs$shoot(int head, double x, double y, double z, boolean dangerous);
}
