package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The tower's chests: they pair, they stay, and they hold what their weights say. */
public class TowerChestGameTest {
	private static final long SEED = 20_260_930L;
	/** Draws for the rate measure. */
	private static final int DRAWS = 50_000;
	/** Sigmas of a category's binomial spread a measured share may stray. */
	private static final double SIGMAS = 4.0;
	private static final int FLOOR = 40;

	private static BlockPos left(final GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(1, 2, 1));
	}

	private static BlockPos placed(final GameTestHelper helper) {
		BlockPos left = left(helper);
		TowerChests.placeDouble(helper.getLevel(), left, Direction.NORTH);
		return left;
	}

	private static Container containerAt(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return ChestBlock.getContainer((ChestBlock) state.getBlock(), state, level, pos, true);
	}

	private static int count(final Container container, final Item item) {
		int found = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				found++;
			}
		}
		return found;
	}

	private static int enchantedBooks(final Container container) {
		int found = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(Items.ENCHANTED_BOOK) && TowerChests.enchanted(stack)) {
				found++;
			}
		}
		return found;
	}

	/** A stand-in for the spells mod, whose items the gametest runtime does not hold. */
	private static TowerChests.SpellItems standIn(final boolean withReturnScroll) {
		return new TowerChests.SpellItems() {
			@Override
			public java.util.Optional<Item> returnScroll() {
				return withReturnScroll ? java.util.Optional.of(Items.PAPER) : java.util.Optional.empty();
			}

			@Override
			public List<Item> scrolls() {
				return List.of(Items.NAME_TAG);
			}

			@Override
			public List<Item> books() {
				return List.of(Items.WRITABLE_BOOK);
			}
		};
	}

	@GameTest
	public void aPlacedChestIsADoubleOfTowerChests(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos left = placed(helper);
		BlockPos right = left.relative(Direction.NORTH.getClockWise());
		BlockState a = level.getBlockState(left);
		BlockState b = level.getBlockState(right);
		Container container = containerAt(level, left);
		boolean valid = level.getBlockEntity(left) instanceof ChestBlockEntity && BlockEntityTypes.CHEST.isValid(a);
		TowerTestSupport.result("chestPairs", "left=" + ChestBlock.getBlockType(a) + " right=" + ChestBlock.getBlockType(b)
			+ " slots=" + container.getContainerSize() + " validForChestEntity=" + valid);
		helper.assertTrue(a.getBlock() == TowerBlocks.TOWER_CHEST && b.getBlock() == TowerBlocks.TOWER_CHEST, "the halves are " + a + " and " + b);
		helper.assertTrue(ChestBlock.getBlockType(a) != ChestBlock.getBlockType(b)
			&& ChestBlock.getBlockType(a) != DoubleBlockCombiner.BlockType.SINGLE && ChestBlock.getBlockType(b) != DoubleBlockCombiner.BlockType.SINGLE,
			"the halves are not paired: " + ChestBlock.getBlockType(a) + " and " + ChestBlock.getBlockType(b));
		helper.assertTrue(container.getContainerSize() == 2 * 27, "a double chest holds " + container.getContainerSize() + " slots");
		helper.assertTrue(valid, "the chest has no chest block entity");
		helper.succeed();
	}

	@GameTest
	public void aDoubleChestSurvivesAPowerSixBlast(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos left = placed(helper);
		BlockPos right = left.relative(Direction.NORTH.getClockWise());
		BlockPos control = helper.absolutePos(new BlockPos(3, 3, 2));
		level.setBlock(control, Blocks.DIRT.defaultBlockState(), 3);
		TowerChests.fill(level, left, FLOOR, false, RandomSource.create(SEED));
		BlockPos centre = helper.absolutePos(new BlockPos(3, 2, 1));
		level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 6.0F, Level.ExplosionInteraction.BLOCK);
		boolean dirtGone = level.getBlockState(control).isAir();
		boolean leftStands = level.getBlockState(left).getBlock() == TowerBlocks.TOWER_CHEST;
		boolean rightStands = level.getBlockState(right).getBlock() == TowerBlocks.TOWER_CHEST;
		TowerTestSupport.result("chestBlast", "power=6 controlDirtGone=" + dirtGone + " leftStands=" + leftStands + " rightStands=" + rightStands);
		helper.assertTrue(dirtGone, "the blast did not break the control block, so it proves nothing");
		helper.assertTrue(leftStands && rightStands, "a blast broke a chest half: left=" + leftStands + " right=" + rightStands);
		helper.succeed();
	}

	@GameTest
	public void aPlayerCannotMineAChest(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos left = placed(helper);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 4.5);
		BlockState state = level.getBlockState(left);
		float hardness = state.getDestroySpeed(level, left);
		float progress = state.getDestroyProgress(player, level, left);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
		float pickProgress = state.getDestroyProgress(player, level, left);
		player.gameMode.handleBlockBreakAction(left, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 1);
		player.gameMode.handleBlockBreakAction(left, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 2);
		boolean stands = level.getBlockState(left).getBlock() == TowerBlocks.TOWER_CHEST;
		TowerTestSupport.result("chestMining", "hardness=" + hardness + " handProgress=" + progress + " pickProgress=" + pickProgress + " stands=" + stands);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(hardness < 0.0F, "the chest has hardness " + hardness);
		helper.assertTrue(pickProgress == 0.0F, "a netherite pickaxe makes progress " + pickProgress);
		helper.assertTrue(stands, "a player's mining removed the chest");
		helper.succeed();
	}

	@GameTest
	public void aChestIsImmuneToWithersDragonsAndPistons(final GameTestHelper helper) {
		BlockState state = TowerBlocks.TOWER_CHEST.defaultBlockState();
		boolean wither = state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE);
		boolean dragon = state.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE);
		net.minecraft.world.level.material.PushReaction push = state.getPistonPushReaction();
		TowerTestSupport.result("chestImmunities", "witherImmune=" + wither + " dragonImmune=" + dragon + " push=" + push);
		helper.assertTrue(wither, "a wither can break a chest");
		helper.assertTrue(dragon, "the dragon can break a chest");
		helper.assertTrue(push == net.minecraft.world.level.material.PushReaction.IMMOVEABLE, "a piston can move a chest: " + push);
		helper.succeed();
	}

	/** Weights of each category, as the observed share of a seeded run must match within {@value #SIGMAS} sigmas. */
	@GameTest(maxTicks = 200)
	public void lootShareMatchesEachWeight(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(SEED);
		Map<TowerChests.Category, Integer> seen = new EnumMap<>(TowerChests.Category.class);
		for (int i = 0; i < DRAWS; i++) {
			seen.merge(TowerChests.roll(level, FLOOR, random).category(), 1, Integer::sum);
		}
		int weights = 0;
		StringBuilder line = new StringBuilder("draws=" + DRAWS);
		List<String> off = new ArrayList<>();
		for (TowerChests.Category category : TowerChests.Category.values()) {
			weights += category.weight;
			double p = category.weight / (double) TowerChests.TOTAL_WEIGHT;
			double expected = DRAWS * p;
			double tolerance = SIGMAS * Math.sqrt(DRAWS * p * (1.0 - p));
			int got = seen.getOrDefault(category, 0);
			line.append(' ').append(category).append('=').append(got).append("/exp").append(Math.round(expected));
			if (Math.abs(got - expected) > tolerance) {
				off.add(category + " saw " + got + ", expected " + expected + " +-" + tolerance);
			}
		}
		TowerTestSupport.result("lootRates", line.toString() + " netherite=" + seen.getOrDefault(TowerChests.Category.NETHERITE_GEAR, 0));
		helper.assertTrue(weights == TowerChests.TOTAL_WEIGHT, "the category weights sum to " + weights + ", not " + TowerChests.TOTAL_WEIGHT);
		helper.assertTrue(off.isEmpty(), "shares off their weights: " + off);
		helper.assertTrue(seen.getOrDefault(TowerChests.Category.NETHERITE_GEAR, 0) > 0, "no netherite gear in " + DRAWS + " draws");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aGuaranteedChestAlwaysHoldsABookAndAReturnScroll(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos left = placed(helper);
		Container container = containerAt(level, left);
		TowerChests.installSpellItems(standIn(true));
		int fills = 300;
		int withBook = 0;
		int withScroll = 0;
		int plainWithBook = 0;
		int plainWithScroll = 0;
		try {
			RandomSource random = RandomSource.create(SEED);
			for (int i = 0; i < fills; i++) {
				container.clearContent();
				TowerChests.fill(level, left, FLOOR, true, random);
				withBook += enchantedBooks(container) >= 1 ? 1 : 0;
				withScroll += count(container, Items.PAPER) >= 1 ? 1 : 0;
				container.clearContent();
				TowerChests.fill(level, left, FLOOR, false, random);
				plainWithBook += enchantedBooks(container) >= 1 ? 1 : 0;
				plainWithScroll += count(container, Items.PAPER) >= 1 ? 1 : 0;
			}
		} finally {
			TowerChests.installSpellItems(null);
		}
		TowerTestSupport.result("guaranteedChest", "fills=" + fills + " withBook=" + withBook + " withReturnScroll=" + withScroll
			+ " plainWithBook=" + plainWithBook + " plainWithReturnScroll=" + plainWithScroll);
		helper.assertTrue(withBook == fills, "a guaranteed chest lacked its enchanted book in " + (fills - withBook) + " of " + fills);
		helper.assertTrue(withScroll == fills, "a guaranteed chest lacked its return scroll in " + (fills - withScroll) + " of " + fills);
		helper.assertTrue(plainWithBook < fills && plainWithScroll < fills, "an ordinary chest always has both, so the guarantee proves nothing");
		helper.succeed();
	}

	@GameTest
	public void withoutTheSpellsModAGuaranteedChestStillHoldsItsBook(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos left = placed(helper);
		Container container = containerAt(level, left);
		boolean absent = TowerChests.REGISTRY.returnScroll().isEmpty() && TowerChests.REGISTRY.scrolls().isEmpty();
		List<ItemStack> put = TowerChests.fill(level, left, FLOOR, true, RandomSource.create(SEED));
		boolean spellbookFromTag = TowerChests.REGISTRY.books().contains(Items.BOOK);
		TowerTestSupport.result("chestNoSpellsMod", "spellsItemsAbsent=" + absent + " stacks=" + put.size() + " books=" + enchantedBooks(container)
			+ " spellbookTagResolves=" + spellbookFromTag);
		helper.assertTrue(absent, "the spells mod's items are present, so this proves nothing");
		helper.assertTrue(enchantedBooks(container) >= 1, "no enchanted book without the spells mod");
		helper.assertTrue(spellbookFromTag, "the spellbook tag did not resolve to the test's stand-in book");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void enchantedGearIsEnchanted(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(SEED);
		int n = 200;
		int diamond = 0;
		int netherite = 0;
		int book = 0;
		for (int i = 0; i < n; i++) {
			ItemStack d = TowerChests.stackFor(level, TowerChests.Category.DIAMOND_GEAR, FLOOR, random);
			ItemStack z = TowerChests.stackFor(level, TowerChests.Category.NETHERITE_GEAR, FLOOR, random);
			ItemStack b = TowerChests.stackFor(level, TowerChests.Category.ENCHANTED_BOOK, FLOOR, random);
			diamond += TowerChests.enchanted(d) && d.getItem().toString().contains("diamond") ? 1 : 0;
			netherite += TowerChests.enchanted(z) && z.getItem().toString().contains("netherite") ? 1 : 0;
			book += TowerChests.enchanted(b) ? 1 : 0;
		}
		TowerTestSupport.result("enchantedGear", "n=" + n + " diamondEnchanted=" + diamond + " netheriteEnchanted=" + netherite + " booksEnchanted=" + book);
		helper.assertTrue(diamond == n, "diamond gear enchanted " + diamond + " of " + n);
		helper.assertTrue(netherite == n, "netherite gear enchanted " + netherite + " of " + n);
		helper.assertTrue(book == n, "books enchanted " + book + " of " + n);
		helper.succeed();
	}
}
