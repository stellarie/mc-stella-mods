package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.Exclusives;
import com.stella.ruins.world.RuinMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 6 and 9, and the sketch review's clamp: the three kinds, the captain's volley, and what each drops. */
public class WatchtowerMobsGameTest {
	static final int ROLLS = 4000;
	static final long SEED = 0x6A22150AL;

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed, final boolean entity) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
		if (entity) {
			Mob mob = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
			builder.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		return table.getRandomItems(builder.create(entity ? LootContextParamSets.ENTITY : LootContextParamSets.CHEST), seed);
	}

	@GameTest
	public void theArcherIsAGreenCappedSkeletonWithABow(final GameTestHelper helper) {
		Mob mob = WatchtowerMobs.WATCH_ARCHER.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		mob.setNoAi(true);
		helper.assertTrue(mob.getType() == EntityTypes.SKELETON, "the archer is no skeleton");
		helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "the archer holds no bow");
		ItemStack cap = mob.getItemBySlot(EquipmentSlot.HEAD);
		helper.assertTrue(cap.is(Items.LEATHER_HELMET) && cap.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(0)).rgb() == WatchtowerMobs.CAP_COLOR, "the cap is not dyed green: " + cap);
		helper.assertTrue(RuinMobs.kindOf(mob) == WatchtowerMobs.WATCH_ARCHER && WatchtowerMobs.WATCH_ARCHER.drops().equals(WatchtowerMobs.ARCHER_DROPS), "the kind or its table is wrong");
		TowerTestSupport.result("archerKit", "cap=" + cap + " main=" + mob.getItemBySlot(EquipmentSlot.MAINHAND));
		mob.discard();
		helper.succeed();
	}

	@GameTest
	public void theCrossbowmanIsAnIronHelmedPillagerWithACrossbow(final GameTestHelper helper) {
		Mob mob = WatchtowerMobs.TOWER_CROSSBOWMAN.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		mob.setNoAi(true);
		helper.assertTrue(mob.getType() == EntityTypes.PILLAGER, "the crossbowman is no pillager");
		helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW), "the crossbowman holds no crossbow");
		helper.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the crossbowman wears no iron helmet");
		helper.assertTrue(RuinMobs.kindOf(mob) == WatchtowerMobs.TOWER_CROSSBOWMAN && WatchtowerMobs.TOWER_CROSSBOWMAN.drops().equals(WatchtowerMobs.CROSSBOWMAN_DROPS), "the kind or its table is wrong");
		mob.discard();
		helper.succeed();
	}

	@GameTest
	public void theCaptainIsALargeBossStrayWithFiveTimesTheHealth(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob plain = EntityTypes.STRAY.create(level, EntitySpawnReason.COMMAND);
		double base = plain.getAttributeBaseValue(Attributes.MAX_HEALTH);
		Mob mob = WatchtowerMobs.WATCH_CAPTAIN.spawn(level, helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		mob.setNoAi(true);
		helper.assertTrue(mob.getType() == EntityTypes.STRAY, "the captain is no stray");
		helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.SCALE) - WatchtowerMobs.CAPTAIN_SCALE) < 1.0E-6, "scale " + mob.getAttributeValue(Attributes.SCALE));
		helper.assertTrue(Math.abs(mob.getMaxHealth() - base * WatchtowerMobs.CAPTAIN_HEALTH) < 1.0E-3, "health " + mob.getMaxHealth() + " base " + base);
		helper.assertTrue(WatchtowerMobs.WATCH_CAPTAIN.boss() && mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "the captain is no boss with a bow");
		helper.assertTrue(WatchtowerMobs.WATCH_CAPTAIN.drops().equals(WatchtowerMobs.CAPTAIN_DROPS), "the captain's table is wrong");
		TowerTestSupport.result("captainKit", "scale=" + mob.getAttributeValue(Attributes.SCALE) + " health=" + mob.getMaxHealth() + " base=" + base);
		mob.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theCaptainLoosesThreeArrowsInAFanAtItsTargetEveryTwoHundredTicks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cow cow = TowerTestSupport.cow(helper, 14.5, 4.5, 1_000_000.0F);
		Mob captain = WatchtowerMobs.WATCH_CAPTAIN.spawn(level, helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
		captain.setNoAi(true);
		captain.setTarget(cow);
		List<List<UUID>> volleys = new ArrayList<>();
		WatchtowerMobs.volleyListener = volleys::add;
		try {
			captain.tickCount = 100;
			WatchtowerMobs.WATCH_CAPTAIN.ability().tick(captain, level);
			helper.assertTrue(volleys.isEmpty(), "the captain fired at tick 100, inside the cooldown");
			captain.tickCount = WatchtowerMobs.VOLLEY_TICKS;
			WatchtowerMobs.WATCH_CAPTAIN.ability().tick(captain, level);
			helper.assertTrue(volleys.size() == 1 && volleys.get(0).size() == WatchtowerMobs.VOLLEY_ARROWS, "volleys " + volleys);
			Vec3 aim = cow.getBoundingBox().getCenter().subtract(captain.getEyePosition());
			double aimYaw = Math.atan2(aim.z, aim.x);
			List<Double> turns = new ArrayList<>();
			for (UUID id : volleys.get(0)) {
				Entity entity = level.getEntity(id);
				helper.assertTrue(entity instanceof Arrow arrow && arrow.getOwner() == captain, "a volley arrow is missing or not the captain's");
				Vec3 motion = entity.getDeltaMovement();
				turns.add(Math.toDegrees(Math.atan2(motion.z, motion.x) - aimYaw));
			}
			TowerTestSupport.result("captainVolley", "arrows=" + turns.size() + " turnsDeg=" + turns + " spread=" + WatchtowerMobs.VOLLEY_SPREAD_DEGREES);
			turns.sort(Double::compare);
			helper.assertTrue(Math.abs(turns.get(0) + WatchtowerMobs.VOLLEY_SPREAD_DEGREES) < 1.5 && Math.abs(turns.get(1)) < 1.5 && Math.abs(turns.get(2) - WatchtowerMobs.VOLLEY_SPREAD_DEGREES) < 1.5,
				"the arrows do not fan out by " + WatchtowerMobs.VOLLEY_SPREAD_DEGREES + " degrees: " + turns);
			for (UUID id : volleys.get(0)) {
				level.getEntity(id).discard();
			}
			captain.tickCount = WatchtowerMobs.VOLLEY_TICKS + 40;
			WatchtowerMobs.WATCH_CAPTAIN.ability().tick(captain, level);
			captain.setTarget(null);
			captain.tickCount = 2 * WatchtowerMobs.VOLLEY_TICKS;
			WatchtowerMobs.WATCH_CAPTAIN.ability().tick(captain, level);
			helper.assertTrue(volleys.size() == 1, "the captain fired outside its window or with no target: " + volleys.size() + " volleys");
		} finally {
			WatchtowerMobs.volleyListener = null;
			captain.discard();
			cow.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aGarrisonMobOutsideTheShaftIsPutBackOnItsPost(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 post = helper.absoluteVec(new Vec3(6.5, 2.0, 6.5));
		Vec3 low = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		Vec3 high = helper.absoluteVec(new Vec3(9.5, 2.0, 9.5));
		for (com.stella.ruins.world.RuinMobKind kind : new com.stella.ruins.world.RuinMobKind[] {WatchtowerMobs.WATCH_ARCHER, WatchtowerMobs.TOWER_CROSSBOWMAN, WatchtowerMobs.WATCH_CAPTAIN}) {
			Mob mob = kind.spawn(level, post);
			mob.setNoAi(true);
			mob.addTag(WatchtowerMobs.garrisonTag((int) Math.floor(low.x), (int) Math.floor(low.z), (int) Math.floor(high.x), (int) Math.floor(high.z),
				(int) Math.floor(post.x), (int) Math.floor(post.y), (int) Math.floor(post.z)));
			mob.snapTo(post.x, post.y, post.z);
			kind.ability().tick(mob, level);
			helper.assertTrue(Math.abs(mob.getX() - post.x) < 1.0E-6, kind.id() + " inside the shaft was moved");
			mob.snapTo(post.x + 9.0, post.y, post.z);
			kind.ability().tick(mob, level);
			TowerTestSupport.result("garrisonClamp", "kind=" + kind.id() + " back=" + mob.position() + " post=" + post);
			helper.assertTrue(Math.abs(mob.getX() - (Math.floor(post.x) + 0.5)) < 1.0E-6 && Math.abs(mob.getZ() - (Math.floor(post.z) + 0.5)) < 1.0E-6,
				kind.id() + " outside the shaft was not put back on its post: " + mob.position());
			mob.snapTo(post.x, post.y, post.z + 7.0);
			kind.ability().tick(mob, level);
			helper.assertTrue(mob.blockPosition().getZ() == (int) Math.floor(post.z), kind.id() + " outside on z was not put back");
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void aMobWithNoGarrisonTagIsLeftWhereItStands(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob mob = WatchtowerMobs.WATCH_ARCHER.spawn(level, helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		mob.setNoAi(true);
		Vec3 where = mob.position();
		helper.assertTrue(!WatchtowerMobs.keepInside(mob) && mob.position().equals(where), "a mob with no tag was moved");
		mob.discard();
		helper.succeed();
	}

	private static int rolls(final GameTestHelper helper, final net.minecraft.resources.ResourceKey<LootTable> table, final Item item, final long seed) {
		RandomSource master = RandomSource.create(seed);
		int hits = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(helper.getLevel(), table, master.nextLong(), true)) {
				helper.assertTrue(stack.is(item) && stack.getCount() == 1, "the table dropped " + stack);
				hits++;
			}
		}
		return hits;
	}

	@GameTest
	public void theArcherDropsAHawkfeatherAtFiftyPercent(final GameTestHelper helper) {
		double rate = rolls(helper, WatchtowerMobs.ARCHER_DROPS, WatchtowerItems.HAWKFEATHER, 20261005L) / (double) ROLLS;
		TowerTestSupport.result("archerDrops", "rolls=" + ROLLS + " featherRate=" + rate);
		helper.assertTrue(Math.abs(rate - 0.50) <= 0.50 * 0.05, "feather rate " + rate);
		helper.succeed();
	}

	@GameTest
	public void theCrossbowmanDropsASignalEmberAtFortyPercent(final GameTestHelper helper) {
		double rate = rolls(helper, WatchtowerMobs.CROSSBOWMAN_DROPS, WatchtowerItems.SIGNAL_EMBER, 20261006L) / (double) ROLLS;
		TowerTestSupport.result("crossbowDrops", "rolls=" + ROLLS + " emberRate=" + rate);
		helper.assertTrue(Math.abs(rate - 0.40) <= 0.40 * 0.05, "ember rate " + rate);
		helper.succeed();
	}

	@GameTest
	public void theCaptainAlwaysDropsOneInsigniaAndThreeOrFourFeathers(final GameTestHelper helper) {
		RandomSource master = RandomSource.create(20261007L);
		boolean three = false;
		boolean four = false;
		for (int i = 0; i < ROLLS; i++) {
			int feathers = 0;
			int insignia = 0;
			for (ItemStack stack : roll(helper.getLevel(), WatchtowerMobs.CAPTAIN_DROPS, master.nextLong(), true)) {
				if (stack.is(WatchtowerItems.HAWKFEATHER)) {
					feathers += stack.getCount();
				} else if (stack.is(WatchtowerItems.CAPTAINS_INSIGNIA)) {
					insignia += stack.getCount();
				} else {
					helper.fail("the captain dropped " + stack);
				}
			}
			helper.assertTrue(feathers >= 3 && feathers <= 4 && insignia == 1, "the captain dropped " + feathers + " feathers and " + insignia + " insignia");
			three |= feathers == 3;
			four |= feathers == 4;
		}
		helper.assertTrue(three && four, "captain feather counts did not cover 3 and 4");
		helper.succeed();
	}

	@GameTest(maxTicks = TowerTestSupport.ARENA_TICKS)
	public void aPlayerKillOfTheCaptainDropsItsInsigniaThroughTheRealDeath(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.join(arena.standAt());
			Vec3 at = arena.standAt().add(0.0, 1.0, 3.0);
			Mob captain = WatchtowerMobs.WATCH_CAPTAIN.spawn(arena.level(), at);
			captain.setNoAi(true);
			captain.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
			int insignia = 0;
			int feathers = 0;
			for (ItemEntity drop : arena.level().getEntitiesOfClass(ItemEntity.class, captain.getBoundingBox().inflate(6.0))) {
				if (drop.getItem().is(WatchtowerItems.CAPTAINS_INSIGNIA)) {
					insignia += drop.getItem().getCount();
				}
				if (drop.getItem().is(WatchtowerItems.HAWKFEATHER)) {
					feathers += drop.getItem().getCount();
				}
				drop.discard();
			}
			TowerTestSupport.result("captainDeath", "insignia=" + insignia + " feathers=" + feathers);
			helper.assertTrue(insignia == 1 && feathers >= 3 && feathers <= 4, "the captain's death dropped " + insignia + " insignia and " + feathers + " feathers");
		});
	}

	@GameTest
	public void theVaultTableGivesFourToEightFeathersAndTheStoreTableGivesNoExclusiveItem(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		int arrows = 0;
		int iron = 0;
		int emeralds = 0;
		for (int i = 0; i < 400; i++) {
			int feathers = 0;
			for (ItemStack stack : roll(level, WatchtowerMobs.VAULT_LOOT, master.nextLong(), false)) {
				if (stack.is(WatchtowerItems.HAWKFEATHER)) {
					feathers += stack.getCount();
				} else if (stack.is(Items.ARROW)) {
					arrows += stack.getCount();
				} else if (stack.is(Items.IRON_INGOT)) {
					iron += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else {
					helper.fail("the vault table gave " + stack);
				}
			}
			helper.assertTrue(feathers >= 4 && feathers <= 8, "roll " + i + " gave " + feathers + " feathers");
		}
		helper.assertTrue(arrows > 0 && iron > 0 && emeralds > 0, "arrows " + arrows + " iron " + iron + " emeralds " + emeralds);
		int exclusive = 0;
		int stacks = 0;
		boolean arrow = false;
		boolean food = false;
		for (int i = 0; i < 400; i++) {
			for (ItemStack stack : roll(level, WatchtowerMobs.STORE_LOOT, master.nextLong(), false)) {
				stacks++;
				arrow |= stack.is(Items.ARROW);
				food |= stack.is(Items.BREAD);
				if (Exclusives.isExclusive(stack)) {
					exclusive++;
				}
			}
		}
		TowerTestSupport.result("lootTables", "vault arrows=" + arrows + " iron=" + iron + " emeralds=" + emeralds + " store stacks=" + stacks + " exclusive=" + exclusive);
		helper.assertTrue(exclusive == 0 && stacks > 0 && arrow && food, "the store table gave " + exclusive + " exclusive items in " + stacks + " stacks");
		helper.succeed();
	}
}
