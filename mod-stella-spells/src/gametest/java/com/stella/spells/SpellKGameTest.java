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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RPG mod's health scale K multiplies every heal and every hit once. A spell that turns health lost into a
 * heal, or copies a hit, must hand the scaled call a vanilla number. {@link KScaleHarness} applies K here.
 */
public class SpellKGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.k");
	private static final double K = 15.0;
	private static final double TOLERANCE = 1.0E-2;
	private static final float BIG = 5000.0F;

	@GameTest(maxTicks = 40)
	public void siphonHealsWhatItTook(final GameTestHelper helper) {
		Object saved = KScaleHarness.enable(K);
		ServerPlayer caster = null;
		try {
			caster = helper.makeMockServerPlayerInLevel();
			place(helper, caster);
			caster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BIG);
			caster.setHealth(100.0F);
			Mob target = durable(helper, 4);
			float casterBefore = caster.getHealth();
			float targetBefore = target.getHealth();
			cast(helper, caster, "soul_siphon");
			double lost = targetBefore - target.getHealth();
			double gained = caster.getHealth() - casterBefore;
			LOG.info("[SPELLTEST] test=siphonHealsWhatItTook RESULT lost={} gained={}", lost, gained);
			helper.assertTrue(lost > 0.0, "the siphon hurt nothing");
			helper.assertTrue(Math.abs(gained - lost) < TOLERANCE, "the siphon took " + lost + " and healed " + gained);
			target.discard();
		} finally {
			KScaleHarness.disable(saved);
			if (caster != null) {
				helper.getLevel().getServer().getPlayerList().remove(caster);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void mobSiphonHealsWhatItTook(final GameTestHelper helper) {
		Object saved = KScaleHarness.enable(K);
		try {
			floor(helper);
			Mob wielder = durable(helper, 1);
			wielder.setHealth(100.0F);
			Mob victim = durable(helper, 3);
			float wielderBefore = wielder.getHealth();
			float victimBefore = victim.getHealth();
			boolean applied = MobImbue.apply(helper.getLevel(), wielder, "soul_siphon", victim, 1.0, 1.0);
			double lost = victimBefore - victim.getHealth();
			double gained = wielder.getHealth() - wielderBefore;
			LOG.info("[SPELLTEST] test=mobSiphonHealsWhatItTook RESULT lost={} gained={}", lost, gained);
			helper.assertTrue(applied && lost > 0.0, "the mob siphon hurt nothing");
			helper.assertTrue(Math.abs(gained - lost) < TOLERANCE, "the mob siphon took " + lost + " and healed " + gained);
			wielder.discard();
			victim.discard();
		} finally {
			KScaleHarness.disable(saved);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void veilBreakDoublesOnce(final GameTestHelper helper) {
		Object saved = KScaleHarness.enable(K);
		ServerPlayer attacker = null;
		try {
			attacker = helper.makeMockServerPlayerInLevel();
			place(helper, attacker);
			Mob plain = durable(helper, 3);
			plain.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			double single = plain.getMaxHealth() - plain.getHealth();

			Mob veiled = durable(helper, 5);
			cast(helper, attacker, "shadow_veil");
			veiled.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			double total = veiled.getMaxHealth() - veiled.getHealth();
			LOG.info("[SPELLTEST] test=veilBreakDoublesOnce RESULT single={} total={}", single, total);
			helper.assertTrue(single > 0.0, "the plain blow hurt nothing");
			helper.assertTrue(Math.abs(total - 2.0 * single) < TOLERANCE, "the veiled blow took " + total + ", not twice " + single);
			plain.discard();
			veiled.discard();
		} finally {
			KScaleHarness.disable(saved);
			if (attacker != null) {
				helper.getLevel().getServer().getPlayerList().remove(attacker);
			}
		}
		helper.succeed();
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		ItemStack stack = book.getDefaultInstance();
		caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, spell + " did not cast: " + result);
	}

	private static void place(final GameTestHelper helper, final ServerPlayer player) {
		floor(helper);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int i = 0; i <= 9; i++) {
			helper.setBlock(2, 0, i, Blocks.STONE);
		}
	}

	// No armor and a very large health, so a hit and a heal read as exact numbers.
	private static Mob durable(final GameTestHelper helper, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BIG);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(BIG);
		return mob;
	}
}
