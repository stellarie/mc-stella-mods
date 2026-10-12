package com.stella.tower;

import com.stella.tower.colosseum.GuardianKind;
import com.stella.tower.colosseum.Guardians;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * A guardians' arena of the test's own, high over the world where the sky is open and no other test comes: a stone floor,
 * air above it, and the chunks held so what stands there is ticked. Every rig is a hundred blocks from the one before, so
 * tests that run at once never share one. The guardians' leash is {@link #ARENA} blocks round the middle; the stone runs
 * a few blocks past it, so a player can stand outside the arena on solid ground.
 * <p>
 * A chunk held a moment ago is not yet ticking, and with many rigs at once it can take many ticks: what is spawned in it is
 * not found by a box query, cannot be ridden, and does not tick. So a case does not start when the rig is built. It hands
 * its scenario to {@link #ready}, which runs it on the first tick the rig's middle is entity ticking, and schedules the rest
 * with {@link #after} and {@link #each}, counted from that tick. All three are called from the test body, never from inside
 * another delayed callback.
 */
final class GuardianRig {
	static final int HALF = 22;
	static final int ARENA = 16;
	static final int FLOOR_Y = 200;
	/** Entity ticking reaches one chunk less than the ticket's radius less two: 3 ticks the middle chunk and its eight neighbours. */
	private static final int RADIUS = 3;
	private static final double STURDY_HEALTH = 2000.0;
	private static final int HEADROOM = 14;
	private static final int HOLD_EVERY = 100;
	private static final long WAIT_FOR_TICKING = 300L;
	private static final int FIRST_X = 1_000_000;
	private static final int FIRST_Z = 1_000_000;
	private static final int SPACING = 128;

	final ServerLevel level;
	/** The middle column at the floor layer. */
	final BlockPos center;
	private final GameTestHelper helper;
	private final List<Mob> spawned = new ArrayList<>();
	private final List<ServerPlayer> players = new ArrayList<>();
	private final List<Scheduled> later = new ArrayList<>();
	private final List<Runnable> perTick = new ArrayList<>();
	private Runnable scenario = () -> {
	};
	private boolean started;
	private long startedAt;

	private record Scheduled(long delay, Runnable action, boolean[] done) {
	}

	private GuardianRig(final GameTestHelper helper, final BlockPos center) {
		this.helper = helper;
		this.level = helper.getLevel();
		this.center = center;
	}

	/**
	 * The rig of a slot. A slot is a place, not a count: the same slot is the same chunks in every run, and the test world keeps
	 * them, so the world generates a rig's chunks once. Two cases that run at once need two slots.
	 */
	static GuardianRig build(final GameTestHelper helper, final int slot) {
		// On the middle of a chunk, so the arena (22 blocks each way) lies in that chunk and its eight neighbours.
		BlockPos center = new BlockPos(((FIRST_X + slot * SPACING) >> 4 << 4) + 8, FLOOR_Y, (FIRST_Z >> 4 << 4) + 8);
		GuardianRig rig = new GuardianRig(helper, center);
		rig.hold();
		// Ticks run faster than the world generates; wait for the chunks here, or the case waits for nothing.
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				rig.level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
			}
		}
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int dy = 1; dy <= HEADROOM; dy++) {
					BlockPos up = center.offset(dx, dy, dz);
					if (!rig.level.getBlockState(up).isAir()) {
						rig.level.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				rig.level.setBlock(center.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		helper.onEachTick(rig::pump);
		helper.runBeforeTestEnd(rig::cleanup);
		return rig;
	}

	/** The scenario to run once the rig ticks: spawn the guardians and players, and begin. */
	void ready(final Runnable scenario) {
		this.scenario = scenario;
	}

	/** Runs the action this many ticks after the scenario began. */
	void after(final long delay, final Runnable action) {
		this.later.add(new Scheduled(delay, action, new boolean[1]));
	}

	/** Runs the action every tick after the scenario began. */
	void each(final Runnable action) {
		this.perTick.add(action);
	}

	/** Ticks since the scenario began; negative before. */
	long elapsed() {
		return this.started ? this.helper.getTick() - this.startedAt : -1L;
	}

	private void pump() {
		long tick = this.helper.getTick();
		if (tick % HOLD_EVERY == 0) {
			hold();
		}
		if (!this.started) {
			if (tick % 200 == 100) {
				StringBuilder out = new StringBuilder();
				for (int dz = -3; dz <= 3; dz++) {
					for (int dx = -3; dx <= 3; dx++) {
						BlockPos at = this.center.offset(dx * 16, 0, dz * 16);
						out.append(this.level.isPositionEntityTicking(at) ? 'E' : this.level.getChunkSource().isPositionTicking(ChunkPos.pack(at)) ? 'b' : this.level.isLoaded(at) ? 'l' : '.');
					}
					out.append('/');
				}
				TowerTestSupport.result("rigWait", "center=" + this.center + " tick=" + tick + " grid=" + out);
			}
			this.helper.assertTrue(tick < WAIT_FOR_TICKING, "the rig's chunks never began to tick");
			if (ticking()) {
				this.started = true;
				this.startedAt = tick;
				// What an earlier run left in these chunks is not this case's.
				for (net.minecraft.world.entity.Entity stale : this.level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
					new net.minecraft.world.phys.AABB(this.center).inflate(HALF + 8, HEADROOM + 20, HALF + 8), entity -> !(entity instanceof ServerPlayer))) {
					stale.discard();
				}
				TowerTestSupport.result("rig", "center=" + this.center + " readyAfter=" + tick);
				this.scenario.run();
			}
			return;
		}
		for (Runnable action : List.copyOf(this.perTick)) {
			action.run();
		}
		for (Scheduled each : List.copyOf(this.later)) {
			if (!each.done()[0] && tick - this.startedAt >= each.delay()) {
				each.done()[0] = true;
				each.action().run();
			}
		}
	}

	/** Whether every chunk the arena touches is entity ticking, not only the middle one. */
	private boolean ticking() {
		for (int dx = -HALF; dx <= HALF; dx += HALF) {
			for (int dz = -HALF; dz <= HALF; dz += HALF) {
				if (!this.level.isPositionEntityTicking(this.center.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	private void hold() {
		this.level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(this.center), RADIUS);
	}

	/** A guardian standing on its post, {@code dx} and {@code dz} from the middle. */
	Guardians.Spawned spawn(final GuardianKind kind, final int dx, final int dz) {
		BlockPos post = this.center.offset(dx, 1, dz);
		Guardians.Spawned made = Guardians.spawn(this.level, kind, new Guardians.Home(this.center, ARENA, post));
		this.helper.assertTrue(made != null, "the " + kind + " guardian could not be spawned");
		this.spawned.add(made.rider());
		this.spawned.add(made.horse());
		return made;
	}

	/**
	 * A survival player at an offset from the middle, facing the given way. A test player is not ticked, so its effects, fire
	 * and knockback stay as they were set, and it cannot be hurt until {@link TowerTestSupport#vulnerable} says so.
	 */
	ServerPlayer player(final int dx, final int dz, final float yaw) {
		ServerPlayer player = TowerTestSupport.join(this.helper, 1.5, 1.5);
		player.teleportTo(this.level, this.center.getX() + dx + 0.5, this.center.getY() + 1.0, this.center.getZ() + dz + 0.5, Set.of(), yaw, 0.0F, true);
		this.players.add(player);
		return player;
	}

	/** Moves a player to an offset from the middle, on the floor. */
	void move(final ServerPlayer player, final int dx, final int dz) {
		player.teleportTo(this.level, this.center.getX() + dx + 0.5, this.center.getY() + 1.0, this.center.getZ() + dz + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	/**
	 * A player who outlasts what the case throws at them: a guardian's blows differ from run to run, and a player who dies
	 * mid-case reads as one who was never hit or never burned.
	 */
	ServerPlayer sturdy(final ServerPlayer player) {
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(STURDY_HEALTH);
		player.setHealth((float) STURDY_HEALTH);
		TowerTestSupport.vulnerable(player);
		return player;
	}

	private void cleanup() {
		for (Mob mob : this.spawned) {
			mob.discard();
		}
		for (ServerPlayer player : this.players) {
			TowerTestSupport.leave(this.helper, player);
		}
	}
}
