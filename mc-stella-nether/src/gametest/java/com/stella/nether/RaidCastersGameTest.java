package com.stella.nether;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Raid mobs that cast: a fire spell or Time Lock at their target, and a buff on themselves. */
public class RaidCastersGameTest {
	static <T extends Mob> T spawn(final ServerLevel level, final EntityType<T> type, final Vec3 at) {
		T mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		// Out of the mobs mod's rolls, so no rolled gear or rank stands in for the spell being measured.
		mob.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	static boolean visible(final ServerLevel level, final Entity... entities) {
		boolean seen = true;
		for (Entity entity : entities) {
			BlazeSovereignGameTest.hold(level, entity.blockPosition());
			seen &= level.getEntity(entity.getUUID()) == entity;
		}
		if (!seen) {
			try {
				Thread.sleep(SovereignSpecialsGameTest.WAIT_MILLIS_PER_TICK);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		return seen;
	}

	@GameTest(environment = "mod-stella-nether-test:casters_kits", maxTicks = 400)
	public void theRaidsCastersCarryTheirSpellsAndOthersDoNot(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 floor = BlazeSovereignGameTest.arena(raid, helper.getLevel().getServer(), 8);
		Mob wither = spawn(raid, EntityTypes.WITHER_SKELETON, floor.add(-3.0, 0.0, 0.0));
		Mob brute = spawn(raid, EntityTypes.PIGLIN_BRUTE, floor.add(3.0, 0.0, 0.0));
		Mob ghast = spawn(raid, EntityTypes.GHAST, floor.add(0.0, 2.0, 3.0));
		Mob plain = spawn(raid, EntityTypes.ZOMBIFIED_PIGLIN, floor.add(0.0, 0.0, -3.0));
		Mob outside = spawn(helper.getLevel(), EntityTypes.WITHER_SKELETON, helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(visible(raid, wither, brute, ghast, plain), "the raid's mobs are not loaded"))
			.thenExecute(() -> {
				boolean casters = RaidCasters.hasCaster(wither) && RaidCasters.hasCaster(brute) && RaidCasters.hasCaster(ghast);
				boolean others = RaidCasters.hasCaster(plain) || RaidCasters.hasCaster(outside);
				NetherTestSupport.result("casterKits", "wither=" + RaidCasters.kitOf(wither) + " brute=" + RaidCasters.kitOf(brute) + " ghast=" + RaidCasters.kitOf(ghast)
					+ " casters=" + casters + " others=" + others);
				for (Mob mob : new Mob[] {wither, brute, ghast, plain, outside}) {
					mob.discard();
				}
				NetherTestSupport.leave(helper, player);
				helper.assertTrue(casters && !others, "casters installed=" + casters + ", on a plain or outside mob=" + others);
			})
			.thenSucceed();
	}

	/** A caster and a target beside it; waits for the caster's strike and its buff, as the caster recorded them. */
	static void castsAt(final GameTestHelper helper, final EntityType<? extends Mob> type, final double lift, final String name) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 floor = BlazeSovereignGameTest.arena(raid, helper.getLevel().getServer(), 8);
		Mob caster = spawn(raid, type, floor.add(0.0, lift, 0.0));
		Zombie target = spawn(raid, EntityTypes.ZOMBIE, floor.add(2.0, 0.0, 0.0));
		target.setNoAi(true);
		target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 4));
		RaidCasters.Kit kit = RaidCasters.KITS.get(type);
		List<String> casts = new CopyOnWriteArrayList<>();
		// Only the strikes at the stand-in and the buffs on the caster count: its own targeting can turn it on another
		// for a moment (seen once in five runs: an Ember Brand recorded while the stand-in never burned).
		RaidCasters.Recorder previous = RaidCasters.setRecorder((mob, spell, at) -> {
			if (mob == caster && (at == target || at == caster)) {
				casts.add(spell);
			}
		});
		boolean[] restored = new boolean[1];
		Runnable restore = () -> {
			if (!restored[0]) {
				restored[0] = true;
				RaidCasters.setRecorder(previous);
			}
		};
		helper.runAfterDelay(390, restore);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(visible(raid, caster, target), "the caster and its target are not loaded"))
			// Hurt once loaded, since a raid mob starts whole: a caster whose buff mends has itself to mend.
			.thenExecute(() -> caster.setHealth(caster.getMaxHealth() * 0.5F))
			.thenWaitUntil(() -> {
				visible(raid, caster, target);
				caster.setTarget(target);
				// A piglin brute's target is its brain's memory, and its brain picks its own when left alone
				// (seen once in five runs: the ring went off at the brain's pick and the stand-in never burned).
				caster.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
				helper.assertTrue(casts.contains(kit.strike()) && casts.contains(kit.aid()), "the caster has cast " + casts);
			})
			.thenExecute(() -> {
				restore.run();
				boolean slowed = target.hasEffect(MobEffects.SLOWNESS);
				boolean burning = target.getRemainingFireTicks() > 0;
				NetherTestSupport.result(name, "casts=" + casts + " targetBurning=" + burning + " targetSlowed=" + slowed
					+ " casterEffects=" + caster.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName()).toList());
				caster.discard();
				target.discard();
				NetherTestSupport.leave(helper, player);
				helper.assertTrue("time_lock".equals(kit.strike()) ? slowed : burning, "the " + kit.strike() + " left the target burning=" + burning + " slowed=" + slowed);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:casters_brute", maxTicks = 400)
	public void aPiglinBruteRingsItselfInFlameAndTurnsToStone(final GameTestHelper helper) {
		castsAt(helper, EntityTypes.PIGLIN_BRUTE, 0.0, "casterBrute");
	}

	@GameTest(environment = "mod-stella-nether-test:casters_wither", maxTicks = 400)
	public void aWitherSkeletonBrandsWithEmbersAndSingsABattleHymn(final GameTestHelper helper) {
		castsAt(helper, EntityTypes.WITHER_SKELETON, 0.0, "casterWither");
	}

	@GameTest(environment = "mod-stella-nether-test:casters_ghast", maxTicks = 400)
	public void aGhastLocksTimeAndMendsItself(final GameTestHelper helper) {
		castsAt(helper, EntityTypes.GHAST, 3.0, "casterGhast");
	}
}
