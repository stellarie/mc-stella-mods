package com.stella.mobs;

import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.FriendlyPatrol;
import com.stella.mobs.behavior.Levitation;
import com.stella.mobs.behavior.WebCastGoal;
import com.stella.mobs.behavior.WitchPotions;
import com.stella.mobs.behavior.WitherPulse;
import com.stella.mobs.behavior.WolfPack;
import com.stella.mobs.gear.GearTier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Round eight's smaller mob changes: levitation capped at two seconds except a shulker's, elite witches that
 * never blind, elite spiders that cast webs (and cave spiders that also wither), wither skeletons' slowing and
 * weakening pulses, wild patrols that never join a raid, and elite wolves that only the wild can roll.
 */
public class MobKitGameTest {
	private static final String TEST = "mobKit";
	private static final Map<UUID, List<List<BlockPos>>> WEBS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Holder<MobEffect>>> PULSES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<List<LivingEntity>>> PULSE_REACH = new ConcurrentHashMap<>();
	private static final int BRED_WOLVES = 60;

	static {
		WebCastGoal.setRecorder((spider, target, webs) -> WEBS.computeIfAbsent(spider.getUUID(), key -> new CopyOnWriteArrayList<>()).add(webs));
		WitherPulse.setRecorder((skeleton, effect, reached) -> {
			PULSES.computeIfAbsent(skeleton.getUUID(), key -> new CopyOnWriteArrayList<>()).add(effect);
			PULSE_REACH.computeIfAbsent(skeleton.getUUID(), key -> new CopyOnWriteArrayList<>()).add(reached);
		});
	}

