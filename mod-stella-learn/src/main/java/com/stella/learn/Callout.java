package com.stella.learn;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One request a fighting mob sends to the mobs around a player. `type` names a registered handler, `priority`
 * decides which of two requests a busy mob keeps, and `durationTicks` is how long an accepting mob holds it.
 * `detail` names what a buff or debuff carries, an effect id.
 */
public record Callout(
	Identifier type, UUID caller, UUID player, int priority, int durationTicks, @Nullable Vec3 position, @Nullable Identifier detail
) {
	public Callout(final Identifier type, final UUID caller, final UUID player, final int priority, final int durationTicks, final @Nullable Vec3 position) {
		this(type, caller, player, priority, durationTicks, position, null);
	}
}
