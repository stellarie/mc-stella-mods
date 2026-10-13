package com.stella.rpg.job;

import com.stella.rpg.RpgSpells;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StellaRpg;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Mage innate. The ward is an absorption shield of (MATK / 100) x {@link #SHIELD_SHARE} of max health. MATK is
 * the value of the Matk line on the stats screen. The ward refills {@link #REFILL_TICKS} ticks after the last hit.
 * When a hit breaks it, each hostile mob within {@link #BURST_RADIUS} blocks takes (MATK / 100) x {@link #BURST_SHARE}
 * of the max health of the mage, the same base as the shield. Small flames circle the feet while the shield holds.
 * The shield is the absorption of the player. A refill sets the absorption to the shield size and never lowers a
 * larger pool, so a ward neither stacks with the Absorption effect nor erases it.
 */
public final class ArcaneWard {
	public static final double SHIELD_SHARE = 0.10;
	public static final double BURST_SHARE = 0.05;
	public static final int REFILL_TICKS = 300;
	public static final double BURST_RADIUS = 3.0;
	private static final int RING_TICKS = 4;
	private static final int RING_POINTS = 10;
	private static final double RING_RADIUS = 0.8;
	private static final double RING_TURN = 0.35;
	private static final int PRUNE_TICKS = 200;
	private static final float EMPTY = 0.001F;

	private static final class State {
		int sinceHit = REFILL_TICKS;
		boolean hit;
		boolean held;
		int age;
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private ArcaneWard() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (amount > 0.0F && entity instanceof ServerPlayer player && JobInnates.has(player, Innate.ARCANE_WARD)) {
				STATES.computeIfAbsent(player.getUUID(), id -> new State()).hit = true;
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(ArcaneWard::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> STATES.remove(newPlayer.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	/** MATK divided by 100, as the stats screen shows it: INT and the MATK percent of the gear. */
	public static double matk(final ServerPlayer player) {
		return RpgSpells.matkPercent(StatBonuses.effective(player, Stat.INT), Affixes.total(player, Affix.MATK_PCT));
	}

	/** The size of the shield in health points, or 0 for a player without the ward. */
	public static float capacity(final ServerPlayer player) {
		if (!JobInnates.has(player, Innate.ARCANE_WARD)) {
			return 0.0F;
		}
		return (float) (SHIELD_SHARE * matk(player) * player.getMaxHealth());
	}

	private static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && JobInnates.has(player, Innate.ARCANE_WARD)) {
				tick(player, STATES.computeIfAbsent(player.getUUID(), id -> new State()));
			}
		}
		if (server.getTickCount() % PRUNE_TICKS == 0) {
			STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	private static void tick(final ServerPlayer player, final State state) {
		if (state.hit) {
			state.sinceHit = 0;
		} else if (state.sinceHit < REFILL_TICKS) {
			state.sinceHit++;
		}
		float absorption = player.getAbsorptionAmount();
		if (state.held && absorption <= EMPTY && state.hit) {
			burst(player);
		}
		state.hit = false;
		float capacity = capacity(player);
		if (capacity > 0.0F && state.sinceHit >= REFILL_TICKS && absorption + EMPTY < capacity) {
			player.setAbsorptionAmount(capacity);
			absorption = player.getAbsorptionAmount();
			StellaRpg.LOGGER.info("[BEHAVIOR] mod={} event=arcane_ward_refill owner={} shield={}", StellaRpg.MOD_ID, player.getUUID(), absorption);
		}
		state.held = absorption > EMPTY;
		if (state.held && state.age % RING_TICKS == 0) {
			JobInnates.ring(player.level(), ParticleTypes.SMALL_FLAME, player.position(), RING_RADIUS, RING_POINTS, state.age * RING_TURN);
		}
		state.age++;
	}

	private static void burst(final ServerPlayer player) {
		double matk = matk(player);
		if (matk <= 0.0) {
			return;
		}
		ServerLevel level = player.level();
		double damage = matk * BURST_SHARE * player.getMaxHealth();
		int hurt = 0;
		for (LivingEntity target : InnateTargets.around(player, BURST_RADIUS)) {
			if (InnateTargets.hostile(target) && InnateTargets.hurtBy(player, target, damage)) {
				hurt++;
			}
		}
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(0.5), player.getZ(), 24, 0.6, 0.4, 0.6, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
		StellaRpg.LOGGER.info("[BEHAVIOR] mod={} event=arcane_ward_break owner={} matk={} hurt={}", StellaRpg.MOD_ID, player.getUUID(), matk, hurt);
	}
}
