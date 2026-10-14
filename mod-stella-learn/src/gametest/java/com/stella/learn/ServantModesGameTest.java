package com.stella.learn;

import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import com.stella.spells.SpellCasting;
import com.stella.spells.SpellbookItem;
import com.stella.spells.SummonSkeleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The summoned skeleton takes its combat mode from the weapon in its hand, through the mobs mod's role goals.
 * The spells, mobs and learn mods are all loaded here. The staff is a blaze rod, which the test pack lists as one.
 */
public class ServantModesGameTest {
	private static final int SWAP_TICKS = 60;
	private static final int MOVE_TICKS = 160;
	private static final double CLOSE_IN = 3.0;
	private static final double WALK_BLOCKS = 20.0;
	private static final double WALK_SPEED = 0.2;
	private static final double FOLLOW_SLACK = 4.0;
	private static final double TELEPORT_JUMP = 2.0;
	private static final int KITE_TICKS = 200;
	private static final double ARROW_SEARCH = 40.0;

	@GameTest(maxTicks = 400, padding = LearnArena.PADDING)
	public void theServantTakesTheModeOfItsHeldWeapon(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer master = LearnArena.player(helper, 24.5, 24.5);
		Skeleton servant = servant(helper, master);
		LearnArena.whenReady(helper, List.of(master, servant), () -> swap(helper, servant, new ItemStack(Items.IRON_SWORD), CombatRole.MELEE, false,
			() -> swap(helper, servant, new ItemStack(Items.BOW), CombatRole.RANGED, true,
				() -> swap(helper, servant, new ItemStack(Items.CROSSBOW), CombatRole.RANGED, true,
					() -> swap(helper, servant, new ItemStack(Items.BLAZE_ROD), CombatRole.MAGIC, true,
						() -> swap(helper, servant, ItemStack.EMPTY, CombatRole.MELEE, false, () -> finish(helper, servant)))))));
	}

