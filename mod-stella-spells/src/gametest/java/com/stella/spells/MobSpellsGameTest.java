package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobs that cast: a witch's two spells, a stray's lance and an elite stray's meteor, an evoker's sanctuary
 * over its illagers, and the village witch, who strikes the village's foes and heals its friends.
 */
public class MobSpellsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.mobspells");
	private static final Map<UUID, List<String>> CASTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<List<LivingEntity>>> REACHED = new ConcurrentHashMap<>();
	private record CastEvent(String spell, int tick) { }
	private static final Map<UUID, List<CastEvent>> CAST_EVENTS = new ConcurrentHashMap<>();

	static {
		MobSpells.setRecorder((caster, spell, reached) -> {
			CASTS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell);
			REACHED.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(List.copyOf(reached));
			CAST_EVENTS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>())
				.add(new CastEvent(spell, caster.level().getServer().getTickCount()));
		});
	}

	@GameTest
	public void aWitchCarriesOneStrikingAndOneAidingSpell(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 2, 2, List.of());
		List<String> spells = MobSpells.spellsOf(witch);
		long striking = spells.stream().filter(MobSpells.OFFENSIVE::contains).count();
		long aiding = spells.stream().filter(MobSpells.SUPPORT::contains).count();
		LOG.info("[SPELLTEST] test=aWitchCarriesOneStrikingAndOneAidingSpell RESULT spells={}", spells);
		helper.assertTrue(spells.size() == 2 && striking == 1 && aiding == 1, "the witch carries " + spells);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aWitchCastsItsSpellAtItsTarget(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of("ice_lance", "stoneguard"));
		Mob pig = target(helper, 5, 5);
		helper.onEachTick(() -> witch.setTarget(pig));
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(witch.getUUID(), List.of());
			helper.assertTrue(casts.contains("ice_lance"), "the witch has cast " + casts);
			int at = casts.indexOf("ice_lance");
			helper.assertTrue(REACHED.get(witch.getUUID()).get(at).contains(pig), "the lance missed its target");
			LOG.info("[SPELLTEST] test=aWitchCastsItsSpellAtItsTarget RESULT casts={} pigFrozen={}", casts, pig.getTicksFrozen());
		});
	}

	@GameTest(maxTicks = 100)
	public void aWitchHealsItsHurtSide(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of("spark_shot", "sanctuary"));
		Mob ally = mob(helper, EntityTypes.ZOMBIE, 3, 1, List.of());
		ally.setNoAi(true);
		ally.setHealth(ally.getMaxHealth() * 0.3F);
		float before = ally.getHealth();
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(witch.getUUID(), List.of());
			helper.assertTrue(casts.contains("sanctuary"), "no sanctuary yet: " + casts);
			LOG.info("[SPELLTEST] test=aWitchHealsItsHurtSide RESULT before={} after={}", before, ally.getHealth());
			helper.assertTrue(ally.getHealth() > before, "the hurt zombie was not healed");
		});
	}

	@GameTest(maxTicks = 100)
	public void aStrayCastsIceLance(final GameTestHelper helper) {
		floor(helper);
		Mob stray = mob(helper, EntityTypes.STRAY, 1, 1, List.of());
		stray.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		Mob pig = target(helper, 5, 5);
		helper.onEachTick(() -> stray.setTarget(pig));
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(stray.getUUID(), List.of());
			helper.assertTrue(casts.contains("ice_lance"), "the stray has cast " + casts);
			helper.assertFalse(casts.contains(MobSpells.FROZEN_METEOR), "an ordinary stray cast Frozen Meteor");
			LOG.info("[SPELLTEST] test=aStrayCastsIceLance RESULT casts={}", casts);
		});
	}

	@GameTest(maxTicks = 100)
	public void anEliteStrayCastsFrozenMeteorWithoutACombo(final GameTestHelper helper) {
		floor(helper);
		Mob stray = mob(helper, EntityTypes.STRAY, 1, 1, List.of());
		stray.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		// The size mark the mobs mod gives an elite.
		stray.getAttribute(Attributes.SCALE).addPermanentModifier(new AttributeModifier(
			Identifier.fromNamespaceAndPath("mod-stella-mobs", "elite_scale"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		Mob pig = target(helper, 5, 5);
		float before = pig.getHealth();
		helper.onEachTick(() -> stray.setTarget(pig));
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(stray.getUUID(), List.of());
			helper.assertTrue(casts.contains(MobSpells.FROZEN_METEOR), "the elite stray has cast " + casts);
			LOG.info("[SPELLTEST] test=anEliteStrayCastsFrozenMeteorWithoutACombo RESULT casts={} pigLost={}", casts, before - pig.getHealth());
		});
	}

	@GameTest(maxTicks = 100)
	public void anEvokerCastsSanctuaryOverItsHurtIllagers(final GameTestHelper helper) {
		floor(helper);
		Mob evoker = mob(helper, EntityTypes.EVOKER, 1, 1, List.of());
		evoker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		Mob vindicator = mob(helper, EntityTypes.VINDICATOR, 3, 1, List.of());
		vindicator.setNoAi(true);
		vindicator.setHealth(vindicator.getMaxHealth() * 0.3F);
		float before = vindicator.getHealth();
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(evoker.getUUID(), List.of());
			helper.assertTrue(casts.contains("sanctuary"), "no sanctuary yet");
			LOG.info("[SPELLTEST] test=anEvokerCastsSanctuaryOverItsHurtIllagers RESULT before={} after={}", before, vindicator.getHealth());
			helper.assertTrue(vindicator.getHealth() > before, "the hurt vindicator was not healed");
		});
	}

	@GameTest
	public void theVillageWitchThrowsNoPotionAndJoinsNoRaid(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 2, 2, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		boolean potions = witch.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof RangedAttackGoal);
		LOG.info("[SPELLTEST] test=theVillageWitchThrowsNoPotionAndJoinsNoRaid RESULT potions={} raid={} noBehavior={}", potions, witch.canJoinRaid(), witch.entityTags().contains(MobSpells.NO_BEHAVIOR_TAG));
		helper.assertTrue(!potions && !witch.canJoinRaid() && witch.entityTags().contains(MobSpells.NO_BEHAVIOR_TAG), "the village witch keeps its potions or its raid");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void theVillageWitchStrikesTheVillagesFoe(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		witch.getGoalSelector().removeAllGoals(goal -> goal instanceof MobSpells.Blink);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 5, 5, List.of());
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(200.0F);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.onEachTick(() -> witch.setTarget(zombie));
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(witch.getUUID(), List.of());
			helper.assertTrue(witch.getTarget() == zombie, "the village witch targets " + witch.getTarget());
			helper.assertTrue(casts.stream().anyMatch(MobSpells.VILLAGE_WITCH_OFFENSIVE::contains), "no strike yet: " + casts);
			LOG.info("[SPELLTEST] test=theVillageWitchStrikesTheVillagesFoe RESULT casts={}", casts);
		});
	}

	@GameTest(maxTicks = 150)
	public void theVillageWitchUsesItsRequestedSpells(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		witch.getGoalSelector().removeAllGoals(goal -> goal instanceof MobSpells.Blink);
		witch.setHealth(witch.getMaxHealth() * 0.4F);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 3, 1, List.of());
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		zombie.setHealth(100.0F);
		Mob villager = mob(helper, EntityTypes.VILLAGER, 2, 3, List.of());
		villager.setNoAi(true);
		villager.setHealth(5.0F);
		helper.succeedWhen(() -> {
			List<String> casts = CASTS.getOrDefault(witch.getUUID(), List.of());
			helper.assertTrue(casts.containsAll(List.of("wardens_roar", "spark_shot", "life_bloom")),
				"the village witch cast " + casts);
			helper.assertTrue(zombie.getHealth() < 100.0F && villager.getHealth() >= 5.0F,
				"the spells missed their enemy or harmed the villager");
			LOG.info("[SPELLTEST] test=theVillageWitchUsesItsRequestedSpells RESULT casts={} zombie={} villager={}",
				casts, zombie.getHealth(), villager.getHealth());
		});
	}

	@GameTest(maxTicks = 700)
	public void villageWitchSpellCooldownsAndHalfHealthTrigger(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		witch.getGoalSelector().removeAllGoals(goal -> goal instanceof MobSpells.Blink);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 4, 1, List.of());
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		witch.setHealth(witch.getMaxHealth() * 0.4F);
		Vec3 pinned = zombie.position();
		Vec3 witchPinned = witch.position();
		helper.onEachTick(() -> {
			zombie.setPos(pinned.x, pinned.y, pinned.z);
			zombie.setDeltaMovement(Vec3.ZERO);
			witch.setPos(witchPinned.x, witchPinned.y, witchPinned.z);
			witch.setDeltaMovement(Vec3.ZERO);
			if (helper.getTick() >= 250 && CAST_EVENTS.getOrDefault(witch.getUUID(), List.of()).stream()
				.filter(event -> event.spell().equals("life_bloom")).count() < 2) {
				witch.setHealth(Math.min(witch.getHealth(), witch.getMaxHealth() * 0.4F));
			}
		});
		helper.runAfterDelay(650, () -> {
			List<CastEvent> events = CAST_EVENTS.getOrDefault(witch.getUUID(), List.of());
			List<Integer> roars = events.stream().filter(event -> event.spell().equals("wardens_roar"))
				.map(CastEvent::tick).toList();
			List<Integer> sparks = events.stream().filter(event -> event.spell().equals("spark_shot"))
				.map(CastEvent::tick).toList();
			List<Integer> blooms = events.stream().filter(event -> event.spell().equals("life_bloom"))
				.map(CastEvent::tick).toList();
			boolean rates = roars.size() >= 2 && blooms.size() >= 2 && sparks.size() >= 3
				&& roars.get(1) - roars.get(0) >= 300
				&& blooms.get(1) - blooms.get(0) >= 200
				&& java.util.stream.IntStream.range(1, sparks.size()).allMatch(i -> sparks.get(i) - sparks.get(i - 1) >= 40);
			LOG.info("[SPELLTEST] test=villageWitchSpellCooldownsAndHalfHealthTrigger RESULT roars={} sparks={} blooms={} rates={}",
				roars, sparks, blooms, rates);
			helper.assertTrue(rates, "the witch did not honor the 15s, 2s, 10s, and half-health spell rules");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void theVillageWitchSparksOncePerTwoSeconds(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		witch.getGoalSelector().removeAllGoals(goal -> goal instanceof MobSpells.Blink);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 6, 6, List.of());
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.onEachTick(() -> witch.setTarget(zombie));
		helper.runAfterDelay(350, () -> {
			List<Integer> sparks = CAST_EVENTS.getOrDefault(witch.getUUID(), List.of()).stream()
				.filter(event -> event.spell().equals("spark_shot")).map(CastEvent::tick).toList();
			List<Integer> gaps = java.util.stream.IntStream.range(1, sparks.size()).mapToObj(i -> sparks.get(i) - sparks.get(i - 1)).toList();
			LOG.info("[SPELLTEST] test=theVillageWitchSparksOncePerTwoSeconds RESULT sparks={} gaps={}", sparks, gaps);
			helper.assertTrue(sparks.size() >= 5 && gaps.stream().allMatch(gap -> gap >= 40)
				&& gaps.stream().allMatch(gap -> gap <= 50),
				"the spark gaps were " + gaps + " over " + sparks);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 700, padding = 40)
	public void theVillageWitchBlinksOncePerFiveSeconds(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 30; x++) {
			for (int z = 0; z <= 30; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		Witch witch = mob(helper, EntityTypes.WITCH, 15, 15, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 17, 15, List.of());
		var witchChunk = witch.chunkPosition();
		var zombieChunk = zombie.chunkPosition();
		helper.getLevel().setChunkForced(witchChunk.x(), witchChunk.z(), true);
		helper.getLevel().setChunkForced(zombieChunk.x(), zombieChunk.z(), true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setNoAi(true);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		Vec3 pinned = zombie.position();
		helper.onEachTick(() -> {
			zombie.setPos(pinned.x, pinned.y, pinned.z);
			zombie.setDeltaMovement(Vec3.ZERO);
			if (witch.distanceTo(zombie) > MobSpells.VILLAGE_WITCH_RETREAT_DISTANCE - 2.0) {
				witch.setPos(pinned.x - 2.0, pinned.y, pinned.z);
			}
		});
		helper.runAfterDelay(650, () -> {
			List<Integer> blinks = CAST_EVENTS.getOrDefault(witch.getUUID(), List.of()).stream()
				.filter(event -> event.spell().equals("blink")).map(CastEvent::tick).toList();
			List<Integer> gaps = java.util.stream.IntStream.range(1, blinks.size()).mapToObj(i -> blinks.get(i) - blinks.get(i - 1)).toList();
			LOG.info("[SPELLTEST] test=theVillageWitchBlinksOncePerFiveSeconds RESULT blinks={} gaps={}", blinks, gaps);
			helper.getLevel().setChunkForced(witchChunk.x(), witchChunk.z(), false);
			helper.getLevel().setChunkForced(zombieChunk.x(), zombieChunk.z(), false);
			helper.assertTrue(blinks.size() >= 3 && gaps.stream().allMatch(gap -> gap >= 100 && gap <= 110),
				"the blink gaps were " + gaps + " over " + blinks);
			helper.succeed();
		});
	}

	@GameTest
	public void theVillageWitchRoarSparesFriendlies(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 2, 2, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 4, 2, List.of());
		Mob patrol = mob(helper, EntityTypes.PILLAGER, 3, 3, List.of(), "mc-stella-villages.patrol");
		Mob villager = mob(helper, EntityTypes.VILLAGER, 2, 4, List.of());
		float patrolBefore = patrol.getHealth();
		float villagerBefore = villager.getHealth();
		float zombieBefore = zombie.getHealth();
		witch.setTarget(zombie);
		boolean cast = MobSpells.castAt(witch, "wardens_roar", zombie);
		LOG.info("[SPELLTEST] test=theVillageWitchRoarSparesFriendlies RESULT cast={} foeLost={} patrolLost={} villagerLost={}",
			cast, zombieBefore - zombie.getHealth(), patrolBefore - patrol.getHealth(), villagerBefore - villager.getHealth());
		helper.assertTrue(cast && zombie.getHealth() < zombieBefore && patrol.getHealth() == patrolBefore
			&& villager.getHealth() == villagerBefore, "the roar missed its foe or hurt a friend");
		helper.succeed();
	}

	@GameTest(maxTicks = 120, padding = 40)
	public void theVillageWitchRetreatsFromAMeleeEnemy(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 30; x++) {
			for (int z = 0; z <= 30; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		Witch witch = mob(helper, EntityTypes.WITCH, 15, 15, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 17, 15, List.of());
		var witchChunk = witch.chunkPosition();
		var zombieChunk = zombie.chunkPosition();
		helper.getLevel().setChunkForced(witchChunk.x(), witchChunk.z(), true);
		helper.getLevel().setChunkForced(zombieChunk.x(), zombieChunk.z(), true);
		zombie.setNoAi(true);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.succeedWhen(() -> {
			double distance = witch.distanceTo(zombie);
			helper.assertTrue(CASTS.getOrDefault(witch.getUUID(), List.of()).contains("blink")
				&& distance >= MobSpells.VILLAGE_WITCH_SAFE_MIN_DISTANCE - 1.0
				&& distance <= MobSpells.VILLAGE_WITCH_SAFE_MAX_DISTANCE + 1.0
				&& witch.hasLineOfSight(zombie),
				"the witch remained in melee reach or blinked too far: " + distance
					+ " target=" + witch.getTarget() + " witchTicks=" + witch.tickCount
					+ " witchTicking=" + helper.getLevel().isPositionEntityTicking(witch.blockPosition()));
			LOG.info("[SPELLTEST] test=theVillageWitchRetreatsFromAMeleeEnemy RESULT distance={}", distance);
			helper.getLevel().setChunkForced(witchChunk.x(), witchChunk.z(), false);
			helper.getLevel().setChunkForced(zombieChunk.x(), zombieChunk.z(), false);
		});
	}

	@GameTest(maxTicks = 150, padding = 60)
	public void theVillageWitchBlinksToAnEnemyInsideFortyEightBlocks(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 44; x++) {
			for (int z = 0; z <= 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		BlockPos far = helper.absolutePos(new BlockPos(36, 1, 1));
		helper.getLevel().setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
		helper.getLevel().getChunkAt(far);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 36, 1, List.of());
		zombie.setNoAi(true);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		double firstDistance = witch.distanceTo(zombie);
		helper.assertTrue(firstDistance > MobSpells.CAST_RANGE * 2.0,
			"the enemy did not start in the new aggro band: " + firstDistance);
		helper.succeedWhen(() -> {
			helper.assertTrue(witch.getTarget() == zombie
				&& witch.distanceTo(zombie) >= MobSpells.VILLAGE_WITCH_SAFE_MIN_DISTANCE - 1.0
				&& witch.distanceTo(zombie) <= MobSpells.VILLAGE_WITCH_SAFE_MAX_DISTANCE + 1.0
				&& witch.hasLineOfSight(zombie)
				&& CASTS.getOrDefault(witch.getUUID(), List.of()).contains("blink"),
				"the witch has not blinked to its distant enemy: target=" + witch.getTarget()
					+ " distance=" + witch.distanceTo(zombie) + " follow=" + witch.getAttributeValue(Attributes.FOLLOW_RANGE)
					+ " sight=" + witch.hasLineOfSight(zombie) + " ticking="
					+ helper.getLevel().isPositionEntityTicking(zombie.blockPosition()));
			LOG.info("[SPELLTEST] test=theVillageWitchBlinksToAnEnemyInsideFortyEightBlocks RESULT first={} final={}",
				firstDistance, witch.distanceTo(zombie));
			helper.getLevel().setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
		});
	}

	@GameTest(maxTicks = 100)
	public void theVillageWitchHealsItselfAtHalfHealth(final GameTestHelper helper) {
		floor(helper);
		Witch witch = mob(helper, EntityTypes.WITCH, 1, 1, List.of(), MobSpells.VILLAGE_WITCH_TAG);
		witch.setHealth(witch.getMaxHealth() * 0.4F);
		float before = witch.getHealth();
		helper.succeedWhen(() -> {
			helper.assertTrue(CASTS.getOrDefault(witch.getUUID(), List.of()).contains("life_bloom")
				&& witch.getHealth() > before, "the witch did not cast Life Bloom below half health");
			LOG.info("[SPELLTEST] test=theVillageWitchHealsItselfAtHalfHealth RESULT before={} after={}",
				before, witch.getHealth());
		});
	}

	@GameTest
	public void aHostileSpellSparesAVillagePatrol(final GameTestHelper helper) {
		floor(helper);
		Mob patrol = mob(helper, EntityTypes.PILLAGER, 2, 2, List.of(), "mc-stella-villages.patrol");
		Mob raider = mob(helper, EntityTypes.PILLAGER, 3, 3, List.of());
		List<Mob> hit = MoreSpells.hostiles(helper.getLevel(), patrol.position(), 4.0);
		LOG.info("[SPELLTEST] test=aHostileSpellSparesAVillagePatrol RESULT patrol={} raider={}", hit.contains(patrol), hit.contains(raider));
		helper.assertTrue(hit.contains(raider), "the raider is not a foe, so the case proves nothing");
		helper.assertFalse(hit.contains(patrol), "a spell counts a village patrol as a foe");
		helper.succeed();
	}

	@GameTest
	public void aRetiredSpellbookBecomesItsSuccessor(final GameTestHelper helper) {
		boolean all = Spellbooks.RETIRED.entrySet().stream().allMatch(entry ->
			BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, entry.getKey()))
				== BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, entry.getValue())));
		LOG.info("[SPELLTEST] test=aRetiredSpellbookBecomesItsSuccessor RESULT aliased={}", all);
		helper.assertTrue(all, "a retired spellbook's id no longer finds its successor");
		helper.succeed();
	}

	@GameTest
	public void aStackOfArrowsPaysPerSixteen(final GameTestHelper helper) {
		int one = SpellImbue.imbueCost(new ItemStack(Items.ARROW, 1));
		int stack = SpellImbue.imbueCost(new ItemStack(Items.ARROW, 64));
		LOG.info("[SPELLTEST] test=aStackOfArrowsPaysPerSixteen RESULT one={} stack={}", one, stack);
		helper.assertTrue(stack == one * (64 / SpellImbue.ARROWS_PER_PRICE), "sixty-four arrows cost " + stack + " against " + one + " for one");
		helper.succeed();
	}

	@GameTest
	public void aMobsImbueRecordLeavesWithIt(final GameTestHelper helper) {
		floor(helper);
		Mob wielder = mob(helper, EntityTypes.ZOMBIE, 1, 1, List.of());
		Mob victim = target(helper, 3, 3);
		MobImbue.cast(wielder, SpellbookCatalog.get(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "frostbind")), victim);
		boolean tracked = MobImbue.tracks(wielder.getUUID());
		wielder.kill(helper.getLevel());
		boolean kept = MobImbue.tracks(wielder.getUUID());
		LOG.info("[SPELLTEST] test=aMobsImbueRecordLeavesWithIt RESULT tracked={} keptAfterDeath={}", tracked, kept);
		helper.assertTrue(tracked, "the cast left no record, so the case proves nothing");
		helper.assertFalse(kept, "a dead mob's cooldowns stayed in the map");
		helper.succeed();
	}

	private static <T extends Mob> T mob(final GameTestHelper helper, final EntityType<T> type, final int x, final int z, final List<String> spells, final String... tags) {
		T mob = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
		for (String spell : spells) {
			mob.addTag(MobSpells.CAST_TAG_PREFIX + spell);
		}
		for (String tag : tags) {
			mob.addTag(tag);
		}
		mob.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		mob.setPersistenceRequired();
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static Mob target(final GameTestHelper helper, final int x, final int z) {
		Mob pig = helper.spawnMob(EntityTypes.PIG, x, 1, z).withNoFreeWill().spawn();
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		pig.setHealth(200.0F);
		pig.setPersistenceRequired();
		return pig;
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
