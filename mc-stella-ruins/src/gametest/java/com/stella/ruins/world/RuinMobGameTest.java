package com.stella.ruins.world;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Items 9 and 15, and what a themed mob is: its kit, its drops, its unique roll, and its level beside the mobs mod. */
public class RuinMobGameTest {
	static final String MOBS_MOD = "mod-stella-mobs";
	static final long ROLL_SEED = 20261004L;
	static final int ROLLS = 200_000;
	static final int CONTROL_MOBS = 300;

	static boolean mobsModLoaded() {
		return FabricLoader.getInstance().isModLoaded(MOBS_MOD);
	}

	static Vec3 spawnSpot(final WorldTestSupport.Arena arena) {
		net.minecraft.core.BlockPos spot = arena.markers(RuinMarkers.SPAWN).get(0);
		return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	static int itemsNear(final WorldTestSupport.Arena arena, final Item item) {
		AABB box = AABB.of(arena.frame().box()).inflate(2.0);
		int total = 0;
		for (ItemEntity entity : arena.level().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	static CompoundTag save(final ServerLevel level, final Mob mob) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		mob.saveWithoutId(output);
		return output.buildResult();
	}

	/** True when a saved mob carries an elite, boss or nemesis mark of the mobs or villages mods. */
	static boolean promoted(final ServerLevel level, final Mob mob) {
		CompoundTag saved = save(level, mob);
		CompoundTag attachments = saved.getCompoundOrEmpty("fabric:attachments");
		CompoundTag elite = attachments.getCompoundOrEmpty("mod-stella-mobs:elite_state");
		return saved.toString().toLowerCase(Locale.ROOT).contains("nemesis") || elite.getBooleanOr("elite", false) || elite.getBooleanOr("boss", false);
	}

	@GameTest(maxTicks = 400)
	public void aKindBuildsAThemedMobWithItsNameGearScaleAttributesAndEffect(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			Mob mob = WorldTestInit.husk.spawn(arena.level(), spawnSpot(arena));
			helper.assertTrue(mob != null && arena.level().getEntity(mob.getUUID()) == mob, "the kind mob is not in the level");
			Mob plain = EntityTypes.HUSK.create(arena.level(), EntitySpawnReason.EVENT);
			helper.assertTrue(mob.getMaxHealth() == plain.getMaxHealth() * 2.0F && mob.getHealth() == mob.getMaxHealth(), "health is " + mob.getHealth() + "/" + mob.getMaxHealth() + ", not twice " + plain.getMaxHealth());
			helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.ATTACK_DAMAGE) - plain.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5) < 1.0E-6, "attack damage is " + mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
			helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - plain.getAttributeValue(Attributes.MOVEMENT_SPEED)) < 1.0E-9, "a speed multiplier of 1.0 changed the speed");
			helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.SCALE) - 1.25) < 1.0E-6, "scale is " + mob.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet is missing");
			helper.assertTrue(mob.getDropChances().byEquipment(EquipmentSlot.HEAD) == 0.0F, "the helmet can drop: " + mob.getDropChances().byEquipment(EquipmentSlot.HEAD));
			MobEffectInstance effect = mob.getEffect(MobEffects.FIRE_RESISTANCE);
			helper.assertTrue(effect != null && effect.isInfiniteDuration(), "the effect is missing or ends");
			helper.assertTrue(mob.getCustomName() != null && mob.getCustomName().getContents() instanceof TranslatableContents name && name.getKey().equals("mob.mod-stella-ruins-test.husk"), "the name is " + mob.getCustomName());
			helper.assertTrue(mob.isPersistenceRequired(), "the mob can despawn");
			helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB) && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "test_husk"), "the ruin tags are missing: " + mob.entityTags());
			WorldTestSupport.result("kindBuildsMob", "health=" + mob.getMaxHealth() + " scale=" + mob.getAttributeValue(Attributes.SCALE) + " tags=" + mob.entityTags().size());
			mob.discard();
		});
	}

	@GameTest(maxTicks = 400)
	public void aKindsAbilityTicksOnItsOwnAndHearsItsMobsHits(final GameTestHelper helper) {
		WorldTestSupport.Arena arena = WorldTestSupport.arena(helper.getLevel(), WorldTestInit.surface, net.minecraft.world.level.block.Rotation.NONE);
		Mob[] mob = {null};
		Pig[] victim = {null};
		int[] before = {0, 0};
		helper.succeedWhen(() -> {
			helper.assertTrue(WorldTestSupport.ready(arena), "the arena chunks do not tick entities yet");
			if (mob[0] == null) {
				before[0] = WorldTestInit.ABILITY_TICKS.get();
				mob[0] = WorldTestInit.husk.spawn(arena.level(), spawnSpot(arena));
				mob[0].setNoAi(true);
				victim[0] = EntityTypes.PIG.create(arena.level(), EntitySpawnReason.EVENT);
				victim[0].snapTo(spawnSpot(arena).add(2.0, 0.0, 0.0));
				arena.level().addFreshEntity(victim[0]);
				before[1] = WorldTestInit.ABILITY_HITS.get();
				victim[0].hurtServer(arena.level(), arena.level().damageSources().mobAttack(mob[0]), 1.0F);
			}
			helper.assertTrue(WorldTestInit.ABILITY_HITS.get() == before[1] + 1, "the ability heard " + (WorldTestInit.ABILITY_HITS.get() - before[1]) + " hits, not 1");
			helper.assertTrue(WorldTestInit.ABILITY_TICKS.get() > before[0], "the ability has not ticked");
			WorldTestSupport.result("abilityTicks", "ticks=" + (WorldTestInit.ABILITY_TICKS.get() - before[0]) + " hits=" + (WorldTestInit.ABILITY_HITS.get() - before[1]));
			mob[0].discard();
			victim[0].discard();
			arena.release();
		});
	}

	@GameTest(maxTicks = 400)
	public void aThemedMobKilledByAPlayerDropsFromItsKindTable(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerPlayer player = arena.join(arena.standAt(3, 3));
			Mob mob = WorldTestInit.husk.spawn(arena.level(), spawnSpot(arena));
			mob.setNoAi(true);
			int before = itemsNear(arena, Items.GOLD_NUGGET);
			mob.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
			int dropped = itemsNear(arena, Items.GOLD_NUGGET) - before;
			WorldTestSupport.result("playerKillDrops", "goldNuggets=" + dropped);
			helper.assertTrue(!mob.isAlive(), "the mob survived the kill");
			helper.assertTrue(dropped == 1, "the kind table dropped " + dropped + " gold nuggets, not 1");
		});
	}

	@GameTest(maxTicks = 400)
	public void aDeathByAnythingButAPlayerRollsNothingExtra(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withUniqueDropChance(1.0));
			try {
				Mob mob = WorldTestInit.husk.spawn(arena.level(), spawnSpot(arena));
				mob.setNoAi(true);
				mob.hurtServer(arena.level(), arena.level().damageSources().generic(), 10_000.0F);
				helper.assertTrue(!mob.isAlive(), "the mob survived");
				helper.assertTrue(itemsNear(arena, Items.GOLD_NUGGET) == 0, "a death by no player dropped from the kind table");
				helper.assertTrue(itemsNear(arena, Items.NETHER_STAR) == 0, "a death by no player rolled the unique at a chance of 1.0");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}

	@GameTest(maxTicks = 400)
	public void theUniqueDropsAtTheConfiguredChanceForASiteMobsOnly(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			RuinConfig.Values old = RuinConfig.get();
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				List<java.util.UUID> roster = arena.entry().roster();
				helper.assertTrue(roster.size() == 3, "the roster holds " + roster.size());
				RuinConfig.replace(old.withUniqueDropChance(1.0));
				((Mob) arena.level().getEntity(roster.get(0))).hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
				int atOne = itemsNear(arena, Items.NETHER_STAR);
				RuinConfig.replace(old.withUniqueDropChance(0.0));
				((Mob) arena.level().getEntity(roster.get(1))).hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
				int atZero = itemsNear(arena, Items.NETHER_STAR);
				WorldTestSupport.result("uniqueFollowsConfig", "starsAtChance1=" + atOne + " starsAfterChance0=" + atZero);
				helper.assertTrue(atOne == 1, "a chance of 1.0 dropped " + atOne + " uniques");
				helper.assertTrue(atZero == 1, "a chance of 0.0 dropped a unique: " + atZero + " in all");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}

	@GameTest
	public void twoHundredThousandSeededUniqueRollsLandWithinFifteenPercentOfTheConfiguredChance(final GameTestHelper helper) {
		double chance = RuinConfig.get().uniqueDropChance();
		helper.assertTrue(chance == RuinConfig.DEFAULT_UNIQUE_DROP_CHANCE && chance == 0.001, "the configured chance is " + chance + ", not the plan's 0.001");
		double expected = ROLLS * chance;
		double tolerance = 0.15 * expected;
		int hits = rolls(RandomSource.create(ROLL_SEED));
		RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withUniqueDropChance(chance * 2.0));
		int doubled;
		try {
			doubled = rolls(RandomSource.create(ROLL_SEED));
		} finally {
			RuinConfig.replace(old);
		}
		WorldTestSupport.result("uniqueRollRate", "rolls=" + ROLLS + " chance=" + chance + " hits=" + hits + " expected=" + expected + " tolerance=" + tolerance + " hitsAtDoubleChance=" + doubled);
		helper.assertTrue(Math.abs(hits - expected) <= tolerance, hits + " hits in " + ROLLS + " rolls is outside " + expected + " +- " + tolerance);
		helper.assertTrue(Math.abs(doubled - expected) > tolerance, "the bound cannot tell twice the chance from the configured one");
		helper.succeed();
	}

	private static int rolls(final RandomSource random) {
		int hits = 0;
		for (int i = 0; i < ROLLS; i++) {
			hits += RuinMobs.rollUnique(random) ? 1 : 0;
		}
		return hits;
	}

	@GameTest(maxTicks = 400)
	public void withTheMobsModARuinMobHasALevelAtLeastTheContainerLevelAndNoPromotion(final GameTestHelper helper) {
		if (!mobsModLoaded()) {
			WorldTestSupport.result("mobsPresent", "skipped=true reason=sibling-absent");
			helper.succeed();
			return;
		}
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			int promotedRuin = 0;
			int promotedControl = 0;
			int below = 0;
			int minGap = Integer.MAX_VALUE;
			for (int i = 0; i < CONTROL_MOBS; i++) {
				Mob ruin = WorldTestInit.husk.spawn(level, spawnSpot(arena));
				int container = RuinLevels.at(level, ruin.position());
				int level0 = RuinLevels.of(ruin);
				minGap = Math.min(minGap, level0 - container);
				below += level0 >= container && level0 >= 0 ? 0 : 1;
				promotedRuin += promoted(level, ruin) ? 1 : 0;
				ruin.discard();
				Mob control = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
				control.snapTo(spawnSpot(arena));
				level.addFreshEntity(control);
				promotedControl += promoted(level, control) ? 1 : 0;
				control.discard();
			}
			WorldTestSupport.result("mobsPresent", "ruinMobs=" + CONTROL_MOBS + " belowContainerLevel=" + below + " minLevelMinusContainer=" + minGap + " promotedRuin=" + promotedRuin
				+ " promotedControl=" + promotedControl);
			helper.assertTrue(promotedControl > 0, "no plain husk was promoted in " + CONTROL_MOBS + ", so the promotion check proves nothing");
			helper.assertTrue(promotedRuin == 0, promotedRuin + " ruin mobs carry an elite, boss or nemesis mark");
			helper.assertTrue(below == 0, below + " ruin mobs had a level below the container level or none");
		});
	}

	@GameTest(maxTicks = 400)
	public void withoutTheMobsModLevelsReadAsAbsentAndAMobStillSpawns(final GameTestHelper helper) {
		if (mobsModLoaded()) {
			WorldTestSupport.result("mobsAbsent", "skipped=true reason=sibling-present");
			helper.succeed();
			return;
		}
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			Mob mob = WorldTestInit.husk.spawn(arena.level(), spawnSpot(arena));
			helper.assertTrue(mob != null && mob.isAlive(), "the mob did not spawn without the mobs mod");
			helper.assertTrue(RuinLevels.at(arena.level(), mob.position()) == 0, "the container level is not 0 without the mobs mod");
			helper.assertTrue(RuinLevels.of(mob) == -1, "a mob has a level without the mobs mod");
			helper.assertTrue(RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), mob.position())) == 1, "a vault rolls more than once at level 0");
			WorldTestSupport.result("mobsAbsent", "containerLevel=0 mobLevel=-1 spawned=true");
			mob.discard();
		});
	}
}
