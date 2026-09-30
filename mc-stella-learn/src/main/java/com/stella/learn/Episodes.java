package com.stella.learn;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Tracks each mob's running episode and turns its outcome into a reward. While an episode runs, the hit points the mob
 * takes count against the tactic it chose and the hit points it deals to that player count for it. When the window
 * closes, or the mob dies, the reward is what it dealt less what it took, and the player's policy moves toward it.
 */
public final class Episodes {
	/** Hit points deducted from a reward when the mob dies inside its window. */
	public static final double DEATH_PENALTY = 10.0;
	private static final Map<UUID, Episode> ACTIVE = new ConcurrentHashMap<>();

	private Episodes() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Episodes::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(Episodes::sweep);
	}

	/** Adds a hit to the running episode of the mob that took it, or of the mob that dealt it to the episode's player. */
	static void afterDamage(final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked) {
		if (damageTaken <= 0.0F) {
			return;
		}

		if (entity instanceof Mob mob) {
			Episode own = ACTIVE.get(mob.getUUID());
			if (own != null) {
				own.taken += damageTaken;
			}
		}

		if (entity instanceof ServerPlayer player && source.getEntity() instanceof Mob attacker) {
			Episode theirs = ACTIVE.get(attacker.getUUID());
			if (theirs != null && theirs.player.equals(player.getUUID())) {
				theirs.dealt += damageTaken;
			}
		}
	}

	public static @Nullable Episode active(final Mob mob) {
		return ACTIVE.get(mob.getUUID());
	}

	static Episode begin(final Mob mob, final ServerPlayer player, final String mobKind, final Situation situation, final Tactic tactic) {
		Episode episode = new Episode(mob.getUUID(), player.getUUID(), mobKind, situation, tactic, mob.tickCount);
		ACTIVE.put(mob.getUUID(), episode);
		return episode;
	}

	private static void sweep(final MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}

		int window = LearnConfigStore.get().rlWindowTicks();
		for (Episode episode : List.copyOf(ACTIVE.values())) {
			Mob mob = find(server, episode.mob);
			boolean died = mob == null || !mob.isAlive();
			if (died || mob.tickCount >= episode.startTick + window) {
				finish(server, episode, died);
			}
		}
	}

	private static @Nullable Mob find(final MinecraftServer server, final UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof Mob mob) {
				return mob;
			}
		}

		return null;
	}

	/** Ends an episode: the player's policy moves toward its reward. An offline player's episode is dropped, unlearned. */
	static void finish(final MinecraftServer server, final Episode episode, final boolean died) {
		ACTIVE.remove(episode.mob);
		ServerPlayer player = server.getPlayerList().getPlayer(episode.player);
		if (player == null) {
			return;
		}

		double reward = episode.dealt - episode.taken - (died ? DEATH_PENALTY : 0.0);
		Policy before = Policies.get(player);
		Policy after = before.with(episode.key(), reward, LearnConfigStore.get().rlAlpha());
		Policies.set(player, after);
		LearnLog.info("learnPolicy", "REWARD", "mob=" + episode.mobKind + " id=" + episode.mob + " " + LearnLog.who(player) + " situation=" + episode.situation
			+ " tactic=" + episode.tactic + " dealt=" + LearnLog.num(episode.dealt) + " taken=" + LearnLog.num(episode.taken) + " died=" + died
			+ " reward=" + LearnLog.num(reward) + " value=" + LearnLog.num(before.get(episode.key()).value()) + "->" + LearnLog.num(after.get(episode.key()).value())
			+ " tries=" + after.get(episode.key()).tries());
	}
}
