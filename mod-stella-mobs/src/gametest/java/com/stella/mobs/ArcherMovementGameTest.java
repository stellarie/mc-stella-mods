package com.stella.mobs;

import com.stella.mobs.behavior.AmbushGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import com.stella.mobs.config.RoleSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * How a plain skeleton archer moves while it fights a survival player on the wide floor.
 *
 * <ul>
 * <li>A pursuer that presses into the near band does not hold the archer there. The archer backs out again.
 * <li>An archer with cover goes to it after each release, then peeks and shoots again. An archer in the open does not hide.
 * <li>An archer in its band never stands still for a whole window while it fights.
 * </ul>
 *
 * <p>The player is a headless mock. The driver sets its gaze and position on every tick. Every time counts the mob's own
 * ticks or the driver's own count, because the level clock barely moves in a test.
 */
public class ArcherMovementGameTest {
	private static final String TEST = "archerMovement";
	private static final int MAX_TICKS = StanceGameTest.READY_TICKS + 900;
	private static final double CENTER = WideArena.SIZE / 2.0 + 0.5;
	private static final double WEST_X = 8.5;
	private static final double EAST_X = 48.5;
	private static final int HEARTBEAT_TICKS = 20;
	/** The archer settles into its fight before the driver measures it. */
	private static final int SETTLE_TICKS = 20;
	private static final int CHUNK = 16;

	private static final double PURSUIT_MOB_Z = 16.5;
	private static final double PURSUIT_START_GAP = 12.0;
	/** Slower than a walking skeleton, so a retreat can open the band again. */
	private static final double PURSUIT_SPEED = 0.12;
	private static final double PURSUIT_HOLD = 2.0;
	private static final int PURSUIT_WINDOW = 200;
	/** "Deep" means a block inside the near edge, so the edge itself does not count. */
	private static final double DEEP_MARGIN = 1.0;
	private static final int MIN_REOPENS = 2;

	private static final double COVER_MOB_Z = 10.5;
	private static final double COVER_SHOT_DISTANCE = 20.0;
	private static final int WALL_GAP = 2;
	private static final int WALL_WIDTH = 7;
	private static final int WALL_HEIGHT = 4;
	private static final int MIN_CYCLES = 2;
	private static final int COVER_WINDOW = 800;
	/** Burst arrows come this close together. A longer gap starts a new release. */
	private static final int RELEASE_GAP = SkeletonVolleyGoal.BURST_SHOT_INTERVAL_TICKS + 5;

	private static final double MOVE_MOB_Z = 16.5;
	private static final double MOVE_GAP = 12.0;
	/** The plan: an archer in its band moves at least once in this many ticks. */
	private static final int STILL_LIMIT = 60;
	private static final double MOVED_BLOCKS = 1.0;
	private static final int MOVE_WINDOW = 240;

	/** One archer, one player, and the driver's count of the fight. */
	private static final class Scene {
		private final String name;
		private final double x;
		private final double mobZ;
		private final double playerZ;
		private ServerPlayer player;
		private Mob mob;
		private WideArena arena;
		private boolean live;
		private boolean done;
		private int ticks;
		private int lastMobTick = -1;
		private boolean stallLogged;

		private Scene(final String name, final double x, final double mobZ, final double playerZ) {
			this.name = name;
			this.x = x;
			this.mobZ = mobZ;
			this.playerZ = playerZ;
		}

		/** The scene's spots and every chunk of the floor, so no chunk still loads its entities. */
		private List<Vec3> spots() {
			List<Vec3> spots = new ArrayList<>(List.of(new Vec3(this.x, 1.0, this.mobZ), new Vec3(this.x, 1.0, this.playerZ)));
			List<Integer> lines = new ArrayList<>();
			for (int at = 0; at < WideArena.SIZE; at += CHUNK) {
				lines.add(at);
			}

			lines.add(WideArena.SIZE - 1);
			for (int x : lines) {
				for (int z : lines) {
					spots.add(new Vec3(x, 1.0, z));
				}
			}

			return spots;
		}

