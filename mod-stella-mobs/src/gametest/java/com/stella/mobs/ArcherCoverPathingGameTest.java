package com.stella.mobs;

import com.stella.mobs.behavior.AmbushGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An archer walks to cover that it can reach, by the shortest path, and gives up a spot that it cannot walk to.
 *
 * <p>Each case puts a plain skeleton and a survival player 20 blocks apart on the wide floor. The player watches the
 * archer, and an archer hides after every shot. A wall 18 blocks long stands just north of the archer, and a pillar
 * stands to its south east. The wall hides the nearest spots, 3 blocks away in a straight line, but the path to them runs
 * round the wall's end, about 25 blocks. The pillar hides a spot 5 blocks away by a short path.
 */
public class ArcherCoverPathingGameTest {
	private static final String TEST = "archerCoverPathing";
	private static final int MAX_TICKS = StanceGameTest.READY_TICKS + 600;
	private static final int HEARTBEAT_TICKS = 10;
	private static final int CHUNK = 16;
	private static final double MOB_X = 28.5;
	private static final double MOB_Z = 24.5;
	private static final double PLAYER_Z = MOB_Z + 20.0;
	private static final int WALL_Z = 22;
	private static final int WALL_FROM_X = 20;
	private static final int WALL_TO_X = 37;
	private static final int PILLAR_FROM_X = 31;
	private static final int PILLAR_TO_X = 33;
	private static final int PILLAR_Z = 27;
	private static final int COVER_HEIGHT = 4;
	/** First release to a reached spot: the burst, the walk, and some slack. */
	private static final int REACH_WINDOW = 200;
	private static final int GIVE_UP_TICKS = AmbushGoal.PROGRESS_TICKS;
	private static final int GIVE_UP_SLACK = 10;
	private static final int FIGHT_ON_WINDOW = 120;

	/** One archer and one player, and the driver's count of the fight. */
	private static final class Scene {
		private final String name;
		private ServerPlayer player;
		private Mob mob;
		private boolean live;
		private boolean done;
		private int ticks;

		private Scene(final String name) {
			this.name = name;
		}

		/** The scene's spots and every chunk of the floor, so no chunk still loads its entities. */
		private List<Vec3> spots() {
			List<Vec3> spots = new ArrayList<>(List.of(new Vec3(MOB_X, 1.0, MOB_Z), new Vec3(MOB_X, 1.0, PLAYER_Z)));
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
			ServerPlayer survivor = arena.survivalPlayer(MOB_X, PLAYER_Z);
			// No mob may target the player while it falls.
			survivor.setPermanentlyInvulnerable(true);
			this.player = survivor;
			this.mob = arena.spawn(EntityTypes.SKELETON, MOB_X, MOB_Z, ArcherCoverPathingGameTest::bow);
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
			if (this.ticks % HEARTBEAT_TICKS == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} t={} mobTick={} pos={} hiding={} hidden={} shots={} navTarget={} goals={}",
					TEST, this.name, this.ticks, mob.tickCount, vec(mob.position()), AmbushGoal.repositioning(mob),
					hidden(player, mob), WideArena.shots(mob).size(), mob.getNavigation().getTargetPos(), runningGoals(mob)
				);
			}

