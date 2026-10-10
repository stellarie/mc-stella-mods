package com.stella.spells;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Summon Wolf: a wolf tamed to the caster, with health from the caster stats, a guard of 6 blocks around the
 * caster, a follow, the aura of a tamed elite wolf and a rider. It never takes a farm animal. Steering with the
 * keys needs a client, so that is a manual item.
 */
public class SummonWolfGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summonwolf");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final long SETTLE_TICKS = 70L;

	@GameTest(maxTicks = 40)
	public void summonWolfTamesAWolfToTheCasterAndStartsTheCooldown(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkillSchoolGameTest.book("summon_wolf")));
		SpellCasting.CastResult result = SpellCasting.cast(caster, SkillSchoolGameTest.book("summon_wolf"), InteractionHand.MAIN_HAND);
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		boolean cooling = caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		LOG.info("[BEHAVIOR] test=summonWolfTamesAWolfToTheCasterAndStartsTheCooldown RESULT result={} wolf={} tame={} owned={} cooling={}",
			result, wolf != null, wolf != null && wolf.isTame(), wolf != null && wolf.isOwnedBy(caster), cooling);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "summon wolf read " + result);
		helper.assertTrue(wolf != null && wolf.getType() == SpellEntities.SUMMONED_WOLF, "no summoned wolf stands in the level");
		helper.assertTrue(wolf.isTame() && wolf.isOwnedBy(caster), "the wolf is not tamed to the caster");
		helper.assertTrue(wolf.entityTags().contains(SummonSkeleton.SUMMON_TAG)
			&& wolf.entityTags().contains(SummonSkeleton.OWNER_TAG_PREFIX + caster.getUUID()), "the wolf carries no summon marks");
		helper.assertTrue(Friends.isFriend(wolf) && !Friends.isFoe(wolf), "the summon mark does not make the wolf a friend");
		helper.assertTrue(cooling, "a landed cast started no cooldown");
		helper.assertTrue(BuiltInRegistries.ITEM.getOptional(Spellbooks.scrollId(SummonWolf.ID)).isEmpty(), "a scroll copies summon wolf");
		wolf.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theWolfHealthRisesWithTheCasterStatsAndKeepsABaseWithoutTheRpgMod(final GameTestHelper helper) {
		ServerPlayer plain = caster(helper);
		SummonWolf.apply(plain, null, plain.position(), SkillSchoolGameTest.modifiers(plain, false));
		SummonedWolf plainWolf = SummonWolf.find(helper.getLevel().getServer(), plain.getUUID());
		ServerPlayer weak = caster(helper);
		ServerPlayer strong = caster(helper);
		StatStub stub = new StatStub(weak.getUUID(), 10.0, strong.getUUID(), 100.0);
		// The stub is global and the cases run side by side, so it is held only across the two casts.
		RpgBridge.setStats(stub);
		try {
			SummonWolf.apply(weak, null, weak.position(), SkillSchoolGameTest.modifiers(weak, false));
			SummonWolf.apply(strong, null, strong.position(), SkillSchoolGameTest.modifiers(strong, false));
		} finally {
			RpgBridge.setStats(null);
		}
		SummonedWolf weakWolf = SummonWolf.find(helper.getLevel().getServer(), weak.getUUID());
		SummonedWolf strongWolf = SummonWolf.find(helper.getLevel().getServer(), strong.getUUID());
		LOG.info("[BEHAVIOR] test=theWolfHealthRisesWithTheCasterStatsAndKeepsABaseWithoutTheRpgMod RESULT plain={} weak={} strong={} base={} perStat={}",
			plainWolf == null ? -1 : plainWolf.getMaxHealth(), weakWolf == null ? -1 : weakWolf.getMaxHealth(),
			strongWolf == null ? -1 : strongWolf.getMaxHealth(), SummonWolf.BASE_HEALTH, SummonWolf.HEALTH_PER_STAT);
		helper.assertTrue(plainWolf != null && weakWolf != null && strongWolf != null, "a cast raised no wolf");
		helper.assertTrue(plainWolf.getMaxHealth() == (float) SummonWolf.BASE_HEALTH, "no RPG mod gave " + plainWolf.getMaxHealth() + ", not the base");
		helper.assertTrue(weakWolf.getMaxHealth() > plainWolf.getMaxHealth(), "10 stat points added no health");
		helper.assertTrue(strongWolf.getMaxHealth() > weakWolf.getMaxHealth(), "100 stat points gave no more health than 10");
		helper.assertTrue(Math.abs(weakWolf.getMaxHealth() - (SummonWolf.BASE_HEALTH + SummonWolf.HEALTH_PER_STAT * 10.0)) < 0.01
			&& Math.abs(strongWolf.getMaxHealth() - (SummonWolf.BASE_HEALTH + SummonWolf.HEALTH_PER_STAT * 100.0)) < 0.01,
			"the health is not the base plus the stats");
		plainWolf.discard();
		weakWolf.discard();
		strongWolf.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aSecondCastCallsTheSameWolfToTheCaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf first = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		helper.assertTrue(first != null, "the first cast raised no wolf");
		Vec3 far = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z + 9.0));
		first.teleportTo(far.x, far.y, far.z);
		boolean again = SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf second = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		int standing = helper.getLevel().getEntitiesOfClass(SummonedWolf.class, caster.getBoundingBox().inflate(40.0), wolf -> wolf.isOwnedBy(caster)).size();
		double distance = first.position().distanceTo(caster.position());
		LOG.info("[BEHAVIOR] test=aSecondCastCallsTheSameWolfToTheCaster RESULT again={} same={} standing={} distance={}", again, second == first, standing, distance);
		helper.assertTrue(again && second == first, "the second cast raised another wolf instead of calling the first");
		helper.assertTrue(standing == 1, standing + " wolves stand for one caster");
		helper.assertTrue(distance < 3.0, "the called wolf stands " + distance + " blocks away");
		first.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void theWolfTakesAHostileWithinSixBlocksOfItsMaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X + 4.0, CASTER_Z);
		helper.assertTrue(wolf != null, "no wolf");
		boolean[] targeted = {false};
		helper.onEachTick(() -> targeted[0] |= wolf.getTarget() == foe);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			double lost = 1000.0 - foe.getHealth();
			LOG.info("[BEHAVIOR] test=theWolfTakesAHostileWithinSixBlocksOfItsMaster RESULT targeted={} lost={} bitBy={}",
				targeted[0], lost, foe.getLastHurtByMob() == wolf);
			helper.assertTrue(targeted[0], "the wolf never targeted a hostile 4 blocks from its master");
			helper.assertTrue(lost > 0.0 && foe.getLastHurtByMob() == wolf, "the wolf never bit the hostile");
			wolf.discard();
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void theWolfIgnoresAHostileBeyondSixBlocksOfItsMaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X + 10.0, CASTER_Z);
		helper.assertTrue(wolf != null, "no wolf");
		boolean[] targeted = {false};
		helper.onEachTick(() -> targeted[0] |= wolf.getTarget() != null);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			LOG.info("[BEHAVIOR] test=theWolfIgnoresAHostileBeyondSixBlocksOfItsMaster RESULT targeted={} lost={} guard={}",
				targeted[0], 1000.0 - foe.getHealth(), SummonedWolf.GUARD_RADIUS);
			helper.assertTrue(SummonedWolf.GUARD_RADIUS == 6.0, "the guard radius is " + SummonedWolf.GUARD_RADIUS);
			helper.assertFalse(targeted[0], "the wolf took a hostile 10 blocks from its master");
			helper.assertTrue(foe.getHealth() == 1000.0F, "the far hostile was hurt");
			wolf.discard();
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void theWolfNeverTargetsOrBitesAFarmAnimal(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		helper.assertTrue(wolf != null, "no wolf");
		List<Mob> farm = List.of(
			farmAnimal(helper, EntityTypes.SHEEP.create(helper.getLevel(), EntitySpawnReason.TRIGGERED), 3.0),
			farmAnimal(helper, EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED), 4.0),
			farmAnimal(helper, EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.TRIGGERED), 5.0),
			farmAnimal(helper, EntityTypes.CHICKEN.create(helper.getLevel(), EntitySpawnReason.TRIGGERED), 6.0));
		boolean[] canAttack = {false};
		for (Mob animal : farm) {
			canAttack[0] |= wolf.canAttack(animal);
			wolf.setTarget(animal);
		}
		boolean forced = wolf.getTarget() != null;
		boolean[] targeted = {false};
		helper.onEachTick(() -> targeted[0] |= wolf.getTarget() != null);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			boolean hurt = farm.stream().anyMatch(animal -> animal.getHealth() < animal.getMaxHealth());
			LOG.info("[BEHAVIOR] test=theWolfNeverTargetsOrBitesAFarmAnimal RESULT canAttack={} forced={} targeted={} hurt={} kinds={}",
				canAttack[0], forced, targeted[0], hurt, farm.stream().map(animal -> animal.getType().toShortString()).toList());
			helper.assertTrue(farm.get(0) instanceof Sheep && farm.get(1) instanceof Cow && farm.get(2) instanceof Pig && farm.get(3) instanceof Chicken,
				"the case did not build the four farm animals");
			helper.assertFalse(canAttack[0], "the wolf can attack a farm animal");
			helper.assertFalse(forced, "a farm animal was set as the wolf target");
			helper.assertFalse(targeted[0], "the wolf targeted something with only farm animals around");
			helper.assertFalse(hurt, "a farm animal was hurt");
			wolf.discard();
			farm.forEach(Mob::discard);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void theWolfFollowsItsMasterWhenIdle(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		helper.assertTrue(wolf != null, "no wolf");
		Vec3 away = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z + 10.0));
		caster.setPos(away.x, away.y, away.z);
		double before = wolf.position().distanceTo(caster.position());
		helper.runAfterDelay(100L, () -> {
			double after = wolf.position().distanceTo(caster.position());
			LOG.info("[BEHAVIOR] test=theWolfFollowsItsMasterWhenIdle RESULT before={} after={}", before, after);
			helper.assertTrue(before > 7.0, "the case left the master only " + before + " blocks from the wolf");
			helper.assertTrue(after < before - 3.0 && after < 6.0, "the wolf went from " + before + " to " + after + " blocks and did not follow");
			wolf.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void aRiddenWolfBitesAHostileInFrontOfItsRiderAndSparesAFarmAnimal(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		helper.assertTrue(wolf != null, "no wolf");
		boolean mounted = caster.startRiding(wolf, true, true);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		Vec3 at = wolf.position();
		Mob foe = FighterBooksGameTest.foe(helper, at.x - helper.absoluteVec(Vec3.ZERO).x, at.z - helper.absoluteVec(Vec3.ZERO).z + 2.0);
		Mob sheep = farmAnimal(helper, EntityTypes.SHEEP.create(helper.getLevel(), EntitySpawnReason.TRIGGERED), 0.0);
		sheep.snapTo(at.x + 1.0, at.y, at.z + 1.5, 0.0F, 0.0F);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			double lost = 1000.0 - foe.getHealth();
			LOG.info("[BEHAVIOR] test=aRiddenWolfBitesAHostileInFrontOfItsRiderAndSparesAFarmAnimal RESULT mounted={} controlled={} lost={} bitBy={} sheepHurt={}",
				mounted, wolf.getControllingPassenger() == caster, lost, foe.getLastHurtByMob() == wolf, sheep.getHealth() < sheep.getMaxHealth());
			helper.assertTrue(mounted && wolf.getControllingPassenger() == caster, "the owner is not riding the wolf");
			helper.assertTrue(lost > 0.0 && foe.getLastHurtByMob() == wolf, "a ridden wolf never bit the hostile in front");
			helper.assertTrue(sheep.getHealth() == sheep.getMaxHealth(), "a ridden wolf bit the sheep");
			caster.stopRiding();
			wolf.discard();
			foe.discard();
			sheep.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void onlyTheOwnerRidesAndItTakesAnEmptyHandUse(final GameTestHelper helper) {
		ServerPlayer owner = caster(helper);
		SummonWolf.apply(owner, null, owner.position(), SkillSchoolGameTest.modifiers(owner, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), owner.getUUID());
		ServerPlayer stranger = caster(helper);
		helper.assertTrue(wolf != null, "no wolf");
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		var used = wolf.mobInteract(owner, InteractionHand.MAIN_HAND);
		boolean ridden = wolf.getFirstPassenger() == owner;
		owner.stopRiding();
		stranger.startRiding(wolf, true, true);
		boolean strangerSteers = wolf.getControllingPassenger() == stranger;
		LOG.info("[BEHAVIOR] test=onlyTheOwnerRidesAndItTakesAnEmptyHandUse RESULT used={} ridden={} strangerSteers={}", used, ridden, strangerSteers);
		helper.assertTrue(ridden, "an empty-hand use by the owner did not mount it");
		helper.assertFalse(strangerSteers, "a stranger steers the wolf");
		stranger.stopRiding();
		wolf.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void theAuraSpeedsAndStrengthensTheSummonerAndSlowsAndWeakensHostiles(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SummonWolf.apply(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		SummonedWolf wolf = SummonWolf.find(helper.getLevel().getServer(), caster.getUUID());
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X + 5.0, CASTER_Z);
		helper.assertTrue(wolf != null, "no wolf");
		helper.runAfterDelay(100L, () -> {
			var speed = caster.getEffect(MobEffects.SPEED);
			var strength = caster.getEffect(MobEffects.STRENGTH);
			LOG.info("[BEHAVIOR] test=theAuraSpeedsAndStrengthensTheSummonerAndSlowsAndWeakensHostiles RESULT speed={} strength={} slow={} weak={}",
				speed, strength, foe.getEffect(MobEffects.SLOWNESS), foe.getEffect(MobEffects.WEAKNESS));
			helper.assertTrue(speed != null && speed.getAmplifier() == SummonedWolf.AURA_AMPLIFIER, "the summoner got no Speed from the aura");
			helper.assertTrue(strength != null && strength.getAmplifier() == SummonedWolf.AURA_AMPLIFIER, "the summoner got no Strength from the aura");
			helper.assertTrue(foe.hasEffect(MobEffects.SLOWNESS) && foe.hasEffect(MobEffects.WEAKNESS), "a hostile near the wolf was not slowed and weakened");
			helper.assertTrue(SummonedWolf.AURA_RADIUS == 8.0 && SummonedWolf.AURA_INTERVAL_TICKS == 40 && SummonedWolf.AURA_TICKS == 80,
				"the aura numbers drifted from the tamed elite wolf");
			caster.removeAllEffects();
			wolf.discard();
			foe.discard();
			helper.succeed();
		});
	}

	/** Steering takes the rider's key input, which only a client sends. */
	@GameTest(required = false, maxTicks = 40)
	public void steeringTheRiddenWolfWithTheKeysRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] summon the wolf, right-click it with an empty hand, steer with W A S D, and watch it bite the hostile in front. This harness has no key input.");
	}

	private static Mob farmAnimal(final GameTestHelper helper, final Mob animal, final double offset) {
		helper.assertTrue(animal != null, "the level refused a farm animal");
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X + 1.0, 1.0, CASTER_Z + offset));
		animal.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		animal.setNoAi(true);
		helper.getLevel().addFreshEntity(animal);
		return animal;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		return player;
	}

	/** Two casters with two stat totals. */
	private static final class StatStub implements RpgBridge.Stats {
		private final UUID weak;
		private final double weakTotal;
		private final UUID strong;
		private final double strongTotal;

		StatStub(final UUID weak, final double weakTotal, final UUID strong, final double strongTotal) {
			this.weak = weak;
			this.weakTotal = weakTotal;
			this.strong = strong;
			this.strongTotal = strongTotal;
		}

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double statTotal(final LivingEntity caster) {
			return caster.getUUID().equals(this.weak) ? this.weakTotal : caster.getUUID().equals(this.strong) ? this.strongTotal : Double.NaN;
		}
	}
}
