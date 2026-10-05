package com.stella.ruins.site.provinggrounds;

import com.mojang.authlib.GameProfile;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinPiece;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinVaultBlockEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the proving grounds tests. An arena is the real site drawn at a spot no earlier run has used, in chunks
 * that stay loaded until the test releases them. A chunk forced in this tick holds its entities hidden until the server
 * ticks it, so every arena holds a spectator keeper and a test waits until the arena ticks entities.
 */
final class ProvingTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);
	private static final int BASE = 3_000_000;
	static final long READY_NANOS = 120_000_000_000L;
	static final long CLOCK_START = 5_000_000L;

	private ProvingTestSupport() {
	}

	/** What a test does in its arena. */
	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	/** A clock a test moves by hand. It replaces the ruins clock until {@link #close()}. */
	static final class Clock implements AutoCloseable {
		private final AtomicLong now = new AtomicLong(CLOCK_START);
		private final RuinClock.Source old;

		Clock() {
			this.old = RuinClock.replace(level -> this.now.get());
		}

		long now() {
			return this.now.get();
		}

		void advance(final long ticks) {
			this.now.addAndGet(ticks);
		}

		@Override
		public void close() {
			RuinClock.replace(this.old);
		}
	}

	/** One drawn site, with the players a test joined to it. */
	record Arena(ServerLevel level, RuinSite site, RuinFrame frame, RuinPiece piece, List<ChunkPos> forced, Map<String, List<BlockPos>> marks,
		List<ServerPlayer> players, ArenaPlan plan) {
		BlockPos corePos() {
			return markers(RuinMarkers.CORE).get(0);
		}

		BlockPos vaultPos() {
			return markers(RuinMarkers.VAULT).get(0);
		}

		BlockPos gongPos() {
			return markers(ArenaPlan.GONG).get(0);
		}

		BlockPos lecternPos(final int tier) {
			return markers(ArenaPlan.LECTERN + tier).get(0);
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

		/** The objective as the core has saved it. */
		TierObjective objective() {
			TierObjective objective = new TierObjective();
			objective.load(entry().objective());
			return objective;
		}

		/** A spot at standing height beside the core. */
		Vec3 standAt(final int dx, final int dz) {
			BlockPos core = corePos();
			return new Vec3(core.getX() + 0.5 + dx, core.getY(), core.getZ() + 0.5 + dz);
		}

		/** A player joined through the player list, so the level counts them in {@code players()}. */
		ServerPlayer join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "gladiator-tester"), false);
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

		void pulse() {
			core().pulse(this.level);
		}

		TierObjective.Result ring(final ServerPlayer player) {
			return GroundsUse.use(this.level, gongPos(), player);
		}

		TierObjective.Result pick(final ServerPlayer player, final int tier) {
			return GroundsUse.use(this.level, lecternPos(tier), player);
		}

		/** Rings the gong and uses a lectern, as a player would. */
		TierObjective.Result start(final ServerPlayer player, final int tier) {
			ring(player);
			return pick(player, tier);
		}

		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		List<Mob> roster() {
			List<Mob> mobs = new ArrayList<>();
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mobs.add(mob);
				}
			}
			return mobs;
		}

		long count(final String kindId) {
			return roster().stream().filter(mob -> mob.entityTags().contains("mod-stella-ruins.kind." + kindId)).count();
		}

		/** The living roster by kind: gladiator, retiarius, hound, beast. */
		int[] mix() {
			return new int[] {(int) count(GroundsMobs.GLADIATOR), (int) count(GroundsMobs.RETIARIUS), (int) count(GroundsMobs.HOUND), (int) count(GroundsMobs.PIT_BEAST)};
		}

		int kill(final ServerPlayer player, final List<Mob> mobs) {
			for (Mob mob : mobs) {
				mob.hurtServer(this.level, this.level.damageSources().playerAttack(player), 10_000.0F);
			}
			return mobs.size();
		}

		int killRoster(final ServerPlayer player) {
			freeze();
			return kill(player, roster());
		}

		/** The iron bars of the main portcullis now: how many of the marked cells hold bars. */
		int portcullisBars() {
			int bars = 0;
			for (BlockPos base : markers(ArenaPlan.PORTCULLIS)) {
				for (int i = 0; i < TierObjective.PORTCULLIS_HEIGHT; i++) {
					bars += this.level.getBlockState(base.above(i)).is(net.minecraft.world.level.block.Blocks.IRON_BARS) ? 1 : 0;
				}
			}
			return bars;
		}

		int itemsInVault(final Item item) {
			int total = 0;
			for (int i = 0; i < vault().getContainerSize(); i++) {
				ItemStack stack = vault().getItem(i);
				total += stack.is(item) ? stack.getCount() : 0;
			}
			return total;
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
			ProvingData.get(this.level).forget(corePos());
			this.forced.forEach(chunk -> this.level.setChunkForced(chunk.x(), chunk.z(), false));
		}
	}

	/** The i-th test seed. Sequential seeds make the first rolls of the legacy random climb in step, so the seeds are spread. */
	static long seed(final long i) {
		return i * 0x9E3779B97F4A7C15L;
	}

	/** The first seed whose roll passes the test. The piece seed is the stream the layout reads. */
	static long seedWhere(final Predicate<ArenaPlan.Params> wanted) {
		for (long i = 1; i < 100_000; i++) {
			if (wanted.test(ArenaPlan.Params.roll(RandomSource.create(seed(i))))) {
				return seed(i);
			}
		}
		throw new IllegalStateException("no seed passes the test");
	}

	/** Draws a site in fresh forced chunks. Entities in those chunks stay hidden for a tick or two. */
	static Arena arena(final ServerLevel level, final RuinSite site, final Rotation rotation, final long seed) {
		int n = NEXT.getAndIncrement();
		int x0 = BASE + (n % 4000) * 144;
		int z0 = BASE + (n / 4000) * 144;
		RuinFrame probe = new RuinFrame(site.id(), new BlockPos(x0, 0, z0), rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
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
		RuinFrame frame = new RuinFrame(site.id(), new BlockPos(x0, ground + 1, z0), rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
		RuinPiece piece = new RuinPiece(frame);
		piece.build(level, frame.box());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		ArenaPlan plan = ArenaLayout.plan(dry);
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>(), plan);
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.standAt(0, 0));
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/** Draws the real site and runs the body once the arena's chunks tick entities. The arena is released when the body ends. */
	static void inArena(final GameTestHelper helper, final Rotation rotation, final long seed, final ArenaBody body) {
		inArena(helper, ProvingGroundsSite.site(), rotation, seed, body);
	}

	static void inArena(final GameTestHelper helper, final RuinSite site, final Rotation rotation, final long seed, final ArenaBody body) {
		boolean[] started = {false};
		RuntimeException[] failure = {null};
		Arena arena = arena(helper.getLevel(), site, rotation, seed);
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

	/** An arena with a plain seed, turned north. */
	static void inArena(final GameTestHelper helper, final ArenaBody body) {
		inArena(helper, Rotation.NONE, 7L, body);
	}

	/** Runs a check that a test repeats each tick, turning anything but an assertion into a failure of that test. */
	static void guarded(final GameTestHelper helper, final Runnable check) {
		try {
			check.run();
		} catch (net.minecraft.gametest.framework.GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a proving grounds test body threw", e);
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

	/** A player with no client counts as still loading, and that makes them invulnerable. */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	/** A player joined in the helper's own level, ticked every tick so it behaves like a real one. */
	static ServerPlayer join(final GameTestHelper helper, final Vec3 relative) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "gladiator-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(relative);
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
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

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, line);
	}
}
