package com.stella.equips;

import com.stella.equips.enchant.Scholar;
import com.stella.equips.enchant.Shakedown;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shakedown drops emeralds from hostile kills; Scholar adds 15% orb experience per piece and skips weapons. */
public final class ShakedownScholarGameTest {
	@GameTest
	public void shakedownDrops(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(9L);
		int max = 0;
		long total = 0;
		int samples = 20_000;
		for (int i = 0; i < samples; i++) {
			int count = Shakedown.drop(3, random);
			max = Math.max(max, count);
			total += count;
		}
		double mean = (double) total / samples;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(holder(helper, Shakedown.KEY), 3);
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int zombieEmeralds = 0;
		for (int i = 0; i < 12; i++) {
			zombieEmeralds += killAndCount(helper, player, helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1 + (i % 3), 2, 1 + (i / 3)));
		}
		int cowEmeralds = killAndCount(helper, player, helper.spawnWithNoFreeWill(EntityTypes.COW, 5, 2, 5));
		log("shakedownDrops", "max=" + max + " mean=" + mean + " zombieEmeralds=" + zombieEmeralds + " cowEmeralds=" + cowEmeralds
			+ " level=" + Shakedown.level(level, sword));
		helper.assertTrue(max == 6 && Math.abs(mean - 3.0) < 0.1 && Shakedown.drop(0, random) == 0, "the drop range is wrong");
		helper.assertTrue(zombieEmeralds > 0 && zombieEmeralds <= 72 && cowEmeralds == 0, "zombies dropped " + zombieEmeralds + ", the cow " + cowEmeralds);
		helper.succeed();
	}

	@GameTest
	public void scholarSupport(final GameTestHelper helper) {
		Enchantment scholar = holder(helper, Scholar.KEY).value();
		List<String> wrong = new ArrayList<>();
		for (Item item : List.of(Items.DIAMOND_CHESTPLATE, Items.LEATHER_BOOTS, Items.IRON_PICKAXE, Items.GOLDEN_SHOVEL, Items.STONE_HOE,
			Items.SHEARS, Items.FISHING_ROD, Items.SHIELD, Items.ELYTRA)) {
			if (!scholar.canEnchant(new ItemStack(item))) {
				wrong.add("missing " + item);
			}
		}
		for (Item item : List.of(Items.DIAMOND_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE)) {
			if (scholar.canEnchant(new ItemStack(item))) {
				wrong.add("weapon " + item);
			}
		}
		log("scholarSupport", "wrong=" + wrong);
		helper.assertTrue(wrong.isEmpty(), "Scholar support is wrong: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void scholarExperience(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Holder<Enchantment> scholar = holder(helper, Scholar.KEY);
		int none = Scholar.scale(player, 100);
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		boots.enchant(scholar, 1);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		int one = Scholar.scale(player, 100);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.enchant(scholar, 1);
		player.setItemSlot(EquipmentSlot.MAINHAND, pick);
		int two = Scholar.scale(player, 100);
		int before = player.totalExperience;
		Vec3 at = player.position();
		ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 20);
		helper.getLevel().addFreshEntity(orb);
		player.takeXpDelay = 0;
		orb.playerTouch(player);
		int gained = player.totalExperience - before;
		log("scholarExperience", "none=" + none + " one=" + one + " two=" + two + " orbGained=" + gained);
		helper.assertTrue(none == 100 && one == 115 && two == 130, "Scholar scaling is wrong: " + none + " " + one + " " + two);
		helper.assertTrue(gained == 26, "a 20-point orb gave " + gained + " with two Scholar pieces");
		helper.succeed();
	}

	private static int killAndCount(final GameTestHelper helper, final ServerPlayer player, final LivingEntity victim) {
		AABB around = victim.getBoundingBox().inflate(2.0);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100_000.0F);
		int count = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(Items.EMERALD))) {
			count += item.getItem().getCount();
			item.discard();
		}
		return count;
	}

	private static Holder<Enchantment> holder(final GameTestHelper helper, final ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static void log(final String test, final String result) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, result);
	}
}
