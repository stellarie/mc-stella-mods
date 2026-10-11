package com.stella.equips;

import com.stella.equips.durability.MaterialDurability;
import com.stella.equips.guardian.SkeletonCrown;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wear by material. Points of wear are charged to real stacks through the game's own durability path,
 * and a real sword strikes a real mob in survival.
 */
public class MaterialDurabilityGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.material-durability");
	private static final int TRIALS = 3000;
	private static final double SAMPLE_TOLERANCE = 0.04;
	private static final int STRIKES = 400;
	private static final double STRIKE_TOLERANCE = 0.10;

	@GameTest
	public void eachMaterialLosesDurabilityWithItsOwnChance(final GameTestHelper helper) {
		Object[][] table = {
			{Items.WOODEN_SWORD, 1.0}, {Items.LEATHER_CHESTPLATE, 1.0}, {Items.GOLDEN_SWORD, 0.80}, {Items.STONE_SWORD, 0.60},
			{Items.CHAINMAIL_CHESTPLATE, 0.60}, {Items.IRON_SWORD, 0.40}, {Items.DIAMOND_SWORD, 0.20}, {Items.NETHERITE_SWORD, 0.10},
		};
		StringBuilder seen = new StringBuilder();
		for (Object[] row : table) {
			Item item = (Item) row[0];
			double wanted = (double) row[1];
			ItemStack probe = new ItemStack(item);
			double rate = wearRate(helper, probe, TRIALS);
			seen.append(item).append('=').append(rate).append(' ');
			helper.assertTrue(MaterialDurability.chance(probe) == wanted, item + " has a wear chance of " + MaterialDurability.chance(probe) + ", not " + wanted);
			helper.assertTrue(Math.abs(rate - wanted) < SAMPLE_TOLERANCE, item + " lost durability on " + rate + " of the hits, not about " + wanted);
		}
		LOG.info("[BEHAVIOR] test=materialDurability.table RESULT trials={} rates={}", TRIALS, seen);
		helper.succeed();
	}

	@GameTest
	public void skeletonCrownWearsLikeNetherite(final GameTestHelper helper) {
		ItemStack crown = new ItemStack(SkeletonCrown.item());
		double rate = wearRate(helper, crown, TRIALS);
		LOG.info("[BEHAVIOR] test=materialDurability.crown RESULT rate={}", rate);
		helper.assertTrue(Math.abs(rate - 0.10) < SAMPLE_TOLERANCE, "the Skeleton Crown lost durability on " + rate + " of the hits, not about 0.1");
		helper.succeed();
	}

	@GameTest
	public void unbreakingStillRollsAfterTheMaterialChance(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
		int ironWorn = 0;
		int woodWorn = 0;
		for (int i = 0; i < TRIALS; i++) {
			ItemStack iron = new ItemStack(Items.IRON_SWORD);
			iron.enchant(unbreaking, 3);
			iron.hurtAndBreak(1, level, null, item -> {
			});
			ironWorn += iron.getDamageValue();
			ItemStack wood = new ItemStack(Items.WOODEN_SWORD);
			wood.enchant(unbreaking, 3);
			wood.hurtAndBreak(1, level, null, item -> {
			});
			woodWorn += wood.getDamageValue();
		}
		double iron = (double) ironWorn / TRIALS;
		double wood = (double) woodWorn / TRIALS;
		LOG.info("[BEHAVIOR] test=materialDurability.unbreaking RESULT ironUnbreakingIII={} woodUnbreakingIII={}", iron, wood);
		helper.assertTrue(Math.abs(iron - 0.40 * 0.25) < SAMPLE_TOLERANCE, "an iron sword with Unbreaking III lost durability on " + iron + " of the hits, not about 0.1");
		helper.assertTrue(Math.abs(wood - 0.25) < SAMPLE_TOLERANCE, "a wooden sword with Unbreaking III lost durability on " + wood + " of the hits, not about 0.25");
		helper.succeed();
	}

	@GameTest
	public void aSurvivalSwordWearsOnItsMaterialChanceWhenItStrikesAMob(final GameTestHelper helper) {
		ServerPlayer player = GuardianGearGameTest.survivor(helper, 2.5, 1.5, 0.0F);
		try {
			Mob target = helper.spawnWithNoFreeWill(EntityTypes.SPIDER, new Vec3(2.5, 2.0, 2.5));
			target.setPersistenceRequired();
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			player.setItemInHand(InteractionHand.MAIN_HAND, sword);
			float maxHealth = (float) target.getAttribute(Attributes.MAX_HEALTH).getValue();
			int landed = 0;
			for (int i = 0; i < STRIKES; i++) {
				target.setHealth(maxHealth);
				target.damageCooldownTime = 0;
				player.resetAttackStrengthTicker();
				float before = target.getHealth();
				player.attack(target);
				if (target.getHealth() < before) {
					landed++;
				}
			}
			double rate = (double) sword.getDamageValue() / landed;
			LOG.info("[BEHAVIOR] test=materialDurability.strikes RESULT landed={} worn={} rate={}", landed, sword.getDamageValue(), rate);
			helper.assertTrue(landed > STRIKES * 0.9, "only " + landed + " of " + STRIKES + " strikes landed, so the rate proves nothing");
			helper.assertTrue(Math.abs(rate - 0.40) < STRIKE_TOLERANCE, "the iron sword wore on " + rate + " of " + landed + " landed strikes, not about 0.4");
			helper.succeed();
		} finally {
			GuardianGearGameTest.leave(helper, player);
		}
	}

	/** The share of single points of wear that leave a fresh stack damaged. */
	private static double wearRate(final GameTestHelper helper, final ItemStack template, final int trials) {
		int worn = 0;
		for (int i = 0; i < trials; i++) {
			ItemStack stack = template.copy();
			stack.hurtAndBreak(1, helper.getLevel(), null, item -> {
			});
			worn += stack.getDamageValue();
		}
		return (double) worn / trials;
	}
}
