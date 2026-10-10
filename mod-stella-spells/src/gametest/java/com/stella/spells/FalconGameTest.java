package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Blitz Beat falcon: it exists while the buff holds, takes no damage, perches on the caster, swoops at the
 * foe each strike hits, and is gone when the buff ends or the caster leaves. What the client draws is a manual item.
 */
public class FalconGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.falcon");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final float BLOW = 3.0F;
	private static final double PERCH_REACH = 2.5;
	private static final double STRIKE_REACH = 1.8;

	/** A source whose every roll comes up on the lucky end. */
	private static final class Always extends LegacyRandomSource {
		Always() {
			super(1L);
		}

		@Override
		public double nextDouble() {
			return 0.0;
		}
	}

	@GameTest(maxTicks = 80)
	public void blitzBeatRaisesAFalconThatStaysOnTheCasterWhileTheBuffHolds(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		boolean applied = ArcherSkills.applyBlitzBeat(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		FalconEntity falcon = Falcons.of(caster);
		helper.assertTrue(applied && falcon != null, "the cast raised no falcon");
		helper.runAfterDelay(40L, () -> {
			FalconEntity later = Falcons.of(caster);
			double away = later == null ? -1.0 : later.position().distanceTo(caster.position());
			LOG.info("[BEHAVIOR] test=blitzBeatRaisesAFalconThatStaysOnTheCasterWhileTheBuffHolds RESULT same={} away={} type={}",
				later == falcon, away, later == null ? "none" : later.getType());
			helper.assertTrue(later == falcon && later.isAlive(), "the falcon was replaced or lost while the buff held");
			helper.assertTrue(later.getType() == SpellEntities.FALCON, "the falcon is not the falcon type");
			helper.assertTrue(away >= 0.0 && away <= PERCH_REACH, "the falcon perched " + away + " blocks from the caster");
			ArcherSkills.clearFor(caster);
			caster.removeAllEffects();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void theFalconTakesNoDamageFromAnySource(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ArcherSkills.applyBlitzBeat(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		FalconEntity falcon = Falcons.of(caster);
		helper.assertTrue(falcon != null, "the cast raised no falcon");
		float before = falcon.getHealth();
		var sources = helper.getLevel().damageSources();
		boolean[] hurt = {
			falcon.hurtServer(helper.getLevel(), sources.generic(), 1000.0F),
			falcon.hurtServer(helper.getLevel(), sources.magic(), 1000.0F),
			falcon.hurtServer(helper.getLevel(), sources.playerAttack(caster), 1000.0F),
			falcon.hurtServer(helper.getLevel(), sources.fellOutOfWorld(), 1000.0F)
		};
		LOG.info("[BEHAVIOR] test=theFalconTakesNoDamageFromAnySource RESULT hurt={} before={} after={} pickable={}",
			java.util.Arrays.toString(hurt), before, falcon.getHealth(), falcon.isPickable());
		for (boolean taken : hurt) {
			helper.assertFalse(taken, "the falcon took a hit");
		}
		helper.assertTrue(falcon.getHealth() == before && falcon.isAlive(), "the falcon health changed");
		helper.assertTrue(falcon.isInvulnerableTo(helper.getLevel(), sources.generic()), "the falcon is not invulnerable");
		helper.assertFalse(falcon.isPickable(), "an arrow or a click can pick the falcon");
		falcon.discard();
		caster.removeAllEffects();
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void eachStrikeSendsTheFalconToTheStruckFoeAndBack(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		ArcherSkills.applyBlitzBeat(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		FalconEntity falcon = Falcons.of(caster);
		helper.assertTrue(falcon != null, "the cast raised no falcon");
		double[] closest = {Double.MAX_VALUE};
		boolean[] swooped = {false};
		helper.onEachTick(() -> {
			closest[0] = Math.min(closest[0], falcon.position().distanceTo(foe.getBoundingBox().getCenter()));
			swooped[0] |= falcon.swooping();
		});
		ArcherSkills.setBlitzRandom(new Always());
		try {
			foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW);
		} finally {
			ArcherSkills.setBlitzRandom(null);
		}
		helper.runAfterDelay(100L, () -> {
			double home = falcon.position().distanceTo(caster.position());
			LOG.info("[BEHAVIOR] test=eachStrikeSendsTheFalconToTheStruckFoeAndBack RESULT closest={} swooped={} home={} ran={}",
				closest[0], swooped[0], home, ArcherSkills.pulsesRun(caster));
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == ArcherSkills.BLITZ_BEAT_STRIKES, "the falcon struck " + ArcherSkills.pulsesRun(caster) + " times");
			helper.assertTrue(swooped[0], "the falcon never swooped");
			helper.assertTrue(closest[0] <= STRIKE_REACH, "the falcon came no nearer than " + closest[0] + " blocks to the foe");
			helper.assertTrue(home <= PERCH_REACH, "the falcon did not come back: " + home + " blocks from the caster");
			ArcherSkills.clearFor(caster);
			caster.removeAllEffects();
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void theFalconLeavesWhenTheBuffEnds(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ArcherSkills.applyBlitzBeat(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		FalconEntity falcon = Falcons.of(caster);
		helper.assertTrue(falcon != null, "the cast raised no falcon");
		caster.removeEffect(SpellStatus.BLITZ_BEAT);
		helper.runAfterDelay(10L, () -> {
			LOG.info("[BEHAVIOR] test=theFalconLeavesWhenTheBuffEnds RESULT removed={} held={}", falcon.isRemoved(), Falcons.of(caster) != null);
			helper.assertTrue(falcon.isRemoved(), "the falcon outlived the buff");
			helper.assertTrue(Falcons.of(caster) == null, "the manager still holds a falcon");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void theFalconLeavesWhenTheCasterLeaves(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ArcherSkills.applyBlitzBeat(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		FalconEntity falcon = Falcons.of(caster);
		helper.assertTrue(falcon != null, "the cast raised no falcon");
		helper.getLevel().getServer().getPlayerList().remove(caster);
		helper.runAfterDelay(10L, () -> {
			LOG.info("[BEHAVIOR] test=theFalconLeavesWhenTheCasterLeaves RESULT removed={}", falcon.isRemoved());
			helper.assertTrue(falcon.isRemoved(), "the falcon outlived its caster");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void aLostFalconIsRaisedAgainWhileTheBuffHolds(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.addEffect(new MobEffectInstance(SpellStatus.BLITZ_BEAT, ArcherSkills.BLITZ_BEAT_TICKS, 0));
		helper.runAfterDelay(30L, () -> {
			FalconEntity raised = Falcons.of(caster);
			LOG.info("[BEHAVIOR] test=aLostFalconIsRaisedAgainWhileTheBuffHolds RESULT raised={}", raised != null);
			helper.assertTrue(raised != null, "a buffed caster with no cast got no falcon");
			raised.discard();
			helper.runAfterDelay(30L, () -> {
				FalconEntity again = Falcons.of(caster);
				helper.assertTrue(again != null && again != raised, "a lost falcon was not raised again");
				caster.removeAllEffects();
				helper.succeed();
			});
		});
	}

	/** The falcon looks, and the swoop reads, only on a client: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theFalconLooksLargeAndBrownOnAClientRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Blitz Beat and look: a larger brown parrot perches on your shoulder, swoops at the struck foe on each strike, and flies off when the buff ends. This harness has no client renderer.");
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		return player;
	}
}
