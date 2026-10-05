package com.stella.ruins.site.ambushcamp;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the camp's tests. An arena is the camp drawn at a spot no earlier run used, in chunks that stay
 * loaded until the test releases them, with a spectator keeper so that the arena's entities tick.
 */
final class CampTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8 + 4);
	static final long READY_NANOS = 120_000_000_000L;
	/** The tick limit of an arena test. It is far above what 120 s can hold, so the wall clock ends a stuck wait. */
	static final int ARENA_TICKS = 1_000_000;
	/** A test player takes hits of any size: with the mobs mod a ruin mob hits by its level. */
	static final double TEST_HEALTH = 1_000_000.0;
	/** A seed whose camp has at least four tents and a trail start that lies inside the activation radius. */
	static final long SEED = playableSeed();

	private CampTestSupport() {
	}

	private static long playableSeed() {
		for (long seed = 0xCA3F0L;; seed++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(seed));
			double dx = plan.trailStart().x() - plan.core().x();
			double dz = plan.trailStart().z() - plan.core().z();
			if (plan.tentCount() >= 4 && Math.sqrt(dx * dx + dz * dz) < AmbushCampSite.ACTIVATION_RADIUS - 2 && plan.draws() == 1) {
				return seed;
			}
		}
	}

	static RuinSite site() {
		return RuinSites.get(AmbushCampSite.ID);
	}

	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	record Arena(ServerLevel level, RuinSite site, RuinFrame frame, RuinPiece piece, List<ChunkPos> forced, Map<String, List<BlockPos>> marks,
		List<ServerPlayer> players, CampPlan plan) {
		BlockPos corePos() {
			return markers(RuinMarkers.CORE).get(0);
		}

		BlockPos vaultPos() {
			return markers(RuinMarkers.VAULT).get(0);
		}

		List<BlockPos> markers(final String name) {
			return this.marks.getOrDefault(name, List.of());
		}

		BlockPos tent(final int id) {
			return markers(CampMarkers.tent(id)).get(0);
		}

		BlockPos door(final int id) {
			return markers(CampMarkers.door(id)).get(0);
		}

		int tents() {
			int count = 0;
			while (this.marks.containsKey(CampMarkers.tent(count + 1))) {
				count++;
			}
			return count;
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

		ChiefObjective objective() {
			ChiefObjective objective = (ChiefObjective) this.site.newObjective();
			objective.load(entry().objective());
			return objective;
		}

		/** The outer end of the trail, inside the activation radius. */
		Vec3 standAt() {
			BlockPos spot = markers(CampMarkers.TRAIL).get(0);
			return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}

		ServerPlayer join(final Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "bandit-hunter"), false);
			ServerPlayer player = new ServerPlayer(this.level.getServer(), this.level, cookie.gameProfile(), cookie.clientInformation());
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			this.level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			sturdy(player);
			player.teleportTo(this.level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
			this.players.add(player);
			return player;
		}

		void leave(final ServerPlayer player) {
			this.level.getServer().getPlayerList().remove(player);
			this.players.remove(player);
		}

		/** A player stands at the trail and the core is pulsed once: the camp wakes. */
		ServerPlayer wake() {
			ServerPlayer player = join(standAt());
			core().pulse(this.level);
			return player;
		}

		/** Stops the roster mobs from moving or fighting, so a test sees only what it causes. */
		void freeze() {
			for (UUID id : entry().roster()) {
				if (this.level.getEntity(id) instanceof Mob mob) {
					mob.setNoAi(true);
				}
			}
		}

		/** The roster mobs of one kind that live now. */
		List<Mob> rosterOf(final String kind) {
			List<Mob> mobs = new ArrayList<>();
			for (UUID id : entry().roster()) {
				Entity mob = this.level.getEntity(id);
				if (mob instanceof Mob m && m.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + kind)) {
					mobs.add(m);
				}
			}
			return mobs;
		}

		Mob chief() {
			List<Mob> chiefs = rosterOf("bandit_chief");
			return chiefs.isEmpty() ? null : chiefs.get(0);
		}

		/** Removes every ruin mob near the camp, as a player's clean-up would. */
		void clearMobs() {
			AABB area = AABB.of(this.frame.box()).inflate(48.0);
			for (Entity near : this.level.getEntities((Entity) null, area, RuinMobs::isRuinMob)) {
				near.discard();
			}
		}

		void release() {
			for (ServerPlayer player : new ArrayList<>(this.players)) {
				leave(player);
			}
			clearMobs();
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

	static Arena arena(final ServerLevel level, final Rotation rotation, final long seed) {
		int n = NEXT.getAndIncrement();
		int x0 = 100_000 + (n % 4000) * 144;
		int z0 = 100_000 + (n / 4000) * 144;
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
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>(), CampPlan.derive(RandomSource.create(seed)));
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.standAt());
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/** Draws the camp and runs the body once the arena's chunks tick entities. The arena is released at the end. */
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

	/** A player joined through the player list and ticked every tick, so it behaves like a real one. */
	static ServerPlayer join(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "camp-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		sturdy(player);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		helper.onEachTick(() -> {
			if (!player.isRemoved()) {
				player.doTick();
			}
		});
		return player;
	}

	static void sturdy(final ServerPlayer player) {
		player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(TEST_HEALTH);
		player.setHealth((float) TEST_HEALTH);
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
