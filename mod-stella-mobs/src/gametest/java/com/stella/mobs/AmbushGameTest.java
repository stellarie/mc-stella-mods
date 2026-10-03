package com.stella.mobs;

import com.stella.mobs.behavior.AmbushGoal;
import com.stella.mobs.behavior.CoverSearch;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.Gaze;
import com.stella.mobs.behavior.RangedReach;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.ShotWatch;
import com.stella.mobs.behavior.Stance;
import com.stella.mobs.behavior.StrayKit;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ranged units ambush. Each case puts a stray, or another shooter, and a survival player on a wide floor, and the stray
 * stands in the open with a stone wall beside it. The player is a headless mock: it never turns or walks by itself, so the
 * driver sets its yaw, pitch, and head yaw on every tick to fix the gaze, and sets its position and known movement to make it
 * chase. A shot is read where it appears, with the seen state of the player at that moment. The hide cycle is read from the
 * recorder of the ambush goal. Every time counts the mob's own ticks, because the level clock barely moves in a test.
 *
 * <p>The subject is a stray. Its vanilla bow goal strafes on its own, which suits a unit that has to leave its spot and come
 * back. A plain skeleton would stand still while it shoots, because the mod's volley goal replaces the vanilla goal for it.
 * Two scenes share a floor at most. They stand 40 blocks apart, beyond the follow range of 32, so no stray takes the other
 * scene's player for a target and no stance resets on a new target.
 */
public class AmbushGameTest {
	private static final String TEST = "ambush";
	private static final int MAX_TICKS = StanceGameTest.READY_TICKS + 700;
	private static final int HEARTBEAT_TICKS = 5;
	private static final double CENTER = WideArena.SIZE / 2.0;
	// Block centres, so the stray stands in one block.
	private static final double STRAY_X = CENTER + 0.5;
	private static final double WEST_X = 8.5;
	private static final double EAST_X = 48.5;
	private static final double STRAY_Z = 10.5;
	/** The plan puts the player 20 blocks from the stray. */
	private static final double SHOT_DISTANCE = 20.0;
	private static final int WALL_WEST_GAP = 2;
	private static final int WALL_WIDTH = 7;
	private static final int WALL_HEIGHT = 4;
	private static final int WALL_Z = (int) STRAY_Z;
	/** Yaw 0 looks toward +Z, and every stray here stands north of its player. */
	private static final float AWAY_YAW = 0.0F;
	private static final int SHOT_WINDOW = 200;
	/** The plan: within 80 ticks of the opening shot the stray is out of sight. */
	private static final int HIDE_WINDOW = 80;
	/** The plan: within 200 ticks of hiding the stray is back in sight and fires again. */
	private static final int RECOVER_WINDOW = 200;
	/** The mark is read this many ticks either side of the forget time. */
	private static final int MARK_SLACK = 10;
	private static final double CHASE_SPEED = 0.2;
	private static final double CHASE_GAP = 14.0;
	/** The player closes to here and holds: inside the pursued radius of 8, and outside the near band of 5. */
	private static final double CHASE_STOP = RoleSpec.RANGED_NEAR + 1.0;
	private static final double CHASE_STRAY_Z = 24.5;
	private static final int CHASE_WINDOW = 100;
	private static final int KITE_WINDOW = 40;
	private static final double HURT_SHARE = 0.30;
	/** A stray strafes and a goal takes a tick or two to react, so the band holds within a block. */
	private static final double BAND_SLACK = 1.0;
	private static final int EXCLUSION_WINDOW = 100;
	private static final double CLAIM_SHOT_DISTANCE = 16.0;

	private enum Look {
		AWAY,
		AT_MOB
	}

	/** One projectile as it appeared, and what the player saw at that moment. */
	private record ShotLog(int ownerTick, boolean seenBy, boolean seenMark, double distance) {
	}

	/** One phase change of the ambush goal, on the mob's own tick. */
	private record PhaseEvent(int mobTick, AmbushGoal.Phase from, AmbushGoal.Phase to, @Nullable Vec3 spot) {
	}

	private record Watch(Supplier<ServerPlayer> player, List<ShotLog> shots) {
	}

	private static final Map<UUID, Watch> WATCHED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<PhaseEvent>> PHASES = new ConcurrentHashMap<>();

