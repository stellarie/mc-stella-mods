package com.stella.rpg;

import com.stella.rpg.bag.Bag;
import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** The mage starter books by item id. The present path uses stand-in items, since no harness loads both mods. */
public final class MageGrantGameTest {
	private static final String SPELLS_MOD = "mod-stella-spells";
	private static final List<Item> STAND_INS = List.of(Items.FLINT, Items.COPPER_INGOT, Items.PACKED_ICE);

	@GameTest
	public void aMageGetsThreeBooksOnceThenNothing(final GameTestHelper helper) {
		ServerPlayer mage = RpgTestSupport.player(helper);
		mage.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(mage, Job.MAGE), "the mage job was refused");
		SpellsBridge.setBookSource(MageGrantGameTest::standIn);
		int first;
		int second;
		try {
			Jobs.grantItems(mage);
			first = held(mage);
			Jobs.grantItems(mage);
			second = held(mage);
		} finally {
			SpellsBridge.setBookSource(null);
		}
		boolean flagged = mage.getAttachedOrElse(SpellsBridge.MAGE_STARTER_GIVEN, false);
		RpgTestSupport.result("aMageGetsThreeBooksOnceThenNothing", "first=" + first + " second=" + second + " flagged=" + flagged);
		helper.assertTrue(first == STAND_INS.size(), "the mage holds " + first + " books after the job change");
		helper.assertTrue(second == first, "a second job change gave books: " + first + " then " + second);
		helper.assertTrue(flagged, "the gift was not recorded");
		helper.succeed();
	}

	@GameTest
	public void aFullPackDropsTheBooksAtTheFeet(final GameTestHelper helper) {
		ServerPlayer mage = RpgTestSupport.realPlayer(helper);
		for (int slot = 0; slot < mage.getInventory().getNonEquipmentItems().size(); slot++) {
			mage.getInventory().getNonEquipmentItems().set(slot, new ItemStack(Items.DIRT));
		}
		for (int slot = 0; slot < Bag.SIZE; slot++) {
			Bag.of(mage).set(slot, new ItemStack(Items.DIRT));
		}
		SpellsBridge.setBookSource(MageGrantGameTest::standIn);
		boolean given;
		try {
			given = SpellsBridge.grantMageStarter(mage);
		} finally {
			SpellsBridge.setBookSource(null);
		}
		long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(mage.blockPosition()).inflate(4.0),
			entity -> STAND_INS.contains(entity.getItem().getItem())).size();
		RpgTestSupport.result("aFullPackDropsTheBooksAtTheFeet", "given=" + given + " held=" + held(mage) + " dropped=" + dropped);
		helper.assertTrue(given, "the grant was refused on a full pack");
		helper.assertTrue(held(mage) == 0, "a book fitted into a full pack");
		helper.assertTrue(dropped == STAND_INS.size(), "dropped " + dropped + " books at the feet");
		helper.succeed();
	}

	@GameTest
	public void aMageWithoutTheSpellsModKeepsTheJobAndGetsNoBook(final GameTestHelper helper) {
		boolean loaded = FabricLoader.getInstance().isModLoaded(SPELLS_MOD);
		helper.assertTrue(!loaded, "the spells mod is loaded here, so this is not the absent path");
		ServerPlayer mage = RpgTestSupport.player(helper);
		mage.experienceLevel = Jobs.MIN_LEVEL;
		boolean chose = Jobs.choose(mage, Job.MAGE);
		boolean kit = Jobs.grantItems(mage);
		boolean flagged = mage.getAttachedOrElse(SpellsBridge.MAGE_STARTER_GIVEN, false);
		long books = SpellsBridge.MAGE_STARTER_IDS.stream().filter(id -> BuiltInRegistries.ITEM.containsKey(id)).count();
		RpgTestSupport.result("aMageWithoutTheSpellsModKeepsTheJobAndGetsNoBook",
			"chose=" + chose + " kit=" + kit + " flagged=" + flagged + " registered=" + books);
		helper.assertTrue(chose && Jobs.of(mage) == Job.MAGE, "the job change failed without the spells mod");
		helper.assertTrue(kit, "the job kit did not land");
		helper.assertTrue(!flagged && books == 0, "a book gift happened with no spells mod");
		helper.succeed();
	}

	private static ItemStack standIn(final Identifier id) {
		int index = SpellsBridge.MAGE_STARTER_IDS.indexOf(id);
		return index < 0 ? ItemStack.EMPTY : new ItemStack(STAND_INS.get(index));
	}

	private static int held(final ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && STAND_INS.contains(stack.getItem())) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
