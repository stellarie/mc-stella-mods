package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.ShieldUseGoal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * The shield guard of a melee bearer. It raises the shield and keeps walking while the player and the mob look at
 * each other, lowers it for a player who looks away, strikes back after a blocked hit, and loses the shield to an
 * axe. Every time below counts the zombie's own ticks, because the level clock barely moves in a test. The guard
 * reports through the recorder, and the maps below are keyed by the zombie so concurrent tests never mix.
 */
public class ShieldGuardGameTest {
	private static final String TEST = "shieldGuard";
	private static final int MAX_TICKS = 200;
	private static final int AXE_MAX_TICKS = 300;
	private static final double PLAYER_X = 20.5;
	private static final double PLAYER_Z = 26.5;
	private static final int START_GAP = 10;
	private static final double CLOSE_BLOCKS = 2.0;
	private static final int RAISE_WITHIN_TICKS = 20;
	private static final int RIPOSTE_WITHIN_TICKS = 2;
	private static final int RAISE_AGAIN_WITHIN_TICKS = 8;
	private static final int SECOND_HIT_WATCH_TICKS = 4;
	private static final float HIT_DAMAGE = 4.0F;

	private static final Map<UUID, List<Integer>> RAISED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Integer>> RIPOSTES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Integer>> DISABLED = new ConcurrentHashMap<>();