	static {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Projectile projectile) || !(projectile.getOwner() instanceof Mob owner)) {
				return;
			}

			Watch watch = WATCHED.get(owner.getUUID());
			ServerPlayer player = watch == null ? null : watch.player().get();
			if (player != null) {
				watch.shots().add(new ShotLog(owner.tickCount, Gaze.seenBy(player, owner), ShotWatch.seenMark(owner), owner.distanceTo(player)));
			}
		});
		AmbushGoal.setRecorder((mob, from, to, spot) -> {
			PHASES.computeIfAbsent(mob.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new PhaseEvent(mob.tickCount, from, to, spot));
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} PHASE mob={} from={} to={} tick={} spot={}",
				TEST, mob.getUUID(), from, to, mob.tickCount, spot == null ? "none" : vec(spot)
			);
		});
	}

	/** One mob, one player, and what the driver measured of them. */
	private static final class Scene {
		private final String name;
		private final double x;
		private final double mobZ;
		private final double playerZ;
		private volatile ServerPlayer player;
		private volatile Mob mob;
		private boolean live;
		private boolean done;
		private int ticks;
		private double closest = Double.MAX_VALUE;
		private int kiteAt = -1;
		private int retreatTicks;

		private Scene(final String name, final double x, final double mobZ, final double playerZ) {
			this.name = name;
			this.x = x;
			this.mobZ = mobZ;
			this.playerZ = playerZ;
		}

		/** The spots of the scene in the test's frame. Each must stand in a chunk that ticks. */
		private List<Vec3> spots() {
			return List.of(
				new Vec3(this.x, 1.0, this.mobZ), new Vec3(this.x, 1.0, this.playerZ),
				new Vec3(this.x + 3.5, 1.0, this.mobZ - 1.5), new Vec3(this.x, 1.0, this.mobZ - 10.0)
			);
		}

		private <T extends Mob> void populate(final WideArena arena, final EntityType<T> type, final Consumer<T> gear) {
			ServerPlayer survivor = arena.survivalPlayer(this.x, this.playerZ);
			// No mob may target the player while it falls.
			survivor.setPermanentlyInvulnerable(true);
			this.player = survivor;
			this.mob = arena.spawn(type, this.x, this.mobZ, gear);
			WATCHED.put(this.mob.getUUID(), new Watch(() -> survivor, new CopyOnWriteArrayList<>()));
		}

		/**
		 * Holds the player's gaze and the mob's target for this tick. It answers false until the mob stands on the ground,
		 * because a goal that starts in the air wastes its window.
		 */
		private boolean step(final Look look) {
			return this.step(look, true);
		}

		/**
		 * The same step, and with `keepTarget` false the mob's target is set on its first tick only. Then the mob's own target
		 * goal keeps or drops the player, as it does in play.
		 */
		private boolean step(final Look look, final boolean keepTarget) {
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

			if (keepTarget || this.ticks == 0) {
				mob.setTarget(player);
			}

			gaze(player, mob, look);
			this.ticks++;
			this.closest = Math.min(this.closest, mob.distanceTo(player));
			if (Stance.of(mob) == Stance.KITE && this.kiteAt < 0) {
				this.kiteAt = this.ticks;
			}

			if (running(mob, RetreatGoal.class)) {
				this.retreatTicks++;
			}

			if (this.ticks % HEARTBEAT_TICKS == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} t={} mobTick={} dist={} stance={} blocked={} phase={} shots={} goals={}",
					TEST, this.name, this.ticks, mob.tickCount, number(mob.distanceTo(player)), Stance.of(mob), blocked(player, mob),
					lastPhase(mob), shots(mob).size(), runningGoals(mob)
				);
			}

			return true;
		}

		/** The chase: the player walks at the mob and reports the walk, as a client would, and holds at `CHASE_STOP`. */
		private boolean stepChase() {
			if (!this.step(Look.AWAY)) {
				return false;
			}

			Vec3 toMob = flat(this.mob.position().subtract(this.player.position()));
			Vec3 move = toMob.length() <= CHASE_STOP ? Vec3.ZERO : toMob.normalize().scale(CHASE_SPEED);
			this.player.setPos(this.player.getX() + move.x, this.player.getY(), this.player.getZ() + move.z);
			this.player.setKnownMovement(move);
			return true;
		}

		private String describe() {
			Mob mob = this.mob;
			ServerPlayer player = this.player;
			return "ticks=" + this.ticks + " mobTick=" + mob.tickCount + " pos=" + vec(mob.position()) + " onGround=" + mob.onGround()
				+ " distance=" + number(mob.distanceTo(player)) + " blocked=" + blocked(player, mob) + " seenBy=" + Gaze.seenBy(player, mob)
				+ " seenMark=" + ShotWatch.seenMark(mob) + " shots=" + shots(mob).size() + " phases=" + phases(mob)
				+ " stance=" + Stance.of(mob) + " goals=" + runningGoals(mob);
		}

		private void result(final String outcome) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {} {}", TEST, this.name, outcome, this.describe());
		}
	}

	/**
	 * An unseen opening shot starts a hide. The player faces away, so the stray fires while the player does not see it, and
	 * within 80 ticks a ray from the player's eye to the stray's eye hits a block. The spot that the stray chose lies out of
	 * the player's sight and inside the band of the cover search.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void unseenShotThenHides(final GameTestHelper helper) {
		hidesAfterAnUnseenShot(helper, "unseenShotThenHides", EntityTypes.STRAY);
	}

	/**
	 * The same hide for a plain skeleton, the archer that a player meets most. Its volley goal replaces the vanilla bow goal
	 * and holds still while it shoots, so it is the other weapon goal that the hide must preempt.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void skeletonHidesAfterAnUnseenShot(final GameTestHelper helper) {
		hidesAfterAnUnseenShot(helper, "skeletonHidesAfterAnUnseenShot", EntityTypes.SKELETON);
	}

	private static <T extends Mob> void hidesAfterAnUnseenShot(final GameTestHelper helper, final String name, final EntityType<T> type) {
		Scene scene = coverScene(helper, name, type);
		int[] hiddenAt = {-1};
		boolean[] exposed = {false};
		helper.onEachTick(() -> {
			if (!scene.step(Look.AWAY)) {
				return;
			}

			List<ShotLog> shots = shots(scene.mob);
			if (shots.isEmpty()) {
				helper.assertTrue(scene.ticks < SHOT_WINDOW, "the unit loosed no arrow in " + SHOT_WINDOW + " ticks: " + scene.describe());
				return;
			}

			ShotLog shot = shots.get(0);
			if (!exposed[0]) {
				exposed[0] = true;
				helper.assertTrue(!blocked(scene.player, scene.mob), "the unit fired from cover, so hiding proves nothing: " + scene.describe());
			}

			int sinceShot = scene.mob.tickCount - shot.ownerTick();
			if (hiddenAt[0] < 0) {
				if (blocked(scene.player, scene.mob)) {
					hiddenAt[0] = scene.mob.tickCount;
				} else {
					helper.assertTrue(
						sinceShot <= HIDE_WINDOW,
						"the unit was in plain sight " + sinceShot + " ticks after its opening shot, past " + HIDE_WINDOW + ": " + scene.describe()
					);
					return;
				}
			}

			scene.done = true;
			PhaseEvent hide = firstChange(scene.mob, AmbushGoal.Phase.HIDE);
			scene.result("hiddenAfter=" + (hiddenAt[0] - shot.ownerTick()) + " seenByAtShot=" + shot.seenBy() + " seenMarkAtShot=" + shot.seenMark());
			helper.assertTrue(!shot.seenBy(), "the player saw the unit at its opening shot, so the shot was not unseen: " + scene.describe());
			helper.assertTrue(hide != null && hide.spot() != null, "the unit left sight with no hide phase recorded: " + scene.describe());
			Vec3 spot = hide.spot();
			double spotDistance = spot.distanceTo(scene.player.position());
			helper.assertTrue(
				spotDistance >= RoleSpec.RANGED_NEAR + CoverSearch.BAND_MARGIN && spotDistance <= RangedReach.RANGE,
				"the cover spot lies " + number(spotDistance) + " blocks from the player, outside the band: " + scene.describe()
			);
			helper.assertTrue(
				hiddenFrom(scene.player, spot.add(0.0, scene.mob.getEyeHeight(), 0.0)),
				"the cover spot " + vec(spot) + " is in the player's sight: " + scene.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * An archer hides after a shot that the player sees, too. Decision D9 now holds only for a unit without a bow or a
	 * crossbow. The scene is the one of the unseen case, with the player turned toward the stray. The stray fires with the
	 * player watching, and starts a hide within 80 ticks.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void seenShotHidesAnArcher(final GameTestHelper helper) {
		Scene scene = coverScene(helper, "seenShotHidesAnArcher");
		helper.onEachTick(() -> {
			if (!scene.step(Look.AT_MOB)) {
				return;
			}

			if (scene.ticks == 1) {
				helper.assertTrue(
					CoverSearch.hidden(scene.mob, scene.player).isPresent(), "the scene has no cover within reach, so it proves nothing: " + scene.describe()
				);
			}

			List<ShotLog> shots = shots(scene.mob);
			if (shots.isEmpty()) {
				helper.assertTrue(scene.ticks < SHOT_WINDOW, "the stray loosed no arrow in " + SHOT_WINDOW + " ticks: " + scene.describe());
				return;
			}

			ShotLog shot = shots.get(0);
			PhaseEvent hide = firstChange(scene.mob, AmbushGoal.Phase.HIDE);
			if (hide == null) {
				helper.assertTrue(
					scene.mob.tickCount - shot.ownerTick() <= HIDE_WINDOW,
					"the stray did not hide within " + HIDE_WINDOW + " ticks of a seen shot: " + scene.describe()
				);
				return;
			}

			scene.done = true;
			scene.result("hideAfter=" + (hide.mobTick() - shot.ownerTick()) + " seenByAtShot=" + shot.seenBy() + " seenMarkAtShot=" + shot.seenMark());
			helper.assertTrue(shot.seenBy(), "the player did not see the stray at its shot, so the case proves nothing: " + scene.describe());
			helper.succeed();
		});
	}

	/**
	 * The seen mark outlasts the look and then clears. The player watches the stray until it has fired once, then turns
	 * away. The mark still holds 10 ticks before 100 ticks have passed without a sighting, and it is clear 10 ticks after.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void seenMarkClearsAfterAHundredTicks(final GameTestHelper helper) {
		Scene scene = coverScene(helper, "seenMarkClearsAfterAHundredTicks");
		int[] turnedAt = {-1};
		boolean[] heldLate = {false};
		boolean[] sampled = {false};
		helper.onEachTick(() -> {
			if (!scene.step(turnedAt[0] < 0 ? Look.AT_MOB : Look.AWAY)) {
				return;
			}

			List<ShotLog> shots = shots(scene.mob);
			if (turnedAt[0] < 0) {
				helper.assertTrue(scene.ticks < SHOT_WINDOW, "the stray loosed no arrow in " + SHOT_WINDOW + " ticks: " + scene.describe());
				if (!shots.isEmpty()) {
					turnedAt[0] = scene.mob.tickCount;
					helper.assertTrue(shots.get(0).seenMark(), "the first shot was not a seen shot, so the case proves nothing: " + scene.describe());
				}

				return;
			}

			int since = scene.mob.tickCount - turnedAt[0];
			if (!sampled[0] && since >= ShotWatch.FORGET_TICKS - MARK_SLACK) {
				sampled[0] = true;
				heldLate[0] = ShotWatch.seenMark(scene.mob);
			}

			if (since < ShotWatch.FORGET_TICKS + MARK_SLACK) {
				return;
			}

			scene.done = true;
			boolean clear = !ShotWatch.seenMark(scene.mob);
			scene.result("turnedAt=" + turnedAt[0] + " heldLate=" + heldLate[0] + " clear=" + clear);
			helper.assertTrue(heldLate[0], "the mark cleared before " + ShotWatch.FORGET_TICKS + " ticks without a sighting: " + scene.describe());
			helper.assertTrue(clear, "the mark held past " + ShotWatch.FORGET_TICKS + " ticks without a sighting: " + scene.describe());
			helper.succeed();
		});
	}

	/**
	 * A hide ends. The stray waits out its cover, walks back into view, and fires again within 200 ticks of hiding. A unit
	 * that never leaves its cover stops fighting. The driver sets the target once, so the stray's own target goal keeps the
	 * player and forgets it as it does in play: the goal drops a player that it has not seen for 60 ticks, which is shorter
	 * than the longest wait. The hide goal must set the target again, or the stray never fires after a long wait.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void peeksAndFiresAgain(final GameTestHelper helper) {
		Scene scene = coverScene(helper, "peeksAndFiresAgain");
		int[] hiddenAt = {-1};
		int[] regainedAt = {-1};
		helper.onEachTick(() -> {
			if (!scene.step(Look.AWAY, false)) {
				return;
			}

			List<ShotLog> shots = shots(scene.mob);
			if (shots.isEmpty()) {
				helper.assertTrue(scene.ticks < SHOT_WINDOW, "the stray loosed no arrow in " + SHOT_WINDOW + " ticks: " + scene.describe());
				return;
			}

			int now = scene.mob.tickCount;
			boolean blocked = blocked(scene.player, scene.mob);
			if (hiddenAt[0] < 0) {
				if (blocked) {
					hiddenAt[0] = now;
				} else {
					helper.assertTrue(
						now - shots.get(0).ownerTick() <= HIDE_WINDOW, "the stray never hid after its opening shot: " + scene.describe()
					);
				}

				return;
			}

			if (regainedAt[0] < 0 && !blocked) {
				regainedAt[0] = now;
			}

			int firedAgain = shots.stream().filter(shot -> shot.ownerTick() > hiddenAt[0]).findFirst().map(ShotLog::ownerTick).orElse(-1);
			if (firedAgain < 0) {
				helper.assertTrue(
					now - hiddenAt[0] <= RECOVER_WINDOW,
					"the stray stayed hidden " + (now - hiddenAt[0]) + " ticks and did not fire again: regainedAt=" + regainedAt[0] + " " + scene.describe()
				);
				return;
			}

			scene.done = true;
			scene.result("hiddenAt=" + hiddenAt[0] + " regainedAfter=" + (regainedAt[0] - hiddenAt[0]) + " firedAgainAfter=" + (firedAgain - hiddenAt[0]));
			helper.assertTrue(regainedAt[0] >= 0 && regainedAt[0] - hiddenAt[0] <= RECOVER_WINDOW, "the stray did not regain sight in " + RECOVER_WINDOW + " ticks: " + scene.describe());
			helper.assertTrue(firedAgain - hiddenAt[0] <= RECOVER_WINDOW, "the stray fired again " + (firedAgain - hiddenAt[0]) + " ticks after it hid, past " + RECOVER_WINDOW + ": " + scene.describe());
			helper.succeed();
		});
	}

	/**
	 * A pursued unit with no cover follows the stance script. The floor is open, so the cover search finds nothing. The
	 * player closes from 14 blocks at 0.2 blocks per tick and holds 6 blocks from the stray, which is inside the pursued
	 * radius and outside the near band. A healthy stray holds its band and fires. A stray at 30 percent health is in KITE
	 * within 40 ticks. Neither unit freezes, and neither one hides.
	 *
	 * <p>The player must hold. A stray walks about as fast as a player closes at 0.2 blocks per tick, and its band goal rests
	 * for 80 ticks after each retreat, so a player who never stops overruns any band. The plan's 14 blocks, 0.2 blocks per
	 * tick, 40 ticks, and 100 ticks fit together only when the player stops near the pursued radius.
	 *
	 * <p>The pursued radius is 8 blocks, the retreat gap, and not the plan's 7. A retreat run holds the gap near 7.2 blocks
	 * for up to 20 ticks, because the stray flees about as fast as the player closes. With a radius of 7 the hurt stray
	 * reached KITE on tick 25 to 43 in twelve runs, past the limit once.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void pursuedWithoutCoverFollowsTheScript(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene healthy = new Scene("pursuedWithoutCover.healthy", WEST_X, CHASE_STRAY_Z, CHASE_STRAY_Z + CHASE_GAP);
		Scene hurt = new Scene("pursuedWithoutCover.hurt", EAST_X, CHASE_STRAY_Z, CHASE_STRAY_Z + CHASE_GAP);
		List<Vec3> spots = new ArrayList<>(healthy.spots());
		spots.addAll(hurt.spots());
		StanceGameTest.whenTicking(helper, spots, () -> {
			healthy.populate(arena, EntityTypes.STRAY, AmbushGameTest::bow);
			hurt.populate(arena, EntityTypes.STRAY, AmbushGameTest::bow);
			hurt.mob.setHealth((float) (hurt.mob.getMaxHealth() * HURT_SHARE));
		});
		helper.onEachTick(() -> {
			healthy.stepChase();
			hurt.stepChase();
			if (healthy.ticks < CHASE_WINDOW || hurt.ticks < CHASE_WINDOW || healthy.done) {
				return;
			}

			healthy.done = true;
			hurt.done = true;
			int arrows = shots(healthy.mob).size();
			healthy.result("healthy closest=" + number(healthy.closest) + " arrows=" + arrows);
			hurt.result("hurt kiteAt=" + hurt.kiteAt + " retreatTicks=" + hurt.retreatTicks);
			helper.assertTrue(
				healthy.closest >= RoleSpec.RANGED_NEAR - BAND_SLACK,
				"the healthy stray let the player to " + number(healthy.closest) + " blocks, inside its band of " + RoleSpec.RANGED_NEAR + ": " + healthy.describe()
			);
			helper.assertTrue(arrows >= 1, "the healthy stray loosed no arrow in " + CHASE_WINDOW + " ticks: " + healthy.describe());
			helper.assertTrue(
				hurt.kiteAt >= 0 && hurt.kiteAt <= KITE_WINDOW,
				"the stray at 30 percent health was not in KITE within " + KITE_WINDOW + " ticks, kiteAt=" + hurt.kiteAt + ": " + hurt.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * Two units never ambush. An elite sniper stray fires an unseen shot with cover beside it, and a melee skeleton looses a
	 * stray arrow with cover beside it. Neither unit starts a hide phase. The cover exists for both, as the cover search
	 * proves, so a unit that hid would show it.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void exclusions(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildWall(helper, WEST_X);
		buildWall(helper, EAST_X);
		Scene sniper = new Scene("exclusions.sniper", WEST_X, STRAY_Z, STRAY_Z + SHOT_DISTANCE);
		Scene melee = new Scene("exclusions.melee", EAST_X, STRAY_Z, STRAY_Z + SHOT_DISTANCE);
		List<Vec3> spots = new ArrayList<>(sniper.spots());
		spots.addAll(melee.spots());
		StanceGameTest.whenTicking(helper, spots, () -> {
			sniper.populate(arena, EntityTypes.STRAY, stray -> {
				bow(stray);
				Elite.mark(stray, GearTier.IRON, 1.0);
			});
			melee.populate(arena, EntityTypes.SKELETON, AmbushGameTest::sword);
		});
		int[] sniperShotAt = {-1};
		int[] meleeShotAt = {-1};
		helper.onEachTick(() -> {
			if (sniper.done) {
				return;
			}

			if (sniper.step(Look.AWAY)) {
				if (sniper.ticks == 1) {
					helper.assertTrue(StrayKit.isSolitary(sniper.mob), "the stray is not an elite sniper, so the case proves nothing: " + sniper.describe());
					helper.assertTrue(CoverSearch.hidden(sniper.mob, sniper.player).isPresent(), "the sniper has no cover within reach: " + sniper.describe());
				}

				if (sniperShotAt[0] < 0 && !shots(sniper.mob).isEmpty()) {
					sniperShotAt[0] = sniper.mob.tickCount;
				}
			}

			if (melee.step(Look.AWAY)) {
				if (melee.ticks == 1) {
					helper.assertTrue(
						ReactiveState.get(melee.mob).getRole() == CombatRole.MELEE, "the skeleton is not a melee unit, so the case proves nothing: " + melee.describe()
					);
					helper.assertTrue(CoverSearch.hidden(melee.mob, melee.player).isPresent(), "the skeleton has no cover within reach: " + melee.describe());
				}

				if (melee.ticks == 2) {
					loose(melee.mob, melee.player);
					meleeShotAt[0] = melee.mob.tickCount;
				}
			}

			if (sniper.live && sniperShotAt[0] < 0) {
				helper.assertTrue(sniper.ticks < SHOT_WINDOW, "the sniper never fired: " + sniper.describe());
			}

			boolean sniperDone = sniperShotAt[0] >= 0 && sniper.mob.tickCount - sniperShotAt[0] >= EXCLUSION_WINDOW;
			boolean meleeDone = meleeShotAt[0] >= 0 && melee.mob.tickCount - meleeShotAt[0] >= EXCLUSION_WINDOW;
			if (!sniperDone || !meleeDone) {
				return;
			}

			sniper.done = true;
			melee.done = true;
			sniper.result("sniperShots=" + shots(sniper.mob).size() + " watched=" + ShotWatch.shotCount(sniper.mob));
			melee.result("meleeShots=" + shots(melee.mob).size() + " watched=" + ShotWatch.shotCount(melee.mob));
			helper.assertTrue(!shots(sniper.mob).get(0).seenBy(), "the sniper shot while the player saw it, so the case proves nothing: " + sniper.describe());
			helper.assertTrue(phases(sniper.mob).isEmpty(), "the elite sniper started an ambush: " + sniper.describe());
			helper.assertTrue(ShotWatch.shotCount(melee.mob) >= 1, "the melee skeleton's arrow was not recorded as a shot: " + melee.describe());
			helper.assertTrue(phases(melee.mob).isEmpty(), "the melee skeleton started an ambush: " + melee.describe());
			helper.succeed();
		});
	}

	/**
	 * The seen state is a cone of 55 degrees on each side of the player's view, with a clear line. A stray 50 degrees off the
	 * view is seen, a stray 60 degrees off is not, and a block between the two eyes hides it at any angle.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void seenByUsesTheWideCone(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("seenByUsesTheWideCone", STRAY_X, STRAY_Z, STRAY_Z + SHOT_DISTANCE);
		StanceGameTest.whenTicking(helper, scene.spots(), () -> {
			scene.populate(arena, EntityTypes.STRAY, AmbushGameTest::bow);
		});
		helper.onEachTick(() -> {
			if (scene.mob == null || !scene.mob.onGround()) {
				return;
			}

			// The stray lies due north, which is yaw 180.
			float inside = 180.0F - (float) (Gaze.SEEN_HALF_ANGLE - 5.0);
			float outside = 180.0F - (float) (Gaze.SEEN_HALF_ANGLE + 5.0);
			boolean ahead = seenAt(scene, 180.0F);
			boolean near = seenAt(scene, inside);
			boolean far = seenAt(scene, outside);
			boolean behind = seenAt(scene, AWAY_YAW);
			helper.setBlock((int) STRAY_X, 1, (int) STRAY_Z + 10, Blocks.STONE);
			helper.setBlock((int) STRAY_X, 2, (int) STRAY_Z + 10, Blocks.STONE);
			helper.setBlock((int) STRAY_X, 3, (int) STRAY_Z + 10, Blocks.STONE);
			boolean walled = seenAt(scene, 180.0F);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={}.seenByUsesTheWideCone RESULT ahead={} insideCone={} outsideCone={} behind={} walled={}",
				TEST, ahead, near, far, behind, walled
			);
			helper.assertTrue(ahead && near, "the player did not see a stray inside the cone: ahead=" + ahead + " inside=" + near);
			helper.assertTrue(!far && !behind, "the player saw a stray outside the cone: outside=" + far + " behind=" + behind);
			helper.assertTrue(!walled, "the player saw a stray through a wall");
			helper.succeed();
		});
	}

	/**
	 * Two ambushers do not claim spots within 2 blocks of each other. The second stray stands beside the first, so left alone
	 * it picks a spot that lies within 2 blocks of the first one's. After the first claims its spot, the second picks a spot
	 * that lies 2 blocks or more away.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void ambushersClaimApartSpots(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildWall(helper, STRAY_X);
		Scene first = new Scene("ambushersClaimApartSpots.first", STRAY_X - 0.2, STRAY_Z, STRAY_Z + CLAIM_SHOT_DISTANCE);
		Scene second = new Scene("ambushersClaimApartSpots.second", STRAY_X + 0.4, STRAY_Z, STRAY_Z + CLAIM_SHOT_DISTANCE);
		List<Vec3> spots = new ArrayList<>(first.spots());
		spots.addAll(second.spots());
		StanceGameTest.whenTicking(helper, spots, () -> {
			first.populate(arena, EntityTypes.STRAY, AmbushGameTest::bow);
			second.populate(arena, EntityTypes.STRAY, AmbushGameTest::bow);
		});
		helper.onEachTick(() -> {
			if (first.mob == null || second.mob == null || !first.mob.onGround() || !second.mob.onGround()) {
				return;
			}

			WideArena.keepValid(first.player);
			Optional<Vec3> firstSpot = CoverSearch.hidden(first.mob, first.player);
			Optional<Vec3> aloneSpot = CoverSearch.hidden(second.mob, first.player);
			helper.assertTrue(firstSpot.isPresent() && aloneSpot.isPresent(), "a stray found no cover, so the case proves nothing: first=" + firstSpot + " second=" + aloneSpot);
			double alone = firstSpot.get().distanceTo(aloneSpot.get());
			CoverSearch.claim(first.mob, firstSpot.get());
			Optional<Vec3> claimedSpot = CoverSearch.hidden(second.mob, first.player);
			CoverSearch.release(first.mob);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={}.ambushersClaimApartSpots RESULT first={} aloneSecond={} aloneGap={} claimedSecond={}",
				TEST, vec(firstSpot.get()), vec(aloneSpot.get()), number(alone), claimedSpot.map(AmbushGameTest::vec).orElse("none")
			);
			helper.assertTrue(
				alone < CoverSearch.CLAIM_SPACING,
				"the second stray picked a spot " + number(alone) + " blocks from the first, so the claim has nothing to separate"
			);
			helper.assertTrue(claimedSpot.isPresent(), "the second stray found no spot once the first claimed one");
			double apart = firstSpot.get().distanceTo(claimedSpot.get());
			helper.assertTrue(apart >= CoverSearch.CLAIM_SPACING, "the second stray claimed a spot " + number(apart) + " blocks from the first, inside " + CoverSearch.CLAIM_SPACING);
			helper.succeed();
		});
	}

	/** The stray, the player, and the wall of the cover cases. */
	private static Scene coverScene(final GameTestHelper helper, final String name) {
		return coverScene(helper, name, EntityTypes.STRAY);
	}

	private static <T extends Mob> Scene coverScene(final GameTestHelper helper, final String name, final EntityType<T> type) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		buildWall(helper, STRAY_X);
		Scene scene = new Scene(name, STRAY_X, STRAY_Z, STRAY_Z + SHOT_DISTANCE);
		StanceGameTest.whenTicking(helper, scene.spots(), () -> scene.populate(arena, type, AmbushGameTest::bow));
		return scene;
	}

	/**
	 * A stone wall east of the stray, beside it and not between it and the player. The player stands due south of the stray,
	 * so the wall's shadow falls north and east of the wall, and a spot there is out of the player's sight.
	 */
	private static void buildWall(final GameTestHelper helper, final double strayX) {
		int from = (int) Math.floor(strayX) + WALL_WEST_GAP;
		for (int x = from; x < from + WALL_WIDTH && x < WideArena.SIZE; x++) {
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}
		}
	}

	private static boolean seenAt(final Scene scene, final float yaw) {
		scene.player.setYRot(yaw);
		scene.player.setXRot(0.0F);
		scene.player.setYHeadRot(yaw);
		return Gaze.seenBy(scene.player, scene.mob);
	}

	/** A stray arrow that a melee skeleton looses at its target. */
	private static void loose(final Mob shooter, final ServerPlayer target) {
		ItemStack ammunition = new ItemStack(Items.ARROW);
		Arrow arrow = new Arrow(shooter.level(), shooter, ammunition, null);
		Vec3 aim = target.getEyePosition().subtract(arrow.position());
		Projectile.spawnProjectileUsingShoot(arrow, (ServerLevel) shooter.level(), ammunition, aim.x, aim.y, aim.z, 1.6F, 0.0F);
	}

	private static void gaze(final ServerPlayer player, final Mob mob, final Look look) {
		if (look == Look.AT_MOB) {
			MeleeKitFloor.lookAt(player, mob);
		} else {
			player.setYRot(AWAY_YAW);
			player.setXRot(0.0F);
			player.setYHeadRot(AWAY_YAW);
		}
	}

	/** True when a block stands between the player's eye and the point. */
	private static boolean hiddenFrom(final ServerPlayer player, final Vec3 point) {
		ClipContext context = new ClipContext(player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		return player.level().clip(context).getType() != HitResult.Type.MISS;
	}

	private static boolean blocked(final ServerPlayer player, final Mob mob) {
		return hiddenFrom(player, mob.getEyePosition());
	}

	private static List<ShotLog> shots(final Mob mob) {
		Watch watch = WATCHED.get(mob.getUUID());
		return watch == null ? List.of() : List.copyOf(watch.shots());
	}

	private static List<PhaseEvent> phases(final Mob mob) {
		return List.copyOf(PHASES.getOrDefault(mob.getUUID(), List.of()));
	}

	private static AmbushGoal.Phase lastPhase(final Mob mob) {
		List<PhaseEvent> phases = phases(mob);
		return phases.isEmpty() ? AmbushGoal.Phase.STALK : phases.get(phases.size() - 1).to();
	}

	/** The first change into `phase`, or null. */
	private static @Nullable PhaseEvent firstChange(final Mob mob, final AmbushGoal.Phase phase) {
		return phases(mob).stream().filter(event -> event.to() == phase).findFirst().orElse(null);
	}

	private static void bow(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	private static void sword(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
	}

	private static boolean running(final Mob mob, final Class<? extends Goal> type) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (type.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
				return true;
			}
		}

		return false;
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
