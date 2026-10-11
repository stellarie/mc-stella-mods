package com.stella.mobs.mixin;

import net.minecraft.world.entity.monster.Vex;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A vex keeps its collision through its tick, so it no longer flies through blocks. */
@Mixin(Vex.class)
public abstract class VexSolidMixin {
	@Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/Vex;noPhysics:Z", opcode = Opcodes.PUTFIELD, ordinal = 0))
	private void stellaMobs$keepPhysics(final Vex vex, final boolean noPhysics) {
		((Vex) (Object) this).noPhysics = false;
	}
}
