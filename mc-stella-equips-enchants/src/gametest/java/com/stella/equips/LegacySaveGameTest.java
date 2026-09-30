package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerTracker;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class LegacySaveGameTest {
	// The attachment Stage 2 retires, and a sibling id that was never registered.
	private static final String POWER_STATE_KEY = StellaEquips.MOD_ID + ":power_state";
	private static final String UNREGISTERED_STATE_KEY = StellaEquips.MOD_ID + ":retired_power_probe";
	private static final String ATTACHMENTS_KEY = "fabric:attachments";
	private static final Identifier VEINMINER = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "tool/veinminer");
	private static final Identifier UNREGISTERED_ENCHANTMENT = Identifier.fromNamespaceAndPath(
		StellaEquips.MOD_ID, "tool/retired_probe"
	);
	// The placeholder enchantment whose key the fixture renames into the removed id.
	private static final String PLACEHOLDER_KEY = "minecraft:unbreaking";
	private static final int KEPT_EFFICIENCY = 3;
	private static final int VANILLA_LEVEL = 7;
	private static final float VANILLA_PROGRESS = 0.25F;
	private static final int VANILLA_TOTAL = 150;
	private static final int LEGACY_LEVEL = 30;
	private static final double LEGACY_POINTS = 5000.0;
	private static final int KEPT_DIRT = 5;
	private static final double TOLERANCE = 1.0E-9;

	@GameTest
	public void legacyPowerStateLoadsWithVanillaXpAndInventoryIntact(final GameTestHelper helper) {
		ProblemReporter.Collector problems = new ProblemReporter.Collector();
		ServerPlayer loaded = loadLegacyPlayer(helper, problems);
		boolean xpKept = loaded.experienceLevel == VANILLA_LEVEL
			&& loaded.totalExperience == VANILLA_TOTAL
			&& Math.abs(loaded.experienceProgress - VANILLA_PROGRESS) < TOLERANCE;
		int dirt = loaded.getInventory().getItem(0).is(Items.DIRT) ? loaded.getInventory().getItem(0).getCount() : 0;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=legacyPlayerLoads RESULT level={} total={} progress={} dirt={} problems={}",
			loaded.experienceLevel, loaded.totalExperience, loaded.experienceProgress, dirt, problems.getReport()
		);
		helper.assertTrue(xpKept && dirt == KEPT_DIRT, "the legacy player loaded with level "
			+ loaded.experienceLevel + ", total " + loaded.totalExperience + ", progress "
			+ loaded.experienceProgress + " and " + dirt + " dirt");
		helper.succeed();
	}

	@GameTest
	public void firstScanReplacesEveryLegacyModifierWithTheVanillaLevelValue(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer loaded = loadLegacyPlayer(helper, new ProblemReporter.Collector());
		PowerTracker.scanPlayer(loaded, config);
		ServerPlayer reference = mockPlayer(helper);
		PowerAttributes.apply(reference, VANILLA_LEVEL, config);
		int mismatched = mismatches(loaded, reference);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=legacyFirstScan RESULT level={} mismatched={} loaded={} expected={}",
			loaded.experienceLevel, mismatched, describe(loaded), describe(reference)
		);
		helper.assertTrue(
			mismatched == 0 && loaded.experienceLevel == VANILLA_LEVEL,
			"after the first scan " + mismatched + " of 5 modifiers still differ from vanilla level " + VANILLA_LEVEL
				+ ": " + describe(loaded) + " expected " + describe(reference)
		);
		helper.succeed();
	}

	@GameTest
	public void scannedPlayerKeepsVanillaLevelAndModifiersAcrossSaveAndReload(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer first = loadLegacyPlayer(helper, new ProblemReporter.Collector());
		PowerTracker.scanPlayer(first, config);
		ServerPlayer second = mockPlayer(helper);
		second.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), save(helper, first)));
		PowerTracker.scanPlayer(second, config);
		ServerPlayer reference = mockPlayer(helper);
		PowerAttributes.apply(reference, VANILLA_LEVEL, config);
		int mismatched = mismatches(second, reference);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=legacySaveReload RESULT level={} mismatched={} reloaded={}",
			second.experienceLevel, mismatched, describe(second)
		);
		helper.assertTrue(
			mismatched == 0 && second.experienceLevel == VANILLA_LEVEL,
			"after save and reload the player had level " + second.experienceLevel + " and " + mismatched
				+ " mismatched modifiers: " + describe(second)
		);
		helper.succeed();
	}

	@GameTest
	public void respawnedPlayerScansFromItsOwnVanillaLevel(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer died = helper.makeMockServerPlayerInLevel();
		died.load(TagValueInput.create(
			ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacyPlayerTag(helper)
		));
		PowerTracker.scanPlayer(died, config);
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(
			died, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED
		);
		PowerTracker.scanPlayer(reborn, config);
		ServerPlayer reference = mockPlayer(helper);
		PowerAttributes.apply(reference, reborn.experienceLevel, config);
		int mismatched = mismatches(reborn, reference);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=legacyRespawn RESULT levelBefore={} levelAfter={} mismatched={} reborn={}",
			VANILLA_LEVEL, reborn.experienceLevel, mismatched, describe(reborn)
		);
		helper.assertTrue(
			mismatched == 0,
			"the respawned player at vanilla level " + reborn.experienceLevel + " carried " + describe(reborn)
				+ " instead of " + describe(reference)
		);
		helper.succeed();
	}

	@GameTest
	public void unknownEnchantmentKeepsThePlayersPhysicalStack(final GameTestHelper helper) {
		assertStackSurvives(helper, "player", loadPlayerStack(helper, UNREGISTERED_ENCHANTMENT), UNREGISTERED_ENCHANTMENT);
	}

	@GameTest
	public void unknownEnchantmentKeepsTheChestsPhysicalStack(final GameTestHelper helper) {
		assertStackSurvives(helper, "chest", loadChestStack(helper, UNREGISTERED_ENCHANTMENT), UNREGISTERED_ENCHANTMENT);
	}

	@GameTest
	public void unknownEnchantmentKeepsTheDroppedPhysicalStack(final GameTestHelper helper) {
		assertStackSurvives(helper, "dropped", loadDroppedStack(helper, UNREGISTERED_ENCHANTMENT), UNREGISTERED_ENCHANTMENT);
	}

	@GameTest
	public void veinminerStacksKeepTheirItemAndOtherEnchantmentsEverywhere(final GameTestHelper helper) {
		List<ItemStack> loaded = List.of(
			loadPlayerStack(helper, VEINMINER),
			loadChestStack(helper, VEINMINER),
			loadDroppedStack(helper, VEINMINER)
		);
		int kept = 0;
		for (ItemStack stack : loaded) {
			kept += keepsItemAndEfficiency(helper, stack) ? 1 : 0;
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=veinminerStacks RESULT registered={} kept={} player={} chest={} dropped={}",
			registered(helper, VEINMINER), kept, describe(loaded.get(0)), describe(loaded.get(1)), describe(loaded.get(2))
		);
		helper.assertTrue(kept == loaded.size(), "only " + kept + " of " + loaded.size() + " Veinminer stacks kept their item");
		helper.succeed();
	}

	private static void assertStackSurvives(
		final GameTestHelper helper, final String where, final ItemStack stack, final Identifier removed
	) {
		boolean unregistered = !registered(helper, removed);
		boolean kept = keepsItemAndEfficiency(helper, stack);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=unknownEnchantmentStack RESULT where={} unregistered={} kept={} stack={}",
			where, unregistered, kept, describe(stack)
		);
		helper.assertTrue(unregistered, removed + " is registered, so this stack does not test an unknown enchantment");
		helper.assertTrue(kept, "the " + where + " stack came back as " + describe(stack));
		helper.succeed();
	}

	private static boolean keepsItemAndEfficiency(final GameTestHelper helper, final ItemStack stack) {
		Holder<Enchantment> efficiency = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
		return stack.is(Items.DIAMOND_PICKAXE) && EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack) == KEPT_EFFICIENCY;
	}

	private static boolean registered(final GameTestHelper helper, final Identifier id) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).isPresent();
	}

	private static ItemStack enchantedPickaxe(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		stack.enchant(registry.getOrThrow(Enchantments.EFFICIENCY), KEPT_EFFICIENCY);
		stack.enchant(registry.getOrThrow(Enchantments.UNBREAKING), 1);
		return stack;
	}

	private static ItemStack loadPlayerStack(final GameTestHelper helper, final Identifier removed) {
		ServerPlayer saved = mockPlayer(helper);
		saved.getInventory().setItem(0, enchantedPickaxe(helper));
		CompoundTag tag = save(helper, saved);
		rename(tag, PLACEHOLDER_KEY, removed.toString());
		ServerPlayer loaded = mockPlayer(helper);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		return loaded.getInventory().getItem(0);
	}

	private static ItemStack loadChestStack(final GameTestHelper helper, final Identifier removed) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
		chest.setItem(0, enchantedPickaxe(helper));
		CompoundTag tag = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
		rename(tag, PLACEHOLDER_KEY, removed.toString());
		chest.clearContent();
		chest.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		return chest.getItem(0);
	}

	private static ItemStack loadDroppedStack(final GameTestHelper helper, final Identifier removed) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		ItemEntity dropped = new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(), enchantedPickaxe(helper));
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		dropped.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		rename(tag, PLACEHOLDER_KEY, removed.toString());
		ItemEntity loaded = new ItemEntity(EntityTypes.ITEM, helper.getLevel());
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		return loaded.getItem();
	}

	private static ServerPlayer loadLegacyPlayer(final GameTestHelper helper, final ProblemReporter reporter) {
		ServerPlayer loaded = mockPlayer(helper);
		loaded.load(TagValueInput.create(reporter, helper.getLevel().registryAccess(), legacyPlayerTag(helper)));
		return loaded;
	}

	// A player saved by the pre-Stage 2 build: vanilla XP, custom points, and the five modifiers at the custom level.
	private static CompoundTag legacyPlayerTag(final GameTestHelper helper) {
		ServerPlayer saved = mockPlayer(helper);
		saved.experienceLevel = VANILLA_LEVEL;
		saved.totalExperience = VANILLA_TOTAL;
		saved.experienceProgress = VANILLA_PROGRESS;
		saved.getInventory().setItem(0, new ItemStack(Items.DIRT, KEPT_DIRT));
		PowerAttributes.apply(saved, LEGACY_LEVEL, PowerFixtures.acceptanceConfig());
		CompoundTag tag = save(helper, saved);
		CompoundTag state = new CompoundTag();
		state.putDouble("combat_points", LEGACY_POINTS);
		state.putInt("level", LEGACY_LEVEL);
		CompoundTag attachments = new CompoundTag();
		attachments.put(POWER_STATE_KEY, state);
		attachments.put(UNREGISTERED_STATE_KEY, state.copy());
		tag.put(ATTACHMENTS_KEY, attachments);
		return tag;
	}

	private static CompoundTag save(final GameTestHelper helper, final ServerPlayer player) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(output);
		return output.buildResult();
	}

	private static void rename(final Tag tag, final String from, final String to) {
		if (tag instanceof CompoundTag compound) {
			for (String key : new ArrayList<>(compound.keySet())) {
				Tag child = compound.get(key);
				rename(child, from, to);
				if (key.equals(from)) {
					compound.remove(key);
					compound.put(to, child);
				}
			}
		} else if (tag instanceof ListTag list) {
			for (Tag child : list) {
				rename(child, from, to);
			}
		}
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static int mismatches(final ServerPlayer actual, final ServerPlayer expected) {
		double[] a = values(actual);
		double[] b = values(expected);
		int different = 0;
		for (int index = 0; index < a.length; index++) {
			different += Math.abs(a[index] - b[index]) > TOLERANCE ? 1 : 0;
		}

		return different;
	}

	private static double[] values(final ServerPlayer player) {
		return new double[] {
			amount(player.getAttribute(Attributes.MAX_HEALTH), "max_health"),
			amount(player.getAttribute(Attributes.ARMOR), "armor"),
			amount(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), "knockback_resistance"),
			amount(player.getAttribute(Attributes.ATTACK_DAMAGE), "attack_damage"),
			amount(player.getAttribute(Attributes.ATTACK_SPEED), "attack_speed")
		};
	}

	private static double amount(final AttributeInstance instance, final String name) {
		if (instance == null) {
			return Double.NaN;
		}

		AttributeModifier modifier = instance.getModifier(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_" + name));
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static String describe(final ServerPlayer player) {
		double[] v = values(player);
		return "health=" + v[0] + " armor=" + v[1] + " knockback=" + v[2] + " damage=" + v[3] + " speed=" + v[4];
	}

	private static String describe(final ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.getItem() + " " + stack.getEnchantments();
	}
}
