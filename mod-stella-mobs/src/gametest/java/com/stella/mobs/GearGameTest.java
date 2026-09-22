package com.stella.mobs;

import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class GearGameTest {
	private static final String PLAIN_SWORD_CONFIG = """
		{
			"entities": {
				"minecraft:zombie": {
					"slots": {
						"mainhand": { "item": "minecraft:diamond_sword" }
					}
				}
			}
		}""";
	private static final String SHARP_SWORD_CONFIG = """
		{
			"entities": {
				"minecraft:zombie": {
					"slots": {
						"mainhand": {
							"item": "minecraft:diamond_sword",
							"enchantments": { "minecraft:sharpness": 3 }
						}
					}
				}
			}
		}""";
	private static final String NO_ROLL_CONFIG = """
		{
			"entities": {
				"minecraft:skeleton": {
					"slots": {
						"mainhand": { "item": "minecraft:diamond_sword" }
					},
					"drop_chance": 1.0
				}
			},
			"rolls": { "roll_chance": 0.0 }
		}""";
	private static final String ROLLING_CONFIG = """
		{
			"entities": {
				"minecraft:zombie": {
					"slots": { "mainhand": { "item": "minecraft:golden_hoe" } },
					"drop_chance": 0.5
				}
			},
			"rolls": { "roll_chance": 1.0 }
		}""";

	@GameTest
	public void parserSkipsMalformedEntries(final GameTestHelper helper) {
		String json = """
			{
				"version": 1,
				"_example": { "minecraft:pig": { "slots": { "mainhand": { "item": "minecraft:stick" } } } },
				"entities": {
					"minecraft:zombie": { "slots": { "mainhand": { "item": "minecraft:diamond_sword" } } },
					"minecraft:creeper": { "slots": { "bananas": { "item": "minecraft:diamond_sword" } } },
					"minecraft:skeleton": 7,
					"not a valid id": { "slots": { "mainhand": { "item": "minecraft:iron_sword" } } },
					"minecraft:husk": { "slots": { "mainhand": { "item": "minecraft:not_a_real_item" } } }
				}
			}""";

		GearConfig config = GearConfigParser.parse(json);

		helper.assertTrue(config.loadoutFor(Identifier.parse("minecraft:zombie")) != null, "the valid zombie entry was dropped");
		helper.assertTrue(config.loadoutFor(Identifier.parse("minecraft:pig")) == null, "an entry under an unknown top level key was accepted");
		helper.assertValueEqual(Integer.valueOf(config.entities().size()), Integer.valueOf(1), "accepted entry count");
		helper.succeed();
	}

	@GameTest
	public void appliesConfiguredItemToConfiguredSlot(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(PLAIN_SWORD_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 1).spawnReason(EntitySpawnReason.NATURAL).spawn();
		ItemStack held = zombie.getItemBySlot(EquipmentSlot.MAINHAND);

		helper.assertTrue(held.is(Items.DIAMOND_SWORD), "the configured main hand was not applied: " + held);
		helper.assertTrue(held.getEnchantments().isEmpty(), "the empty configured enchantment set was not written: " + held.getEnchantments());
		helper.succeed();
	}

	@GameTest
	public void appliesConfiguredEnchantmentLevel(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(SHARP_SWORD_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 1).spawnReason(EntitySpawnReason.NATURAL).spawn();
		Registry<Enchantment> enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder.Reference<Enchantment> sharpness = enchantments.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness")));
		ItemStack held = zombie.getItemBySlot(EquipmentSlot.MAINHAND);

		helper.assertValueEqual(Integer.valueOf(EnchantmentHelper.getItemEnchantmentLevel(sharpness, held)), Integer.valueOf(3), "main hand sharpness level");
		helper.assertValueEqual(Integer.valueOf(held.getEnchantments().size()), Integer.valueOf(1), "main hand enchantment count");
		helper.succeed();
	}

	@GameTest
	public void keepsVanillaEquipmentWhenRollsAreOff(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(NO_ROLL_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 1).spawnReason(EntitySpawnReason.NATURAL).spawn();
		ItemStack held = zombie.getItemBySlot(EquipmentSlot.MAINHAND);

		helper.assertFalse(held.is(Items.DIAMOND_SWORD), "the skeleton entry was applied to a zombie: " + held);
		helper.assertValueEqual(
			Float.valueOf(zombie.getDropChances().byEquipment(EquipmentSlot.MAINHAND)),
			Float.valueOf(0.085F),
			"the vanilla main hand drop chance with rolls off"
		);
		helper.succeed();
	}

	@GameTest
	public void rollsGearForAMobWithoutAnEntry(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(ROLLING_CONFIG));

		Zombie husk = helper.spawnMob(EntityTypes.HUSK, 1, 1, 1).spawnReason(EntitySpawnReason.NATURAL).spawn();
		ItemStack held = husk.getItemBySlot(EquipmentSlot.MAINHAND);
		float chance = husk.getDropChances().byEquipment(EquipmentSlot.MAINHAND);

		helper.assertFalse(held.isEmpty(), "a mob with no entry did not roll a main hand");
		helper.assertTrue(isATierDropChance(chance), "the rolled main hand kept a non-tier drop chance: " + chance);
		helper.succeed();
	}

	@GameTest
	public void exactEntryStillWinsWithRollsOn(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(ROLLING_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 1).spawnReason(EntitySpawnReason.NATURAL).spawn();
		ItemStack held = zombie.getItemBySlot(EquipmentSlot.MAINHAND);

		helper.assertTrue(held.is(Items.GOLDEN_HOE), "the exact entry stopped winning: " + held);
		helper.assertValueEqual(
			Float.valueOf(zombie.getDropChances().byEquipment(EquipmentSlot.MAINHAND)),
			Float.valueOf(0.5F),
			"the exact main hand drop chance"
		);
		helper.succeed();
	}

	private static boolean isATierDropChance(final float chance) {
		for (GearTier tier : GearTier.values()) {
			if (chance == tier.dropChance()) {
				return true;
			}
		}

		return false;
	}

	@GameTest
	public void storeWritesDefaultAndReloads(final GameTestHelper helper) {
		Path file = GearConfigStore.path().resolveSibling("gametest-loadouts.json");

		try {
			Files.deleteIfExists(file);
			helper.assertFalse(Files.exists(file), "the test config file could not be removed");

			GearConfigStore.loadFrom(file);
			helper.assertTrue(Files.exists(file), "the default config file was not written");
			helper.assertValueEqual(Integer.valueOf(GearConfigStore.get().entities().size()), Integer.valueOf(4), "default entry count");
			helper.assertTrue(GearConfigStore.get().loadoutFor(Identifier.parse("minecraft:zombie")) != null, "the default config has no zombie entry");
			helper.assertValueEqual(
				Double.valueOf(GearConfigStore.get().rollFor(Identifier.parse("minecraft:zombie")).rollChance()),
				Double.valueOf(0.15),
				"the default roll chance"
			);

			Files.writeString(file, PLAIN_SWORD_CONFIG, StandardCharsets.UTF_8);
			GearConfigStore.loadFrom(file);
			helper.assertTrue(GearConfigStore.get().loadoutFor(Identifier.parse("minecraft:zombie")) != null, "the reloaded config was not kept");
		} catch (IOException e) {
			helper.fail("config store file error: " + e);
		}

		helper.succeed();
	}
}
