package com.stella.ruins.reward;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/** Each unique hook, fired by the real damage, death, tick, and arrow paths of a test unique item. */
public class RuinUniquesGameTest {
	private static void strike(final GameTestHelper helper, final ServerPlayer player, final Cow target, final float amount) {
		target.damageCooldownTime = 0;
		target.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), amount);
	}

	private static ServerPlayer holder(final GameTestHelper helper, final EquipmentSlot slot, final Item item) {
		ServerPlayer player = RewardTestSupport.join(helper);
		player.setItemSlot(slot, new ItemStack(item));
		return player;
	}

	@GameTest
	public void theAttackHookFiresForAHeldUnique(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueSword);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		UUID id = player.getUUID();
		long fired = RewardTestSupport.count(id, "attack");
		RewardTestSupport.result("hookAttack", "fired=" + fired + " lines=" + RewardTestSupport.seen(id));
		helper.assertTrue(fired == 1, "attack hook fired " + fired + " times");
		helper.assertTrue(RewardTestSupport.seen(id).getFirst().startsWith("attack:" + cow.getUUID() + ":5.0"), "wrong attack record " + RewardTestSupport.seen(id));
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theHurtHookFiresForWornArmor(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.HEAD, RewardTestInit.uniqueHelm);
		RewardTestSupport.vulnerable(player);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		player.damageCooldownTime = 0;
		player.hurtServer(helper.getLevel(), cow.damageSources().mobAttack(cow), 4.0F);
		UUID id = player.getUUID();
		long fired = RewardTestSupport.count(id, "hurt");
		RewardTestSupport.result("hookHurt", "fired=" + fired + " lines=" + RewardTestSupport.seen(id));
		helper.assertTrue(fired == 1, "hurt hook fired " + fired + " times");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theKillHookFiresWhenTheUserKills(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueSword);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 4.0F);
		strike(helper, player, cow, 50.0F);
		UUID id = player.getUUID();
		long fired = RewardTestSupport.count(id, "kill");
		RewardTestSupport.result("hookKill", "fired=" + fired + " lines=" + RewardTestSupport.seen(id));
		helper.assertTrue(cow.isDeadOrDying(), "the target survived");
		helper.assertTrue(fired == 1, "kill hook fired " + fired + " times");
		helper.assertTrue(RewardTestSupport.seen(id).contains("kill:" + cow.getUUID()), "wrong kill record " + RewardTestSupport.seen(id));
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theTickHookFiresEveryTwentyTicks(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.OFFHAND, RewardTestInit.uniqueCharm);
		UUID id = player.getUUID();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(RewardTestSupport.count(id, "tick") >= 2, "tick hook fired " + RewardTestSupport.count(id, "tick") + " times"))
			.thenExecute(() -> {
				RewardTestSupport.result("hookTick", "fired=" + RewardTestSupport.count(id, "tick"));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest
	public void theArrowHookFiresForABowShot(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueBow);
		player.getInventory().add(new ItemStack(Items.ARROW, 4));
		player.startUsingItem(InteractionHand.MAIN_HAND);
		ItemStack bow = player.getItemInHand(InteractionHand.MAIN_HAND);
		bow.releaseUsing(helper.getLevel(), player, bow.getUseDuration(player) - 20);
		UUID id = player.getUUID();
		long fired = RewardTestSupport.count(id, "arrow");
		RewardTestSupport.result("hookArrow", "fired=" + fired + " lines=" + RewardTestSupport.seen(id));
		helper.assertTrue(fired == 1, "arrow hook fired " + fired + " times; the bow may not have shot");
		helper.assertTrue(helper.getLevel().getEntities(EntityTypes.ARROW, arrow -> id.equals(arrow.getOwner() == null ? null : arrow.getOwner().getUUID())).size() >= 1,
			"no arrow of the player stands in the level");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void anArrowFiresItsHookOnlyOnce(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueBow);
		ServerLevel level = helper.getLevel();
		AbstractArrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
		arrow.setOwner(player);
		arrow.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 3.0, 3.5)));
		level.addFreshEntity(arrow);
		UUID id = player.getUUID();
		helper.assertTrue(RewardTestSupport.count(id, "arrow") == 1, "first load fired " + RewardTestSupport.count(id, "arrow"));
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(arrow, level);
		helper.assertTrue(RewardTestSupport.count(id, "arrow") == 1, "a second load fired the hook again");
		helper.assertTrue(arrow.entityTags().contains(UniqueEffects.FIRED_TAG), "the arrow carries no fired tag");
		arrow.discard();
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theOffHandIsRead(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.OFFHAND, RewardTestInit.uniqueCharm);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(RewardTestSupport.count(player.getUUID(), "attack") == 1, "the off-hand unique did not fire");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aHelmetCarriedInAHandIsNotWorn(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.OFFHAND, RewardTestInit.uniqueHelm);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(RewardTestSupport.seen(player.getUUID()).isEmpty(), "a carried helmet fired " + RewardTestSupport.seen(player.getUUID()));
		player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(RewardTestInit.uniqueHelm));
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(RewardTestSupport.count(player.getUUID(), "attack") == 1, "the worn helmet did not fire");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aPlainItemFiresNothingUntilAUniqueIsCarried(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, Items.IRON_SWORD);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		UUID id = player.getUUID();
		strike(helper, player, cow, 2.0F);
		helper.assertTrue(RewardTestSupport.seen(id).isEmpty(), "a plain sword fired " + RewardTestSupport.seen(id));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RewardTestInit.uniqueSword));
		strike(helper, player, cow, 2.0F);
		helper.assertTrue(RewardTestSupport.count(id, "attack") == 1, "the unique sword fired " + RewardTestSupport.count(id, "attack") + " times");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void twoItemsWithOneEffectFireItOnce(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueSword);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(RewardTestInit.uniqueHelm));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(RewardTestInit.uniqueCharm));
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		long fired = RewardTestSupport.count(player.getUUID(), "attack");
		RewardTestSupport.result("hookDedupe", "items=3 fired=" + fired);
		helper.assertTrue(fired == 1, "three carriers fired the effect " + fired + " times");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aThrowingHookDoesNotStopTheOthers(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueThrower);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(RewardTestInit.uniqueCharm));
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(RewardTestSupport.count(player.getUUID(), "attack") == 1, "the probe did not run after the thrower failed");
		helper.assertTrue(cow.getHealth() == 15.0F, "the damage was lost, health " + cow.getHealth());
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aHookThatStrikesAgainDoesNotRecurse(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueRecurse);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 40.0F);
		strike(helper, player, cow, 5.0F);
		long fired = RewardTestSupport.count(player.getUUID(), "attack");
		RewardTestSupport.result("hookRecursion", "fired=" + fired + " health=" + cow.getHealth());
		helper.assertTrue(fired == 1, "the hook ran " + fired + " times through its own damage");
		helper.assertTrue(cow.getHealth() == 34.0F, "health " + cow.getHealth() + ", want 34");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void anAffixThatNamesAnEffectFiresIt(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack sword = RuinAffixes.apply(new ItemStack(Items.IRON_SWORD), RewardTestInit.echo);
		helper.assertTrue(!sword.isEmpty(), "the affix gave no sword");
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(RewardTestSupport.count(player.getUUID(), "attack") == 1, "the affix effect did not fire");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aUniqueWithAnUndefinedEffectIsIgnored(final GameTestHelper helper) {
		ServerPlayer player = holder(helper, EquipmentSlot.MAINHAND, RewardTestInit.uniqueOrphan);
		Cow cow = RewardTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		strike(helper, player, cow, 5.0F);
		helper.assertTrue(cow.getHealth() == 15.0F, "health " + cow.getHealth());
		helper.assertTrue(RewardTestSupport.seen(player.getUUID()).isEmpty(), "an undefined effect recorded something");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aUniqueCarriesItsComponentRarityAndLore(final GameTestHelper helper) {
		ItemStack stack = new ItemStack(RewardTestInit.uniqueSword);
		helper.assertTrue(RewardTestInit.PROBE.equals(RuinUniques.effectOf(stack)), "effect is " + RuinUniques.effectOf(stack));
		helper.assertTrue(stack.get(DataComponents.RARITY) == Rarity.EPIC, "rarity is " + stack.get(DataComponents.RARITY));
		List<net.minecraft.network.chat.Component> lines = stack.get(DataComponents.LORE).lines();
		helper.assertTrue(lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents contents
			&& contents.getKey().equals("effect.mod-stella-ruins.test_probe"), "lore is " + lines);
		helper.assertTrue(RuinUniques.all().containsKey(com.stella.ruins.StellaRuins.id("test_unique_sword")), "the unique is not listed");
		helper.succeed();
	}
}
