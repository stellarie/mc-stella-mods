package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The single raid instance: its state on the Overworld, its host and mode, where each run lies, and the
 * boons it hands out. Each run lies {@link #RUN_STRIDE} blocks from the last, so a reset leaves the old
 * run's land, and everything dropped on it, behind for good.
 */
public final class RaidInstance {
	public static final List<Holder<MobEffect>> PLAYER_BOONS = List.of(
		MobEffects.SPEED, MobEffects.HASTE, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.REGENERATION,
		MobEffects.RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.NIGHT_VISION, MobEffects.ABSORPTION,
		MobEffects.HEALTH_BOOST, MobEffects.SLOW_FALLING, MobEffects.LUCK
	);
	public static final int PLAYER_BOONS_EACH = 1;
	public static final List<Holder<MobEffect>> MOB_BOONS = List.of(
		MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.REGENERATION,
		MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST, MobEffects.FIRE_RESISTANCE
	);
	public static final int MOB_BOONS_EACH = 2;
	/** Blocks between one run's origin and the next; far past any view distance. */
	public static final int RUN_STRIDE = 2048;
	private static final int RUNS_PER_ROW = 1000;
	/** The arrival hall's floor height at a run's origin. */
	public static final int ARRIVAL_Y = 64;

	public static final AttachmentType<RaidState> STATE = AttachmentRegistry.createPersistent(StellaNether.id("raid_state"), RaidState.CODEC);

	/** The boons a player was handed on entering, and in which run. */
	public record Boons(int run, List<Holder<MobEffect>> effects) {
		public static final Codec<Boons> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("run").forGetter(Boons::run),
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().fieldOf("effects").forGetter(Boons::effects)
		).apply(instance, Boons::new));
	}

	public static final AttachmentType<Boons> PLAYER_BOONS_HELD = AttachmentRegistry.createPersistent(StellaNether.id("raid_boons"), Boons.CODEC);
	/** The run whose mode a mob was last boosted for, so a reloaded mob is not boosted twice. */
	public static final AttachmentType<Integer> MOB_BOOSTED = AttachmentRegistry.createPersistent(StellaNether.id("raid_boosted"), Codec.INT);

	private RaidInstance() {
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			boolean from = origin.dimension().equals(RaidTravel.RAID);
			boolean to = destination.dimension().equals(RaidTravel.RAID);
			if (to && !from) {
				grantBoons(player);
			} else if (from && !to) {
				takeBoons(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob instanceof Enemy && level.dimension().equals(RaidTravel.RAID)) {
				boostMob(level, mob);
			}
		});
	}

	public static RaidState state(final MinecraftServer server) {
		RaidState state = ((AttachmentTarget) server.overworld()).getAttached(STATE);
		return state == null ? RaidState.EMPTY : state;
	}

	static void set(final MinecraftServer server, final RaidState state) {
		((AttachmentTarget) server.overworld()).setAttached(STATE, state);
	}

	/** Closes the running raid and moves the next run to fresh land. */
	public static void reset(final MinecraftServer server, final String reason) {
		RaidState old = state(server);
		set(server, new RaidState(old.run() + 1, false, Optional.empty(), RaidMode.NORMAL, 0L));
		StellaNether.LOGGER.info("[NETHER] raid reset run={} next={} reason={}", old.run(), old.run() + 1, reason);
	}

	/** A player crossing in: into an empty raid they become its host with their mode; otherwise they join. */
	public static RaidState enter(final ServerPlayer player, final RaidMode requested) {
		MinecraftServer server = player.level().getServer();
		RaidState state = state(server);
		if (!state.active()) {
			state = new RaidState(state.run(), true, Optional.of(player.getUUID()), requested, 0L);
			set(server, state);
			StellaNether.LOGGER.info("[NETHER] raid opened run={} host={} mode={}", state.run(), player.getScoreboardName(), requested);
		}
		return state;
	}

	public static BlockPos origin(final int run) {
		return new BlockPos((run % RUNS_PER_ROW - RUNS_PER_ROW / 2) * RUN_STRIDE, 0, (run / RUNS_PER_ROW - RUNS_PER_ROW / 2) * RUN_STRIDE);
	}

	/** Where a player lands in the current run: on the hall's floor, facing its portal out. */
	public static BlockPos arrival(final MinecraftServer server) {
		return origin(state(server).run()).offset(0, ARRIVAL_Y, 1);
	}

	/** The bottom block of the current run's portal out. */
	public static BlockPos exitPortal(final MinecraftServer server) {
		return origin(state(server).run()).offset(0, ARRIVAL_Y + 1, -4);
	}

	/** Distinct effects drawn at random from a pool. */
	public static List<Holder<MobEffect>> draw(final List<Holder<MobEffect>> pool, final int count, final RandomSource random) {
		List<Holder<MobEffect>> left = new ArrayList<>(pool);
		List<Holder<MobEffect>> drawn = new ArrayList<>();
		for (int i = 0; i < count && !left.isEmpty(); i++) {
			drawn.add(left.remove(random.nextInt(left.size())));
		}
		return drawn;
	}

	private static MobEffectInstance lasting(final Holder<MobEffect> effect) {
		return new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, true);
	}

	static void grantBoons(final ServerPlayer player) {
		takeBoons(player);
		RaidState state = state(player.level().getServer());
		List<Holder<MobEffect>> boons = draw(PLAYER_BOONS, PLAYER_BOONS_EACH, player.getRandom());
		boons.forEach(effect -> player.addEffect(lasting(effect)));
		player.setAttached(PLAYER_BOONS_HELD, new Boons(state.run(), boons));
		StellaNether.LOGGER.debug("[NETHER] player={} boons={}", player.getScoreboardName(), boons);
	}

	/** Takes back only a lasting effect, so a potion of the same kind drunk outside is left alone. */
	static void takeBoons(final ServerPlayer player) {
		Boons held = player.removeAttached(PLAYER_BOONS_HELD);
		if (held == null) {
			return;
		}
		for (Holder<MobEffect> effect : held.effects()) {
			MobEffectInstance active = player.getEffect(effect);
			if (active != null && active.isInfiniteDuration()) {
				player.removeEffect(effect);
			}
		}
	}

	static void boostMob(final ServerLevel level, final Mob mob) {
		RaidState state = state(level.getServer());
		Integer boosted = mob.getAttached(MOB_BOOSTED);
		if (!state.active() || state.mode() != RaidMode.HARD || (boosted != null && boosted == state.run())) {
			return;
		}
		mob.setAttached(MOB_BOOSTED, state.run());
		// Undead refuse regeneration, so only effects this mob can hold are drawn.
		List<Holder<MobEffect>> pool = MOB_BOONS.stream().filter(effect -> mob.canBeAffected(lasting(effect))).toList();
		draw(pool, MOB_BOONS_EACH, mob.getRandom()).forEach(effect -> mob.addEffect(lasting(effect)));
	}
}
