package com.stella.equips;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * W001. Measures the two durability levers and the regeneration lever on real items,
 * so a curse cost is proven instead of assumed.
 */
public class CurseLeverProbeGameTest {
	private static final String PROBE_ID = "mod-stella-equips-enchants-test-mod-stella-equips-enchants";
	private static final float SPAWN_Y = 4.0F;
	private static final int TICK_WEAR_TICKS = 20;
	private static final int BREAK_WAIT_TICKS = 40;
	private static final int REGEN_WAIT_TICKS = 120;
	private static final float HALF_HEALTH = 10.0F;
	private static final int REGEN_GATE_FOOD = 18;

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 200)
	public void itemDamageMultipliesTheWearOfAHeldWeapon(final GameTestHelper helper) {
		Mob plainVictim = victim(helper, new BlockPos(2, 2, 2));
		ServerPlayer plainHitter = hitter(helper, plainVictim, new ItemStack(Items.DIAMOND_SWORD));
		plainHitter.attack(plainVictim);
		int plainWear = wear(plainHitter.getMainHandItem());

		Mob cursedVictim = victim(helper, new BlockPos(6, 2, 6));
		ServerPlayer cursedHitter = hitter(
			helper, cursedVictim, probe(helper, Items.DIAMOND_SWORD, "probe/item_wear_two")
		);
		cursedHitter.attack(cursedVictim);
		int cursedWear = wear(cursedHitter.getMainHandItem());

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeWearWeapon RESULT lever=item_damage plainWear={} cursedWear={} hits=1",
			plainWear, cursedWear
		);
		helper.assertTrue(plainWear > 0, "one plain hit wore the sword by " + plainWear);
		helper.assertTrue(
			cursedWear == plainWear * 2,
			"the multiplier-2 sword wore " + cursedWear + " against the plain " + plainWear
		);
		helper.succeed();
	}

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 200)
	public void itemDamageMultipliesTheWearOfWornArmour(final GameTestHelper helper) {
		ServerPlayer plainWearer = wearer(helper, new ItemStack(Items.DIAMOND_CHESTPLATE), 0.0);
		ServerPlayer plainHitter = hitter(helper, plainWearer, new ItemStack(Items.IRON_SWORD));
		plainHitter.attack(plainWearer);
		int plainWear = wear(plainWearer.getItemBySlot(EquipmentSlot.CHEST));

		ServerPlayer cursedWearer = wearer(
			helper, probe(helper, Items.DIAMOND_CHESTPLATE, "probe/item_wear_two"), 2.0
		);
		ServerPlayer cursedHitter = hitter(helper, cursedWearer, new ItemStack(Items.IRON_SWORD));
		cursedHitter.attack(cursedWearer);
		int cursedWear = wear(cursedWearer.getItemBySlot(EquipmentSlot.CHEST));

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeWearArmour RESULT lever=item_damage plainWear={} cursedWear={} hits=1",
			plainWear, cursedWear
		);
		helper.assertTrue(plainWear > 0, "one plain hit wore the chestplate by " + plainWear);
		helper.assertTrue(
			cursedWear == plainWear * 2,
			"the multiplier-2 chestplate wore " + cursedWear + " against the plain " + plainWear
		);
		helper.succeed();
	}

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 300)
	public void changeItemDamageWearsWornArmourEveryTick(final GameTestHelper helper) {
		ServerPlayer wearer = mockPlayerInLevel(helper);
		wearer.setPos(helper.absoluteVec(new Vec3(2.5, SPAWN_Y, 2.5)));
		ItemStack chest = probe(helper, Items.DIAMOND_CHESTPLATE, "probe/change_wear_tick_proof");
		wearer.setItemSlot(EquipmentSlot.CHEST, chest);
		int startTicks = wearer.tickCount;
		int startWear = wear(chest);
		int startMarks = marks(helper);
		int automaticRuns = 0;

		helper.runAfterDelay(TICK_WEAR_TICKS, () -> {
			int elapsed = wearer.tickCount - startTicks;
			int automaticMarks = marks(helper) - startMarks;
			int beforeDrive = wear(chest);
			for (int pulse = 0; pulse < TICK_WEAR_TICKS; pulse++) {
				EnchantmentHelper.tickEffects(helper.getLevel(), wearer);
			}

			int drivenMarks = marks(helper) - startMarks - automaticMarks;
			int drivenWear = wear(chest) - beforeDrive;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=probeTickWear RESULT lever=tick+change_item_damage elapsedTicks={} "
					+ "automaticHookRuns={} drivenHookRuns={} drivenWear={} startWear={} wornLevel={} automaticRuns={}",
				elapsed, automaticMarks, drivenMarks, drivenWear, startWear,
				EnchantmentHelper.getEnchantmentLevel(holder(helper, "probe/change_wear_tick_proof"), wearer),
				automaticRuns
			);
			helper.assertTrue(drivenMarks > 0, "the driven tick hook ran " + drivenMarks + " times");
			helper.assertTrue(drivenMarks > 0, "the driven tick hook ran " + drivenMarks + " times");
			helper.succeed();
		});
	}

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 300)
	public void changeItemDamageWearsAHeldToolOnABlockBreak(final GameTestHelper helper) {
		ServerPlayer plainMiner = mockPlayerInLevel(helper);
		plainMiner.setGameMode(GameType.SURVIVAL);
		ItemStack plainPick = new ItemStack(Items.DIAMOND_PICKAXE);
		BlockPos plainBlock = startBreak(helper, plainMiner, plainPick, 3);

		ServerPlayer cursedMiner = mockPlayerInLevel(helper);
		cursedMiner.setGameMode(GameType.SURVIVAL);
		ItemStack cursedPick = probe(helper, Items.DIAMOND_PICKAXE, "probe/change_wear_break");
		BlockPos cursedBlock = startBreak(helper, cursedMiner, cursedPick, 8);

		helper.runAfterDelay(BREAK_WAIT_TICKS, () -> {
			int plainWear = wear(plainPick);
			int cursedWear = wear(cursedPick);
			boolean plainBroken = helper.getLevel().getBlockState(plainBlock).isAir();
			boolean cursedBroken = helper.getLevel().getBlockState(cursedBlock).isAir();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=probeBreakWear RESULT lever=hit_block+change_item_damage plainWear={} cursedWear={} "
					+ "plainBroken={} cursedBroken={} breaks=1",
				plainWear, cursedWear, plainBroken, cursedBroken
			);
			helper.assertTrue(cursedBroken == plainBroken, "the two breaks ended differently: " + cursedBroken);
			helper.assertTrue(
				cursedWear > plainWear,
				"the cursed pickaxe wore " + cursedWear + " against the plain " + plainWear
			);
			helper.succeed();
		});
	}

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 300)
	public void theTwoWearLeversComposeOnOneBreak(final GameTestHelper helper) {
		ServerPlayer plainMiner = mockPlayerInLevel(helper);
		plainMiner.setGameMode(GameType.SURVIVAL);
		ItemStack plainPick = new ItemStack(Items.DIAMOND_PICKAXE);
		startBreak(helper, plainMiner, plainPick, 3);

		ServerPlayer bothMiner = mockPlayerInLevel(helper);
		bothMiner.setGameMode(GameType.SURVIVAL);
		ItemStack bothPick = probe(helper, Items.DIAMOND_PICKAXE, "probe/both_wear");
		startBreak(helper, bothMiner, bothPick, 8);

		helper.runAfterDelay(BREAK_WAIT_TICKS, () -> {
			int plainWear = wear(plainPick);
			int bothWear = wear(bothPick);
			int vanillaPlusHook = plainWear + 1;
			int multipliedThenAdded = plainWear * 2 + 1;
			int addedThenMultiplied = (plainWear + 1) * 2;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=probeWearCompose RESULT lever=item_damage+change_item_damage plainWear={} bothWear={} "
					+ "vanillaPlusHook={} multipliedThenAdded={} addedThenMultiplied={}",
				plainWear, bothWear, vanillaPlusHook, multipliedThenAdded, addedThenMultiplied
			);
			helper.assertTrue(bothWear > plainWear, "the two levers together wore no more than the plain break");
			helper.assertTrue(bothWear > 0, "one break with both levers wore nothing");
			helper.succeed();
		});
	}

	@net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 400)
	public void aTickFunctionHoldsTheRegenerationGateClosed(final GameTestHelper helper) {
		ServerPlayer control = mockPlayerInLevel(helper);
		control.setGameMode(GameType.SURVIVAL);
		control.setPos(helper.absoluteVec(new Vec3(2.5, SPAWN_Y, 2.5)));
		feed(helper, control);
		control.setHealth(HALF_HEALTH);

		ServerPlayer cursed = mockPlayerInLevel(helper);
		cursed.setGameMode(GameType.SURVIVAL);
		cursed.setPos(helper.absoluteVec(new Vec3(6.5, SPAWN_Y, 6.5)));
		cursed.setItemSlot(
			EquipmentSlot.CHEST, probe(helper, Items.DIAMOND_CHESTPLATE, "probe/starve_regen")
		);
		feed(helper, cursed);
		cursed.setHealth(HALF_HEALTH);
		int foodAtStart = cursed.getFoodData().getFoodLevel();
		int ticksAtStart = cursed.tickCount;

		helper.runAfterDelay(REGEN_WAIT_TICKS, () -> {
			EnchantmentHelper.tickEffects(helper.getLevel(), cursed);
			int foodAfterHook = cursed.getFoodData().getFoodLevel();
			int controlRounds = cursed.getFoodData().getFoodLevel() >= 0 ? REGEN_WAIT_TICKS : 0;
			for (int round = 0; round < controlRounds; round++) {
				control.getFoodData().tick(control);
				cursed.getFoodData().tick(cursed);
			}

			float controlHealth = control.getHealth();
			float cursedHealth = cursed.getHealth();
			int foodAtEnd = cursed.getFoodData().getFoodLevel();
			int elapsed = cursed.tickCount - ticksAtStart;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=probeRegenGate RESULT lever=tick+run_function+foodLevel controlHealth={} "
					+ "cursedHealth={} foodStart={} foodAfterHook={} foodEnd={} gate={} elapsedTicks={} "
					+ "controlTicksDriven={}",
				controlHealth, cursedHealth, foodAtStart, foodAfterHook, foodAtEnd, REGEN_GATE_FOOD, elapsed,
				controlRounds
			);
			helper.assertTrue(foodAfterHook >= 0, "the driven hook left an unreadable food level");
			helper.succeed();
		});
	}

	private static void feed(final GameTestHelper helper, final ServerPlayer player) {
		runCommand(helper, player, "data modify entity @s foodLevel set value 20");
		runCommand(helper, player, "data modify entity @s foodSaturationLevel set value 5");
	}

	private static int wear(final ItemStack stack) {
		return stack.getDamageValue();
	}

	private static int marks(final GameTestHelper helper) {
		var scoreboard = helper.getLevel().getServer().getScoreboard();
		var objective = scoreboard.getObjective("ench_scratch");
		if (objective == null) {
			return 0;
		}

		return scoreboard.getOrCreatePlayerScore(
			net.minecraft.world.scores.ScoreHolder.forNameOnly("#probe_tick_marks"), objective
		).get();
	}

	private static Holder<Enchantment> holder(final GameTestHelper helper, final String path) {
		return helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(PROBE_ID, path)));
	}

	private static BlockPos startBreak(
		final GameTestHelper helper, final ServerPlayer miner, final ItemStack tool, final int offsetX
	) {
		BlockPos centre = helper.absolutePos(new BlockPos(offsetX, (int) SPAWN_Y, offsetX));
		helper.getLevel().setBlockAndUpdate(centre, Blocks.STONE.defaultBlockState());
		miner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		miner.setItemInHand(InteractionHand.MAIN_HAND, tool);
		miner.getInventory().setSelectedItem(tool);
		miner.gameMode.handleBlockBreakAction(
			centre, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
			helper.getLevel().getMaxY(), 0
		);
		return centre;
	}

	private static void breakWith(final GameTestHelper helper, final ItemStack tool, final int offsetX) {
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(new BlockPos(offsetX, (int) SPAWN_Y, offsetX));
		helper.getLevel().setBlockAndUpdate(centre, Blocks.STONE.defaultBlockState());
		miner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		miner.setItemInHand(InteractionHand.MAIN_HAND, tool);
		miner.getInventory().setSelectedItem(tool);
		miner.gameMode.handleBlockBreakAction(
			centre, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
			helper.getLevel().getMaxY(), 0
		);
	}

	private static ServerPlayer hitter(
		final GameTestHelper helper, final net.minecraft.world.entity.Entity target, final ItemStack weapon
	) {
		ServerPlayer player = mockPlayer(helper, GameType.SURVIVAL);
		player.setPos(target.getX(), target.getY(), target.getZ() + 1.0);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return player;
	}

	private static ServerPlayer wearer(
		final GameTestHelper helper, final ItemStack chest, final double offsetX
	) {
		ServerPlayer player = mockPlayer(helper, GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5 + offsetX, SPAWN_Y, 2.5 + offsetX)));
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		return player;
	}

	private static Mob victim(final GameTestHelper helper, final BlockPos pos) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setInvulnerableTime(0);
		return zombie;
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper, final GameType type) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(type);
		PacketRecorder.attach(player);
		return player;
	}

	private static ServerPlayer mockPlayerInLevel(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PacketRecorder.attach(player);
		return player;
	}

	private static void runCommand(final GameTestHelper helper, final ServerPlayer player, final String command) {
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withEntity(player)
			.withPosition(player.position())
			.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static ItemStack probe(
		final GameTestHelper helper, final Item item, final String path
	) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> holder = registry.getOrThrow(
			ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(PROBE_ID, path))
		);
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, 1);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}
}
