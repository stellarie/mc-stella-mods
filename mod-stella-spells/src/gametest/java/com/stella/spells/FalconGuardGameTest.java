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
 * The Archer innate, Falcon Guard. A black falcon circles above the archer beside the brown Blitz Beat falcon. On each
 * blow of the archer it has a 35 percent chance, plus the Blitz Beat LUK bonus, to strike the mob that was hit three
 * times. Every two seconds, when the archer has struck no blow for five seconds, it strikes the nearest hostile three
 * times. Each hit is the average of the lowest and highest ranged roll. A foe it kills pays the archer 25 percent of
 * its experience. The job comes from the RPG mod's job share, which this class stands in for.
 */
public class FalconGuardGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.falconguard");
	private static final double OWNER_X = 1.5;
	private static final double OWNER_Z = 1.5;
	private static final double LOW_ROLL = 20.0;
	private static final double HIGH_ROLL = 60.0;
	private static final int SAMPLES = 1_500;
	private static final double HEIGHT_SLACK = 0.1;
	/** A blow too small to change the case, and a blow big enough to tell from a falcon hit. */
	private static final float TAP = 0.01F;
	private static final float BLOW = 3.0F;
	private static final double LUK_POINTS = 100.0;

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

	/** A source whose every roll is a success. */
	private static final RandomSource ALWAYS = new LegacyRandomSource(1L) {
		@Override
		public double nextDouble() {
			return 0.0;
		}
	};

	/** A source whose every roll fails, so a blow calls nothing. */
	private static final RandomSource NEVER = new LegacyRandomSource(1L) {
		@Override
		public double nextDouble() {
			return 0.999;
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

	/** One melee blow of the archer at the foe. True when it left hits owed to the falcon or to Blitz Beat. */
	private static boolean blow(final GameTestHelper helper, final ServerPlayer owner, final Mob foe, final float amount) {
		foe.damageCooldownTime = 0;
		foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), amount);
		return ArcherSkills.pulsesOwed(owner) > 0;
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
	public void aboutThirtyFivePercentOfBlowsCallTheFalconAndLukRaisesTheChance(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		UUID id = owner.getUUID();
		int plain = 0;
		int lucky = 0;
		int unjobbed = 0;
		double plainChance;
		double luckyChance;
		try {
			plainChance = ArcherSkills.guardChance(owner);
			FalconGuard.setRandom(id, RandomSource.create(35L));
			for (int blow = 0; blow < SAMPLES; blow++) {
				plain += blow(helper, owner, foe, TAP) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
			ArcherSkills.setBlitzStats((holder, key) -> holder.getUUID().equals(id) && key.equals("luk") ? LUK_POINTS : 0.0);
			luckyChance = ArcherSkills.guardChance(owner);
			FalconGuard.setRandom(id, RandomSource.create(36L));
			for (int blow = 0; blow < SAMPLES; blow++) {
				lucky += blow(helper, owner, foe, TAP) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
			ArcherSkills.setBlitzStats(null);
			FalconGuard.setRandom(id, ALWAYS);
			Jobs.INNATES.put(id, "none");
			for (int blow = 0; blow < SAMPLES / 5; blow++) {
				unjobbed += blow(helper, owner, foe, TAP) ? 1 : 0;
				ArcherSkills.clearFor(owner);
			}
		} finally {
			ArcherSkills.setBlitzStats(null);
			retire(owner);
			foe.discard();
		}
		double sigmaPlain = Math.sqrt(SAMPLES * plainChance * (1.0 - plainChance));
		double sigmaLucky = Math.sqrt(SAMPLES * luckyChance * (1.0 - luckyChance));
		LOG.info("[BEHAVIOR] test=aboutThirtyFivePercentOfBlowsCallTheFalconAndLukRaisesTheChance RESULT plain={} of={} chance={} lucky={} luckyChance={} unjobbed={}",
			plain, SAMPLES, plainChance, lucky, luckyChance, unjobbed);
		helper.assertTrue(FalconGuard.CHANCE == 0.35 && FalconGuard.CHECK_TICKS == 40 && FalconGuard.IDLE_TICKS == 100
			&& FalconGuard.HITS == 3 && FalconGuard.XP_SHARE == 0.25,
			"the falcon numbers drifted from 35 percent, 2 s, 5 s idle, 3 hits and 25 percent");
		helper.assertTrue(ArcherSkills.BLITZ_BEAT_CHANCE_PER_LUK == 0.002, "the LUK bonus drifted from 1 percent per 5 LUK");
		helper.assertTrue(Math.abs(plainChance - 0.35) < 1e-9 && Math.abs(luckyChance - 0.55) < 1e-9,
			"the chance is " + plainChance + " and with 100 LUK " + luckyChance + ", not 0.35 and 0.55");
		helper.assertTrue(Math.abs(plain - SAMPLES * plainChance) <= 3.0 * sigmaPlain, "the falcon came on " + plain + " of " + SAMPLES + " blows");
		helper.assertTrue(Math.abs(lucky - SAMPLES * luckyChance) <= 3.0 * sigmaLucky,
			"with 100 LUK the falcon came on " + lucky + " of " + SAMPLES + " blows");
		helper.assertTrue(unjobbed == 0, "an archer without the innate, whose every roll succeeds, called the falcon " + unjobbed + " times");
		helper.succeed();
	}

	@GameTest(maxTicks = 260)
	public void eachStrikeIsThreeHitsOfTheAverageRollAndBlitzBeatStillWorksBeside(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		float average = (float) ((LOW_ROLL + HIGH_ROLL) / 2.0 / RpgBridge.k());
		float parrot = ArcherSkills.strikeDamage(owner, false);
		helper.assertTrue(Math.abs(average - parrot) > 1.0F && Math.abs(average - LOW_ROLL / RpgBridge.k()) > 1.0F
			&& Math.abs(average - HIGH_ROLL / RpgBridge.k()) > 1.0F, "the hit sizes cannot be told apart");
		// The stand-in covers the blow only, which is where the falcon fixes the size of its hits.
		FalconGuard.setRandom(owner.getUUID(), ALWAYS);
		boolean called;
		try {
			RpgBridge.setStats(ATK);
			called = blow(helper, owner, foe, TAP);
		} finally {
			RpgBridge.setStats(null);
		}
		FalconGuard.setRandom(owner.getUUID(), NEVER);
		helper.assertTrue(called, "the blow did not call the falcon");
		List<Long> hits = new ArrayList<>();
		List<Float> amounts = new ArrayList<>();
		float[] last = {foe.getHealth()};
		boolean[] guardSwooped = {false};
		boolean[] brownSwoopedFirst = {false};
		long blitzAt = 25L;
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
		// The blow starts the five seconds of rest again. Blitz Beat rests only two seconds, so its two idle strikes come
		// before the falcon could strike, and the window that is read ends first.
		helper.runAfterDelay(blitzAt, () -> {
			ArcherSkills.applyBlitzBeat(owner, null, owner.position(), SkillSchoolGameTest.modifiers(owner, false));
			ArcherSkills.setBlitzRandom(NEVER);
			try {
				blow(helper, owner, foe, TAP);
			} finally {
				ArcherSkills.setBlitzRandom(null);
			}
		});
		long readAt = blitzAt + FalconGuard.IDLE_TICKS - 2L;
		helper.runAfterDelay(readAt, () -> {
			List<Long> guardTicks = new ArrayList<>();
			int parrotHits = 0;
			for (int i = 0; i < hits.size(); i++) {
				if (Math.abs(amounts.get(i) - average) < 0.01F) {
					guardTicks.add(hits.get(i));
				} else if (Math.abs(amounts.get(i) - parrot) < 0.01F && hits.get(i) > blitzAt) {
					parrotHits++;
				}
			}
			LOG.info("[BEHAVIOR] test=eachStrikeIsThreeHitsOfTheAverageRollAndBlitzBeatStillWorksBeside RESULT average={} parrot={} hits={} amounts={} guard={} parrotHits={} swooped={} readAt={}",
				average, parrot, hits, amounts, guardTicks, parrotHits, guardSwooped[0], readAt);
			helper.assertTrue(readAt < blitzAt + FalconGuard.IDLE_TICKS, "the read window reaches the idle strikes of the falcon");
			helper.assertTrue(guardTicks.size() == FalconGuard.HITS, "the falcon landed " + guardTicks.size() + " hits of " + average + ": " + amounts);
			for (int i = 1; i < guardTicks.size(); i++) {
				helper.assertTrue(guardTicks.get(i) - guardTicks.get(i - 1) == ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS,
					"the hits came " + (guardTicks.get(i) - guardTicks.get(i - 1)) + " ticks apart");
			}
			helper.assertTrue(guardTicks.getLast() < blitzAt, "the strike came after Blitz Beat was cast, so the two overlap: " + guardTicks);
			helper.assertTrue(guardSwooped[0], "the black falcon never flew at the foe");
			helper.assertFalse(brownSwoopedFirst[0], "the brown falcon flew at the foe before Blitz Beat struck");
			helper.assertTrue(parrotHits >= 2 * ArcherSkills.PARROT_HITS, "Blitz Beat landed " + parrotHits + " idle hits of " + parrot + " beside the guard");
			helper.assertTrue(Falcons.guardOf(owner) != null && Falcons.of(owner) != null && Falcons.guardOf(owner) != Falcons.of(owner),
				"the two falcons do not both stand");
			foe.discard();
			retire(owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 460)
	public void anIdleArcherGetsThreeHitsEveryTwoSecondsOnlyAfterFiveSecondsWithoutABlow(final GameTestHelper helper) {
		ServerPlayer owner = archer(helper);
		Mob foe = foe(helper, OWNER_X + 3.0, OWNER_Z + 4.0);
		FalconGuard.setRandom(owner.getUUID(), NEVER);
		float guardHit = ArcherSkills.guardDamage(owner);
		helper.assertTrue(Math.abs(guardHit - BLOW) > 0.5F, "a blow and a falcon hit cannot be told apart");
		List<Long> hits = new ArrayList<>();
		long[] firstBlow = {-1};
		long[] secondBlow = {-1};
		float[] last = {foe.getHealth()};
		long[] lastHit = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			float now = foe.getHealth();
			if (now < last[0] && Math.abs((last[0] - now) - guardHit) < 0.01F) {
				hits.add(tick);
				lastHit[0] = tick;
			}
			last[0] = foe.getHealth();
			if (tick == 1) {
				blow(helper, owner, foe, BLOW);
				firstBlow[0] = tick;
				last[0] = foe.getHealth();
			}
			// A second blow, once two strikes are done and the next one has not begun.
			if (secondBlow[0] < 0 && hits.size() >= 2 * FalconGuard.HITS && tick - lastHit[0] == 3) {
				blow(helper, owner, foe, BLOW);
				secondBlow[0] = tick;
				last[0] = foe.getHealth();
			}
			if (secondBlow[0] > 0 && tick == secondBlow[0] + FalconGuard.IDLE_TICKS + 2L * FalconGuard.CHECK_TICKS + 20L) {
				List<List<Long>> strikes = strikes(hits);
				LOG.info("[BEHAVIOR] test=anIdleArcherGetsThreeHitsEveryTwoSecondsOnlyAfterFiveSecondsWithoutABlow RESULT firstBlow={} secondBlow={} hits={} strikes={}",
					firstBlow[0], secondBlow[0], hits, strikes);
				long beforeSecond = hits.stream().filter(at -> at < secondBlow[0]).count();
				helper.assertTrue(!strikes.isEmpty() && strikes.getFirst().getFirst() - firstBlow[0] >= FalconGuard.IDLE_TICKS,
					"the falcon struck at " + (strikes.isEmpty() ? -1 : strikes.getFirst().getFirst()) + ", " + (strikes.isEmpty() ? -1 : strikes.getFirst().getFirst() - firstBlow[0])
						+ " ticks after the first blow");
				for (List<Long> strike : strikes) {
					helper.assertTrue(strike.size() == FalconGuard.HITS, "a strike landed " + strike.size() + " hits: " + strikes);
					for (int i = 1; i < strike.size(); i++) {
						helper.assertTrue(strike.get(i) - strike.get(i - 1) == ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS, "the hits came unevenly: " + strike);
					}
				}
				helper.assertTrue(beforeSecond >= 2 * FalconGuard.HITS, "the idle falcon struck " + beforeSecond + " times before the second blow");
				for (int i = 1; i < strikes.size(); i++) {
					long gap = strikes.get(i).getFirst() - strikes.get(i - 1).getFirst();
					boolean acrossBlow = strikes.get(i - 1).getFirst() < secondBlow[0] && strikes.get(i).getFirst() > secondBlow[0];
					helper.assertTrue(acrossBlow ? gap >= FalconGuard.IDLE_TICKS : gap == FalconGuard.CHECK_TICKS,
						"strikes came " + gap + " ticks apart: " + strikes);
				}
				long afterSecond = hits.stream().filter(at -> at > secondBlow[0]).findFirst().orElse(-1L);
				helper.assertTrue(afterSecond >= secondBlow[0] + FalconGuard.IDLE_TICKS,
					"the falcon struck " + (afterSecond - secondBlow[0]) + " ticks after a blow, not " + FalconGuard.IDLE_TICKS);
				foe.discard();
				retire(owner);
				helper.succeed();
			}
		});
	}

	/** Groups hit ticks into strikes: a gap longer than the hit gap starts a new one. */
	private static List<List<Long>> strikes(final List<Long> hits) {
		List<List<Long>> strikes = new ArrayList<>();
		for (long tick : hits) {
			if (strikes.isEmpty() || tick - strikes.getLast().getLast() > ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS) {
				strikes.add(new ArrayList<>());
			}
			strikes.getLast().add(tick);
		}
		return strikes;
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
		// The blow leaves the foe alive, so the falcon makes the kill.
		FalconGuard.setRandom(owner.getUUID(), ALWAYS);
		boolean struck = blow(helper, owner, foe, TAP);
		FalconGuard.setRandom(owner.getUUID(), NEVER);
		helper.runAfterDelay(30L, () -> {
			int gained = owner.totalExperience - before;
			int orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, foeArea).size();
			LOG.info("[BEHAVIOR] test=aFoeTheFalconKillsPaysTwentyFivePercentOfItsExperienceAtOnce RESULT struck={} reward={} expected={} blitzShare={} gained={} orbs={} controlOrbValue={} alive={}",
				struck, reward, expected, blitzShare, gained, orbs, controlValue, foe.isAlive());
			helper.assertTrue(struck, "the blow queued no strike");
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
		boolean absentIdle;
		boolean broken;
		boolean brokenStrike;
		boolean present;
		boolean presentStrike;
		try {
			FabricLoader.getInstance().getObjectShare().remove(FalconGuard.JOBS_SHARE);
			absent = FalconGuard.holds(owner);
			absentStrike = blow(helper, owner, foe, TAP);
			absentIdle = ArcherSkills.guardIdleCheck(owner, ArcherSkills.dueIn(FalconGuard.IDLE_TICKS));
			ArcherSkills.clearFor(owner);
			Function<ServerPlayer, String> fails = player -> {
				throw new IllegalStateException("the job share is broken");
			};
			FabricLoader.getInstance().getObjectShare().put(FalconGuard.JOBS_SHARE, Map.of(FalconGuard.INNATE_FUNCTION, fails));
			broken = FalconGuard.holds(owner);
			brokenStrike = blow(helper, owner, foe, TAP);
			ArcherSkills.clearFor(owner);
		} finally {
			FabricLoader.getInstance().getObjectShare().remove(FalconGuard.JOBS_SHARE);
			if (saved != null) {
				FabricLoader.getInstance().getObjectShare().put(FalconGuard.JOBS_SHARE, saved);
			}
			Jobs.install();
			present = FalconGuard.holds(owner);
			presentStrike = blow(helper, owner, foe, TAP);
			ArcherSkills.clearFor(owner);
		}
		LOG.info("[BEHAVIOR] test=withoutTheRpgModTheFalconStaysQuiet RESULT absent={} absentStrike={} absentIdle={} broken={} brokenStrike={} restored={} restoredStrike={}",
			absent, absentStrike, absentIdle, broken, brokenStrike, present, presentStrike);
		helper.assertTrue(!absent && !absentStrike && !absentIdle, "the innate held, or the falcon struck, with no job share");
		helper.assertTrue(!broken && !brokenStrike, "the innate held, or the falcon struck, with a job share that fails");
		helper.assertTrue(present && presentStrike, "with the stand-in back the innate held " + present + " and the blow called the falcon " + presentStrike);
		foe.discard();
		retire(owner);
		helper.succeed();
	}

	/** How the falcon looks above the player is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theBlackFalconLooksRightRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] choose Archer at the trainer. A larger black parrot circles above your head, opposite the brown Blitz Beat parrot, "
			+ "and never sits. When you hit a mob it may dive at the mob three times. After five seconds without a hit it dives at the nearest hostile three times every two seconds.");
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
