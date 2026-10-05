package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Exclusives;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 7 and 8: the Hawkeye set recipes and bonuses, and the Hawkeye Longbow. */
public class WatchtowerRewardsGameTest {
	private static ItemStack craft(final GameTestHelper helper, final int w, final int h, final ItemStack... cells) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(w, h, List.of(cells));
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return found.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static Item piece(final EquipmentSlot slot) {
		return WatchtowerItems.HAWKEYE.pieces().get(slot);
	}

	@GameTest
	public void everyHawkeyeRecipeCraftsItsPieceAndOnlyFromFeathersAndLeather(final GameTestHelper helper) {
		ItemStack f = new ItemStack(WatchtowerItems.HAWKFEATHER);
		ItemStack l = new ItemStack(Items.LEATHER);
		ItemStack e = ItemStack.EMPTY;
		helper.assertTrue(craft(helper, 3, 2, l, l, l, f, e, f).is(piece(EquipmentSlot.HEAD)), "the helmet recipe gave " + craft(helper, 3, 2, l, l, l, f, e, f));
		helper.assertTrue(craft(helper, 3, 3, f, e, f, l, l, l, l, f, l).is(piece(EquipmentSlot.CHEST)), "the chestplate recipe failed");
		helper.assertTrue(craft(helper, 3, 3, l, l, l, f, e, f, l, e, l).is(piece(EquipmentSlot.LEGS)), "the leggings recipe failed");
		helper.assertTrue(craft(helper, 3, 2, f, e, f, l, e, l).is(piece(EquipmentSlot.FEET)), "the boots recipe failed");
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		ItemStack control = craft(helper, 3, 2, iron, iron, iron, f, e, f);
		helper.assertTrue(!WatchtowerItems.HAWKEYE.pieces().containsValue(control.getItem()), "iron ingots made a Hawkeye piece");
		ItemStack plain = craft(helper, 3, 2, l, l, l, l, e, l);
		helper.assertTrue(!WatchtowerItems.HAWKEYE.pieces().containsValue(plain.getItem()), "leather alone made a Hawkeye piece");
		TowerTestSupport.result("hawkeyeRecipes", "four recipes craft; controls gave " + control.getItem() + " and " + plain.getItem());
		helper.succeed();
	}

	@GameTest
	public void theSetHasItsDefenseAndFeathersRepairIt(final GameTestHelper helper) {
		var spec = WatchtowerItems.HAWKEYE.spec();
		helper.assertTrue(spec.helmet() == 3 && spec.chestplate() == 7 && spec.leggings() == 5 && spec.boots() == 2 && spec.toughness() == 1.0F, "defense " + spec);
		helper.assertTrue(new ItemStack(WatchtowerItems.HAWKFEATHER).is(WatchtowerItems.HAWKFEATHER_REPAIR) && !new ItemStack(Items.FEATHER).is(WatchtowerItems.HAWKFEATHER_REPAIR),
			"the feather does not repair the set, or a plain feather does");
		for (Item item : WatchtowerItems.HAWKEYE.pieces().values()) {
			helper.assertTrue(Exclusives.isExclusive(item) && BuiltInRegistries.ITEM.getKey(item).getPath().startsWith("hawkeye_"), "a set piece is not exclusive: " + item);
		}
		helper.assertTrue(Exclusives.isExclusive(WatchtowerItems.HAWKFEATHER) && Exclusives.isExclusive(WatchtowerItems.SIGNAL_EMBER)
			&& Exclusives.isExclusive(WatchtowerItems.CAPTAINS_INSIGNIA) && Exclusives.isExclusive(WatchtowerItems.LONGBOW), "a tower item is not exclusive");
		helper.assertTrue(TowerTestSupport.site().newUnique().is(WatchtowerItems.LONGBOW), "the site's unique is not the longbow");
		helper.assertTrue(WatchtowerItems.EFFECT_ID.equals(RuinUniques.effectOf(new ItemStack(WatchtowerItems.LONGBOW))), "the longbow has no hawkeye effect");
		helper.succeed();
	}

