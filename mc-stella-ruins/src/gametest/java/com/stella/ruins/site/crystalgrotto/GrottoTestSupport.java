package com.stella.ruins.site.crystalgrotto;

import com.mojang.authlib.GameProfile;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fixtures for the grotto's tests. An arena is the grotto drawn at a spot no earlier run used, in chunks that stay
 * loaded until the test releases them, with a spectator keeper so that the arena's entities tick.
 */
final class GrottoTestSupport {
	private static final AtomicInteger NEXT = new AtomicInteger((int) ((System.currentTimeMillis() / 1000L) % 100_000L) * 8);
	static final long READY_NANOS = 120_000_000_000L;

	private GrottoTestSupport() {
	}

	static RuinSite site() {
		return RuinSites.get(CrystalGrottoSite.ID);
	}

	/** What a test does in its arena. */
	@FunctionalInterface
	interface ArenaBody {
		void run(Arena arena);
	}

	record Arena(ServerLevel level, RuinSite site, RuinFrame frame, RuinPiece piece, List<ChunkPos> forced, Map<String, List<BlockPos>> marks,
		List<ServerPlayer> players, GrottoPlan plan) {
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

		/** The objective as the core last saved it. */
		ChimeObjective objective() {
			ChimeObjective objective = (ChimeObjective) this.site.newObjective();
			objective.load(entry().objective());
			return objective;
		}

		/** A free floor spot beside the chamber wall, inside the activation radius. */
		Vec3 standAt() {
			BlockPos spot = markers(GrottoMarkers.SENTINEL).get(0);
			return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}

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

		/** A player stands in the radius and the core is pulsed once: the grotto wakes. */
		ServerPlayer wake() {
			ServerPlayer player = join(standAt());
			core().pulse(this.level);
			return player;
		}

		/** Pulses the core until the chimes have played and the player's turn is on. */
		void listen() {
			for (int i = 0; i < 12 && objective().playing(); i++) {
				core().pulse(this.level);
			}
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
		int rosterOf(final String kind) {
			int count = 0;
			for (UUID id : entry().roster()) {
				Entity mob = this.level.getEntity(id);
				if (mob != null && mob.entityTags().contains(com.stella.ruins.world.RuinMobKind.TAG_KIND_PREFIX + kind)) {
					count++;
				}
			}
			return count;
		}

		/** The shaft block of a pillar that a player attacks. */
		BlockPos shaft(final int pillar) {
			return markers(GrottoMarkers.PILLAR).get(pillar).above();
		}

		/** The attack on a shaft, through the real Fabric event. Returns the event's answer. */
		InteractionResult strike(final ServerPlayer player, final int pillar) {
			return AttackBlockCallback.EVENT.invoker().interact(player, this.level, InteractionHand.MAIN_HAND, shaft(pillar), Direction.UP);
		}

		/** The right-click on the core with an item. */
		InteractionResult useCore(final ServerPlayer player, final ItemStack stack) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos pos = corePos();
			return this.level.getBlockState(pos).useItemOn(stack, this.level, player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		}

		/** Plays one full round: the player's turn must be on. Returns the order that was struck. */
		int[] solveRound(final ServerPlayer player) {
			int[] order = objective().order();
			for (int pillar : order) {
				strike(player, pillar);
			}
			return order;
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
			net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.of(box).inflate(48.0);
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
		Arena arena = new Arena(level, site, frame, piece, forced, dry.markers(), new ArrayList<>(), GrottoPlan.derive(RandomSource.create(seed)));
		RuinData.get(level).forget(arena.corePos());
		ServerPlayer keeper = arena.join(arena.standAt());
		keeper.setGameMode(GameType.SPECTATOR);
		return arena;
	}

	/** Draws the grotto and runs the body once the arena's chunks tick entities. The arena is released at the end. */
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
			StellaRuins.LOGGER.error("[RUINS] a grotto test body threw", e);
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
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "grotto-tester"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
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

	/** A cow stands still and sunlight does not hurt it, so only the test deals damage. */
	static net.minecraft.world.entity.animal.cow.Cow cow(final GameTestHelper helper, final double x, final double z, final float health) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.entity.animal.cow.Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cow.addTag(com.stella.ruins.world.RuinMobKind.NO_SPAWN_ROLL_TAG);
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
