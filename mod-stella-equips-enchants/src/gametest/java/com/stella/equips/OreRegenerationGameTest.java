package com.stella.equips;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.Objective;

public class OreRegenerationGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final String HANDLER_CLASS = "com.stella.equips.enchant.OreRegeneration";
	private static final int RATE_SAMPLES = 1500;
	private static final int FIRST_LEVEL = 1;
	private static final int SCALED_LEVEL = 5;
	// The approved rate: 5 percent per level, so 25 percent at level 5.
	private static final int APPROVED_CHANCE_PER_LEVEL_PERCENT = 5;
	private static final int RED_COOLDOWN_TICKS = 20;
	// 0.75 to the power of 64 is below one in a hundred million, so a healthy handler always regrows in budget.
	private static final int REGROW_ATTEMPTS = 64;

	@GameTest(maxTicks = 200)
	public void oreRegenerationMeasuresRatesAfterCompletedBreaks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Enchantment enchantment = enchantments.getValue(
			Identifier.fromNamespaceAndPath(MOD_ID, "tool/ore_regeneration")
		);
		if (enchantment == null) {
			throw new AssertionError("the ore_regeneration enchantment is missing");
		}

		int maxLevel = enchantment.getMaxLevel();
		if (maxLevel != 5) {
			throw new AssertionError("Ore Regeneration max level is " + maxLevel + " instead of 5");
		}

		List<Block> oreBlocks = oreBlocks(level);
		if (oreBlocks.isEmpty()) {
			throw new AssertionError("the minecraft:ores block tag is empty");
		}

		ServerPlayer miner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		PacketRecorder.attach(miner);
		BlockPos target = helper.absolutePos(new BlockPos(2, 3, 2));
		miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		ItemStack plainPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		hold(miner, plainPickaxe);
		level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
		boolean controlDestroyed = miner.gameMode.destroyBlock(target);
		boolean controlAir = level.getBlockState(target).isAir();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreRegenerationBreakControl RESULT destroyReturned={} air={}",
			controlDestroyed, controlAir
		);
		if (!controlDestroyed || !controlAir) {
			throw new AssertionError("gameMode.destroyBlock did not remove the unenchanted control block");
		}

		HandlerRules rules = handlerRules();
		int chancePerLevel = rules.chancePerLevelPercent();
		int cooldownTicks = rules.cooldownTicks();
		Registry<Enchantment> registry = enchantments;
		int clock = 0;
		runCommand(helper, miner, "scoreboard players set #clock ench_clock 0");
		runCommand(helper, miner, "scoreboard players set @s ench_cd_ore 0");

		Map<Integer, Integer> regenerated = new LinkedHashMap<>();
		Map<Integer, Integer> completed = new LinkedHashMap<>();
		for (int levelValue : List.of(FIRST_LEVEL, SCALED_LEVEL)) {
			hold(miner, enchanted(registry, levelValue));
			int successes = 0;
			int breaks = 0;
			for (int sample = 0; sample < RATE_SAMPLES; sample++) {
				clock += cooldownTicks;
				runCommand(helper, miner, "scoreboard players set #clock ench_clock " + clock);
				level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
				if (!miner.gameMode.destroyBlock(target)) {
					continue;
				}
				breaks++;
				Block afterBreak = level.getBlockState(target).getBlock();
				if (afterBreak == Blocks.DIAMOND_ORE) {
					successes++;
				} else if (afterBreak != Blocks.AIR) {
					throw new AssertionError("a diamond ore break restored " + BuiltInRegistries.BLOCK.getKey(afterBreak));
				}
			}
			regenerated.put(levelValue, successes);
			completed.put(levelValue, breaks);
		}

		hold(miner, enchanted(registry, maxLevel));
		List<String> identityFailures = new ArrayList<>();
		int oreBreaks = 0;
		for (Block ore : oreBlocks) {
			// At 25 percent an ore regrows within the attempt budget unless the handler is broken.
			boolean regrew = false;
			for (int attempt = 0; attempt < REGROW_ATTEMPTS && !regrew; attempt++) {
				clock += cooldownTicks;
				runCommand(helper, miner, "scoreboard players set #clock ench_clock " + clock);
				level.setBlockAndUpdate(target, ore.defaultBlockState());
				boolean destroyed = miner.gameMode.destroyBlock(target);
				Block afterBreak = level.getBlockState(target).getBlock();
				if (destroyed) {
					oreBreaks++;
				}
				if (!destroyed || (afterBreak != ore && afterBreak != Blocks.AIR)) {
					identityFailures.add(
						BuiltInRegistries.BLOCK.getKey(ore) + "->" + BuiltInRegistries.BLOCK.getKey(afterBreak)
					);
				}
				regrew = afterBreak == ore;
			}
			if (!regrew) {
				identityFailures.add(BuiltInRegistries.BLOCK.getKey(ore) + " never regrew");
			}
		}

		clock += cooldownTicks;
		runCommand(helper, miner, "scoreboard players set #clock ench_clock " + clock);
		level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
		boolean nonOreDestroyed = miner.gameMode.destroyBlock(target);
		boolean nonOreStayedAir = nonOreDestroyed && level.getBlockState(target).isAir();

		boolean cooldownReady = false;
		for (int attempt = 0; attempt < REGROW_ATTEMPTS && !cooldownReady; attempt++) {
			clock += cooldownTicks;
			runCommand(helper, miner, "scoreboard players set #clock ench_clock " + clock);
			level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
			cooldownReady = miner.gameMode.destroyBlock(target) && level.getBlockState(target).is(Blocks.DIAMOND_ORE);
		}
		int cooldownStartedAt = score(helper, miner, "ench_cd_ore");

		boolean blockedAtZero = regrowths(helper, miner, level, target, cooldownStartedAt) == 0;
		boolean blockedAtNineteen = regrowths(helper, miner, level, target, cooldownStartedAt + cooldownTicks - 1) == 0;
		boolean readyAtTwenty = false;
		for (int attempt = 0; attempt < REGROW_ATTEMPTS && !readyAtTwenty; attempt++) {
			runCommand(
				helper, miner,
				"scoreboard players set #clock ench_clock " + (score(helper, miner, "ench_cd_ore") + cooldownTicks)
			);
			level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
			readyAtTwenty = miner.gameMode.destroyBlock(target) && level.getBlockState(target).is(Blocks.DIAMOND_ORE);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreRegeneration RESULT handlerPresent={} chancePerLevelPercent={} maxLevel={} "
				+ "cooldownTicks={} controlAir={} level1={}/{} completed1={} rate1Percent={}% level5={}/{} "
				+ "completed5={} rate5Percent={}% oreBlocks={} oreBreaks={} identityFailures={} "
				+ "nonOreStayedAir={} cooldownReady={} blockedAtZero={} blockedAtNineteen={} readyAtTwenty={}",
			rules.handlerPresent(), chancePerLevel, maxLevel, cooldownTicks, controlAir,
			regenerated.get(FIRST_LEVEL), RATE_SAMPLES, completed.get(FIRST_LEVEL),
			percent(regenerated.get(FIRST_LEVEL), RATE_SAMPLES),
			regenerated.get(SCALED_LEVEL), RATE_SAMPLES, completed.get(SCALED_LEVEL),
			percent(regenerated.get(SCALED_LEVEL), RATE_SAMPLES),
			oreBlocks.size(), oreBreaks, identityFailures, nonOreStayedAir,
			cooldownReady, blockedAtZero, blockedAtNineteen, readyAtTwenty
		);

		helper.assertTrue(rules.handlerPresent(), "the after-break Ore Regeneration handler is missing");
		helper.assertTrue(
			chancePerLevel == APPROVED_CHANCE_PER_LEVEL_PERCENT,
			"chance per level is " + chancePerLevel + " percent instead of " + APPROVED_CHANCE_PER_LEVEL_PERCENT
		);
		helper.assertTrue(cooldownTicks == RED_COOLDOWN_TICKS, "cooldown is not 20 ticks");
		helper.assertTrue(completed.get(FIRST_LEVEL) == RATE_SAMPLES, "level 1 sample breaks did not all complete");
		helper.assertTrue(completed.get(SCALED_LEVEL) == RATE_SAMPLES, "level " + SCALED_LEVEL + " sample breaks did not all complete");
		helper.assertTrue(
			withinFourStandardDeviations(regenerated.get(FIRST_LEVEL), RATE_SAMPLES, APPROVED_CHANCE_PER_LEVEL_PERCENT),
			"level 1 rate was " + regenerated.get(FIRST_LEVEL) + "/" + RATE_SAMPLES + ", expected "
				+ APPROVED_CHANCE_PER_LEVEL_PERCENT + " percent"
		);
		int expectedLevelThree = Math.min(100, APPROVED_CHANCE_PER_LEVEL_PERCENT * SCALED_LEVEL);
		helper.assertTrue(
			withinFourStandardDeviations(regenerated.get(SCALED_LEVEL), RATE_SAMPLES, expectedLevelThree),
			"level " + SCALED_LEVEL + " rate was " + regenerated.get(SCALED_LEVEL) + "/" + RATE_SAMPLES
				+ ", expected " + expectedLevelThree + " percent"
		);
		helper.assertTrue(identityFailures.isEmpty(), "ore identity changed: " + identityFailures);
		helper.assertTrue(nonOreStayedAir, "stone regenerated as " + level.getBlockState(target));
		helper.assertTrue(cooldownReady, "level 5 did not regenerate an ore when cooldown was ready");
		helper.assertTrue(blockedAtZero, "an ore regenerated at zero cooldown ticks");
		helper.assertTrue(blockedAtNineteen, "an ore regenerated before 20 cooldown ticks");
		helper.assertTrue(readyAtTwenty, "an ore did not regenerate at 20 cooldown ticks");
		helper.succeed();
	}

	@GameTest
	public void theDescriptionNamesTheApprovedChancePerLevel(final GameTestHelper helper) {
		String lang;
		try (java.io.InputStream stream = OreRegenerationGameTest.class.getResourceAsStream(
			"/assets/" + MOD_ID + "/lang/en_us.json"
		)) {
			lang = stream == null ? "" : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			lang = "";
		}

		String key = "\"enchantment." + MOD_ID + ".tool.ore_regeneration.desc\": \"";
		int start = lang.indexOf(key);
		String description = start < 0 ? "" : lang.substring(start + key.length(), lang.indexOf('"', start + key.length()));
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreRegenerationText RESULT description={} approvedPercent={}",
			description, APPROVED_CHANCE_PER_LEVEL_PERCENT
		);
		helper.assertTrue(
			description.contains(" " + APPROVED_CHANCE_PER_LEVEL_PERCENT + "% chance per level"),
			"the description reads '" + description + "' instead of naming " + APPROVED_CHANCE_PER_LEVEL_PERCENT + "% per level"
		);
		helper.succeed();
	}

	// Breaks a fresh ore REGROW_ATTEMPTS times with the clock held, and counts the regrowths.
	private static int regrowths(
		final GameTestHelper helper, final ServerPlayer miner, final ServerLevel level, final BlockPos target, final int clock
	) {
		int regrew = 0;
		for (int attempt = 0; attempt < REGROW_ATTEMPTS; attempt++) {
			runCommand(helper, miner, "scoreboard players set #clock ench_clock " + clock);
			level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
			regrew += miner.gameMode.destroyBlock(target) && level.getBlockState(target).is(Blocks.DIAMOND_ORE) ? 1 : 0;
		}

		return regrew;
	}

	private static List<Block> oreBlocks(final ServerLevel level) {
		Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		TagKey<Block> ores = TagKey.create(
			Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "ores")
		);
		List<Block> blocks = new ArrayList<>();
		registry.getTagOrEmpty(ores).forEach(holder -> blocks.add(holder.value()));
		blocks.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
		return blocks;
	}

	private static double percent(final int successes, final int samples) {
		return successes * 100.0 / samples;
	}

	private static boolean withinFourStandardDeviations(
		final int actual, final int samples, final int expectedPercent
	) {
		double probability = expectedPercent / 100.0;
		double expected = samples * probability;
		double deviation = Math.sqrt(samples * probability * (1.0 - probability));
		return Math.abs(actual - expected) <= 4.0 * deviation + 1.0;
	}

	private static HandlerRules handlerRules() {
		try {
			Class<?> handler = Class.forName(HANDLER_CLASS);
			Field chance = handler.getDeclaredField("CHANCE_PER_LEVEL_PERCENT");
			Field cooldown = handler.getDeclaredField("COOLDOWN_TICKS");
			chance.setAccessible(true);
			cooldown.setAccessible(true);
			return new HandlerRules(true, chance.getInt(null), cooldown.getInt(null));
		} catch (ReflectiveOperationException | LinkageError error) {
			return new HandlerRules(false, -1, RED_COOLDOWN_TICKS);
		}
	}

	private static ItemStack enchanted(final Registry<Enchantment> registry, final int level) {
		Holder<Enchantment> holder = registry.getOrThrow(ResourceKey.create(
			Registries.ENCHANTMENT,
			Identifier.fromNamespaceAndPath(MOD_ID, "tool/ore_regeneration")
		));
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(holder, level);
		EnchantmentHelper.setEnchantments(pickaxe, enchantments.toImmutable());
		return pickaxe;
	}

	private static void hold(final ServerPlayer player, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.getInventory().setSelectedItem(stack);
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		PacketRecorder.attach(player);
		return player;
	}

	private static void runCommand(
		final GameTestHelper helper, final ServerPlayer player, final String command
	) {
		CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withEntity(player)
			.withPosition(player.position())
			.withPermission(LevelBasedPermissionSet.GAMEMASTER)
			.withSuppressedOutput();
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
	}

	private static int score(final GameTestHelper helper, final ServerPlayer player, final String name) {
		var scoreboard = helper.getLevel().getServer().getScoreboard();
		Objective objective = scoreboard.getObjective(name);
		if (objective == null) {
			throw new AssertionError("scoreboard objective " + name + " is missing");
		}
		return scoreboard.getOrCreatePlayerScore(player, objective).get();
	}

	private record HandlerRules(boolean handlerPresent, int chancePerLevelPercent, int cooldownTicks) {
	}
}
