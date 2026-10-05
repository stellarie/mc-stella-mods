package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.Exclusives;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinMobKind;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 3 and 10: who wakes, and what each kind drops. */
public class MinerMobsGameTest {
	static final long SEED = 0x10C0FFEEL;
	static final int ROLLS = 4000;

	private static Vec3 spot(final BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed, final boolean entity) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
		if (entity) {
			Mob mob = CampMobs.RESTLESS_MINER.base().create(level, EntitySpawnReason.COMMAND);
			builder.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		return table.getRandomItems(builder.create(entity ? LootContextParamSets.ENTITY : LootContextParamSets.CHEST), seed);
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void activationWakesFiveRestlessMinersAndSixTunnelRatsAtTheCampSpots(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			int miners = arena.rosterOf(CampMobs.RESTLESS_MINER).size();
			int rats = arena.rosterOf(CampMobs.TUNNEL_RAT).size();
			MinerTestSupport.result("campRoster", "miners=" + miners + " rats=" + rats + " roster=" + arena.entry().roster().size());
			helper.assertTrue(miners == CampPlan.MINERS, "the camp woke " + miners + " miners, not " + CampPlan.MINERS);
			helper.assertTrue(rats == CampPlan.RATS, "the camp woke " + rats + " rats, not " + CampPlan.RATS);
			List<BlockPos> spots = arena.markers(RuinMarkers.SPAWN);
			for (UUID id : arena.entry().roster()) {
				Mob mob = (Mob) arena.level().getEntity(id);
				helper.assertTrue(spots.stream().anyMatch(spot -> mob.blockPosition().distManhattan(spot) <= 1),
					"a roster mob woke away from every camp spot at " + mob.blockPosition());
			}
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void theTwoKindsAreAZombieMinerWithAPickAndASilverfishAtScale(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			BlockPos at = arena.markers(RuinMarkers.SPAWN).get(0);
			Mob miner = CampMobs.RESTLESS_MINER.spawn(arena.level(), spot(at));
			Mob rat = CampMobs.TUNNEL_RAT.spawn(arena.level(), spot(at));
			miner.setNoAi(true);
			rat.setNoAi(true);
			for (Mob mob : new Mob[] {miner, rat}) {
				helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB), mob.getType() + " is not tagged as a ruin mob");
			}
			helper.assertTrue(miner.getType() == EntityTypes.ZOMBIE, "the miner is a " + miner.getType());
			helper.assertTrue(miner.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_PICKAXE), "the miner holds " + miner.getItemBySlot(EquipmentSlot.MAINHAND));
			helper.assertTrue(miner.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "the miner wears " + miner.getItemBySlot(EquipmentSlot.HEAD));
			helper.assertTrue(rat.getType() == EntityTypes.SILVERFISH, "the rat is a " + rat.getType());
			helper.assertTrue(Math.abs(rat.getAttributeValue(Attributes.SCALE) - 1.2) < 1.0E-6, "the rat is at scale " + rat.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(CampMobs.RESTLESS_MINER.ability() != null && CampMobs.TUNNEL_RAT.ability() == null, "the kinds carry the wrong abilities");
			helper.assertTrue(CampMobs.RESTLESS_MINER.drops().equals(CampMobs.MINER_DROPS) && CampMobs.TUNNEL_RAT.drops().equals(CampMobs.RAT_DROPS),
				"a kind names another drop table");
			MinerTestSupport.result("campKit", "miner=" + miner.getType() + " held=" + miner.getItemBySlot(EquipmentSlot.MAINHAND) + " rat=" + rat.getType()
				+ " scale=" + rat.getAttributeValue(Attributes.SCALE));
			miner.discard();
			rat.discard();
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aMinersHitLeavesMiningFatigueOneForAHundredTicks(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			BlockPos at = arena.markers(RuinMarkers.SPAWN).get(0);
			Mob miner = CampMobs.RESTLESS_MINER.spawn(arena.level(), spot(at));
			miner.setNoAi(true);
			Cow cow = MinerTestSupport.cowAt(arena.level(), spot(at).add(1.0, 0.0, 0.0), 1_000_000.0F);
			CampMobs.RESTLESS_MINER.ability().onHurtTarget(miner, cow);
			MobEffectInstance fatigue = cow.getEffect(MobEffects.MINING_FATIGUE);
			MinerTestSupport.result("minerFatigue", "fatigue=" + (fatigue == null ? "none" : fatigue.getDuration() + "/" + fatigue.getAmplifier()) + " ticks=" + CampMobs.FATIGUE_TICKS);
			helper.assertTrue(fatigue != null && fatigue.getAmplifier() == 0, "the pick left " + fatigue);
			helper.assertTrue(fatigue.getDuration() > 0 && fatigue.getDuration() <= CampMobs.FATIGUE_TICKS, "the fatigue lasts " + fatigue.getDuration());
			helper.assertTrue(CampMobs.FATIGUE_TICKS == 100, "the fatigue constant moved to " + CampMobs.FATIGUE_TICKS);
			cow.removeEffect(MobEffects.MINING_FATIGUE);
			miner.discard();
			cow.discard();
		});
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261005L);
		int minerRolls = 0;
		int ratRolls = 0;
		boolean one = false;
		boolean two = false;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, CampMobs.MINER_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(CampItems.GLEAMING_ORE_CHUNK) && stack.getCount() >= 1 && stack.getCount() <= 2, "the miner dropped " + stack);
				minerRolls++;
				one |= stack.getCount() == 1;
				two |= stack.getCount() == 2;
			}
			for (ItemStack stack : roll(level, CampMobs.RAT_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(CampItems.RAT_TAIL) && stack.getCount() == 1, "the rat dropped " + stack);
				ratRolls++;
			}
		}
		double minerRate = minerRolls / (double) ROLLS;
		double ratRate = ratRolls / (double) ROLLS;
		MinerTestSupport.result("mobDrops", "rolls=" + ROLLS + " oreRate=" + minerRate + " tailRate=" + ratRate);
		helper.assertTrue(Math.abs(minerRate - 0.60) <= 0.60 * 0.05, "the ore chunk rate is " + minerRate);
		helper.assertTrue(Math.abs(ratRate - 0.30) <= 0.30 * 0.10, "the rat tail rate is " + ratRate);
		helper.assertTrue(one && two, "the ore chunk counts did not cover 1 and 2");
		helper.assertTrue(Exclusives.isExclusive(new ItemStack(CampItems.GLEAMING_ORE_CHUNK)) && Exclusives.isExclusive(new ItemStack(CampItems.RAT_TAIL)),
			"a camp material is not exclusive");
		helper.succeed();
	}

	@GameTest
	public void theCacheVaultHoldsTheOreIronGoldCoalAndDiamonds(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		int diamonds = 0;
		for (int i = 0; i < 400; i++) {
			int ore = 0;
			int iron = 0;
			int gold = 0;
			int coal = 0;
			for (ItemStack stack : roll(level, CampMobs.VAULT_LOOT, master.nextLong(), false)) {
				if (stack.is(CampItems.GLEAMING_ORE_CHUNK)) {
					ore += stack.getCount();
				} else if (stack.is(Items.IRON_INGOT)) {
					iron += stack.getCount();
				} else if (stack.is(Items.GOLD_INGOT)) {
					gold += stack.getCount();
				} else if (stack.is(Items.COAL)) {
					coal += stack.getCount();
				} else if (stack.is(Items.DIAMOND)) {
					diamonds += stack.getCount();
				} else if (!stack.is(CampItems.MINERS_JOURNAL_PAGE)) {
					helper.fail("the cache gave " + stack);
				}
			}
			helper.assertTrue(ore >= 6 && ore <= 12, "roll " + i + " gave " + ore + " ore chunks");
			helper.assertTrue(iron >= 3 && gold >= 1 && coal >= 4, "roll " + i + " missed the iron, the gold or the coal");
		}
		MinerTestSupport.result("cacheTable", "rolls=400 diamonds=" + diamonds + " oreRange=6-12");
		helper.assertTrue(diamonds > 0 && diamonds < 400 * 2, "the diamond pool gave " + diamonds);
		helper.succeed();
	}
}
