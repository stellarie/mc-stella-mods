package com.stella.ruins.site.ruinedkeep;

import com.mojang.authlib.GameProfile;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the keep's tests. An arena is a keep drawn at a spot no earlier run has used, in chunks that stay
 * loaded until the test releases them. A spectator keeper holds the chunks ticking.
 */
final class KeepTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);
	static final long READY_NANOS = 120_000_000_000L;

	private KeepTestSupport() {
	}

	static RuinSite site() {
		return RuinSites.get(RuinedKeepSite.ID);
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, line);
	}

	static RuinFrame frame(final long seed, final int turns, final BlockPos min) {
		RuinSite site = site();
		return new RuinFrame(site.id(), min, turns, seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
	}

	/** Draws the keep into a map of world positions, through one box or chunk by chunk. */
	static Map<BlockPos, BlockState> draw(final RuinFrame frame, final boolean chunked) {
		Map<BlockPos, BlockState> sink = new TreeMap<>(Comparator.comparingLong(BlockPos::asLong));
		BoundingBox box = frame.box();
		if (!chunked) {
			site().layout().draw(LayoutContext.forCapture(frame, sink, box));
			return sink;
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				site().layout().draw(LayoutContext.forCapture(frame, sink, clip));
			}
		}
		return sink;
	}

	static KeepPlan planOf(final RuinFrame frame) {
		return KeepPlan.of(RandomSource.create(frame.seed()));
	}

	/** One drawn keep, with the players a test joined to it. */
	record Arena(ServerLevel level, RuinFrame frame, RuinPiece piece, KeepPlan plan, List<ChunkPos> forced, Map<String, List<BlockPos>> marks, List<ServerPlayer> players) {
		BlockPos corePos() {
			return this.marks.get(RuinMarkers.CORE).get(0);
		}

		BlockPos vaultPos() {
			return this.marks.get(RuinMarkers.VAULT).get(0);
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

		/** The centre of a local spot's block, in the world. */
		Vec3 at(final int x, final int y, final int z) {
			BlockPos pos = this.frame.toWorld(x, y, z);
			return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		}

		ServerPlayer join(final Vec3 where) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "delver"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(this.level, where.x, where.y, where.z, java.util.Set.of(), 0.0F, 0.0F, true);
			this.players.add(player);
			return player;
		}

		void leave(final ServerPlayer player) {
			this.level.getServer().getPlayerList().remove(player);
			this.players.remove(player);
		}

		void moveTo(final ServerPlayer player, final Vec3 where) {
			player.teleportTo(this.level, where.x, where.y, where.z, java.util.Set.of(), 0.0F, 0.0F, true);
		}

		/** A player at the forecourt, outside the gate, who wakes the core when it pulses. */
		ServerPlayer wake() {
			ServerPlayer player = join(at(24, 0, 47));
			core().pulse(this.level);
			return player;
		}

		/** Stops every roster mob from wandering or fighting. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		/** The living roster mobs of one kind. */
		List<Mob> roster(final RuinMobKind kind) {
			List<Mob> out = new ArrayList<>();
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + kind.id())) {
					out.add(mob);
				}
			}
			return out;
		}

		void kill(final ServerPlayer player, final Mob mob) {
			mob.hurtServer(this.level, this.level.damageSources().playerAttack(player), 100_000.0F);
		}

		int itemsNear(final Item item) {
			int total = 0;
			for (ItemEntity entity : this.level.getEntitiesOfClass(ItemEntity.class, area())) {
				if (entity.getItem().is(item)) {
					total += entity.getItem().getCount();
				}
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
			for (ItemEntity item : this.level.getEntitiesOfClass(ItemEntity.class, area())) {
				item.discard();
			}
			RuinData.get(this.level).forget(corePos());
			this.forced.forEach(chunk -> this.level.setChunkForced(chunk.x(), chunk.z(), false));
		}
	}

	static Arena arena(final ServerLevel level, final Rotation rotation) {
		int n = NEXT.getAndIncrement();
		int x0 = 300_000 + (n % 4000) * 144;
		int z0 = 300_000 + (n / 4000) * 144;
		RuinSite site = site();
		RuinFrame probe = frame(0L, rotation.ordinal(), new BlockPos(x0, 0, z0));
		BoundingBox box = probe.box();
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				if (level.setChunkForced(cx, cz, true)) {
					forced.add(new ChunkPos(cx, cz));
				}
			}
		}
		int ground = com.stella.ruins.world.Placement.Probe.of(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState()).groundY(x0, z0);
		BlockPos min = new BlockPos(x0, ground + 1, z0);
		RuinFrame frame = frame(0x4EE90000L + n, rotation.ordinal(), min);
		RuinPiece piece = new RuinPiece(frame);
		piece.build(level, frame.box());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		site.layout().draw(dry);
		Arena arena = new Arena(level, frame, piece, planOf(frame), forced, dry.markers(), new ArrayList<>());
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.at(24, 28, 30));
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	/** Draws the keep and runs the body once its chunks tick entities. The arena is released when the body ends. */
	static void inArena(final GameTestHelper helper, final ArenaBody body) {
		Arena arena = arena(helper.getLevel(), Rotation.NONE);
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
		} catch (net.minecraft.gametest.framework.GameTestAssertException e) {
			throw e;
		} catch (RuntimeException e) {
			StellaRuins.LOGGER.error("[RUINS] a keep test body threw", e);
			helper.fail("the test body threw " + e);
		}
	}

	private static void awaitReady(final GameTestHelper helper, final Arena arena, final long startedNanos) {
		if (ready(arena)) {
			return;
		}
		if (System.nanoTime() - startedNanos > READY_NANOS) {
			helper.fail("the arena chunks did not tick entities within " + READY_NANOS / 1_000_000_000L + " s");
		}
		helper.assertTrue(false, "the arena chunks do not tick entities yet");
	}

	private static boolean ready(final Arena arena) {
		for (ChunkPos chunk : arena.frameChunks()) {
			if (!arena.level().isPositionEntityTicking(new BlockPos(chunk.getMiddleBlockX(), 64, chunk.getMiddleBlockZ()))) {
				return false;
			}
		}
		return true;
	}

	/** A player joined through the player list and ticked every tick, as the reward tests do. */
	static ServerPlayer joinPlayer(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "keep-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		helper.onEachTick(() -> {
			if (!player.isRemoved()) {
				player.doTick();
			}
		});
		return player;
	}

	/** A player with no client counts as still loading, and that makes them invulnerable. */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** A cow stands still and sunlight does not hurt it, so only the test deals damage. */
	static net.minecraft.world.entity.animal.cow.Cow cow(final GameTestHelper helper, final double x, final double z, final float health, final float maxHealth) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.entity.animal.cow.Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cow.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		cow.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		cow.setNoAi(true);
		cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		cow.setHealth(health);
		level.addFreshEntity(cow);
		return cow;
	}
}
