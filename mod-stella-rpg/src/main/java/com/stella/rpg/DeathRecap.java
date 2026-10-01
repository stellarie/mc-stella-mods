package com.stella.rpg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * After a player dies, chat lists the hits that led to the death: up to {@link #MAX_LINES} hits from the last
 * {@link #WINDOW_TICKS} ticks, newest last, and their total. Each line names the attacker, its level and rank when
 * the mobs mod knows them, the damage type, and the health the hit removed. {@link DamageLog} measures each hit.
 */
public final class DeathRecap {
	public static final int WINDOW_TICKS = 200;
	public static final int MAX_LINES = 6;

	/** One hit a player took: when, from whom, what kind, and the health it removed. */
	public record Hit(long tick, String attacker, String type, int lost) {
	}

	/** Test seam: the lines each recap sends. */
	@FunctionalInterface
	public interface Recorder {
		void recap(ServerPlayer player, List<String> lines);
	}

	private static final Map<UUID, Deque<Hit>> RECENT = new ConcurrentHashMap<>();
	private static volatile Recorder recorder = (player, lines) -> { };

	private DeathRecap() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				send(player);
			}
		});
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (player, lines) -> { } : newRecorder;
	}

	/** Called by {@link DamageLog} with the health one hit removed from a player. */
	static void record(final ServerPlayer player, final DamageSource source, final int lost) {
		record(player, new Hit(player.level().getGameTime(), attackerOf(player, source), source.getMsgId(), lost));
	}

	static void record(final ServerPlayer player, final Hit hit) {
		Deque<Hit> hits = RECENT.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		synchronized (hits) {
			hits.addLast(hit);
			prune(hits, hit.tick());
		}
	}

	/** The recap lines for a player at a tick, without sending them. */
	public static List<String> lines(final ServerPlayer player, final long now) {
		Deque<Hit> hits = RECENT.get(player.getUUID());
		List<String> lines = new ArrayList<>();
		if (hits == null) {
			return lines;
		}
		List<Hit> recent;
		synchronized (hits) {
			prune(hits, now);
			recent = new ArrayList<>(hits);
		}
		if (recent.isEmpty()) {
			return lines;
		}
		List<Hit> shown = recent.subList(Math.max(0, recent.size() - MAX_LINES), recent.size());
		int total = recent.stream().mapToInt(Hit::lost).sum();
		lines.add("Death recap, last " + (WINDOW_TICKS / 20) + " seconds:");
		for (Hit hit : shown) {
			double ago = (now - hit.tick()) / 20.0;
			lines.add(String.format(" %s - %s - %,d HP (%.1f s before)", hit.attacker(), hit.type(), hit.lost(), ago));
		}
		lines.add(String.format(" Total: %,d HP from %d hit%s", total, recent.size(), recent.size() == 1 ? "" : "s"));
		return lines;
	}

	private static void send(final ServerPlayer player) {
		List<String> lines = lines(player, player.level().getGameTime());
		RECENT.remove(player.getUUID());
		if (lines.isEmpty()) {
			return;
		}
		recorder.recap(player, lines);
		for (int i = 0; i < lines.size(); i++) {
			ChatFormatting color = i == 0 ? ChatFormatting.RED : i == lines.size() - 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
			player.sendSystemMessage(Component.literal(lines.get(i)).withStyle(color));
		}
	}

	private static void prune(final Deque<Hit> hits, final long now) {
		while (!hits.isEmpty() && now - hits.peekFirst().tick() > WINDOW_TICKS) {
			hits.removeFirst();
		}
	}

	private static String attackerOf(final ServerPlayer player, final DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == player) {
			return "Environment";
		}
		String name = attacker.getDisplayName().getString();
		if (attacker instanceof Mob mob && FabricLoader.getInstance().isModLoaded("mod-stella-mobs")) {
			return name + " (Lv " + MobsBridge.threat(mob) + (MobsBridge.eliteOrBoss(mob) ? ", elite" : "") + ")";
		}
		return name;
	}
}
