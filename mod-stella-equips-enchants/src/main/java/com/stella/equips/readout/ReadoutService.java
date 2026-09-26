package com.stella.equips.readout;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.power.PowerLog;
import com.stella.equips.power.PowerTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps one ghost text display per mob per player. The display never joins the level, so the
 * vanilla tracking broadcast cannot reach a second player.
 */
public final class ReadoutService {
	private static final Map<UUID, ReadoutOwner> OWNERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> OPT_OUT = new ConcurrentHashMap<>();
	// Attacker and mob pairs to recheck, with the last server tick to recheck them on.
	private static final Map<PendingPair, Integer> PENDING = new ConcurrentHashMap<>();
	private static final int PENDING_TICKS = 3;
	private static int serverTicks;
	private static boolean registered;

	private ReadoutService() {
	}

	private record PendingPair(UUID player, UUID mob) {
	}

	public static void register() {
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

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncPositions(player, config);
		}

		if (serverTicks % config.tracker().scanIntervalTicks() != 0) {
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

	/**
	 * Moves every existing readout onto its mob. Only the owner's own readouts cost work, and a
	 * mob outside the radius holds no readout, so the scan stays bounded by the cap.
	 */
	private static void syncPositions(final ServerPlayer player, final PowerConfig config) {
		ReadoutOwner owner = OWNERS.get(player.getUUID());

		if (owner == null || !owner.enabled() || owner.readouts().isEmpty()) {
			return;
		}

		ServerLevel level = player.level();
		double heightOffset = config.readout().heightOffset();

		for (ReadoutOwner.Readout readout : owner.readouts().values()) {
			Entity entity = level.getEntity(readout.mobEntityId());

			if (!(entity instanceof Mob mob) || !mob.getUUID().equals(readout.mobId())) {
				continue;
			}

			Display.TextDisplay display = readout.display();
			double y = mob.getY() + mob.getBbHeight() + heightOffset;

			if (display.getX() == mob.getX() && display.getY() == y && display.getZ() == mob.getZ()) {
				continue;
			}

			display.setPos(mob.getX(), y, mob.getZ());
			player.connection.send(
				ClientboundTeleportEntityPacket.teleport(
					display.getId(), PositionMoveRotation.of(display), Set.of(), display.onGround()
				)
			);
		}
	}

	public static void scanPlayer(final ServerPlayer player, final PowerConfig config) {
		ReadoutOwner owner = owner(player, config);

		if (!owner.enabled()) {
			clear(owner, player, "off");
			return;
		}

		double radius = config.readout().radius();
		int cap = config.readout().maxPerPlayer();
		int playerLevel = PowerTracker.playerLevel(player, config);
		ServerLevel level = player.level();
		AABB bounds = player.getBoundingBox().inflate(radius);
		List<Mob> inRange = new ArrayList<>();
		Set<UUID> reachable = ConcurrentHashMap.newKeySet();

		for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds, ReadoutService::isThreat)) {
			if (horizontalDistance(player, mob) <= radius) {
				inRange.add(mob);
				reachable.add(mob.getUUID());
			}
		}

		inRange.sort(
			Comparator.comparingDouble((Mob mob) -> horizontalDistance(player, mob))
				.thenComparing(mob -> mob.getUUID().toString())
		);
		List<Mob> selected = inRange.size() > cap ? List.copyOf(inRange.subList(0, cap)) : inRange;
		Set<UUID> keep = ConcurrentHashMap.newKeySet();

		for (Mob mob : selected) {
			keep.add(mob.getUUID());
		}

		for (ReadoutOwner.Readout readout : List.copyOf(owner.readouts().values())) {
			if (!keep.contains(readout.mobId())) {
				remove(owner, player, readout, reasonFor(readout, level, reachable));
			}
		}

