package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The falcon of every player or mob who holds Blitz Beat: one each, raised when the buff is cast, brought back
 * when the owner changes level or logs in with the buff still running, and gone with the buff. The falcon
 * removes itself when its owner stops qualifying, so this class only has to raise it. A mob owner is
 * kept up by {@link MobBlitz}, which knows which mobs carry the buff for good.
 * A player who holds the {@link FalconGuard} innate also has a black falcon of the other type, raised the same way.
 * It is a second falcon, so it never replaces the Blitz Beat falcon of the same player.
 */
final class Falcons {
	private static final int CHECK_INTERVAL_TICKS = 10;
	/** The falcon each owner holds, by owner id. */
	private static final Map<UUID, UUID> BY_OWNER = new ConcurrentHashMap<>();
	/** The Falcon Guard falcon each player holds, by owner id. */
	private static final Map<UUID, UUID> GUARDS = new ConcurrentHashMap<>();

	private Falcons() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Falcons::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BY_OWNER.clear();
			GUARDS.clear();
		});
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && player.hasEffect(SpellStatus.BLITZ_BEAT)) {
				ensure(player);
			}
			if (player.isAlive() && !player.isSpectator() && FalconGuard.holds(player)) {
				ensureGuard(player);
			}
		}
		BY_OWNER.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null && !MobBlitz.tracks(entry.getKey()));
		GUARDS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** The falcon this owner holds, or null when it has none in the world. */
	static @Nullable FalconEntity of(final LivingEntity owner) {
		UUID id = BY_OWNER.get(owner.getUUID());
		if (id == null || !(owner.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getEntity(id) instanceof FalconEntity falcon && falcon.isAlive() ? falcon : null;
	}

	/** The Falcon Guard falcon this player holds, or null when it has none in the world. */
	static @Nullable FalconEntity guardOf(final ServerPlayer owner) {
		UUID id = GUARDS.get(owner.getUUID());
		if (id == null) {
			return null;
		}
		return owner.level().getEntity(id) instanceof FalconEntity falcon && falcon.isAlive() ? falcon : null;
	}

	/** Raises the Falcon Guard falcon of an archer if it has none in its level. */
	static @Nullable FalconEntity ensureGuard(final ServerPlayer owner) {
		FalconEntity held = guardOf(owner);
		if (held != null) {
			return held;
		}
		ServerLevel level = owner.level();
		FalconEntity falcon = SpellEntities.GUARD_FALCON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (falcon == null) {
			return null;
		}
		falcon.bind(owner);
		var at = FalconEntity.guardHover(owner, 0);
		falcon.snapTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
		if (!level.addFreshEntity(falcon)) {
			return null;
		}
		GUARDS.put(owner.getUUID(), falcon.getUUID());
		Spellbooks.LOGGER.info("[SPELL] event=FALCON_GUARD_RAISED owner={} falcon={}", owner.getUUID(), falcon.getUUID());
		return falcon;
	}

	/** Sends the Falcon Guard falcon of an archer at a foe it struck. */
	static void swoopGuard(final ServerPlayer owner, final Entity foe) {
		FalconEntity falcon = ensureGuard(owner);
		if (falcon != null) {
			falcon.swoopAt(foe);
		}
	}

	/** Drops the record of a mob owner that is gone. */
	static void forget(final UUID owner) {
		BY_OWNER.remove(owner);
	}

	/** Raises the falcon of a buffed owner if it has none in its level. */
	static @Nullable FalconEntity ensure(final LivingEntity owner) {
		FalconEntity held = of(owner);
		if (held != null) {
			return held;
		}
		if (!(owner.level() instanceof ServerLevel level)) {
			return null;
		}
		FalconEntity falcon = SpellEntities.FALCON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (falcon == null) {
			return null;
		}
		falcon.bind(owner);
		var at = FalconEntity.hover(owner, 0);
		falcon.snapTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
		if (!level.addFreshEntity(falcon)) {
			return null;
		}
		BY_OWNER.put(owner.getUUID(), falcon.getUUID());
		Spellbooks.LOGGER.info("[SPELL] event=FALCON_RAISED owner={} falcon={}", owner.getUUID(), falcon.getUUID());
		return falcon;
	}

	/** Sends the owner falcon at a foe a strike hit; raises the falcon first when it is missing. */
	static void swoop(final LivingEntity owner, final Entity foe) {
		FalconEntity falcon = ensure(owner);
		if (falcon != null) {
			falcon.swoopAt(foe);
		}
	}
}