	private static void wear(final ServerPlayer player, final EquipmentSlot... slots) {
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(piece(slot)));
		}
	}

	private static void expectSpeed(final GameTestHelper helper, final ServerPlayer player, final double base, final double factor, final String what) {
		double got = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(Math.abs(got - base * factor) < 1.0E-6, what + ": speed " + got + " want " + base * factor);
	}

	@GameTest(maxTicks = 200)
	public void twoPiecesGiveTenPercentSpeedAndFourPiecesKeepIt(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper);
		double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		wear(player, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
		helper.startSequence()
			.thenWaitUntil(() -> expectSpeed(helper, player, base, 1.0 + WatchtowerItems.SET_SPEED, "two pieces"))
			.thenExecute(() -> wear(player, EquipmentSlot.LEGS, EquipmentSlot.FEET))
			.thenIdle(25)
			.thenWaitUntil(() -> expectSpeed(helper, player, base, 1.0 + WatchtowerItems.SET_SPEED, "four pieces"))
			.thenExecute(() -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY))
			.thenExecute(() -> player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY))
			.thenExecute(() -> player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY))
			.thenWaitUntil(() -> expectSpeed(helper, player, base, 1.0, "one piece left"))
			.thenExecute(() -> {
				TowerTestSupport.result("hawkeyeSpeed", "base=" + base + " twoPieces=" + base * (1.0 + WatchtowerItems.SET_SPEED));
				TowerTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	private static float lost(final ServerLevel level, final Cow cow, final net.minecraft.world.damagesource.DamageSource source, final float amount) {
		cow.damageCooldownTime = 0;
		float before = cow.getHealth();
		cow.hurtServer(level, source, amount);
		return before - cow.getHealth();
	}

	@GameTest(maxTicks = 100)
	public void fourPiecesMakeArrowsHitTwentyPercentHarderAndNothingElseDoes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper);
		Cow cow = TowerTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		net.minecraft.world.damagesource.DamageSource shot = level.damageSources().arrow(arrow, player);
		net.minecraft.world.damagesource.DamageSource swing = level.damageSources().playerAttack(player);
		float plain = lost(level, cow, shot, 6.0F);
		float plainSwing = lost(level, cow, swing, 6.0F);
		wear(player, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);
		float three = lost(level, cow, shot, 6.0F);
		wear(player, EquipmentSlot.FEET);
		float four = lost(level, cow, shot, 6.0F);
		float fourSwing = lost(level, cow, swing, 6.0F);
		TowerTestSupport.result("hawkeyeArrowBonus", "plain=" + plain + " three=" + three + " four=" + four + " swingPlain=" + plainSwing + " swingFour=" + fourSwing);
		helper.assertTrue(plain > 0.0F && Math.abs(three - plain) < 0.01F, "three pieces changed the arrow damage: " + plain + " -> " + three);
		helper.assertTrue(Math.abs(four - plain * (1.0F + HawkeyeEffect.SET_ARROW_BONUS)) < 0.05F, "four pieces: " + four + " is not " + (1.0F + HawkeyeEffect.SET_ARROW_BONUS) + " times " + plain);
		helper.assertTrue(Math.abs(fourSwing - plainSwing) < 0.01F, "four pieces changed a melee blow: " + plainSwing + " -> " + fourSwing);
		arrow.discard();
		cow.discard();
		TowerTestSupport.leave(helper, player);
		helper.succeed();
	}

	private static Arrow shoot(final ServerLevel level, final ServerPlayer player, final ItemStack bow, final Vec3 at, final Vec3 motion) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
		arrow.snapTo(at.x, at.y, at.z);
		arrow.setDeltaMovement(motion);
		level.addFreshEntity(arrow);
		return arrow;
	}

	private static List<Arrow> arrowsOf(final ServerLevel level, final ServerPlayer player) {
		return level.getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(40.0), arrow -> arrow.getOwner() == player);
	}

	@GameTest(maxTicks = 100)
	public void everyFifthArrowOfTheLongbowPiercesTwoTargets(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper);
		ItemStack bow = new ItemStack(WatchtowerItems.LONGBOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		HawkeyeEffect.reset(player.getUUID());
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
		StringBuilder line = new StringBuilder();
		for (int shot = 1; shot <= 10; shot++) {
			Arrow arrow = shoot(level, player, bow, at, new Vec3(0.0, 0.0, 0.0));
			boolean sharp = shot % HawkeyeEffect.EVERY == 0;
			List<Arrow> mine = arrowsOf(level, player);
			helper.assertTrue(mine.size() == 1, "shot " + shot + ": " + mine.size() + " arrows stand for the player");
			Arrow now = mine.get(0);
			line.append(shot).append(':').append(now.getPierceLevel()).append(' ');
			helper.assertTrue(now.entityTags().contains(HawkeyeEffect.ARROW_TAG) == sharp, "shot " + shot + ": the hawkeye tag is " + now.entityTags().contains(HawkeyeEffect.ARROW_TAG));
			helper.assertTrue(now.getPierceLevel() == (sharp ? HawkeyeEffect.PIERCE : 0), "shot " + shot + ": the pierce level is " + now.getPierceLevel());
			helper.assertTrue(sharp == arrow.isRemoved() || !sharp, "shot " + shot + ": the plain arrow of a sharp shot stayed in the world");
			now.discard();
		}
		TowerTestSupport.result("longbowFifth", "pierceByShot=" + line + "count=" + HawkeyeEffect.count(player.getUUID()));
		helper.assertTrue(HawkeyeEffect.count(player.getUUID()) == 10, "the count is " + HawkeyeEffect.count(player.getUUID()));
		TowerTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aLongbowArrowThatHitsGivesGlowingForOneHundredTicksAndAPlainArrowDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper);
		ItemStack bow = new ItemStack(WatchtowerItems.LONGBOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		HawkeyeEffect.reset(player.getUUID());
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
		Arrow sharp = null;
		for (int shot = 1; shot <= HawkeyeEffect.EVERY; shot++) {
			shoot(level, player, bow, at, Vec3.ZERO);
		}
		for (Arrow arrow : arrowsOf(level, player)) {
			if (arrow.entityTags().contains(HawkeyeEffect.ARROW_TAG)) {
				sharp = arrow;
			}
		}
		helper.assertTrue(sharp != null, "no sharp arrow after five shots");
		Arrow plain = null;
		for (Arrow arrow : arrowsOf(level, player)) {
			if (!arrow.entityTags().contains(HawkeyeEffect.ARROW_TAG)) {
				plain = arrow;
			}
		}
		Cow struck = TowerTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		Cow spared = TowerTestSupport.cow(helper, 5.5, 3.5, 1_000_000.0F);
		struck.damageCooldownTime = 0;
		struck.hurtServer(level, level.damageSources().arrow(sharp, player), 2.0F);
		spared.damageCooldownTime = 0;
		spared.hurtServer(level, level.damageSources().arrow(plain, player), 2.0F);
		MobEffectInstance glow = struck.getEffect(MobEffects.GLOWING);
		TowerTestSupport.result("longbowGlow", "struck=" + (glow == null ? "none" : glow.getDuration()) + " plainArrow=" + (spared.getEffect(MobEffects.GLOWING) == null ? "none" : "glowing"));
		helper.assertTrue(glow != null && glow.getDuration() > HawkeyeEffect.GLOW_TICKS - 5 && glow.getDuration() <= HawkeyeEffect.GLOW_TICKS, "glowing is " + glow);
		helper.assertTrue(spared.getEffect(MobEffects.GLOWING) == null, "a plain arrow gave Glowing");
		for (Arrow arrow : arrowsOf(level, player)) {
			arrow.discard();
		}
		struck.discard();
		spared.discard();
		TowerTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aSharpArrowFliesThroughTwoCowsAndBothGlow(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper);
		ItemStack bow = new ItemStack(WatchtowerItems.LONGBOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		HawkeyeEffect.reset(player.getUUID());
		for (int i = 0; i < HawkeyeEffect.EVERY - 1; i++) {
			shoot(level, player, bow, helper.absoluteVec(new Vec3(1.5, 6.0, 1.5)), Vec3.ZERO).discard();
		}
		Cow first = TowerTestSupport.cow(helper, 4.5, 4.5, 1_000_000.0F);
		Cow second = TowerTestSupport.cow(helper, 7.5, 4.5, 1_000_000.0F);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.6, 4.5));
		shoot(level, player, bow, at, new Vec3(1.2, 0.0, 0.0));
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(first.getEffect(MobEffects.GLOWING) != null && second.getEffect(MobEffects.GLOWING) != null,
					"the cows glow: first " + (first.getEffect(MobEffects.GLOWING) != null) + " second " + (second.getEffect(MobEffects.GLOWING) != null));
			})
			.thenExecute(() -> {
				TowerTestSupport.result("longbowFlight", "firstGlowing=true secondGlowing=true");
				first.discard();
				second.discard();
				for (Arrow arrow : arrowsOf(level, player)) {
					arrow.discard();
				}
				TowerTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}
}
