package com.stella.ruins.reward;

import com.stella.ruins.site.deepforge.DeepForgeItems;
import com.stella.ruins.site.meteorcrater.CraterItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** The affixes that give the forge and crater drops a use: cinderbrand, deepdelver and emberward. */
public class RuinSinkAffixesGameTest {
	private static ItemStack smith(final ServerPlayer player, final Item template, final ItemStack base, final Item addition) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(template));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(addition));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
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
	public void theThreeTemplatesCraftFromTheOrphanMaterialsAndOnlyFromThem(final GameTestHelper helper) {
		Item dust = DeepForgeItems.CINDER_DUST;
		Item shard = DeepForgeItems.FORGEHEART_SHARD;
		Item core = DeepForgeItems.FORGE_CORE;
		helper.assertTrue(craft(helper, 3, 2, core, dust, dust, dust, dust, null).is(DeepForgeItems.CINDERBRAND.template()), "a core and four dust did not craft the cinderbrand template");
		helper.assertTrue(craft(helper, 3, 2, core, shard, shard, null, null, null).is(DeepForgeItems.DEEPDELVER.template()), "a core and two shards did not craft the deepdelver template");
		helper.assertTrue(craft(helper, 3, 2, CraterItems.EMBER_CORE, CraterItems.EMBER_CORE, CraterItems.EMBER_CORE, CraterItems.STAR_FRAGMENT, null, null).is(CraterItems.EMBERWARD.template()),
			"three ember cores and a star fragment did not craft the emberward template");
		helper.assertTrue(craft(helper, 3, 2, core, Items.COAL, Items.COAL, Items.COAL, Items.COAL, null).isEmpty(), "coal crafted a template");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void eachAffixGoesOnItsOwnKindOfItemAndOnNoOther(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack sword = new ItemStack(DeepForgeItems.SWORD);
		ItemStack pickaxe = new ItemStack(DeepForgeItems.PICKAXE);
		ItemStack chest = new ItemStack(CraterItems.STARWEAVE.pieces().get(EquipmentSlot.CHEST));
		ItemStack burning = smith(player, DeepForgeItems.CINDERBRAND.template(), sword, DeepForgeItems.CINDER_DUST);
		ItemStack delving = smith(player, DeepForgeItems.DEEPDELVER.template(), pickaxe, DeepForgeItems.FORGEHEART_SHARD);
		ItemStack warded = smith(player, CraterItems.EMBERWARD.template(), chest, CraterItems.EMBER_CORE);
		helper.assertTrue("cinderbrand".equals(RuinAffixes.affixOf(burning)), "cinderbrand on a sword gave " + RuinAffixes.affixOf(burning));
		helper.assertTrue("deepdelver".equals(RuinAffixes.affixOf(delving)), "deepdelver on a pickaxe gave " + RuinAffixes.affixOf(delving));
		helper.assertTrue("emberward".equals(RuinAffixes.affixOf(warded)), "emberward on a chestplate gave " + RuinAffixes.affixOf(warded));
		helper.assertTrue(smith(player, DeepForgeItems.CINDERBRAND.template(), chest, DeepForgeItems.CINDER_DUST).isEmpty(), "cinderbrand went on armor");
		helper.assertTrue(smith(player, DeepForgeItems.DEEPDELVER.template(), sword, DeepForgeItems.FORGEHEART_SHARD).isEmpty(), "deepdelver went on a sword");
		helper.assertTrue(smith(player, CraterItems.EMBERWARD.template(), sword, CraterItems.EMBER_CORE).isEmpty(), "emberward went on a sword");
		helper.assertTrue(smith(player, DeepForgeItems.CINDERBRAND.template(), sword, Items.COAL).isEmpty(), "coal worked as the cinderbrand material");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void cinderbrandSetsATargetAfireOnAHit(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		RewardTestSupport.vulnerable(player);
		ItemStack burning = smith(player, DeepForgeItems.CINDERBRAND.template(), new ItemStack(DeepForgeItems.SWORD), DeepForgeItems.CINDER_DUST);
		Cow plain = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		Cow target = RewardTestSupport.cow(helper, 6.5, 3.5, 20.0F);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DeepForgeItems.SWORD));
		plain.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
		boolean plainBurns = plain.isOnFire();
		player.setItemSlot(EquipmentSlot.MAINHAND, burning);
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
		RewardTestSupport.result("cinderbrandHit", "plainBurns=" + plainBurns + " affixedBurns=" + target.isOnFire());
		helper.assertTrue(!plainBurns, "a plain deepsteel sword set the target afire, so the check proves nothing");
		helper.assertTrue(target.isOnFire(), "a cinderbrand hit did not set the target afire");
		plain.discard();
		target.discard();
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void deepdelverRaisesTheMiningEfficiencyOfTheWielder(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack delving = smith(player, DeepForgeItems.DEEPDELVER.template(), new ItemStack(DeepForgeItems.PICKAXE), DeepForgeItems.FORGEHEART_SHARD);
		double[] plain = new double[1];
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DeepForgeItems.PICKAXE));
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				plain[0] = player.getAttributeValue(Attributes.MINING_EFFICIENCY);
				player.setItemSlot(EquipmentSlot.MAINHAND, delving);
			})
			.thenWaitUntil(() -> helper.assertTrue(player.getAttributeValue(Attributes.MINING_EFFICIENCY) > plain[0],
				"mining efficiency " + player.getAttributeValue(Attributes.MINING_EFFICIENCY) + " did not pass " + plain[0]))
			.thenExecute(() -> {
				RewardTestSupport.result("deepdelver", "plain=" + plain[0] + " affixed=" + player.getAttributeValue(Attributes.MINING_EFFICIENCY));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void emberwardPutsOutFireAndGivesFireResistanceToItsWearer(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack warded = smith(player, CraterItems.EMBERWARD.template(), new ItemStack(CraterItems.STARWEAVE.pieces().get(EquipmentSlot.CHEST)), CraterItems.EMBER_CORE);
		helper.assertTrue(!UniqueEffects.effectIdsOf(player).contains("emberward"), "a bare player carries the emberward effect");
		player.setItemSlot(EquipmentSlot.CHEST, warded);
		helper.assertTrue(UniqueEffects.effectIdsOf(player).contains("emberward"), "the worn piece names no emberward effect");
		player.igniteForTicks(200);
		helper.assertTrue(player.isOnFire() && !player.hasEffect(MobEffects.FIRE_RESISTANCE), "the control player is not burning");
		CraterItems.EMBERWARD_EFFECT.onTick(player);
		RewardTestSupport.result("emberward", "onFire=" + player.isOnFire() + " fireResistance=" + player.hasEffect(MobEffects.FIRE_RESISTANCE));
		helper.assertTrue(!player.isOnFire() && player.hasEffect(MobEffects.FIRE_RESISTANCE), "the wearer still burns or has no Fire Resistance");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}
}
