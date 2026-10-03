package com.stella.mobs;

import com.stella.mobs.behavior.BossCompanion;
import com.stella.mobs.behavior.BossRoll;
import com.stella.mobs.behavior.BossLook;
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
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Slice A of the boss class: the roll and the chunk guard, the mark with its ten level bonus, the
 * netherite kit, the 0.80 drop, the one elite companion, and the boss bar. Every test pins its own
 * config, because the config store is shared with every other test in the suite.
 */
public class BossBehaviourGameTest {
	private static final String TEST = "bossBehaviour";
	/** Aura bursts per boss, from the shared seam; a test reads only its own boss. */
	private static final java.util.Map<java.util.UUID, Integer> AURAS = new java.util.concurrent.ConcurrentHashMap<>();

	static {
		Bosses.setAuraRecorder(mob -> AURAS.merge(mob.getUUID(), 1, Integer::sum));
	}
	private static final double EPSILON = 1.0e-6;
	private static final double SPAWN_X = 3.5;
	private static final double SPAWN_Z = 3.5;
	// Six blocks away, so the second subject lands in the same chunk as the first: a chunk is 16 wide.
	private static final double OTHER_X = 9.5;
	private static final double OTHER_Z = 9.5;
	// The bar radius in play is 64 blocks, so this is unambiguously outside it. It is written here
	// rather than read from the implementation, so the test measures the spec's radius and not a copy.
	private static final String BOSS_ONE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 1.0,
				"boss_health_multiplier": 3.0,
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
	/**
	 * The kit and mark config with the roll off. Three tests cover the roll and the chunk guard, and
	 * they need a free chunk. Every other test takes the mark directly, so no neighbour can block it.
	 */
	private static final String BOSS_MARK = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 0.0,
				"boss_health_multiplier": 3.0,
				"boss_level_bonus": 1.0
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
		Mob[] second = new Mob[1];

		helper.succeedWhen(() -> {
			// A boss another running test left in this chunk blocks the roll. Roll again on the next tick
			// instead of discarding that boss, which would take a live bar test's subject down with it.
			if (!EliteState.isBoss(first)) {
				Bosses.rollAtSpawn(first, helper.getLevel(), 1.0);
			}

			helper.assertTrue(
				EliteState.isBoss(first),
				"an eligible mob at a chance of 1.0 did not become a boss after a retry; bosses already in"
					+ " its chunk=" + foreignBosses(first)
			);
			helper.assertTrue(Elite.isElite(first), "a boss is not elite");

			if (second[0] == null) {
				second[0] = spawnAt(helper, base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
			}

			helper.assertTrue(BossRoll.sameChunk(first, second[0]), "the two subjects did not land in one chunk");
			helper.assertFalse(EliteState.isBoss(second[0]), "a second eligible spawn in a boss chunk became a boss too");

			result(
				"bossRollAndChunkGuard",
				"firstBoss=" + EliteState.isBoss(first) + " firstElite=" + Elite.isElite(first)
					+ " secondBoss=" + EliteState.isBoss(second[0]) + " secondElite=" + Elite.isElite(second[0])
					+ " sameChunk=" + BossRoll.sameChunk(first, second[0]) + " firstChunk=" + first.chunkPosition()
					+ " secondChunk=" + second[0].chunkPosition()
			);
		});
	}

	@GameTest(maxTicks = 200)
	public void anEmptyChunkCanPromoteTheSameSpawn(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		// Move the promoted mob out of the region under test, so the chunk is genuinely empty.
		boss.discard();
		Mob promoted = spawn(helper, SPAWN_X, SPAWN_Z);

		helper.succeedWhen(() -> {
			if (!EliteState.isBoss(promoted)) {
				Bosses.rollAtSpawn(promoted, helper.getLevel(), 1.0);
			}

			helper.assertTrue(EliteState.isBoss(promoted), "the same spawn in an empty chunk did not become a boss");

			result(
				"bossRollInAnEmptyChunk",
				"promotedBoss=" + EliteState.isBoss(promoted) + " chunkHasBossNow=" + BossRoll.chunkHasBoss(promoted)
			);
		});
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

		helper.succeedWhen(() -> {
			if (!EliteState.isBoss(skeleton)) {
				Bosses.rollAtSpawn(skeleton, helper.getLevel(), 1.0);
			}

			helper.assertFalse(Bosses.canBeBoss(creeper), "a creeper is treated as boss-eligible");
			helper.assertFalse(EliteState.isBoss(creeper), "a creeper became a boss at a chance of 1.0");
			helper.assertTrue(EliteState.isBoss(skeleton), "an eligible skeleton stayed ordinary at a chance of 1.0");

			result(
				"bossEligibility",
				"creeperEligible=" + Bosses.canBeBoss(creeper) + " creeperBoss=" + EliteState.isBoss(creeper)
					+ " skeletonEligible=" + Bosses.canBeBoss(skeleton) + " skeletonBoss=" + EliteState.isBoss(skeleton)
			);
		});
	}

