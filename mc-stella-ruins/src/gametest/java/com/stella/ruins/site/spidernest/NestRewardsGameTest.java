package com.stella.ruins.site.spidernest;

import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Exclusives;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Items 8 to 10: the Silkweave recipes, the bow, Widow's Fang, and the drops and tables of the nest. */
public class NestRewardsGameTest {
	static final int ROLLS = 4000;
	static final int DRAW_SHOTS = 64;
	/** A shot's speed varies a little: the arrow's aim gets a random spread that is not normalized again. */
	static final float ARROW_SPREAD = 0.08F;

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed, final boolean entity) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
		if (entity) {
			Mob mob = EntityTypes.SPIDER.create(level, EntitySpawnReason.COMMAND);
			builder.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		return table.getRandomItems(builder.create(entity ? LootContextParamSets.ENTITY : LootContextParamSets.CHEST), seed);
	}

	private static ItemStack craft(final GameTestHelper helper, final int width, final int height, final Item... cells) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Item cell : cells) {
			stacks.add(cell == null ? ItemStack.EMPTY : new ItemStack(cell));
		}
		CraftingInput input = CraftingInput.of(width, height, stacks);
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	@GameTest
	public void silkweaveRecipesCraftTheLightArmorAndTheSilkstringBow(final GameTestHelper helper) {
		Item s = NestItems.SILKWEAVE_ITEM;
		helper.assertTrue(craft(helper, 3, 2, s, s, s, s, null, s).is(NestItems.SILKWEAVE_HELMET), "no hood from 5 silkweave");
		helper.assertTrue(craft(helper, 3, 3, s, null, s, s, s, s, s, s, s).is(NestItems.SILKWEAVE_CHESTPLATE), "no vest from 8 silkweave");
		helper.assertTrue(craft(helper, 3, 3, s, s, s, s, null, s, s, null, s).is(NestItems.SILKWEAVE_LEGGINGS), "no leggings from 7 silkweave");
		helper.assertTrue(craft(helper, 3, 2, s, null, s, s, null, s).is(NestItems.SILKWEAVE_BOOTS), "no boots from 4 silkweave");
		ItemStack bow = craft(helper, 3, 3, null, Items.STICK, s, Items.STICK, null, s, null, Items.STICK, s);
		helper.assertTrue(bow.is(NestItems.SILKSTRING_BOW) && bow.getCount() == 1, "no Silkstring Bow from sticks and silkweave: " + bow);
		helper.assertTrue(craft(helper, 3, 3, null, Items.STICK, Items.STRING, Items.STICK, null, Items.STRING, null, Items.STICK, Items.STRING).is(Items.BOW), "the plain bow recipe broke");
		helper.assertTrue(craft(helper, 3, 2, Items.STRING, Items.STRING, Items.STRING, Items.STRING, null, Items.STRING).isEmpty(), "string made a hood");
		NestTestSupport.result("recipes", "hood,vest,leggings,boots,bow craft from silkweave");
		helper.succeed();
	}

	@GameTest
	public void theSilkweaveArmorIsLeatherTierWithFivePercentSpeedOnEveryPiece(final GameTestHelper helper) {
		Item[] pieces = {NestItems.SILKWEAVE_HELMET, NestItems.SILKWEAVE_CHESTPLATE, NestItems.SILKWEAVE_LEGGINGS, NestItems.SILKWEAVE_BOOTS};
		double[] defense = {2, 5, 4, 2};
		EquipmentSlotGroup[] slots = {EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};
		for (int i = 0; i < pieces.length; i++) {
			ItemAttributeModifiers mods = new ItemStack(pieces[i]).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			double armor = 0;
			int speed = 0;
			for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
				if (entry.attribute().is(Attributes.ARMOR)) {
					armor += entry.modifier().amount();
				}
				if (entry.attribute().is(Attributes.MOVEMENT_SPEED)) {
					speed++;
					helper.assertTrue(entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE && Math.abs(entry.modifier().amount() - 0.05) < 1.0E-9 && entry.slot() == slots[i],
						BuiltInRegistries.ITEM.getKey(pieces[i]) + " speed modifier " + entry);
				}
			}
			helper.assertTrue(armor == defense[i], BuiltInRegistries.ITEM.getKey(pieces[i]) + " defense " + armor + ", not " + defense[i]);
			helper.assertTrue(speed == 1, BuiltInRegistries.ITEM.getKey(pieces[i]) + " has " + speed + " speed modifiers");
		}
		ServerPlayer player = NestTestSupport.join(helper);
		double[] speeds = new double[2];
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				speeds[0] = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NestItems.SILKWEAVE_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(NestItems.SILKWEAVE_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(NestItems.SILKWEAVE_LEGGINGS));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(NestItems.SILKWEAVE_BOOTS));
			})
			.thenIdle(3)
			.thenExecute(() -> {
				speeds[1] = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
				NestTestSupport.result("armorSpeed", "bare=" + speeds[0] + " worn=" + speeds[1] + " armor=" + player.getArmorValue());
				helper.assertTrue(Math.abs(speeds[1] - speeds[0] * 1.20) < 1.0E-6 && speeds[1] > speeds[0], "movement speed " + speeds[0] + " -> " + speeds[1]);
				helper.assertTrue(player.getArmorValue() == 13, "the full set gives armor " + player.getArmorValue());
				NestTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	private static float arrowSpeed(final GameTestHelper helper, final ServerPlayer player, final Item bowItem, final int held) {
		player.getInventory().setItem(9, new ItemStack(Items.ARROW, DRAW_SHOTS));
		double total = 0.0;
		for (int shot = 0; shot < DRAW_SHOTS; shot++) {
			float speed = shotSpeed(helper, player, bowItem, held);
			helper.assertTrue(speed > 0.0F, "no arrow from draw " + held + ", shot " + shot);
			total += speed;
		}
		return (float) (total / DRAW_SHOTS);
	}

	private static float shotSpeed(final GameTestHelper helper, final ServerPlayer player, final Item bowItem, final int held) {
		ServerLevel level = helper.getLevel();
		ItemStack bow = new ItemStack(bowItem);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);
		Set<java.util.UUID> before = new HashSet<>();
		AABB area = player.getBoundingBox().inflate(8.0);
		level.getEntitiesOfClass(AbstractArrow.class, area).forEach(arrow -> before.add(arrow.getUUID()));
		int duration = bow.getUseDuration(player);
		bowItem.releaseUsing(bow, level, player, duration - held);
		float speed = -1.0F;
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, area)) {
			if (arrow.getOwner() == player && !before.contains(arrow.getUUID())) {
				speed = (float) arrow.getDeltaMovement().length();
				arrow.discard();
			}
		}
		return speed;
	}

	@GameTest
	public void theSilkstringBowDrawsTwentyPercentFaster(final GameTestHelper helper) {
		ServerPlayer player = NestTestSupport.join(helper);
		player.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
		helper.assertTrue(!player.getProjectile(new ItemStack(Items.BOW)).isEmpty(), "the test player found no arrows in its inventory");
		BowItem plain = (BowItem) Items.BOW;
		int plainFullTicks = 0;
		while (BowItem.getPowerForTime(plainFullTicks) < 1.0F) {
			plainFullTicks++;
		}
		int silkFullTicks = 0;
		while (Math.ceil(silkFullTicks * SilkstringBow.DRAW_FACTOR) < plainFullTicks) {
			silkFullTicks++;
		}
		StringBuilder log = new StringBuilder();
		for (int held : new int[] {6, 8, 10, 12, 15, silkFullTicks, plainFullTicks}) {
			int expectedTicks = (int) Math.ceil(held * SilkstringBow.DRAW_FACTOR);
			float silk = arrowSpeed(helper, player, NestItems.SILKSTRING_BOW, held);
			float same = arrowSpeed(helper, player, plain, expectedTicks);
			float slower = arrowSpeed(helper, player, plain, held);
			log.append(held).append(':').append(silk).append('/').append(same).append('/').append(slower).append(' ');
			helper.assertTrue(silk > 0.0F && Math.abs(silk - same) < ARROW_SPREAD, "a draw of " + held + " ticks gave " + silk + ", the plain draw of " + expectedTicks + " gave " + same);
			helper.assertTrue(held >= silkFullTicks || silk > slower + 0.01F, "a draw of " + held + " ticks was not faster than the plain bow: " + silk + " vs " + slower);
		}
		float full = arrowSpeed(helper, player, NestItems.SILKSTRING_BOW, silkFullTicks);
		float plainPartial = arrowSpeed(helper, player, plain, silkFullTicks);
		float plainFull = arrowSpeed(helper, player, plain, plainFullTicks);
		NestTestSupport.result("bowDraw", "shotsPerDraw=" + DRAW_SHOTS + " " + log + "fullTicks=" + silkFullTicks + " full=" + full + " plainPartial=" + plainPartial);
		helper.assertTrue(Math.abs(full - plainFull) < ARROW_SPREAD, "the silk bow was not at full power after " + silkFullTicks + " ticks");
		helper.assertTrue(silkFullTicks < plainFullTicks && full > plainPartial + 0.01F, "the silk bow reached full power no sooner than the plain bow");
		NestTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void everyNestItemIsExclusiveAndTheUniqueIsWidowsFang(final GameTestHelper helper) {
		for (Item item : new Item[] {NestItems.SILKWEAVE_ITEM, NestItems.VENOM_SAC, NestItems.MATRIARCH_FANG, NestItems.SILKWEAVE_HELMET, NestItems.SILKWEAVE_CHESTPLATE,
			NestItems.SILKWEAVE_LEGGINGS, NestItems.SILKWEAVE_BOOTS, NestItems.SILKSTRING_BOW, NestItems.WIDOWS_FANG}) {
			helper.assertTrue(Exclusives.isExclusive(item), BuiltInRegistries.ITEM.getKey(item) + " is not exclusive");
		}
		helper.assertTrue(NestTestSupport.site().newUnique().is(NestItems.WIDOWS_FANG), "the site's unique is not Widow's Fang");
		helper.assertTrue(WidowEffect.ID.equals(RuinUniques.effectOf(new ItemStack(NestItems.WIDOWS_FANG))), "Widow's Fang has no widow effect");
		helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(com.stella.ruins.StellaRuins.id("egg_sac")), "the egg sac has an item");
		helper.succeed();
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261005L);
		int weaverRolls = 0;
		int silk = 0;
		boolean one = false;
		boolean two = false;
		int venomRolls = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, NestMobs.WEAVER_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(NestItems.SILKWEAVE_ITEM) && stack.getCount() >= 1 && stack.getCount() <= 2, "the Weaver dropped " + stack);
				weaverRolls++;
				silk += stack.getCount();
				one |= stack.getCount() == 1;
				two |= stack.getCount() == 2;
			}
			for (ItemStack stack : roll(level, NestMobs.CRAWLER_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(NestItems.VENOM_SAC) && stack.getCount() == 1, "the Crawler dropped " + stack);
				venomRolls++;
			}
		}
		double weaverRate = weaverRolls / (double) ROLLS;
		double venomRate = venomRolls / (double) ROLLS;
		NestTestSupport.result("mobDrops", "rolls=" + ROLLS + " silkRate=" + weaverRate + " venomRate=" + venomRate + " silk=" + silk);
		helper.assertTrue(Math.abs(weaverRate - 0.60) <= 0.60 * 0.05, "silkweave rate " + weaverRate);
		helper.assertTrue(Math.abs(venomRate - 0.30) <= 0.30 * 0.10, "venom sac rate " + venomRate);
		helper.assertTrue(one && two, "silkweave counts did not cover 1 and 2");
		int fangs = 0;
		for (int i = 0; i < 400; i++) {
			int mine = 0;
			int silkweave = 0;
			for (ItemStack stack : roll(level, NestMobs.MATRIARCH_DROPS, master.nextLong(), true)) {
				if (stack.is(NestItems.MATRIARCH_FANG)) {
					mine += stack.getCount();
				} else if (stack.is(NestItems.SILKWEAVE_ITEM)) {
					silkweave += stack.getCount();
				} else {
					helper.fail("the Matriarch dropped " + stack);
				}
			}
			helper.assertTrue(mine == 1 && silkweave >= 4 && silkweave <= 6, "the Matriarch gave " + mine + " fangs and " + silkweave + " silkweave");
			fangs += mine;
		}
		helper.assertTrue(fangs == 400, "fangs " + fangs);
		helper.succeed();
	}

	@GameTest
	public void theVaultTablesHoldTheCleanAndTheHatchedTreasure(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		for (int i = 0; i < 1000; i++) {
			int silk = 0;
			int string = 0;
			int emeralds = 0;
			for (ItemStack stack : roll(level, NestMobs.VAULT_LOOT, master.nextLong(), false)) {
				if (stack.is(NestItems.SILKWEAVE_ITEM)) {
					silk += stack.getCount();
				} else if (stack.is(Items.STRING)) {
					string += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else {
					helper.fail("the clean vault table gave " + stack);
				}
			}
			helper.assertTrue(silk >= 4 && silk <= 8, "roll " + i + " gave " + silk + " silkweave");
			helper.assertTrue(string >= 1 && emeralds >= 1, "roll " + i + " missed the string or the emeralds");
			int hatched = 0;
			for (ItemStack stack : roll(level, NestMobs.HATCHED_VAULT_LOOT, master.nextLong(), false)) {
				helper.assertTrue(stack.is(NestItems.SILKWEAVE_ITEM), "the hatched table gave " + stack);
				hatched += stack.getCount();
			}
			helper.assertTrue(hatched >= 2 && hatched <= 4, "roll " + i + " of the hatched table gave " + hatched + " silkweave");
		}
		helper.succeed();
	}

	/** A random source that always rolls a web: the first double of a seed under the chance. */
	private static Supplier<RandomSource> alwaysWeb() {
		long seed = 0;
		while (RandomSource.create(seed).nextDouble() >= WidowEffect.WEB_CHANCE) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	private static Supplier<RandomSource> neverWeb() {
		long seed = 0;
		while (RandomSource.create(seed).nextDouble() < WidowEffect.WEB_CHANCE) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	@GameTest
	public void widowsFangWebsTheTargetAtTwentyPercentOverFourThousandSeededTrials(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261005L);
		int webs = 0;
		for (int i = 0; i < ROLLS; i++) {
			if (WidowEffect.roll(random)) {
				webs++;
			}
		}
		double rate = webs / (double) ROLLS;
		NestTestSupport.result("widowRate", "trials=" + ROLLS + " webs=" + webs + " rate=" + rate);
		helper.assertTrue(Math.abs(rate - WidowEffect.WEB_CHANCE) <= WidowEffect.WEB_CHANCE * 0.15, "web rate " + rate + " is not within 15% of " + WidowEffect.WEB_CHANCE);
		helper.assertTrue(Math.abs(WidowEffect.WEB_CHANCE - 0.20) < 1.0E-9 && WidowEffect.POISON_TICKS == 60, "the widow constants moved");
		helper.succeed();
	}

	@GameTest
	public void aWidowsFangHitPoisonsForSixtyTicksAndWebsWhenTheRollHits(final GameTestHelper helper) {
		ServerPlayer player = NestTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(NestItems.WIDOWS_FANG));
		Cow cow = NestTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		ServerLevel level = helper.getLevel();
		net.minecraft.core.BlockPos feet = cow.blockPosition();
		WidowEffect.setRandom(player.getUUID(), alwaysWeb());
		cow.damageCooldownTime = 0;
		cow.hurtServer(level, player.damageSources().playerAttack(player), 4.0F);
		MobEffectInstance poison = cow.getEffect(MobEffects.POISON);
		NestTestSupport.result("widowHit", "poison=" + (poison == null ? "none" : poison.getDuration() + "/" + poison.getAmplifier()) + " web=" + level.getBlockState(feet));
		helper.assertTrue(poison != null && poison.getAmplifier() == 0 && poison.getDuration() > 0 && poison.getDuration() <= 60, "poison is " + poison);
		helper.assertTrue(level.getBlockState(feet).is(Blocks.COBWEB), "a hit that rolled a web left " + level.getBlockState(feet));
		level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
		cow.removeEffect(MobEffects.POISON);
		WidowEffect.setRandom(player.getUUID(), neverWeb());
		cow.damageCooldownTime = 0;
		cow.hurtServer(level, player.damageSources().playerAttack(player), 4.0F);
		helper.assertTrue(cow.getEffect(MobEffects.POISON) != null, "the second hit did not poison");
		helper.assertTrue(level.getBlockState(feet).isAir(), "a failed roll left a web: " + level.getBlockState(feet));
		cow.removeEffect(MobEffects.POISON);
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		WidowEffect.setRandom(player.getUUID(), alwaysWeb());
		cow.damageCooldownTime = 0;
		cow.hurtServer(level, player.damageSources().playerAttack(player), 4.0F);
		helper.assertTrue(cow.getEffect(MobEffects.POISON) == null && level.getBlockState(feet).isAir(), "the effect fired without the fang in a hand");
		WidowEffect.setRandom(player.getUUID(), null);
		NestTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}
}
