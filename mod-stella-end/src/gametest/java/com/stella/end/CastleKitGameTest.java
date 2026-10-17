package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The kit of a castle wither skeleton: its speed and strength, its netherite and enchantments, its sword and shield. */
public class CastleKitGameTest {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Wither skeletons spawned to read their kits. One in ten rides, and a rider is the same skeleton, so none is skipped. */
	private static final int SKELETONS = 8;

	private static Registry<Enchantment> registry(final ServerLevel end) {
		return end.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static ItemEnchantments enchantsOf(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	/** Wither skeletons spawned on a floor of the real castle, one per spawn cell from a spread of cells; every one is recorded. */
	static List<WitherSkeleton> spawnSkeletons(final GameTestHelper helper, final ServerLevel end, final int floor, final int count, final boolean mounted) {
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		List<WitherSkeleton> spawned = new ArrayList<>();
		int step = Math.max(1, plan.spawnCells().size() / count);
		for (int i = 0; i < count; i++) {
			int[] cell = plan.spawnCells().get((i * step) % plan.spawnCells().size());
			BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
			Mob mob = EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, mounted);
			helper.assertTrue(mob instanceof WitherSkeleton, "a wither skeleton was refused at " + at);
			spawned.add((WitherSkeleton) mob);
		}
		return spawned;
	}

	@GameTest(environment = "mod-stella-end-test:kit_speed_damage", maxTicks = 20)
	public void aCastleWitherSkeletonMovesAtSevenTenthsAndHitsOneAndAHalfTimesAsHardAsItsBase(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<WitherSkeleton> spawned = spawnSkeletons(helper, end, 3, SKELETONS, false);
		StringBuilder bad = new StringBuilder();
		double speedSeen = 0.0;
		double damageSeen = 0.0;
		for (WitherSkeleton skeleton : spawned) {
			double speed = ratio(skeleton, Attributes.MOVEMENT_SPEED, EndMobs.SPEED_ID);
			double damage = ratio(skeleton, Attributes.ATTACK_DAMAGE, EndMobs.DAMAGE_ID);
			speedSeen = speed;
			damageSeen = damage;
			if (Math.abs(speed - EndMobs.SPEED_FACTOR) > 1.0E-6 || Math.abs(damage - EndMobs.DAMAGE_FACTOR) > 1.0E-6) {
				bad.append(" speed ").append(speed).append(" damage ").append(damage);
			}
		}
		EndTestSupport.result("kitSpeedDamage", "skeletons=" + spawned.size() + " speedRatio=" + speedSeen + " damageRatio=" + damageSeen);
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "ratios off the shipped factors:" + bad);
		helper.succeed();
	}

	/** The value of an attribute with the castle modifier, over the value with it taken out: what the modifier does to the base. */
	private static double ratio(final WitherSkeleton skeleton, final Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, final Identifier id) {
		AttributeInstance instance = skeleton.getAttribute(attribute);
		AttributeModifier modifier = instance.getModifier(id);
		double with = instance.getValue();
		instance.removeModifier(id);
		double without = instance.getValue();
		instance.addPermanentModifier(modifier);
		return with / without;
	}

