package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.RuinMobKind;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance item 12, and the kit and drops of the four mobs. */
public class DeepForgeMobGameTest {
	static final int TICKS = 12000;
	static final long SEED = 0xF0E6E700L;

	static Vec3 spot(final ForgeTestSupport.Arena arena, final int dx) {
		BlockPos at = arena.markers("guardian").get(0);
		return new Vec3(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5);
	}

	/** A husk with a huge health pool, so a hit never kills it. Husks do not burn in daylight. */
	static Husk target(final ServerLevel level, final Vec3 at) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
		husk.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000.0);
		husk.setHealth(1_000_000.0F);
		husk.setNoAi(true);
		husk.snapTo(at.x, at.y, at.z);
		level.addFreshEntity(husk);
		return husk;
	}

	@GameTest(maxTicks = TICKS)
	public void aCinderImpsHitSetsItsTargetOnFireForEightyTicks(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			Mob imp = DeepForgeMobs.imp.spawn(level, spot(arena, 0));
			imp.setNoAi(true);
			Husk victim = target(level, spot(arena, 2));
			Husk control = target(level, spot(arena, -2));
			control.hurtServer(level, level.damageSources().generic(), 1.0F);
			helper.assertTrue(!control.isOnFire(), "a plain hit set the control on fire, so the check proves nothing");
			victim.hurtServer(level, level.damageSources().mobAttack(imp), 1.0F);
			DeepForgeMobGameTest.result("impIgnites", "fireTicks=" + victim.getRemainingFireTicks() + " control=" + control.getRemainingFireTicks());
			helper.assertTrue(victim.isOnFire() && victim.getRemainingFireTicks() >= DeepForgeMobs.IMP_FIRE_TICKS - 5, "the imp's target burns for " + victim.getRemainingFireTicks() + " ticks");
			imp.discard();
			victim.discard();
			control.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theForgeGuardianSlamKnocksBackAndHurtsPlayersWithinFiveBlocksOnly(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 1, arena -> {
			ServerLevel level = arena.level();
			Mob guardian = DeepForgeMobs.guardian.spawn(level, spot(arena, 0));
			guardian.setNoAi(true);
			ServerPlayer near = arena.join(spot(arena, 3));
			ServerPlayer far = arena.join(spot(arena, 9));
			float nearBefore = near.getHealth();
			float farBefore = far.getHealth();
			Vec3 nearPush = near.getDeltaMovement();
			guardian.tickCount = DeepForgeMobs.SLAM_PERIOD_TICKS - 1;
			DeepForgeMobs.guardian.ability().tick(guardian, level);
			helper.assertTrue(near.getHealth() == nearBefore, "the guardian slammed before its period");
			guardian.tickCount = DeepForgeMobs.SLAM_PERIOD_TICKS + 20;
			DeepForgeMobs.guardian.ability().tick(guardian, level);
			helper.assertTrue(near.getHealth() == nearBefore, "the guardian slammed outside its five tick window");
			guardian.tickCount = DeepForgeMobs.SLAM_PERIOD_TICKS;
			DeepForgeMobs.guardian.ability().tick(guardian, level);
			Vec3 away = near.position().subtract(guardian.position()).normalize();
			Vec3 push = near.getDeltaMovement().subtract(nearPush);
			double along = push.x * away.x + push.z * away.z;
			DeepForgeMobGameTest.result("guardianSlam", "nearDamage=" + (nearBefore - near.getHealth()) + " push=" + String.format("%.2f", along) + " farDamage=" + (farBefore - far.getHealth()));
			// A mob attack scales with the world difficulty, so the expectation does too.
			float expected = switch (level.getDifficulty()) {
				case NORMAL -> DeepForgeMobs.SLAM_DAMAGE;
				case EASY -> Math.min(DeepForgeMobs.SLAM_DAMAGE / 2.0F + 1.0F, DeepForgeMobs.SLAM_DAMAGE);
				case HARD -> DeepForgeMobs.SLAM_DAMAGE * 1.5F;
				default -> 0.0F;
			};
			helper.assertTrue(expected > 0.0F, "the test world is peaceful, so a slam cannot be measured");
			helper.assertTrue(Math.abs((nearBefore - near.getHealth()) - expected) < 0.01F, "the slam dealt " + (nearBefore - near.getHealth()) + " damage on " + level.getDifficulty() + ", not " + expected);
			helper.assertTrue(along > 1.0, "the slam pushed the player " + along + " blocks per tick away, not clear of the guardian");
			helper.assertTrue(far.getHealth() == farBefore && far.getDeltaMovement().horizontalDistance() < 0.1, "a player nine blocks away was slammed");
			guardian.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aDeepGolemAndTheGuardianHuntSurvivalPlayersInRangeAndNobodyElse(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 2, arena -> {
			ServerLevel level = arena.level();
			Mob golem = DeepForgeMobs.golem.spawn(level, spot(arena, 0));
			golem.setNoAi(true);
			ServerPlayer spectator = arena.join(spot(arena, 2));
			spectator.setGameMode(GameType.SPECTATOR);
			DeepForgeMobs.golem.ability().tick(golem, level);
			helper.assertTrue(golem.getTarget() == null, "a golem hunted a spectator");
			ServerPlayer far = arena.join(spot(arena, 40));
			DeepForgeMobs.golem.ability().tick(golem, level);
			helper.assertTrue(golem.getTarget() == null, "a golem hunted a player forty blocks away");
			ServerPlayer near = arena.join(spot(arena, -3));
			DeepForgeMobs.golem.ability().tick(golem, level);
			helper.assertTrue(golem.getTarget() == near, "a golem did not hunt the survival player beside it");
			golem.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachKindBuildsWithTheKitThePlanNames(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 3, arena -> {
			ServerLevel level = arena.level();
			Mob plainWither = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
			Mob plainBlaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.EVENT);
			Mob plainGolem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.EVENT);
			Mob keeper = DeepForgeMobs.keeper.spawn(level, spot(arena, 0));
			Mob imp = DeepForgeMobs.imp.spawn(level, spot(arena, 1));
			Mob golem = DeepForgeMobs.golem.spawn(level, spot(arena, 2));
			Mob guardian = DeepForgeMobs.guardian.spawn(level, spot(arena, 3));
			helper.assertTrue(Math.abs(keeper.getAttributeValue(Attributes.SCALE) - 1.2) < 1.0E-6, "keeper scale " + keeper.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(keeper.getMaxHealth() - plainWither.getMaxHealth() * 2.5) < 1.0E-3, "keeper health " + keeper.getMaxHealth());
			helper.assertTrue(keeper.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STONE_AXE) && keeper.getDropChances().byEquipment(EquipmentSlot.MAINHAND) == 0.0F, "the keeper's axe is missing or can drop");
			helper.assertTrue(Math.abs(imp.getAttributeValue(Attributes.SCALE) - 0.6) < 1.0E-6, "imp scale " + imp.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(imp.getMaxHealth() - plainBlaze.getMaxHealth() * 0.8) < 1.0E-3, "imp health " + imp.getMaxHealth());
			helper.assertTrue(Math.abs(golem.getAttributeValue(Attributes.SCALE) - 1.0) < 1.0E-6 && Math.abs(golem.getMaxHealth() - plainGolem.getMaxHealth()) < 1.0E-3, "golem differs from a plain golem");
			helper.assertTrue(Math.abs(guardian.getAttributeValue(Attributes.SCALE) - 1.8) < 1.0E-6, "guardian scale " + guardian.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(guardian.getMaxHealth() - plainGolem.getMaxHealth() * 6.0) < 1.0E-3, "guardian health " + guardian.getMaxHealth());
			helper.assertTrue(guardian.isCustomNameVisible() && !keeper.isCustomNameVisible(), "the boss name is not shown, or a keeper's is");
			for (Mob mob : new Mob[] {keeper, imp, golem, guardian}) {
				mob.setNoAi(true);
				mob.discard();
			}
		});
	}

	/** One roll of a loot table for a mob killed by a player. */
	static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final Mob mob, final ServerPlayer player) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		DamageSource source = level.damageSources().playerAttack(player);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.THIS_ENTITY, mob)
			.withParameter(LootContextParams.ORIGIN, mob.position())
			.withParameter(LootContextParams.DAMAGE_SOURCE, source)
			.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
			.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
			.create(LootContextParamSets.ENTITY);
		return table.getRandomItems(params);
	}

	@GameTest(maxTicks = TICKS)
	public void everyMobTableHoldsItsOwnDropsAtItsOwnRate(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 4, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(spot(arena, 0));
			Mob imp = DeepForgeMobs.imp.spawn(level, spot(arena, 2));
			imp.setNoAi(true);
			int dusts = 0;
			int drops = 0;
			int rolls = 400;
			for (int i = 0; i < rolls; i++) {
				for (ItemStack stack : roll(level, DeepForgeMobs.IMP_DROPS, imp, player)) {
					helper.assertTrue(stack.is(DeepForgeItems.CINDER_DUST) && stack.getCount() >= 1 && stack.getCount() <= 2, "an imp dropped " + stack);
					dusts += stack.getCount();
					drops++;
				}
			}
			double rate = drops / (double) rolls;
			DeepForgeMobGameTest.result("impDrops", "rolls=" + rolls + " dropRate=" + String.format("%.3f", rate) + " dust=" + dusts);
			helper.assertTrue(rate > 0.40 && rate < 0.60, "the imp's drop rate is " + rate + ", not near one half");
			Mob golem = DeepForgeMobs.golem.spawn(level, spot(arena, -2));
			golem.setNoAi(true);
			for (int i = 0; i < 50; i++) {
				List<ItemStack> got = roll(level, DeepForgeMobs.GOLEM_DROPS, golem, player);
				helper.assertTrue(got.size() == 1 && got.get(0).is(DeepForgeItems.ORE_CHUNK) && got.get(0).getCount() >= 2 && got.get(0).getCount() <= 3, "a golem dropped " + got);
			}
			for (int i = 0; i < 50; i++) {
				List<ItemStack> got = roll(level, DeepForgeMobs.KEEPER_DROPS, golem, player);
				int cores = got.stream().filter(s -> s.is(DeepForgeItems.FORGE_CORE)).mapToInt(ItemStack::getCount).sum();
				int chunks = got.stream().filter(s -> s.is(DeepForgeItems.ORE_CHUNK)).mapToInt(ItemStack::getCount).sum();
				helper.assertTrue(cores == 1 && chunks >= 1 && chunks <= 2, "a keeper's table gave " + cores + " cores and " + chunks + " chunks");
			}
			for (int i = 0; i < 50; i++) {
				List<ItemStack> got = roll(level, DeepForgeMobs.GUARDIAN_DROPS, golem, player);
				int ingots = got.stream().filter(s -> s.is(DeepForgeItems.INGOT)).mapToInt(ItemStack::getCount).sum();
				int shards = got.stream().filter(s -> s.is(DeepForgeItems.FORGEHEART_SHARD)).mapToInt(ItemStack::getCount).sum();
				helper.assertTrue(ingots >= 4 && ingots <= 6 && shards == 1, "the guardian's table gave " + ingots + " ingots and " + shards + " shards");
			}
			imp.discard();
			golem.discard();
		});
	}

	static void result(final String test, final String line) {
		ForgeTestSupport.result(test, line);
	}
}
