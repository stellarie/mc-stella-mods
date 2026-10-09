package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import com.stella.ruins.world.RuinClues;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Acceptance items 6, 8 and 9: the compass, the pick and the headlamp. */
public class CampItemsGameTest {
	static final int ROLLS = 4000;

	private static ItemStack craft(final GameTestHelper helper, final int width, final int height, final Item... cells) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Item cell : cells) {
			stacks.add(cell == null ? ItemStack.EMPTY : new ItemStack(cell));
		}
		CraftingInput input = CraftingInput.of(width, height, stacks);
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	/** A source that always rolls the extra drop, or one that never does. Never loops forever. */
	private static Supplier<RandomSource> seeded(final boolean hit) {
		for (long seed = 0; seed < 100_000L; seed++) {
			if ((RandomSource.create(seed).nextDouble() < HeadlampEffect.EXTRA_DROP_CHANCE) == hit) {
				long found = seed;
				return () -> RandomSource.create(found);
			}
		}
		throw new IllegalStateException("no seed rolls " + hit + " at " + HeadlampEffect.EXTRA_DROP_CHANCE);
	}

	@GameTest
	public void gleamingOreChunksCraftTheProspectorsPickButNotTheLuckyHeadlamp(final GameTestHelper helper) {
		Item ore = CampItems.GLEAMING_ORE_CHUNK;
		ItemStack pick = craft(helper, 3, 3, ore, ore, ore, null, Items.STICK, null, null, Items.STICK, null);
		helper.assertTrue(pick.is(CampItems.PROSPECTORS_PICK) && pick.getCount() == 1, "three chunks and two sticks gave " + pick);
		ItemStack lamp = craft(helper, 3, 2, ore, ore, ore, ore, Items.TORCH, ore);
		helper.assertTrue(lamp.isEmpty(), "five chunks and a torch gave " + lamp + ", but the headlamp is a unique and has no recipe");
		helper.assertTrue(craft(helper, 3, 3, ore, ore, ore, null, Items.STICK, null, null, Items.STICK, null).get(RuinUniques.UNIQUE) != null
			&& craft(helper, 3, 3, ore, ore, ore, null, Items.STICK, null, null, Items.STICK, null).get(RuinUniques.UNIQUE).equals(ProspectorEffect.ID),
			"the pick carries no prospector effect");
		helper.assertTrue(craft(helper, 3, 2, Items.STICK, Items.STICK, Items.STICK, Items.STICK, Items.TORCH, Items.STICK).isEmpty(), "sticks made a lamp");
		MinerTestSupport.result("campRecipes", "pick=" + pick.getItem() + " lamp=" + lamp.getItem());
		helper.succeed();
	}

	@GameTest
	public void thePickGivesHasteOneWhileItIsHeld(final GameTestHelper helper) {
		MinerTestSupport.Joined joined = MinerTestSupport.join(helper);
		ServerPlayer player = joined.player();
		helper.assertTrue(!UniqueEffects.effectIdsOf(player).contains(ProspectorEffect.ID), "a bare hand carries the prospector effect");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CampItems.PROSPECTORS_PICK));
		helper.assertTrue(UniqueEffects.effectIdsOf(player).contains(ProspectorEffect.ID), "the held pick names no prospector effect");
		new ProspectorEffect().onTick(player);
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		MinerTestSupport.result("pickHaste", "haste=" + (haste == null ? "none" : haste.getDuration() + "/" + haste.getAmplifier()) + " ticks=" + ProspectorEffect.HASTE_TICKS);
		helper.assertTrue(haste != null && haste.getAmplifier() == 0, "the pick left " + haste);
		helper.assertTrue(haste.getDuration() > 0 && haste.getDuration() <= ProspectorEffect.HASTE_TICKS, "the haste lasts " + haste.getDuration());
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		player.removeEffect(MobEffects.HASTE);
		helper.assertTrue(!UniqueEffects.effectIdsOf(player).contains(ProspectorEffect.ID), "the effect stayed without the pick");
		helper.assertTrue(RuinUniques.effectOf(new ItemStack(CampItems.PROSPECTORS_PICK)).equals(ProspectorEffect.ID), "the pick is not a unique with the effect");
		MinerTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theHeadlampGivesNightVisionBelowY40(final GameTestHelper helper) {
		MinerTestSupport.Joined joined = MinerTestSupport.join(helper);
		ServerPlayer player = joined.player();
		ServerLevel level = helper.getLevel();
		HeadlampEffect effect = new HeadlampEffect();
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(CampItems.LUCKY_HEADLAMP));
		player.teleportTo(level, player.getX(), HeadlampEffect.LAMP_Y - 1, player.getZ(), Set.of(), 0.0F, 0.0F, true);
		effect.onTick(player);
		MobEffectInstance night = player.getEffect(MobEffects.NIGHT_VISION);
		MinerTestSupport.result("lampLight", "y=" + player.getY() + " night=" + (night == null ? "none" : night.getDuration() + "/" + night.getAmplifier()));
		helper.assertTrue(night != null && night.getAmplifier() == 0, "the lamp left " + night);
		helper.assertTrue(night.getDuration() > 0 && night.getDuration() <= HeadlampEffect.NIGHT_VISION_TICKS, "the night vision lasts " + night.getDuration());
		player.removeEffect(MobEffects.NIGHT_VISION);
		player.teleportTo(level, player.getX(), HeadlampEffect.LAMP_Y, player.getZ(), Set.of(), 0.0F, 0.0F, true);
		effect.onTick(player);
		helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) == null, "the lamp lit the way at Y " + HeadlampEffect.LAMP_Y);
		helper.assertTrue(RuinUniques.effectOf(new ItemStack(CampItems.LUCKY_HEADLAMP)).equals(HeadlampEffect.ID), "the lamp is not a unique with the effect");
		MinerTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theHeadlampRollsOneExtraOreDropAtTenPercent(final GameTestHelper helper) {
		MinerTestSupport.Joined joined = MinerTestSupport.join(helper);
		ServerPlayer player = joined.player();
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockState ore = Blocks.IRON_ORE.defaultBlockState();
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(CampItems.LUCKY_HEADLAMP));
		HeadlampEffect.setRandom(player.getUUID(), seeded(true));
		ItemStack extra = HeadlampEffect.extraDrop(level, player, pos, ore);
		MinerTestSupport.result("lampDrop", "extra=" + extra + " chance=" + HeadlampEffect.EXTRA_DROP_CHANCE);
		helper.assertTrue(extra.is(Items.RAW_IRON) && extra.getCount() == 1, "the lamp gave " + extra);
		HeadlampEffect.setRandom(player.getUUID(), seeded(false));
		helper.assertTrue(HeadlampEffect.extraDrop(level, player, pos, ore).isEmpty(), "a lost roll paid out");
		HeadlampEffect.setRandom(player.getUUID(), seeded(true));
		helper.assertTrue(HeadlampEffect.extraDrop(level, player, pos, Blocks.STONE.defaultBlockState()).isEmpty(), "the lamp paid for stone");
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		helper.assertTrue(HeadlampEffect.extraDrop(level, player, pos, ore).isEmpty(), "the lamp paid without being worn");
		HeadlampEffect.setRandom(player.getUUID(), null);
		MinerTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theExtraDropRollsAtTenPercentOverFourThousandTrials(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261005L);
		int hits = 0;
		for (int i = 0; i < ROLLS; i++) {
			if (HeadlampEffect.roll(random)) {
				hits++;
			}
		}
		double rate = hits / (double) ROLLS;
		MinerTestSupport.result("lampRate", "trials=" + ROLLS + " hits=" + hits + " rate=" + rate);
		helper.assertTrue(Math.abs(rate - HeadlampEffect.EXTRA_DROP_CHANCE) <= HeadlampEffect.EXTRA_DROP_CHANCE * 0.15, "the drop rate is " + rate);
		helper.assertTrue(Math.abs(HeadlampEffect.EXTRA_DROP_CHANCE - 0.10) < 1.0E-9, "the chance moved to " + HeadlampEffect.EXTRA_DROP_CHANCE);
		helper.succeed();
	}

	@GameTest
	public void theCompassPointsAtTheNearestLargeRuinAndNeverAtASmallOne(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
		RuinClues.Locator old = RuinClues.replaceLocator((lvl, id, origin) -> switch (id) {
			// A small site stands next door, and the proving grounds lie past the radius.
			case LostMinersCampSite.ID -> Optional.of(from.offset(16, 0, 0));
			case "deep_forge" -> Optional.of(from.offset(3000, 0, 0));
			case "sealed_vault" -> Optional.of(from.offset(200, 0, 0));
			case "proving_grounds" -> Optional.of(from.offset(MinersCompassItem.SEARCH_BLOCKS * 4, 0, 0));
			default -> Optional.empty();
		});
		try {
			Optional<MinersCompassItem.Target> target = MinersCompassItem.nearest(level, from);
			MinerTestSupport.result("compassTarget", "site=" + target.map(MinersCompassItem.Target::site).orElse("none") + " from=" + from.toShortString()
				+ " radius=" + MinersCompassItem.SEARCH_BLOCKS);
			helper.assertTrue(target.isPresent() && target.get().site().equals("sealed_vault"),
				"the compass chose " + target.map(MinersCompassItem.Target::site).orElse("nothing"));
			helper.assertTrue(target.get().pos().dimension().equals(level.dimension()), "the target is in " + target.get().pos().dimension());
			helper.assertTrue(MinersCompassItem.LARGE_SITES.equals(List.of("deep_forge", "ruined_keep", "sealed_vault", "proving_grounds")),
				"the large site list is " + MinersCompassItem.LARGE_SITES);
			helper.assertTrue(!MinersCompassItem.LARGE_SITES.contains(LostMinersCampSite.ID), "the camp counts as a large ruin");
			ItemStack compass = new ItemStack(CampItems.MINERS_COMPASS);
			MinersCompassItem.aim(compass, target.get().pos());
			LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
			helper.assertTrue(tracker != null && tracker.target().isPresent() && tracker.target().get().equals(target.get().pos()), "the compass holds " + tracker);
		} finally {
			RuinClues.replaceLocator(old);
		}
		helper.succeed();
	}

	@GameTest
	public void theCompassUseAimsAtTheNearestRuinAndSaysSoWhenNoneIsInRange(final GameTestHelper helper) {
		MinerTestSupport.Joined joined = MinerTestSupport.join(helper);
		ServerPlayer player = joined.player();
		ServerLevel level = helper.getLevel();
		BlockPos from = player.blockPosition();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CampItems.MINERS_COMPASS));
		RuinClues.Locator old = RuinClues.replaceLocator((lvl, id, origin) -> "ruined_keep".equals(id) ? Optional.of(from.offset(400, 0, 0)) : Optional.empty());
		InteractionResult found;
		try {
			found = CampItems.MINERS_COMPASS.use(level, player, InteractionHand.MAIN_HAND);
		} finally {
			RuinClues.replaceLocator(old);
		}
		List<String> said = MinerTestSupport.messages(joined);
		LodestoneTracker tracker = player.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.LODESTONE_TRACKER);
		MinerTestSupport.result("compassUse", "found=" + found + " said=" + said + " tracker=" + (tracker != null && tracker.target().isPresent()));
		helper.assertTrue(found.consumesAction(), "the compass use gave " + found);
		helper.assertTrue(tracker != null && tracker.target().isPresent() && tracker.target().get().pos().equals(from.offset(400, 0, 0)), "the compass holds " + tracker);
		helper.assertTrue(said.stream().anyMatch(line -> line.contains("needle")), "the compass said " + said);
		// Every large ruin lies past the search radius: the compass must find none of them.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CampItems.MINERS_COMPASS));
		old = RuinClues.replaceLocator((lvl, id, origin) -> Optional.of(from.offset(MinersCompassItem.SEARCH_BLOCKS + 400, 0, 0)));
		InteractionResult nothing;
		try {
			nothing = CampItems.MINERS_COMPASS.use(level, player, InteractionHand.MAIN_HAND);
		} finally {
			RuinClues.replaceLocator(old);
		}
		List<String> empty = MinerTestSupport.messages(joined);
		LodestoneTracker none = player.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.LODESTONE_TRACKER);
		MinerTestSupport.result("compassNothing", "gave=" + nothing + " said=" + empty + " tracker=" + (none != null));
		helper.assertTrue(nothing == InteractionResult.CONSUME, "a far search gave " + nothing);
		helper.assertTrue(none == null, "a far search left a target on the compass");
		helper.assertTrue(empty.stream().anyMatch(line -> line.contains("100 chunks")), "the compass said " + empty);
		MinerTestSupport.leave(helper, player);
		helper.succeed();
	}
}
