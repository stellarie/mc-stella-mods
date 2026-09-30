package com.stella.spells;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Four quick slots beside the hotbar: any item, used by its key without being selected. */
public class QuickSlotsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.quickslots");
	/** The vanilla inventory screen's background height, which is not ours to move. */
	private static final int VANILLA_PANEL_HEIGHT = 166;

	@GameTest
	public void theInventoryMenuGainsTheQuickSlots(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			InventoryMenu menu = player.inventoryMenu;
			int size = menu.slots.size();
			boolean backed = true;
			for (int i = 0; i < QuickSlots.COUNT; i++) {
				Slot slot = menu.slots.get(QuickSlots.MENU_FIRST_SLOT + i);
				slot.set(new ItemStack(Items.DIAMOND, i + 1));
				backed &= QuickSlots.of(player).get(i).is(Items.DIAMOND) && QuickSlots.of(player).get(i).getCount() == i + 1;
			}
			LOG.info("[SPELLTEST] test=theInventoryMenuGainsTheQuickSlots RESULT menuSlots={} firstQuick={} backed={}", size, QuickSlots.MENU_FIRST_SLOT, backed);
			helper.assertTrue(size == QuickSlots.MENU_FIRST_SLOT + QuickSlots.COUNT, "the inventory menu has " + size + " slots");
			helper.assertTrue(backed, "a menu slot did not write through to the quick slots");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void theQuickSlotsSitInARowBelowThePanel(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			boolean row = true;
			int lowest = Integer.MAX_VALUE;
			for (int i = 0; i < QuickSlots.COUNT; i++) {
				Slot slot = player.inventoryMenu.slots.get(QuickSlots.MENU_FIRST_SLOT + i);
				row &= slot.x == QuickSlots.MENU_X + i * QuickSlots.MENU_STEP && slot.y == QuickSlots.MENU_Y;
				lowest = Math.min(lowest, slot.y);
			}
			LOG.info("[SPELLTEST] test=theQuickSlotsSitInARowBelowThePanel RESULT row={} lowestY={} panelHeight={}", row, lowest, VANILLA_PANEL_HEIGHT);
			helper.assertTrue(row, "the quick slots are not one row at the shipped spot");
			helper.assertTrue(lowest >= VANILLA_PANEL_HEIGHT, "a quick slot sits inside the 166 pixel inventory panel");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void aQuickSlotTakesAnyItem(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			Slot slot = player.inventoryMenu.slots.get(QuickSlots.MENU_FIRST_SLOT);
			boolean all = slot.mayPlace(new ItemStack(Items.DIRT)) && slot.mayPlace(new ItemStack(Items.NETHERITE_SWORD))
				&& slot.mayPlace(new ItemStack(spark(helper)));
			LOG.info("[SPELLTEST] test=aQuickSlotTakesAnyItem RESULT acceptsAll={}", all);
			helper.assertTrue(all, "a quick slot refused an item");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void pressingASlotCastsItsBookWithoutSelectingIt(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Mob zombie = target(helper);
		SpellbookItem book = spark(helper);
		ItemStack stick = new ItemStack(Items.STICK);
		player.getInventory().setItem(player.getInventory().getSelectedSlot(), stick);
		QuickSlots.of(player).set(2, new ItemStack(book));
		float before = zombie.getHealth();
		Findable.then(helper, List.of(zombie), () -> {
			try {
				QuickSlots.Outcome outcome = QuickSlots.use(player, 2);
				float lost = before - zombie.getHealth();
				boolean heldKept = player.getMainHandItem() == stick && stick.getCount() == 1;
				boolean bookHome = QuickSlots.of(player).get(2).is(book);
				boolean resting = player.getCooldowns().isOnCooldown(QuickSlots.of(player).get(2));
				LOG.info("[SPELLTEST] test=pressingASlotCastsItsBookWithoutSelectingIt RESULT outcome={} lost={} heldKept={} bookHome={} resting={}", outcome, lost, heldKept, bookHome, resting);
				helper.assertTrue(outcome == QuickSlots.Outcome.USED, "the key returned " + outcome);
				helper.assertTrue(Math.abs(lost - SpellCasting.DAMAGE) < 1.0E-4, "Spark Shot took " + lost);
				helper.assertTrue(heldKept, "the selected item was disturbed");
				helper.assertTrue(bookHome && resting, "the book did not return to its slot on cooldown");
			} finally {
				drop(helper, player);
			}
			helper.succeed();
		});
	}

	@GameTest
	public void pressingAnEmptySlotDoesNothing(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			ItemStack stick = new ItemStack(Items.STICK);
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), stick);
			QuickSlots.Outcome outcome = QuickSlots.use(player, 1);
			LOG.info("[SPELLTEST] test=pressingAnEmptySlotDoesNothing RESULT outcome={}", outcome);
			helper.assertTrue(outcome == QuickSlots.Outcome.EMPTY, "an empty slot returned " + outcome);
			helper.assertTrue(player.getMainHandItem() == stick, "the selected item was disturbed");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void aScrollInASlotIsSpentByOneCast(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Mob zombie = target(helper);
		SpellScrollItem scroll = (SpellScrollItem) BuiltInRegistries.ITEM.getValue(Spellbooks.scrollId(Spellbooks.SPARK_SHOT_ID));
		QuickSlots.of(player).set(0, new ItemStack(scroll, 2));
		Findable.then(helper, List.of(zombie), () -> {
			try {
				QuickSlots.Outcome outcome = QuickSlots.use(player, 0);
				int left = QuickSlots.of(player).get(0).getCount();
				LOG.info("[SPELLTEST] test=aScrollInASlotIsSpentByOneCast RESULT outcome={} left={}", outcome, left);
				helper.assertTrue(outcome == QuickSlots.Outcome.USED && left == 1, "outcome " + outcome + ", " + left + " scrolls left");
			} finally {
				drop(helper, player);
			}
			helper.succeed();
		});
	}

	@GameTest
	public void foodInASlotIsEatenAtOnce(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			player.getFoodData().setFoodLevel(6);
			ItemStack stick = new ItemStack(Items.STICK);
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), stick);
			QuickSlots.of(player).set(3, new ItemStack(Items.BREAD, 3));
			QuickSlots.Outcome outcome = QuickSlots.use(player, 3);
			int food = player.getFoodData().getFoodLevel();
			int left = QuickSlots.of(player).get(3).getCount();
			LOG.info("[SPELLTEST] test=foodInASlotIsEatenAtOnce RESULT outcome={} food={} left={} using={}", outcome, food, left, player.isUsingItem());
			helper.assertTrue(food > 6 && left == 2, "food " + food + ", " + left + " bread left");
			helper.assertTrue(!player.isUsingItem() && player.getMainHandItem() == stick, "the hand was left mid-use or disturbed");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void aBowInASlotIsNotLeftDrawn(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			player.getInventory().setItem(20, new ItemStack(Items.ARROW, 8));
			ItemStack stick = new ItemStack(Items.STICK);
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), stick);
			QuickSlots.of(player).set(0, new ItemStack(Items.BOW));
			QuickSlots.Outcome outcome = QuickSlots.use(player, 0);
			LOG.info("[SPELLTEST] test=aBowInASlotIsNotLeftDrawn RESULT outcome={} using={} bowKept={}", outcome, player.isUsingItem(), QuickSlots.of(player).get(0).is(Items.BOW));
			helper.assertTrue(outcome == QuickSlots.Outcome.NO_EFFECT, "a bow returned " + outcome);
			helper.assertTrue(!player.isUsingItem(), "the bow was left drawn");
			helper.assertTrue(QuickSlots.of(player).get(0).is(Items.BOW) && player.getMainHandItem() == stick, "the bow or the hand was disturbed");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void aBusyHandBlocksTheKey(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Mob zombie = target(helper);
		try {
			player.getFoodData().setFoodLevel(6);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			QuickSlots.of(player).set(0, new ItemStack(spark(helper)));
			float before = zombie.getHealth();
			QuickSlots.Outcome outcome = QuickSlots.use(player, 0);
			LOG.info("[SPELLTEST] test=aBusyHandBlocksTheKey RESULT outcome={} using={} lost={}", outcome, player.isUsingItem(), before - zombie.getHealth());
			helper.assertTrue(outcome == QuickSlots.Outcome.BUSY, "a busy hand returned " + outcome);
			helper.assertTrue(player.isUsingItem() && zombie.getHealth() == before, "the key disturbed the item in use");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void anOutOfRangeSlotIsRefused(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			QuickSlots.Outcome below = QuickSlots.use(player, -1);
			QuickSlots.Outcome above = QuickSlots.use(player, QuickSlots.COUNT);
			LOG.info("[SPELLTEST] test=anOutOfRangeSlotIsRefused RESULT below={} above={}", below, above);
			helper.assertTrue(below == QuickSlots.Outcome.INVALID && above == QuickSlots.Outcome.INVALID, "a forged slot number was accepted");
		} finally {
			drop(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void theSlotsSurviveTheSaveCodec(final GameTestHelper helper) {
		QuickSlots slots = new QuickSlots();
		slots.set(0, new ItemStack(Items.DIAMOND, 5));
		slots.set(3, new ItemStack(spark(helper)));
		RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
		DataResult<net.minecraft.nbt.Tag> encoded = QuickSlots.CODEC.encodeStart(ops, slots);
		QuickSlots back = QuickSlots.CODEC.parse(ops, encoded.getOrThrow()).getOrThrow();
		boolean same = back.get(0).is(Items.DIAMOND) && back.get(0).getCount() == 5 && back.get(1).isEmpty() && back.get(2).isEmpty() && back.get(3).is(spark(helper));
		LOG.info("[SPELLTEST] test=theSlotsSurviveTheSaveCodec RESULT same={}", same);
		helper.assertTrue(same, "the quick slots changed across a save and load");
		helper.succeed();
	}

	@GameTest
	public void aDeathDropsTheSlots(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			QuickSlots.of(player).set(1, new ItemStack(Items.NETHERITE_SCRAP, 7));
			player.getInventory().dropAll();
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6.0),
				item -> item.getItem().is(Items.NETHERITE_SCRAP)).stream().mapToInt(item -> item.getItem().getCount()).sum();
			boolean emptied = QuickSlots.of(player).get(1).isEmpty();
			LOG.info("[SPELLTEST] test=aDeathDropsTheSlots RESULT dropped={} emptied={}", dropped, emptied);
			helper.assertTrue(dropped == 7 && emptied, "dropped " + dropped + ", emptied " + emptied);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(required = false)
	public void quickSlotKeysHudAndInventoryLayoutRequireManualReview(final GameTestHelper helper) {
		SpellTestLog.track(helper, "quickSlotKeysHudAndInventoryLayoutRequireManualReview");
		helper.fail("Manual client review required: four slots draw right of the hotbar, four slots show beside the inventory screen and take any item, the four keys are unbound until set under Controls, and each key uses its slot without selecting it.");
	}

	private static SpellbookItem spark(final GameTestHelper helper) {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:spark_shot"));
	}

	/** Puts the caster's slots on the floor so the next test's player starts clean, then removes it. */
	private static void drop(final GameTestHelper helper, final ServerPlayer player) {
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			QuickSlots.of(player).set(i, ItemStack.EMPTY);
		}
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "quickslotter"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}

	private static Mob target(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
