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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps one ghost text display per mob per player. The display never joins the level, so the
 * vanilla tracking broadcast cannot reach a second player.
 */
public final class ReadoutService {
	private static final Map<UUID, ReadoutOwner> OWNERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> OPT_OUT = new ConcurrentHashMap<>();
	private static int serverTicks;
	private static boolean registered;

	private ReadoutService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ReadoutService::onServerTick);
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

		Mob targeted = ReadoutTarget.of(player, selected);

		for (Mob mob : selected) {
			int mobLevel = PowerTracker.mobLevel(mob, config);
			int gap = mobLevel - playerLevel;
			PowerCurve.Band band = PowerCurve.band(gap, config);
			Component text = mob == targeted
				? ReadoutText.build(mobLevel, mob.getDisplayName(), band, mob.getHealth(), mob.getMaxHealth())
				: ReadoutText.build(mobLevel, mob.getDisplayName(), band);
			ReadoutOwner.Readout existing = owner.get(mob.getUUID());

			if (existing == null) {
				spawn(owner, player, mob, mobLevel, playerLevel, gap, band, text, config);
			} else if (existing.band() != band || existing.mobLevel() != mobLevel
				|| !existing.text().equals(text.getString())) {
				update(owner, player, existing, mob, mobLevel, playerLevel, gap, band, text);
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
		final int mobLevel,
		final int playerLevel,
		final int gap,
		final PowerCurve.Band band,
		final Component text,
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
		owner.put(new ReadoutOwner.Readout(mob.getUUID(), mob.getId(), display, mobLevel, band, text.getString()));
		PowerLog.recorder().readoutSpawn(
			player.getScoreboardName(), mob.getUUID().toString(), mob.getDisplayName().getString(),
			display.getId(), mobLevel, playerLevel, gap, band.name(), ReadoutText.colorName(band), text.getString()
		);
	}

	private static void update(
		final ReadoutOwner owner,
		final ServerPlayer player,
		final ReadoutOwner.Readout existing,
		final Mob mob,
		final int mobLevel,
		final int playerLevel,
		final int gap,
		final PowerCurve.Band band,
		final Component text
	) {
		Display.TextDisplay display = existing.display();
		display.setText(text);
		player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), display.getEntityData().packDirty()));
		owner.put(new ReadoutOwner.Readout(mob.getUUID(), mob.getId(), display, mobLevel, band, text.getString()));
		PowerLog.recorder().readoutBand(
			player.getScoreboardName(), mob.getUUID().toString(), display.getId(), gap,
			existing.band().name(), band.name(), mobLevel, playerLevel, text.getString()
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