		for (Mob mob : selected) {
			int threat = PowerTracker.threat(mob, config);
			int gap = PowerCurve.normalizedThreat(threat, config) - playerLevel;
			PowerCurve.Band band = PowerCurve.band(gap, config);
			ReadoutOwner.Readout existing = owner.get(mob.getUUID());

			if (existing == null) {
				boolean hostile = hostile(mob, player);
				spawn(owner, player, mob, threat, playerLevel, gap, band, textFor(mob, threat, band, hostile), hostile, config);
			} else {
				refresh(owner, player, existing, mob, threat, playerLevel, gap, band);
			}
		}
	}

	/** Red when the mob is an enemy by nature, targets this viewer, or is angry at this viewer. */
	public static boolean hostile(final Mob mob, final ServerPlayer viewer) {
		return mob instanceof Enemy
			|| mob.getTarget() == viewer
			|| mob instanceof NeutralMob neutral && neutral.isAngryAt(viewer, viewer.level());
	}

	private static Component textFor(final Mob mob, final int threat, final PowerCurve.Band band, final boolean hostile) {
		return ReadoutText.build(
			threat, ReadoutRank.of(mob), mob.getDisplayName(), band, mob.getHealth(), mob.getMaxHealth(), hostile
		);
	}

	private static void refresh(
		final ReadoutOwner owner,
		final ServerPlayer player,
		final ReadoutOwner.Readout existing,
		final Mob mob,
		final int threat,
		final int playerLevel,
		final int gap,
		final PowerCurve.Band band
	) {
		boolean hostile = hostile(mob, player);
		Component text = textFor(mob, threat, band, hostile);
		if (existing.band() != band || existing.threat() != threat || existing.hostile() != hostile
			|| !existing.text().equals(text.getString())) {
			update(owner, player, existing, mob, threat, playerLevel, gap, band, text, hostile);
		}
	}

	/** A player's hit makes a neutral mob angry on its next goal tick, so the pair is rechecked for a few ticks. */
	private static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float base, final float taken, final boolean blocked
	) {
		if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer attacker) {
			ReadoutOwner owner = OWNERS.get(attacker.getUUID());
			if (owner != null && owner.get(mob.getUUID()) != null) {
				PENDING.put(new PendingPair(attacker.getUUID(), mob.getUUID()), serverTicks + PENDING_TICKS);
			}
		}
	}

	private static void refreshPending(final MinecraftServer server, final PowerConfig config) {
		if (PENDING.isEmpty()) {
			return;
		}

		for (Map.Entry<PendingPair, Integer> entry : List.copyOf(PENDING.entrySet())) {
			PendingPair pair = entry.getKey();
			ServerPlayer player = server.getPlayerList().getPlayer(pair.player());
			ReadoutOwner owner = player == null ? null : OWNERS.get(pair.player());
			ReadoutOwner.Readout existing = owner == null ? null : owner.get(pair.mob());
			Entity entity = existing == null ? null : player.level().getEntity(existing.mobEntityId());

			if (entity instanceof Mob mob && mob.getUUID().equals(pair.mob()) && owner.enabled()) {
				int playerLevel = PowerTracker.playerLevel(player, config);
				int threat = PowerTracker.threat(mob, config);
				int gap = PowerCurve.normalizedThreat(threat, config) - playerLevel;
				refresh(owner, player, existing, mob, threat, playerLevel, gap, PowerCurve.band(gap, config));
			}

			if (entity == null || serverTicks >= entry.getValue()) {
				PENDING.remove(pair);
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
		ReadoutOwner owner = OWNERS.remove(playerId);
		OPT_OUT.remove(playerId);

		if (owner != null) {
			owner.clear();
		}
	}

	private static boolean isThreat(final Mob mob) {
		return mob.isAlive() && !mob.isRemoved() && !mob.isDeadOrDying();
	}

	private static double horizontalDistance(final ServerPlayer player, final Mob mob) {
		return Math.hypot(mob.getX() - player.getX(), mob.getZ() - player.getZ());
	}

	private static void spawn(
		final ReadoutOwner owner,
		final ServerPlayer player,
		final Mob mob,
		final int threat,
		final int playerLevel,
		final int gap,
		final PowerCurve.Band band,
		final Component text,
		final boolean hostile,
		final PowerConfig config
	) {
		ServerLevel level = player.level();
		Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		display.setId(level.getNextEntityId());
		display.setPos(
			mob.getX(),
			mob.getY() + mob.getBbHeight() + config.readout().heightOffset(),
			mob.getZ()
		);
		display.setViewRange((float) config.readout().viewRange());
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setText(text);
		display.updateDataBeforeSync();
		player.connection.send(addPacket(display));
		player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), display.getEntityData().getNonDefaultValues()));
		owner.put(new ReadoutOwner.Readout(mob.getUUID(), mob.getId(), display, threat, band, text.getString(), hostile));
		PowerLog.recorder().readoutSpawn(
			player.getScoreboardName(), mob.getUUID().toString(), mob.getDisplayName().getString(),
			display.getId(), threat, playerLevel, gap, band.name(), ReadoutText.colorName(band), text.getString()
		);
	}

	private static void update(
		final ReadoutOwner owner,
		final ServerPlayer player,
		final ReadoutOwner.Readout existing,
		final Mob mob,
		final int threat,
		final int playerLevel,
		final int gap,
		final PowerCurve.Band band,
		final Component text,
		final boolean hostile
	) {
		Display.TextDisplay display = existing.display();
		display.setText(text);
		player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), display.getEntityData().packDirty()));
		owner.put(new ReadoutOwner.Readout(mob.getUUID(), mob.getId(), display, threat, band, text.getString(), hostile));
		if (existing.hostile() != hostile) {
			PowerLog.recorder().readoutHostility(
				player.getScoreboardName(), mob.getUUID().toString(), display.getId(), existing.hostile(), hostile
			);
		}
		PowerLog.recorder().readoutBand(
			player.getScoreboardName(), mob.getUUID().toString(), display.getId(), gap,
			existing.band().name(), band.name(), threat, playerLevel, text.getString()
		);
	}

	private static void remove(
		final ReadoutOwner owner,
		final ServerPlayer player,
		final ReadoutOwner.Readout readout,
		final String reason
	) {
		owner.remove(readout.mobId());
		player.connection.send(new ClientboundRemoveEntitiesPacket(readout.display().getId()));
		PowerLog.recorder().readoutRemove(
			player.getScoreboardName(), readout.mobId().toString(), readout.display().getId(), reason,
			owner.size()
		);
	}

	private static void clear(final ReadoutOwner owner, final ServerPlayer player, final String reason) {
		for (ReadoutOwner.Readout readout : List.copyOf(owner.readouts().values())) {
			remove(owner, player, readout, reason);
		}
	}

	private static String reasonFor(
		final ReadoutOwner.Readout readout,
		final ServerLevel level,
		final Set<UUID> reachable
	) {
		if (reachable.contains(readout.mobId())) {
			return "cap";
		}

		Entity entity = level.getEntity(readout.mobEntityId());

		if (entity instanceof Mob mob && mob.getUUID().equals(readout.mobId()) && isThreat(mob)) {
			return "range";
		}

		return "dead";
	}

	private static ClientboundAddEntityPacket addPacket(final Display.TextDisplay display) {
		return new ClientboundAddEntityPacket(
			display.getId(),
			display.getUUID(),
			display.getX(),
			display.getY(),
			display.getZ(),
			display.getXRot(),
			display.getYRot(),
			EntityTypes.TEXT_DISPLAY,
			0,
			Vec3.ZERO,
			display.getYHeadRot()
		);
	}
}
