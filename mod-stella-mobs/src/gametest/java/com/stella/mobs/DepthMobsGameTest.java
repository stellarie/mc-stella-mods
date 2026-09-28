package com.stella.mobs;

import com.stella.mobs.behavior.DepthMobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Depth mobs: one natural spawn in a hundred below y=0 turns Depth, never within four chunks of another. A Depth
 * mob has twice the health of its kind and drops echo shards and a keepsake; its zombie gets up twice, its
 * archer slips to a safer spot, its creeper leaves fire, and its spider's bite darkens and poisons.
 */
public class DepthMobsGameTest {
	private static final String TEST = "depthMobs";
	private static final double FAR_FROM_TESTS = 10_000.0;

	@GameTest
	public void theDepthChanceIsWhatShips(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_260_928L);
		int samples = 200_000;
		int depth = 0;
		for (int i = 0; i < samples; i++) {
			depth += DepthMobs.rolls(random) ? 1 : 0;
		}
		double rate = depth / (double) samples;
		double band = 4 * Math.sqrt(DepthMobs.CHANCE * (1 - DepthMobs.CHANCE) / samples);
		result("theDepthChanceIsWhatShips", "rate=" + rate + " chance=" + DepthMobs.CHANCE);
		helper.assertTrue(Math.abs(rate - DepthMobs.CHANCE) <= band, "Depth rolled at " + rate);
		helper.succeed();
	}

	@GameTest
	public void onlyANaturalSpawnBelowZeroTurnsDepth(final GameTestHelper helper) {
		// Far from every test area: a Depth mob is solitary, so a neighbouring test's Depth mob within the
		// solitary radius would refuse the roll and read as a height rule failing (once in six gate runs).
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)).add(FAR_FROM_TESTS, 0.0, FAR_FROM_TESTS);
		Zombie deep = unspawned(helper, EntityTypes.ZOMBIE, at.x, -20.0, at.z);
		Zombie high = unspawned(helper, EntityTypes.ZOMBIE, at.x + DepthMobs.SOLITARY_RADIUS * 3, 20.0, at.z);
		Zombie spawner = unspawned(helper, EntityTypes.ZOMBIE, at.x + DepthMobs.SOLITARY_RADIUS * 6, -20.0, at.z);
		boolean deepTurned;
		boolean highTurned;
		boolean spawnerTurned;
		try {
			DepthMobs.pinChance(1.0);
			deepTurned = DepthMobs.rollAtSpawn(deep, helper.getLevel(), EntitySpawnReason.NATURAL);
			highTurned = DepthMobs.rollAtSpawn(high, helper.getLevel(), EntitySpawnReason.NATURAL);
			spawnerTurned = DepthMobs.rollAtSpawn(spawner, helper.getLevel(), EntitySpawnReason.SPAWNER);
		} finally {
			DepthMobs.pinChance(DepthMobs.CHANCE);
		}
		result("onlyANaturalSpawnBelowZeroTurnsDepth", "deep=" + deepTurned + " high=" + highTurned + " spawner=" + spawnerTurned);
		deep.discard();
		high.discard();
		spawner.discard();
		helper.assertTrue(deepTurned && !highTurned && !spawnerTurned, "the Depth roll answered deep=" + deepTurned + " high=" + highTurned + " spawner=" + spawnerTurned);
		helper.succeed();
	}

	@GameTest
	public void aDepthMobIsSolitary(final GameTestHelper helper) {
		floor(helper, 8);
		Mob first = spawn(helper, EntityTypes.ZOMBIE, 2, 2);
		DepthMobs.makeDepth(first);
		Zombie second = unspawned(helper, EntityTypes.ZOMBIE, first.getX() + 10.0, -20.0, first.getZ());
		second.setPos(first.getX() + 10.0, first.getY(), first.getZ());
		boolean alone = DepthMobs.alone(helper.getLevel(), second);
		result("aDepthMobIsSolitary", "alone=" + alone);
		second.discard();
		helper.assertFalse(alone, "a second Depth mob could rise ten blocks from the first");
		helper.succeed();
	}

	@GameTest
	public void aDepthMobHasTwiceTheHealthAndItsName(final GameTestHelper helper) {
		floor(helper, 8);
		Mob zombie = spawn(helper, EntityTypes.ZOMBIE, 2, 2);
		double before = zombie.getMaxHealth();
		DepthMobs.makeDepth(zombie);
		String name = zombie.getCustomName() == null ? "none" : zombie.getCustomName().getString();
		result("aDepthMobHasTwiceTheHealthAndItsName", "before=" + before + " after=" + zombie.getMaxHealth() + " name=" + name);
		helper.assertTrue(Math.abs(zombie.getMaxHealth() - 2.0 * before) < 1.0E-3 && zombie.getHealth() == zombie.getMaxHealth(), "Depth health is " + zombie.getMaxHealth());
		helper.assertTrue(name.startsWith("Depth") && zombie.isPersistenceRequired(), "the Depth mob is named " + name);
		helper.succeed();
	}

	@GameTest
	public void aDepthZombieGetsUpTwice(final GameTestHelper helper) {
		floor(helper, 8);
		Mob zombie = spawn(helper, EntityTypes.ZOMBIE, 2, 2);
		DepthMobs.makeDepth(zombie);
		int[] alive = new int[3];
		for (int i = 0; i < 3; i++) {
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10_000.0F);
			alive[i] = zombie.isAlive() ? 1 : 0;
		}
		result("aDepthZombieGetsUpTwice", "alive=" + java.util.Arrays.toString(alive));
		helper.assertTrue(alive[0] == 1 && alive[1] == 1 && alive[2] == 0, "the Depth zombie lived through " + java.util.Arrays.toString(alive));
		helper.succeed();
	}

	/** A /kill or the void is not a blow to rise from: the Depth zombie's revives answer only a fight. */
	@GameTest
	public void aKillCommandEndsADepthZombieAtOnce(final GameTestHelper helper) {
		floor(helper, 8);
		Mob zombie = spawn(helper, EntityTypes.ZOMBIE, 2, 2);
		DepthMobs.makeDepth(zombie);
		int revives = DepthMobs.revivesLeft(zombie);
		zombie.kill(helper.getLevel());
		result("aKillCommandEndsADepthZombieAtOnce", "revivesBefore=" + revives + " alive=" + zombie.isAlive());
		helper.assertTrue(revives > 0, "the zombie had no revives, so the case proves nothing");
		helper.assertFalse(zombie.isAlive(), "the Depth zombie rose from a /kill");
		helper.succeed();
	}

	@GameTest(maxTicks = 60, padding = 32)
	public void aDepthArcherSlipsAwayFromACloseTarget(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		floor(helper, 24);
		// Inside the test's own area, whose chunks always tick: a mob out in a chunk that does not never runs its goals.
		Mob skeleton = spawn(helper, EntityTypes.SKELETON, 2, 2);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		DepthMobs.makeDepth(skeleton);
		Mob target = spawn(helper, EntityTypes.PIG, 3, 2);
		target.setNoAi(true);
		helper.onEachTick(() -> skeleton.setTarget(target));
		helper.succeedWhen(() -> {
			double distance = skeleton.distanceTo(target);
			helper.assertTrue(distance >= DepthMobs.SAFE_DISTANCE_MIN - 1.0, "the archer stands " + distance + " from its target");
			result("aDepthArcherSlipsAwayFromACloseTarget", "distance=" + distance);
		});
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void aDepthCreepersBlastLeavesFire(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		floor(helper, 12);
		Creeper creeper = (Creeper) spawn(helper, EntityTypes.CREEPER, 6, 6);
		DepthMobs.makeDepth(creeper);
		net.minecraft.core.BlockPos center = creeper.blockPosition();
		creeper.ignite();
		helper.succeedWhen(() -> {
			helper.assertTrue(creeper.isRemoved(), "the creeper has not blown yet");
			int fires = 0;
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					for (int dy = -3; dy <= 3; dy++) {
						fires += helper.getLevel().getBlockState(center.offset(dx, dy, dz)).is(Blocks.FIRE) ? 1 : 0;
					}
				}
			}
			result("aDepthCreepersBlastLeavesFire", "fires=" + fires);
			helper.assertTrue(fires > 0, "the blast left no fire");
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					for (int dy = -3; dy <= 3; dy++) {
						if (helper.getLevel().getBlockState(center.offset(dx, dy, dz)).is(Blocks.FIRE)) {
							helper.getLevel().setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		});
	}

	@GameTest
	public void aDepthSpidersBiteDarkensAndPoisons(final GameTestHelper helper) {
		floor(helper, 8);
		Mob spider = spawn(helper, EntityTypes.SPIDER, 2, 2);
		DepthMobs.makeDepth(spider);
		Mob victim = spawn(helper, EntityTypes.PIG, 4, 2);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(spider), 1.0F);
		int poison = victim.hasEffect(MobEffects.POISON) ? victim.getEffect(MobEffects.POISON).getAmplifier() : -1;
		result("aDepthSpidersBiteDarkensAndPoisons", "darkness=" + victim.hasEffect(MobEffects.DARKNESS) + " poison=" + poison);
		helper.assertTrue(victim.hasEffect(MobEffects.DARKNESS) && poison == DepthMobs.BITE_POISON_AMPLIFIER, "the bite left darkness=" + victim.hasEffect(MobEffects.DARKNESS) + " poison=" + poison);
		helper.succeed();
	}

	@GameTest
	public void aDepthMobDropsEchoShardsAndAKeepsake(final GameTestHelper helper) {
		floor(helper, 8);
		Mob zombie = spawn(helper, EntityTypes.ZOMBIE, 2, 2);
		DepthMobs.makeDepth(zombie);
		List<ItemStack> drops = DepthMobs.drops(zombie, RandomSource.create(1L));
		result("aDepthMobDropsEchoShardsAndAKeepsake", "drops=" + drops);
		helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.ECHO_SHARD)) && drops.stream().anyMatch(stack -> stack.is(Items.GOLDEN_APPLE)),
			"a Depth zombie drops " + drops);
		helper.succeed();
	}

	private static <T extends Mob> T unspawned(final GameTestHelper helper, final EntityType<T> type, final double x, final double y, final double z) {
		T mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		mob.snapTo(x, y, z, 0.0F, 0.0F);
		return mob;
	}

	private static Mob spawn(final GameTestHelper helper, final EntityType<? extends Mob> type, final int x, final int z) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		mob.setPersistenceRequired();
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static void floor(final GameTestHelper helper, final int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void result(final String test, final String outcome) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test={} {}", TEST, test, outcome);
	}
}
