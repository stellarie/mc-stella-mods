package com.stella.mobs;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A flat night floor, 56 blocks square, with every chunk under it forced. The floor of `Arena` is 8 blocks wide and
 * ends before a ranged unit's range does. A test on this floor sets `padding = WideArena.PADDING`, so no
 * neighbouring test sits inside the follow range of its mobs or players.
 *
 * <p>The arena also records every projectile a watched mob looses at the tick it appears, with the distance from the
 * mob to its target at that tick. A recorded set cannot admit a neighbour's projectile and cannot lose its own.
 */
final class WideArena {
	static final int SIZE = 56;
	static final int PADDING = 64;
	/** Two blocks above the floor, so a mob lands and registers on the ground. */
	static final int SPAWN_Y = 3;
	static final float HEALTH = 10_000.0F;
	/** Ticks a test waits for its chunks to tick entities before it fails. */
	private static final int READY_TICKS = 300;
	private static final AtomicInteger PLAYER_SERIAL = new AtomicInteger();
	private static final Map<UUID, Watch> WATCHED = new ConcurrentHashMap<>();
	private static boolean installed;

	/** One projectile as it appeared. `ownerTick` is the shooter's own tick count. */
	record Shot(
		int ownerTick, double distance, Vec3 ownerEye, Vec3 spawnAt, Vec3 targetAt, Vec3 targetMotion, Vec3 velocity, Projectile projectile
	) {
	}

	private record Watch(Supplier<LivingEntity> target, List<Shot> shots) {
	}

	private final GameTestHelper helper;
	private final List<Entity> spawned = new ArrayList<>();
	private final List<ChunkPos> forced = new ArrayList<>();
	private boolean finished;

	private WideArena(final GameTestHelper helper) {
		this.helper = helper;
	}

	/**
	 * Builds the floor and forces its chunks. Call it from the test body: a tick hook added from a delayed callback
	 * corrupts the framework's tick table.
	 */
	static WideArena build(final GameTestHelper helper) {
		install();
		helper.setTime(Arena.NIGHT_TIME);
		// A peaceful level drops every player target.
		// Every test shares the setting.
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		WideArena arena = new WideArena(helper);
		ServerLevel level = helper.getLevel();
		BlockPos low = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos high = helper.absolutePos(new BlockPos(SIZE, 0, SIZE));
		// An edge chunk stopped ticking entities.
		// A margin chunk keeps it ticking.
		for (int chunkX = (Math.min(low.getX(), high.getX()) >> 4) - 1; chunkX <= (Math.max(low.getX(), high.getX()) >> 4) + 1; chunkX++) {
			for (int chunkZ = (Math.min(low.getZ(), high.getZ()) >> 4) - 1; chunkZ <= (Math.max(low.getZ(), high.getZ()) >> 4) + 1; chunkZ++) {
				level.setChunkForced(chunkX, chunkZ, true);
				arena.forced.add(new ChunkPos(chunkX, chunkZ));
			}
		}

		helper.onEachTick(arena::holdChunks);

		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				// The lower layers fill the world's air pocket.
				helper.setBlock(x, -2, z, Blocks.STONE);
				helper.setBlock(x, -1, z, Blocks.STONE);
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}

