package com.stella.equips.enchant;

import com.mojang.serialization.MapCodec;
import com.stella.equips.StellaEquips;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Undertow moves the owner and the victim to their pre-hit midpoint, one block apart. A hit only queues
 * the move; the level's end tick moves both or neither.
 */
public final class UndertowPull implements EnchantmentEntityEffect {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "undertow_pull");
	/** The two actors end this far apart, and a pair already this close does not move. */
	public static final double GAP = 1.0;
	public static final double HALF_GAP = GAP / 2.0;
	public static final UndertowPull INSTANCE = new UndertowPull();
	public static final MapCodec<UndertowPull> CODEC = MapCodec.unit(INSTANCE);

	public interface Recorder {
		default void queued(final UUID owner, final UUID victim, final double length) {
		}

		default void cancelled(final UUID owner, final UUID victim, final double length) {
		}

		default void moved(final UUID owner, final UUID victim, final Vec3 ownerEnd, final Vec3 victimEnd) {
		}

		default void skipped(final UUID owner, final UUID victim, final String reason) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	// Pending moves per level, keyed by owner and victim so the latest hit on a pair wins.
	private static final Map<ResourceKey<Level>, Map<Pair, Request>> QUEUE = new HashMap<>();

	private record Pair(UUID owner, UUID victim) {
	}

	private record Request(Vec3 ownerStart, Vec3 victimStart) {
	}

	private UndertowPull() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE, ID, CODEC);
		ServerTickEvents.END_LEVEL_TICK.register(UndertowPull::drain);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	@Override
	public void apply(
		final ServerLevel level, final int enchantmentLevel, final EnchantedItemInUse item, final Entity victim, final Vec3 origin
	) {
		LivingEntity owner = item.owner();
		if (owner == null || owner == victim) {
			return;
		}

		Pair pair = new Pair(owner.getUUID(), victim.getUUID());
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		double length = victimStart.subtract(ownerStart).length();
		Map<Pair, Request> pending = QUEUE.computeIfAbsent(level.dimension(), key -> new LinkedHashMap<>());
		if (!Double.isFinite(length) || length <= GAP) {
			pending.remove(pair);
			recorder.cancelled(pair.owner(), pair.victim(), length);
			return;
		}

		pending.put(pair, new Request(ownerStart, victimStart));
		recorder.queued(pair.owner(), pair.victim(), length);
	}

	@Override
	public MapCodec<UndertowPull> codec() {
		return CODEC;
	}

	private static void drain(final ServerLevel level) {
		Map<Pair, Request> pending = QUEUE.remove(level.dimension());
		if (pending == null) {
			return;
		}

		for (Map.Entry<Pair, Request> entry : pending.entrySet()) {
			Pair pair = entry.getKey();
			String reason = move(level, pair, entry.getValue());
			if (reason != null) {
				recorder.skipped(pair.owner(), pair.victim(), reason);
			}
		}
	}

	/** Moves both actors and returns null, or returns why neither moved. */
	private static String move(final ServerLevel level, final Pair pair, final Request request) {
		Entity owner = level.getEntity(pair.owner());
		Entity victim = level.getEntity(pair.victim());
		if (owner == null || victim == null || !owner.isAlive() || !victim.isAlive()) {
			return "stale";
		}

		if (owner.isPassenger() || owner.isVehicle() || victim.isPassenger() || victim.isVehicle()) {
			return "mounted";
		}

		Vec3 offset = request.victimStart().subtract(request.ownerStart());
		double length = offset.length();
		if (!Double.isFinite(length) || length <= GAP) {
			return "short";
		}

		Vec3 unit = offset.scale(1.0 / length);
		Vec3 midpoint = request.ownerStart().add(request.victimStart()).scale(0.5);
		Vec3 ownerEnd = midpoint.subtract(unit.scale(HALF_GAP));
		Vec3 victimEnd = midpoint.add(unit.scale(HALF_GAP));
		AABB ownerBox = owner.getBoundingBox().move(ownerEnd.subtract(owner.position()));
		AABB victimBox = victim.getBoundingBox().move(victimEnd.subtract(victim.position()));
		if (!clearPath(level, owner, ownerEnd) || !clearPath(level, victim, victimEnd)) {
			return "blocked_path";
		}

		if (!level.noCollision(owner, ownerBox) || !level.noCollision(victim, victimBox)
			|| !level.getWorldBorder().isWithinBounds(ownerBox) || !level.getWorldBorder().isWithinBounds(victimBox)) {
			return "blocked_end";
		}

		if (ownerBox.intersects(victimBox)) {
			return "overlap";
		}

		place(owner, ownerEnd);
		place(victim, victimEnd);
		recorder.moved(pair.owner(), pair.victim(), ownerEnd, victimEnd);
		return null;
	}

	// The whole swept volume from where the actor stands to its end must be free of blocks.
	private static boolean clearPath(final ServerLevel level, final Entity entity, final Vec3 end) {
		Vec3 delta = end.subtract(entity.position());
		return level.noCollision(entity, entity.getBoundingBox().expandTowards(delta));
	}

	private static void place(final Entity entity, final Vec3 end) {
		entity.teleportTo(end.x, end.y, end.z);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.resetFallDistance();
	}
}