		private void populate(final WideArena arena) {
			this.arena = arena;
			ServerPlayer survivor = arena.survivalPlayer(this.x, this.playerZ);
			// No mob may target the player while it falls.
			survivor.setPermanentlyInvulnerable(true);
			this.player = survivor;
			this.mob = arena.spawn(EntityTypes.SKELETON, this.x, this.mobZ, ArcherMovementGameTest::bow);
			WideArena.watch(this.mob, () -> survivor);
		}

		/** One driver tick. False until the archer stands on the ground. */
		private boolean step() {
			ServerPlayer player = this.player;
			Mob mob = this.mob;
			if (player == null || mob == null || !mob.isAlive() || this.done) {
				return false;
			}

			WideArena.keepValid(player);
			if (!this.live) {
				if (!mob.onGround()) {
					return false;
				}

				player.setPermanentlyInvulnerable(false);
				this.live = true;
			}

			mob.setTarget(player);
			MeleeKitFloor.lookAt(player, mob);
			this.ticks++;
			// A stalled mob clock is a harness fault.
			if (mob.tickCount == this.lastMobTick && !this.stallLogged) {
				this.stallLogged = true;
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} STALL t={} mobTick={} pos={} chunk={} arenaChunk={} forced={} entityTicking={}",
					TEST, this.name, this.ticks, mob.tickCount, vec(mob.position()), mob.chunkPosition(), this.arena.holds(mob.chunkPosition()),
					((net.minecraft.server.level.ServerLevel) mob.level()).getForceLoadedChunks().contains(mob.chunkPosition().pack()),
					((net.minecraft.server.level.ServerLevel) mob.level()).isPositionEntityTicking(mob.blockPosition())
				);
			}

