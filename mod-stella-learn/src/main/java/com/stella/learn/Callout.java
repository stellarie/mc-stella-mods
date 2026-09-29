package com.stella.learn;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One request a fighting mob sends to the mobs around a player. `type` names a registered handler, `priority`
 * decides which of two requests a busy mob keeps, and `durationTicks` is how long an accepting mob holds it.
 */
public record Callout(Identifier type, UUID caller, UUID player, int priority, int durationTicks, @Nullable Vec3 position) {
}
