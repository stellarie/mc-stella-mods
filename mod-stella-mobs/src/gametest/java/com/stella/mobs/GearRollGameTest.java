package com.stella.mobs;

import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.ShieldUseGoal;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.EnchantRoller;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.gear.TierRoller;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class GearRollGameTest {
	private static final int MAX_TICKS = 200;
	private static final int ROLLS = 256;
	private static final long WEIGHT_SEED = 0xC0FFEEL;
	private static final int WEIGHT_ROLLS = 1000;

	@GameTest(maxTicks = MAX_TICKS)
	public void sameTypeRollsDifferentGear(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RollSpec spec = new RollSpec(1.0, 1.0);
		Set<String> outcomes = new HashSet<>();

		for (int i = 0; i < ROLLS; i++) {
			Mob mob = newMob(helper);
			GearRoller.roll(mob, spec, RandomSource.create(i), registry);
			outcomes.add(describe(mob));
		}

		helper.assertTrue(outcomes.size() >= 2, ROLLS + " rolls produced one outcome: " + outcomes);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void cheapTierIsRolledMoreOften(final GameTestHelper helper) {
		List<GearTier> tiers = TierRoller.tiers();
		GearTier cheapest = tiers.getFirst();
		GearTier dearest = tiers.getLast();

		helper.assertTrue(
			cheapest.weight() > dearest.weight(),
			"the cheapest tier does not outweigh the dearest: " + cheapest.weight() + " against " + dearest.weight()
		);
		helper.assertValueEqual(Integer.valueOf(TierRoller.totalWeight(tiers)), Integer.valueOf(100), "total tier weight");

		RandomSource random = RandomSource.create(WEIGHT_SEED);
		int cheap = 0;
		int expensive = 0;

		for (int i = 0; i < WEIGHT_ROLLS; i++) {
			GearTier tier = TierRoller.pick(tiers, 100, random);

			if (tier == cheapest) {
				cheap++;
			}

			if (tier == dearest) {
				expensive++;
			}
		}

		helper.assertTrue(
			cheap > expensive,
			"over " + WEIGHT_ROLLS + " seeded rolls the cheapest tier came up " + cheap + " times against " + expensive
		);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void rolledItemCarriesRegistryEnchantments(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		EnchantRoller.enchant(sword, RandomSource.create(11L), registry);

		helper.assertFalse(sword.getEnchantments().isEmpty(), "the rolled item carries no enchantment");
		List<Holder<Enchantment>> expected = registry.listElements()
			.filter(holder -> holder.value().canEnchant(sword))
			.map(holder -> (Holder<Enchantment>) holder)
			.toList();
		helper.assertTrue(expected.size() >= 5, "the independent registry walk found only " + expected.size());
		helper.assertValueEqual(
			Integer.valueOf(EnchantRoller.poolFor(sword, registry).size()),
			Integer.valueOf(expected.size()),
			"the pool does not match an independent walk of the registry"
		);

		for (Holder<Enchantment> holder : sword.getEnchantments().keySet()) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(holder, sword);
			helper.assertTrue(
				level >= holder.value().getMinLevel() && level <= holder.value().getMaxLevel(),
				"level " + level + " is outside the range 1 to " + holder.value().getMaxLevel()
			);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void dropChanceFallsWithTier(final GameTestHelper helper) {
		List<GearTier> tiers = TierRoller.tiers();

		for (int i = 1; i < tiers.size(); i++) {
			float previous = tiers.get(i - 1).dropChance();
			float current = tiers.get(i).dropChance();
			helper.assertTrue(
				current < previous, "tier " + tiers.get(i) + " drops at " + current + ", not below " + previous
			);
		}

		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Mob plain = newMob(helper);
		GearRoller.roll(plain, new RollSpec(1.0, 1.0), RandomSource.create(21L), registry);
		ItemStack chest = plain.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertFalse(chest.isEmpty(), "the seeded roll left the chest empty");
		GearTier tier = tierOfArmor(chest);
		helper.assertValueEqual(
			Float.valueOf(plain.getDropChances().byEquipment(EquipmentSlot.CHEST)),
			Float.valueOf(tier.dropChance()),
			"the rolled chest drop chance"
		);

		Mob halved = newMob(helper);
		GearRoller.roll(halved, new RollSpec(1.0, 0.5), RandomSource.create(21L), registry);
		helper.assertValueEqual(
			Float.valueOf(halved.getDropChances().byEquipment(EquipmentSlot.CHEST)),
			Float.valueOf((float) (tier.dropChance() * 0.5)),
			"the halved chest drop chance"
		);
		helper.succeed();
	}

	static GearTier tierOfArmor(final ItemStack stack) {
		for (GearTier tier : TierRoller.tiers()) {
			for (EquipmentSlot slot : GearTier.armorSlots()) {
				if (stack.is(tier.armorFor(slot))) {
					return tier;
				}
			}
		}

		throw new IllegalStateException("No gear tier owns " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void exactLoadoutWinsOverTheRoll(final GameTestHelper helper) {
		useConfig("""
			{
				"entities": {
					"minecraft:zombie": {
						"slots": { "mainhand": { "item": "minecraft:golden_hoe" } },
						"drop_chance": 0.5
					}
				},
				"rolls": { "roll_chance": 1.0 }
			}""");
		Mob exact = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 2).spawn();
		Mob rolling = helper.spawnMob(EntityTypes.HUSK, 4, 3, 4).spawn();

		helper.runAfterDelay(2L, () -> {
			helper.assertTrue(
				exact.getMainHandItem().is(Items.GOLDEN_HOE), "the exact loadout did not win: " + describe(exact)
			);
			helper.assertValueEqual(
				Float.valueOf(exact.getDropChances().byEquipment(EquipmentSlot.MAINHAND)),
				Float.valueOf(0.5F),
				"the exact main hand drop chance"
			);
			helper.assertFalse(
				rolling.getMainHandItem().isEmpty(), "the roll never reached the spawned husk: " + describe(rolling)
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void rangedWeaponOnlyForAMobThatCanUseIt(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RollSpec spec = new RollSpec(1.0, 1.0);
		boolean skeletonSawARangedWeapon = false;

		for (int i = 0; i < ROLLS; i++) {
			Mob skeleton = created(EntityTypes.SKELETON, helper);
			GearRoller.roll(skeleton, spec, RandomSource.create(i), registry);
			skeletonSawARangedWeapon |= isRangedWeapon(skeleton.getMainHandItem());

			Mob zombie = created(EntityTypes.ZOMBIE, helper);
			GearRoller.roll(zombie, spec, RandomSource.create(i), registry);
			helper.assertFalse(
				isRangedWeapon(zombie.getMainHandItem()), "a zombie rolled a ranged weapon: " + describe(zombie)
			);
		}

		helper.assertTrue(
			skeletonSawARangedWeapon, "no skeleton roll produced a bow over " + ROLLS + " seeded rolls"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shieldOnlyFollowsAMeleeMainHand(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RollSpec spec = new RollSpec(1.0, 1.0);
		boolean sawAShield = false;

		for (int i = 0; i < ROLLS; i++) {
			Mob zombie = created(EntityTypes.ZOMBIE, helper);
			GearRoller.roll(zombie, spec, RandomSource.create(i), registry);

			if (zombie.getOffhandItem().is(Items.SHIELD)) {
				sawAShield = true;
				helper.assertTrue(
					isMeleeWeapon(zombie.getMainHandItem()),
					"a shield arrived without a melee main hand: " + describe(zombie)
				);
			}
		}

		helper.assertTrue(sawAShield, "no shield appeared over " + ROLLS + " seeded rolls");
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void gearDrivesTheRoleForARollingMob(final GameTestHelper helper) {
		useConfig("""
			{
				"entities": {},
				"rolls": { "roll_chance": 1.0 }
			}""");
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, 3, 2).spawn();
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();

		helper.runAfterDelay(2L, () -> {
			boolean archer = isRangedWeapon(skeleton.getMainHandItem());
			helper.assertValueEqual(
				ReactiveState.get(skeleton).getRole(),
				archer ? CombatRole.RANGED : CombatRole.MELEE,
				"the rolled skeleton role against its main hand: " + describe(skeleton)
			);
			helper.assertValueEqual(
				ReactiveState.get(zombie).getRole(), CombatRole.MELEE, "the rolled zombie role: " + describe(zombie)
			);

			if (archer) {
				helper.assertValueEqual(
					Double.valueOf(ReactiveState.get(skeleton).getNearDistance()), Double.valueOf(5.0), "the archer near band"
				);
				helper.assertValueEqual(
					Double.valueOf(ReactiveState.get(skeleton).getFarDistance()), Double.valueOf(15.0), "the archer far band"
				);
			}

			if (zombie.getOffhandItem().is(Items.SHIELD)) {
				helper.assertTrue(
					hasGoal(zombie, ShieldUseGoal.class), "a rolled shield arrived without the shield goal"
				);
			}

			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void magicStaysMagicWhateverItHolds(final GameTestHelper helper) {
		useConfig("""
			{
				"entities": {
					"minecraft:evoker": { "slots": { "mainhand": { "item": "minecraft:iron_sword" } } }
				}
			}""");
		Mob caster = helper.spawnMob(EntityTypes.EVOKER, 2, 3, 2).spawn();

		helper.runAfterDelay(2L, () -> {
			helper.assertTrue(caster.getMainHandItem().is(Items.IRON_SWORD), "the evoker did not hold the sword");
			helper.assertValueEqual(ReactiveState.get(caster).getRole(), CombatRole.MAGIC, "the casting evoker role");
			helper.assertTrue(
				hasGoal(caster, KeepDistanceGoal.class), "the magic unit has no distance goal"
			);
			helper.succeed();
		});
	}

	static Mob created(final EntityType<? extends Mob> type, final GameTestHelper helper) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		helper.assertTrue(mob != null, "the test mob could not be created");
		return mob;
	}

	static boolean isRangedWeapon(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
	}

	static boolean isMeleeWeapon(final ItemStack stack) {
		return stack.is(Items.WOODEN_SWORD)
			|| stack.is(Items.STONE_SWORD)
			|| stack.is(Items.COPPER_SWORD)
			|| stack.is(Items.IRON_SWORD)
			|| stack.is(Items.GOLDEN_SWORD)
			|| stack.is(Items.DIAMOND_SWORD)
			|| stack.is(Items.NETHERITE_SWORD);
	}

	static boolean hasGoal(final Mob mob, final Class<? extends Goal> goalClass) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (goalClass.isInstance(wrapped.getGoal())) {
				return true;
			}
		}

		return false;
	}

	static Mob newMob(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		helper.assertTrue(mob != null, "the test zombie could not be created");
		return mob;
	}

	static String describe(final Mob mob) {
		StringBuilder text = new StringBuilder();

		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET
		}) {
			ItemStack stack = mob.getItemBySlot(slot);
			text.append(slot).append('=');

			if (!stack.isEmpty()) {
				text.append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			}

			text.append(' ');
		}

		return text.toString();
	}

	static void useConfig(final String json) {
		GearConfigStore.set(GearConfigParser.parse(json));
	}
}