	static {
		ShieldUseGoal.setRecorder(new ShieldUseGoal.Recorder() {
			@Override
			public void guardRaised(final Mob mob) {
				RAISED.computeIfAbsent(mob.getUUID(), key -> new CopyOnWriteArrayList<>()).add(mob.tickCount);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={} t={} RAISED mob={}", TEST, mob.tickCount, mob.getUUID());
			}

			@Override
			public void riposte(final Mob mob, final LivingEntity target) {
				RIPOSTES.computeIfAbsent(mob.getUUID(), key -> new CopyOnWriteArrayList<>()).add(mob.tickCount);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={} t={} RIPOSTE mob={}", TEST, mob.tickCount, mob.getUUID());
			}

			@Override
			public void disabled(final Mob mob, final int ticks) {
				DISABLED.computeIfAbsent(mob.getUUID(), key -> new CopyOnWriteArrayList<>()).add(ticks);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={} t={} DISABLED mob={} ticks={}", TEST, mob.tickCount, mob.getUUID(), ticks);
			}
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void raisesShieldWhileAdvancing(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Zombie zombie = shieldZombie(scene, (int) PLAYER_X, (int) PLAYER_Z - START_GAP);
		zombie.setTarget(player);
		double[] previousGap = {MeleeKitFloor.flat(zombie, player)};
		boolean[] wasUp = {false};
		double[] closedWithShieldUp = {0.0};

		helper.onEachTick(() -> {
			MeleeKitFloor.lookAt(player, zombie);
			zombie.setTarget(player);
			boolean up = zombie.isUsingItem();
			double gap = MeleeKitFloor.flat(zombie, player);
			if (up && wasUp[0]) {
				closedWithShieldUp[0] += Math.max(0.0, previousGap[0] - gap);
			}

			wasUp[0] = up;
			previousGap[0] = gap;
		});
		helper.succeedWhen(() -> {
			int firstRaise = first(RAISED, zombie);
			String seen = "firstRaise=" + firstRaise + " closedWithShieldUp=" + closedWithShieldUp[0]
				+ " gap=" + MeleeKitFloor.flat(zombie, player) + " ticking=" + scene.ticking();
			helper.assertTrue(firstRaise >= 0 && firstRaise <= RAISE_WITHIN_TICKS, "the guard did not raise the shield in time: " + seen);
			helper.assertTrue(closedWithShieldUp[0] >= CLOSE_BLOCKS, "the zombie did not close in with the shield up: " + seen);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.raisesShieldWhileAdvancing RESULT {}", TEST, seen);
			scene.end();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void unwatchedPursuesWithShieldDown(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Zombie zombie = shieldZombie(scene, (int) PLAYER_X, (int) PLAYER_Z - START_GAP);
		zombie.setTarget(player);
		double startGap = MeleeKitFloor.flat(zombie, player);
		double[] nearest = {startGap};
		boolean[] shieldSeen = {false};

		helper.onEachTick(() -> {
			MeleeKitFloor.lookAway(player, zombie);
			zombie.setTarget(player);
			if (zombie.isUsingItem()) {
				shieldSeen[0] = true;
			}

			nearest[0] = Math.min(nearest[0], MeleeKitFloor.flat(zombie, player));
		});
		helper.succeedWhen(() -> {
			String seen = "startGap=" + startGap + " nearest=" + nearest[0] + " shieldSeen=" + shieldSeen[0]
				+ " raises=" + RAISED.getOrDefault(zombie.getUUID(), List.of()).size() + " ticking=" + scene.ticking();
			helper.assertFalse(shieldSeen[0], "the shield went up for a player who looked away: " + seen);
			helper.assertTrue(
				startGap - nearest[0] >= CLOSE_BLOCKS && nearest[0] <= MeleeKitFloor.CONTACT_BLOCKS,
				"the zombie did not pursue the player who looked away: " + seen
			);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.unwatchedPursuesWithShieldDown RESULT {}", TEST, seen);
			scene.end();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void ripostesAfterBlockedHit(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Zombie zombie = shieldZombie(scene, (int) PLAYER_X, (int) PLAYER_Z - 1);
		zombie.setTarget(player);
		int[] hitTick = {-1};
		boolean[] lowered = {false};
		int[] blockingAgain = {-1};

		helper.onEachTick(() -> {
			MeleeKitFloor.lookAt(player, zombie);
			zombie.setTarget(player);
			if (hitTick[0] >= 0) {
				if (!zombie.isBlocking()) {
					lowered[0] = true;
				} else if (lowered[0] && blockingAgain[0] < 0) {
					blockingAgain[0] = zombie.tickCount;
				}
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking() && zombie.isBlocking(), "the guard never raised the shield"))
			.thenExecute(() -> {
				hitTick[0] = zombie.tickCount;
				helper.assertFalse(hit(helper, zombie, player), "the first player hit was not blocked");
			})
			.thenWaitUntil(() -> helper.assertTrue(count(RIPOSTES, zombie) >= 1, "no riposte followed the blocked hit"))
			.thenExecute(() -> {
				int delay = first(RIPOSTES, zombie) - hitTick[0];
				helper.assertTrue(delay <= RIPOSTE_WITHIN_TICKS, "the riposte came " + delay + " ticks after the hit");
			})
			.thenWaitUntil(() -> helper.assertTrue(blockingAgain[0] >= 0, "the shield never blocked again after the riposte"))
			.thenExecute(() -> {
				int raiseDelay = blockingAgain[0] - hitTick[0];
				helper.assertTrue(raiseDelay <= RAISE_AGAIN_WITHIN_TICKS, "the shield blocked again only after " + raiseDelay + " ticks");
				helper.assertFalse(hit(helper, zombie, player), "the second player hit was not blocked, so the cooldown check proves nothing");
			})
			.thenIdle(SECOND_HIT_WATCH_TICKS)
			.thenExecute(() -> {
				int ripostes = count(RIPOSTES, zombie);
				String seen = "hitTick=" + hitTick[0] + " riposteTick=" + first(RIPOSTES, zombie)
					+ " blockingAgain=" + blockingAgain[0] + " ripostes=" + ripostes + " now=" + zombie.tickCount;
				helper.assertTrue(ripostes == 1, "a second blocked hit inside the cooldown got another riposte: " + seen);
				helper.assertTrue(
					zombie.tickCount - first(RIPOSTES, zombie) < ShieldUseGoal.RIPOSTE_COOLDOWN_TICKS,
					"the second hit fell outside the riposte cooldown, so the check proves nothing: " + seen
				);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.ripostesAfterBlockedHit RESULT {}", TEST, seen);
				scene.end();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = AXE_MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void axeDisablesShield(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		Zombie zombie = shieldZombie(scene, (int) PLAYER_X, (int) PLAYER_Z - 1);
		zombie.setTarget(player);
		BlocksAttacks shield = zombie.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS);
		int expectedTicks = Math.round(player.getSecondsToDisableBlocking() * shield.disableCooldownScale() * 20.0F);
		helper.assertTrue(expectedTicks > 0, "the axe does not disable a shield, so the test has no premise");
		int[] hitTick = {-1};
		int[] earlyRaise = {-1};
		int[] raisedAgain = {-1};

		helper.onEachTick(() -> {
			MeleeKitFloor.lookAt(player, zombie);
			zombie.setTarget(player);
			if (hitTick[0] >= 0 && zombie.isUsingItem()) {
				int elapsed = zombie.tickCount - hitTick[0];
				if (elapsed < expectedTicks && earlyRaise[0] < 0) {
					earlyRaise[0] = elapsed;
				}

				if (elapsed >= expectedTicks && raisedAgain[0] < 0) {
					raisedAgain[0] = elapsed;
				}
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking() && zombie.isBlocking(), "the guard never raised the shield"))
			.thenExecute(() -> {
				hitTick[0] = zombie.tickCount;
				helper.assertFalse(hit(helper, zombie, player), "the axe hit was not blocked");
				helper.assertFalse(zombie.isUsingItem(), "the axe hit left the shield raised");
				helper.assertTrue(
					DISABLED.getOrDefault(zombie.getUUID(), List.of()).equals(List.of(expectedTicks)),
					"the disable report is " + DISABLED.getOrDefault(zombie.getUUID(), List.of()) + ", expected [" + expectedTicks + "]"
				);
			})
			.thenWaitUntil(() -> helper.assertTrue(raisedAgain[0] >= 0, "the shield never rose again after the disable time"))
			.thenExecute(() -> {
				String seen = "expectedTicks=" + expectedTicks + " hitTick=" + hitTick[0] + " earlyRaise=" + earlyRaise[0]
					+ " raisedAgainAfter=" + raisedAgain[0] + " ripostes=" + count(RIPOSTES, zombie);
				helper.assertTrue(earlyRaise[0] < 0, "the shield rose inside the disable time: " + seen);
				helper.assertTrue(count(RIPOSTES, zombie) == 0, "a riposte followed the axe hit: " + seen);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.axeDisablesShield RESULT {}", TEST, seen);
				scene.end();
			})
			.thenSucceed();
	}

	private static Zombie shieldZombie(final MeleeKitFloor scene, final int x, final int z) {
		Zombie zombie = scene.mob(EntityTypes.ZOMBIE, x, MeleeKitFloor.SPAWN_Y, z);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		// A hand-armed mob needs the goal added.
		BehaviorInstaller.installShield(zombie);
		return zombie;
	}

	private static boolean hit(final GameTestHelper helper, final Zombie zombie, final ServerPlayer player) {
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		return zombie.hurtServer(helper.getLevel(), source, HIT_DAMAGE);
	}

	private static int first(final Map<UUID, List<Integer>> record, final Mob mob) {
		List<Integer> ticks = record.getOrDefault(mob.getUUID(), List.of());
		return ticks.isEmpty() ? -1 : ticks.getFirst();
	}

	private static int count(final Map<UUID, List<Integer>> record, final Mob mob) {
		return record.getOrDefault(mob.getUUID(), List.of()).size();
	}
}
