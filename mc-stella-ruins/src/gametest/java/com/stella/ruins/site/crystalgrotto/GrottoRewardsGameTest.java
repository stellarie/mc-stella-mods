package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.world.Exclusives;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Items 9 to 11: the Resonant affix, the exclusive drops and the vault table, and the Echo Prism. */
public class GrottoRewardsGameTest {
	static final int ROLLS = 4000;

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed, final boolean entity) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
		if (entity) {
			Mob mob = EntityTypes.STRAY.create(level, EntitySpawnReason.COMMAND);
			builder.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		return table.getRandomItems(builder.create(entity ? LootContextParamSets.ENTITY : LootContextParamSets.CHEST), seed);
	}

	private static ItemStack smith(final ServerPlayer player, final ItemStack base) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(GrottoItems.RESONANT.template()));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(GrottoItems.RESONANT_SHARD));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
	}

	private static List<ItemAttributeModifiers.Entry> resonantEntries(final ItemStack stack) {
		List<ItemAttributeModifiers.Entry> out = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.modifier().id().getPath().startsWith("affix/resonant/")) {
				out.add(entry);
			}
		}
		return out;
	}

	@GameTest
	public void theResonantAffixAppliesToASwordAnAxeABowAndACrossbow(final GameTestHelper helper) {
		ServerPlayer player = GrottoTestSupport.join(helper);
		for (Item item : new Item[] {Items.IRON_SWORD, Items.DIAMOND_AXE, Items.BOW, Items.CROSSBOW}) {
			ItemStack result = smith(player, new ItemStack(item));
			helper.assertTrue(!result.isEmpty(), item + " got no result at the smithing table");
			helper.assertTrue("resonant".equals(RuinAffixes.affixOf(result)), item + " affix is " + RuinAffixes.affixOf(result));
			List<ItemAttributeModifiers.Entry> entries = resonantEntries(result);
			helper.assertTrue(entries.size() == 1, item + " modifiers " + entries.size());
			ItemAttributeModifiers.Entry entry = entries.getFirst();
			helper.assertTrue(entry.attribute().is(Attributes.ATTACK_SPEED) && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
				&& Math.abs(entry.modifier().amount() - GrottoItems.RESONANT_ATTACK_SPEED) < 1.0E-9 && entry.slot() == EquipmentSlotGroup.MAINHAND, item + " modifier " + entry);
			ItemLore lore = result.get(DataComponents.LORE);
			boolean loreLine = lore != null && lore.lines().stream().anyMatch(line -> line.getContents() instanceof TranslatableContents c && c.getKey().equals("affix.mod-stella-ruins.resonant"));
			helper.assertTrue(loreLine, item + " has no affix lore line");
		}
		helper.assertTrue(smith(player, new ItemStack(Items.IRON_SHOVEL)).isEmpty() && smith(player, new ItemStack(Items.STICK)).isEmpty(), "the affix took a shovel or a stick");
		GrottoTestSupport.result("affix", "sword,axe,bow,crossbow take +10% attack speed; shovel refused");
		GrottoTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aResonantSwordSwingsFasterThanAPlainOne(final GameTestHelper helper) {
		ServerPlayer player = GrottoTestSupport.join(helper);
		ItemStack plain = new ItemStack(Items.IRON_SWORD);
		ItemStack resonant = smith(player, plain);
		double[] speeds = new double[2];
		player.setItemSlot(EquipmentSlot.MAINHAND, plain);
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				speeds[0] = player.getAttributeValue(Attributes.ATTACK_SPEED);
				player.setItemSlot(EquipmentSlot.MAINHAND, resonant);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				speeds[1] = player.getAttributeValue(Attributes.ATTACK_SPEED);
				GrottoTestSupport.result("attackSpeed", "plain=" + speeds[0] + " resonant=" + speeds[1]);
				helper.assertTrue(Math.abs(speeds[1] - speeds[0] * 1.10) < 1.0E-6 && speeds[1] > speeds[0], "attack speed " + speeds[0] + " -> " + speeds[1]);
				GrottoTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest
	public void theSpellbookTestReadsIdsOfTheSpellsModOnly(final GameTestHelper helper) {
		helper.assertTrue(GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("mod-stella-spells", "spark_shot")), "spark_shot is a spellbook");
		helper.assertTrue(GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("mod-stella-spells", "battle_hymn")), "battle_hymn is a spellbook");
		helper.assertTrue(!GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("mod-stella-spells", "spark_shot_scroll")), "a scroll is no spellbook");
		helper.assertTrue(!GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("mod-stella-spells", "scroll_book")), "the scroll book is no spellbook");
		helper.assertTrue(!GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("mod-stella-spells", "return_scroll")), "the return scroll is no spellbook");
		helper.assertTrue(!GrottoItems.isSpellbook(Identifier.fromNamespaceAndPath("minecraft", "spark_shot")), "another namespace counted");
		helper.assertTrue(!GrottoItems.acceptsResonant(new ItemStack(Items.BOOK)), "a plain book was accepted");
		helper.succeed();
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261005L);
		int sentinelRolls = 0;
		int shards = 0;
		boolean one = false;
		boolean two = false;
		int gelRolls = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, GrottoMobs.SENTINEL_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(GrottoItems.RESONANT_SHARD), "the sentinel dropped " + stack);
				sentinelRolls++;
				shards += stack.getCount();
				one |= stack.getCount() == 1;
				two |= stack.getCount() == 2;
				helper.assertTrue(stack.getCount() >= 1 && stack.getCount() <= 2, "shard count " + stack.getCount());
			}
			for (ItemStack stack : roll(level, GrottoMobs.SLIME_DROPS, master.nextLong(), true)) {
				helper.assertTrue(stack.is(GrottoItems.PRISM_GEL) && stack.getCount() == 1, "the slime dropped " + stack);
				gelRolls++;
			}
		}
		double sentinelRate = sentinelRolls / (double) ROLLS;
		double gelRate = gelRolls / (double) ROLLS;
		GrottoTestSupport.result("mobDrops", "rolls=" + ROLLS + " shardRate=" + sentinelRate + " gelRate=" + gelRate + " shards=" + shards);
		helper.assertTrue(Math.abs(sentinelRate - 0.70) <= 0.70 * 0.05, "shard rate " + sentinelRate);
		helper.assertTrue(Math.abs(gelRate - 0.50) <= 0.50 * 0.05, "gel rate " + gelRate);
		helper.assertTrue(one && two, "shard counts did not cover 1 and 2");
		helper.succeed();
	}

	@GameTest
	public void theVaultTableHoldsTheAffixShardsAndTheTreasure(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		int cores = 0;
		for (int i = 0; i < ROLLS; i++) {
			int affix = 0;
			int shards = 0;
			int amethyst = 0;
			int lapis = 0;
			int emeralds = 0;
			for (ItemStack stack : roll(level, GrottoMobs.VAULT_LOOT, master.nextLong(), false)) {
				if (stack.is(GrottoItems.RESONANT.template())) {
					affix += stack.getCount();
				} else if (stack.is(GrottoItems.RESONANT_SHARD)) {
					shards += stack.getCount();
				} else if (stack.is(Items.AMETHYST_SHARD)) {
					amethyst += stack.getCount();
				} else if (stack.is(Items.LAPIS_LAZULI)) {
					lapis += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else if (stack.is(GrottoItems.RESONANT_CRYSTAL_CORE)) {
					cores += stack.getCount();
				} else {
					helper.fail("the vault table gave " + stack);
				}
			}
			helper.assertTrue(affix == 1, "roll " + i + " gave " + affix + " affix templates");
			helper.assertTrue(shards >= 2 && shards <= 4, "roll " + i + " gave " + shards + " shards");
			helper.assertTrue(amethyst >= 4 && lapis >= 3 && emeralds >= 1, "roll " + i + " missed the treasure");
		}
		double coreRate = cores / (double) ROLLS;
		GrottoTestSupport.result("vaultTable", "rolls=" + ROLLS + " coreRate=" + coreRate);
		helper.assertTrue(Math.abs(coreRate - 0.35) <= 0.35 * 0.10, "crystal core rate " + coreRate);
		helper.succeed();
	}

	@GameTest
	public void everyGrottoItemIsExclusiveAndTheUniqueIsTheEchoPrism(final GameTestHelper helper) {
		for (Item item : new Item[] {GrottoItems.RESONANT_SHARD, GrottoItems.PRISM_GEL, GrottoItems.RESONANT_CRYSTAL_CORE, GrottoItems.RESONANT.template(), GrottoItems.ECHO_PRISM}) {
			helper.assertTrue(Exclusives.isExclusive(item), BuiltInRegistries.ITEM.getKey(item) + " is not exclusive");
		}
		helper.assertTrue(com.stella.ruins.site.crystalgrotto.GrottoTestSupport.site().newUnique().is(GrottoItems.ECHO_PRISM), "the site's unique is not the Echo Prism");
		helper.assertTrue("echo".equals(com.stella.ruins.reward.RuinUniques.effectOf(new ItemStack(GrottoItems.ECHO_PRISM))), "the Echo Prism has no echo effect");
		helper.succeed();
	}

	@GameTest
	public void theEchoRepeatsAHitAtFifteenPercentOverFourThousandSeededTrials(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261005L);
		int echoes = 0;
		for (int i = 0; i < 4000; i++) {
			if (EchoEffect.roll(random)) {
				echoes++;
			}
		}
		double rate = echoes / 4000.0;
		GrottoTestSupport.result("echoRate", "trials=4000 echoes=" + echoes + " rate=" + rate);
		helper.assertTrue(Math.abs(rate - EchoEffect.CHANCE) <= EchoEffect.CHANCE * 0.15, "echo rate " + rate + " is not within 15% of " + EchoEffect.CHANCE);
		helper.succeed();
	}

	/** A random source that always rolls an echo: the first double of a seed under the chance. */
	private static Supplier<RandomSource> alwaysEcho() {
		long seed = 0;
		while (RandomSource.create(seed).nextDouble() >= EchoEffect.CHANCE) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	private static Supplier<RandomSource> neverEcho() {
		long seed = 0;
		while (RandomSource.create(seed).nextDouble() < EchoEffect.CHANCE) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	@GameTest(maxTicks = 200)
	public void anEchoHitsAgainForHalfTheDamageTenTicksLater(final GameTestHelper helper) {
		ServerPlayer player = GrottoTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(GrottoItems.ECHO_PRISM));
		Cow cow = GrottoTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		EchoEffect.setRandom(player.getUUID(), alwaysEcho());
		float before = cow.getHealth();
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		float afterHit = cow.getHealth();
		helper.assertTrue(Math.abs(before - afterHit - 8.0F) < 1.0E-4F, "the first hit did " + (before - afterHit));
		helper.assertTrue(EchoEffect.pending(player.getUUID()) == 1, "no echo is waiting: " + EchoEffect.pending(player.getUUID()));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(cow.getHealth() < afterHit - 0.1F, "the echo has not landed; health " + cow.getHealth()))
			.thenExecute(() -> {
				float echoed = afterHit - cow.getHealth();
				GrottoTestSupport.result("echoHit", "first=8.0 echo=" + echoed + " pending=" + EchoEffect.pending(player.getUUID()));
				helper.assertTrue(Math.abs(echoed - 4.0F) < 1.0E-4F, "the echo did " + echoed + ", not half of 8");
			})
			.thenExecuteAfter(15, () -> {
				helper.assertTrue(EchoEffect.pending(player.getUUID()) == 0 && Math.abs((40.0F - cow.getHealth()) - 12.0F) < 1.0E-4F,
					"the echo echoed: health " + cow.getHealth() + " pending " + EchoEffect.pending(player.getUUID()));
				EchoEffect.setRandom(player.getUUID(), null);
				GrottoTestSupport.leave(helper, player);
				cow.discard();
			})
			.thenSucceed();
	}

	@GameTest
	public void noEchoLandsWhenTheRollFails(final GameTestHelper helper) {
		ServerPlayer player = GrottoTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(GrottoItems.ECHO_PRISM));
		Cow cow = GrottoTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		EchoEffect.setRandom(player.getUUID(), neverEcho());
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		helper.assertTrue(EchoEffect.pending(player.getUUID()) == 0, "an echo waits after a failed roll");
		EchoEffect.setRandom(player.getUUID(), null);
		GrottoTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}

	@GameTest
	public void theEchoNeedsTheItemInAHand(final GameTestHelper helper) {
		ServerPlayer player = GrottoTestSupport.join(helper);
		Cow cow = GrottoTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		EchoEffect.setRandom(player.getUUID(), alwaysEcho());
		cow.damageCooldownTime = 0;
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 8.0F);
		helper.assertTrue(EchoEffect.pending(player.getUUID()) == 0, "an echo waits though no prism is held");
		EchoEffect.setRandom(player.getUUID(), null);
		GrottoTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}
}
