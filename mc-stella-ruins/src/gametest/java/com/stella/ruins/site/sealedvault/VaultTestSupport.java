package com.stella.ruins.site.sealedvault;

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
import java.util.Deque;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the vault's tests. A capture draws the vault into a map, for tests that read blocks only. An arena is
 * the vault drawn in a level, in chunks that stay loaded until the test releases them, with a spectator keeper so
 * that the arena's entities tick.
 */
final class VaultTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);
	static final long READY_NANOS = 120_000_000_000L;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private VaultTestSupport() {
	}

	static RuinSite site() {
		return RuinSites.get(SealedVaultSite.ID);
	}

	static VaultPlan plan(final long seed) {
		return VaultPlan.derive(RandomSource.create(seed));
	}

	/** The first seed whose plan passes a test. Tests use it to pick a vault with the element they need. */
	static long seedWith(final java.util.function.Predicate<VaultPlan> wanted) {
		for (int n = 0; n < 5000; n++) {
			long seed = 0x7A5E000L + n * 131L;
			if (wanted.test(plan(seed))) {
				return seed;
			}
		}
		throw new IllegalStateException("no seed has the wanted plan");
	}

	// ---- capture ----

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return RuinFrame.of(site(), new BlockPos(5000, 40, -3000), rotation, seed);
	}

	/** One drawn vault, read as a map from world position to block. A spot the layout never wrote is air. */
	record Capture(RuinFrame frame, Map<BlockPos, BlockState> blocks, Map<String, List<BlockPos>> markers) {
		BlockState at(final BlockPos pos) {
			return this.blocks.getOrDefault(pos, AIR);
		}

		BlockState local(final int x, final int y, final int z) {
			return at(this.frame.toWorld(x, y, z));
		}

		BlockPos world(final int x, final int y, final int z) {
			return this.frame.toWorld(x, y, z);
		}
	}

	static Capture capture(final long seed, final Rotation rotation) {
		RuinFrame frame = frame(seed, rotation);
		Map<BlockPos, BlockState> sink = new HashMap<>();
		BoundingBox box = frame.box();
		BlockState rock = Blocks.STONE.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					sink.put(new BlockPos(x, y, z), rock);
				}
			}
		}
		LayoutContext ctx = LayoutContext.forCapture(frame, sink, frame.box());
		site().layout().draw(ctx);
		return new Capture(frame, sink, ctx.markers());
	}

	// ---- walking ----

	/**
	 * Walks a vault block by block. A walker stands on a spot with a solid block below and {@code clearance} free
	 * blocks above. It steps to a neighbour up one block, level, or down three. A stair block counts as a full block,
	 * so a stair is a climb of one block at each step.
	 */
	record Walker(Capture capture, int clearance) {
		boolean passable(final BlockPos pos) {
			return this.capture.at(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
		}

		boolean standable(final BlockPos pos) {
			if (!this.capture.frame().box().isInside(pos) || !passable(pos) || passable(pos.below())) {
				return false;
			}
			for (int k = 1; k < this.clearance; k++) {
				if (!passable(pos.above(k))) {
					return false;
				}
			}
			return true;
		}

		Set<BlockPos> reach(final BlockPos start) {
			Set<BlockPos> seen = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			if (standable(start)) {
				seen.add(start);
				queue.add(start);
			}
			while (!queue.isEmpty()) {
				BlockPos at = queue.poll();
				for (Direction side : Direction.Plane.HORIZONTAL) {
					for (int dy = 1; dy >= -3; dy--) {
						BlockPos next = at.relative(side).above(dy);
						if (seen.contains(next) || !standable(next) || !canStep(at, side, dy)) {
							continue;
						}
						seen.add(next);
						queue.add(next);
					}
				}
			}
			return seen;
		}

		private boolean canStep(final BlockPos from, final Direction side, final int dy) {
			if (dy == 1) {
				return passable(from.above(this.clearance));
			}
			if (dy < 0) {
				BlockPos beside = from.relative(side);
				for (int k = 0; k < this.clearance; k++) {
					if (!passable(beside.above(k))) {
						return false;
					}
				}
			}
			return true;
		}

		/** True when the reach holds a standing spot inside one room of the plan. */
		boolean reaches(final Set<BlockPos> reach, final int level, final int cell) {
			int ax = VaultPlan.cellMinX(cell % VaultPlan.GRID);
			int az = VaultPlan.cellMinZ(cell / VaultPlan.GRID);
			for (int u = 0; u <= 5; u++) {
				for (int v = 0; v <= 5; v++) {
					if (reach.contains(this.capture.world(ax + u, VaultPlan.stand(level), az + v))) {
						return true;
					}
				}
			}
			return false;
		}
	}

	// ---- arenas ----

	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	record Arena(ServerLevel level, RuinSite site, RuinFrame frame, RuinPiece piece, List<ChunkPos> forced, Map<String, List<BlockPos>> marks,
		List<ServerPlayer> players, VaultPlan plan, long seed) {
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

		/** A spot in the entrance tunnel, outside the activation reach of the whole maze but inside the radius. */
		Vec3 standAt() {
			BlockPos spot = markers(VaultMarkers.ENTRANCE).get(0);
			return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}

		/** A free spot in the core room, level 1. */
		Vec3 standNearCore() {
			BlockPos spot = local(VaultPlan.cellMinX(3) + 4, 9, VaultPlan.cellMinZ(3) + 4);
			return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}

		ServerPlayer join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "delver"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(this.level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
			this.players.add(player);
			return player;
		}

		void leave(final ServerPlayer player) {
			this.level.getServer().getPlayerList().remove(player);
			this.players.remove(player);
		}

		/** A player stands in the radius and the core is pulsed once: the vault wakes. */
		ServerPlayer wake() {
			ServerPlayer player = join(standNearCore());
			core().pulse(this.level);
			return player;
		}

		/** Stops the roster mobs from moving or fighting. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		List<Mob> roster(final String kind) {
			List<Mob> out = new ArrayList<>();
			for (UUID id : entry().roster()) {
				Entity mob = this.level.getEntity(id);
				if (mob instanceof Mob m && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + kind)) {
					out.add(m);
				}
			}
			return out;
		}

		Mob warden() {
			List<Mob> found = roster("vault_warden");
			return found.isEmpty() ? null : found.get(0);
		}

		/** The right-click on the core with an item. */
		net.minecraft.world.InteractionResult useCore(final ServerPlayer player, final ItemStack stack) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			BlockPos pos = corePos();
			return this.level.getBlockState(pos).useItemOn(stack, this.level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
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
			BoundingBox box = this.frame.box();
			AABB area = AABB.of(box).inflate(48.0);
			for (Entity near : this.level.getEntities((Entity) null, area, entity -> RuinMobs.isRuinMob(entity) || entity.entityTags().contains("mod-stella-ruins.vault_arrow"))) {
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

		BlockPos local(final int x, final int y, final int z) {
			return this.frame.toWorld(x, y, z);
		}

		/** A wall block on the west skin of the maze, clear of every doorway: the layout writes it. */
		BlockPos probeWall() {
			return local(VaultPlan.OFFSET, 10, VaultPlan.cellMinZ(3) + 1);
		}

		/** A floor block of the slab under a level 1 room that holds nothing else. */
		BlockPos probeFloor() {
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				BlockPos at = local(VaultPlan.cellMinX(c % VaultPlan.GRID), VaultLayout.SLAB, VaultPlan.cellMinZ(c / VaultPlan.GRID));
				if (this.level.getBlockState(at).is(VaultBlocks.FLOOR)) {
					return at;
				}
			}
			throw new IllegalStateException("no slab block found");
		}
	}

	static Arena arena(final ServerLevel level, final Rotation rotation, final long seed) {
		return arena(level, rotation, seed, true);
	}

	/** Like {@link #arena(ServerLevel, Rotation, long)}, and draws the vault only when asked, so a test can time the draw. */
	static Arena arena(final ServerLevel level, final Rotation rotation, final long seed, final boolean draw) {
		int n = NEXT.getAndIncrement();
		int x0 = 100_000 + (n % 4000) * 160;
		int z0 = 100_000 + (n / 4000) * 160;
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
		if (draw) {
			piece.build(level, frame.box());
		}
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>(), plan(seed), seed);
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.standAt());
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/** Draws the vault and runs the body once the arena's chunks tick entities. The arena is released at the end. */
	static void inArena(final GameTestHelper helper, final long seed, final Rotation rotation, final ArenaBody body) {
		Arena arena = arena(helper.getLevel(), rotation, seed);
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

	static void inArena(final GameTestHelper helper, final long seed, final ArenaBody body) {
		inArena(helper, seed, Rotation.NONE, body);
	}

	/** A check that throws anything but an assertion error would kill the test server, so it becomes a failure. */
	static void guarded(final GameTestHelper helper, final Runnable check) {
		try {
			check.run();
		} catch (GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a vault test body threw", e);
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

	/** A player joined through the player list and ticked every tick, so it behaves like a real one. */
	static ServerPlayer join(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "vault-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		helper.onEachTick(() -> {
			if (!player.isRemoved()) {
				player.doTick();
			}
		});
		return player;
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** A cow stands still and sunlight does not hurt it, so only the test deals damage. */
	static net.minecraft.world.entity.animal.cow.Cow cow(final GameTestHelper helper, final double x, final double z, final float health) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.entity.animal.cow.Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cow.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		cow.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		cow.setNoAi(true);
		cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.max(health, 20.0F));
		cow.setHealth(health);
		level.addFreshEntity(cow);
		return cow;
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[RUINS] test={} RESULT {}", test, line);
	}
}
