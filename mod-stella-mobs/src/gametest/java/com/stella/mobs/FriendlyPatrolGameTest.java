package com.stella.mobs;

import com.stella.mobs.behavior.ChunkEncounters;
import com.stella.mobs.behavior.ChunkEncounters.Encounter;
import com.stella.mobs.behavior.FriendlyPatrol;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * A wild pillager pair is a village patrol on its rounds: it wears the "Village Patrol" label, leaves
 * players, villagers and golems alone, hunts the monsters around it, lets a player sleep beside it, and
 * never frightens a villager.
 */
public class FriendlyPatrolGameTest {
	private static final String TEST = "friendlyPatrol";
	private static final long WATCH_TICKS = 100L;

	@GameTest(padding = 32)
	public void aPairWearsTheVillagePatrolLabel(final GameTestHelper helper) {
		List<Mob> pair = pair(helper);
		for (Mob mob : pair) {
			helper.assertTrue(mob.getCustomName() != null && FriendlyPatrol.LABEL.equals(mob.getCustomName().getString()), "a pair member is named " + mob.getCustomName());
			helper.assertTrue(TextColor.fromLegacyFormat(FriendlyPatrol.LABEL_COLOR).equals(mob.getCustomName().getStyle().getColor()), "the label is " + mob.getCustomName().getStyle().getColor());
			helper.assertTrue(mob.isCustomNameVisible(), "the label is hidden");
			helper.assertTrue(FriendlyPatrol.isFriendly(mob), "a pair member is not friendly");
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aPairWearsTheVillagePatrolLabel members={} label={}", TEST, pair.size(), FriendlyPatrol.LABEL);
		pair.forEach(Mob::discard);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void aPairLeavesAPlayerAlone(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		List<Mob> pair = pair(helper);
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		Vec3 at = pair.get(0).position().add(3.0, 0.0, 0.0);
		player.setPos(at.x, at.y, at.z);
		int[] targeted = {0};
		helper.onEachTick(() -> pair.forEach(mob -> targeted[0] += mob.getTarget() == player ? 1 : 0));
		helper.runAfterDelay(WATCH_TICKS, () -> {
			helper.getLevel().getServer().getPlayerList().remove(player);
			pair.forEach(Mob::discard);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aPairLeavesAPlayerAlone targetedTicks={} watched={}", TEST, targeted[0], WATCH_TICKS);
			helper.assertTrue(targeted[0] == 0, "a pair member targeted the survival player for " + targeted[0] + " ticks");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void aPairHuntsAZombie(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		List<Mob> pair = pair(helper);
		List<Vec3> positions = new java.util.ArrayList<>();
		for (int index = 0; index < pair.size(); index++) {
			Vec3 position = helper.absoluteVec(new Vec3(1.5 + index, 1.0, 2.5));
			positions.add(position);
			pair.get(index).snapTo(position, 0.0F, 0.0F);
		}
		Zombie zombie = besides(helper, pair.get(0), EntityTypes.ZOMBIE, 5.0);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		Set<ChunkPos> held = new HashSet<>();
		pair.forEach(member -> held.add(member.chunkPosition()));
		held.add(zombie.chunkPosition());
		held.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true));
		helper.onEachTick(() -> {
			for (int index = 0; index < pair.size(); index++) {
				Mob member = pair.get(index);
				Vec3 position = positions.get(index);
				member.setPos(position.x, position.y, position.z);
				member.setDeltaMovement(Vec3.ZERO);
			}
		});
		helper.runAfterDelay(190, () -> {
			if (pair.stream().noneMatch(mob -> mob.getTarget() == zombie)) {
				StellaMobs.LOGGER.info("[BEHAVIOR] test={} state=pairHuntPending zombieAlive={} zombiePos={} members={}",
					TEST, zombie.isAlive(), zombie.position(), pair.stream().map(mob ->
						mob.getUUID() + "@" + mob.position() + " ticks=" + mob.tickCount + " target=" + mob.getTarget()).toList());
			}
		});
		helper.succeedWhen(() -> {
			long hunters = pair.stream().filter(mob -> mob.getTarget() == zombie).count();
			helper.assertTrue(hunters > 0, "no pair member has taken the zombie as its target");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aPairHuntsAZombie hunters={}", TEST, hunters);
			pair.forEach(Mob::discard);
			zombie.discard();
			held.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false));
		});
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void anIronGolemLeavesAPairAlone(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		List<Mob> pair = pair(helper);
		IronGolem golem = besides(helper, pair.get(0), EntityTypes.IRON_GOLEM, 4.0);
		int[] targeted = {0};
		helper.onEachTick(() -> targeted[0] += golem.getTarget() != null && pair.contains(golem.getTarget()) ? 1 : 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			pair.forEach(Mob::discard);
			golem.discard();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anIronGolemLeavesAPairAlone targetedTicks={} watched={}", TEST, targeted[0], WATCH_TICKS);
			helper.assertTrue(targeted[0] == 0, "the iron golem targeted a pair member for " + targeted[0] + " ticks");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 32)
	public void aPlayerSleepsBesideAPair(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		List<Mob> pair = pair(helper);
		BlockPos foot = pair.get(0).blockPosition().offset(3, 0, 0);
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		helper.getLevel().setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.getLevel().setBlockAndUpdate(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.setPos(foot.getX() + 1.5, foot.getY(), foot.getZ() + 0.5);
		for (Mob mob : pair) {
			mob.setNoAi(true);
			mob.snapTo(Vec3.atBottomCenterOf(foot.offset(-2, 0, 0)), 0.0F, 0.0F);
		}
		var result = player.startSleepInBed((BedBlock) Blocks.BED.red(), helper.getLevel().getBlockState(foot), helper.getLevel().environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, foot), foot);
		boolean slept = player.isSleeping();
		String problem = result.left().map(Object::toString).orElse("none");
		player.stopSleeping();
		helper.getLevel().getServer().getPlayerList().remove(player);
		pair.forEach(Mob::discard);
		helper.getLevel().setBlockAndUpdate(foot, Blocks.AIR.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(foot.north(), Blocks.AIR.defaultBlockState());
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aPlayerSleepsBesideAPair slept={} problem={}", TEST, slept, problem);
		helper.assertTrue(slept, "the player could not sleep beside the pair: " + problem);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void aVillagerIsNotAfraidOfAPair(final GameTestHelper helper) {
		helper.setTime(6_000L);
		List<Mob> pair = pair(helper);
		pair.forEach(mob -> mob.setNoAi(true));
		Villager villager = besides(helper, pair.get(0), EntityTypes.VILLAGER, 3.0);
		int[] afraid = {0};
		helper.onEachTick(() -> afraid[0] += villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).filter(pair::contains).isPresent() ? 1 : 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			pair.forEach(Mob::discard);
			villager.discard();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aVillagerIsNotAfraidOfAPair afraidTicks={} watched={}", TEST, afraid[0], WATCH_TICKS);
			helper.assertTrue(afraid[0] == 0, "the villager counted a pair member as its nearest hostile for " + afraid[0] + " ticks");
			helper.succeed();
		});
	}

	private static List<Mob> pair(final GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		List<Mob> pair = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.PILLAGER_PAIR, RandomSource.create(11L));
		helper.assertTrue(pair.size() == ChunkEncounters.PILLAGER_PAIR_SIZE, "the pair roll spawned " + pair.size() + " pillagers");
		return pair;
	}

	private static <T extends Mob> T besides(final GameTestHelper helper, final Mob anchor, final net.minecraft.world.entity.EntityType<T> type, final double distance) {
		T mob = type.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		helper.assertTrue(mob != null, "no " + type + " was created");
		mob.snapTo(anchor.position().add(distance, 0.0, 0.0), 0.0F, 0.0F);
		mob.setPersistenceRequired();
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

}
