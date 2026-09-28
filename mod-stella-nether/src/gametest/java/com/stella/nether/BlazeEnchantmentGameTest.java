package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Overclock and Magma Armor, the Blaze Core's enchantments, and the anvil that sets them. */
public class BlazeEnchantmentGameTest {
	static final float HIT = 2.0F;

	static Holder<Enchantment> holder(final GameTestHelper helper, final ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	static ItemStack enchanted(final GameTestHelper helper, final ItemStack stack, final ResourceKey<Enchantment> key) {
		stack.enchant(holder(helper, key), 1);
		return stack;
	}

	/** A still, bare zombie with health to spare, that the mobs mod leaves alone. */
	static Zombie dummy(final GameTestHelper helper, final int x) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
		zombie.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(x + 0.5, 2.0, 1.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	/** What a hit from the player took from a target, each hit landing fresh. */
	static float strike(final GameTestHelper helper, final ServerPlayer player, final Zombie target) {
		float before = target.getHealth();
		target.setInvulnerableTime(0);
		target.damageCooldownTime = 0;
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		return before - target.getHealth();
	}

	static ServerPlayer wielder(final GameTestHelper helper, final ItemStack weapon) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 3.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return player;
	}

	@GameTest(environment = "mod-stella-nether-test:oc_grow", maxTicks = 20)
	public void overclockHitsHarderEachTimeOnTheSameEnemy(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, enchanted(helper, new ItemStack(Items.IRON_SWORD), BlazeEnchantments.OVERCLOCK));
		Zombie target = dummy(helper, 3);
		List<Float> losses = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			losses.add(strike(helper, player, target));
		}
		MobEffectInstance buff = player.getEffect(BlazeEnchantments.OVERCLOCK_EFFECT);
		NetherTestSupport.result("overclockGrows", "losses=" + losses + " buff=" + buff);
		target.discard();
		NetherTestSupport.leave(helper, player);
		for (int i = 0; i < losses.size(); i++) {
			double expected = HIT * (1.0 + BlazeEnchantments.OVERCLOCK_STEP * i);
			helper.assertTrue(Math.abs(losses.get(i) - expected) < 0.01, "hit " + (i + 1) + " took " + losses.get(i) + " against " + expected);
		}
		helper.assertTrue(buff != null && buff.getAmplifier() == losses.size() - 1, "the Overclock buff reads " + buff);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:oc_reset", maxTicks = 20)
	public void overclockStartsOverOnAnotherEnemy(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, enchanted(helper, new ItemStack(Items.IRON_SWORD), BlazeEnchantments.OVERCLOCK));
		Zombie first = dummy(helper, 3);
		Zombie second = dummy(helper, 5);
		for (int i = 0; i < 3; i++) {
			strike(helper, player, first);
		}
		float onSecond = strike(helper, player, second);
		float backOnFirst = strike(helper, player, first);
		NetherTestSupport.result("overclockResets", "second=" + onSecond + " backOnFirst=" + backOnFirst);
		first.discard();
		second.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(onSecond - HIT) < 0.01 && Math.abs(backOnFirst - HIT) < 0.01,
			"after switching enemies the hits took " + onSecond + " and " + backOnFirst);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:oc_plain", maxTicks = 20)
	public void aPlainWeaponHitsTheSameEachTime(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, new ItemStack(Items.IRON_SWORD));
		Zombie target = dummy(helper, 3);
		List<Float> losses = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			losses.add(strike(helper, player, target));
		}
		NetherTestSupport.result("plainWeapon", "losses=" + losses);
		target.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(losses.stream().allMatch(loss -> Math.abs(loss - HIT) < 0.01), "a plain sword took " + losses);
		helper.succeed();
	}

	static ServerPlayer wearer(final GameTestHelper helper, final boolean magma) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		NetherTestSupport.vulnerable(player);
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		player.setItemSlot(EquipmentSlot.CHEST, magma ? enchanted(helper, chest, BlazeEnchantments.MAGMA_ARMOR) : chest);
		return player;
	}

	/** What a hit through armor-piercing magic took, the wearer healed back after. */
	static float take(final GameTestHelper helper, final ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.setInvulnerableTime(0);
		player.damageCooldownTime = 0;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), HIT);
		return player.getMaxHealth() - player.getHealth();
	}

	@GameTest(environment = "mod-stella-nether-test:mg_grow", maxTicks = 20)
	public void magmaArmorSparesOnePercentMorePerHit(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, true);
		int hits = 10;
		float last = 0.0F;
		for (int i = 0; i < hits; i++) {
			last = take(helper, player);
		}
		double reduction = BlazeEnchantments.magmaReduction(player);
		double expectedLast = HIT * (1.0 - BlazeEnchantments.MAGMA_PER_HIT * (hits - 1));
		NetherTestSupport.result("magmaGrows", "reduction=" + reduction + " lastLoss=" + last + " expectedLast=" + expectedLast);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(reduction - BlazeEnchantments.MAGMA_PER_HIT * hits) < 1.0E-6 && Math.abs(last - expectedLast) < 0.01,
			"after " + hits + " hits the armor spares " + reduction + " and the last took " + last);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:mg_cap", maxTicks = 20)
	public void magmaArmorStopsAtItsCap(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, true);
		int hits = (int) Math.round(BlazeEnchantments.MAGMA_CAP / BlazeEnchantments.MAGMA_PER_HIT) + 15;
		for (int i = 0; i < hits; i++) {
			take(helper, player);
		}
		double reduction = BlazeEnchantments.magmaReduction(player);
		NetherTestSupport.result("magmaCap", "hits=" + hits + " reduction=" + reduction);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(reduction - BlazeEnchantments.MAGMA_CAP) < 1.0E-6, "after " + hits + " hits the armor spares " + reduction);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:mg_cool", maxTicks = 300)
	public void magmaArmorCoolsOnceLeftAlone(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, true);
		int hits = 10;
		for (int i = 0; i < hits; i++) {
			take(helper, player);
		}
		double built = BlazeEnchantments.magmaReduction(player);
		int cooling = 40;
		helper.runAfterDelay(BlazeEnchantments.MAGMA_COOL_AFTER_TICKS + cooling, () -> {
			double reduction = BlazeEnchantments.magmaReduction(player);
			double most = built - BlazeEnchantments.MAGMA_COOL_PER_SECOND * (cooling - 3) / 20.0;
			double least = built - BlazeEnchantments.MAGMA_COOL_PER_SECOND * (cooling + 3) / 20.0;
			NetherTestSupport.result("magmaCools", "built=" + built + " after=" + reduction + " between=" + least + ".." + most);
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(reduction <= most && reduction >= least, "the armor cooled from " + built + " to " + reduction);
			helper.succeed();
		});
	}

	@GameTest(environment = "mod-stella-nether-test:mg_plain", maxTicks = 20)
	public void plainArmorSparesNothingMore(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, false);
		List<Float> losses = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			losses.add(take(helper, player));
		}
		NetherTestSupport.result("plainArmor", "losses=" + losses);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(losses.stream().allMatch(loss -> Math.abs(loss - HIT) < 0.01), "plain armor took " + losses);
		helper.succeed();
	}

	static ItemStack anvil(final GameTestHelper helper, final ItemStack input) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		AnvilMenu menu = new AnvilMenu(0, player.getInventory());
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(NetherItems.BLAZE_CORE));
		menu.createResult();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		NetherTestSupport.leave(helper, player);
		return result;
	}

	@GameTest(environment = "mod-stella-nether-test:core_anvil", maxTicks = 20)
	public void aBlazeCoreAtTheAnvilSetsTheRightEnchantment(final GameTestHelper helper) {
		ItemStack sword = anvil(helper, new ItemStack(Items.DIAMOND_SWORD));
		ItemStack chest = anvil(helper, new ItemStack(Items.DIAMOND_CHESTPLATE));
		ItemStack stick = anvil(helper, new ItemStack(Items.STICK));
		int swordOverclock = EnchantmentHelper.getItemEnchantmentLevel(holder(helper, BlazeEnchantments.OVERCLOCK), sword);
		int chestMagma = EnchantmentHelper.getItemEnchantmentLevel(holder(helper, BlazeEnchantments.MAGMA_ARMOR), chest);
		int swordMagma = EnchantmentHelper.getItemEnchantmentLevel(holder(helper, BlazeEnchantments.MAGMA_ARMOR), sword);
		NetherTestSupport.result("coreAnvil", "swordOverclock=" + swordOverclock + " chestMagma=" + chestMagma + " swordMagma=" + swordMagma + " stick=" + stick.isEmpty());
		helper.assertTrue(swordOverclock == 1 && chestMagma == 1 && swordMagma == 0 && stick.isEmpty(),
			"the anvil made sword overclock=" + swordOverclock + " chest magma=" + chestMagma + " sword magma=" + swordMagma + " stick empty=" + stick.isEmpty());
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:core_table", maxTicks = 20)
	public void theBlazeCoreEnchantmentsComeOnlyFromTheCore(final GameTestHelper helper) {
		boolean inTable = holder(helper, BlazeEnchantments.OVERCLOCK).is(EnchantmentTags.IN_ENCHANTING_TABLE)
			|| holder(helper, BlazeEnchantments.MAGMA_ARMOR).is(EnchantmentTags.IN_ENCHANTING_TABLE);
		boolean inLoot = holder(helper, BlazeEnchantments.OVERCLOCK).is(EnchantmentTags.ON_RANDOM_LOOT)
			|| holder(helper, BlazeEnchantments.MAGMA_ARMOR).is(EnchantmentTags.ON_RANDOM_LOOT);
		NetherTestSupport.result("coreOnly", "inTable=" + inTable + " inLoot=" + inLoot);
		helper.assertTrue(!inTable && !inLoot, "a Blaze Core enchantment is in the table=" + inTable + " or loot=" + inLoot);
		helper.succeed();
	}
}
