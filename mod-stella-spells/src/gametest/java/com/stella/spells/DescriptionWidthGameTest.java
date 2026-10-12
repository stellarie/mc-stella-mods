package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every description line a spell, skill or item of this mod shows is at most 24 characters wide. */
public final class DescriptionWidthGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.descriptionWidth");
	private static final String MOD_ID = Spellbooks.MOD_ID;
	private static final int[] REFINES = {1, 7, 10, 12, 15};

	@GameTest
	public void everyRegisteredItemShowsShortDescriptionLines(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyRegisteredItemShowsShortDescriptionLines");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			ServerPlayer viewer = viewer(helper, players);
			List<String> tooLong = new ArrayList<>();
			int items = 0;
			int shown = 0;
			for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
				if (!MOD_ID.equals(id.getNamespace())) {
					continue;
				}
				items++;
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
				// A viewer shows the live numbers. No viewer shows the static text.
				for (ServerPlayer who : new ServerPlayer[] {viewer, null}) {
					List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, who, stack));
					shown += lines.size();
					tooLong.addAll(DescriptionSupport.tooLong(id + (who == null ? " static" : " live"), lines));
				}
			}
			LOG.info("[SPELLTEST] test=descriptionWidthItems RESULT {}", "items=" + items + " lines=" + shown + " tooLong=" + tooLong.size());
			helper.assertTrue(items > 0 && shown > items, "the items showed only " + shown + " lines");
			helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		} finally {
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest
	public void refinedAwakenedAndImbuedItemsShowShortDescriptionLines(final GameTestHelper helper) {
		SpellTestLog.track(helper, "refinedAwakenedAndImbuedItemsShowShortDescriptionLines");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			ServerPlayer viewer = viewer(helper, players);
			List<ItemStack> stacks = new ArrayList<>();
			List<Identifier> spells = new ArrayList<>();
			for (SpellbookItem book : Spellbooks.CATALOG) {
				spells.add(book.definition().id());
				ItemStack plain = new ItemStack(book);
				for (int level : REFINES) {
					stacks.add(BookRefine.withLevel(plain, level));
				}
				ItemStack awakened = plain.copy();
				awakened.set(Awakening.AWAKENED, Unit.INSTANCE);
				stacks.add(BookRefine.withLevel(awakened, BookRefine.MAX_LEVEL));
				ItemStack waking = plain.copy();
				waking.set(Awakening.COUNT, 7);
				stacks.add(waking);
			}
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(spells));
			stacks.add(sword);
			List<String> tooLong = new ArrayList<>();
			int shown = 0;
			for (ItemStack stack : stacks) {
				List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, viewer, stack));
				shown += lines.size();
				tooLong.addAll(DescriptionSupport.tooLong(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " +" + BookRefine.level(stack), lines));
			}
			LOG.info("[SPELLTEST] test=descriptionWidthRefined RESULT {}", "stacks=" + stacks.size() + " lines=" + shown + " tooLong=" + tooLong.size());
			helper.assertTrue(shown > stacks.size(), "the stacks showed only " + shown + " lines");
			helper.assertTrue(tooLong.stream().distinct().toList().isEmpty(),
				"description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong.stream().distinct().toList());
		} finally {
			leave(helper, players);
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	private static ServerPlayer viewer(final GameTestHelper helper, final List<ServerPlayer> players) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		players.add(player);
		return player;
	}

	private static void leave(final GameTestHelper helper, final List<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}
}
