package com.stella.villages;

import com.stella.villages.mission.MissionService;
import com.stella.villages.mission.QuestRewards;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/** The duke's quest rewards: tiers by mob, a budget that grows with them, and the weighted pool it buys from. */
public final class QuestRewardGameTest {
	private static final int ROLLS = 200;
	private static final long SEED = 20261003L;
	private static final Set<Item> ORES = Set.of(
		Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD
	);

	private enum Category { ORE, GEAR, BOOK, FOOD }

	@GameTest
	public void everyQuestTypeHasATier(final GameTestHelper helper) {
		StringBuilder listing = new StringBuilder();
		Set<Integer> seen = new TreeSet<>();
		for (Identifier id : MissionService.defaultOfferPool()) {
			int tier = QuestRewards.tierOf(BuiltInRegistries.ENTITY_TYPE.getValue(id));
			listing.append(id.getPath()).append('=').append(tier).append(' ');
			seen.add(tier);
			helper.assertTrue(tier >= 1 && tier <= QuestRewards.MAX_TIER, id + " has tier " + tier);
		}
		PatrolTestSupport.result("everyQuestTypeHasATier", listing.toString().trim());
		int ravager = QuestRewards.tierOf(EntityTypes.RAVAGER);
		helper.assertTrue(ravager == QuestRewards.MAX_TIER, "the ravager is tier " + ravager);
		helper.assertTrue(QuestRewards.tierOf(EntityTypes.EVOKER) == QuestRewards.MAX_TIER, "the evoker is not in the top ordinary tier");
		helper.assertTrue(QuestRewards.tierOf(EntityTypes.ELDER_GUARDIAN) == QuestRewards.MAX_TIER, "the elder guardian is not in the top ordinary tier");
		helper.assertTrue(QuestRewards.tierOf(EntityTypes.ZOMBIE) < ravager, "the zombie is not below the ravager");
		helper.assertTrue(seen.size() >= 3, "the pool spreads over only the tiers " + seen);
		helper.succeed();
	}

	@GameTest
	public void harderMobsPayMore(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double zombie = mean(level, EntityTypes.ZOMBIE, false, 10);
		double ravager = mean(level, EntityTypes.RAVAGER, false, 10);
		double eliteZombie = mean(level, EntityTypes.ZOMBIE, true, 10);
		double threatZombie = mean(level, EntityTypes.ZOMBIE, false, 100);
		PatrolTestSupport.result("harderMobsPayMore", "zombie=" + zombie + " ravager=" + ravager + " eliteZombie=" + eliteZombie + " threatZombie=" + threatZombie);
		helper.assertTrue(zombie > 0.0, "a zombie quest pays nothing");
		helper.assertTrue(ravager >= 2.0 * zombie, "a ravager pays " + ravager + ", a zombie " + zombie);
		helper.assertTrue(eliteZombie > zombie * 1.3, "an elite zombie pays " + eliteZombie + ", an ordinary one " + zombie);
		helper.assertTrue(threatZombie > zombie * 1.3, "a level 100 zombie pays " + threatZombie + ", a level 10 one " + zombie);
		helper.succeed();
	}

	@GameTest
	public void poolHasEveryCategory(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(SEED);
		Set<Category> seen = EnumSet.noneOf(Category.class);
		Set<Item> ores = new HashSet<>();
		for (int roll = 0; roll < ROLLS; roll++) {
			for (ItemStack stack : QuestRewards.roll(level, EntityTypes.VINDICATOR, false, 10, random)) {
				helper.assertTrue(stack.getCount() >= 1 && stack.getCount() <= stack.getMaxStackSize(), "an invalid stack: " + stack);
				Category category = categoryOf(stack);
				if (category != null) {
					seen.add(category);
				}
				if (ORES.contains(stack.getItem())) {
					ores.add(stack.getItem());
				}
			}
		}
		PatrolTestSupport.result("poolHasEveryCategory", "categories=" + seen + " ores=" + ores);
		helper.assertTrue(seen.equals(EnumSet.allOf(Category.class)), "the rolls reached only " + seen);
		helper.assertTrue(ores.size() >= 4, "the ore rolls reached only " + ores);
		helper.succeed();
	}

	@GameTest
	public void noNetheriteBelowTheTopTier(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(SEED);
		int checked = 0;
		for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.VINDICATOR, EntityTypes.RAVAGER, EntityTypes.EVOKER)) {
			for (boolean elite : new boolean[] {false, true}) {
				for (int roll = 0; roll < ROLLS; roll++) {
					for (ItemStack stack : QuestRewards.roll(level, type, elite, roll % 2 == 0 ? 0 : 150, random)) {
						checked++;
						String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
						helper.assertTrue(!id.contains("netherite") && !stack.is(Items.ANCIENT_DEBRIS), type + " paid " + id);
					}
				}
			}
		}
		PatrolTestSupport.result("noNetheriteBelowTheTopTier", "stacks=" + checked);
		helper.assertTrue(checked > 0, "the rolls paid nothing");
		helper.succeed();
	}

	@GameTest
	public void tippedFoodIsHarmless(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(SEED);
		int tipped = 0;
		for (int roll = 0; roll < ROLLS; roll++) {
			for (ItemStack stack : QuestRewards.roll(level, EntityTypes.RAVAGER, true, 60, random)) {
				Consumable consumable = stack.get(DataComponents.CONSUMABLE);
				if (consumable == null) {
					continue;
				}
				for (var effect : consumable.onConsumeEffects()) {
					if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
						tipped++;
						helper.assertTrue(!apply.effects().isEmpty(), "an empty tip on " + stack);
						for (var instance : apply.effects()) {
							helper.assertTrue(instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL,
								"harmful effect " + instance.getEffect().getRegisteredName() + " on " + stack);
						}
					}
				}
			}
		}
		PatrolTestSupport.result("tippedFoodIsHarmless", "tippedStacks=" + tipped);
		helper.assertTrue(tipped > 0, "no food was tipped in " + ROLLS + " rolls");
		helper.succeed();
	}

	private static double mean(final ServerLevel level, final EntityType<?> type, final boolean elite, final int threat) {
		RandomSource random = RandomSource.create(SEED);
		double total = 0.0;
		for (int roll = 0; roll < ROLLS; roll++) {
			for (ItemStack stack : QuestRewards.roll(level, type, elite, threat, random)) {
				total += QuestRewards.value(stack);
			}
		}
		return total / ROLLS;
	}

	private static Category categoryOf(final ItemStack stack) {
		if (stack.is(Items.ENCHANTED_BOOK)) {
			return Category.BOOK;
		}
		if (ORES.contains(stack.getItem())) {
			return Category.ORE;
		}
		if (stack.isEnchanted()) {
			return Category.GEAR;
		}
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable != null && consumable.onConsumeEffects().stream().anyMatch(ApplyStatusEffectsConsumeEffect.class::isInstance)) {
			return Category.FOOD;
		}
		return null;
	}
}
