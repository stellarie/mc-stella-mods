package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mob that holds Blitz Beat through the {@code mod-stella-spells:mob-blitz-beat} share: it keeps the buff and
 * a falcon, its own melee and arrow hits roll the falcon, and each strike lands on the struck foe at the mob
 * attack damage. A strike never lands on an ally of the mob. The roll is forced, so no case waits on luck.
 */
public class MobBlitzGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.mobblitz");
	private static final String SHARE = "mod-stella-spells:mob-blitz-beat";
	private static final double OWNER_X = 1.5;
	private static final double OWNER_Z = 1.5;
	private static final double ATTACK = 7.0;
	private static final double TARGET_HEALTH = 1000.0;
	/** The most the falcon may sit from its owner: the circle, plus the climb above the head. */
	private static final double HOME_REACH = FalconEntity.HOVER_RADIUS + FalconEntity.HOVER_CLEARANCE + FalconEntity.HOVER_BOB + 2.0;
	private static final double STRIKE_REACH = 1.8;
	/** The arrow needs a few ticks to land before the strikes are owed. */
	private static final long FLIGHT_TICKS = 30L;
	private static final long SETTLE_TICKS = 12L * ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS + 60L;
	/** What each watched victim took, in order; the first entry is the blow, the rest are falcon strikes. */
	private static final Map<UUID, List<Float>> TAKEN = new ConcurrentHashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			List<Float> taken = TAKEN.get(entity.getUUID());
			if (taken != null && !blocked) {
				taken.add(damage);
			}
		});
	}

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
	public void theShareGivesAMobAPermanentBlitzBeatAndAFalconOverIt(final GameTestHelper helper) {
		ready(helper);
		Mob skeleton = mob(helper, EntityTypes.SKELETON, OWNER_X, OWNER_Z);
		share().accept(skeleton);
		MobEffectInstance held = skeleton.getEffect(SpellStatus.BLITZ_BEAT);
		helper.runAfterDelay(30L, () -> {
			FalconEntity falcon = falconOf(helper, skeleton);
			double away = falcon == null ? -1.0 : falcon.position().distanceTo(skeleton.position());
			LOG.info("[BEHAVIOR] test=theShareGivesAMobAPermanentBlitzBeatAndAFalconOverIt RESULT held={} infinite={} falcon={} away={}",
				held != null, held != null && held.isInfiniteDuration(), falcon != null, away);
			helper.assertTrue(held != null && held.isInfiniteDuration(), "the share left no permanent buff");
			helper.assertTrue(falcon != null, "the buffed mob got no falcon");
			helper.assertTrue(away >= 0.0 && away <= HOME_REACH, "the falcon flew " + away + " blocks from the mob");
			discard(skeleton, falcon);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void aSkeletonArrowHitCallsTheFalconWhichStrikesTheTargetAtTheMobAttackDamage(final GameTestHelper helper) {
		ready(helper);
		Skeleton skeleton = (Skeleton) mob(helper, EntityTypes.SKELETON, OWNER_X, OWNER_Z);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Mob target = target(helper, OWNER_X, OWNER_Z + 3.0);
		share().accept(skeleton);
		List<Float> taken = watch(target);
		double[] closest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			FalconEntity falcon = falconOf(helper, skeleton);
			if (falcon != null && falcon.swooping()) {
				closest[0] = Math.min(closest[0], falcon.position().distanceTo(target.getBoundingBox().getCenter()));
			}
		});
		MobBlitz.setRandom(skeleton, new Always());
		skeleton.performRangedAttack(target, 1.0F);
		helper.runAfterDelay(FLIGHT_TICKS, () -> MobBlitz.setRandom(skeleton, null));
		int want = 1 + ArcherSkills.BLITZ_BEAT_BOW_STRIKES;
		helper.runAfterDelay(FLIGHT_TICKS + SETTLE_TICKS, () -> {
			MobBlitz.setRandom(skeleton, null);
			LOG.info("[BEHAVIOR] test=aSkeletonArrowHitCallsTheFalconWhichStrikesTheTargetAtTheMobAttackDamage RESULT taken={} closest={}", taken, closest[0]);
			helper.assertTrue(taken.size() == want, "the target took " + taken.size() + " hits, not " + want + ": " + taken);
			for (Float strike : taken.subList(1, taken.size())) {
				helper.assertTrue(Math.abs(strike - ATTACK) < 0.01, "a falcon strike dealt " + strike + ", not the mob attack " + ATTACK);
			}
			helper.assertTrue(closest[0] <= STRIKE_REACH, "the falcon came no nearer than " + closest[0] + " blocks to the target");
			discard(skeleton, target, falconOf(helper, skeleton));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void aMobMeleeHitCallsTheFalconAndNeverStrikesItsOwner(final GameTestHelper helper) {
		ready(helper);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, OWNER_X, OWNER_Z);
		Mob target = target(helper, OWNER_X, OWNER_Z + 1.0);
		share().accept(zombie);
		List<Float> taken = watch(target);
		List<Float> own = watch(zombie);
		MobBlitz.setRandom(zombie, new Always());
		try {
			zombie.doHurtTarget(helper.getLevel(), target);
		} finally {
			MobBlitz.setRandom(zombie, null);
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			LOG.info("[BEHAVIOR] test=aMobMeleeHitCallsTheFalconAndNeverStrikesItsOwner RESULT taken={} ownerTook={}", taken, own);
			helper.assertTrue(taken.size() == 1 + ArcherSkills.BLITZ_BEAT_STRIKES, "the target took " + taken.size() + " hits: " + taken);
			helper.assertTrue(own.isEmpty(), "the owner took " + own);
			discard(zombie, target, falconOf(helper, zombie));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void aStrikeNeverLandsOnAnAllyOfTheMob(final GameTestHelper helper) {
		ready(helper);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, OWNER_X, OWNER_Z);
		Mob fellowHostile = mob(helper, EntityTypes.SKELETON, OWNER_X + 2.0, OWNER_Z);
		fellowHostile.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		fellowHostile.setHealth((float) TARGET_HEALTH);
		Mob teamed = target(helper, OWNER_X, OWNER_Z + 2.0);
		PlayerTeam team = helper.getLevel().getScoreboard().addPlayerTeam("mobblitz_" + zombie.getUUID().toString().substring(0, 8));
		helper.getLevel().getScoreboard().addPlayerToTeam(zombie.getScoreboardName(), team);
		helper.getLevel().getScoreboard().addPlayerToTeam(teamed.getScoreboardName(), team);
		share().accept(zombie);
		List<Float> hostileTook = watch(fellowHostile);
		List<Float> teamedTook = watch(teamed);
		MobBlitz.setRandom(zombie, new Always());
		try {
			zombie.doHurtTarget(helper.getLevel(), fellowHostile);
			zombie.doHurtTarget(helper.getLevel(), teamed);
		} finally {
			MobBlitz.setRandom(zombie, null);
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			LOG.info("[BEHAVIOR] test=aStrikeNeverLandsOnAnAllyOfTheMob RESULT hostileTook={} teamedTook={}", hostileTook, teamedTook);
			helper.assertTrue(hostileTook.size() == 1, "a fellow hostile took " + hostileTook);
			helper.assertTrue(teamedTook.size() == 1, "a teammate took " + teamedTook);
			helper.getLevel().getScoreboard().removePlayerTeam(team);
			discard(zombie, fellowHostile, teamed, falconOf(helper, zombie));
			helper.succeed();
		});
	}

	@SuppressWarnings("unchecked")
	private static Consumer<Mob> share() {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SHARE);
		if (!(shared instanceof Consumer<?>)) {
			throw new IllegalStateException("the share " + SHARE + " is not published");
		}
		return (Consumer<Mob>) shared;
	}

	private static void ready(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		helper.setTime(18_000L);
	}

	private static Mob mob(final GameTestHelper helper, final net.minecraft.world.entity.EntityType<? extends Mob> type, final double x, final double z) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a mob");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setSilent(true);
		mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** A big, tough, unmoving target: an iron golem takes an arrow from a few blocks away without a miss. */
	private static Mob target(final GameTestHelper helper, final double x, final double z) {
		Mob golem = mob(helper, EntityTypes.IRON_GOLEM, x, z);
		golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		golem.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		golem.setHealth((float) TARGET_HEALTH);
		return golem;
	}

	private static List<Float> watch(final LivingEntity victim) {
		List<Float> taken = new CopyOnWriteArrayList<>();
		TAKEN.put(victim.getUUID(), taken);
		return taken;
	}

	private static FalconEntity falconOf(final GameTestHelper helper, final Mob owner) {
		ServerLevel level = helper.getLevel();
		for (FalconEntity falcon : level.getEntitiesOfClass(FalconEntity.class, owner.getBoundingBox().inflate(16.0))) {
			if (owner.getUUID().equals(falcon.falconOwner()) && falcon.isAlive()) {
				return falcon;
			}
		}
		return null;
	}

	private static void discard(final Entity... subjects) {
		for (Entity subject : subjects) {
			if (subject != null) {
				TAKEN.remove(subject.getUUID());
				subject.discard();
			}
		}
	}
}
