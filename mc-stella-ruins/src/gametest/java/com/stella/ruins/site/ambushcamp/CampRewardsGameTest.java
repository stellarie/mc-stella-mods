package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Exclusives;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 7 and 8: the Bandit's Mark affix, the Cutthroat's Cloak, the vault table, and the template recipe. */
public class CampRewardsGameTest {
	static final int ROLLS = 4000;

	private static ItemStack smith(final ServerPlayer player, final ItemStack base) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(CampItems.BANDITS_MARK_AFFIX.template()));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(CampItems.BANDITS_MARK));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
	}

	private static List<ItemAttributeModifiers.Entry> markEntries(final ItemStack stack) {
		List<ItemAttributeModifiers.Entry> out = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.modifier().id().getPath().startsWith("affix/bandits_mark/")) {
				out.add(entry);
			}
		}
		return out;
	}

	private static Supplier<RandomSource> seeded(final boolean crit) {
		long seed = 0;
		while ((RandomSource.create(seed).nextDouble() < BanditsMarkEffect.CHANCE) != crit) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	@GameTest
	public void theBanditsMarkAppliesToASwordAnAxeAndAMaceAndRefusesTheRest(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		for (Item item : new Item[] {Items.IRON_SWORD, Items.DIAMOND_AXE, Items.MACE}) {
			ItemStack result = smith(player, new ItemStack(item));
			helper.assertTrue(!result.isEmpty(), item + " got no result at the smithing table");
			helper.assertTrue("bandits_mark".equals(RuinAffixes.affixOf(result)), item + " affix is " + RuinAffixes.affixOf(result));
			List<ItemAttributeModifiers.Entry> entries = markEntries(result);
			helper.assertTrue(entries.size() == 1, item + " modifiers " + entries.size());
			ItemAttributeModifiers.Entry entry = entries.getFirst();
			helper.assertTrue(entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				&& Math.abs(entry.modifier().amount() - 0.10) < 1.0E-9 && entry.slot() == EquipmentSlotGroup.MAINHAND, item + " modifier " + entry);
			ItemLore lore = result.get(DataComponents.LORE);
			boolean loreLine = lore != null && lore.lines().stream().anyMatch(line -> line.getContents() instanceof TranslatableContents c && c.getKey().equals("affix.mod-stella-ruins.bandits_mark"));
			helper.assertTrue(loreLine, item + " has no affix lore line");
		}
		for (Item item : new Item[] {Items.BOW, Items.CROSSBOW, Items.IRON_SHOVEL, Items.STICK, Items.IRON_CHESTPLATE}) {
			helper.assertTrue(smith(player, new ItemStack(item)).isEmpty(), "the affix took " + item);
		}
		CampTestSupport.result("affix", "sword, axe, mace take +10% attack damage; bow, crossbow, shovel, stick, chestplate refused");
		CampTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aMarkedSwordHitsTenPercentHarderThanAPlainOne(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		ItemStack plain = new ItemStack(Items.IRON_SWORD);
		ItemStack marked = smith(player, plain);
		double[] damage = new double[2];
		player.setItemSlot(EquipmentSlot.MAINHAND, plain);
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				damage[0] = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
				player.setItemSlot(EquipmentSlot.MAINHAND, marked);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				damage[1] = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
				CampTestSupport.result("attackDamage", "plain=" + damage[0] + " marked=" + damage[1]);
				helper.assertTrue(Math.abs(damage[1] - damage[0] * 1.10) < 1.0E-6 && damage[1] > damage[0], "attack damage " + damage[0] + " -> " + damage[1]);
				CampTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest
	public void theCritRollLandsAtTenPercentOverFourThousandSeededTrials(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261005L);
		int crits = 0;
		for (int i = 0; i < ROLLS; i++) {
			if (BanditsMarkEffect.roll(random)) {
				crits++;
			}
		}
		double rate = crits / (double) ROLLS;
		CampTestSupport.result("critRate", "trials=" + ROLLS + " crits=" + crits + " rate=" + rate);
		helper.assertTrue(Math.abs(rate - BanditsMarkEffect.CHANCE) <= BanditsMarkEffect.CHANCE * 0.15, "crit rate " + rate + " is not within 15% of " + BanditsMarkEffect.CHANCE);
		helper.succeed();
	}

	@GameTest
	public void aCritAddsHalfTheDamageThroughTheAttackHook(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, smith(player, new ItemStack(Items.IRON_SWORD)));
		Cow cow = CampTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		BanditsMarkEffect.setRandom(player.getUUID(), seeded(true));
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		float lost = 40.0F - cow.getHealth();
		CampTestSupport.result("critHit", "hit=8.0 lost=" + lost);
		helper.assertTrue(Math.abs(lost - 12.0F) < 1.0E-3F, "a crit hit of 8 took " + lost + ", not 12");
		BanditsMarkEffect.setRandom(player.getUUID(), null);
		CampTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}

	@GameTest
	public void noCritLandsWhenTheRollFailsOrTheMarkIsAbsent(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, smith(player, new ItemStack(Items.IRON_SWORD)));
		Cow cow = CampTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		BanditsMarkEffect.setRandom(player.getUUID(), seeded(false));
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		helper.assertTrue(Math.abs((40.0F - cow.getHealth()) - 8.0F) < 1.0E-3F, "a failed roll still added damage: " + (40.0F - cow.getHealth()));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		BanditsMarkEffect.setRandom(player.getUUID(), seeded(true));
		cow.setHealth(40.0F);
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		helper.assertTrue(Math.abs((40.0F - cow.getHealth()) - 8.0F) < 1.0E-3F, "a plain sword crit: " + (40.0F - cow.getHealth()));
		BanditsMarkEffect.setRandom(player.getUUID(), null);
		CampTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}

	@GameTest
	public void theCloakGivesInvisibilityForSixtyTicksAfterAHitThenRestsSixHundred(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CampItems.CUTTHROATS_CLOAK));
		CampTestSupport.vulnerable(player);
		long[] now = {10_000L};
		java.util.function.LongSupplier saved = CutthroatEffect.replaceClock(() -> now[0]);
		CutthroatEffect.reset(player.getUUID());
		try {
			helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY), "the wearer was invisible before any hit");
			hit(helper, player);
			MobEffectInstance first = player.getEffect(MobEffects.INVISIBILITY);
			helper.assertTrue(first != null && first.getAmplifier() == 0 && first.getDuration() > 0 && first.getDuration() <= CutthroatEffect.INVISIBLE_TICKS, "first hit gave " + first);
			helper.assertTrue(CutthroatEffect.readyAt(player.getUUID()) == now[0] + CutthroatEffect.COOLDOWN_TICKS, "the cloak rests until " + CutthroatEffect.readyAt(player.getUUID()));
			player.removeEffect(MobEffects.INVISIBILITY);
			now[0] += CutthroatEffect.COOLDOWN_TICKS - 1L;
			hit(helper, player);
			helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY), "the cloak fired one tick before its rest ended");
			now[0] += 1L;
			hit(helper, player);
			MobEffectInstance again = player.getEffect(MobEffects.INVISIBILITY);
			CampTestSupport.result("cloak", "first=" + first.getDuration() + " again=" + (again == null ? "none" : again.getDuration()) + " rest=" + CutthroatEffect.COOLDOWN_TICKS);
			helper.assertTrue(again != null && again.getDuration() <= CutthroatEffect.INVISIBLE_TICKS, "the cloak did not fire after its rest: " + again);
		} finally {
			CutthroatEffect.replaceClock(saved);
			CutthroatEffect.reset(player.getUUID());
			CampTestSupport.leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void theCloakDoesNothingWhenItIsNotWorn(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		CampTestSupport.vulnerable(player);
		CutthroatEffect.reset(player.getUUID());
		hit(helper, player);
		helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY), "a player without the cloak turned invisible");
		CampTestSupport.leave(helper, player);
		helper.succeed();
	}

	private static void hit(final GameTestHelper helper, final ServerPlayer player) {
		player.damageCooldownTime = 0;
		player.setHealth(player.getMaxHealth());
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2.0F);
	}

	@GameTest
	public void theVaultTableHoldsMarksCoinsAndTreasure(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(CampMobs.VAULT_LOOT);
		for (int i = 0; i < ROLLS; i++) {
			int marks = 0;
			int coins = 0;
			int emeralds = 0;
			int iron = 0;
			int leather = 0;
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
			for (ItemStack stack : table.getRandomItems(params, master.nextLong())) {
				if (stack.is(CampItems.BANDITS_MARK)) {
					marks += stack.getCount();
				} else if (stack.is(CampItems.STOLEN_COIN)) {
					coins += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else if (stack.is(Items.IRON_INGOT)) {
					iron += stack.getCount();
				} else if (stack.is(Items.LEATHER)) {
					leather += stack.getCount();
				} else {
					helper.fail("the vault table gave " + stack);
				}
			}
			helper.assertTrue(marks >= 2 && marks <= 3, "roll " + i + " gave " + marks + " marks");
			helper.assertTrue(coins >= 4 && coins <= 10, "roll " + i + " gave " + coins + " coins");
			helper.assertTrue(emeralds >= 1 && iron >= 2 && leather >= 3, "roll " + i + " missed the treasure");
		}
		CampTestSupport.result("vaultTable", "rolls=" + ROLLS + " marks=2-3 coins=4-10 emeralds iron leather");
		helper.succeed();
	}

	@GameTest
	public void everyCampItemIsExclusiveAndTheUniqueIsTheCloak(final GameTestHelper helper) {
		for (Item item : new Item[] {CampItems.BANDITS_MARK, CampItems.STOLEN_COIN, CampItems.CHIEFS_LEDGER, CampItems.BANDITS_MARK_AFFIX.template(), CampItems.CUTTHROATS_CLOAK}) {
			helper.assertTrue(Exclusives.isExclusive(item), BuiltInRegistries.ITEM.getKey(item) + " is not exclusive");
		}
		helper.assertTrue(CampTestSupport.site().newUnique().is(CampItems.CUTTHROATS_CLOAK), "the site's unique is not the cloak");
		helper.assertTrue("cutthroat".equals(RuinUniques.effectOf(new ItemStack(CampItems.CUTTHROATS_CLOAK))), "the cloak has no cutthroat effect");
		net.minecraft.world.item.equipment.Equippable equippable = new ItemStack(CampItems.CUTTHROATS_CLOAK).get(DataComponents.EQUIPPABLE);
		helper.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.CHEST, "the cloak is not a chestplate");
		helper.succeed();
	}

	private static ItemStack craft(final GameTestHelper helper, final int coins, final int ledgers) {
		ServerLevel level = helper.getLevel();
		List<ItemStack> cells = new ArrayList<>();
		for (int i = 0; i < coins; i++) {
			cells.add(new ItemStack(CampItems.STOLEN_COIN));
		}
		for (int i = 0; i < ledgers; i++) {
			cells.add(new ItemStack(CampItems.CHIEFS_LEDGER));
		}
		while (cells.size() < 9) {
			cells.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, 3, cells);
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return found.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	@GameTest
	public void theAffixTemplateCraftsFromOneLedgerAndEightCoinsOnly(final GameTestHelper helper) {
		ItemStack made = craft(helper, 8, 1);
		helper.assertTrue(made.is(CampItems.BANDITS_MARK_AFFIX.template()) && made.getCount() == 1, "one ledger and eight coins gave " + made);
		helper.assertTrue(craft(helper, 7, 1).isEmpty(), "seven coins crafted a template");
		helper.assertTrue(craft(helper, 8, 0).isEmpty(), "eight coins without a ledger crafted a template");
		helper.assertTrue(craft(helper, 8, 2).isEmpty(), "two ledgers crafted a template");
		helper.succeed();
	}
}
