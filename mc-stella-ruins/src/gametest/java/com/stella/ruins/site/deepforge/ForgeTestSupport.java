package com.stella.ruins.site.deepforge;

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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the Deep Forge tests. A capture draws the forge into a map with no world. An arena draws it into
 * forced chunks at a spot no earlier run has used, and holds a spectator so the chunks tick entities.
 */
final class ForgeTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);
	static final long READY_NANOS = 120_000_000_000L;

	private ForgeTestSupport() {
	}

	static RuinSite site() {
		RuinSite site = RuinSites.get(DeepForgeSite.ID);
		if (site == null) {
			throw new IllegalStateException("the deep_forge site is not registered");
		}
		return site;
	}

	static RuinFrame frame(final long seed, final int turns) {
		return RuinFrame.of(site(), new BlockPos(5, 100, 9), Rotation.values()[turns], seed);
	}

	/** Draws the whole forge through one box into a map of world positions, and returns the markers it named. */
	record Capture(RuinFrame frame, Map<BlockPos, BlockState> blocks, Map<String, List<BlockPos>> markers) {
		BlockState at(final int x, final int y, final int z) {
			return this.blocks.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
		}

		BlockPos local(final int x, final int y, final int z) {
			return this.frame.toWorld(x, y, z);
		}

		List<BlockPos> markers(final String name) {
			return this.markers.getOrDefault(name, List.of());
		}
	}

	static Capture capture(final long seed, final int turns) {
		RuinFrame frame = frame(seed, turns);
		Map<BlockPos, BlockState> sink = new HashMap<>(1 << 19);
		LayoutContext ctx = LayoutContext.forCapture(frame, sink, frame.box());
		site().layout().draw(ctx);
		return new Capture(frame, sink, ctx.markers());
	}

	/** The plan a seed rolls, in the frame the layout would use. */
	static Plan plan(final long seed) {
		RuinFrame frame = frame(seed, 0);
		return DeepForgeLayout.planOf(LayoutContext.forDryRun(frame));
	}

	// ------------------------------------------------------------------ walking

	/** A player's reach through the captured blocks: one block steps, no falls past one block. */
	static final class Walk {
		private final Capture capture;

		Walk(final Capture capture) {
			this.capture = capture;
		}

		boolean passable(final BlockState state) {
			return state.isAir() || state.is(Blocks.RAIL) || state.getBlock() instanceof ForgeLeverBlock;
		}

		private boolean support(final BlockState state) {
			return !passable(state) && state.getFluidState().isEmpty() && !state.is(Blocks.IRON_BARS) && !state.is(Blocks.LANTERN);
		}

		boolean valid(final int x, final int y, final int z) {
			return passable(this.capture.at(x, y, z)) && passable(this.capture.at(x, y + 1, z)) && support(this.capture.at(x, y - 1, z));
		}

		private static long key(final int x, final int y, final int z) {
			return ((long) x & 0xFFFFFL) | (((long) z & 0xFFFFFL) << 20) | (((long) y & 0xFFFFFL) << 40);
		}

		/** Every standing spot reachable from the start. */
		Set<Long> reach(final BlockPos start) {
			Set<Long> seen = new HashSet<>();
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			if (valid(start.getX(), start.getY(), start.getZ())) {
				seen.add(key(start.getX(), start.getY(), start.getZ()));
				queue.add(new int[] {start.getX(), start.getY(), start.getZ()});
			}
			int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				for (int[] d : around) {
					int nx = at[0] + d[0];
					int nz = at[2] + d[1];
					for (int dy = -1; dy <= 1; dy++) {
						int ny = at[1] + dy;
						if (dy == 1 && !passable(this.capture.at(at[0], at[1] + 2, at[2]))) {
							continue;
						}
						if (valid(nx, ny, nz) && seen.add(key(nx, ny, nz))) {
							queue.add(new int[] {nx, ny, nz});
						}
					}
				}
			}
			return seen;
		}

		boolean reached(final Set<Long> seen, final BlockPos spot) {
			return seen.contains(key(spot.getX(), spot.getY(), spot.getZ()));
		}

		/** True when some standing spot beside the block is reached: for a block the player uses from next to it. */
		boolean reachedBeside(final Set<Long> seen, final BlockPos block) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					if (seen.contains(key(block.getX() + d.getStepX(), block.getY() + dy, block.getZ() + d.getStepZ()))) {
						return true;
					}
				}
			}
			return false;
		}

		/** Counts the reached spots that stand beside lava with nothing between: an unguarded edge. */
		List<BlockPos> unguardedLava(final Set<Long> seen) {
			List<BlockPos> bad = new ArrayList<>();
			int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (long packed : seen) {
				int x = (int) ((packed & 0xFFFFFL) << 44 >> 44);
				int z = (int) (((packed >> 20) & 0xFFFFFL) << 44 >> 44);
				int y = (int) (((packed >> 40) & 0xFFFFFL) << 44 >> 44);
				for (int[] d : around) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (this.capture.at(nx, y - 1, nz).is(Blocks.LAVA) && passable(this.capture.at(nx, y, nz))) {
						bad.add(new BlockPos(x, y, z));
						break;
					}
				}
			}
			return bad;
		}
	}

	// ------------------------------------------------------------------ arenas

	/** One drawn forge, with the players a test joined to it. */
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

		/** A spot at core level in the hall, beside the core. */
		Vec3 standAt(final int dx, final int dz) {
			BlockPos core = corePos();
			return new Vec3(core.getX() + 0.5 + dx, core.getY(), core.getZ() + 0.5 + dz);
		}

		ServerPlayer join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "delver"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(this.level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
			this.players.add(player);
			return player;
		}

		ServerPlayer wake() {
			ServerPlayer player = join(standAt(3, 3));
			core().pulse(this.level);
			return player;
		}

		/** The roster mobs of one kind that are in the level now. */
		List<Mob> roster(final RuinMobKind kind) {
			List<Mob> found = new ArrayList<>();
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + kind.id())) {
					found.add(mob);
				}
			}
			return found;
		}

		/** Stops the roster mobs from wandering or fighting, so a test sees only what it causes. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		void kill(final Mob mob, final ServerPlayer player) {
			mob.hurtServer(this.level, this.level.damageSources().playerAttack(player), 10_000.0F);
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

		AABB area() {
			return AABB.of(this.frame.box()).inflate(2.0);
		}

		void release() {
			for (ServerPlayer player : new ArrayList<>(this.players)) {
				this.level.getServer().getPlayerList().remove(player);
				this.players.remove(player);
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

	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	/** Draws the forge in fresh forced chunks at a seed and a turn. The test world keeps what earlier runs built. */
	static Arena arena(final ServerLevel level, final long seed, final Rotation rotation) {
		int n = NEXT.getAndIncrement();
		int x0 = 400_000 + (n % 4000) * 160;
		int z0 = 400_000 + (n / 4000) * 160;
		RuinSite site = site();
		RuinFrame probe = new RuinFrame(site.id(), new BlockPos(x0, 0, z0), rotation.ordinal(), 0L, site.sizeX(), site.sizeY(), site.sizeZ(), 0);
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
		RuinFrame frame = new RuinFrame(site.id(), min, rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), 0);
		RuinPiece piece = new RuinPiece(frame);
		piece.build(level, frame.box());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>());
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.standAt(0, 0));
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	static void inArena(final GameTestHelper helper, final long seed, final ArenaBody body) {
		Arena arena = arena(helper.getLevel(), seed, Rotation.NONE);
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

	static void guarded(final GameTestHelper helper, final Runnable check) {
		try {
			check.run();
		} catch (GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a deep forge test body threw", e);
			helper.fail("the test body threw " + e);
		}
	}

	static void awaitReady(final GameTestHelper helper, final Arena arena, final long startedNanos) {
		boolean ready = true;
		for (ChunkPos chunk : arena.frameChunks()) {
			if (!arena.level().isPositionEntityTicking(new BlockPos(chunk.getMiddleBlockX(), 64, chunk.getMiddleBlockZ()))) {
				ready = false;
				break;
			}
		}
		if (ready) {
			return;
		}
		if (System.nanoTime() - startedNanos > READY_NANOS) {
			helper.fail("the arena chunks did not tick entities within " + READY_NANOS / 1_000_000_000L + " s");
		}
		helper.assertTrue(false, "the arena chunks do not tick entities yet");
	}

	static BlockHitResult hit(final BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}

	/** Uses a block with an item in the main hand, as a right click does. */
	static net.minecraft.world.InteractionResult use(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return level.getBlockState(pos).useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit(pos));
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[RUINS] test={} RESULT {}", test, line);
	}
}
