package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RPG mod's duration multiplier stretches a spell's effect, never its cooldown. */
public class IntDurationGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.intdur");
	private static final double MULTIPLIER = 2.0;

	private static final class Stub implements RpgBridge.Stats {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double durationMultiplier(final LivingEntity caster) {
			return MULTIPLIER;
		}
	}

	@GameTest(maxTicks = 60)
	public void intDurationStretchesBuffsAndControl(final GameTestHelper helper) {
		ServerPlayer caster = null;
		try {
			RpgBridge.setStats(new Stub());
			caster = caster(helper);
			Mob zombie = zombie(helper, 4);
			check(helper, caster, "quicksilver", caster, MobEffects.SPEED, SpellNumbers.QUICKSILVER.duration().ticks());
			check(helper, caster, "berserk", caster, MobEffects.STRENGTH, SpellNumbers.BERSERK.duration().ticks());
			check(helper, caster, "frostbind", zombie, MobEffects.SLOWNESS, SpellNumbers.FROSTBIND.duration().ticks());
			zombie.discard();

			ItemStack book = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "spark_shot")).getDefaultInstance();
			SpellDefinition definition = ((SpellbookItem) book.getItem()).definition();
			int cooldown = SpellTooltip.modifiers(caster, book, definition).cooldownTicks();
			LOG.info("[SPELLTEST] test=intDurationStretchesBuffsAndControl RESULT cooldown={} base={}", cooldown, definition.baseCooldownTicks());
			helper.assertTrue(cooldown == definition.baseCooldownTicks(), "the duration multiplier changed a cooldown to " + cooldown);
		} finally {
			RpgBridge.setStats(null);
			if (caster != null) {
				helper.getLevel().getServer().getPlayerList().remove(caster);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void noRpgKeepsTheBaseDuration(final GameTestHelper helper) {
		ServerPlayer caster = null;
		try {
			RpgBridge.setStats(null);
			caster = caster(helper);
			check(helper, caster, "quicksilver", caster, MobEffects.SPEED, SpellNumbers.QUICKSILVER.duration().ticks(), 1.0);
		} finally {
			if (caster != null) {
				helper.getLevel().getServer().getPlayerList().remove(caster);
			}
		}
		helper.succeed();
	}

	private static void check(
		final GameTestHelper helper, final ServerPlayer caster, final String spell, final LivingEntity bearer,
		final Holder<MobEffect> effect, final int base
	) {
		check(helper, caster, spell, bearer, effect, base, MULTIPLIER);
	}

	private static void check(
		final GameTestHelper helper, final ServerPlayer caster, final String spell, final LivingEntity bearer,
		final Holder<MobEffect> effect, final int base, final double multiplier
	) {
		bearer.removeAllEffects();
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, spell + " did not cast: " + result);
		MobEffectInstance applied = bearer.getEffect(effect);
		int expected = (int) Math.round(base * multiplier);
		LOG.info("[SPELLTEST] test=intDuration RESULT spell={} duration={} expected={}", spell, applied == null ? -1 : applied.getDuration(), expected);
		helper.assertTrue(applied != null && applied.getDuration() == expected,
			spell + " lasted " + (applied == null ? "never" : applied.getDuration()) + ", not " + expected);
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

	private static Mob zombie(final GameTestHelper helper, final int z) {
		helper.setTime(18_000L);
		for (int i = 0; i <= 9; i++) {
			helper.setBlock(2, 0, i, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