	@GameTest
	public void levitationFromAnythingButAShulkerLastsTwoSeconds(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Zombie struck = zombie(helper, 2, 2);
		Mob pig = helper.spawnMob(EntityTypes.PIG, 5, 1, 5).withNoFreeWill().spawn();
		struck.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 300), pig);
		int fromPig = struck.getEffect(MobEffects.LEVITATION).getDuration();
		Zombie shot = zombie(helper, 4, 2);
		Mob shulker = helper.spawnMob(EntityTypes.SHULKER, 6, 1, 2).withNoFreeWill().spawn();
		shot.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), shulker);
		int fromShulker = shot.getEffect(MobEffects.LEVITATION).getDuration();
		result("levitationCap", "fromPig=" + fromPig + " fromShulker=" + fromShulker + " cap=" + Levitation.MAX_TICKS);
		helper.assertTrue(fromPig == Levitation.MAX_TICKS, "levitation from a pig lasts " + fromPig);
		helper.assertTrue(fromShulker == 200, "a shulker's levitation was cut to " + fromShulker);
		helper.succeed();
	}

	@GameTest
	public void anEliteWitchCarriesNoBlindnessAndShortLevitation(final GameTestHelper helper) {
		boolean blinds = WitchPotions.eliteOffensive().stream().anyMatch(carried -> carried.effect().equals(MobEffects.BLINDNESS));
		int levitation = WitchPotions.offensive().stream().filter(carried -> carried.effect().equals(MobEffects.LEVITATION))
			.mapToInt(WitchPotions.Carried::durationTicks).findFirst().orElse(-1);
		result("eliteWitchKit", "blinds=" + blinds + " levitation=" + levitation + " plain=" + WitchPotions.offensive().size() + " elite=" + WitchPotions.eliteOffensive().size());
		helper.assertTrue(!blinds && WitchPotions.eliteOffensive().size() == WitchPotions.offensive().size() - 1, "the elite witch's throws still blind");
		helper.assertTrue(levitation == Levitation.MAX_TICKS, "the witch's levitation lasts " + levitation);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anEliteSpiderCastsThreeWebsOneOverItsTarget(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Spider spider = spider(helper, EntityTypes.SPIDER, 1, 1);
		Elite.mark(spider, GearTier.IRON, 1.0);
		Zombie target = zombie(helper, 5, 5);
		helper.onEachTick(() -> spider.setTarget(target));
		helper.succeedWhen(() -> {
			List<List<BlockPos>> casts = WEBS.getOrDefault(spider.getUUID(), List.of());
			helper.assertTrue(!casts.isEmpty(), "no web cast yet");
			List<BlockPos> webs = casts.get(0);
			BlockPos feet = target.blockPosition();
			boolean near = webs.stream().allMatch(pos -> Math.abs(pos.getX() - feet.getX()) <= 1 && Math.abs(pos.getY() - feet.getY()) <= 1 && Math.abs(pos.getZ() - feet.getZ()) <= 1);
			result("webCast", "webs=" + webs.size() + " coversTarget=" + webs.contains(feet) + " near=" + near);
			helper.assertTrue(webs.size() == WebCastGoal.WEBS && webs.contains(feet) && near, "the cast webbed " + webs + " around " + feet);
		});
	}

	@GameTest(maxTicks = 300)
	public void anEliteCaveSpiderWithersAndItsWebsClear(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Spider spider = spider(helper, EntityTypes.CAVE_SPIDER, 1, 1);
		Elite.mark(spider, GearTier.IRON, 1.0);
		Mob target = helper.spawnMob(EntityTypes.PIG, 5, 1, 5).withNoFreeWill().spawn();
		target.setPersistenceRequired();
		int[] castAt = {-1};
		boolean[] witheredAtCast = {false};
		helper.onEachTick(() -> {
			spider.setTarget(target);
			if (castAt[0] < 0 && !WEBS.getOrDefault(spider.getUUID(), List.of()).isEmpty()) {
				castAt[0] = (int) helper.getTick();
				witheredAtCast[0] = target.hasEffect(MobEffects.WITHER);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(castAt[0] >= 0, "no web cast yet");
			helper.assertTrue(helper.getTick() >= castAt[0] + WebCastGoal.WEB_LIFETIME_TICKS + 5, "the webs have not had their time yet");
			List<BlockPos> webs = WEBS.get(spider.getUUID()).get(0);
			boolean cleared = webs.stream().noneMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.COBWEB));
			result("caveSpiderCast", "witheredAtCast=" + witheredAtCast[0] + " webs=" + webs.size() + " cleared=" + cleared);
			helper.assertTrue(witheredAtCast[0], "the cave spider's cast did not wither its target");
			helper.assertTrue(cleared, "the webs are still there after their time");
			spider.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aWitherSkeletonSlowsATargetThatTurnsItsBack(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		WitherSkeleton skeleton = witherSkeleton(helper, 1, 1);
		Zombie away = zombie(helper, 6, 6);
		// Facing further along the line from the skeleton, its back turned.
		away.setYRot(-45.0F);
		away.setYHeadRot(-45.0F);
		helper.onEachTick(() -> skeleton.setTarget(away));
		helper.succeedWhen(() -> {
			List<Holder<MobEffect>> pulses = PULSES.getOrDefault(skeleton.getUUID(), List.of());
			helper.assertTrue(!pulses.isEmpty(), "no pulse yet; faces away=" + WitherPulse.facesAway(away, skeleton));
			helper.assertTrue(pulses.get(0).equals(MobEffects.SLOWNESS) && PULSE_REACH.get(skeleton.getUUID()).get(0).contains(away), "the pulse was " + pulses);
			result("witherSlowPulse", "pulses=" + pulses.size() + " slowed=" + away.hasEffect(MobEffects.SLOWNESS));
		});
	}

	@GameTest(maxTicks = 60)
	public void aWitherSkeletonNeverPulsesAtATargetFacingIt(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		WitherSkeleton skeleton = witherSkeleton(helper, 1, 1);
		Zombie facing = zombie(helper, 6, 6);
		facing.setYRot(135.0F);
		facing.setYHeadRot(135.0F);
		helper.onEachTick(() -> skeleton.setTarget(facing));
		helper.runAfterDelay(40L, () -> {
			result("witherNoPulseFacing", "facesAway=" + WitherPulse.facesAway(facing, skeleton) + " pulses=" + PULSES.getOrDefault(skeleton.getUUID(), List.of()).size());
			helper.assertFalse(WitherPulse.facesAway(facing, skeleton), "the facing zombie reads as turned away, so the case proves nothing");
			helper.assertTrue(PULSES.getOrDefault(skeleton.getUUID(), List.of()).isEmpty(), "the skeleton pulsed at a target facing it");
			helper.succeed();
		});
	}

	@GameTest
	public void anEliteWitherSkeletonAnswersABlowWithOneWeaknessPulse(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		WitherSkeleton skeleton = witherSkeleton(helper, 1, 1);
		Elite.mark(skeleton, GearTier.IRON, 1.0);
		Zombie attacker = zombie(helper, 3, 1);
		skeleton.setTarget(attacker);
		skeleton.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
		skeleton.damageCooldownTime = 0;
		skeleton.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
		List<Holder<MobEffect>> pulses = PULSES.getOrDefault(skeleton.getUUID(), List.of()).stream().filter(effect -> effect.equals(MobEffects.WEAKNESS)).toList();
		result("witherWeakness", "weaknessPulses=" + pulses.size() + " attackerWeak=" + attacker.hasEffect(MobEffects.WEAKNESS));
		helper.assertTrue(pulses.size() == 1 && attacker.hasEffect(MobEffects.WEAKNESS), "two blows made " + pulses.size() + " weakness pulses");
		helper.succeed();
	}

	@GameTest
	public void aWildPatrolNeverJoinsARaid(final GameTestHelper helper) {
		Pillager pillager = EntityTypes.PILLAGER.create(helper.getLevel(), EntitySpawnReason.PATROL);
		FriendlyPatrol.befriend(pillager);
		result("patrolRaid", "canJoinRaid=" + pillager.canJoinRaid());
		helper.assertFalse(pillager.canJoinRaid(), "a friendly patrol can join a raid");
		pillager.discard();
		helper.succeed();
	}

	@GameTest
	public void aBredWolfNeverRollsElite(final GameTestHelper helper) {
		int elites = 0;
		for (int i = 0; i < BRED_WOLVES; i++) {
			Wolf pup = EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.BREEDING);
			pup.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);
			helper.getLevel().addFreshEntity(pup);
			elites += Elite.isElite(pup) ? 1 : 0;
			pup.discard();
		}
		result("bredWolves", "pups=" + BRED_WOLVES + " elites=" + elites + " chance=" + WolfPack.ELITE_CHANCE);
		helper.assertTrue(elites == 0, elites + " of " + BRED_WOLVES + " bred pups rolled elite");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void anEliteWolfSparesAVillagePatrol(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Wolf wolf = EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.EVENT);
		wolf.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		wolf.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);
		wolf.setPersistenceRequired();
		helper.getLevel().addFreshEntity(wolf);
		WolfPack.makeElite(wolf);
		Pillager patrol = EntityTypes.PILLAGER.create(helper.getLevel(), EntitySpawnReason.PATROL);
		FriendlyPatrol.befriend(patrol);
		patrol.snapTo(helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)), 0.0F, 0.0F);
		patrol.setNoAi(true);
		patrol.setPersistenceRequired();
		helper.getLevel().addFreshEntity(patrol);
		Zombie hostile = zombie(helper, 4, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(hostile.hasEffect(MobEffects.SLOWNESS), "the aura has not pulsed yet");
			result("wolfSparesPatrol", "patrolSlowed=" + patrol.hasEffect(MobEffects.SLOWNESS));
			helper.assertFalse(patrol.hasEffect(MobEffects.SLOWNESS), "the elite wolf slowed a village patrol");
			wolf.discard();
		});
	}

	private static Zombie zombie(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setPersistenceRequired();
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static Spider spider(final GameTestHelper helper, final net.minecraft.world.entity.EntityType<? extends Spider> type, final int x, final int z) {
		Spider spider = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
		spider.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		spider.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		spider.setPersistenceRequired();
		helper.getLevel().addFreshEntity(spider);
		return spider;
	}

	private static WitherSkeleton witherSkeleton(final GameTestHelper helper, final int x, final int z) {
		WitherSkeleton skeleton = EntityTypes.WITHER_SKELETON.create(helper.getLevel(), EntitySpawnReason.EVENT);
		skeleton.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		skeleton.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		skeleton.setHealth(500.0F);
		skeleton.setPersistenceRequired();
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	private static void result(final String test, final String outcome) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test={} {}", TEST, test, outcome);
	}
}
