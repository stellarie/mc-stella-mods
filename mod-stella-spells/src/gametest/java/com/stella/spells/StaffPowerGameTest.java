package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A staff in the tag mod-stella-equips-enchants:mage_staffs, held in the main hand, makes a cast hit harder and rest
 * shorter. The test pack lists a blaze rod in that tag, so the equips mod is not needed. The book sits in the off hand.
 */
public class StaffPowerGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.staffpower");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");

	@GameTest
	public void aStaffAddsTwentyFivePercentAndCutsTwentyPercent(final GameTestHelper helper) {
		ServerPlayer bare = caster(helper);
		float plain = castFromOffhand(helper, bare);
		ServerPlayer holder = caster(helper);
		holder.setItemSlot(EquipmentSlot.MAINHAND, staff(0));
		float boosted = castFromOffhand(helper, holder);
		int base = baseCooldown();
		int expected = (int) Math.round(base * 0.80);
		ItemStack book = holder.getOffhandItem();
		tick(holder, expected - 1);
		boolean restingBefore = holder.getCooldowns().isOnCooldown(book);
		tick(holder, 1);
		boolean restingAfter = holder.getCooldowns().isOnCooldown(book);
		remove(helper, bare, holder);
		LOG.info("[SPELLTEST] test=aStaffAddsTwentyFivePercentAndCutsTwentyPercent RESULT plain={} boosted={} base={} expected={} restingBefore={} restingAfter={}",
			plain, boosted, base, expected, restingBefore, restingAfter);
		helper.assertTrue(Math.abs(plain - SpellCasting.DAMAGE) < 1.0E-4, "a bare cast took " + plain);
		helper.assertTrue(Math.abs(boosted - 1.25F * plain) < 1.0E-3, "the staff cast took " + boosted + ", not 1.25 x " + plain);
		helper.assertTrue(expected > 20 && expected < base, "the check needs a cooldown between the floor and the base, got " + expected);
		helper.assertTrue(restingBefore, "the book was ready one tick before " + expected);
		helper.assertTrue(!restingAfter, "the book was still resting at " + expected);
		helper.succeed();
	}

	@GameTest
	public void aPlusFifteenStaffAddsSixtyFivePercentAndCutsFortyPercent(final GameTestHelper helper) {
		ServerPlayer bare = caster(helper);
		float plain = castFromOffhand(helper, bare);
		ServerPlayer holder = caster(helper);
		holder.setItemSlot(EquipmentSlot.MAINHAND, staff(15));
		float boosted = castFromOffhand(helper, holder);
		int base = baseCooldown();
		int expected = (int) Math.round(base * 0.60);
		ItemStack book = holder.getOffhandItem();
		tick(holder, expected - 1);
		boolean restingBefore = holder.getCooldowns().isOnCooldown(book);
		tick(holder, 1);
		boolean restingAfter = holder.getCooldowns().isOnCooldown(book);
		remove(helper, bare, holder);
		LOG.info("[SPELLTEST] test=aPlusFifteenStaffAddsSixtyFivePercentAndCutsFortyPercent RESULT plain={} boosted={} base={} expected={} restingBefore={} restingAfter={}",
			plain, boosted, base, expected, restingBefore, restingAfter);
		helper.assertTrue(Math.abs(boosted - 1.65F * plain) < 1.0E-3, "the +15 staff cast took " + boosted + ", not 1.65 x " + plain);
		helper.assertTrue(expected > 20 && expected < base, "the check needs a cooldown between the floor and the base, got " + expected);
		helper.assertTrue(restingBefore, "the book was ready one tick before " + expected);
		helper.assertTrue(!restingAfter, "the book was still resting at " + expected);
		helper.succeed();
	}

	@GameTest
	public void everyMilestoneStepsTheStaff(final GameTestHelper helper) {
		int[] levels = {0, 6, 7, 9, 10, 11, 12, 14, 15};
		double[] power = {1.25, 1.25, 1.35, 1.35, 1.45, 1.45, 1.55, 1.55, 1.65};
		double[] cut = {0.20, 0.20, 0.25, 0.25, 0.30, 0.30, 0.35, 0.35, 0.40};
		int base = baseCooldown();
		StringBuilder log = new StringBuilder();
		ServerPlayer holder = caster(helper);
		SpellModifiers.CastModifiers plain = SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY);
		for (int i = 0; i < levels.length; i++) {
			holder.setItemSlot(EquipmentSlot.MAINHAND, staff(levels[i]));
			SpellModifiers.CastModifiers grown = RpgBridge.grow(holder, plain);
			int expected = (int) Math.round(base * (1.0 - cut[i]));
			log.append('+').append(levels[i]).append('=').append(grown.powerMultiplier()).append('/').append(grown.cooldownTicks()).append(' ');
			helper.assertTrue(Math.abs(grown.powerMultiplier() - power[i]) < 1.0E-9, "+" + levels[i] + " gave power " + grown.powerMultiplier());
			helper.assertTrue(grown.cooldownTicks() == expected, "+" + levels[i] + " gave cooldown " + grown.cooldownTicks() + ", expected " + expected);
		}
		// A staff in the off hand grows nothing.
		holder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
		holder.setItemSlot(EquipmentSlot.OFFHAND, staff(15));
		helper.assertTrue(RpgBridge.grow(holder, plain) == plain, "a staff in the off hand grew the cast");
		remove(helper, holder);
		LOG.info("[SPELLTEST] test=everyMilestoneStepsTheStaff RESULT {}", log.toString().trim());
		helper.succeed();
	}

	private static float castFromOffhand(final GameTestHelper helper, final ServerPlayer caster) {
		Mob zombie = target(helper);
		ItemStack book = book();
		caster.setItemInHand(InteractionHand.OFF_HAND, book);
		float before = zombie.getHealth();
		SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.OFF_HAND);
		float lost = before - zombie.getHealth();
		zombie.discard();
		return lost;
	}

	private static int baseCooldown() {
		return SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY).cooldownTicks();
	}

	private static ItemStack staff(final int refine) {
		ItemStack stack = new ItemStack(Items.BLAZE_ROD);
		if (refine > 0) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(StaffPower.REFINE_KEY, refine));
		}
		return stack;
	}

	private static ItemStack book() {
		return BuiltInRegistries.ITEM.getValue(SPARK_SHOT).getDefaultInstance();
	}

	private static void tick(final ServerPlayer player, final int ticks) {
		for (int i = 0; i < ticks; i++) {
			player.getCooldowns().tick();
		}
	}

	private static void remove(final GameTestHelper helper, final ServerPlayer... players) {
		for (ServerPlayer player : players) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}

	private static Mob target(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
