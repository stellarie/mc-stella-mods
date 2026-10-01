package com.stella.rpg;

import com.stella.rpg.tomb.TombstoneBlock;
import com.stella.rpg.tomb.TombstoneBlockEntity;
import com.stella.rpg.tomb.Tombstones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class TombstoneGameTest {
	private static final float LETHAL_DAMAGE = 1000.0F;
	private static final int SEARCH = Tombstones.SEARCH_RADIUS;
	private static final List<Item> BULK = List.of(
		Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.BIRCH_LOG, Items.COAL, Items.IRON_INGOT,
		Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.APPLE, Items.BREAD, Items.ARROW, Items.STICK
	);

	@GameTest(maxTicks = 100)
	public void picksFiveToEight(final GameTestHelper helper) {
		List<Integer> sizes = new ArrayList<>();
		for (int round = 0; round < 6; round++) {
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			fillTwentyStacks(player);
			Map<String, Integer> before = totals(player.getInventory());
			BlockPos death = player.blockPosition();
			ServerPlayer reborn = dieAndRespawn(helper, player);
			TombstoneBlockEntity tomb = tombstoneOf(helper.getLevel(), death, player.getUUID());
			helper.assertTrue(tomb != null, "no tombstone for round " + round);
			int size = tomb.contents().size();
			helper.assertTrue(size >= 5 && size <= 8, "tombstone holds " + size + " stacks");
			Map<String, Integer> after = totals(reborn.getInventory());
			merge(after, tomb.contents());
			helper.assertTrue(before.equals(after), "before " + before + " after " + after);
			int kept = nonEmptyStacks(reborn.getInventory());
			helper.assertTrue(kept + size == 20, "kept " + kept + " tomb " + size);
			sizes.add(size);
		}
		RpgTestSupport.result("picksFiveToEight", "sizes=" + sizes);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void pickSizeCoversFiveToEight(final GameTestHelper helper) {
		List<Integer> candidates = new ArrayList<>();
		for (int slot = 0; slot < 21; slot++) {
			candidates.add(slot);
		}
		RandomSource random = RandomSource.create(42L);
		Set<Integer> seen = new HashSet<>();
		for (int draw = 0; draw < 200; draw++) {
			List<Integer> picked = Tombstones.randomPick(candidates, random);
			helper.assertTrue(new HashSet<>(picked).size() == picked.size(), "duplicate slot in " + picked);
			helper.assertTrue(candidates.containsAll(picked), "slot outside candidates in " + picked);
			seen.add(picked.size());
		}
		helper.assertTrue(seen.equals(Set.of(5, 6, 7, 8)), "sizes seen " + seen);
		List<Integer> few = Tombstones.randomPick(List.of(3, 9, 12), random);
		helper.assertTrue(new HashSet<>(few).equals(Set.of(3, 9, 12)), "few " + few);
		RpgTestSupport.result("pickSizeCoversFiveToEight", "sizes=" + seen);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void fewStacksAllTaken(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		BlockPos death = player.blockPosition();
		ServerPlayer reborn = dieAndRespawn(helper, player);
		TombstoneBlockEntity tomb = tombstoneOf(helper.getLevel(), death, player.getUUID());
		helper.assertTrue(tomb != null, "no tombstone");
		helper.assertTrue(tomb.contents().size() == 3, "tombstone holds " + tomb.contents().size());
		helper.assertTrue(nonEmptyStacks(reborn.getInventory()) == 0, "reborn holds " + totals(reborn.getInventory()));
		RpgTestSupport.result("fewStacksAllTaken", "tomb=" + tomb.contents().size());
		helper.succeed();
	}

	/** A death with a tombstone gives one Grave Compass aimed at the tomb; a second death retargets the same compass. */
	@GameTest(maxTicks = 60)
	public void graveCompassPointsToTomb(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		BlockPos death = player.blockPosition();
		ServerPlayer reborn = dieAndRespawn(helper, player);
		BlockPos tomb = tombstonePos(helper.getLevel(), death, player.getUUID());
		ItemStack first = graveCompass(reborn);
		helper.assertTrue(tomb != null && first != null, "no tomb or no compass: tomb=" + tomb + " compass=" + first);
		helper.assertTrue(first.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER).target().map(target -> target.pos().equals(tomb)).orElse(false),
			"the compass points to " + first.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER) + ", not " + tomb);
		// A later death sets a new tomb; giving again must re-aim the compass the player already carries.
		BlockPos secondTomb = tomb.offset(5, 0, 5);
		reborn.setAttached(com.stella.rpg.tomb.GraveCompass.LAST_TOMB, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), secondTomb));
		com.stella.rpg.tomb.GraveCompass.give(reborn, reborn);
		ServerPlayer again = reborn;
		long compasses = java.util.stream.IntStream.range(0, again.getInventory().getContainerSize())
			.filter(slot -> com.stella.rpg.tomb.GraveCompass.isGraveCompass(again.getInventory().getItem(slot))).count();
		ItemStack second = graveCompass(again);
		RpgTestSupport.result("graveCompass", "tomb=" + tomb + " secondTomb=" + secondTomb + " compasses=" + compasses);
		helper.assertTrue(compasses == 1, "the player carries " + compasses + " grave compasses");
		helper.assertTrue(second.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER).target()
			.map(target -> target.pos().equals(secondTomb)).orElse(false), "the compass was not retargeted to " + secondTomb);
		helper.succeed();
	}

	private static ItemStack graveCompass(final ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (com.stella.rpg.tomb.GraveCompass.isGraveCompass(player.getInventory().getItem(slot))) {
				return player.getInventory().getItem(slot);
			}
		}
		return null;
	}

	@GameTest(maxTicks = 40)
	public void equippedCanBePicked(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		int chestSlot = EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE);
		BlockPos death = player.blockPosition();
		Tombstones.setPicker((candidates, random) -> List.of(chestSlot));
		ServerPlayer reborn;
		try {
			reborn = dieAndRespawn(helper, player);
		} finally {
			Tombstones.resetPicker();
		}
		TombstoneBlockEntity tomb = tombstoneOf(helper.getLevel(), death, player.getUUID());
		helper.assertTrue(tomb != null, "no tombstone");
		helper.assertTrue(tomb.contents().size() == 1 && tomb.contents().getFirst().is(Items.IRON_CHESTPLATE),
			"tombstone holds " + tomb.contents());
		helper.assertTrue(reborn.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "chestplate is worn twice");
		helper.assertTrue(nonEmptyStacks(reborn.getInventory()) == 19, "reborn holds " + nonEmptyStacks(reborn.getInventory()));
		RpgTestSupport.result("equippedCanBePicked", "tomb=" + tomb.contents());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void excludedNeverPicked(final GameTestHelper helper) {
		for (int round = 0; round < 3; round++) {
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			player.getInventory().setItem(0, new ItemStack(Items.BEDROCK, 2));
			for (int slot = 1; slot <= 5; slot++) {
				player.getInventory().setItem(slot, new ItemStack(BULK.get(slot), slot));
			}
			helper.assertTrue(player.getInventory().getItem(0).is(Tombstones.NEVER_TOMBSTONE), "bedrock is not in the tag");
			BlockPos death = player.blockPosition();
			ServerPlayer reborn = dieAndRespawn(helper, player);
			TombstoneBlockEntity tomb = tombstoneOf(helper.getLevel(), death, player.getUUID());
			helper.assertTrue(tomb != null, "no tombstone");
			helper.assertTrue(tomb.contents().stream().noneMatch(stack -> stack.is(Items.BEDROCK)), "bedrock in the tombstone");
			helper.assertTrue(tomb.contents().size() == 5, "tombstone holds " + tomb.contents().size());
			helper.assertTrue(reborn.getInventory().countItem(Items.BEDROCK) == 2, "bedrock lost");
		}
		RpgTestSupport.result("excludedNeverPicked", "bedrock stayed in 3 of 3 deaths");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void keepInventoryUnchanged(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean original = level.getGameRules().get(GameRules.KEEP_INVENTORY);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		Map<String, Integer> before = totals(player.getInventory());
		BlockPos death = player.blockPosition();
		ServerPlayer reborn;
		level.getGameRules().set(GameRules.KEEP_INVENTORY, true, level.getServer());
		try {
			reborn = dieAndRespawn(helper, player);
		} finally {
			level.getGameRules().set(GameRules.KEEP_INVENTORY, original, level.getServer());
		}
		helper.assertTrue(tombstoneOf(level, death, player.getUUID()) == null, "a tombstone appeared");
		helper.assertTrue(before.equals(totals(reborn.getInventory())), "before " + before + " after " + totals(reborn.getInventory()));
		helper.assertTrue(nonEmptyStacks(reborn.getInventory()) == 20, "reborn holds " + nonEmptyStacks(reborn.getInventory()));
		RpgTestSupport.result("keepInventoryUnchanged", "stacks=" + nonEmptyStacks(reborn.getInventory()));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void accessWindow(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		BlockPos relative = new BlockPos(2, 1, 2);
		BlockPos pos = helper.absolutePos(relative);
		long now = level.getGameTime();

		buildTomb(helper, relative, owner.getUUID(), now);
		helper.useBlock(relative, owner);
		helper.assertTrue(owner.containerMenu != owner.inventoryMenu, "the owner could not open at 0 ticks");
		owner.closeContainer();

		buildTomb(helper, relative, owner.getUUID(), now - 11999L);
		helper.useBlock(relative, stranger);
		helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "a stranger opened at 11999 ticks");

		buildTomb(helper, relative, owner.getUUID(), now - 12000L);
		helper.useBlock(relative, stranger);
		helper.assertTrue(stranger.containerMenu != stranger.inventoryMenu, "a stranger could not open at 12000 ticks");
		helper.assertTrue(level.getBlockState(pos).is(TombstoneBlock.BLOCK), "the tombstone left");
		RpgTestSupport.result("accessWindow", "owner=0 stranger=11999 refused, 12000 opened");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void emptyBreaks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		BlockPos death = player.blockPosition();
		ServerPlayer reborn = dieAndRespawn(helper, player);
		BlockPos tombPos = tombstonePos(level, death, player.getUUID());
		helper.assertTrue(tombPos != null, "no tombstone");
		TombstoneBlockEntity tomb = (TombstoneBlockEntity) level.getBlockEntity(tombPos);
		UUID nameId = tomb.nameDisplayId();
		UUID headId = tomb.headDisplayId();
		AABB around = new AABB(tombPos).inflate(2.0);
		List<Display.TextDisplay> labels = level.getEntitiesOfClass(Display.TextDisplay.class, around, entity -> entity.entityTags().contains(Tombstones.TAG));
		List<Display.ItemDisplay> heads = level.getEntitiesOfClass(Display.ItemDisplay.class, around, entity -> entity.entityTags().contains(Tombstones.TAG));
		helper.assertTrue(labels.size() == 1 && heads.size() == 1, "displays " + labels.size() + " " + heads.size());
		helper.assertTrue(labels.getFirst().getText().getString().equals("rpg-player"), "label " + labels.getFirst().getText().getString());
		helper.assertTrue(labels.getFirst().getY() == tombPos.getY() + 1.6, "label y " + labels.getFirst().getY());
		ItemStack skull = heads.getFirst().getItemStack();
		helper.assertTrue(skull.is(Items.PLAYER_HEAD) && skull.get(DataComponents.PROFILE) != null, "head " + skull);
		helper.assertTrue(heads.getFirst().getY() == tombPos.getY() + 1.2, "head y " + heads.getFirst().getY());
		helper.assertTrue(nameId.equals(labels.getFirst().getUUID()) && headId.equals(heads.getFirst().getUUID()), "display ids differ");

		List<ItemStack> held = tomb.contents();
		helper.assertTrue(held.size() >= 5 && held.size() <= 8, "held " + held.size());
		takeEverything(helper, reborn, tombPos);
		helper.assertTrue(level.getBlockState(tombPos).isAir(), "the block stayed: " + level.getBlockState(tombPos));
		helper.assertTrue(level.getEntity(nameId) == null, "the label stayed");
		helper.assertTrue(level.getEntity(headId) == null, "the head stayed");
		helper.assertTrue(level.getEntitiesOfClass(Display.class, around, entity -> entity.entityTags().contains(Tombstones.TAG)).isEmpty(),
			"a tagged display stayed");
		Map<String, Integer> taken = totals(reborn.getInventory());
		Map<String, Integer> expected = new HashMap<>();
		merge(expected, held);
		helper.assertTrue(taken.equals(expected), "taken " + taken + " expected " + expected);
		RpgTestSupport.result("emptyBreaks", "taken=" + held.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void unbreakable(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		BlockPos death = player.blockPosition();
		ServerPlayer reborn = dieAndRespawn(helper, player);
		reborn.getAbilities().invulnerable = true;
		BlockPos tombPos = tombstonePos(level, death, player.getUUID());
		helper.assertTrue(tombPos != null, "no tombstone");
		TombstoneBlockEntity tomb = (TombstoneBlockEntity) level.getBlockEntity(tombPos);
		List<ItemStack> held = tomb.contents();
		moveNear(reborn, tombPos);

		level.explode(null, tombPos.getX() + 0.5, tombPos.getY() + 0.5, tombPos.getZ() + 0.5, 8.0F, Level.ExplosionInteraction.TNT);
		assertIntact(helper, level, tombPos, held, "explosion");

		reborn.gameMode.handleBlockBreakAction(tombPos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 1);
		reborn.gameMode.handleBlockBreakAction(tombPos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 2);
		assertIntact(helper, level, tombPos, held, "survival break");
		helper.assertTrue(level.getBlockState(tombPos).getDestroySpeed(level, tombPos) < 0.0F, "destroy time is not negative");
		helper.assertTrue(level.getBlockState(tombPos).getPistonPushReaction() == PushReaction.IMMOVEABLE, "a piston can move it");
		RpgTestSupport.result("unbreakable", "held=" + held.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void placementFallback(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		Map<String, Integer> before = totals(player.getInventory());
		BlockPos death = player.blockPosition();
		Map<BlockPos, BlockState> replaced = new HashMap<>();
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dy = -SEARCH; dy <= SEARCH; dy++) {
				for (int dz = -SEARCH; dz <= SEARCH; dz++) {
					BlockPos pos = death.offset(dx, dy, dz);
					if (dx * dx + dy * dy + dz * dz <= SEARCH * SEARCH && level.isLoaded(pos) && level.isInWorldBounds(pos)
						&& level.getBlockState(pos).canBeReplaced()) {
						replaced.put(pos, level.getBlockState(pos));
						level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
					}
				}
			}
		}
		AABB around = new AABB(death).inflate(SEARCH + 4);
		List<ItemEntity> dropped;
		ServerPlayer reborn;
		try {
			reborn = dieAndRespawn(helper, player);
			dropped = level.getEntitiesOfClass(ItemEntity.class, around);
			helper.assertTrue(tombstonePos(level, death, player.getUUID()) == null, "a tombstone appeared in a sealed area");
			int droppedStacks = dropped.size();
			helper.assertTrue(droppedStacks >= 5 && droppedStacks <= 8, "dropped " + droppedStacks + " stacks");
			Map<String, Integer> after = totals(reborn.getInventory());
			for (ItemEntity entity : dropped) {
				merge(after, List.of(entity.getItem()));
			}
			helper.assertTrue(before.equals(after), "before " + before + " after " + after);
			RpgTestSupport.result("placementFallback", "dropped=" + droppedStacks);
		} finally {
			for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, around)) {
				entity.discard();
			}
			replaced.forEach((pos, state) -> level.setBlock(pos, state, 2));
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void noInsert(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos relative = new BlockPos(2, 1, 2);
		buildTomb(helper, relative, player.getUUID(), level.getGameTime());
		TombstoneBlockEntity tomb = (TombstoneBlockEntity) level.getBlockEntity(helper.absolutePos(relative));
		int before = tomb.contents().size();
		helper.useBlock(relative, player);
		AbstractContainerMenu menu = player.containerMenu;
		helper.assertTrue(menu != player.inventoryMenu, "the tombstone did not open");
		helper.assertTrue(menu.slots.size() == 9 + 36, "slots " + menu.slots.size());

		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
		int carriedSlot = 9 + 27;
		menu.setCarried(new ItemStack(Items.EMERALD, 7));
		for (int slot = 0; slot < 9; slot++) {
			menu.clicked(slot, 0, ContainerInput.PICKUP, player);
		}
		menu.quickMoveStack(player, carriedSlot);
		menu.clicked(carriedSlot, 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(menu.getCarried().is(Items.EMERALD) && menu.getCarried().getCount() == 7, "carried " + menu.getCarried());
		helper.assertTrue(tomb.contents().size() == before, "contents changed to " + tomb.contents().size());
		helper.assertTrue(tomb.contents().stream().noneMatch(stack -> stack.is(Items.EMERALD) || stack.getCount() == 5),
			"an inserted item is inside");
		helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "the inventory stack moved");
		RpgTestSupport.result("noInsert", "contents=" + tomb.contents().size());
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void persists(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		fillTwentyStacks(player);
		BlockPos death = player.blockPosition();
		dieAndRespawn(helper, player);
		BlockPos tombPos = tombstonePos(level, death, player.getUUID());
		helper.assertTrue(tombPos != null, "no tombstone");
		TombstoneBlockEntity tomb = (TombstoneBlockEntity) level.getBlockEntity(tombPos);
		BlockEntity loaded = BlockEntity.loadStatic(tombPos, level.getBlockState(tombPos), tomb.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		helper.assertTrue(loaded instanceof TombstoneBlockEntity, "loaded " + loaded);
		TombstoneBlockEntity again = (TombstoneBlockEntity) loaded;
		helper.assertTrue(again.ownerId().equals(player.getUUID()), "owner " + again.ownerId());
		helper.assertTrue(again.ownerName().equals("rpg-player"), "name " + again.ownerName());
		helper.assertTrue(again.deathTime() == tomb.deathTime(), "death time " + again.deathTime() + " vs " + tomb.deathTime());
		helper.assertTrue(tomb.nameDisplayId().equals(again.nameDisplayId()) && tomb.headDisplayId().equals(again.headDisplayId()), "display ids");
		List<ItemStack> was = tomb.contents();
		List<ItemStack> now = again.contents();
		helper.assertTrue(was.size() == now.size(), "size " + now.size() + " vs " + was.size());
		for (int i = 0; i < was.size(); i++) {
			helper.assertTrue(ItemStack.matches(was.get(i), now.get(i)), "stack " + i + " " + now.get(i) + " vs " + was.get(i));
		}
		RpgTestSupport.result("persists", "stacks=" + now.size() + " deathTime=" + again.deathTime());
		helper.succeed();
	}

	private static void fillTwentyStacks(final ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < BULK.size(); slot++) {
			inventory.setItem(slot, new ItemStack(BULK.get(slot), 1 + slot * 3));
		}
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
	}

	private static Map<String, Integer> totals(final Inventory inventory) {
		Map<String, Integer> totals = new HashMap<>();
		List<ItemStack> all = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (!com.stella.rpg.tomb.GraveCompass.isGraveCompass(inventory.getItem(slot))) {
				all.add(inventory.getItem(slot));
			}
		}
		merge(totals, all);
		return totals;
	}

	private static void merge(final Map<String, Integer> totals, final List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
	}

	private static int nonEmptyStacks(final Inventory inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			// The grave compass is the mod's gift, not one of the player's own stacks.
			if (!inventory.getItem(slot).isEmpty() && !com.stella.rpg.tomb.GraveCompass.isGraveCompass(inventory.getItem(slot))) {
				count++;
			}
		}
		return count;
	}

	private static BlockPos tombstonePos(final ServerLevel level, final BlockPos center, final UUID owner) {
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dy = -SEARCH; dy <= SEARCH; dy++) {
				for (int dz = -SEARCH; dz <= SEARCH; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity tomb && tomb.ownerId().equals(owner)) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	static TombstoneBlockEntity tombstoneOf(final ServerLevel level, final BlockPos center, final UUID owner) {
		BlockPos pos = tombstonePos(level, center, owner);
		return pos == null ? null : (TombstoneBlockEntity) level.getBlockEntity(pos);
	}

	private static void buildTomb(final GameTestHelper helper, final BlockPos relative, final UUID owner, final long deathTime) {
		helper.setBlock(relative, TombstoneBlock.BLOCK);
		TombstoneBlockEntity tomb = (TombstoneBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
		tomb.fill(owner, "owner", deathTime, List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.APPLE, 2)));
	}

	private static void takeEverything(final GameTestHelper helper, final ServerPlayer player, final BlockPos tombPos) {
		moveNear(player, tombPos);
		player.getInventory().clearContent();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tombPos), Direction.UP, tombPos, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		AbstractContainerMenu menu = player.containerMenu;
		helper.assertTrue(menu != player.inventoryMenu, "the owner could not open the tombstone");
		for (int slot = 0; slot < 9; slot++) {
			menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
		}
	}

	private static void moveNear(final ServerPlayer player, final BlockPos pos) {
		player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
	}

	private static void assertIntact(
		final GameTestHelper helper,
		final ServerLevel level,
		final BlockPos pos,
		final List<ItemStack> expected,
		final String cause
	) {
		helper.assertTrue(level.getBlockState(pos).is(TombstoneBlock.BLOCK), "the block is gone after " + cause);
		BlockEntity entity = level.getBlockEntity(pos);
		helper.assertTrue(entity instanceof TombstoneBlockEntity, "the block entity is gone after " + cause);
		List<ItemStack> now = ((TombstoneBlockEntity) entity).contents();
		helper.assertTrue(now.size() == expected.size(), "contents changed after " + cause);
		for (int i = 0; i < now.size(); i++) {
			helper.assertTrue(ItemStack.matches(now.get(i), expected.get(i)), "stack " + i + " changed after " + cause);
		}
	}

	static ServerPlayer dieAndRespawn(final GameTestHelper helper, final ServerPlayer player) {
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), LETHAL_DAMAGE);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
		return helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
	}
}
