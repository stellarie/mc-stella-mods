package com.stella.rpg.net;

import com.stella.rpg.Stat;
import com.stella.rpg.StatPoints;
import com.stella.rpg.WeaponSlot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class RpgNet {
	private static final Map<UUID, StatsSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

	private RpgNet() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(StatsSyncPayload.TYPE, StatsSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AllocatePayload.TYPE, AllocatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AllocateBatchPayload.TYPE, AllocateBatchPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RespecPayload.TYPE, RespecPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WeaponHotkeyPayload.TYPE, WeaponHotkeyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
			AllocatePayload.TYPE, (payload, context) -> allocate(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(
			AllocateBatchPayload.TYPE, (payload, context) -> allocateBatch(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(
			RespecPayload.TYPE, (payload, context) -> respec(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(
			WeaponHotkeyPayload.TYPE, (payload, context) -> WeaponSlot.press(context.player(), payload.index()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
	}

	public static StatsSyncPayload allocate(final ServerPlayer player, final AllocatePayload payload) {
		StatPoints.Result result;
		if (payload.stat() < 0 || payload.stat() >= Stat.values().length) {
			result = StatPoints.Result.INVALID_STAT;
		} else {
			result = StatPoints.allocate(player, Stat.values()[payload.stat()], payload.amount());
		}
		return reply(player, result);
	}

	public static StatsSyncPayload allocateBatch(final ServerPlayer player, final AllocateBatchPayload payload) {
		return reply(player, StatPoints.allocateAll(player, payload.amounts()));
	}

	public static StatsSyncPayload respec(final ServerPlayer player) {
		return reply(player, StatPoints.respec(player));
	}

	public static boolean sync(final ServerPlayer player) {
		return send(player, StatsSyncPayload.of(player));
	}

	public static void syncIfChanged(final ServerPlayer player) {
		StatsSyncPayload last = LAST_SENT.get(player.getUUID());
		if (last == null || !last.equals(StatsSyncPayload.of(player))) {
			sync(player);
		}
	}

	private static StatsSyncPayload reply(final ServerPlayer player, final StatPoints.Result result) {
		StatsSyncPayload payload = StatsSyncPayload.of(player, result);
		send(player, payload);
		return payload;
	}

	private static boolean send(final ServerPlayer player, final StatsSyncPayload payload) {
		if (!ServerPlayNetworking.canSend(player, StatsSyncPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		LAST_SENT.put(player.getUUID(), payload.withResult(StatPoints.Result.OK));
		return true;
	}
}
