package com.stella.equips.readout;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.power.PowerLog;
import com.stella.equips.power.PowerTracker;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Sends each player one payload for the mob they look at. The client draws the plate, so the
 * server holds no per-mob entity and a second player never sees the first player's target.
 */
public final class ReadoutService {
	/** The look test runs this often; a target change shows within this many ticks. */
	public static final int LOOK_INTERVAL_TICKS = 2;
	private static final Map<UUID, ReadoutOwner> OWNERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> OPT_OUT = new ConcurrentHashMap<>();
	// Attackers to rescan, with the last server tick to rescan them on.
	private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
	/** After a player's hit, the attacker is rescanned every tick for this many ticks. */
	public static final int PENDING_TICKS = 3;
	private static int serverTicks;
	private static boolean registered;

	private ReadoutService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ReadoutPayload.TYPE, ReadoutPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ReadoutService::onServerTick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ReadoutService::afterDamage);
		ServerPlayConnectionEvents.DISCONNECT.register(
			(listener, server) -> forget(listener.getPlayer().getUUID())
		);
		registered = true;
	}

	public static boolean isRegistered() {
		return registered;
	}

	public static int serverTicks() {
		return serverTicks;
	}

	public static void onServerTick(final MinecraftServer server) {
		serverTicks++;
		PowerConfig config = PowerConfigStore.get();
		refreshPending(server, config);

		if (serverTicks % LOOK_INTERVAL_TICKS != 0) {
			return;
		}

		Set<UUID> online = ConcurrentHashMap.newKeySet();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
			scanPlayer(player, config);
		}

		for (UUID playerId : Set.copyOf(OWNERS.keySet())) {
			if (!online.contains(playerId)) {
				forget(playerId);
			}
		}
	}

	/** Finds the looked-at mob and sends a payload only when it differs from the last one sent. */
	public static void scanPlayer(final ServerPlayer player, final PowerConfig config) {
		ReadoutOwner owner = owner(player, config);

		if (!owner.enabled()) {
			clear(owner, player, "off");
			return;
		}

		Mob mob = ReadoutLook.find(player, config.readout().radius());

		if (mob == null) {
			clear(owner, player, "none");
			return;
		}

		ReadoutPayload next = payloadFor(mob, player, config);

		if (!next.equals(owner.current())) {
			ReadoutPayload previous = owner.current();
			owner.set(next, mob.getUUID());
			send(player, next);
			PowerLog.recorder().readoutTarget(
				player.getScoreboardName(), mob.getUUID().toString(), next.entityId(), next.threat(),
				next.bandValue().name(), next.hostile(), next.health(), next.absorption(),
				previous == null || previous.entityId() != next.entityId()
			);
		}
	}

	private static ReadoutPayload payloadFor(final Mob mob, final ServerPlayer player, final PowerConfig config) {
		int threat = PowerTracker.threat(mob, config);
		int gap = PowerCurve.normalizedThreat(threat, config) - PowerTracker.playerLevel(player, config);
		return ReadoutPayload.of(
			mob.getId(), threat, PowerCurve.band(gap, config), ReadoutRank.of(mob), hostile(mob, player),
			mob.getHealth(), mob.getMaxHealth(), mob.getAbsorptionAmount(), mob.getDisplayName().getString()
		);
	}

	/** Red when the mob is an enemy by nature, targets this viewer, or is angry at this viewer. */
	public static boolean hostile(final Mob mob, final ServerPlayer viewer) {
		return mob instanceof Enemy
			|| mob.getTarget() == viewer
			|| mob instanceof NeutralMob neutral && neutral.isAngryAt(viewer, viewer.level());
	}

	/** A player's hit makes a neutral mob angry on its next goal tick, so the attacker is rescanned for a few ticks. */
	private static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float base, final float taken, final boolean blocked
	) {
		if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer attacker) {
			ReadoutOwner owner = OWNERS.get(attacker.getUUID());
			if (owner != null && mob.getUUID().equals(owner.currentMobId())) {
				PENDING.put(attacker.getUUID(), serverTicks + PENDING_TICKS);
			}
		}
	}

	private static void refreshPending(final MinecraftServer server, final PowerConfig config) {
		if (PENDING.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Integer> entry : List.copyOf(PENDING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				PENDING.remove(entry.getKey());
				continue;
			}

			scanPlayer(player, config);

			if (serverTicks >= entry.getValue()) {
				PENDING.remove(entry.getKey());
			}
		}
	}

	public static ReadoutOwner owner(final ServerPlayer player, final PowerConfig config) {
		ReadoutOwner existing = OWNERS.get(player.getUUID());

		if (existing != null) {
			return existing;
		}

		return OWNERS.computeIfAbsent(
			player.getUUID(),
			id -> new ReadoutOwner(OPT_OUT.getOrDefault(id, config.readout().enabledByDefault()))
		);
	}

	public static ReadoutOwner owner(final ServerPlayer player) {
		return owner(player, PowerConfigStore.get());
	}

	public static ReadoutOwner online(final UUID playerId) {
		return OWNERS.get(playerId);
	}

	public static void setEnabled(final ServerPlayer player, final boolean enabled) {
		ReadoutOwner owner = owner(player);
		OPT_OUT.put(player.getUUID(), enabled);
		owner.setEnabled(enabled);

		if (!enabled) {
			clear(owner, player, "off");
		}
	}

	public static void forget(final UUID playerId) {
		OWNERS.remove(playerId);
		OPT_OUT.remove(playerId);
		PENDING.remove(playerId);
	}

	/** Sends one clear payload when a readout was up, and nothing otherwise. */
	private static void clear(final ReadoutOwner owner, final ServerPlayer player, final String reason) {
		if (owner.current() == null) {
			return;
		}

		int entityId = owner.current().entityId();
		owner.clear();
		send(player, ReadoutPayload.clear());
		PowerLog.recorder().readoutClear(player.getScoreboardName(), entityId, reason);
	}

	private static void send(final ServerPlayer player, final ReadoutPayload payload) {
		player.connection.send(ServerPlayNetworking.createClientboundPacket(payload));
	}
}
