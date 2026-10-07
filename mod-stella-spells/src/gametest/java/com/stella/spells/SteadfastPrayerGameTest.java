package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Steadfast Prayer: no harmful status lands on the holder for the approved twenty seconds, a helpful
 * one still does, and every blow the holder deals is worth a tenth while it holds.
 */
public class SteadfastPrayerGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.steadfast");
	/** The approved length of the prayer, and the approved share of a praying holder's blow. */
	private static final int TWENTY_SECONDS = 400;
	private static final float APPROVED_BLOW = 10.0F;

	/** Every harmful status is refused while the prayer holds, and a helpful one still lands. */
	@GameTest
	public void thePrayerRefusesEveryHarmfulStatus(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		use(helper, holder, "steadfast_prayer");
		boolean poison = holder.addEffect(new MobEffectInstance(MobEffects.POISON, TWENTY_SECONDS, 0));
		boolean slowness = holder.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TWENTY_SECONDS, 0));
		boolean wither = holder.addEffect(new MobEffectInstance(MobEffects.WITHER, TWENTY_SECONDS, 0));
		boolean regen = holder.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TWENTY_SECONDS, 0));
		LOG.info("[BEHAVIOR] test=prayerStatus RESULT poison={} slowness={} wither={} regen={} poisonHeld={} regenHeld={}",
			poison, slowness, wither, regen, holder.hasEffect(MobEffects.POISON), holder.hasEffect(MobEffects.REGENERATION));
		helper.assertTrue(!poison && !holder.hasEffect(MobEffects.POISON), "poison landed on a holder of the prayer");
		helper.assertTrue(!slowness && !holder.hasEffect(MobEffects.SLOWNESS), "slowness landed on a holder of the prayer");
		helper.assertTrue(!wither && !holder.hasEffect(MobEffects.WITHER), "wither landed on a holder of the prayer");
		helper.assertTrue(regen && holder.hasEffect(MobEffects.REGENERATION), "the prayer also refused a helpful status");
		helper.succeed();
	}

	/** The prayer leaves a tenth of the holder's blow, and the bare blow is whole. */
	@GameTest
	public void thePrayerLeavesATenthOfTheHoldersBlow(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		Mob victim = victim(helper);
		float bare = taken(holder, victim, APPROVED_BLOW);
		use(helper, holder, "steadfast_prayer");
		float praying = taken(holder, victim, APPROVED_BLOW);
		float expected = APPROVED_BLOW * (float) SteadfastPrayer.OUTGOING_DAMAGE_SHARE;
		LOG.info("[BEHAVIOR] test=prayerBlow RESULT bare={} praying={} expected={} share={}",
			bare, praying, expected, SteadfastPrayer.OUTGOING_DAMAGE_SHARE);
		helper.assertTrue(Math.abs(bare - APPROVED_BLOW) < 0.01F, "a bare blow of " + APPROVED_BLOW + " took " + bare);
		helper.assertTrue(Math.abs(praying - expected) < 0.01F, "a praying blow of " + APPROVED_BLOW + " took " + praying + ", not " + expected);
		helper.succeed();
	}

	/** The book grants the prayer for the approved twenty seconds. */
	@GameTest
	public void theBookGrantsThePrayerForTwentySeconds(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		use(helper, holder, "steadfast_prayer");
		MobEffectInstance prayer = holder.getEffect(SpellStatus.STEADFAST_PRAYER);
		int duration = prayer == null ? -1 : prayer.getDuration();
		LOG.info("[BEHAVIOR] test=prayerDuration RESULT duration={} want={} shipped={}", duration, TWENTY_SECONDS, SteadfastPrayer.PRAYER_TICKS);
		helper.assertTrue(duration == TWENTY_SECONDS && SteadfastPrayer.PRAYER_TICKS == TWENTY_SECONDS,
			"the prayer lasts " + duration + " ticks with the shipped constant at " + SteadfastPrayer.PRAYER_TICKS + ", not " + TWENTY_SECONDS);
		helper.succeed();
	}

	/** What a blow of this size takes off the victim, with its own damage cooldown cleared first. */
	private static float taken(final ServerPlayer dealer, final Mob victim, final float amount) {
		victim.damageCooldownTime = 0;
		float before = victim.getHealth();
		victim.hurtServer((net.minecraft.server.level.ServerLevel) dealer.level(), dealer.level().damageSources().playerAttack(dealer), amount);
		return before - victim.getHealth();
	}

	private static Mob victim(final GameTestHelper helper) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 3).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(1_000.0F);
		return mob;
	}

	private static ServerPlayer holder(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static void use(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, spell + " did not cast: " + result);
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}
}