	@GameTest(environment = "mod-stella-end-test:kit_spec", maxTicks = 20)
	public void theKitConstantsAreTheNumbersTheSpecNames(final GameTestHelper helper) {
		boolean ok = EndMobs.SPEED_FACTOR == 0.7 && EndMobs.DAMAGE_FACTOR == 1.5 && MobGear.SKELETON_ENCHANTS == 3 && MobGear.SPECIAL_LEVEL == 4
			&& MobGear.THORNS_LEVEL == 4 && TeleportChaseGoal.MIN_DISTANCE == 8.0 && TeleportChaseGoal.COOLDOWN == 160
			&& EndLevels.ARCHER == 110 && EndLevels.MOUNT == 95 && ArcherVolleyGoal.VOLLEY_SIZE == 12 && ArcherVolleyGoal.SPACING == 3
			&& HurtTeleportGoal.COOLDOWN == 600 && MountGoals.Aura.RANGE == 5.0 && EndMobs.MOUNT_SHARE * 10 == EndMobs.ROLLS;
		EndTestSupport.result("kitSpec", "ok=" + ok);
		helper.assertTrue(ok, "a kit constant departs from the specified number");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:kit_netherite", maxTicks = 20)
	public void everyCastleWitherSkeletonWearsFullNetheriteAndEveryPieceAndTheSwordHoldThreeRandomEnchantments(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		boolean equips = MobGear.find(registry(end), MobGear.SPECIAL_SWORD.get(0)) != null;
		List<WitherSkeleton> spawned = spawnSkeletons(helper, end, 4, SKELETONS, false);
		StringBuilder bad = new StringBuilder();
		int pieces = 0;
		for (WitherSkeleton skeleton : spawned) {
			ItemStack[] worn = {skeleton.getItemBySlot(EquipmentSlot.HEAD), skeleton.getItemBySlot(EquipmentSlot.CHEST), skeleton.getItemBySlot(EquipmentSlot.LEGS),
				skeleton.getItemBySlot(EquipmentSlot.FEET)};
			net.minecraft.world.item.Item[] expected = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
			for (int i = 0; i < worn.length; i++) {
				pieces++;
				if (!worn[i].is(expected[i])) {
					bad.append(" slot ").append(ARMOR[i]).append(" holds ").append(worn[i]);
				}
				if (enchantsOf(worn[i]).size() != MobGear.SKELETON_ENCHANTS) {
					bad.append(" slot ").append(ARMOR[i]).append(" holds ").append(enchantsOf(worn[i]).size()).append(" enchantments");
				}
			}
			ItemStack sword = skeleton.getMainHandItem();
			int expectedOnSword = MobGear.SKELETON_ENCHANTS + (equips ? 1 : 0);
			if (!sword.is(Items.NETHERITE_SWORD) || enchantsOf(sword).size() != expectedOnSword) {
				bad.append(" sword ").append(sword).append(" holds ").append(enchantsOf(sword).size()).append(" enchantments, wanted ").append(expectedOnSword);
			}
		}
		EndTestSupport.result("kitNetherite", "skeletons=" + spawned.size() + " pieces=" + pieces + " equips=" + equips + " bad=" + bad.length());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "kits off the plan:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:kit_roll", maxTicks = 200)
	public void theRandomRollGivesExactlyThreeEnchantmentsThatDoNotExcludeEachOtherForEverySlotAndSeed(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		Registry<Enchantment> registry = registry(end);
		ItemStack[] kinds = {new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_LEGGINGS),
			new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.NETHERITE_SWORD)};
		StringBuilder bad = new StringBuilder();
		int rolls = 0;
		int modpack = 0;
		for (ItemStack kind : kinds) {
			for (long seed = 0; seed < 400; seed++) {
				ItemStack stack = kind.copy();
				int added = MobGear.enchantRandom(stack, MobGear.SKELETON_ENCHANTS, RandomSource.create(seed), registry, List.of());
				ItemEnchantments held = enchantsOf(stack);
				rolls++;
				if (added != MobGear.SKELETON_ENCHANTS || held.size() != MobGear.SKELETON_ENCHANTS) {
					bad.append(' ').append(kind.getItem()).append(" seed ").append(seed).append(" holds ").append(held.size());
				}
				List<Holder<Enchantment>> keys = new ArrayList<>(held.keySet());
				for (int i = 0; i < keys.size(); i++) {
					modpack += keys.get(i).unwrapKey().map(key -> !key.identifier().getNamespace().equals("minecraft")).orElse(false) ? 1 : 0;
					if (!keys.get(i).value().isSupportedItem(stack) || held.getLevel(keys.get(i)) < keys.get(i).value().getMinLevel()
						|| held.getLevel(keys.get(i)) > keys.get(i).value().getMaxLevel()) {
						bad.append(' ').append(kind.getItem()).append(" seed ").append(seed).append(" holds a misfit ").append(keys.get(i));
					}
					for (int j = i + 1; j < keys.size(); j++) {
						if (!Enchantment.areCompatible(keys.get(i), keys.get(j))) {
							bad.append(' ').append(kind.getItem()).append(" seed ").append(seed).append(" holds a clash");
						}
					}
				}
			}
		}
		EndTestSupport.result("kitRoll", "rolls=" + rolls + " modpackEnchantsSeen=" + modpack + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "random rolls off the plan:" + bad);
		helper.assertTrue(modpack > 0, "no modpack enchantment came up in " + rolls + " rolls: the pool is vanilla only");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:kit_special", maxTicks = 200)
	public void theSwordHoldsExactlyOneOfVenomChillWaveformAtLevelFourAndEachComesUpAndTheShieldHoldsThornsFour(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Registry<Enchantment> registry = registry(end);
		int[] counts = new int[MobGear.SPECIAL_SWORD.size()];
		StringBuilder bad = new StringBuilder();
		int rolls = 1500;
		for (long seed = 0; seed < rolls; seed++) {
			ItemStack sword = MobGear.skeletonSword(RandomSource.create(seed), registry);
			ItemEnchantments held = enchantsOf(sword);
			int specials = 0;
			for (int i = 0; i < counts.length; i++) {
				Holder<Enchantment> holder = MobGear.find(registry, MobGear.SPECIAL_SWORD.get(i));
				if (holder == null) {
					bad.append(" the registry lacks ").append(MobGear.SPECIAL_SWORD.get(i));
					continue;
				}
				if (held.getLevel(holder) > 0) {
					specials++;
					counts[i]++;
					if (held.getLevel(holder) != MobGear.SPECIAL_LEVEL) {
						bad.append(" seed ").append(seed).append(' ').append(MobGear.SPECIAL_SWORD.get(i)).append(" at level ").append(held.getLevel(holder));
					}
				}
			}
			if (specials != 1) {
				bad.append(" seed ").append(seed).append(" holds ").append(specials).append(" special enchantments");
			}
			if (held.size() != MobGear.SKELETON_ENCHANTS + 1) {
				bad.append(" seed ").append(seed).append(" holds ").append(held.size()).append(" enchantments");
			}
		}
		// Each of the three is one third of the rolls, give or take five standard deviations of a fair draw.
		double expected = rolls / (double) counts.length;
		double slack = 5.0 * Math.sqrt(rolls * (1.0 / counts.length) * (1.0 - 1.0 / counts.length));
		for (int i = 0; i < counts.length; i++) {
			if (Math.abs(counts[i] - expected) > slack) {
				bad.append(' ').append(MobGear.SPECIAL_SWORD.get(i)).append(" came up ").append(counts[i]).append(" of ").append(rolls);
			}
		}
		ItemStack shield = MobGear.thornsShield(registry);
		Holder<Enchantment> thorns = MobGear.find(registry, Identifier.withDefaultNamespace("thorns"));
		boolean shielded = shield.is(Items.SHIELD) && enchantsOf(shield).getLevel(thorns) == MobGear.THORNS_LEVEL;
		EndTestSupport.result("kitSpecial", "rolls=" + rolls + " counts=" + java.util.Arrays.toString(counts) + " shieldThorns=" + enchantsOf(shield).getLevel(thorns) + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "special enchantments off the plan:" + bad);
		helper.assertTrue(shielded, "the shield does not hold thorns " + MobGear.THORNS_LEVEL + ": " + enchantsOf(shield));
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:kit_special_live", maxTicks = 40)
	public void aSpawnedWitherSkeletonHoldsOneSpecialSwordEnchantmentAndAThornsShieldWhenTheEquipsModIsPresent(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Registry<Enchantment> registry = registry(end);
		boolean equips = MobGear.find(registry, MobGear.SPECIAL_SWORD.get(0)) != null;
		helper.assertTrue(equips, "the equips mod is not in the game test run, so the present case cannot be shown");
		List<WitherSkeleton> spawned = spawnSkeletons(helper, end, 2, SKELETONS, false);
		Holder<Enchantment> thorns = MobGear.find(registry, Identifier.withDefaultNamespace("thorns"));
		StringBuilder bad = new StringBuilder();
		for (WitherSkeleton skeleton : spawned) {
			int specials = 0;
			for (Identifier id : MobGear.SPECIAL_SWORD) {
				specials += enchantsOf(skeleton.getMainHandItem()).getLevel(MobGear.find(registry, id)) == MobGear.SPECIAL_LEVEL ? 1 : 0;
			}
			if (specials != 1) {
				bad.append(" a sword holds ").append(specials).append(" special enchantments");
			}
			if (enchantsOf(skeleton.getOffhandItem()).getLevel(thorns) != MobGear.THORNS_LEVEL || !skeleton.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
				bad.append(" an off hand holds ").append(skeleton.getOffhandItem());
			}
		}
		EndTestSupport.result("kitSpecialLive", "skeletons=" + spawned.size() + " bad=" + bad.length());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "live kits off the plan:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:kit_special_absent", maxTicks = 200)
	public void withoutTheEquipsEnchantmentsTheSwordHoldsOnlyItsThreeRandomOnesAndNothingFails(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		Registry<Enchantment> registry = registry(end);
		// A registry that lacks the equips mod answers null for its ids; names that no registry holds stand for that case.
		List<Identifier> gone = List.of(Identifier.parse("mod-stella-equips-enchants:weapon/absent_one"), Identifier.parse("mod-stella-equips-enchants:weapon/absent_two"));
		StringBuilder bad = new StringBuilder();
		for (long seed = 0; seed < 200; seed++) {
			ItemStack sword = MobGear.skeletonSword(RandomSource.create(seed), registry, gone);
			if (enchantsOf(sword).size() != MobGear.SKELETON_ENCHANTS) {
				bad.append(" seed ").append(seed).append(" holds ").append(enchantsOf(sword).size());
			}
		}
		EndTestSupport.result("kitSpecialAbsent", "rolls=200 bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "the absent case holds the wrong number of enchantments:" + bad);
		helper.succeed();
	}
}
