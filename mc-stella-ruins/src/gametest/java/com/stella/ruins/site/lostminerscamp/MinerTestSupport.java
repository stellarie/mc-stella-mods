package com.stella.ruins.site.lostminerscamp;

import com.mojang.authlib.GameProfile;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinPiece;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import com.stella.ruins.world.RuinVaultBlockEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the camp's tests: a capture that needs no
 * world, and an arena drawn in forced chunks with a keeper.
 */
final class MinerTestSupport {
	static final long READY_NANOS = 120_000_000_000L;
	/** The tick limit of an arena test: the wall clock ends a stuck wait. */
	static final int ARENA_TICKS = 1_000_000;
	/** A test player takes hits of any size. */
	static final double TEST_HEALTH = 1_000_000.0;
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8 + 4);

	private MinerTestSupport() {
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[RUINS] test={} RESULT {}", test, line);
	}

	static RuinSite site() {
		return RuinSites.get(LostMinersCampSite.ID);
	}

	static RuinFrame frame(final long seed, final int turns) {
		return RuinFrame.of(site(), new BlockPos(5, 100, 9), Rotation.values()[turns], seed);
	}

	/** The camp drawn through one box into a map of world spots, and the markers it named. */
	record Captured(RuinFrame frame, Map<BlockPos, BlockState> blocks, Map<String, List<BlockPos>> markers) {
		/** The block at a local spot, or air when the camp leaves the spot to the world. */
		BlockState state(final int x, final int y, final int z) {
			return this.blocks.getOrDefault(this.frame.toWorld(x, y, z), Blocks.AIR.defaultBlockState());
		}

		BlockPos world(final int x, final int y, final int z) {
			return this.frame.toWorld(x, y, z);
		}

		List<BlockPos> markers(final String name) {
			return this.markers.getOrDefault(name, List.of());
		}
	}

	static Captured capture(final long seed, final int turns) {
		RuinFrame frame = frame(seed, turns);
		Map<BlockPos, BlockState> sink = new HashMap<>(1 << 14);
		LayoutContext ctx = LayoutContext.forCapture(frame, sink, frame.box());
		site().layout().draw(ctx);
		return new Captured(frame, sink, ctx.markers());
	}

	/** Walking spots of a drawn camp: passable twice high, over a solid block. */
	static final class Walk {
		private final Captured capture;
		private final Set<BlockPos> dug = new HashSet<>();

		Walk(final Captured capture) {
			this.capture = capture;
		}

		/** The collapse rubble the route had to dig out, one block at a time. */
		Set<BlockPos> dug() {
			return this.dug;
		}

		/** Rubble the plan drops into the tunnel. A walker with a pick clears it. */
		private boolean rubble(final int x, final int y, final int z) {
			BlockState state = this.capture.state(x, y, z);
			return state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE);
		}

		boolean passable(final int x, final int y, final int z) {
			if (rubble(x, y, z)) {
				this.dug.add(new BlockPos(x, y, z));
				return true;
			}
			BlockState state = this.capture.state(x, y, z);
			return state.isAir() || state.is(Blocks.RAIL) || state.is(Blocks.LADDER);
		}

		boolean valid(final int x, final int y, final int z) {
			BlockState below = this.capture.state(x, y - 1, z);
			boolean support = !below.isAir() && below.getFluidState().isEmpty() && !this.passable(x, y - 1, z);
			return support && passable(x, y, z) && passable(x, y + 1, z);
		}

		/** Every standing spot reachable from the start, one block up or down at a time. */
		Set<BlockPos> reach(final int sx, final int sy, final int sz) {
			Set<BlockPos> seen = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			if (valid(sx, sy, sz)) {
				BlockPos start = new BlockPos(sx, sy, sz);
				seen.add(start);
				queue.add(start);
			}
			int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!queue.isEmpty()) {
				BlockPos at = queue.poll();
				for (int[] step : around) {
					int nx = at.getX() + step[0];
					int nz = at.getZ() + step[1];
					for (int dy = -1; dy <= 1; dy++) {
						int ny = at.getY() + dy;
						if (dy == 1 && !passable(at.getX(), at.getY() + 2, at.getZ())) {
							continue;
						}
						BlockPos next = new BlockPos(nx, ny, nz);
						if (valid(nx, ny, nz) && seen.add(next)) {
							queue.add(next);
						}
					}
				}
			}
			return seen;
		}

		/** True when a standing spot beside the block is reached: for a block used from next to it. */
		boolean beside(final Set<BlockPos> seen, final BlockPos spot) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 0; dy++) {
						if (seen.contains(spot.offset(dx, dy, dz))) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	/** A player joined through the player list, with the channel its packets land in. */
	record Joined(ServerPlayer player, EmbeddedChannel channel) {
	}

	/** The overlay and chat lines a joined player was sent since the last call. */
	static List<String> messages(final Joined joined) {
		List<String> lines = new ArrayList<>();
		Object packet;
		while ((packet = joined.channel().readOutbound()) != null) {
			if (packet instanceof ClientboundSystemChatPacket chat) {
				lines.add(chat.content().getString());
			}
		}
		return lines;
	}

	/** A player joined in the test's own plot and ticked every tick, so it behaves like a real one. */
	static Joined join(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "camp-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		helper.onEachTick(() -> {
			if (!player.isRemoved()) {
				player.doTick();
			}
		});
		return new Joined(player, channel);
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	static final class Arena {
		final ServerLevel level;
		final RuinSite site;
		final RuinFrame frame;
		final RuinPiece piece;
		final List<ChunkPos> forced;
		final Map<String, List<BlockPos>> marks;
		final List<Joined> joined = new ArrayList<>();
		final CampPlan plan;

		Arena(final ServerLevel level, final RuinSite site, final RuinFrame frame, final RuinPiece piece, final List<ChunkPos> forced,
			final Map<String, List<BlockPos>> marks, final CampPlan plan) {
			this.level = level;
			this.site = site;
			this.frame = frame;
			this.piece = piece;
			this.forced = forced;
			this.marks = marks;
			this.plan = plan;
		}

		ServerLevel level() {
			return this.level;
		}

		RuinSite site() {
			return this.site;
		}

		RuinFrame frame() {
			return this.frame;
		}

		RuinPiece piece() {
			return this.piece;
		}

		CampPlan plan() {
			return this.plan;
		}

		BlockPos corePos() {
			return markers(RuinMarkers.CORE).get(0);
		}

		BlockPos vaultPos() {
			return markers(RuinMarkers.VAULT).get(0);
		}

		BlockPos lecternPos() {
			return markers(CampMarkers.LECTERN).get(0);
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

		/** The objective as the core last saved it. */
		JournalObjective objective() {
			JournalObjective objective = (JournalObjective) this.site.newObjective();
			objective.load(entry().objective());
			return objective;
		}

		/** A spot inside the tent, within the activation radius of the core. */
		Vec3 standAt() {
			BlockPos spot = markers(CampMarkers.TENT).get(0);
			return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}

		Joined join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "delver"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			EmbeddedChannel channel = new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(TEST_HEALTH);
			player.setHealth((float) TEST_HEALTH);
			player.teleportTo(this.level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
			Joined entry = new Joined(player, channel);
			this.joined.add(entry);
			return entry;
		}

		void move(final ServerPlayer player, final Vec3 at) {
			player.teleportTo(this.level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		}

		/** The overlay and chat lines the player was sent since the last call. */
		List<String> messages(final ServerPlayer player) {
			for (Joined entry : this.joined) {
				if (entry.player() == player) {
					return MinerTestSupport.messages(entry);
				}
			}
			return List.of();
		}

		/** A player stands at the tent and the core is pulsed once: the camp wakes. */
		Joined wake() {
			Joined entry = join(standAt());
			core().pulse(this.level);
			return entry;
		}

		/** Stops the roster mobs from moving or fighting. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		/** The roster mobs of one kind that live now. */
		List<Mob> rosterOf(final RuinMobKind kind) {
			List<Mob> mobs = new ArrayList<>();
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob && mob.isAlive() && RuinMobs.kindOf(mob) == kind) {
					mobs.add(mob);
				}
			}
			return mobs;
		}

		/** Kills every roster mob as a player's blows would, through the death hook. */
		int killRoster() {
			int killed = 0;
			for (UUID id : entry().roster()) {
				Entity mob = this.level.getEntity(id);
				if (mob instanceof Mob alive) {
					alive.hurtServer(this.level, this.level.damageSources().genericKill(), 10_000.0F);
					killed++;
				}
			}
			return killed;
		}

		void release() {
			for (Joined entry : new ArrayList<>(this.joined)) {
				this.level.getServer().getPlayerList().remove(entry.player());
			}
			this.joined.clear();
			RuinData.Entry entry = entry();
			if (entry != null) {
				for (UUID id : entry.roster()) {
					Entity mob = this.level.getEntity(id);
					if (mob != null) {
						mob.discard();
					}
				}
			}
			AABB area = AABB.of(this.frame.box()).inflate(48.0);
			for (Entity near : this.level.getEntities((Entity) null, area, RuinMobs::isRuinMob)) {
				near.discard();
			}
			RuinData.get(this.level).forget(corePos());
			this.forced.forEach(chunk -> this.level.setChunkForced(chunk.x(), chunk.z(), false));
		}

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
	}

	/** Draws the camp in fresh forced chunks at a seed and a turn. */
	static Arena arena(final ServerLevel level, final long seed, final Rotation rotation) {
		int n = NEXT.getAndIncrement();
		int x0 = 200_000 + (n % 4000) * 160;
		int z0 = 200_000 + (n / 4000) * 160;
		RuinSite site = site();
		RuinFrame probe = new RuinFrame(site.id(), new BlockPos(x0, 0, z0), rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
		BoundingBox box = probe.box();
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				if (level.setChunkForced(cx, cz, true)) {
					forced.add(new ChunkPos(cx, cz));
				}
			}
		}
		int ground = Placement.Probe.of(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState()).groundY(x0, z0);
		BlockPos min = new BlockPos(x0, ground + 1, z0);
		RuinFrame frame = new RuinFrame(site.id(), min, rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
		RuinPiece piece = new RuinPiece(frame);
		piece.build(level, frame.box());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), CampPlan.derive(RandomSource.create(seed)));
		RuinData.get(level).forget(arena.corePos());
		Joined keeper = arena.join(arena.standAt());
		keeper.player().setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/** Draws the camp and runs the body once the arena's chunks tick entities. */
	static void inArena(final GameTestHelper helper, final long seed, final ArenaBody body) {
		inArena(helper, seed, Rotation.NONE, body);
	}

	static void inArena(final GameTestHelper helper, final long seed, final Rotation rotation, final ArenaBody body) {
		Arena arena = arena(helper.getLevel(), seed, rotation);
		boolean[] started = {false};
		RuntimeException[] failure = {null};
		long begun = System.nanoTime();
		helper.succeedWhen(() -> guarded(helper, () -> {
			if (failure[0] != null) {
				// A failed body fails the test again without logging the same trace every tick.
				helper.fail("the test body threw " + failure[0]);
				return;
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

	/** A check that throws anything but an assertion error would kill the test server, so it becomes a failure. */
	static void guarded(final GameTestHelper helper, final Runnable check) {
		try {
			check.run();
		} catch (GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a camp test body threw", e);
			helper.fail("the test body threw " + e);
		}
	}

	static void awaitReady(final GameTestHelper helper, final Arena arena, final long startedNanos) {
		if (ready(arena)) {
			return;
		}
		if (System.nanoTime() - startedNanos > READY_NANOS) {
			helper.fail("the arena chunks did not tick entities within " + READY_NANOS / 1_000_000_000L + " s");
		}
		helper.assertTrue(false, "the arena chunks do not tick entities yet");
	}

	static boolean ready(final Arena arena) {
		for (ChunkPos chunk : arena.frameChunks()) {
			if (!arena.level().isPositionEntityTicking(new BlockPos(chunk.getMiddleBlockX(), 64, chunk.getMiddleBlockZ()))) {
				return false;
			}
		}
		return true;
	}

	/** A cow stands still and sunlight does not hurt it, so only the test deals damage. */
	static net.minecraft.world.entity.animal.cow.Cow cow(final GameTestHelper helper, final double x, final double z, final float health) {
		return cowAt(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)), health);
	}

	/** A cow stands still at a world spot, so only the test deals damage. */
	static net.minecraft.world.entity.animal.cow.Cow cowAt(final ServerLevel level, final Vec3 at, final float health) {
		net.minecraft.world.entity.animal.cow.Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cow.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		cow.snapTo(at.x, at.y, at.z);
		cow.setNoAi(true);
		cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.max(health, 20.0F));
		cow.setHealth(health);
		level.addFreshEntity(cow);
		return cow;
	}
}
