package com.stella.mobs;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class BehaviorGameTest {
	private static final String SHIELD_CONFIG = """
		{
			"version": 1,
			"entities": {
				"minecraft:zombie": {
					"slots": {
						"offhand": { "item": "minecraft:shield" }
					}
				}
			}
		}""";
	private static final String NO_SHIELD_CONFIG = """
		{
			"version": 1,
			"entities": {
				"minecraft:zombie": {
					"slots": {
						"mainhand": { "item": "minecraft:diamond_sword" }
					}
				}
			}
		}""";
	private static final float MELEE_DAMAGE = 5.0F;
	private static final int SETTLE_TICKS = 8;
	private static final int MAX_TICKS = 200;
	private static final long NIGHT_TIME = 18000L;
	private static final float RETREAT_HEALTH = 6.0F;

	@GameTest(maxTicks = MAX_TICKS)
	public void shieldedMobBlocksMeleeFromTheFront(final GameTestHelper helper) {
		prepare(helper, SHIELD_CONFIG);
		Zombie attacker = spawnAttacker(helper, 4, 2);
		Zombie defender = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).spawn();
		defender.setTarget(attacker);
		helper.assertTrue(defender.getHealth() == defender.getMaxHealth(), "the defender lost health at spawn");

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(defender.isBlocking(), "the shielded zombie never raised its shield"))
			.thenIdle(SETTLE_TICKS)
			.thenExecute(() -> {
				helper.assertTrue(defender.isBlocking(), "the shield dropped before the hit landed");
				DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
				boolean hurt = defender.hurtServer(helper.getLevel(), source, MELEE_DAMAGE);
				helper.assertFalse(hurt, "a fully blocked hit reported damage");
				helper.assertValueEqual(
					Float.valueOf(defender.getHealth()), Float.valueOf(defender.getMaxHealth()), "shielded zombie health"
				);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void unshieldedMobTakesMeleeDamage(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie attacker = spawnAttacker(helper, 4, 2);
		Zombie defender = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).spawn();
		defender.setTarget(attacker);

		helper.startSequence()
			.thenIdle(SETTLE_TICKS)
			.thenExecute(() -> {
				DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
				boolean hurt = defender.hurtServer(helper.getLevel(), source, MELEE_DAMAGE);
				helper.assertTrue(hurt, "an unshielded hit reported no damage");
				helper.assertTrue(defender.getHealth() < defender.getMaxHealth(), "the unshielded zombie took no damage");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shieldReleasesAfterTheHoldTime(final GameTestHelper helper) {
		prepare(helper, SHIELD_CONFIG);
		Zombie attacker = spawnAttacker(helper, 4, 2);
		Zombie defender = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).spawn();
		defender.setTarget(attacker);

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(defender.isBlocking(), "the shielded zombie never raised its shield"))
			.thenWaitUntil(() -> helper.assertFalse(defender.isBlocking(), "the shield never released after the hold time"))
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void lowHealthRetreats(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie attacker = spawnAttacker(helper, 6, 4);
		Zombie defender = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();
		defender.getGoalSelector().removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
		defender.setHealth(RETREAT_HEALTH);
		defender.setTarget(attacker);
		double startDistance = defender.position().distanceTo(attacker.position());

		helper.succeedWhen(() -> helper.assertTrue(
			defender.position().distanceTo(attacker.position()) > startDistance + 0.75,
			"no retreat: distance=" + defender.position().distanceTo(attacker.position())
				+ " start=" + startDistance
				+ " readyTick=" + ReactiveState.get(defender).getRetreatReadyTick()
				+ " navDone=" + defender.getNavigation().isDone()
				+ " defenderHealth=" + defender.getHealth()
				+ " attackerHealth=" + attacker.getHealth()
		));
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void allyAttackIsAdopted(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie attacker = spawnAttacker(helper, 4, 4);
		Mob ally = helper.spawnMob(EntityTypes.SKELETON, 1, 3, 1).spawn();
		Mob defender = helper.spawnMob(EntityTypes.SKELETON, 1, 3, 6).spawn();

		helper.runAfterDelay(10L, () -> ally.hurtServer(
			helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
		));
		helper.succeedWhen(() -> helper.assertTrue(
			defender.getTarget() == attacker, "the defender did not adopt the ally's attacker"
		));
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void noAllyInDangerKeepsOwnTarget(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie other = spawnAttacker(helper, 4, 4);
		helper.spawnMob(EntityTypes.SKELETON, 1, 3, 1).spawn();
		Mob defender = helper.spawnMob(EntityTypes.SKELETON, 1, 3, 6).spawn();
		defender.setTarget(other);

		helper.runAfterDelay(40L, () -> {
			helper.assertTrue(defender.getTarget() == other, "the defender dropped its own target with no ally in danger");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void stateIsPerEntity(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie first = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		Zombie second = helper.spawnMob(EntityTypes.ZOMBIE, 3, 3, 1).spawn();

		helper.runAfterDelay(2L, () -> {
			ReactiveState firstState = ReactiveState.get(first);
			ReactiveState secondState = ReactiveState.get(second);
			helper.assertTrue(firstState != secondState, "two mobs shared one state object");
			firstState.setShieldReadyTick(500L);
			helper.assertValueEqual(
				Long.valueOf(secondState.getShieldReadyTick()), Long.valueOf(0L), "second mob shield ready tick"
			);
			helper.assertValueEqual(
				Long.valueOf(firstState.getShieldReadyTick()), Long.valueOf(500L), "first mob shield ready tick"
			);
			secondState.setLastDefendedHurtTimestamp(7);
			helper.assertValueEqual(
				Integer.valueOf(firstState.getLastDefendedHurtTimestamp()), Integer.valueOf(0), "first mob defended timestamp"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void gearIsNotReappliedForTheSameMob(final GameTestHelper helper) {
		prepare(helper, NO_SHIELD_CONFIG);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();

		helper.runAfterDelay(5L, () -> {
			helper.assertTrue(
				zombie.getMainHandItem().is(Items.DIAMOND_SWORD), "the configured gear never reached the zombie"
			);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
			StellaMobs.onEntityLoad(zombie, helper.getLevel());
			helper.assertTrue(
				zombie.getMainHandItem().is(Items.STICK),
				"the load handler re-applied the configured gear over the held item"
			);
			helper.succeed();
		});
	}

	private static void prepare(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper);
		addCreativePlayer(helper);
	}

	private static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static void addCreativePlayer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		player.setPos(position.x, position.y, position.z);
	}

	private static Zombie spawnAttacker(final GameTestHelper helper, final int x, final int z) {
		Zombie attacker = helper.spawnMob(EntityTypes.ZOMBIE, x, 3, z).withNoFreeWill().spawn();
		attacker.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return attacker;
	}
}
