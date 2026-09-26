package com.stella.spells;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface SpellEffect {
	SpellEffect NO_OP = (caster, target, point, modifiers) -> false;

	boolean apply(ServerPlayer caster, Mob target, Vec3 point, SpellModifiers.CastModifiers modifiers);
}
