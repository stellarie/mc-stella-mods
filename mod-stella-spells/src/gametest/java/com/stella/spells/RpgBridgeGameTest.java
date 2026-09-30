package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RPG mod's Intelligence reaches a player spell through {@link RpgBridge}. The RPG mod is not in this harness,
 * so each test installs a stand-in for its own cast and removes it after.
 */
public class RpgBridgeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.rpgbridge");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final float HIT = 4.0F;

	@GameTest
	public void absentModIsNeutral(final GameTestHelper helper) {
		RpgBridge.setStats(null);
		ServerPlayer player = player(helper);
		SpellModifiers.CastModifiers base = SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY);
		SpellModifiers.CastModifiers grown = RpgBridge.grow(player, base);
		LOG.info("[SPELLTEST] test=absentModIsNeutral RESULT base={} grown={}", base, grown);
		helper.assertTrue(grown.equals(base), "with no rpg mod the modifiers became " + grown);
		helper.assertTrue(grown.cooldownTicks() == base.cooldownTicks(), "the cooldown became " + grown.cooldownTicks());
		helper.succeed();
	}

	@GameTest
	public void badValuesAreNeutral(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		SpellModifiers.CastModifiers base = SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY);
		try {
			double[] bad = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY};
			for (double value : bad) {
				RpgBridge.setStats(stats(value, 0.5));
				helper.assertTrue(RpgBridge.grow(player, base) == base, "power " + value + " changed the modifiers");
				RpgBridge.setStats(stats(1.5, value));
				helper.assertTrue(RpgBridge.grow(player, base) == base, "cooldown " + value + " changed the modifiers");
			}
			RpgBridge.setStats(stats(1.0, 1.0));
			helper.assertTrue(RpgBridge.grow(player, base) == base, "1.0 and 1.0 changed the modifiers");
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest
	public void stubScalesCast(final GameTestHelper helper) {
		ServerPlayer neutral = caster(helper);
		float plain = castBook(helper, neutral, 1.0, 1.0);
		int base = SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY).cooldownTicks();
		int expected = (int) Math.round(base * 0.9);
		ServerPlayer caster = caster(helper);
		float scaled = castBook(helper, caster, 1.5, 0.9);
		ItemStack held = caster.getMainHandItem();
		tick(caster, expected - 1);
		boolean restingBefore = caster.getCooldowns().isOnCooldown(held);
		tick(caster, 1);
		boolean restingAfter = caster.getCooldowns().isOnCooldown(held);
		remove(helper, neutral, caster);
		LOG.info("[SPELLTEST] test=stubScalesCast RESULT plain={} scaled={} base={} expected={} restingBefore={} restingAfter={}", plain, scaled, base, expected, restingBefore, restingAfter);
		helper.assertTrue(Math.abs(plain - SpellCasting.DAMAGE) < 1.0E-4, "a neutral cast took " + plain);
		helper.assertTrue(Math.abs(scaled - 1.5F * plain) < 1.0E-3, "the scaled cast took " + scaled + ", not 1.5 x " + plain);
		helper.assertTrue(expected > 20 && expected < base, "the check needs a cooldown between the floor and the base, got " + expected);
		helper.assertTrue(restingBefore, "the book was ready one tick before " + expected);
		helper.assertTrue(!restingAfter, "the book was still resting at " + expected);
		helper.succeed();
	}

	@GameTest
	public void cooldownFloor(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		castBook(helper, caster, 1.0, 0.1);
		ItemStack held = caster.getMainHandItem();
		tick(caster, 19);
		boolean restingBefore = caster.getCooldowns().isOnCooldown(held);
		tick(caster, 1);
		boolean restingAfter = caster.getCooldowns().isOnCooldown(held);
		remove(helper, caster);
		LOG.info("[SPELLTEST] test=cooldownFloor RESULT restingBefore={} restingAfter={}", restingBefore, restingAfter);
		helper.assertTrue(restingBefore, "the book was ready before the 20 tick floor");
		helper.assertTrue(!restingAfter, "the book was still resting after the 20 tick floor");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void imbueScales(final GameTestHelper helper) {
		ServerPlayer neutral = caster(helper);
		float plain = imbuedHit(helper, neutral, 1.0, 1.0);
		int base = SpellModifiers.resolve(book(), ItemStack.EMPTY, ItemStack.EMPTY, 60).cooldownTicks();
		int expected = (int) Math.round(base * 0.9);
		ServerPlayer caster = caster(helper);
		float scaled = imbuedHit(helper, caster, 1.5, 0.9);
		ItemStack held = caster.getMainHandItem();
		tick(caster, expected - 1);
		boolean restingBefore = caster.getCooldowns().isOnCooldown(held);
		tick(caster, 1);
		boolean restingAfter = caster.getCooldowns().isOnCooldown(held);
		remove(helper, neutral, caster);
		float spell = SpellCasting.DAMAGE * (float) SpellImbue.IMBUE_POWER;
		LOG.info("[SPELLTEST] test=imbueScales RESULT plain={} scaled={} expected={} restingBefore={} restingAfter={}", plain, scaled, expected, restingBefore, restingAfter);
		helper.assertTrue(Math.abs(plain - (HIT + spell)) < 0.01F, "a neutral imbued hit took " + plain);
		helper.assertTrue(Math.abs(scaled - (HIT + 1.5F * spell)) < 0.01F, "the scaled imbued hit took " + scaled);
		helper.assertTrue(restingBefore, "the imbued piece was ready one tick before " + expected);
		helper.assertTrue(!restingAfter, "the imbued piece was still resting at " + expected);
		helper.succeed();
	}

	private static float castBook(final GameTestHelper helper, final ServerPlayer caster, final double power, final double cooldown) {
		Mob zombie = target(helper);
		ItemStack book = book();
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		float before = zombie.getHealth();
		RpgBridge.setStats(stats(power, cooldown));
		try {
			SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
		} finally {
			RpgBridge.setStats(null);
		}
		float lost = before - zombie.getHealth();
		zombie.discard();
		return lost;
	}

	private static float imbuedHit(final GameTestHelper helper, final ServerPlayer player, final double power, final double cooldown) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(SPARK_SHOT));
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		Mob zombie = target(helper);
		RpgBridge.setStats(stats(power, cooldown));
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		} finally {
			RpgBridge.setStats(null);
		}
		float taken = zombie.getMaxHealth() - zombie.getHealth();
		zombie.discard();
		return taken;
	}

	private static RpgBridge.Stats stats(final double power, final double cooldown) {
		return new RpgBridge.Stats() {
			@Override
			public double power(final Player player) {
				return power;
			}

			@Override
			public double cooldown(final Player player) {
				return cooldown;
			}
		};
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

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
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
