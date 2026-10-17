package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** The castle's mobs are one side: none of them takes another as a target, even after a blow, and a player stays a target. */
public class FactionGameTest {
	record Cast(String name, Mob mob) {
	}

	/** One of every kind of castle mob, standing in the clear middle of the arena. */
	static List<Cast> cast(final GameTestHelper helper, final ServerLevel end) {
		int y = EndCastle.standY(EndCastle.FLOORS);
		List<Cast> found = new ArrayList<>();
		found.add(new Cast("wither skeleton", EndMobs.spawn(end, 6, EndMobs.Kind.WITHER_SKELETON, new BlockPos(-3, y, -3))));
		found.add(new Cast("enderman", EndMobs.spawn(end, 6, EndMobs.Kind.ENDERMAN, new BlockPos(3, y, -3))));
		found.add(new Cast("witch", EndMobs.spawnWitch(end, 5, new BlockPos(-3, y, 3))));
		found.add(new Cast("undead blaze", UndeadBlazes.spawn(end, new Vec3(3.5, y + 1.0, 3.5))));
		Skeleton side = EntityTypes.SKELETON.create(end, EntitySpawnReason.EVENT);
		Wyverns.own(side, new Vec3(0.5, y, -4.5), EndLevels.ROOFTOP);
		end.addFreshEntity(side);
		found.add(new Cast("dragon side skeleton", side));
		found.add(new Cast("sovereign", UndeadSovereign.spawn(end)));
		for (Cast one : found) {
			helper.assertTrue(one.mob() != null && one.mob().isAlive(), one.name() + " was not made");
		}
		return found;
	}

	@GameTest(environment = "mod-stella-end-test:faction_target", maxTicks = 80)
	public void noCastleMobTakesAnotherCastleMobAsATargetHoweverItIsSet(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = SovereignGameTest.inArena(helper, end, 6.0, 0.0);
		List<Cast> cast = cast(helper, end);
		Zombie stranger = EndTestSupport.dummy(end, new Vec3(0.5, EndCastle.standY(EndCastle.FLOORS), 5.5));
		List<String> bad = new ArrayList<>();
		for (Cast hunter : cast) {
			for (Cast prey : cast) {
				if (hunter != prey) {
					hunter.mob().setTarget(prey.mob());
					if (hunter.mob().getTarget() == prey.mob()) {
						bad.add(hunter.name() + " took " + prey.name() + " as a target");
					}
				}
			}
			hunter.mob().setTarget(null);
		}
		List<String> open = new ArrayList<>();
		for (Cast hunter : cast) {
			hunter.mob().setTarget(player);
			if (hunter.mob().getTarget() != player) {
				open.add(hunter.name() + " cannot target the player");
			}
			hunter.mob().setTarget(stranger);
			if (hunter.mob().getTarget() != stranger) {
				open.add(hunter.name() + " cannot target a creature outside the castle");
			}
			hunter.mob().setTarget(null);
		}
		EndTestSupport.result("factionTarget", "mobs=" + cast.size() + " crossTargets=" + bad.size() + " blockedOutsiders=" + open.size());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "castle mobs targeted each other: " + bad);
		helper.assertTrue(open.isEmpty(), "the faction rule hides too much: " + open);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:faction_hurt", maxTicks = 120)
	public void aCastleMobHurtByAnotherCastleMobDoesNotAnswerButOneHurtByAPlayerDoes(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = SovereignGameTest.inArena(helper, end, 6.0, 0.0);
		List<Cast> cast = cast(helper, end);
		Zombie stranger = EndTestSupport.dummy(end, new Vec3(0.5, EndCastle.standY(EndCastle.FLOORS), 5.5));
		List<String> bad = new ArrayList<>();
		List<String> open = new ArrayList<>();
		for (Cast attacker : cast) {
			for (Cast victim : cast) {
				if (attacker != victim) {
					victim.mob().damageCooldownTime = 0;
					victim.mob().hurtServer(end, end.damageSources().mobAttack(attacker.mob()), 1.0F);
					if (victim.mob().getLastHurtByMob() == attacker.mob()) {
						bad.add(victim.name() + " remembers a blow from " + attacker.name());
					}
				}
			}
		}
		for (Cast victim : cast) {
			victim.mob().damageCooldownTime = 0;
			victim.mob().hurtServer(end, end.damageSources().mobAttack(stranger), 1.0F);
			if (victim.mob().getLastHurtByMob() != stranger) {
				open.add(victim.name() + " forgets a blow from a creature outside the castle");
			}
			victim.mob().damageCooldownTime = 0;
			victim.mob().hurtServer(end, end.damageSources().playerAttack(player), 1.0F);
			if (victim.mob().getLastHurtByMob() != player) {
				open.add(victim.name() + " forgets a blow from the player");
			}
		}
		helper.startSequence()
			.thenIdle(1)
			.thenExecute(() -> {
				for (Cast victim : cast) {
					victim.mob().setLastHurtByMob(null);
					victim.mob().setTarget(null);
				}
				for (Cast attacker : cast) {
					for (Cast victim : cast) {
						if (attacker != victim) {
							victim.mob().damageCooldownTime = 0;
							victim.mob().hurtServer(end, end.damageSources().mobAttack(attacker.mob()), 1.0F);
						}
					}
				}
			})
			.thenIdle(30)
			.thenExecute(() -> {
				for (Cast one : cast) {
					LivingEntity target = one.mob().getTarget();
					if (target != null && Factions.isCastle(target)) {
						bad.add(one.name() + " answered with a hunt of " + target);
					}
				}
				EndTestSupport.result("factionHurt", "mobs=" + cast.size() + " answers=" + bad.size() + " forgotten=" + open.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "castle mobs answered each other: " + bad);
				helper.assertTrue(open.isEmpty(), "the faction rule hides too much: " + open);
			})
			.thenSucceed();
	}
}
