package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * A village head or witch that a player talks to stands still, drops its target and faces the player while
 * any player has its menu open, and for {@link #LINGER_TICKS} after the last one closes.
 */
public final class Conversation {
	public static final int LINGER_TICKS = 100;
	private static final long OPEN = Long.MAX_VALUE;

	private static final class Hold {
		private final Mob mob;
		private final Vec3 anchor;
		private final Set<UUID> listeners = ConcurrentHashMap.newKeySet();
		private volatile UUID facing;
		private volatile long releaseAt = OPEN;

		private Hold(final Mob mob) {
			this.mob = mob;
			this.anchor = mob.position();
		}
	}

	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

	private Conversation() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(Conversation::onLevelTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOLDS.clear());
	}

	public static void begin(final Mob mob, final ServerPlayer player) {
		Hold hold = HOLDS.computeIfAbsent(mob.getUUID(), id -> new Hold(mob));
		hold.listeners.add(player.getUUID());
		hold.facing = player.getUUID();
		hold.releaseAt = OPEN;
		mob.getNavigation().stop();
		mob.setTarget(null);
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=conversation_started mob={} player={} listeners={}",
			McStellaVillages.MOD_ID, mob.getUUID(), player.getUUID(), hold.listeners.size());
	}

	/** A menu closed: once no one listens, the mob keeps still a little longer, then goes back to its work. */
	public static void linger(final Mob mob, final ServerPlayer player) {
		Hold hold = HOLDS.get(mob.getUUID());
		if (hold == null || !hold.listeners.remove(player.getUUID())) {
			return;
		}
		if (hold.listeners.isEmpty()) {
			hold.releaseAt = (long) mob.tickCount + LINGER_TICKS;
		} else {
			hold.facing = hold.listeners.iterator().next();
		}
	}

	public static boolean held(final Mob mob) {
		return HOLDS.containsKey(mob.getUUID());
	}

	private static void onLevelTick(final ServerLevel level) {
		for (Map.Entry<UUID, Hold> entry : HOLDS.entrySet()) {
			Hold hold = entry.getValue();
			Mob mob = hold.mob;
			if (mob.level() != level) {
				continue;
			}
			if (!mob.isAlive() || mob.isRemoved() || mob.tickCount >= hold.releaseAt) {
				HOLDS.remove(entry.getKey(), hold);
				McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=conversation_ended mob={}",
					McStellaVillages.MOD_ID, mob.getUUID());
				continue;
			}
			mob.getNavigation().stop();
			mob.setTarget(null);
			mob.setDeltaMovement(Vec3.ZERO);
			mob.setPos(hold.anchor);
			if (level.getEntity(hold.facing) instanceof ServerPlayer player) {
				face(mob, player);
			}
		}
	}

	// Set after the mob's own tick, so no look goal turns it away.
	static void face(final Mob mob, final ServerPlayer player) {
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double dy = player.getEyeY() - mob.getEyeY();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setXRot(pitch);
		mob.getLookControl().setLookAt(player);
	}
}
