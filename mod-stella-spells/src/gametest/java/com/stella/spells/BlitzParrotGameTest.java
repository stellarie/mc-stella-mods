package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Blitz Beat parrot flies above its owner and never sits. When the owner has not struck for two seconds it
 * attacks a hostile within eight blocks on its own, three hits every thirty ticks, and each hit marks the foe
 * Glowing. A hit by the owner pauses it. A foe it kills gives the owner a share of its experience at once.
 */
public class BlitzParrotGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.blitzparrot");
	private static final double OWNER_X = 1.5;
	private static final double OWNER_Z = 1.5;
	/** The far corner of the 8 by 8 arena: past the falcon's reach, and clear of the casters beside this cell. */
	private static final double FAR_CORNER = 7.5;
	private static final float BLOW = 3.0F;
	private static final double HEIGHT_SLACK = 0.1;

	/** A source whose every roll comes up on the unlucky end, so a blow calls no extra strikes. */
	private static final class Never extends LegacyRandomSource {
		Never() {
			super(1L);
		}

		@Override
		public double nextDouble() {
			return 0.999;
		}
	}

	@GameTest(maxTicks = 100)
	public void theParrotHoversAboveThePlayerAndNeverPerches(final GameTestHelper helper) {
		ServerPlayer owner = buffed(helper);
		FalconEntity parrot = Falcons.of(owner);
		helper.assertTrue(parrot != null, "the cast raised no parrot");
		double[] lift = {Double.MAX_VALUE, -Double.MAX_VALUE};
		double[] widest = {0.0};
		boolean[] sat = {false};
		boolean[] grounded = {false};
		int[] seen = {0};
		helper.onEachTick(() -> {
			double up = parrot.getY() - owner.getY();
			lift[0] = Math.min(lift[0], up);
			lift[1] = Math.max(lift[1], up);
			widest[0] = Math.max(widest[0], Math.hypot(parrot.getX() - owner.getX(), parrot.getZ() - owner.getZ()));
			sat[0] |= parrot.isInSittingPose();
			grounded[0] |= parrot.onGround();
			seen[0]++;
		});
		helper.runAfterDelay(60L, () -> {
			double head = owner.getBbHeight();
			LOG.info("[BEHAVIOR] test=theParrotHoversAboveThePlayerAndNeverPerches RESULT ticks={} lowest={} highest={} widest={} sat={} grounded={} head={}",
				seen[0], lift[0], lift[1], widest[0], sat[0], grounded[0], head);
			helper.assertTrue(seen[0] >= 50, "the case watched the parrot for " + seen[0] + " ticks");
			helper.assertTrue(lift[0] >= head, "the parrot dipped to " + lift[0] + " blocks over the feet, below the head at " + head);
			helper.assertTrue(lift[1] <= head + FalconEntity.HOVER_CLEARANCE + FalconEntity.HOVER_BOB + HEIGHT_SLACK,
				"the parrot rose to " + lift[1] + " blocks over the feet");
			helper.assertTrue(widest[0] <= FalconEntity.HOVER_RADIUS + HEIGHT_SLACK, "the parrot strayed " + widest[0] + " blocks from the owner");
			helper.assertFalse(sat[0], "the parrot took the sitting pose");
			helper.assertFalse(grounded[0], "the parrot stood on the ground");
			finish(owner, parrot);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 260)
	public void anIdleOwnerGetsThreeParrotHitsEveryThirtyTicksOnAFoeWithinEightBlocksOnly(final GameTestHelper helper) {
		ServerPlayer owner = buffed(helper);
		Mob near = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		Mob far = foe(helper, FAR_CORNER, FAR_CORNER);
		double reach = near.position().distanceTo(owner.position());
		double beyond = far.position().distanceTo(owner.position());
		List<Long> hits = new ArrayList<>();
		List<Float> amounts = new ArrayList<>();
		float[] last = {near.getHealth()};
		helper.onEachTick(() -> {
			float now = near.getHealth();
			if (now < last[0]) {
				hits.add(helper.getTick());
				amounts.add(last[0] - now);
			}
			last[0] = now;
		});
		long window = ArcherSkills.PARROT_IDLE_TICKS + 4L * ArcherSkills.PARROT_INTERVAL_TICKS + 30L;
		helper.runAfterDelay(window, () -> {
			List<List<Long>> attacks = attacks(hits);
			double farLost = 1000.0 - far.getHealth();
			MobEffectInstance glow = near.getEffect(MobEffects.GLOWING);
			float strike = ArcherSkills.strikeDamage(owner, false);
			LOG.info("[BEHAVIOR] test=anIdleOwnerGetsThreeParrotHitsEveryThirtyTicksOnAFoeWithinEightBlocksOnly RESULT near={} far={} hits={} attacks={} amounts={} farLost={} glow={}",
				reach, beyond, hits, attacks, amounts, farLost, glow == null ? -1 : glow.getDuration());
			helper.assertTrue(reach <= ArcherSkills.PARROT_RANGE && beyond > ArcherSkills.PARROT_RANGE, "the foes are not placed either side of the reach");
			helper.assertTrue(attacks.size() >= 4, "the parrot attacked " + attacks.size() + " times");
			helper.assertTrue(hits.get(0) >= ArcherSkills.PARROT_IDLE_TICKS, "the parrot struck at tick " + hits.get(0) + ", before the owner had rested " + ArcherSkills.PARROT_IDLE_TICKS);
			for (List<Long> attack : attacks) {
				helper.assertTrue(attack.size() == ArcherSkills.PARROT_HITS, "an attack landed " + attack.size() + " hits: " + attacks);
			}
			for (int index = 1; index < attacks.size(); index++) {
				long gap = attacks.get(index).get(0) - attacks.get(index - 1).get(0);
				helper.assertTrue(gap == ArcherSkills.PARROT_INTERVAL_TICKS, "attacks came " + gap + " ticks apart, not " + ArcherSkills.PARROT_INTERVAL_TICKS);
			}
			for (float amount : amounts) {
				helper.assertTrue(Math.abs(amount - strike) < 0.01, "a parrot hit dealt " + amount + ", not the lowest roll " + strike);
			}
			helper.assertTrue(farLost == 0.0, "the parrot struck a foe " + beyond + " blocks away");
			helper.assertTrue(glow != null && glow.getDuration() <= ArcherSkills.PARROT_GLOW_TICKS && glow.getDuration() > 0,
				"a struck foe was not marked Glowing for " + ArcherSkills.PARROT_GLOW_TICKS + " ticks: " + glow);
			near.discard();
			far.discard();
			finish(owner, Falcons.of(owner));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 220)
	public void aBlowFromTheOwnerPausesTheParrotForTwoSeconds(final GameTestHelper helper) {
		ServerPlayer owner = buffed(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		long blowAt = ArcherSkills.PARROT_IDLE_TICKS + 22L;
		List<Long> parrotHits = new ArrayList<>();
		float[] last = {foe.getHealth()};
		helper.onEachTick(() -> {
			float now = foe.getHealth();
			if (now < last[0] && Math.abs((last[0] - now) - BLOW) > 0.01F) {
				parrotHits.add(helper.getTick());
			}
			last[0] = now;
		});
		ArcherSkills.setBlitzRandom(new Never());
		helper.runAfterDelay(blowAt, () -> {
			foe.damageCooldownTime = 0;
			foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), BLOW);
		});
		helper.runAfterDelay(blowAt + ArcherSkills.PARROT_IDLE_TICKS + 60L, () -> {
			ArcherSkills.setBlitzRandom(null);
			long resumed = parrotHits.stream().filter(tick -> tick > blowAt).findFirst().orElse(-1L);
			long quiet = parrotHits.stream().filter(tick -> tick > blowAt && tick < blowAt + ArcherSkills.PARROT_IDLE_TICKS).count();
			LOG.info("[BEHAVIOR] test=aBlowFromTheOwnerPausesTheParrotForTwoSeconds RESULT blowAt={} hits={} quietHits={} resumedAt={}",
				blowAt, parrotHits, quiet, resumed);
			helper.assertTrue(parrotHits.stream().anyMatch(tick -> tick < blowAt), "the parrot never attacked before the blow, so the pause proves nothing");
			helper.assertTrue(quiet == 0, "the parrot struck " + quiet + " times within " + ArcherSkills.PARROT_IDLE_TICKS + " ticks of the owner's blow");
			helper.assertTrue(resumed >= blowAt + ArcherSkills.PARROT_IDLE_TICKS, "the parrot never resumed: " + parrotHits);
			foe.discard();
			finish(owner, Falcons.of(owner));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void aFoeTheParrotKillsGivesThirtyFivePercentOfItsExperienceAtOnce(final GameTestHelper helper) {
		ServerPlayer owner = caster(helper);
		Mob control = ravager(helper, OWNER_X + 1.0, OWNER_Z + 5.0);
		control.setHealth(1.0F);
		control.damageCooldownTime = 0;
		control.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), BLOW);
		AABB around = control.getBoundingBox().inflate(4.0);
		List<ExperienceOrb> controlOrbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around);
		int controlValue = controlOrbs.stream().mapToInt(ExperienceOrb::getValue).sum();
		controlOrbs.forEach(ExperienceOrb::discard);
		Mob foe = ravager(helper, OWNER_X + 4.0, OWNER_Z + 5.0);
		foe.setHealth(1.0F);
		int reward = foe.getExperienceReward(helper.getLevel(), owner);
		int expected = (int) Math.round(reward * ArcherSkills.PARROT_XP_SHARE);
		ArcherSkills.applyBlitzBeat(owner, null, owner.position(), SkillSchoolGameTest.modifiers(owner, false));
		int before = owner.totalExperience;
		AABB foeArea = foe.getBoundingBox().inflate(4.0);
		helper.runAfterDelay(ArcherSkills.PARROT_IDLE_TICKS + 20L, () -> {
			int gained = owner.totalExperience - before;
			int orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, foeArea).size();
			LOG.info("[BEHAVIOR] test=aFoeTheParrotKillsGivesThirtyFivePercentOfItsExperienceAtOnce RESULT reward={} expected={} gained={} orbs={} controlOrbValue={} alive={}",
				reward, expected, gained, orbs, controlValue, foe.isAlive());
			helper.assertTrue(controlValue > 0, "a kill by the owner dropped no experience, so the missing orbs prove nothing");
			helper.assertTrue(reward > 0 && expected > 0, "the foe carries no experience");
			helper.assertFalse(foe.isAlive(), "the parrot never killed the foe");
			helper.assertTrue(gained == expected, "the owner gained " + gained + " experience, not " + expected + " of " + reward);
			helper.assertTrue(orbs == 0, "the parrot kill dropped " + orbs + " experience orbs");
			finish(owner, Falcons.of(owner));
			helper.succeed();
		});
	}

	private static void finish(final ServerPlayer owner, final FalconEntity parrot) {
		owner.removeAllEffects();
		ArcherSkills.clearFor(owner);
		if (parrot != null) {
			parrot.discard();
		}
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		return player;
	}

	private static ServerPlayer buffed(final GameTestHelper helper) {
		ServerPlayer player = caster(helper);
		ArcherSkills.applyBlitzBeat(player, null, player.position(), SkillSchoolGameTest.modifiers(player, false));
		return player;
	}

	private static Mob foe(final GameTestHelper helper, final double x, final double z) {
		return FighterBooksGameTest.foe(helper, x, z);
	}

	private static Mob ravager(final GameTestHelper helper, final double x, final double z) {
		Mob mob = EntityTypes.RAVAGER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** Groups hit ticks into attacks: a gap longer than half the interval starts a new one. */
	private static List<List<Long>> attacks(final List<Long> hits) {
		List<List<Long>> attacks = new ArrayList<>();
		for (long tick : hits) {
			if (attacks.isEmpty() || tick - attacks.get(attacks.size() - 1).get(attacks.get(attacks.size() - 1).size() - 1) > ArcherSkills.PARROT_INTERVAL_TICKS / 2) {
				attacks.add(new ArrayList<>());
			}
			attacks.get(attacks.size() - 1).add(tick);
		}
		return attacks;
	}

	/** How the parrot looks above the player is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theParrotLooksRightAboveThePlayerRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Blitz Beat. The larger brown parrot circles above your head with its wings beating and does not sit. Stand still near a hostile for two seconds and it dives at the hostile three times, which then glows.");
	}
}
