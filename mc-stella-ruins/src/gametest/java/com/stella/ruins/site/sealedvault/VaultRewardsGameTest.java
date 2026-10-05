package com.stella.ruins.site.sealedvault;

import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Exclusives;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Items 7, 10, 11 and 12: the vault table, the Void-touched affix, and the Abyssal Edge. */
public class VaultRewardsGameTest {
	static final int ROLLS = 4000;

	private static ItemStack smith(final ServerPlayer player, final ItemStack base) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(VaultItems.VOID_TOUCHED.template()));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(VaultItems.VOID_ESSENCE));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
	}

	/** A sword that carries the affix component and nothing else, so the effect is the only difference. */
	private static ItemStack affixed(final ServerPlayer player) {
		return smith(player, new ItemStack(Items.IRON_SWORD));
	}

	private static float lost(final Cow cow, final ServerLevel level, final ServerPlayer player, final float damage, final float start) {
		cow.damageCooldownTime = 0;
		cow.hurtServer(level, player.damageSources().playerAttack(player), damage);
		return start - cow.getHealth();
	}

	@GameTest
	public void theVoidTouchedAffixAppliesToSwordsAxesMacesTridentsBowsAndCrossbows(final GameTestHelper helper) {
		ServerPlayer player = VaultTestSupport.join(helper);
		for (Item item : new Item[] {Items.IRON_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT, Items.BOW, Items.CROSSBOW}) {
			ItemStack result = smith(player, new ItemStack(item));
			helper.assertTrue(!result.isEmpty(), item + " got no result at the smithing table");
			helper.assertTrue(VaultItems.AFFIX_ID.equals(RuinAffixes.affixOf(result)), item + " affix is " + RuinAffixes.affixOf(result));
			ItemLore lore = result.get(DataComponents.LORE);
			boolean loreLine = lore != null && lore.lines().stream().anyMatch(line -> line.getContents() instanceof TranslatableContents c && c.getKey().equals("affix.mod-stella-ruins.void_touched"));
			helper.assertTrue(loreLine, item + " has no affix lore line");
		}
		helper.assertTrue(smith(player, new ItemStack(Items.IRON_SHOVEL)).isEmpty() && smith(player, new ItemStack(Items.STICK)).isEmpty() && smith(player, new ItemStack(Items.IRON_CHESTPLATE)).isEmpty(),
			"the affix took a shovel, a stick or a chestplate");
		VaultTestSupport.result("affix", "sword,axe,mace,trident,bow,crossbow take it; shovel, stick, chestplate refused");
		VaultTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aVoidTouchedHitAddsFifteenPercentAsMagicDamageThatArmorDoesNotReduce(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = VaultTestSupport.join(helper);
		Cow plain = VaultTestSupport.cow(helper, 3.5, 3.5, 100.0F);
		Cow touched = VaultTestSupport.cow(helper, 5.5, 3.5, 100.0F);
		for (Cow cow : new Cow[] {plain, touched}) {
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
			cow.setHealth(100.0F);
			cow.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		}
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		float dealt = lost(plain, level, player, 8.0F, 100.0F);
		player.setItemSlot(EquipmentSlot.MAINHAND, affixed(player));
		float total = lost(touched, level, player, 8.0F, 100.0F);
		float bonus = total - dealt;
		float expected = 8.0F * VoidTouchedEffect.BONUS_SHARE;
		VaultTestSupport.result("voidTouchedDamage", "armorHit=" + dealt + " withAffix=" + total + " bonus=" + bonus + " expected=" + expected);
		helper.assertTrue(dealt > 0.0F && dealt < 4.0F, "armor did not reduce the plain hit: " + dealt);
		helper.assertTrue(Math.abs(bonus - expected) < 1.0E-3F, "the bonus was " + bonus + ", not 15% of the 8 point hit (" + expected + ") past armor 20");
		VaultTestSupport.leave(helper, player);
		plain.discard();
		touched.discard();
		helper.succeed();
	}

	@GameTest
	public void aVoidTouchedHitHealsTheHolderFivePercentOfTheDamage(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = VaultTestSupport.join(helper);
		Cow cow = VaultTestSupport.cow(helper, 3.5, 3.5, 100.0F);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		cow.setHealth(100.0F);
		player.setItemSlot(EquipmentSlot.MAINHAND, affixed(player));
		player.setHealth(6.0F);
		float dealt = lost(cow, level, player, 10.0F, 100.0F);
		float healed = player.getHealth() - 6.0F;
		float expected = (dealt / (1.0F + VoidTouchedEffect.BONUS_SHARE)) * VoidTouchedEffect.STEAL_SHARE;
		VaultTestSupport.result("voidTouchedSteal", "total=" + dealt + " healed=" + healed + " expected=" + expected);
		helper.assertTrue(Math.abs(healed - expected) < 1.0E-3F && healed > 0.0F, "the holder healed " + healed + ", not 5% of the hit " + expected);
		VaultTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}

	@GameTest
	public void noVoidTouchedEffectFiresWithoutTheAffix(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = VaultTestSupport.join(helper);
		Cow cow = VaultTestSupport.cow(helper, 3.5, 3.5, 100.0F);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		cow.setHealth(100.0F);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		player.setHealth(6.0F);
		float dealt = lost(cow, level, player, 10.0F, 100.0F);
		helper.assertTrue(Math.abs(dealt - 10.0F) < 1.0E-3F && player.getHealth() == 6.0F, "a plain sword took " + dealt + " and healed " + (player.getHealth() - 6.0F));
		VaultTestSupport.leave(helper, player);
		cow.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void stoppingTheServerPreventsFurtherRiftPulls(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Spider spider = EntityTypes.SPIDER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		spider.snapTo(helper.absoluteVec(new Vec3(6.5, 2.0, 3.5)));
		spider.setNoAi(true);
		spider.setNoGravity(true);
		level.addFreshEntity(spider);
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getEntitiesOfClass(Spider.class, spider.getBoundingBox().inflate(1.0),
				entity -> entity == spider && entity.isAlive()).contains(spider), "the subject spider is not indexed yet");
			try {
				Vec3 at = spider.position().add(-2.0, spider.getBbHeight() / 2.0, 0.0);
				AbyssEffect.open(level, at);
				spider.setDeltaMovement(Vec3.ZERO);
				AbyssEffect.tick();
				helper.assertTrue(spider.getDeltaMovement().lengthSqr() > 0.0, "the control rift did not pull");
				spider.setDeltaMovement(Vec3.ZERO);
				AbyssEffect.onServerStopping(level.getServer());
				AbyssEffect.tick();
				VaultTestSupport.result("riftStop", "movementAfterStop=" + spider.getDeltaMovement().lengthSqr());
				helper.assertTrue(spider.getDeltaMovement().lengthSqr() == 0.0, "a stopped server's rift still pulled");
			} finally {
				spider.discard();
			}
		});
	}

	@GameTest
	public void anAbyssalEdgeKillOpensARiftForSixtyTicksThatPullsHostilesIn(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = VaultTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(VaultItems.ABYSSAL_EDGE));
		Cow cow = VaultTestSupport.cow(helper, 3.5, 3.5, 4.0F);
		Spider spider = EntityTypes.SPIDER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		spider.snapTo(helper.absoluteVec(new Vec3(6.5, 2.0, 3.5)));
		spider.setNoAi(true);
		level.addFreshEntity(spider);
		Vec3 riftAt = cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0);
		int before = AbyssEffect.openRifts();
		cow.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
		int opened = AbyssEffect.openRifts() - before;
		helper.assertTrue(opened == 1, "the kill opened " + opened + " rifts");
		Vec3 pulled = spider.getDeltaMovement();
		AbyssEffect.tick();
		Vec3 after = spider.getDeltaMovement();
		Vec3 toward = riftAt.subtract(spider.position().add(0.0, spider.getBbHeight() / 2.0, 0.0)).normalize();
		double along = after.subtract(pulled).dot(toward);
		VaultTestSupport.result("abyss", "rifts=" + opened + " pullAlong=" + along + " spiderDistance=" + spider.position().distanceTo(riftAt));
		helper.assertTrue(along > AbyssEffect.PULL * 0.9, "the rift pulled the spider " + along + " toward itself");
		Mob far = EntityTypes.SPIDER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		far.snapTo(helper.absoluteVec(new Vec3(14.5, 2.0, 3.5)));
		far.setNoAi(true);
		level.addFreshEntity(far);
		AbyssEffect.tick();
		helper.assertTrue(far.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6, "the rift pulled a spider 8 blocks away");
		for (int i = 0; i < AbyssEffect.RIFT_TICKS + 2; i++) {
			AbyssEffect.tick();
		}
		helper.assertTrue(AbyssEffect.openRifts() == before, "the rift did not close after " + AbyssEffect.RIFT_TICKS + " ticks");
		VaultTestSupport.leave(helper, player);
		spider.discard();
		far.discard();
		helper.succeed();
	}

	private static List<ItemStack> chest(final ServerLevel level, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(VaultMobs.VAULT_LOOT);
		return table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST), seed);
	}

	@GameTest
	public void theVaultTableHoldsTheAffixEssenceAndTheTreasure(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_017L);
		int scrapRolls = 0;
		boolean essenceLow = false;
		boolean essenceHigh = false;
		for (int i = 0; i < ROLLS; i++) {
			int affix = 0;
			int essence = 0;
			int diamonds = 0;
			int emeralds = 0;
			int scrap = 0;
			for (ItemStack stack : chest(level, master.nextLong())) {
				if (stack.is(VaultItems.VOID_TOUCHED.template())) {
					affix += stack.getCount();
				} else if (stack.is(VaultItems.VOID_ESSENCE)) {
					essence += stack.getCount();
				} else if (stack.is(Items.DIAMOND)) {
					diamonds += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else if (stack.is(Items.NETHERITE_SCRAP)) {
					scrap += stack.getCount();
				} else {
					helper.fail("the vault table gave " + stack);
				}
			}
			helper.assertTrue(affix == 1, "roll " + i + " gave " + affix + " affix templates");
			helper.assertTrue(essence >= 4 && essence <= 8, "roll " + i + " gave " + essence + " void essence");
			helper.assertTrue(diamonds >= 2 && diamonds <= 5 && emeralds >= 4 && emeralds <= 10, "roll " + i + " gave " + diamonds + " diamonds and " + emeralds + " emeralds");
			essenceLow |= essence == 4;
			essenceHigh |= essence == 8;
			if (scrap > 0) {
				scrapRolls++;
				helper.assertTrue(scrap >= 1 && scrap <= 3, "roll " + i + " gave " + scrap + " scrap");
			}
		}
		double scrapRate = scrapRolls / (double) ROLLS;
		VaultTestSupport.result("vaultTable", "rolls=" + ROLLS + " scrapRate=" + scrapRate);
		helper.assertTrue(Math.abs(scrapRate - 0.6) <= 0.6 * 0.06, "scrap rate " + scrapRate);
		helper.assertTrue(essenceLow && essenceHigh, "the essence count did not cover 4 and 8");
		helper.succeed();
	}

	@GameTest
	public void everyVaultItemIsExclusiveAndTheUniqueIsTheAbyssalEdge(final GameTestHelper helper) {
		for (Item item : new Item[] {VaultItems.VAULT_SEAL, VaultItems.VOID_ESSENCE, VaultItems.VAULT_DUST, VaultItems.VOID_TOUCHED.template(), VaultItems.ABYSSAL_EDGE}) {
			helper.assertTrue(Exclusives.isExclusive(item), BuiltInRegistries.ITEM.getKey(item) + " is not exclusive");
		}
		helper.assertTrue(VaultTestSupport.site().newUnique().is(VaultItems.ABYSSAL_EDGE), "the site's unique is not the Abyssal Edge");
		helper.assertTrue(AbyssEffect.ID.equals(RuinUniques.effectOf(new ItemStack(VaultItems.ABYSSAL_EDGE))), "the Abyssal Edge has no abyss effect");
		helper.assertTrue(com.stella.ruins.reward.UniqueEffects.isDefined(VoidTouchedEffect.ID) && com.stella.ruins.reward.UniqueEffects.isDefined(AbyssEffect.ID), "an effect is not defined");
		helper.succeed();
	}
}