			return true;
		}

		private String describe() {
			return "ticks=" + this.ticks + " mobTick=" + this.mob.tickCount + " pos=" + vec(this.mob.position())
				+ " hidden=" + hidden(this.player, this.mob) + " shots=" + WideArena.shots(this.mob).size()
				+ " goals=" + runningGoals(this.mob);
		}

		private void result(final String outcome) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {} {}", TEST, this.name, outcome, this.describe());
		}
	}

	/**
	 * A wall stands between the archer and the nearest hidden spots. The way to them runs round the wall's end, about 25
	 * blocks. The pillar's spot lies a short walk away. After its first release the archer starts a hide on
	 * the near side of the wall, and it stands out of the player's sight there within 200 ticks of that release.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void choosesReachableCover(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildCover(helper);
		Scene scene = new Scene("choosesReachableCover");
		StanceGameTest.whenTicking(helper, scene.spots(), () -> scene.populate(arena));
		BlockPos[] spot = {null};
		boolean[] wasHiding = {false};
		helper.onEachTick(() -> {
			if (!scene.step()) {
				return;
			}

			List<WideArena.Shot> shots = WideArena.shots(scene.mob);
			boolean hiding = AmbushGoal.repositioning(scene.mob);
			if (hiding && !wasHiding[0] && spot[0] == null) {
				spot[0] = scene.mob.getNavigation().getTargetPos();
			}

			wasHiding[0] = hiding;
			if (shots.isEmpty()) {
				return;
			}

			int sinceRelease = scene.mob.tickCount - shots.get(0).ownerTick();
			boolean reached = spot[0] != null && hidden(scene.player, scene.mob)
				&& flat(scene.mob.position().subtract(Vec3.atBottomCenterOf(spot[0]))).length() <= 1.5;
			if (!reached) {
				helper.assertTrue(
					sinceRelease <= REACH_WINDOW,
					"the archer reached no cover within " + REACH_WINDOW + " ticks of its first release: spot=" + spot[0] + " " + scene.describe()
				);
				return;
			}

			scene.done = true;
			scene.result("spot=" + helper.relativePos(spot[0]) + " reachedAfter=" + sinceRelease);
			// The arena may be rotated, so the side is read in the world's frame.
			Vec3 wall = helper.absoluteVec(new Vec3(MOB_X, 1.0, WALL_Z + 0.5));
			Vec3 archerSide = flat(helper.absoluteVec(new Vec3(MOB_X, 1.0, MOB_Z)).subtract(wall));
			boolean sameSide = flat(Vec3.atBottomCenterOf(spot[0]).subtract(wall)).dot(archerSide) > 0.0;
			helper.assertTrue(sameSide, "the archer chose a spot behind the wall: " + helper.relativePos(spot[0]));
			helper.succeed();
		});
	}

	/**
	 * A ring of stone one block high stands round the archer, and the archer cannot jump. The path finder still finds a way
	 * over the ring to the pillar's spot, but the archer cannot walk it. The archer drops the spot within 20 ticks of no
	 * progress, plus slack, and it shoots again.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void stuckArcherGivesUp(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildCover(helper);
		Scene scene = new Scene("stuckArcherGivesUp");
		StanceGameTest.whenTicking(helper, scene.spots(), () -> {
			ringAround(helper);
			scene.populate(arena);
			scene.mob.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0);
		});
		int[] hideStart = {-1};
		int[] hideEnd = {-1};
		helper.onEachTick(() -> {
			if (!scene.step()) {
				return;
			}

			int now = scene.mob.tickCount;
			boolean hiding = AmbushGoal.repositioning(scene.mob);
			if (hideStart[0] < 0) {
				if (hiding) {
					hideStart[0] = now;
				} else {
					helper.assertTrue(scene.ticks < REACH_WINDOW, "the archer started no hide in " + REACH_WINDOW + " ticks: " + scene.describe());
				}

				return;
			}

			if (hideEnd[0] < 0) {
				if (!hiding) {
					hideEnd[0] = now;
				}

				return;
			}

			long shotsAfter = WideArena.shots(scene.mob).stream().filter(shot -> shot.ownerTick() > hideEnd[0]).count();
			if (shotsAfter == 0) {
				helper.assertTrue(
					now - hideEnd[0] <= FIGHT_ON_WINDOW, "the archer did not shoot again after it gave up: " + scene.describe()
				);
				return;
			}

			scene.done = true;
			int held = hideEnd[0] - hideStart[0];
			scene.result("hideStart=" + hideStart[0] + " held=" + held);
			helper.assertTrue(
				held <= GIVE_UP_TICKS + GIVE_UP_SLACK,
				"the archer kept a spot it could not walk to for " + held + " ticks, past " + (GIVE_UP_TICKS + GIVE_UP_SLACK)
			);
			helper.succeed();
		});
	}

	/** The long wall north of the archer and the pillar to its south east. */
	private static void buildCover(final GameTestHelper helper) {
		for (int y = 1; y <= COVER_HEIGHT; y++) {
			for (int x = WALL_FROM_X; x <= WALL_TO_X; x++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}

			for (int x = PILLAR_FROM_X; x <= PILLAR_TO_X; x++) {
				helper.setBlock(x, y, PILLAR_Z, Blocks.STONE);
			}
		}
	}

	/** A one block ring round the archer's spawn block. */
	private static void ringAround(final GameTestHelper helper) {
		int x = (int) Math.floor(MOB_X);
		int z = (int) Math.floor(MOB_Z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(x + dx, 1, z + dz, Blocks.STONE);
				}
			}
		}
	}

	private static boolean hidden(final ServerPlayer player, final Mob mob) {
		ClipContext context = new ClipContext(player.getEyePosition(), mob.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		return player.level().clip(context).getType() != HitResult.Type.MISS;
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

	private static String vec(final @Nullable Vec3 vector) {
		return vector == null ? "none" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vector.x, vector.y, vector.z);
	}
}
