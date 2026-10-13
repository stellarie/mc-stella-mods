package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Archer innate, Falcon Guard. A black falcon circles above the archer beside the brown Blitz Beat falcon. Every
 * two seconds it has a 35 percent chance to strike a hostile near the archer three times, each hit the average of the
 * lowest and highest ranged roll. A foe it kills pays the archer 25 percent of its experience. The job comes from the
 * RPG mod's job share, which this class stands in for.
 */
public class FalconGuardGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.falconguard");
	private static final double OWNER_X = 1.5;
	private static final double OWNER_Z = 1.5;
	private static final double LOW_ROLL = 20.0;
	private static final double HIGH_ROLL = 60.0;
	private static final int SAMPLES = 1_500;
	private static final double HEIGHT_SLACK = 0.1;

	/** A stand-in for the job share of the RPG mod, by player. A player it does not know asks the share that was there. */
	private static final class Jobs {
		static final Map<UUID, String> INNATES = new ConcurrentHashMap<>();
		private static volatile Function<ServerPlayer, String> previous;
		static final Function<ServerPlayer, String> INNATE = player -> {
			String own = INNATES.get(player.getUUID());
			Function<ServerPlayer, String> other = previous;
			return own != null ? own : other != null ? other.apply(player) : "none";
		};

		@SuppressWarnings("unchecked")
		static synchronized void install() {
			Object now = FabricLoader.getInstance().getObjectShare().get(FalconGuard.JOBS_SHARE);
			if (now instanceof Map<?, ?> map && map.get(FalconGuard.INNATE_FUNCTION) == INNATE) {
				return;
			}
			previous = now instanceof Map<?, ?> map && map.get(FalconGuard.INNATE_FUNCTION) instanceof Function<?, ?> function
				? (Function<ServerPlayer, String>) function : null;
			FabricLoader.getInstance().getObjectShare().put(FalconGuard.JOBS_SHARE, Map.of(FalconGuard.INNATE_FUNCTION, INNATE));
		}
	}

	/** The RPG mod's Atk range for a ranged shot, as a stand-in: the low and the high end, in K units. */
	private static final RpgBridge.Stats ATK = new RpgBridge.Stats() {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double[] atkRange(final LivingEntity caster, final boolean ranged) {
			return ranged ? new double[] {LOW_ROLL, HIGH_ROLL} : new double[] {Double.NaN, Double.NaN};
		}
	};

	/** A source whose first roll is a success and every roll after it is not. */
	private static final class Once extends LegacyRandomSource {
		private boolean spent;

		Once() {
			super(1L);
		}

		@Override
		public double nextDouble() {
			boolean first = !this.spent;
			this.spent = true;
			return first ? 0.0 : 0.999;
		}
	}

	private static final RandomSource ALWAYS = new LegacyRandomSource(1L) {
		@Override
		public double nextDouble() {
			return 0.0;
		}
	};

	private static ServerPlayer archer(final GameTestHelper helper) {
		Jobs.install();
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		Jobs.INNATES.put(player.getUUID(), FalconGuard.INNATE_KEY);
		return player;
	}

	private static void retire(final ServerPlayer owner) {
		Jobs.INNATES.remove(owner.getUUID());
		FalconGuard.setRandom(owner.getUUID(), null);
		owner.removeAllEffects();
		ArcherSkills.clearFor(owner);
		FalconEntity guard = Falcons.guardOf(owner);
		FalconEntity brown = Falcons.of(owner);
		if (guard != null) {
			guard.discard();
		}
		if (brown != null) {
			brown.discard();
		}
	}

	private static Mob foe(final GameTestHelper helper, final double x, final double z) {
		return FighterBooksGameTest.foe(helper, x, z);
	}

	@GameTest(maxTicks = 130)
	public void aBlackFalconCirclesBesideTheBrownOneAndLeavesWithTheInnate(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		ArcherSkills.applyBlitzBeat(owner, null, owner.position(), SkillSchoolGameTest.modifiers(owner, false));
		FalconEntity brown = Falcons.of(owner);
		helper.assertTrue(brown != null && !brown.isGuard() && brown.getType() == SpellEntities.FALCON, "Blitz Beat raised " + brown);
		FalconEntity[] black = {null};
		double[] lift = {Double.MAX_VALUE, -Double.MAX_VALUE};
		double[] widest = {0.0};
		double[] closest = {Double.MAX_VALUE};
		boolean[] sat = {false};
		int[] seen = {0};
		helper.onEachTick(() -> {
			if (black[0] == null) {
				black[0] = Falcons.guardOf(owner);
				return;
			}
			double up = black[0].getY() - owner.getY();
			lift[0] = Math.min(lift[0], up);
			lift[1] = Math.max(lift[1], up);
			widest[0] = Math.max(widest[0], Math.hypot(black[0].getX() - owner.getX(), black[0].getZ() - owner.getZ()));
			closest[0] = Math.min(closest[0], Math.hypot(black[0].getX() - brown.getX(), black[0].getZ() - brown.getZ()));
			sat[0] |= black[0].isInSittingPose();
			seen[0]++;
		});
		helper.runAfterDelay(90L, () -> {
			double head = owner.getBbHeight();
			LOG.info("[BEHAVIOR] test=aBlackFalconCirclesBesideTheBrownOneAndLeavesWithTheInnate RESULT ticks={} lowest={} highest={} widest={} apart={} sat={} type={} brown={}",
				seen[0], lift[0], lift[1], widest[0], closest[0], sat[0], black[0] == null ? "none" : black[0].getType(), brown.isAlive());
			helper.assertTrue(black[0] != null && black[0].isGuard() && black[0].getType() == SpellEntities.GUARD_FALCON && black[0] != brown,
				"the innate raised " + black[0]);
			helper.assertTrue(seen[0] >= 60, "the case watched the black falcon for " + seen[0] + " ticks");
			helper.assertTrue(lift[0] >= head && lift[1] <= head + FalconEntity.HOVER_CLEARANCE + FalconEntity.HOVER_BOB + HEIGHT_SLACK,
				"the black falcon flew " + lift[0] + " to " + lift[1] + " blocks over the feet, head at " + head);
			helper.assertTrue(widest[0] <= FalconEntity.HOVER_RADIUS + HEIGHT_SLACK, "the black falcon strayed " + widest[0] + " blocks");
			helper.assertTrue(closest[0] > FalconEntity.HOVER_RADIUS, "the two falcons flew within " + closest[0] + " blocks of each other");
			helper.assertFalse(sat[0], "the black falcon took the sitting pose");
			helper.assertTrue(brown.isAlive() && Falcons.of(owner) == brown, "the black falcon replaced the Blitz Beat falcon");
			Jobs.INNATES.remove(owner.getUUID());
		});
		helper.runAfterDelay(110L, () -> {
			helper.assertTrue(black[0].isRemoved() && Falcons.guardOf(owner) == null, "the black falcon stayed after the job lost the innate");
			helper.assertTrue(brown.isAlive(), "losing the innate removed the Blitz Beat falcon");
			retire(owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void aboutThirtyFivePercentOfTheTwoSecondChecksStrikeAFoeInReachOnly(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		double reach = foe.position().distanceTo(owner.position());
		int struck = 0;
		int unjobbed = 0;
		int alone = 0;
		try {
			FalconGuard.setRandom(owner.getUUID(), RandomSource.create(35L));
			for (int check = 0; check < SAMPLES; check++) {
				struck += ArcherSkills.guardCheck(owner) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
			FalconGuard.setRandom(owner.getUUID(), ALWAYS);
			Jobs.INNATES.put(owner.getUUID(), "none");
			for (int check = 0; check < SAMPLES / 5; check++) {
				unjobbed += ArcherSkills.guardCheck(owner) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
			Jobs.INNATES.put(owner.getUUID(), FalconGuard.INNATE_KEY);
			foe.discard();
			for (int check = 0; check < SAMPLES / 5; check++) {
				alone += ArcherSkills.guardCheck(owner) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
		} finally {
			retire(owner);
		}
		double sigma = Math.sqrt(SAMPLES * FalconGuard.CHANCE * (1.0 - FalconGuard.CHANCE));
		LOG.info("[BEHAVIOR] test=aboutThirtyFivePercentOfTheTwoSecondChecksStrikeAFoeInReachOnly RESULT struck={} of={} chance={} reach={} unjobbed={} alone={}",
			struck, SAMPLES, FalconGuard.CHANCE, reach, unjobbed, alone);
		helper.assertTrue(FalconGuard.CHANCE == 0.35 && FalconGuard.CHECK_TICKS == 40 && FalconGuard.HITS == 3 && FalconGuard.XP_SHARE == 0.25,
			"the falcon numbers drifted from 35 percent, 2 s, 3 hits and 25 percent");
		helper.assertTrue(reach <= FalconGuard.RANGE, "the foe is not within reach: " + reach);
		helper.assertTrue(Math.abs(struck - SAMPLES * FalconGuard.CHANCE) <= 3.0 * sigma, "the falcon struck " + struck + " times in " + SAMPLES + " checks");
		helper.assertTrue(unjobbed == 0, "an archer without the innate, whose every roll succeeds, struck " + unjobbed + " times");
		helper.assertTrue(alone == 0, "the falcon struck " + alone + " times with no foe in reach");
		helper.succeed();
	}

	@GameTest(maxTicks = 240)
	public void eachStrikeIsThreeHitsOfTheAverageRollAndBlitzBeatStillWorksBeside(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		FalconGuard.setRandom(owner.getUUID(), new Once());
		float average = (float) ((LOW_ROLL + HIGH_ROLL) / 2.0 / RpgBridge.k());
		float parrot = ArcherSkills.strikeDamage(owner, false);
		helper.assertTrue(Math.abs(average - parrot) > 1.0F && Math.abs(average - LOW_ROLL / RpgBridge.k()) > 1.0F
			&& Math.abs(average - HIGH_ROLL / RpgBridge.k()) > 1.0F, "the hit sizes cannot be told apart");
		// The check runs on the server tick whose clock is a multiple of the check gap. The stand-in covers that tick only.
		int clock = ArcherSkills.dueIn(0);
		int wait = Math.floorMod(-(clock + 1), FalconGuard.CHECK_TICKS);
		long arm = wait == 0 ? FalconGuard.CHECK_TICKS : wait;
		helper.runAfterDelay(arm, () -> RpgBridge.setStats(ATK));
		helper.runAfterDelay(arm + 1, () -> RpgBridge.setStats(null));
		List<Long> hits = new ArrayList<>();
		List<Float> amounts = new ArrayList<>();
		float[] last = {foe.getHealth()};
		boolean[] guardSwooped = {false};
		boolean[] brownSwoopedFirst = {false};
		long blitzAt = arm + 25L;
		helper.onEachTick(() -> {
			float now = foe.getHealth();
			if (now < last[0]) {
				hits.add(helper.getTick());
				amounts.add(last[0] - now);
			}
			last[0] = now;
			FalconEntity black = Falcons.guardOf(owner);
			FalconEntity brown = Falcons.of(owner);
			guardSwooped[0] |= black != null && black.swooping();
			brownSwoopedFirst[0] |= brown != null && brown.swooping() && helper.getTick() < blitzAt + ArcherSkills.PARROT_IDLE_TICKS;
		});
		helper.runAfterDelay(blitzAt, () -> ArcherSkills.applyBlitzBeat(owner, null, owner.position(), SkillSchoolGameTest.modifiers(owner, false)));
		helper.runAfterDelay(blitzAt + ArcherSkills.PARROT_IDLE_TICKS + 2L * ArcherSkills.PARROT_INTERVAL_TICKS + 20L, () -> {
			List<Long> guardTicks = new ArrayList<>();
			int parrotHits = 0;
			for (int i = 0; i < hits.size(); i++) {
				if (Math.abs(amounts.get(i) - average) < 0.01F) {
					guardTicks.add(hits.get(i));
				} else if (Math.abs(amounts.get(i) - parrot) < 0.01F) {
					parrotHits++;
				}
			}
			LOG.info("[BEHAVIOR] test=eachStrikeIsThreeHitsOfTheAverageRollAndBlitzBeatStillWorksBeside RESULT average={} parrot={} hits={} amounts={} guard={} parrotHits={} swooped={}",
				average, parrot, hits, amounts, guardTicks, parrotHits, guardSwooped[0]);
			helper.assertTrue(guardTicks.size() == FalconGuard.HITS, "the falcon landed " + guardTicks.size() + " hits of " + average + ": " + amounts);
			for (int i = 1; i < guardTicks.size(); i++) {
				helper.assertTrue(guardTicks.get(i) - guardTicks.get(i - 1) == ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS,
					"the hits came " + (guardTicks.get(i) - guardTicks.get(i - 1)) + " ticks apart");
			}
			helper.assertTrue(guardTicks.getLast() < blitzAt, "the strike came after Blitz Beat was cast, so the two overlap: " + guardTicks);
			helper.assertTrue(guardSwooped[0], "the black falcon never flew at the foe");
			helper.assertFalse(brownSwoopedFirst[0], "the brown falcon flew at the foe before Blitz Beat struck");
			helper.assertTrue(parrotHits >= ArcherSkills.PARROT_HITS, "Blitz Beat landed " + parrotHits + " idle hits of " + parrot + " beside the guard");
			helper.assertTrue(Falcons.guardOf(owner) != null && Falcons.of(owner) != null && Falcons.guardOf(owner) != Falcons.of(owner),
				"the two falcons do not both stand");
			foe.discard();
			retire(owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void aFoeTheFalconKillsPaysTwentyFivePercentOfItsExperienceAtOnce(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob control = ravager(helper, OWNER_X + 1.0, OWNER_Z + 5.0);
		control.setHealth(1.0F);
		control.damageCooldownTime = 0;
		control.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 3.0F);
		AABB around = control.getBoundingBox().inflate(4.0);
		List<ExperienceOrb> controlOrbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around);
		int controlValue = controlOrbs.stream().mapToInt(ExperienceOrb::getValue).sum();
		controlOrbs.forEach(ExperienceOrb::discard);
		Mob foe = ravager(helper, OWNER_X + 4.0, OWNER_Z + 5.0);
		foe.setHealth(1.0F);
		int reward = foe.getExperienceReward(helper.getLevel(), owner);
		int expected = (int) Math.round(reward * FalconGuard.XP_SHARE);
		int blitzShare = (int) Math.round(reward * ArcherSkills.PARROT_XP_SHARE);
		int before = owner.totalExperience;
		AABB foeArea = foe.getBoundingBox().inflate(4.0);
		FalconGuard.setRandom(owner.getUUID(), ALWAYS);
		boolean struck = ArcherSkills.guardCheck(owner);
		FalconGuard.setRandom(owner.getUUID(), null);
		helper.runAfterDelay(30L, () -> {
			int gained = owner.totalExperience - before;
			int orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, foeArea).size();
			LOG.info("[BEHAVIOR] test=aFoeTheFalconKillsPaysTwentyFivePercentOfItsExperienceAtOnce RESULT struck={} reward={} expected={} blitzShare={} gained={} orbs={} controlOrbValue={} alive={}",
				struck, reward, expected, blitzShare, gained, orbs, controlValue, foe.isAlive());
			helper.assertTrue(struck, "the check queued no strike");
			helper.assertTrue(controlValue > 0, "a kill by the owner dropped no experience, so the missing orbs prove nothing");
			helper.assertTrue(reward > 0 && expected > 0 && expected != blitzShare, "the foe carries no experience that tells 25 percent from 35");
			helper.assertFalse(foe.isAlive(), "the falcon never killed the foe");
			helper.assertTrue(gained == expected, "the owner gained " + gained + " experience, not " + expected + " of " + reward);
			helper.assertTrue(orbs == 0, "the falcon kill dropped " + orbs + " experience orbs");
			control.discard();
			retire(owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void theBlackFalconIsBackAfterDeathAndRespawn(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		UUID id = owner.getUUID();
		owner.setNoGravity(false);
		owner.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1_000_000.0F);
		helper.assertTrue(owner.isDeadOrDying(), "the archer survived, so the case proves nothing");
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(owner, false, Entity.RemovalReason.KILLED);
		helper.runAfterDelay(30L, () -> {
			FalconEntity black = Falcons.guardOf(reborn);
			LOG.info("[BEHAVIOR] test=theBlackFalconIsBackAfterDeathAndRespawn RESULT reborn={} falcon={} owner={}",
				reborn.getUUID().equals(id), black, black == null ? null : black.falconOwner());
			helper.assertTrue(black != null && black.isAlive() && id.equals(black.falconOwner()), "no black falcon circles the respawned archer: " + black);
			helper.assertTrue(black.distanceTo(reborn) < FalconEntity.HOVER_RADIUS + FalconEntity.HOVER_CLEARANCE + FalconEntity.HOVER_BOB + 2.0F,
				"the black falcon is " + black.distanceTo(reborn) + " blocks from the respawned archer");
			retire(reborn);
			helper.succeed();
		});
	}

	@GameTest
	public void withoutTheRpgModTheFalconStaysQuiet(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		FalconGuard.setRandom(owner.getUUID(), ALWAYS);
		Object saved = FabricLoader.getInstance().getObjectShare().get(FalconGuard.JOBS_SHARE);
		boolean absent;
		boolean absentStrike;
		boolean broken;
		boolean brokenStrike;
		boolean present;
		try {
			FabricLoader.getInstance().getObjectShare().remove(FalconGuard.JOBS_SHARE);
			absent = FalconGuard.holds(owner);
			absentStrike = ArcherSkills.guardCheck(owner);
			Function<ServerPlayer, String> fails = player -> {
				throw new IllegalStateException("the job share is broken");
			};
			FabricLoader.getInstance().getObjectShare().put(FalconGuard.JOBS_SHARE, Map.of(FalconGuard.INNATE_FUNCTION, fails));
			broken = FalconGuard.holds(owner);
			brokenStrike = ArcherSkills.guardCheck(owner);
		} finally {
			FabricLoader.getInstance().getObjectShare().remove(FalconGuard.JOBS_SHARE);
			if (saved != null) {
				FabricLoader.getInstance().getObjectShare().put(FalconGuard.JOBS_SHARE, saved);
			}
			Jobs.install();
			present = FalconGuard.holds(owner);
			ArcherSkills.clearFor(owner);
		}
		LOG.info("[BEHAVIOR] test=withoutTheRpgModTheFalconStaysQuiet RESULT absent={} absentStrike={} broken={} brokenStrike={} restored={}",
			absent, absentStrike, broken, brokenStrike, present);
		helper.assertTrue(!absent && !absentStrike, "the innate held, or the falcon struck, with no job share");
		helper.assertTrue(!broken && !brokenStrike, "the innate held, or the falcon struck, with a job share that fails");
		helper.assertTrue(present, "the stand-in did not return, so the case proves nothing about a present share");
		foe.discard();
		retire(owner);
		helper.succeed();
	}

	/** How the falcon looks above the player is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theBlackFalconLooksRightRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] choose Archer at the trainer. A larger black parrot circles above your head, opposite the brown Blitz Beat parrot, "
			+ "and never sits. Every few seconds it may dive at a nearby hostile three times.");
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
}
