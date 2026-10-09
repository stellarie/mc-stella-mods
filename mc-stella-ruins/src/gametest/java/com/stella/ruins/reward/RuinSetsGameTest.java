package com.stella.ruins.reward;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Set bonuses at the wearer. Each test waits for the tracker through real ticks, because the equipment event
 * fires from the entity tick.
 */
public class RuinSetsGameTest {
	private static final double LUCK_2 = 1.0;
	private static final double HEALTH_4 = 4.0;
	private static final double BASE_HEALTH = 20.0;

	private static Item piece(final RuinSets.SetDefinition set, final EquipmentSlot slot) {
		return set.pieces().get(slot);
	}

	private static void wear(final ServerPlayer player, final RuinSets.SetDefinition set, final EquipmentSlot... slots) {
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(piece(set, slot)));
		}
	}

	private static void expect(final GameTestHelper helper, final ServerPlayer player, final double luck, final double health, final String what) {
		double gotLuck = player.getAttributeValue(Attributes.LUCK);
		double gotHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
		helper.assertTrue(gotLuck == luck && gotHealth == health, what + ": luck " + gotLuck + " want " + luck + ", max health " + gotHealth + " want " + health);
	}

	@GameTest(maxTicks = 200)
	public void twoPiecesGiveTheTwoPieceBonusOnly(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		wear(player, RewardTestInit.alpha, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
		helper.startSequence()
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH, "two pieces"))
			.thenExecute(() -> {
				RewardTestSupport.result("twoPieceBonus", "luck=" + player.getAttributeValue(Attributes.LUCK) + " maxHealth=" + player.getAttributeValue(Attributes.MAX_HEALTH));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void fourPiecesGiveBothBonuses(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		wear(player, RewardTestInit.alpha, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		helper.startSequence()
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH + HEALTH_4, "four pieces"))
			.thenExecute(() -> {
				RewardTestSupport.result("fourPieceBonus", "luck=" + player.getAttributeValue(Attributes.LUCK) + " maxHealth=" + player.getAttributeValue(Attributes.MAX_HEALTH));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void removingAPieceRemovesTheBonus(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		wear(player, RewardTestInit.alpha, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		helper.startSequence()
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH + HEALTH_4, "four pieces first"))
			.thenExecute(() -> player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY))
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH, "three pieces"))
			.thenExecute(() -> {
				player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> expect(helper, player, 0.0, BASE_HEALTH, "one piece"))
			.thenExecute(() -> {
				RewardTestSupport.result("removePiece", "luck=" + player.getAttributeValue(Attributes.LUCK) + " maxHealth=" + player.getAttributeValue(Attributes.MAX_HEALTH));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	/** A mob is outside the player sweep, so only the equipment event can reach it. */
	@GameTest(maxTicks = 100)
	public void aMobWearingTheSetGetsTheBonusFromTheEquipmentEvent(final GameTestHelper helper) {
		net.minecraft.world.entity.animal.cow.Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			cow.setItemSlot(slot, new ItemStack(piece(RewardTestInit.alpha, slot)));
		}
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(cow.getAttributeValue(Attributes.MAX_HEALTH) == BASE_HEALTH + HEALTH_4,
				"max health " + cow.getAttributeValue(Attributes.MAX_HEALTH) + ", want " + (BASE_HEALTH + HEALTH_4)))
			.thenExecute(() -> cow.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY))
			.thenWaitUntil(() -> helper.assertTrue(cow.getAttributeValue(Attributes.MAX_HEALTH) == BASE_HEALTH,
				"max health " + cow.getAttributeValue(Attributes.MAX_HEALTH) + " after a piece came off, want " + BASE_HEALTH))
			.thenExecute(() -> {
				RewardTestSupport.result("mobSetEvent", "gained=true lostOnRemoval=true");
				cow.discard();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aPieceInTheWrongSlotDoesNotCount(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.HEAD)));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.CHEST)));
		helper.startSequence()
			.thenIdle(25)
			.thenExecute(() -> {
				helper.assertTrue(RuinSets.wornPieces(player, "test_alpha") == 1, "worn pieces " + RuinSets.wornPieces(player, "test_alpha") + ", want 1");
				expect(helper, player, 0.0, BASE_HEALTH, "a chestplate in the legs slot");
				player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.CHEST)));
			})
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH, "the chestplate moved to its own slot"))
			.thenExecute(() -> RewardTestSupport.leave(helper, player))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void piecesOfTwoSetsDoNotAddUp(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.HEAD)));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(piece(RewardTestInit.beta, EquipmentSlot.CHEST)));
		helper.startSequence()
			.thenIdle(25)
			.thenExecute(() -> {
				expect(helper, player, 0.0, BASE_HEALTH, "one piece of each set");
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.CHEST)));
			})
			.thenWaitUntil(() -> expect(helper, player, LUCK_2, BASE_HEALTH, "two pieces of one set"))
			.thenExecute(() -> RewardTestSupport.leave(helper, player))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void theFourPieceEffectRenewsOnlyWhileTheBonusHolds(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		wear(player, RewardTestInit.alpha, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(player.hasEffect(MobEffects.SPEED), "the four-piece effect never arrived"))
			.thenExecute(() -> {
				player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				player.removeEffect(MobEffects.SPEED);
			})
			.thenIdle(45)
			.thenExecute(() -> {
				helper.assertTrue(!player.hasEffect(MobEffects.SPEED), "the effect came back after a piece was removed");
				RewardTestSupport.result("periodicEffect", "renewedWhileWorn=true goneAfterRemoval=true");
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aPieceGivesItsSpecDefense(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		wear(player, RewardTestInit.beta, EquipmentSlot.CHEST);
		RuinSets.SetDefinition beta = RewardTestInit.beta;
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(
				player.getAttributeValue(Attributes.ARMOR) == beta.spec().chestplate(),
				"armor " + player.getAttributeValue(Attributes.ARMOR) + ", want " + beta.spec().chestplate()))
			.thenExecute(() -> RewardTestSupport.leave(helper, player))
			.thenSucceed();
	}

	@GameTest
	public void aPieceListsEveryBonusInItsLore(final GameTestHelper helper) {
		ItemStack helmet = new ItemStack(piece(RewardTestInit.alpha, EquipmentSlot.HEAD));
		ItemLore lore = helmet.get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the piece has no lore");
		List<Component> lines = lore.lines();
		helper.assertTrue(lines.size() == 1 + RewardTestInit.alpha.spec().bonuses().size(), "lore has " + lines.size() + " lines");
		for (int i = 0; i < RewardTestInit.alpha.spec().bonuses().size(); i++) {
			SetBonus bonus = RewardTestInit.alpha.spec().bonuses().get(i);
			TranslatableContents line = (TranslatableContents) lines.get(i + 1).getContents();
			helper.assertTrue(line.getKey().equals("ruins.reward.set.bonus") && line.getArgs()[0].equals(bonus.pieces()),
				"line " + (i + 1) + " is not the " + bonus.pieces() + "-piece bonus");
		}
		helper.succeed();
	}

	@GameTest
	public void aSetRegistersFourPiecesThatKnowTheirSet(final GameTestHelper helper) {
		for (ArmorType type : new ArmorType[] {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
			Item item = BuiltInRegistries.ITEM.getValue(com.stella.ruins.StellaRuins.id("test_alpha_" + type.getName()));
			helper.assertTrue(item != Items.AIR, "test_alpha_" + type.getName() + " is not registered");
			RuinSets.Piece member = RuinSets.pieceOf(new ItemStack(item)).orElse(null);
			helper.assertTrue(member != null && member.setId().equals("test_alpha") && member.slot() == type.getSlot(), "the piece does not know its set");
		}
		helper.succeed();
	}

	@GameTest
	public void aBonusThatListsAnAttributeTwiceIsRejected(final GameTestHelper helper) {
		boolean rejected = false;
		try {
			new SetBonus(2, List.of(AttributeBonus.add(Attributes.LUCK, 1.0), AttributeBonus.add(Attributes.LUCK, 2.0)));
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		helper.assertTrue(rejected, "a repeated attribute was accepted");
		helper.succeed();
	}

	@GameTest
	public void everySetPieceShipsItsAssets(final GameTestHelper helper) {
		for (RuinSets.SetDefinition set : RuinSets.all()) {
			String asset = set.spec().equipmentAsset().getPath();
			require(helper, "/assets/mod-stella-ruins/equipment/" + asset + ".json");
			require(helper, "/assets/mod-stella-ruins/textures/entity/equipment/humanoid/" + asset + ".png");
			require(helper, "/assets/mod-stella-ruins/textures/entity/equipment/humanoid_leggings/" + asset + ".png");
			for (Item item : set.pieces().values()) {
				String id = BuiltInRegistries.ITEM.getKey(item).getPath();
				require(helper, "/assets/mod-stella-ruins/items/" + id + ".json");
				require(helper, "/assets/mod-stella-ruins/models/item/" + id + ".json");
				require(helper, "/assets/mod-stella-ruins/textures/item/" + id + ".png");
			}
		}
		RewardTestSupport.result("setAssets", "sets=" + RuinSets.all().size());
		helper.succeed();
	}

	private static void require(final GameTestHelper helper, final String path) {
		try (InputStream in = RuinSets.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null && in.readAllBytes().length > 0, "missing resource " + path);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(maxTicks = 100)
	public void aHybridBonusHoldsAtTwoPlusTwoAndDropsWhenAPieceLeaves(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		net.minecraft.resources.Identifier id = RuinSets.hybridModifierId("test_alpha_beta");
		double base = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		wear(player, RewardTestInit.alpha, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
		wear(player, RewardTestInit.beta, EquipmentSlot.LEGS);
		RuinSets.refresh(player);
		helper.assertTrue(player.getAttribute(Attributes.SAFE_FALL_DISTANCE).getModifier(id) == null, "two of one set and one of the other already gave the hybrid");
		wear(player, RewardTestInit.beta, EquipmentSlot.FEET);
		RuinSets.refresh(player);
		double with = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		helper.assertTrue(player.getAttribute(Attributes.SAFE_FALL_DISTANCE).getModifier(id) != null && with == base + 5.0, "two plus two gave a fall distance of " + with + ", want " + (base + 5.0));
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		RuinSets.refresh(player);
		double without = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		RewardTestSupport.result("hybridBonus", "base=" + base + " with=" + with + " withoutAPiece=" + without);
		helper.assertTrue(player.getAttribute(Attributes.SAFE_FALL_DISTANCE).getModifier(id) == null && without == base, "the hybrid stayed after a piece left: " + without);
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aHybridThatNamesAnUndefinedSetOrTooManyPiecesIsRejected(final GameTestHelper helper) {
		boolean unknown = false;
		try {
			RuinSets.defineHybrid(new HybridBonus("test_bad_set", java.util.Map.of("test_alpha", 2, "no_such_set", 2), List.of(), null));
		} catch (IllegalStateException e) {
			unknown = true;
		}
		boolean tooMany = false;
		try {
			new HybridBonus("test_too_many", java.util.Map.of("test_alpha", 3, "test_beta", 2), List.of(), null);
		} catch (IllegalArgumentException e) {
			tooMany = true;
		}
		helper.assertTrue(unknown, "a hybrid named a set no one defined");
		helper.assertTrue(tooMany, "a hybrid asked for five pieces");
		helper.succeed();
	}
}
