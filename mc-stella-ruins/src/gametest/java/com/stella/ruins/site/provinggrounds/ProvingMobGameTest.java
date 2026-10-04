package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.Exclusives;
import com.stella.ruins.world.RuinConfig;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Acceptance item 10 and the four kinds: their bases and looks, the net, the angry hound, and what each kind drops at
 * its table rate. A control in each ability test proves the check can tell a hit from a miss.
 */
public class ProvingMobGameTest {
	static final long ROLL_SEED = 20261005L;
	static final int KILLS = 2000;
	/** A light hit. With the mobs mod a ruin mob scales damage with its level, and a heavy base would kill the subject, and a death fires no after-damage event. */
	static final float HIT = 0.01F;

	private static Mob spawn(final GameTestHelper helper, final RuinMobKind kind, final double x, final double z) {
		Mob mob = kind.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)));
		mob.setNoAi(true);
		mob.tickCount = 0;
		return mob;
	}

	@GameTest(maxTicks = 100)
	public void theFourKindsAreVanillaTypesWithTheNamedBasesAndLooks(final GameTestHelper helper) {
		Mob gladiator = spawn(helper, GroundsMobs.gladiator(), 0.5, 0.5);
		Mob retiarius = spawn(helper, GroundsMobs.retiarius(), 3.5, 0.5);
		Mob hound = spawn(helper, GroundsMobs.hound(), 6.5, 0.5);
		Mob beast = spawn(helper, GroundsMobs.beast(), 9.5, 0.5);
		helper.assertTrue(gladiator.getType() == EntityTypes.VINDICATOR && Math.abs(gladiator.getAttributeBaseValue(Attributes.SCALE) - GroundsMobs.GLADIATOR_SCALE) < 1.0E-6, "the gladiator is a " + gladiator.getType() + " at scale " + gladiator.getAttributeBaseValue(Attributes.SCALE));
		helper.assertTrue(gladiator.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the gladiator wears " + gladiator.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(retiarius.getType() == EntityTypes.DROWNED && retiarius.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TRIDENT) && retiarius.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET),
			"the retiarius is a " + retiarius.getType() + " with " + retiarius.getItemBySlot(EquipmentSlot.MAINHAND) + " and " + retiarius.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(hound.getType() == EntityTypes.WOLF && beast.getType() == EntityTypes.RAVAGER, "the hound is a " + hound.getType() + " and the beast a " + beast.getType());
		for (Mob mob : new Mob[] {gladiator, retiarius}) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.MAINHAND}) {
				helper.assertTrue(mob.getDropChances().byEquipment(slot) == 0.0F, mob.getType() + " drops its " + slot);
			}
		}
		for (Mob mob : new Mob[] {gladiator, retiarius, hound, beast}) {
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theHoundHasTwiceTheWolfHealthAndTheBeastThreeTimesTheRavagers(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
		Mob ravager = EntityTypes.RAVAGER.create(level, EntitySpawnReason.COMMAND);
		Mob hound = spawn(helper, GroundsMobs.hound(), 0.5, 0.5);
		Mob beast = spawn(helper, GroundsMobs.beast(), 4.5, 0.5);
		var houndMod = hound.getAttribute(Attributes.MAX_HEALTH).getModifier(com.stella.ruins.StellaRuins.id("kind_health"));
		var beastMod = beast.getAttribute(Attributes.MAX_HEALTH).getModifier(com.stella.ruins.StellaRuins.id("kind_health"));
		ProvingTestSupport.result("health", "wolf=" + wolf.getMaxHealth() + " hound=" + hound.getMaxHealth() + " ravager=" + ravager.getMaxHealth() + " beast=" + beast.getMaxHealth() + " houndMod=" + houndMod + " beastMod=" + beastMod);
		// With the mobs mod a ruin mob also scales with its level, so the kind's own multiplier is read from its modifier.
		helper.assertTrue(houndMod != null && Math.abs(houndMod.amount() + 1.0 - GroundsMobs.HOUND_HEALTH) < 1.0E-9, "the hound's health modifier is " + houndMod);
		helper.assertTrue(beastMod != null && Math.abs(beastMod.amount() + 1.0 - GroundsMobs.BEAST_HEALTH) < 1.0E-9, "the beast's health modifier is " + beastMod);
		hound.discard();
		beast.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aRetiariusHitSlowsThePlayerWithSlownessTwoForFortyTicksAndAPlainDrownedDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob retiarius = spawn(helper, GroundsMobs.retiarius(), 0.5, 0.5);
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 2.0, 0.5));
		ProvingTestSupport.vulnerable(player);
		// With the mobs mod a ruin mob hits at its level, and AFTER_DAMAGE never fires for a death, so the subject must survive.
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000.0);
		player.setHealth(1_000_000.0F);
		ServerPlayer control = ProvingTestSupport.join(helper, new Vec3(1.5, 2.0, 4.5));
		ProvingTestSupport.vulnerable(control);
		Mob plain = EntityTypes.DROWNED.create(level, EntitySpawnReason.COMMAND);
		plain.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		plain.snapTo(helper.absoluteVec(new Vec3(0.5, 2.0, 4.5)));
		plain.setNoAi(true);
		level.addFreshEntity(plain);
		float before = player.getHealth();
		boolean hit = player.hurtServer(level, level.damageSources().mobAttack(retiarius), HIT);
		control.hurtServer(level, level.damageSources().mobAttack(plain), HIT);
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
		ProvingTestSupport.result("netSlow", "hurt=" + hit + " lost=" + (before - player.getHealth()) + " slowTicks=" + (slow == null ? -1 : slow.getDuration()) + " amplifier=" + (slow == null ? -1 : slow.getAmplifier()) + " control=" + control.hasEffect(MobEffects.SLOWNESS) + " effects=" + player.getActiveEffects() + " retiariusAlive=" + retiarius.isAlive() + " tags=" + retiarius.entityTags());
		helper.assertTrue(hit && player.getHealth() < before, "the retiarius' hit did not hurt the player, so the check proves nothing");
		helper.assertTrue(slow != null && slow.getDuration() == GroundsMobs.NET_TICKS && slow.getAmplifier() == 1, "the player's Slowness is " + slow);
		helper.assertTrue(control.getHealth() < control.getMaxHealth() && !control.hasEffect(MobEffects.SLOWNESS), "a plain drowned slowed the player");
		retiarius.discard();
		plain.discard();
		ProvingTestSupport.leave(helper, player);
		ProvingTestSupport.leave(helper, control);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aRetiariusHitOnAMobDoesNotSlowIt(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob retiarius = spawn(helper, GroundsMobs.retiarius(), 0.5, 0.5);
		Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		pig.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		pig.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		level.addFreshEntity(pig);
		pig.hurtServer(level, level.damageSources().mobAttack(retiarius), HIT);
		helper.assertTrue(pig.getHealth() < 1000.0F, "the hit did not land, so the check proves nothing");
		helper.assertTrue(!pig.hasEffect(MobEffects.SLOWNESS), "the net slowed a pig");
		retiarius.discard();
		pig.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anArenaHoundHuntsTheNearestPlayerInSightAndIgnoresFarOnesAndSpectators(final GameTestHelper helper) {
		Mob hound = spawn(helper, GroundsMobs.hound(), 0.5, 0.5);
		ServerPlayer far = ProvingTestSupport.join(helper, new Vec3(0.5, 2.0, 60.5));
		ServerPlayer watcher = ProvingTestSupport.join(helper, new Vec3(1.5, 2.0, 0.5));
		watcher.setGameMode(GameType.SPECTATOR);
		RuinMobs.kindOf(hound).ability().tick(hound, helper.getLevel());
		// Other tests run beside this one in the same level, so a player of theirs may stand within sight: only these two count.
		helper.assertTrue(((Wolf) hound).getTarget() != far && ((Wolf) hound).getTarget() != watcher, "the hound hunted " + ((Wolf) hound).getTarget() + ", a far player or a spectator");
		((Wolf) hound).setTarget(null);
		ServerPlayer near = ProvingTestSupport.join(helper, new Vec3(8.5, 2.0, 0.5));
		ServerPlayer nearer = ProvingTestSupport.join(helper, new Vec3(3.5, 2.0, 0.5));
		RuinMobs.kindOf(hound).ability().tick(hound, helper.getLevel());
		ProvingTestSupport.result("hound", "target=" + ((Wolf) hound).getTarget());
		helper.assertTrue(((Wolf) hound).getTarget() == nearer, "the hound's target is " + ((Wolf) hound).getTarget() + ", not the nearest player");
		hound.discard();
		for (ServerPlayer player : new ServerPlayer[] {far, watcher, near, nearer}) {
			ProvingTestSupport.leave(helper, player);
		}
		helper.succeed();
	}

	/** The plan allows 15 percent. Four standard deviations is tighter at 2000 kills, and it still never fails a run at the true rate. */
	static double tolerance(final int kills, final double chance) {
		return Math.min(0.15 * kills * chance, 4.0 * Math.sqrt(kills * chance * (1.0 - chance)));
	}

	private static List<ItemStack> kill(final ServerLevel level, final ResourceKey<LootTable> key, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Pig victim = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.THIS_ENTITY, victim)
			.withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(LootContextParamSets.ENTITY);
		return table.getRandomItems(params, seed);
	}

	/** Rolls a kind's table once for each kill and returns how many kills dropped the item, and the counts seen. */
	private static int[] drops(final ServerLevel level, final ResourceKey<LootTable> key, final Item item, final Set<Integer> counts) {
		int dropping = 0;
		int items = 0;
		RandomSource seeds = RandomSource.create(ROLL_SEED);
		for (int i = 0; i < KILLS; i++) {
			int count = kill(level, key, seeds.nextLong()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
			dropping += count > 0 ? 1 : 0;
			items += count;
			if (count > 0) {
				counts.add(count);
			}
		}
		return new int[] {dropping, items};
	}

	private static void rate(final GameTestHelper helper, final String name, final ResourceKey<LootTable> table, final Item item, final double chance) {
		Set<Integer> counts = new HashSet<>();
		int[] result = drops(helper.getLevel(), table, item, counts);
		double expected = KILLS * chance;
		double tolerance = tolerance(KILLS, chance);
		ProvingTestSupport.result(name, "kills=" + KILLS + " dropping=" + result[0] + " expected=" + expected + " tolerance=" + tolerance + " counts=" + counts);
		helper.assertTrue(Math.abs(result[0] - expected) <= tolerance, result[0] + " of " + KILLS + " kills dropped the item, outside " + expected + " +- " + tolerance);
		helper.assertTrue(Math.abs(KILLS * (chance - 0.05) - expected) > tolerance && Math.abs(KILLS * (chance + 0.05) - expected) > tolerance, "the band cannot tell " + (chance - 0.05) + " or " + (chance + 0.05) + " from " + chance);
		helper.assertTrue(counts.equals(Set.of(1)), "the counts were " + counts + ", not 1");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void twoThousandGladiatorKillsDropOneTokenAtFifteenPercent(final GameTestHelper helper) {
		rate(helper, "gladiatorDrops", GroundsMobs.GLADIATOR_DROPS, GroundsItems.CHAMPION_TOKEN, 0.15);
	}

	@GameTest(maxTicks = 400)
	public void twoThousandRetiariusKillsDropOneNetFiberAtFortyPercent(final GameTestHelper helper) {
		rate(helper, "retiariusDrops", GroundsMobs.RETIARIUS_DROPS, GroundsItems.NET_FIBER, 0.40);
	}

	@GameTest(maxTicks = 400)
	public void twoThousandHoundKillsDropOneFangAtFortyPercent(final GameTestHelper helper) {
		rate(helper, "houndDrops", GroundsMobs.HOUND_DROPS, GroundsItems.HOUND_FANG, 0.40);
	}

	@GameTest(maxTicks = 400)
	public void twoThousandBeastKillsAlwaysDropThreeTokens(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		int[] result = drops(helper.getLevel(), GroundsMobs.BEAST_DROPS, GroundsItems.CHAMPION_TOKEN, counts);
		ProvingTestSupport.result("beastDrops", "kills=" + KILLS + " dropping=" + result[0] + " counts=" + counts);
		helper.assertTrue(result[0] == KILLS && counts.equals(Set.of(3)), result[0] + " of " + KILLS + " kills dropped tokens, in counts " + counts);
		helper.succeed();
	}

	/** Rolls a vault table and returns how many of each item it gave. */
	private static int count(final List<ItemStack> stacks, final Item item) {
		return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	@GameTest(maxTicks = 400)
	public void theVaultTablesMatchThePlanInTokensAndDiamonds(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int[][] bands = {{2, 3}, {4, 6}, {8, 12}};
		for (int tier = 1; tier <= 3; tier++) {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(ProvingGroundsSite.vaultTable(tier));
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
			int min = 999;
			int max = 0;
			int diamondsMin = 999;
			int diamondsMax = 0;
			RandomSource seeds = RandomSource.create(ROLL_SEED);
			for (int i = 0; i < KILLS; i++) {
				List<ItemStack> rolled = table.getRandomItems(params, seeds.nextLong());
				int tokens = count(rolled, GroundsItems.CHAMPION_TOKEN);
				int diamonds = count(rolled, Items.DIAMOND);
				min = Math.min(min, tokens);
				max = Math.max(max, tokens);
				diamondsMin = Math.min(diamondsMin, diamonds);
				diamondsMax = Math.max(diamondsMax, diamonds);
			}
			ProvingTestSupport.result("vaultTable" + tier, "tokens=" + min + ".." + max + " diamonds=" + diamondsMin + ".." + diamondsMax);
			helper.assertTrue(min == bands[tier - 1][0] && max == bands[tier - 1][1], "tier " + tier + " tokens ran " + min + ".." + max + ", not " + bands[tier - 1][0] + ".." + bands[tier - 1][1]);
			helper.assertTrue(tier == 3 ? diamondsMin >= 1 && diamondsMax <= 3 : diamondsMax == 0, "tier " + tier + " diamonds ran " + diamondsMin + ".." + diamondsMax);
		}
		helper.succeed();
	}

	@GameTest
	public void everyExclusiveItemOfTheSiteComesFromItsOwnTables(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (ResourceKey<LootTable> key : List.of(GroundsMobs.GLADIATOR_DROPS, GroundsMobs.RETIARIUS_DROPS, GroundsMobs.HOUND_DROPS, GroundsMobs.BEAST_DROPS,
			ProvingGroundsSite.vaultTable(1), ProvingGroundsSite.vaultTable(2), ProvingGroundsSite.vaultTable(3))) {
			helper.assertTrue(Exclusives.ownsTable(key.identifier()), key.identifier() + " is outside the mod's namespace");
			helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, key.identifier() + " did not load");
		}
		for (Item item : List.of(GroundsItems.CHAMPION_TOKEN, GroundsItems.NET_FIBER, GroundsItems.HOUND_FANG, GroundsRewards.gladius())) {
			helper.assertTrue(Exclusives.isExclusive(new ItemStack(item)), item + " is not exclusive");
		}
		helper.succeed();
	}

	private static int tokensAround(final ServerLevel level, final Mob mob) {
		int total = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(4.0))) {
			total += entity.getItem().is(GroundsItems.CHAMPION_TOKEN) ? entity.getItem().getCount() : 0;
		}
		return total;
	}

	@GameTest(maxTicks = 100)
	public void aBeastKilledByAPlayerDropsThreeTokensAndOneKilledByTheWorldDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(2.5, 2.0, 0.5));
		Mob byPlayer = spawn(helper, GroundsMobs.beast(), 0.5, 0.5);
		byPlayer.hurtServer(level, level.damageSources().playerAttack(player), 100_000.0F);
		int dropped = tokensAround(level, byPlayer);
		Mob byWorld = spawn(helper, GroundsMobs.beast(), 14.5, 14.5);
		byWorld.hurtServer(level, level.damageSources().generic(), 100_000.0F);
		int none = tokensAround(level, byWorld);
		ProvingTestSupport.result("beastKill", "byPlayer=" + dropped + " byWorld=" + none);
		helper.assertTrue(dropped == 3, "a player's kill dropped " + dropped + " tokens");
		helper.assertTrue(none == 0, "a death by the world dropped " + none + " tokens");
		ProvingTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 6000)
	public void aGladiatorOfTheRunDropsTheGladiusWhenTheUniqueChanceIsOne(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withUniqueDropChance(1.0));
			try {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				Mob victim = arena.roster().get(0);
				arena.freeze();
				victim.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 100_000.0F);
				int gladii = 0;
				for (ItemEntity entity : arena.level().getEntitiesOfClass(ItemEntity.class, victim.getBoundingBox().inflate(4.0))) {
					gladii += entity.getItem().is(GroundsRewards.gladius()) ? entity.getItem().getCount() : 0;
				}
				ProvingTestSupport.result("uniqueDrop", "gladii=" + gladii);
				helper.assertTrue(gladii == 1, "the kill dropped " + gladii + " gladii");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}
}
