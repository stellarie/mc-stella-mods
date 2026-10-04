package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.RuinMobKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Acceptance items 7 and 8, and the Hollow Pilgrim hood. Each ability is called through its kind at a known mob age,
 * so a test sees one trigger. A control in each test proves the check can tell a hit from a miss.
 */
public class WayshrineMobGameTest {
	static final long ROLL_SEED = 20261005L;
	static final int KILLS = 2000;

	private static Mob spawn(final GameTestHelper helper, final RuinMobKind kind, final double x, final double z) {
		Mob mob = kind.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)));
		mob.setNoAi(true);
		mob.tickCount = 0;
		return mob;
	}

	private static Mob hurt(final Mob mob, final float fraction) {
		mob.setHealth(mob.getMaxHealth() * fraction);
		return mob;
	}

	private static void tick(final GameTestHelper helper, final Mob mob) {
		RuinMobKind kind = com.stella.ruins.world.RuinMobs.kindOf(mob);
		kind.ability().tick(mob, helper.getLevel());
	}

	@GameTest(maxTicks = 100)
	public void aPilgrimHealsTheMostHurtRuinAllyWithinSixBlocksByFour(final GameTestHelper helper) {
		Mob healer = spawn(helper, WayshrineMobs.pilgrim(), 0.5, 0.5);
		Mob worse = hurt(spawn(helper, WayshrineMobs.pilgrim(), 5.5, 0.5), 0.4F);
		Mob lighter = hurt(spawn(helper, WayshrineMobs.pilgrim(), 0.5, 5.5), 0.8F);
		float worseBefore = worse.getHealth();
		float lighterBefore = lighter.getHealth();
		tick(helper, healer);
		WayshrineTestSupport.result("pilgrimHeal", "healed=" + (worse.getHealth() - worseBefore) + " other=" + (lighter.getHealth() - lighterBefore) + " amount=" + WayshrineMobs.HEAL_AMOUNT);
		helper.assertTrue(worse.getHealth() - worseBefore == WayshrineMobs.HEAL_AMOUNT, "the most hurt ally healed " + (worse.getHealth() - worseBefore) + ", not " + WayshrineMobs.HEAL_AMOUNT);
		helper.assertTrue(lighter.getHealth() == lighterBefore, "a second ally was healed too");
		for (Mob mob : new Mob[] {healer, worse, lighter}) {
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPilgrimDoesNotHealAnAllyBeyondSixBlocks(final GameTestHelper helper) {
		Mob healer = spawn(helper, WayshrineMobs.pilgrim(), 0.5, 0.5);
		Mob near = hurt(spawn(helper, WayshrineMobs.pilgrim(), 5.5, 0.5), 0.5F);
		// The far ally is hurt worse, so a range that reached it would heal it first.
		Mob far = hurt(spawn(helper, WayshrineMobs.pilgrim(), 7.5, 0.5), 0.2F);
		float nearBefore = near.getHealth();
		float farBefore = far.getHealth();
		tick(helper, healer);
		helper.assertTrue(near.getHealth() > nearBefore, "the ally at 5 blocks was not healed, so the check proves nothing");
		helper.assertTrue(far.getHealth() == farBefore, "the ally at 7 blocks was healed");
		for (Mob mob : new Mob[] {healer, near, far}) {
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPilgrimDoesNotHealAPlayerAMobOfTheWorldOrItself(final GameTestHelper helper) {
		Mob healer = hurt(spawn(helper, WayshrineMobs.pilgrim(), 0.5, 0.5), 0.5F);
		ServerPlayer player = WayshrineTestSupport.join(helper, new Vec3(2.5, 2.0, 0.5));
		player.setHealth(10.0F);
		Mob wild = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		wild.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		wild.snapTo(helper.absoluteVec(new Vec3(0.5, 2.0, 3.5)));
		wild.setNoAi(true);
		helper.getLevel().addFreshEntity(wild);
		// The world's zombie is hurt worse than the ally, so a healer that picked it would pick it first.
		wild.setHealth(wild.getMaxHealth() * 0.2F);
		Mob ally = hurt(spawn(helper, WayshrineMobs.pilgrim(), -3.5, 0.5), 0.5F);
		float healerBefore = healer.getHealth();
		float wildBefore = wild.getHealth();
		float allyBefore = ally.getHealth();
		tick(helper, healer);
		WayshrineTestSupport.result("pilgrimTargets", "ally=" + (ally.getHealth() - allyBefore) + " player=" + player.getHealth() + " wild=" + (wild.getHealth() - wildBefore) + " self=" + (healer.getHealth() - healerBefore));
		helper.assertTrue(ally.getHealth() > allyBefore, "the ruin ally was not healed, so the check proves nothing");
		helper.assertTrue(player.getHealth() == 10.0F, "a player was healed to " + player.getHealth());
		helper.assertTrue(wild.getHealth() == wildBefore, "a zombie of the world was healed");
		helper.assertTrue(healer.getHealth() == healerBefore, "the pilgrim healed itself");
		for (Mob mob : new Mob[] {healer, wild, ally}) {
			mob.discard();
		}
		WayshrineTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPilgrimHealsOnceInEachPeriodOfItsOwnAge(final GameTestHelper helper) {
		Mob healer = spawn(helper, WayshrineMobs.pilgrim(), 0.5, 0.5);
		Mob ally = hurt(spawn(helper, WayshrineMobs.pilgrim(), 3.5, 0.5), 0.3F);
		float before = ally.getHealth();
		healer.tickCount = WayshrineMobs.HEAL_PERIOD / 2;
		tick(helper, healer);
		helper.assertTrue(ally.getHealth() == before, "the pilgrim healed in the middle of its period");
		healer.tickCount = WayshrineMobs.HEAL_PERIOD * 3;
		tick(helper, healer);
		helper.assertTrue(ally.getHealth() == before + WayshrineMobs.HEAL_AMOUNT, "the pilgrim did not heal at the start of a period");
		healer.discard();
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aWispHitBlindsThePlayerForFortyTicksAndAPlainVexDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob wisp = spawn(helper, WayshrineMobs.wisp(), 0.5, 0.5);
		ServerPlayer player = WayshrineTestSupport.join(helper, new Vec3(1.5, 2.0, 0.5));
		WayshrineTestSupport.vulnerable(player);
		// With the mobs mod a ruin mob hits at its level, and AFTER_DAMAGE never fires for a death, so the subject must survive.
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000.0);
		player.setHealth(1_000_000.0F);
		ServerPlayer control = WayshrineTestSupport.join(helper, new Vec3(1.5, 2.0, 4.5));
		WayshrineTestSupport.vulnerable(control);
		Mob plain = EntityTypes.VEX.create(level, EntitySpawnReason.COMMAND);
		plain.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		plain.snapTo(helper.absoluteVec(new Vec3(0.5, 2.0, 4.5)));
		plain.setNoAi(true);
		level.addFreshEntity(plain);
		float before = player.getHealth();
		boolean hit = player.hurtServer(level, level.damageSources().mobAttack(wisp), 1.0F);
		control.hurtServer(level, level.damageSources().mobAttack(plain), 1.0F);
		MobEffectInstance blind = player.getEffect(MobEffects.BLINDNESS);
		WayshrineTestSupport.result("wispBlind", "hurt=" + hit + " healthLost=" + (before - player.getHealth()) + " blindTicks=" + (blind == null ? -1 : blind.getDuration()) + " controlBlind=" + control.hasEffect(MobEffects.BLINDNESS));
		helper.assertTrue(hit && player.getHealth() < before, "the wisp's hit did not hurt the player, so the check proves nothing");
		helper.assertTrue(blind != null && blind.getDuration() == WayshrineMobs.BLIND_TICKS && blind.getAmplifier() == 0, "the player's Blindness is " + blind);
		helper.assertTrue(control.getHealth() < control.getMaxHealth() && !control.hasEffect(MobEffects.BLINDNESS), "a plain vex's hit blinded the player");
		wisp.discard();
		plain.discard();
		WayshrineTestSupport.leave(helper, player);
		WayshrineTestSupport.leave(helper, control);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aWispHitOnAMobDoesNotBlindIt(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob wisp = spawn(helper, WayshrineMobs.wisp(), 0.5, 0.5);
		Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		pig.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		pig.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		level.addFreshEntity(pig);
		pig.hurtServer(level, level.damageSources().mobAttack(wisp), 1.0F);
		helper.assertTrue(pig.getHealth() < 1000.0F, "the hit did not land, so the check proves nothing");
		helper.assertTrue(!pig.hasEffect(MobEffects.BLINDNESS), "the wisp blinded a pig");
		wisp.discard();
		pig.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theAcolyteSlowsPlayersWithinEightBlocksAndNoOneFarther(final GameTestHelper helper) {
		Mob acolyte = spawn(helper, WayshrineMobs.acolyte(), 0.5, 0.5);
		ServerPlayer near = WayshrineTestSupport.join(helper, new Vec3(7.5, 2.0, 0.5));
		ServerPlayer far = WayshrineTestSupport.join(helper, new Vec3(-9.5, 2.0, 0.5));
		ServerPlayer watcher = WayshrineTestSupport.join(helper, new Vec3(0.5, 2.0, 3.5));
		watcher.setGameMode(GameType.SPECTATOR);
		tick(helper, acolyte);
		MobEffectInstance slow = near.getEffect(MobEffects.SLOWNESS);
		WayshrineTestSupport.result("acolyteSlow", "near=" + (slow == null ? -1 : slow.getDuration()) + " far=" + far.hasEffect(MobEffects.SLOWNESS) + " spectator=" + watcher.hasEffect(MobEffects.SLOWNESS));
		helper.assertTrue(slow != null && slow.getAmplifier() == 0 && slow.getDuration() == WayshrineMobs.SLOW_TICKS, "the near player's Slowness is " + slow);
		helper.assertTrue(!far.hasEffect(MobEffects.SLOWNESS), "a player 10 blocks away was slowed");
		helper.assertTrue(!watcher.hasEffect(MobEffects.SLOWNESS), "a spectator was slowed");
		acolyte.discard();
		for (ServerPlayer player : new ServerPlayer[] {near, far, watcher}) {
			WayshrineTestSupport.leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theAcolyteSlowsOnceInEachPeriodOfItsOwnAge(final GameTestHelper helper) {
		Mob acolyte = spawn(helper, WayshrineMobs.acolyte(), 0.5, 0.5);
		ServerPlayer near = WayshrineTestSupport.join(helper, new Vec3(3.5, 2.0, 0.5));
		acolyte.tickCount = WayshrineMobs.SLOW_PERIOD / 2;
		tick(helper, acolyte);
		helper.assertTrue(!near.hasEffect(MobEffects.SLOWNESS), "the acolyte slowed in the middle of its period");
		acolyte.tickCount = WayshrineMobs.SLOW_PERIOD * 2;
		tick(helper, acolyte);
		helper.assertTrue(near.hasEffect(MobEffects.SLOWNESS), "the acolyte did not slow at the start of a period");
		acolyte.discard();
		WayshrineTestSupport.leave(helper, near);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPilgrimWearsAWhiteUnbreakableLeatherHoodAndRobe(final GameTestHelper helper) {
		Mob pilgrim = spawn(helper, WayshrineMobs.pilgrim(), 0.5, 0.5);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
			ItemStack worn = pilgrim.getItemBySlot(slot);
			helper.assertTrue(!worn.isEmpty() && worn.has(DataComponents.DYED_COLOR) && (worn.get(DataComponents.DYED_COLOR).rgb() & 0xFFFFFF) == 0xFFFFFF, slot + " holds " + worn + ", not a white dyed piece");
			helper.assertTrue(pilgrim.getDropChances().byEquipment(slot) == 0.0F, slot + " can drop");
		}
		ItemStack hood = pilgrim.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack plain = new ItemStack(Items.LEATHER_HELMET);
		WayshrineTestSupport.result("pilgrimHood", "hood=" + hood.getItem() + " damageable=" + hood.isDamageableItem() + " controlDamageable=" + plain.isDamageableItem());
		helper.assertTrue(hood.is(Items.LEATHER_HELMET) && hood.has(DataComponents.UNBREAKABLE) && !hood.isDamageableItem(), "the hood wears out: " + hood);
		helper.assertTrue(plain.isDamageableItem(), "a plain leather helmet does not wear out, so the check proves nothing");
		pilgrim.discard();
		helper.succeed();
	}

	/** Zombies burn in the sun unless they wear a helmet that cannot break. The control wears nothing and must burn. */
	@GameTest(maxTicks = 700)
	public void theHoodKeepsAPilgrimFromBurningAtNoonWhileAZombieInAFailingHoodBurns(final GameTestHelper helper) {
		Mob[] subjects = new Mob[2];
		long[] noonBefore = {0L};
		WayshrineTestSupport.inArenaAfter(helper, net.minecraft.world.level.block.Rotation.NONE, WayshrineTestSupport.seedOf(WayshrinePlan.RING), 140, arena -> {
			var clock = arena.level().dimensionType().defaultClock().orElseThrow();
			noonBefore[0] = arena.level().clockManager().getInstance(clock).totalTicks();
			helper.setTime(6000L);
			subjects[0] = WayshrineMobs.pilgrim().spawn(arena.level(), Vec3.atBottomCenterOf(arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM)).get(0)));
			// Both hoods start one point from breaking: the pilgrim's cannot break, the control's does and then the sun burns it.
			ItemStack worn = subjects[0].getItemBySlot(EquipmentSlot.HEAD);
			worn.setDamageValue(worn.getMaxDamage() - 1);
			subjects[1] = EntityTypes.ZOMBIE.create(arena.level(), EntitySpawnReason.COMMAND);
			subjects[1].addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
			ItemStack weak = new ItemStack(Items.LEATHER_HELMET);
			weak.setDamageValue(weak.getMaxDamage() - 1);
			subjects[1].setItemSlot(EquipmentSlot.HEAD, weak);
			subjects[1].snapTo(Vec3.atBottomCenterOf(arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM)).get(1)));
			arena.level().addFreshEntity(subjects[1]);
		}, arena -> {
			boolean day = arena.level().isBrightOutside();
			boolean pilgrimBurned = subjects[0].isOnFire() || subjects[0].getHealth() < subjects[0].getMaxHealth();
			boolean bareBurned = subjects[1].isOnFire() || subjects[1].getHealth() < subjects[1].getMaxHealth() || !subjects[1].isAlive();
			WayshrineTestSupport.result("sunBurn", "day=" + day + " pilgrimBurned=" + pilgrimBurned + " failingHoodBurned=" + bareBurned);
			subjects[0].discard();
			subjects[1].discard();
			arena.level().clockManager().setTotalTicks(arena.level().dimensionType().defaultClock().orElseThrow(), noonBefore[0]);
			helper.assertTrue(day, "it was not day at the end of the test, so the sun check proves nothing");
			helper.assertTrue(bareBurned, "the zombie in a failing hood did not burn, so the sun check proves nothing");
			helper.assertTrue(!pilgrimBurned, "the pilgrim burned in the sun despite its hood");
		});
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
		// Sequential seeds make the first random values of the legacy generator climb in step, so one stream hands out the seeds.
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

	@GameTest(maxTicks = 400)
	public void twoThousandPilgrimKillsDropAshAtSixtyPercentInOneOrTwo(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		int[] result = drops(helper.getLevel(), WayshrineMobs.PILGRIM_DROPS, WayshrineItems.PILGRIM_ASH, counts);
		double expected = KILLS * 0.6;
		double tolerance = tolerance(KILLS, 0.6);
		WayshrineTestSupport.result("pilgrimDrops", "kills=" + KILLS + " dropping=" + result[0] + " items=" + result[1] + " expected=" + expected + " tolerance=" + tolerance + " counts=" + counts);
		helper.assertTrue(Math.abs(result[0] - expected) <= tolerance, result[0] + " of " + KILLS + " kills dropped ash, outside " + expected + " +- " + tolerance);
		helper.assertTrue(Math.abs(KILLS * 0.5 - expected) > tolerance && Math.abs(KILLS * 0.55 - expected) > tolerance && Math.abs(KILLS * 0.65 - expected) > tolerance, "the band cannot tell 50, 55 or 65 percent from 60");
		helper.assertTrue(counts.equals(Set.of(1, 2)), "the ash counts were " + counts + ", not 1 and 2");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void twoThousandWispKillsDropOneLightAtFiftyPercent(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		int[] result = drops(helper.getLevel(), WayshrineMobs.WISP_DROPS, WayshrineItems.WISP_LIGHT, counts);
		double expected = KILLS * 0.5;
		double tolerance = tolerance(KILLS, 0.5);
		WayshrineTestSupport.result("wispDrops", "kills=" + KILLS + " dropping=" + result[0] + " expected=" + expected + " tolerance=" + tolerance + " counts=" + counts);
		helper.assertTrue(Math.abs(result[0] - expected) <= tolerance, result[0] + " of " + KILLS + " kills dropped light, outside " + expected + " +- " + tolerance);
		helper.assertTrue(Math.abs(KILLS * 0.45 - expected) > tolerance && Math.abs(KILLS * 0.55 - expected) > tolerance, "the band cannot tell 45 or 55 percent from 50");
		helper.assertTrue(counts.equals(Set.of(1)), "the light counts were " + counts + ", not 1");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void twoThousandAcolyteKillsAlwaysDropTwoOrThreeShards(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		int[] result = drops(helper.getLevel(), WayshrineMobs.ACOLYTE_DROPS, WayshrineItems.SHRINE_SHARD, counts);
		double mean = (double) result[1] / KILLS;
		WayshrineTestSupport.result("acolyteDrops", "kills=" + KILLS + " dropping=" + result[0] + " meanShards=" + mean + " counts=" + counts);
		helper.assertTrue(result[0] == KILLS, "only " + result[0] + " of " + KILLS + " kills dropped shards");
		helper.assertTrue(counts.equals(Set.of(2, 3)), "the shard counts were " + counts + ", not 2 and 3");
		helper.assertTrue(Math.abs(mean - 2.5) <= 0.1, "the mean shard count is " + mean);
		helper.succeed();
	}

	private static int shardsAround(final ServerLevel level, final Mob mob) {
		int total = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(4.0))) {
			total += entity.getItem().is(WayshrineItems.SHRINE_SHARD) ? entity.getItem().getCount() : 0;
		}
		return total;
	}

	@GameTest(maxTicks = 100)
	public void anAcolyteKilledByAPlayerDropsShardsAndOneKilledByNothingDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = WayshrineTestSupport.join(helper, new Vec3(2.5, 2.0, 0.5));
		Mob byPlayer = spawn(helper, WayshrineMobs.acolyte(), 0.5, 0.5);
		byPlayer.hurtServer(level, level.damageSources().playerAttack(player), 10_000.0F);
		int dropped = shardsAround(level, byPlayer);
		Mob byWorld = spawn(helper, WayshrineMobs.acolyte(), 12.5, 12.5);
		byWorld.hurtServer(level, level.damageSources().generic(), 10_000.0F);
		int none = shardsAround(level, byWorld);
		WayshrineTestSupport.result("acolyteKill", "byPlayer=" + dropped + " byWorld=" + none);
		helper.assertTrue(dropped >= 2 && dropped <= 3, "a player's kill dropped " + dropped + " shards");
		helper.assertTrue(none == 0, "a death by the world dropped " + none + " shards");
		WayshrineTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void theVaultTableMatchesThePlan(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(WayshrineSite.VAULT_LOOT);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		int[] shards = {99, 0};
		int[] ash = {99, 0};
		int[] light = {99, 0};
		int[] emeralds = {999, 0};
		int bread = 0;
		int carrots = 0;
		int both = 0;
		RandomSource seeds = RandomSource.create(ROLL_SEED);
		for (int i = 0; i < KILLS; i++) {
			int s = 0;
			int a = 0;
			int l = 0;
			int e = 0;
			boolean b = false;
			boolean c = false;
			for (ItemStack stack : table.getRandomItems(params, seeds.nextLong())) {
				if (stack.is(WayshrineItems.SHRINE_SHARD)) {
					s += stack.getCount();
				} else if (stack.is(WayshrineItems.PILGRIM_ASH)) {
					a += stack.getCount();
				} else if (stack.is(WayshrineItems.WISP_LIGHT)) {
					l += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					e += stack.getCount();
				} else if (stack.is(Items.BREAD)) {
					b = true;
				} else if (stack.is(Items.GOLDEN_CARROT)) {
					c = true;
				}
			}
			shards[0] = Math.min(shards[0], s);
			shards[1] = Math.max(shards[1], s);
			ash[0] = Math.min(ash[0], a);
			ash[1] = Math.max(ash[1], a);
			light[0] = Math.min(light[0], l);
			light[1] = Math.max(light[1], l);
			emeralds[0] = Math.min(emeralds[0], e);
			emeralds[1] = Math.max(emeralds[1], e);
			bread += b ? 1 : 0;
			carrots += c ? 1 : 0;
			both += b && c ? 1 : 0;
		}
		WayshrineTestSupport.result("vaultTable", "rolls=" + KILLS + " shards=" + shards[0] + ".." + shards[1] + " ash=" + ash[0] + ".." + ash[1] + " light=" + light[0] + ".." + light[1] + " emeralds=" + emeralds[0] + ".." + emeralds[1]
			+ " bread=" + bread + " carrots=" + carrots + " both=" + both);
		helper.assertTrue(shards[0] == 2 && shards[1] == 4, "shards ran " + shards[0] + ".." + shards[1] + ", not 2..4");
		helper.assertTrue(ash[0] == 0 && ash[1] == 2, "ash ran " + ash[0] + ".." + ash[1] + ", not 0..2");
		helper.assertTrue(light[0] == 0 && light[1] == 1, "light ran " + light[0] + ".." + light[1] + ", not 0..1");
		helper.assertTrue(emeralds[0] == 4 && emeralds[1] == 12, "emeralds ran " + emeralds[0] + ".." + emeralds[1] + ", not 4..12");
		helper.assertTrue(bread > 0 && carrots > 0 && both == 0 && bread + carrots == KILLS, "the food was bread " + bread + ", carrots " + carrots + ", both " + both);
		helper.succeed();
	}

	@GameTest
	public void everyExclusiveItemOfTheSiteComesFromItsOwnTables(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (ResourceKey<LootTable> key : List.of(WayshrineMobs.PILGRIM_DROPS, WayshrineMobs.WISP_DROPS, WayshrineMobs.ACOLYTE_DROPS, WayshrineSite.VAULT_LOOT)) {
			helper.assertTrue(com.stella.ruins.world.Exclusives.ownsTable(key.identifier()), key.identifier() + " is outside the mod's namespace");
			helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, key.identifier() + " did not load");
		}
		helper.succeed();
	}
}
