package com.stella.ruins.world;

import com.mojang.authlib.GameProfile;
import com.stella.ruins.StellaRuins;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the world library's tests. An arena is a site drawn at a spot no earlier run has used, in chunks that
 * stay loaded until the test releases them. The test world keeps what earlier runs built, so a reused spot would
 * prove nothing.
 * <p>
 * A chunk forced in this tick holds its entities hidden until the server ticks it, and a forced chunk ticks entities only
 * with a player in range. So every arena holds a spectator keeper, and a test that spawns mobs waits in {@link #inArena}
 * until the arena's chunks tick entities.
 */
final class WorldTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);

	private WorldTestSupport() {
	}

	/** What a test does in its arena. */
	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	/** One drawn site, with the players a test joined to it. */
	record Arena(ServerLevel level, RuinSite site, RuinFrame frame, RuinPiece piece, List<ChunkPos> forced, Map<String, List<BlockPos>> marks, List<ServerPlayer> players) {
		BlockPos corePos() {
			return markers(RuinMarkers.CORE).get(0);
		}

		BlockPos vaultPos() {
			return markers(RuinMarkers.VAULT).get(0);
		}

		List<BlockPos> markers(final String name) {
			return this.marks.getOrDefault(name, List.of());
		}

		RuinCoreBlockEntity core() {
			return (RuinCoreBlockEntity) this.level.getBlockEntity(corePos());
		}

		RuinVaultBlockEntity vault() {
			return (RuinVaultBlockEntity) this.level.getBlockEntity(vaultPos());
		}

		RuinData.Entry entry() {
			return RuinData.get(this.level).entry(corePos());
		}

		RuinPhase phase() {
			RuinData.Entry entry = entry();
			return entry == null ? RuinPhase.DORMANT : entry.phase();
		}

		/** A spot at floor level beside the core. */
		Vec3 standAt(final int dx, final int dz) {
			BlockPos core = corePos();
			return new Vec3(core.getX() + 0.5 + dx, core.getY(), core.getZ() + 0.5 + dz);
		}

		/** A player joined through the player list, so the level counts them in {@code players()}. */
		ServerPlayer join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "delver"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(this.level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
			this.players.add(player);
			return player;
		}

		void leave(final ServerPlayer player) {
			this.level.getServer().getPlayerList().remove(player);
			this.players.remove(player);
		}

		/** Wakes the core: a player stands in its radius and the core is pulsed once. */
		ServerPlayer wake() {
			ServerPlayer player = join(standAt(3, 3));
			core().pulse(this.level);
			return player;
		}

		/** Stops the roster mobs from wandering or fighting, so a test sees only what it causes. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		/** Kills every living roster mob by a player's hand. Returns how many died. */
		int killRoster(final ServerPlayer player) {
			int killed = 0;
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.hurtServer(this.level, this.level.damageSources().playerAttack(player), 10_000.0F);
					killed++;
				}
			}
			return killed;
		}

		/** Wakes the core, freezes the roster and kills it. The player stays in the radius. */
		ServerPlayer finish(final GameTestHelper helper) {
			ServerPlayer player = wake();
			helper.assertTrue(phase() == RuinPhase.ACTIVE, "the core did not wake");
			freeze();
			killRoster(player);
			return player;
		}

		/** Every chunk the arena needs ticking: its box and a one chunk margin, whoever forced them. */
		List<ChunkPos> frameChunks() {
			BoundingBox box = this.frame.box();
			List<ChunkPos> all = new ArrayList<>();
			for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
				for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
					all.add(new ChunkPos(cx, cz));
				}
			}
			return all;
		}

		/** The site's box, a little wider, for item and mob queries. */
		AABB area() {
			return AABB.of(this.frame.box()).inflate(2.0);
		}

		void release() {
			for (ServerPlayer player : new ArrayList<>(this.players)) {
				leave(player);
			}
			RuinData.Entry entry = entry();
			if (entry != null) {
				for (UUID id : entry.roster()) {
					Entity mob = this.level.getEntity(id);
					if (mob != null) {
						mob.discard();
					}
				}
			}
			for (Entity near : this.level.getEntities((Entity) null, area().inflate(46.0), RuinMobs::isRuinMob)) {
				near.discard();
			}
			RuinData.get(this.level).forget(corePos());
			this.forced.forEach(chunk -> this.level.setChunkForced(chunk.x(), chunk.z(), false));
		}
	}

	/** Draws a site in fresh forced chunks. Entities in those chunks stay hidden for a tick or two. */
	static Arena arena(final ServerLevel level, final RuinSite site, final Rotation rotation) {
		int n = NEXT.getAndIncrement();
		return arena(level, site, rotation, 100_000 + (n % 4000) * 144, 100_000 + (n / 4000) * 144, n);
	}

	private static Arena arena(final ServerLevel level, final RuinSite site, final Rotation rotation, final int x0, final int z0, final int n) {
		RuinFrame probe = new RuinFrame(site.id(), new BlockPos(x0, 0, z0), rotation.ordinal(), 0L, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
		BoundingBox box = probe.box();
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				// Forced chunks are a set, not a count: keep only what this arena newly forced, so its release cannot unforce a neighbour.
				if (level.setChunkForced(cx, cz, true)) {
					forced.add(new ChunkPos(cx, cz));
				}
			}
		}
		int ground = Placement.Probe.of(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState()).groundY(x0, z0);
		BlockPos min = new BlockPos(x0, ground + 1, z0);
		RuinFrame frame = new RuinFrame(site.id(), min, rotation.ordinal(), 0x5EED0000L + n, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
		RuinPiece piece = new RuinPiece(frame);
		piece.build(level, frame.box());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>());
		RuinData.get(level).forget(arena.corePos());
		// A forced chunk ticks entities only with a player in range. A spectator keeps the arena ticking and never wakes a core.
		ServerPlayer keeper = arena.join(arena.standAt(0, 0));
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/**
	 * Draws a site and runs the body once the arena's chunks tick entities. The arena is released when the body ends,
	 * whether it passes or fails. A body that passes ends the test; one that throws fails it with that error.
	 */
	static void inArena(final GameTestHelper helper, final RuinSite site, final ArenaBody body) {
		runIn(helper, arena(helper.getLevel(), site, Rotation.NONE), body);
	}

	private static void runIn(final GameTestHelper helper, final Arena arena, final ArenaBody body) {
		boolean[] started = {false};
		RuntimeException[] failure = {null};
		long begun = System.nanoTime();
		helper.succeedWhen(() -> guarded(helper, () -> {
			if (failure[0] != null) {
				throw failure[0];
			}
			if (started[0]) {
				return;
			}
			awaitReady(helper, arena, begun);
			started[0] = true;
			try {
				body.run(arena);
			} catch (RuntimeException e) {
				failure[0] = e;
				throw e;
			} finally {
				arena.release();
			}
		}));
	}

	/**
	 * Runs a check that a test repeats each tick. A check that throws anything but an assertion error would kill the
	 * test server in the tick loop, so it is turned into a failure of that test.
	 */
	static void guarded(final GameTestHelper helper, final Runnable check) {
		try {
			check.run();
		} catch (net.minecraft.gametest.framework.GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a test body threw", e);
			helper.fail("the test body threw " + e);
		}
	}

	/** The wall-clock wait for chunk loading. Ticks run fast when the server is behind, so ticks cannot bound a chunk load. */
	static final long READY_NANOS = 120_000_000_000L;

	/** Fails the test only when the arena chunks stay unready past the wall-clock deadline. */
	static void awaitReady(final GameTestHelper helper, final Arena arena, final long startedNanos) {
		if (ready(arena)) {
			return;
		}
		if (System.nanoTime() - startedNanos > READY_NANOS) {
			helper.fail("the arena chunks did not tick entities within " + READY_NANOS / 1_000_000_000L + " s");
		}
		long ticking = arena.frameChunks().stream().filter(c -> arena.level().isPositionEntityTicking(new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ()))).count();
		ServerPlayer keeper = arena.players().isEmpty() ? null : arena.players().get(0);
		helper.assertTrue(false, "the arena chunks do not tick entities yet: " + ticking + "/" + arena.frameChunks().size() + " tick, keeper "
			+ (keeper == null ? "none" : "in level " + arena.level().players().contains(keeper) + " at " + keeper.blockPosition().toShortString() + " removed " + keeper.isRemoved()));
	}

	static boolean ready(final Arena arena) {
		for (ChunkPos chunk : arena.frameChunks()) {
			if (!arena.level().isPositionEntityTicking(new BlockPos(chunk.getMiddleBlockX(), 64, chunk.getMiddleBlockZ()))) {
				return false;
			}
		}
		return true;
	}

	/** A chunk no earlier run has used. */
	static ChunkPos freshChunk() {
		int n = NEXT.getAndIncrement();
		return new ChunkPos(((100_000 + (n % 4000) * 144) >> 4) + 4, ((100_000 + (n / 4000) * 144) >> 4) + 4);
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[RUINS] test={} RESULT {}", test, line);
	}
}