		return arena;
	}

	private static synchronized void install() {
		if (installed) {
			return;
		}

		installed = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Projectile projectile) || !(projectile.getOwner() instanceof Mob owner)) {
				return;
			}

			Watch watch = WATCHED.get(owner.getUUID());
			LivingEntity target = watch == null ? null : watch.target().get();
			if (target == null) {
				return;
			}

			watch.shots().add(new Shot(
				owner.tickCount, owner.distanceTo(target), owner.getEyePosition(), projectile.position(),
				target.getBoundingBox().getCenter(), target.getKnownMovement(), projectile.getDeltaMovement(), projectile
			));
		});
	}

	/** Starts recording what `shooter` looses. The distance of each shot is read against `target` at that moment. */
	static void watch(final Mob shooter, final Supplier<LivingEntity> target) {
		WATCHED.put(shooter.getUUID(), new Watch(target, new CopyOnWriteArrayList<>()));
	}

	/** Every projectile `shooter` has loosed since `watch`, in order. */
	static List<Shot> shots(final Mob shooter) {
		Watch watch = WATCHED.get(shooter.getUUID());
		return watch == null ? List.of() : List.copyOf(watch.shots());
	}

	/** The point in the test's own frame, in the world's. */
	Vec3 at(final double x, final double y, final double z) {
		return this.helper.absoluteVec(new Vec3(x, y, z));
	}

	/**
	 * A mob of `type` on the floor at `x`, `z`, off the spawn rolls so its gear is exactly what `gear` gives it, and at
	 * level zero so its numbers do not depend on how far the arena sits from 0,0. `gear` runs before the mob loads,
	 * which is when the installer reads its hand.
	 */
	<T extends Mob> T spawn(final EntityType<T> type, final double x, final double z, final Consumer<T> gear) {
		T mob = type.create(this.helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = this.at(x, SPAWN_Y, z);
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		gear.accept(mob);
		this.helper.getLevel().addFreshEntity(mob);
		Arena.neutralThreat(mob);
		this.spawned.add(mob);
		return mob;
	}

	/** Adds a mob the test spawned itself, so `finish` removes it. */
	void track(final Entity entity) {
		this.spawned.add(entity);
	}

	/** A survival player a mob can legally target and hurt. It cannot die in a test, and a blow does not move it. */
	ServerPlayer survivalPlayer(final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
			new GameProfile(UUID.randomUUID(), "reach-player-" + PLAYER_SERIAL.incrementAndGet()), false
		);
		ServerPlayer player = new ServerPlayer(this.helper.getLevel().getServer(), this.helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		this.helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		// A real client reports that it has loaded.
		// Until then no mob can hurt or target it.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		player.setHealth(HEALTH);
		player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		Vec3 at = this.at(x, 1.0, z);
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	/** A hit leaves immunity frames, and 26.3 drops a target that carries them. Call it every tick. */
	static void keepValid(final LivingEntity target) {
		target.setInvulnerableTime(0);
	}

	/**
	 * Runs `populate` on the first tick when every spot stands in a chunk that ticks entities. A spawn into a chunk that
	 * does not tick would stand still. A chunk forced this tick joins some ticks later, so a callout from tick 0 reaches
	 * nobody. Registered from the test body, because a tick hook added from a delayed callback corrupts the framework.
	 */
	void whenReady(final List<Vec3> relativeSpots, final Runnable populate) {
		boolean[] done = new boolean[1];
		int[] waited = new int[1];
		this.helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}

			ServerLevel level = this.helper.getLevel();
			boolean ready = relativeSpots.stream().allMatch(spot -> level.isPositionEntityTicking(this.helper.absolutePos(BlockPos.containing(spot))));
			if (!ready) {
				if (++waited[0] > READY_TICKS) {
					this.helper.fail("the arena's chunks never ticked entities in " + READY_TICKS + " ticks");
				}

				return;
			}

			done[0] = true;
			try {
				populate.run();
			} catch (RuntimeException failure) {
				// A plain exception here stops the server.
				// A test failure stops only this test.
				this.helper.fail("the arena could not be set up: " + failure);
			}
		});
	}

	/**
	 * Forces again any chunk of the arena that another test released. A neighbour that frees a shared chunk stops every
	 * entity in it, so a mob that walks into that chunk freezes.
	 */
	private void holdChunks() {
		if (this.finished) {
			return;
		}

		ServerLevel level = this.helper.getLevel();
		for (ChunkPos chunk : this.forced) {
			if (!level.getForceLoadedChunks().contains(chunk.pack())) {
				level.setChunkForced(chunk.x(), chunk.z(), true);
				StellaMobs.LOGGER.info("[BEHAVIOR] test=wideArena REFORCE chunk={}", chunk);
			}
		}
	}

	/** True when the arena forced this chunk. */
	boolean holds(final ChunkPos chunk) {
		return this.forced.contains(chunk);
	}

	/** Removes what the test spawned and frees its chunks, so a finished test leaves no armed mob behind. */
	void finish() {
		this.finished = true;
		this.spawned.forEach(Entity::discard);
		this.forced.forEach(chunk -> this.helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false));
	}
}