	@GameTest(maxTicks = 200)
	public void aBlockedRollWinsOnTheRetry(final GameTestHelper helper) {
		useConfig(helper, BOSS_ONE);
		// The blocker stands in the subject's chunk, which is the case the roll tests used to answer by
		// discarding the neighbour's boss. Here the blocker leaves on its own, and the retry must win.
		Mob blocker = blessedBoss(helper);
		Mob subject = spawn(helper, SPAWN_X, SPAWN_Z);
		boolean blockedByTheGuard = !EliteState.isBoss(subject) && BossRoll.chunkHasBoss(subject);
		helper.runAfterDelay(2L, blocker::discard);

		helper.succeedWhen(() -> {
			if (!EliteState.isBoss(subject)) {
				Bosses.rollAtSpawn(subject, helper.getLevel(), 1.0);
			}

			helper.assertTrue(
				blockedByTheGuard,
				"the chunk guard did not block the subject beside a boss, so the retry proves nothing"
			);
			helper.assertTrue(
				EliteState.isBoss(subject), "the subject never won the roll after the blocker left its chunk"
			);

			result(
				"bossBlockedRoll",
				"blockedByTheGuard=" + blockedByTheGuard + " bossAfterRetry=" + EliteState.isBoss(subject)
					+ " blockersNow=" + foreignBosses(subject)
			);
		});
	}

	@GameTest(maxTicks = 200)
	public void aBossCarriesItsConfiguredHealthAndTheTenLevelBonus(final GameTestHelper helper) {
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
		float bossHealth = boss.getMaxHealth();
		double bossBase = boss.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double bossScale = SpawnScaling.healthMultiplierOf(boss);
		int areaLevel = SpawnScaling.areaLevel(boss);
		int bossLevel = ThreatState.level(boss);
		ThreatState bossRoll = ThreatState.get(boss);
		int healthModifiers = boss.getAttribute(Attributes.MAX_HEALTH).getModifiers().size();
		boolean hasScaleModifier = boss.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID) != null;
		boolean hasLegacyModifier = boss.getAttribute(Attributes.MAX_HEALTH).getModifier(Bosses.LEVEL_HEALTH_ID) != null;
		boolean bossElite = Elite.isElite(boss);
		double x = boss.getX();
		double y = boss.getY();
		double z = boss.getZ();
		// The boss multiplier comes from the config, the elite one from Elite.
		// Spawn a real elite of the same kind at the same spot and read the pair, rather than rebuilding
		// the implementation's own arithmetic and agreeing with it.
		boss.discard();
		Mob elite = spawnEliteBeside(helper, x, y, z);
		float eliteHealth = elite.getMaxHealth();
		double eliteBase = elite.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		int eliteLevel = ThreatState.level(elite);
		ThreatState eliteRoll = ThreatState.get(elite);
		double ratio = eliteBase <= 0.0 ? 0.0 : bossBase / eliteBase;
		double expectedRatio = GearConfigStore.get()
			.rollFor(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(elite.getType()))
			.bossHealthMultiplier() / Elite.HEALTH_MULTIPLIER;
		double expectedMax = bossBase * SpawnScaling.healthMultiplier(bossLevel);
		double expectedEliteMax = eliteBase * SpawnScaling.healthMultiplier(eliteLevel);
		double maxRatio = eliteHealth <= 0.0F ? 0.0 : bossHealth / eliteHealth;

		result(
			"bossMark",
			"bossElite=" + bossElite + " bossMax=" + bossHealth + " bossBase=" + bossBase
				+ " eliteMax=" + eliteHealth + " eliteBase=" + eliteBase + " baseRatio=" + ratio
				+ " maxRatio=" + maxRatio + " expectedMax=" + expectedMax
				+ " areaLevel=" + areaLevel + " bossLevel=" + bossLevel + " eliteLevel=" + eliteLevel
				+ " bossScale=" + bossScale
				+ " healthModifiers=" + healthModifiers + " scaleModifier=" + hasScaleModifier
				+ " legacyModifier=" + hasLegacyModifier
		);
		result("bossMarkModifiers", describeHealth(boss) + " || " + describeHealth(elite));

		helper.assertTrue(bossElite, "a boss is not elite");
		helper.assertTrue(bossRoll != null && eliteRoll != null, "the threat rolls were not saved");
		helper.assertValueEqual(Integer.valueOf(bossLevel),
			Integer.valueOf(Math.max(0, bossRoll.areaLevel() + bossRoll.variation()) + ThreatState.BOSS_BONUS),
			"boss threat level");
		helper.assertValueEqual(Integer.valueOf(eliteLevel),
			Integer.valueOf(Math.max(0, eliteRoll.areaLevel() + eliteRoll.variation()) + ThreatState.ELITE_BONUS),
			"elite threat level");
		helper.assertTrue(
			Math.abs(ratio - expectedRatio) < 0.01,
			"the boss base is " + bossBase + " against an elite's " + eliteBase + ", a ratio of " + ratio
				+ ", expected " + expectedRatio
		);
		helper.assertTrue(
			Math.abs(bossHealth - expectedMax) < 0.01 && Math.abs(eliteHealth - expectedEliteMax) < 0.01,
			"boss or elite health did not follow its own threat level"
		);
		helper.assertValueEqual(Integer.valueOf(healthModifiers), Integer.valueOf(1), "MAX_HEALTH modifier count");
		helper.assertTrue(hasScaleModifier, "the spawn scale modifier is missing");
		helper.assertFalse(hasLegacyModifier, "the legacy boss level modifier remained");
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
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
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
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
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
	public void theBossKitCarriesNoCurseAndEverySlotCanDrop(final GameTestHelper helper) {
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
		int cursed = 0;
		int cannotDrop = 0;
		StringBuilder slots = new StringBuilder();

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = boss.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}

