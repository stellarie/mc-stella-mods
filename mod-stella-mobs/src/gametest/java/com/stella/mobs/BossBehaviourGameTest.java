package com.stella.mobs;

import com.stella.mobs.behavior.BossCompanion;
import com.stella.mobs.behavior.BossRoll;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.EnchantRoller;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Slice A of the boss class: the roll and the chunk guard, the mark with its ten level bonus, the
 * netherite kit, the 0.80 drop, the one elite companion, and the boss bar. Every test pins its own
 * config, because the config store is shared with every other test in the suite.
 */
public class BossBehaviourGameTest {
	private static final String TEST = "bossBehaviour";
	private static final double EPSILON = 1.0e-6;
	private static final double SPAWN_X = 3.5;
	private static final double SPAWN_Z = 3.5;
	// Six blocks away, so the second subject lands in the same chunk as the first: a chunk is 16 wide.
	private static final double OTHER_X = 9.5;
	private static final double OTHER_Z = 9.5;
	private static final String BOSS_ONE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 1.0,
				"boss_health_multiplier": 4.0,
				"boss_level_bonus": 1.0
			}
		}""";
	private static final String BOSS_OFF = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 0.0
			}
		}""";
	/** The elite-only config the health comparison uses: one elite, no boss. */
	private static final String ELITE_ONE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 1.0,
				"boss_chance": 0.0
			}
		}""";
	private static final String BOSS_INELIGIBLE_ONLY = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 1.0,
				"entities": { "minecraft:skeleton": { "boss_chance": 1.0 } }
			}
		}""";

	@GameTest(maxTicks = 200)
	public void anEligibleMobBecomesABossAndTheChunkGuardBlocksTheSecond(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		// One block apart: the pair is in one chunk whatever the structure origin is, so the guard is
		// the thing under test rather than the arena's coordinates.
		var base = helper.absolutePos(new net.minecraft.core.BlockPos(3, 3, 3));
		// The second subject shares the first one's block, so the pair is in one chunk whatever the
		// structure origin is and the guard is the only thing under test.
		Mob first = spawnAt(helper, base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		Mob second = spawnAt(helper, base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		boolean firstIsBoss = EliteState.isBoss(first);
		boolean firstIsElite = Elite.isElite(first);
		boolean secondIsBoss = EliteState.isBoss(second);

		result(
			"bossRollAndChunkGuard",
			"firstBoss=" + firstIsBoss + " firstElite=" + firstIsElite + " secondBoss=" + secondIsBoss
				+ " secondElite=" + Elite.isElite(second) + " sameChunk=" + BossRoll.sameChunk(first, second)
				+ " firstChunk=" + first.chunkPosition() + " secondChunk=" + second.chunkPosition()
		);

		helper.assertTrue(
			firstIsBoss,
			"an eligible mob at a chance of 1.0 did not become a boss; bosses already in its chunk="
				+ foreignBosses(helper, first) + " (the config store is global, so a neighbouring arena can"
				+ " roll one while this test runs)"
		);
		helper.assertTrue(firstIsElite, "a boss is not elite");
		helper.assertTrue(BossRoll.sameChunk(first, second), "the two subjects did not land in one chunk");
		helper.assertFalse(secondIsBoss, "a second eligible spawn in a boss chunk became a boss too");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void anEmptyChunkCanPromoteTheSameSpawn(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		// Move the promoted mob out of the region under test, so the chunk is genuinely empty.
		boss.discard();
		Mob promoted = spawn(helper, SPAWN_X, SPAWN_Z);
		boolean chunkHasBoss = BossRoll.chunkHasBoss(promoted);
		boolean promotedIsBoss = EliteState.isBoss(promoted);

		result(
			"bossRollInAnEmptyChunk",
			"promotedBoss=" + promotedIsBoss + " chunkHasBossBefore=" + chunkHasBoss
		);

		helper.assertTrue(promotedIsBoss, "the same spawn in an empty chunk did not become a boss");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void anIneligibleMobNeverBecomesABoss(final GameTestHelper helper) {
		useConfig(helper, BOSS_INELIGIBLE_ONLY);
		Mob creeper = helper.spawnMob(EntityTypes.CREEPER, 2, 3, 2)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 6, 3, 2)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
		boolean creeperIsBoss = EliteState.isBoss(creeper);
		boolean skeletonIsBoss = EliteState.isBoss(skeleton);

		result(
			"bossEligibility",
			"creeperEligible=" + Bosses.canBeBoss(creeper) + " creeperBoss=" + creeperIsBoss
				+ " skeletonEligible=" + Bosses.canBeBoss(skeleton) + " skeletonBoss=" + skeletonIsBoss
		);

		helper.assertFalse(Bosses.canBeBoss(creeper), "a creeper is treated as boss-eligible");
		helper.assertFalse(creeperIsBoss, "a creeper became a boss at a chance of 1.0");
		helper.assertTrue(skeletonIsBoss, "an eligible skeleton stayed ordinary at a chance of 1.0");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aBossCarriesTwiceAnElitesHealthAndTheTenLevelBonus(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		float bossHealth = boss.getMaxHealth();
		double bossBase = boss.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double areaScale = SpawnScaling.healthMultiplierOf(boss);
		// The boss step, not R12's own scaling: these are the two different modifiers by id. The
		// attribute is passed explicitly, because selecting it from the id read R12's health modifier
		// for the health case and reported 0.0 while the dump showed 0.5.
		double levelHealth = bossLevelBonus(boss, Attributes.MAX_HEALTH, Bosses.LEVEL_HEALTH_ID);
		double levelDamage = bossLevelBonus(boss, Attributes.ATTACK_DAMAGE, Bosses.LEVEL_DAMAGE_ID);
		int areaLevel = SpawnScaling.areaLevel(boss);
		double levelBonus = GearConfigStore.get().rollFor(Identifier.parse("minecraft:skeleton")).bossLevelBonus();
		int healthModifiers = boss.getAttribute(Attributes.MAX_HEALTH).getModifiers().size();
		boolean hasScaleModifier = boss.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID) != null;
		boolean hasLevelModifier = boss.getAttribute(Attributes.MAX_HEALTH).getModifier(Bosses.LEVEL_HEALTH_ID) != null;
		// The step is ten levels of R12's own slope, so it is a constant: the area level is already
		// paid for by spawn_scale_health, which both mobs carry exactly once.
		double expectedLevelHealth = Bosses.LEVEL_HEALTH_STEP * levelBonus;
		double expectedLevelDamage = Bosses.LEVEL_DAMAGE_STEP * levelBonus;
		boolean bossElite = Elite.isElite(boss);
		double x = boss.getX();
		double y = boss.getY();
		double z = boss.getZ();
		// The spec in oniichan's own words: a boss has twice the health of an elite standing beside it.
		// Spawn a real elite of the same kind at the same spot and read the pair, rather than rebuilding
		// the implementation's own arithmetic and agreeing with it.
		boss.discard();
		Mob elite = spawnEliteBeside(helper, x, y, z);
		float eliteHealth = elite.getMaxHealth();
		double eliteBase = elite.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double areaRatio = SpawnScaling.healthMultiplierOf(elite) <= 0.0
			? 0.0
			: SpawnScaling.healthMultiplierOf(boss) / SpawnScaling.healthMultiplierOf(elite);
		double ratio = eliteBase <= 0.0 ? 0.0 : bossBase / eliteBase;
		double expectedMax = 2.0 * eliteHealth * (1.0 + expectedLevelHealth);
		double maxRatio = eliteHealth <= 0.0F ? 0.0 : bossHealth / eliteHealth;

		result(
			"bossMark",
			"bossElite=" + bossElite + " bossMax=" + bossHealth + " bossBase=" + bossBase
				+ " eliteMax=" + eliteHealth + " eliteBase=" + eliteBase + " baseRatio=" + ratio
				+ " maxRatio=" + maxRatio + " expectedMax=" + expectedMax
				+ " areaRatio=" + areaRatio + " areaLevel=" + areaLevel
				+ " levelHealthStep=" + levelHealth + " expectedLevelHealthStep=" + expectedLevelHealth
				+ " levelDamageStep=" + levelDamage + " expectedLevelDamageStep=" + expectedLevelDamage
				+ " healthModifiers=" + healthModifiers + " scaleModifier=" + hasScaleModifier
				+ " levelModifier=" + hasLevelModifier
		);
		result("bossMarkModifiers", describeHealth(boss) + " || " + describeHealth(elite));

		helper.assertTrue(bossElite, "a boss is not elite");
		// The step is a constant at every position. The shipped config gives 0.5 and 0.25 everywhere,
		// including area level 0, where a step multiplied by the area level reads 0.0.
		helper.assertTrue(
			Math.abs(levelHealth - expectedLevelHealth) < 1.0e-9,
			"the boss health step is " + levelHealth + ", not the constant " + expectedLevelHealth
				+ " at area level " + areaLevel
		);
		helper.assertTrue(
			Math.abs(levelDamage - expectedLevelDamage) < 1.0e-9,
			"the boss damage step is " + levelDamage + ", not the constant " + expectedLevelDamage
				+ " at area level " + areaLevel
		);
		// The spec is twice an elite's health. Compare the bases, because the boss's level step also
		// raises its maximum and that is its own clause below.
		helper.assertTrue(
			Math.abs(ratio - 2.0) < 0.01,
			"the boss base is " + bossBase + " against an elite's " + eliteBase + ", a ratio of " + ratio
		);
		// Twice the elite's maximum, times one plus the ten level step.
		helper.assertTrue(
			Math.abs(bossHealth - expectedMax) < 0.01,
			"the boss maximum is " + bossHealth + ", not twice the elite's " + eliteHealth
				+ " times one plus the step, which is " + expectedMax
		);
		// The area multiplier must land on both mobs exactly once. A boss carrying it twice shows up
		// here as a ratio near 1.04 or 1.75 rather than 1.0.
		helper.assertTrue(
			Math.abs(areaRatio - 1.0) < 0.01,
			"the boss carries a different area scale from the elite beside it: " + areaRatio
		);
		// Exactly two health modifiers: R12's spawn scaling and the boss level step. A third means a
		// second scaling path ran, which is the defect this slice shipped.
		helper.assertValueEqual(Integer.valueOf(healthModifiers), Integer.valueOf(2), "MAX_HEALTH modifier count");
		helper.assertTrue(hasScaleModifier, "the spawn scale modifier is missing");
		helper.assertTrue(hasLevelModifier, "the boss level modifier is missing");
		helper.succeed();
	}

	/** A real, rolled elite of the same kind at the same spot, for the health comparison. */
	private static Mob spawnEliteBeside(final GameTestHelper helper, final double x, final double y, final double z) {
		GearConfigStore.set(GearConfigParser.parse(ELITE_ONE));
		Mob elite = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		if (elite == null) {
			throw new IllegalStateException("the comparison elite could not be created");
		}

		elite.snapTo(x, y, z, 0.0F, 0.0F);
		elite.finalizeSpawn(
			helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(elite.blockPosition()),
			EntitySpawnReason.NATURAL, null
		);
		helper.getLevel().addFreshEntity(elite);
		return elite;
	}

	@GameTest(maxTicks = 200)
	public void theBossKitIsFullyEnchantedNetherite(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		int minimum = Integer.MAX_VALUE;
		boolean netherite = true;
		boolean consistent = true;
		boolean enchanted = true;

		for (EquipmentSlot slot : GearTier.armorSlots()) {
			ItemStack stack = boss.getItemBySlot(slot);
			netherite &= stack.is(GearTier.NETHERITE.armorFor(slot));
			int count = stack.getEnchantments().size();
			minimum = Math.min(minimum, count);
			enchanted &= count > 0;
			consistent &= hasNoExclusivePair(stack);
		}

		result(
			"bossKit",
			"armourNetherite=" + netherite + " minimumEnchantments=" + minimum + " anyEnchantment=" + enchanted
				+ " mutualExclusion=" + !consistent + " mainHand=" + boss.getMainHandItem()
		);

		helper.assertTrue(netherite, "the boss armour is not netherite: " + describe(boss));
		helper.assertTrue(enchanted, "a boss armour piece carries no enchantment: " + describe(boss));
		helper.assertTrue(consistent, "the boss armour carries two mutually exclusive enchantments: " + describe(boss));
		helper.assertTrue(
			minimum >= 3, "the thinnest boss armour piece carries only " + minimum + " enchantments: " + describe(boss)
		);
		helper.succeed();
	}

	private static String describeHealth(final Mob mob) {
		StringBuilder text = new StringBuilder();
		var instance = mob.getAttribute(Attributes.MAX_HEALTH);
		text.append(mob.getType()).append(" base=").append(instance.getBaseValue())
			.append(" max=").append(mob.getMaxHealth()).append(" modifiers=[");

		for (var modifier : instance.getModifiers()) {
			text.append(modifier.id().toString()).append('(').append(modifier.operation()).append('=')
				.append(modifier.amount()).append(") ");
		}

		return text.append(']').toString();
	}

	private static String describe(final Mob mob) {
		return "boss=" + EliteState.isBoss(mob) + " type=" + mob.getType() + " head=" + mob.getItemBySlot(EquipmentSlot.HEAD);
	}

	@GameTest(maxTicks = 200)
	public void everyEquippedSlotDropsAtEightyPercent(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		boolean atRate = true;
		StringBuilder seen = new StringBuilder();

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (boss.getItemBySlot(slot).isEmpty()) {
				continue;
			}

			float chance = boss.getDropChances().byEquipment(slot);
			atRate &= Math.abs(chance - Bosses.DROP_CHANCE) < EPSILON;
			seen.append(slot).append('=').append(chance).append(' ');
		}

		result("bossDrop", "dropChance=" + Bosses.DROP_CHANCE + " atRate=" + atRate + " slots=" + seen);

		helper.assertTrue(atRate, "an equipped boss slot does not drop at " + Bosses.DROP_CHANCE);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void exactlyOneEliteCompanionSpawnsAndItIsNotABoss(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		boolean bossFlag = EliteState.isBoss(boss);
		helper.succeedWhen(() -> {
			List<Mob> companions = BossCompanion.companionsOf(helper.getLevel(), boss);
			helper.assertTrue(bossFlag, "the subject never became a boss, so no companion was due");
			helper.assertValueEqual(Integer.valueOf(companions.size()), Integer.valueOf(BossCompanion.COUNT), "companion count");
			Mob companion = companions.getFirst();
			boolean elite = Elite.isElite(companion);
			boolean companionBoss = EliteState.isBoss(companion);
			boolean summoned = EliteState.isSummoned(companion);

			result(
				"bossCompanion",
				"count=" + companions.size() + " elite=" + elite + " boss=" + companionBoss + " summoned=" + summoned
					+ " health=" + companion.getMaxHealth()
			);

			helper.assertTrue(elite, "the companion is not elite");
			helper.assertFalse(companionBoss, "the companion rolled a boss itself");
			helper.assertTrue(summoned, "the companion is not flagged summoned");
		});
	}

	@GameTest(maxTicks = 200)
	public void theBossBarReportsTheHealthFraction(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		helper.runAfterDelay(2L, () -> {
			float full = ReactiveState.get(boss).bossBarProgress();
			boss.setHealth(boss.getMaxHealth() * 0.25F);
			Bosses.updateBossBar((ServerLevel) helper.getLevel(), boss);
			float quarter = ReactiveState.get(boss).bossBarProgress();

			result("bossBar", "full=" + full + " quarter=" + quarter + " health=" + boss.getHealth());

			helper.assertTrue(Math.abs(full - 1.0F) < 0.01F, "the boss bar does not start full: " + full);
			helper.assertTrue(
				Math.abs(quarter - 0.25F) < 0.01F, "the boss bar does not follow the health fraction: " + quarter
			);
			helper.succeed();
		});
	}

	private static void useConfig(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		// Another arena's boss can be standing in this structure's chunk, and the chunk guard would
		// then leave this test's subject ordinary. Clear the ground before the subject arrives.
		clearBosses(helper);
	}

	private static int foreignBosses(final GameTestHelper helper, final Mob subject) {
		var chunk = subject.chunkPosition();
		var box = new net.minecraft.world.phys.AABB(
			chunk.getMinBlockX(), helper.getLevel().getMinY(), chunk.getMinBlockZ(),
			chunk.getMaxBlockX() + 1.0, helper.getLevel().getMaxY() + 1.0, chunk.getMaxBlockZ() + 1.0
		);
		return helper.getLevel()
			.getEntitiesOfClass(Mob.class, box, candidate -> candidate != subject && Bosses.isBoss(candidate))
			.size();
	}

	private static void clearBosses(final GameTestHelper helper) {
		var base = helper.absolutePos(new net.minecraft.core.BlockPos(3, 3, 3));
		// The whole chunk, because the guard is chunk-exact and the level holds every arena at once: an
		// eight block box misses a neighbouring arena's boss in the same chunk, and that boss then blocks
		// this test's roll with "did not become a boss". Removed bosses are ignored by the guard, so the
		// discard takes effect on the same tick.
		var chunk = helper.getLevel().getChunkAt(base).getPos();
		var box = new net.minecraft.world.phys.AABB(
			chunk.getMinBlockX(), helper.getLevel().getMinY(), chunk.getMinBlockZ(),
			chunk.getMaxBlockX() + 1.0, helper.getLevel().getMaxY() + 1.0, chunk.getMaxBlockZ() + 1.0
		);
		List<Mob> foreign = helper.getLevel().getEntitiesOfClass(Mob.class, box, Bosses::isBoss);

		if (!foreign.isEmpty()) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=bossBehaviour CLEAR chunk={} foreignBosses={}", chunk, foreign.size()
			);
		}

		for (Mob other : foreign) {
			other.discard();
		}
	}

	private static Mob spawn(final GameTestHelper helper, final double x, final double z) {
		return helper.spawnMob(EntityTypes.SKELETON, (int) x, 3, (int) z)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
	}

	private static Mob spawnAt(final GameTestHelper helper, final double x, final double y, final double z) {
		Mob mob = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		if (mob == null) {
			throw new IllegalStateException("the test skeleton could not be created");
		}

		mob.snapTo(x, y, z, 0.0F, 0.0F);
		mob.finalizeSpawn(
			helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()),
			EntitySpawnReason.NATURAL, null
		);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static double bossLevelBonus(final Mob mob, final Holder<Attribute> attribute, final Identifier id) {
		var target = mob.getAttribute(attribute);
		if (target == null) {
			return 0.0;
		}

		var modifier = target.getModifier(id);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static boolean hasNoExclusivePair(final ItemStack stack) {
		ItemEnchantments enchantments = stack.getEnchantments();
		List<Holder<Enchantment>> holders = List.copyOf(enchantments.keySet());

		for (int i = 0; i < holders.size(); i++) {
			for (int j = i + 1; j < holders.size(); j++) {
				if (!Enchantment.areCompatible(holders.get(i), holders.get(j))) {
					return false;
				}
			}
		}

		return true;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", TEST + "." + test, detail);
	}
}