			this.lastMobTick = mob.tickCount;
			if (this.ticks % HEARTBEAT_TICKS == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} t={} mobTick={} dist={} pos={} hiding={} shots={} goals={}",
					TEST, this.name, this.ticks, mob.tickCount, number(mob.distanceTo(player)), vec(mob.position()),
					AmbushGoal.repositioning(mob), WideArena.shots(mob).size(), runningGoals(mob)
				);
			}

			return true;
		}

		private String describe() {
			return "ticks=" + this.ticks + " mobTick=" + this.mob.tickCount + " pos=" + vec(this.mob.position())
				+ " distance=" + number(this.mob.distanceTo(this.player)) + " shots=" + WideArena.shots(this.mob).size()
				+ " goals=" + runningGoals(this.mob);
		}

		private void result(final String outcome) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {} {}", TEST, this.name, outcome, this.describe());
		}
	}

	/**
	 * A pursuer presses into the near band and keeps pressing. The player walks at the archer at 0.12 blocks per tick and
	 * holds 2 blocks from it. Over 200 ticks from the first entry, the archer opens the band again at least twice. It never
	 * spends more than one band window a block or more inside the near edge.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void pursuedArcherRetreats(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("pursuedArcherRetreats", CENTER, PURSUIT_MOB_Z, PURSUIT_MOB_Z - PURSUIT_START_GAP);
		StanceGameTest.whenTicking(helper, scene.spots(), () -> scene.populate(arena));
		double near = RoleSpec.RANGED_NEAR;
		int[] enteredAt = {-1};
		int[] deepRun = {0};
		int[] longestDeep = {0};
		int[] reopens = {0};
		boolean[] inside = {false};
		helper.onEachTick(() -> {
			if (!scene.step() || scene.ticks < SETTLE_TICKS) {
				return;
			}

			Vec3 toMob = flat(scene.mob.position().subtract(scene.player.position()));
			Vec3 move = toMob.length() <= PURSUIT_HOLD ? Vec3.ZERO : toMob.normalize().scale(PURSUIT_SPEED);
			scene.player.setPos(scene.player.getX() + move.x, scene.player.getY(), scene.player.getZ() + move.z);
			scene.player.setKnownMovement(move);
			double distance = scene.mob.distanceTo(scene.player);
			if (enteredAt[0] < 0) {
				if (distance < near) {
					enteredAt[0] = scene.ticks;
					inside[0] = true;
				}

				return;
			}

			if (inside[0] && distance >= near) {
				reopens[0]++;
			}

			inside[0] = distance < near;
			deepRun[0] = distance < near - DEEP_MARGIN ? deepRun[0] + 1 : 0;
			longestDeep[0] = Math.max(longestDeep[0], deepRun[0]);
			if (scene.ticks - enteredAt[0] < PURSUIT_WINDOW) {
				return;
			}

			scene.done = true;
			scene.result("reopens=" + reopens[0] + " longestDeep=" + longestDeep[0] + " enteredAt=" + enteredAt[0]);
			helper.assertTrue(
				reopens[0] >= MIN_REOPENS,
				"the archer opened its band " + reopens[0] + " times under pursuit, fewer than " + MIN_REOPENS + ": " + scene.describe()
			);
			helper.assertTrue(
				longestDeep[0] <= KeepDistanceGoal.WINDOW_TICKS,
				"the archer stayed deep inside its band for " + longestDeep[0] + " ticks, past " + KeepDistanceGoal.WINDOW_TICKS + ": " + scene.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * Shoot, then hide. The player watches each archer from 20 blocks, so every shot is a seen shot. The west archer has a
	 * wall beside it. It releases, hides, releases again, and hides again: two cycles, each hide before the next release.
	 * The east archer stands in the open. It releases twice and never hides.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void shotThenHide(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildWall(helper, WEST_X);
		Scene covered = new Scene("shotThenHide.covered", WEST_X, COVER_MOB_Z, COVER_MOB_Z + COVER_SHOT_DISTANCE);
		Scene open = new Scene("shotThenHide.open", EAST_X, COVER_MOB_Z, COVER_MOB_Z + COVER_SHOT_DISTANCE);
		List<Vec3> spots = new ArrayList<>(covered.spots());
		spots.addAll(open.spots());
		StanceGameTest.whenTicking(helper, spots, () -> {
			covered.populate(arena);
			open.populate(arena);
		});
		List<Integer> coveredHides = new ArrayList<>();
		List<Integer> openHides = new ArrayList<>();
		boolean[] wasHiding = {false, false};
		helper.onEachTick(() -> {
			if (covered.done) {
				return;
			}

			if (covered.step()) {
				boolean hiding = AmbushGoal.repositioning(covered.mob);
				if (hiding && !wasHiding[0]) {
					coveredHides.add(covered.mob.tickCount);
				}

				wasHiding[0] = hiding;
			}

			if (open.step()) {
				boolean hiding = AmbushGoal.repositioning(open.mob);
				if (hiding && !wasHiding[1]) {
					openHides.add(open.mob.tickCount);
				}

				wasHiding[1] = hiding;
			}

			if (!covered.live || !open.live) {
				return;
			}

			List<Integer> coveredReleases = releases(covered.mob);
			List<Integer> openReleases = releases(open.mob);
			boolean coveredDone = coveredHides.size() >= MIN_CYCLES && coveredReleases.size() >= MIN_CYCLES;
			boolean openDone = openReleases.size() >= MIN_CYCLES;
			if (!coveredDone || !openDone) {
				helper.assertTrue(
					covered.ticks < COVER_WINDOW,
					"no two shot-hide cycles in " + COVER_WINDOW + " ticks: releases=" + coveredReleases + " hides=" + coveredHides
						+ " openReleases=" + openReleases + " openHides=" + openHides + " " + covered.describe()
				);
				return;
			}

			covered.done = true;
			open.done = true;
			covered.result("releases=" + coveredReleases + " hides=" + coveredHides);
			open.result("releases=" + openReleases + " hides=" + openHides);
			int firstRelease = coveredReleases.get(0);
			int secondRelease = coveredReleases.get(1);
			int firstHide = coveredHides.get(0);
			int secondHide = coveredHides.get(1);
			helper.assertTrue(
				firstRelease < firstHide && firstHide < secondRelease,
				"the covered archer did not hide between its first two releases: releases=" + coveredReleases + " hides=" + coveredHides
			);
			helper.assertTrue(
				secondRelease < secondHide,
				"the covered archer did not hide after its second release: releases=" + coveredReleases + " hides=" + coveredHides
			);
			helper.assertTrue(openHides.isEmpty(), "the archer in the open hid: hides=" + openHides + " " + open.describe());
			helper.succeed();
		});
	}

	/**
	 * An archer in its band keeps moving. The player stands 12 blocks away and watches. Over 240 ticks the archer never
	 * stays within one block of a spot for more than 60 ticks, and it shoots.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void archerKeepsMoving(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("archerKeepsMoving", CENTER, MOVE_MOB_Z, MOVE_MOB_Z + MOVE_GAP);
		StanceGameTest.whenTicking(helper, scene.spots(), () -> scene.populate(arena));
		Vec3[] anchor = {null};
		int[] anchoredAt = {0};
		int[] longestStill = {0};
		helper.onEachTick(() -> {
			if (!scene.step() || scene.ticks < SETTLE_TICKS) {
				return;
			}

			Vec3 position = scene.mob.position();
			if (anchor[0] == null || flat(position.subtract(anchor[0])).length() > MOVED_BLOCKS) {
				anchor[0] = position;
				anchoredAt[0] = scene.ticks;
			}

			int still = scene.ticks - anchoredAt[0];
			longestStill[0] = Math.max(longestStill[0], still);
			helper.assertTrue(
				still <= STILL_LIMIT,
				"the archer stood within " + MOVED_BLOCKS + " block of one spot for " + still + " ticks: " + scene.describe()
			);
			if (scene.ticks - SETTLE_TICKS < MOVE_WINDOW) {
				return;
			}

			scene.done = true;
			scene.result("longestStill=" + longestStill[0]);
			helper.assertTrue(!WideArena.shots(scene.mob).isEmpty(), "the archer never shot: " + scene.describe());
			helper.succeed();
		});
	}

	/** The owner tick of the first arrow of each release. */
	private static List<Integer> releases(final Mob mob) {
		List<Integer> starts = new ArrayList<>();
		int last = Integer.MIN_VALUE / 2;
		for (WideArena.Shot shot : WideArena.shots(mob)) {
			if (shot.ownerTick() - last > RELEASE_GAP) {
				starts.add(shot.ownerTick());
			}

			last = shot.ownerTick();
		}

		return starts;
	}

	/** A wall east of the archer, beside it and not between it and the player. Its shadow falls north and east. */
	private static void buildWall(final GameTestHelper helper, final double archerX) {
		int from = (int) Math.floor(archerX) + WALL_GAP;
		for (int x = from; x < from + WALL_WIDTH; x++) {
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				helper.setBlock(x, y, (int) COVER_MOB_Z, Blocks.STONE);
			}
		}
	}

	private static void bow(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	private static String runningGoals(final Mob mob) {
		StringBuilder names = new StringBuilder();
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.isRunning()) {
				names.append(wrapped.getGoal().getClass().getSimpleName()).append(':').append(wrapped.getPriority()).append(' ');
			}
		}

		return names.length() == 0 ? "none" : names.toString().trim();
	}

	private static Vec3 flat(final Vec3 vector) {
		return new Vec3(vector.x, 0.0, vector.z);
	}

	private static String vec(final Vec3 vector) {
		return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vector.x, vector.y, vector.z);
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