	@GameTest(maxTicks = 500, padding = LearnArena.PADDING)
	public void aRangedServantKeepsOffAFoeAndASwordServantClosesIn(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer master = LearnArena.player(helper, 24.5, 24.5);
		ServerPlayer stranger = LearnArena.player(helper, 30.5, 24.5);
		Skeleton servant = servant(helper, master);
		Vec3 at = helper.absoluteVec(new Vec3(24.5, LearnArena.SPAWN_Y, 28.5));
		servant.teleportTo(at.x, at.y, at.z);
		Mob foe = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 31).spawn();
		foe.setNoAi(true);
		// A mob never targets an invulnerable body, so the foe is only too tough to kill.
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		foe.setHealth(100_000.0F);
		foe.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		final boolean[] turned = {false};
		helper.onEachTick(() -> {
			turned[0] |= servant.getTarget() instanceof Player;
			stranger.setInvulnerableTime(0);
		});
		LearnArena.whenReady(helper, List.of(master, servant, foe), () -> {
			// A bow in hand, and a foe three blocks off: inside the ranged band's near edge.
			SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			helper.runAfterDelay(5L, () -> servant.hurtServer(helper.getLevel(), stranger.damageSources().playerAttack(stranger), 1.0F));
			LearnArena.pollUntil(helper, () -> servant.distanceTo(foe) >= RoleSpec.RANGED_NEAR - 0.25, MOVE_TICKS, () -> {
				double kept = servant.distanceTo(foe);
				SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				LearnArena.pollUntil(helper, () -> servant.distanceTo(foe) <= CLOSE_IN, MOVE_TICKS, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aRangedServantKeepsOffAFoeAndASwordServantClosesIn RESULT kept={} closed={} turnedOnPlayer={}",
						kept, servant.distanceTo(foe), turned[0]);
					helper.assertFalse(turned[0], "the servant targeted a player");
					finish(helper, servant);
				}, () -> "the servant with a sword stayed " + servant.distanceTo(foe) + " blocks from its foe");
			}, () -> "the servant with a bow stood " + servant.distanceTo(foe) + " blocks from a foe inside its near band");
		});
	}

	/**
	 * Out of combat the servant is a follower: it holds no band, no standoff and no shot, and it keeps pace with a master
	 * who walks 20 blocks. Given a foe it kites in steps: it walks a step, stops and looses a shot.
	 */
	@GameTest(maxTicks = 700, padding = LearnArena.PADDING, required = false)
	public void aRangedServantFollowsItsMasterThenKitesAFoe(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer master = LearnArena.player(helper, 4.5, 24.5);
		Skeleton servant = servant(helper, master);
		Vec3 beside = helper.absoluteVec(new Vec3(6.5, LearnArena.SPAWN_Y, 24.5));
		servant.teleportTo(beside.x, beside.y, beside.z);
		SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Vec3 start = helper.absoluteVec(new Vec3(4.5, 1.0, 24.5));
		Vec3 end = helper.absoluteVec(new Vec3(4.5 + WALK_BLOCKS, 1.0, 24.5));
		int[] phase = {0};
		int[] ticks = {0};
		boolean[] ready = {false};
		double[] widest = {0.0};
		double[] jump = {0.0};
		Vec3[] last = {null};
		boolean[] combatGoalRan = {false};
		Mob[] foe = {null};
		Set<UUID> arrows = new HashSet<>();
		boolean[] running = {false};
		Vec3[] stepFrom = {null};
		double[] travel = {0.0};
		int[] restArrows = {0};
		LearnArena.whenReady(helper, List.of(master, servant), () -> ready[0] = true);
		helper.onEachTick(() -> {
			if (!ready[0] || !servant.isAlive()) {
				return;
			}

			ticks[0]++;
			master.setInvulnerableTime(0);
			if (phase[0] == 0) {
				helper.assertTrue(ticks[0] < SWAP_TICKS, "the bow servant never took its band goal");
				if (hasBand(servant)) {
					phase[0] = 1;
					ticks[0] = 0;
				}

				return;
			}

			if (phase[0] == 1 || phase[0] == 2) {
				combatGoalRan[0] |= servant.getTarget() != null || runningCombatGoal(servant);
				if (last[0] != null) {
					jump[0] = Math.max(jump[0], servant.position().distanceTo(last[0]));
				}

				last[0] = servant.position();
				widest[0] = Math.max(widest[0], servant.distanceTo(master));
			}

			if (phase[0] == 1) {
				double share = Math.min(1.0, ticks[0] * WALK_SPEED / WALK_BLOCKS);
				Vec3 at = start.lerp(end, share);
				master.setPos(at.x, at.y, at.z);
				if (share >= 1.0) {
					phase[0] = 2;
					ticks[0] = 0;
				}

				return;
			}

			if (phase[0] == 2) {
				if (servant.distanceTo(master) > SummonSkeleton.FOLLOW_DISTANCE && ticks[0] < MOVE_TICKS) {
					return;
				}

				StellaLearn.LOGGER.info("[BEHAVIOR] test=aRangedServantFollowsItsMasterThenKitesAFoe FOLLOW widest={} settled={} biggestJump={} combatGoalRan={}",
					widest[0], servant.distanceTo(master), jump[0], combatGoalRan[0]);
				helper.assertFalse(combatGoalRan[0], "the servant ran a combat goal or held a target with no foe");
				helper.assertTrue(widest[0] <= SummonSkeleton.FOLLOW_DISTANCE + FOLLOW_SLACK,
					"the servant lagged " + widest[0] + " blocks behind its walking master");
				helper.assertTrue(jump[0] < TELEPORT_JUMP, "the servant teleported " + jump[0] + " blocks instead of walking");
				helper.assertTrue(servant.distanceTo(master) <= SummonSkeleton.FOLLOW_DISTANCE, "the servant stood " + servant.distanceTo(master) + " blocks from its master");
				Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
				zombie.snapTo(servant.getX() + 1.0, servant.getY(), servant.getZ(), 0.0F, 0.0F);
				zombie.setNoAi(true);
				zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
				zombie.setHealth(100_000.0F);
				zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				helper.getLevel().addFreshEntity(zombie);
				helper.runBeforeTestEnd(zombie::discard);
				foe[0] = zombie;
				phase[0] = 3;
				ticks[0] = 0;
				return;
			}

			Mob target = foe[0];
			helper.assertTrue(ticks[0] < KITE_TICKS, "the servant closed no kite step against its foe in " + KITE_TICKS + " ticks");
			servant.setTarget(target);
			helper.getLevel().getEntitiesOfClass(AbstractArrow.class, servant.getBoundingBox().inflate(ARROW_SEARCH), arrow -> arrow.getOwner() == servant)
				.forEach(arrow -> arrows.add(arrow.getUUID()));
			boolean now = servant.getGoalSelector().getAvailableGoals().stream()
				.anyMatch(wrapped -> wrapped.isRunning() && wrapped.getGoal() instanceof KeepDistanceGoal<?>);
			if (now && !running[0]) {
				if (stepFrom[0] != null) {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aRangedServantFollowsItsMasterThenKitesAFoe RESULT stepTravel={} arrowsInRest={}",
						travel[0], arrows.size() - restArrows[0]);
					helper.assertTrue(Math.abs(travel[0] - KeepDistanceGoal.STEP_BLOCKS) <= 1.0,
						"the servant stepped " + travel[0] + " blocks, not " + KeepDistanceGoal.STEP_BLOCKS);
					helper.assertTrue(arrows.size() > restArrows[0], "the servant loosed no arrow in its rest between steps");
					foe[0].discard();
					servant.discard();
					helper.succeed();
					return;
				}

				stepFrom[0] = servant.position();
			}

			if (now) {
				travel[0] = Math.hypot(servant.getX() - stepFrom[0].x, servant.getZ() - stepFrom[0].z);
			} else if (running[0]) {
				restArrows[0] = arrows.size();
			}

			running[0] = now;
		});
	}

	private static boolean runningCombatGoal(final Skeleton servant) {
		for (WrappedGoal wrapped : servant.getGoalSelector().getAvailableGoals()) {
			if (wrapped.isRunning() && (wrapped.getGoal() instanceof KeepDistanceGoal<?> || wrapped.getGoal() instanceof HoldStandoffGoal<?>
				|| wrapped.getGoal().getClass().getSimpleName().equals("ShootGoal"))) {
				return true;
			}
		}

		return false;
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the cast raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(26.5, LearnArena.SPAWN_Y, 26.5));
		servant.teleportTo(at.x, at.y, at.z);
		return servant;
	}

	private static void swap(final GameTestHelper helper, final Skeleton servant, final ItemStack weapon, final CombatRole role,
		final boolean holdsBand, final Runnable next) {
		SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, weapon);
		LearnArena.pollUntil(helper, () -> modeMatches(servant, role, holdsBand), SWAP_TICKS, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theServantTakesTheModeOfItsHeldWeapon RESULT weapon={} role={} band={}",
				weapon.getItem(), role, holdsBand);
			next.run();
		}, () -> "the servant holding " + weapon.getItem() + " has role " + ReactiveState.get(servant).getRole()
			+ " and band goal " + hasBand(servant) + ", wanted " + role + " and " + holdsBand);
	}

	private static boolean modeMatches(final Skeleton servant, final CombatRole role, final boolean holdsBand) {
		return ReactiveState.get(servant).getRole() == role && hasBand(servant) == holdsBand;
	}

	private static boolean hasBand(final Skeleton servant) {
		return servant.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof KeepDistanceGoal<?>);
	}

	private static void finish(final GameTestHelper helper, final Skeleton servant) {
		servant.discard();
		helper.succeed();
	}
}