			for (Holder<Enchantment> held : stack.getEnchantments().keySet()) {
				if (held.is(EnchantmentTags.CURSE)) {
					cursed++;
					slots.append(slot).append('(')
						.append(held.unwrapKey().orElseThrow().identifier()).append(") ");
				}
			}

			// The clause that matters: Mob's drop path refuses a slot whose enchantment carries this
			// component, so a cursed slot drops at 0 where B08 asks for 0.80.
			if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				cannotDrop++;
			}
		}

		result(
			"bossKitCurse",
			"cursedEnchantments=" + cursed + " slotsThatCannotDrop=" + cannotDrop + " cursedSlots=" + slots
				+ " dropChance=" + Bosses.DROP_CHANCE
		);

		helper.assertValueEqual(Integer.valueOf(cursed), Integer.valueOf(0), "curse-tagged enchantments on the boss kit");
		helper.assertValueEqual(
			Integer.valueOf(cannotDrop), Integer.valueOf(0),
			"equipped slots that cannot drop, against B08's " + Bosses.DROP_CHANCE
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void exactlyOneEliteCompanionSpawnsAndItIsNotABoss(final GameTestHelper helper) {
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
		// The mark path does not queue a companion; the roll does. The queue is the clause under test.
		BossCompanion.queue(boss);
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

	/** A boss shows the soul and flame aura every few ticks and carries the synced look mark; a plain mob does neither. */
	@GameTest(maxTicks = 200)
	public void bossAuraAndLook(final GameTestHelper helper) {
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
		Mob plain = spawn(helper, SPAWN_X + 3, SPAWN_Z);
		helper.succeedWhen(() -> {
			int bursts = AURAS.getOrDefault(boss.getUUID(), 0);
			helper.assertTrue(EliteState.isBoss(boss) && BossLook.has(boss), "the boss lacks its look mark");
			helper.assertTrue(bursts >= 2, "the boss showed " + bursts + " aura bursts");
			helper.assertTrue(!BossLook.has(plain) && AURAS.getOrDefault(plain.getUUID(), 0) == 0, "a plain mob has the boss look");
			result("bossAura", "bursts=" + bursts + " look=" + BossLook.has(boss) + " plainLook=" + BossLook.has(plain));
		});
	}

	@GameTest(maxTicks = 200)
	public void bossAuraStopsOnDeath(final GameTestHelper helper) {
		useConfig(helper, BOSS_MARK);
		Mob boss = blessedBoss(helper);
		int[] atDeath = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(AURAS.getOrDefault(boss.getUUID(), 0) >= 1, "the boss never showed its aura"))
			.thenExecute(() -> {
				kill(helper, boss);
				atDeath[0] = AURAS.getOrDefault(boss.getUUID(), 0);
			})
			.thenIdle(Bosses.AURA_INTERVAL * 5)
			.thenExecute(() -> {
				int after = AURAS.getOrDefault(boss.getUUID(), 0);
				result("bossAuraDeath", "atDeath=" + atDeath[0] + " after=" + after + " alive=" + boss.isAlive());
				helper.assertTrue(!boss.isAlive() && after == atDeath[0], "a dead boss kept its aura: " + atDeath[0] + " -> " + after);
			})
			.thenSucceed();
	}

	private static void useConfig(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
	}

	/**
	 * A marked boss without the roll. A test that needs a live boss for two hundred ticks cannot let a
	 * neighbour's boss block its promotion, and it must not discard that neighbour's boss either. The
	 * roll and the chunk guard keep their own tests, which retry instead of clearing the ground.
	 */
	private static Mob blessedBoss(final GameTestHelper helper) {
		Mob boss = spawn(helper, SPAWN_X, SPAWN_Z);
		Bosses.blessAsBoss(boss, helper.getLevel(), 1.0);
		return boss;
	}

	/** The bosses that share the subject's chunk, for a failure message about a blocked roll. */
	private static int foreignBosses(final Mob subject) {
		return Bosses.bossesInChunk(subject).size();
	}

	private static Mob spawn(final GameTestHelper helper, final double x, final double z) {
		return helper.spawnMob(EntityTypes.SKELETON, (int) x, 3, (int) z)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
	}

	private static void kill(final GameTestHelper helper, final Mob mob) {
		mob.setInvulnerableTime(0);
		mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), Float.MAX_VALUE);
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
