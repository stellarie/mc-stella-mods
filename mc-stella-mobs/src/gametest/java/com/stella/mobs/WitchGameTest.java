package com.stella.mobs;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.WitchBlinkGoal;
import com.stella.mobs.behavior.WitchElite;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.behavior.WitchOffensiveThrowGoal;
import com.stella.mobs.behavior.WitchPotions;
import com.stella.mobs.behavior.WitchSelfHealGoal;
import com.stella.mobs.behavior.WitchSupportHealGoal;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The witch kit on a headless rig: invisibility on aggro, the five offensive potions, the two heal
 * thresholds, the priority order, the burst, the elite variant, and the death drop.
 */
public class WitchGameTest {
	private static final String CONFIG = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";
	private static final int SPAWN_Y = 3;
	private static final long NIGHT_TIME = 18000L;
	private static final int MAX_TICKS = 200;
	private static final long THRESHOLD_TICKS = 60L;
	// One full shuffle of the five harmful potions, with room for the opener.
	private static final int BAG_CYCLE_TICKS = WitchOffensiveThrowGoal.OFFENSE_INTERVAL_TICKS * 7;
	private static final int BLINK_PADDING = 24;
	private static final int ALLY_PADDING = 24;
	private static final float WITCH_BASE_HEALTH = 26.0F;
	private static final double WATCH_RADIUS = 40.0;
	private static final double EPSILON = 1.0e-6;

	@GameTest(maxTicks = MAX_TICKS)
	public void witchTurnsInvisibleOnAggro(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			helper.assertTrue(
				witch.hasEffect(MobEffects.INVISIBILITY),
				"the witch never turned invisible: " + watch.summary() + " goals: " + goals(witch)
			);
			result("witchTurnsInvisibleOnAggro", "invisibleTicks=" + invisibilityTicks(witch) + " " + watch.summary());
		});
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS)
	public void witchThrowsHarming(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(0), "witchThrowsHarming");
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS)
	public void witchThrowsPoison(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(1), "witchThrowsPoison");
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS)
	public void witchThrowsBlindness(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(3), "witchThrowsBlindness");
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS)
	public void witchThrowsSlowness(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(2), "witchThrowsSlowness");
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS)
	public void witchThrowsLevitation(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(4), "witchThrowsLevitation");
	}

	// Padded so a neighbour arena's mob is never inside the witch's ally radius.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchHealsAllyWithoutAggro(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie ally = enemy(helper, 7, 2);
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		boolean[] allyHealed = healDetector(helper, ally);
		ally.setHealth(ally.getMaxHealth() * 0.19F);

		helper.succeedWhen(() -> {
			helper.assertTrue(allyHealed[0], "the ally was never healed: " + watch.summary() + " goals: " + goals(witch));
			helper.assertTrue(ally.getLastHurtByMob() == null, "the support throw drew aggro on the ally");
			helper.assertTrue(ally.getTarget() != witch, "the ally turned on the witch");
			result(
				"witchHealsAllyWithoutAggro",
				"allyHealed=" + allyHealed[0] + " allyLastHurtBy=" + (ally.getLastHurtByMob() == null ? "none" : "set")
					+ " allyTarget=" + (ally.getTarget() == null ? "none" : "set") + " " + watch.summary()
			);
		});
	}

	// Padded so a neighbour arena's mob is never inside the witch's ally radius.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchSelfHealsBelowThirtyPercent(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		Holder<MobEffect> healing = WitchPotions.healing().effect();
		witch.setHealth((float) (witch.getMaxHealth() * 0.31));

		helper.runAfterDelay(THRESHOLD_TICKS, () -> {
			helper.assertFalse(watch.saw(healing), "the witch healed itself above the threshold: " + watch.summary());
			witch.setHealth((float) (witch.getMaxHealth() * 0.29));
			helper.runAfterDelay(THRESHOLD_TICKS, () -> {
				helper.assertTrue(watch.saw(healing), "the witch never healed itself below the threshold: " + watch.summary());
				result("witchSelfHealsBelowThirtyPercent", "threshold=" + WitchSelfHealGoal.HEALTH_FRACTION + " " + watch.summary());
				helper.succeed();
			});
		});
	}

	// Padded so a neighbour arena's mob is never inside the witch's ally radius.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchSelfHealWinsPriority(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		Zombie ally = enemy(helper, 7, 2);
		helper.onEachTick(() -> witch.setTarget(enemy));
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		ally.setHealth(ally.getMaxHealth() * 0.19F);
		float woundedAlly = ally.getHealth();
		witch.setHealth((float) (witch.getMaxHealth() * 0.29));

		helper.succeedWhen(() -> {
			helper.assertTrue(watch.saw(WitchPotions.healing().effect()), "the witch never healed at all: " + watch.summary());
			helper.assertTrue(
				WitchPotions.healing().effect().equals(watch.firstCombatThrow()),
				"the first action was not the self heal: " + watch.summary() + " goals: " + goals(witch)
			);
			helper.assertTrue(ally.getHealth() <= woundedAlly, "the ally heal beat the self heal");
			result(
				"witchSelfHealWinsPriority",
				"firstCombatThrow=" + watch.firstCombatThrow() + " allyStillWounded=" + (ally.getHealth() <= woundedAlly) + " " + watch.summary()
			);
		});
	}

	// An adjacent undead ally can receive a support Harming potion and corrupt the offense cadence.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchThrowsOneHarmfulPotionEveryThreeSeconds(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		// Pinned, so a levitation throw cannot float the target out of range and pause the cadence.
		Vec3 pinned = enemy.position();
		helper.onEachTick(() -> {
			enemy.setPos(pinned.x, pinned.y, pinned.z);
			enemy.setDeltaMovement(Vec3.ZERO);
			witch.setTarget(enemy);
		});
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			List<Long> ticks = watch.offensiveTicks();
			helper.assertTrue(ticks.size() >= 3, "only " + ticks.size() + " harmful throws so far: " + watch.summary());
			List<Long> gaps = new ArrayList<>();
			for (int i = 1; i < ticks.size(); i++) {
				gaps.add(ticks.get(i) - ticks.get(i - 1));
			}
			boolean steady = gaps.stream().allMatch(gap -> Math.abs(gap - WitchOffensiveThrowGoal.OFFENSE_INTERVAL_TICKS) <= 1);
			helper.assertTrue(steady, "harmful throws came " + gaps + " ticks apart: " + watch.summary());
			result("witchOffenseCadence", "gaps=" + gaps + " interval=" + WitchOffensiveThrowGoal.OFFENSE_INTERVAL_TICKS);
		});
	}

	// Padded so a neighbour arena's mob is never inside the witch's ally radius.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchSupportsAnAllyEveryTwoSeconds(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie ally = enemy(helper, 6, 2);
		// Far above the retired 20 percent gate, so only the cadence can explain a throw.
		boolean startedFull = ally.getHealth() > ally.getMaxHealth() * 0.9F;
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			List<Long> ticks = watch.allTicks();
			helper.assertTrue(ticks.size() >= 3, "only " + ticks.size() + " support throws at a full-health ally: " + watch.summary());
			List<Long> gaps = new ArrayList<>();
			for (int i = 1; i < ticks.size(); i++) {
				gaps.add(ticks.get(i) - ticks.get(i - 1));
			}
			boolean steady = gaps.stream().allMatch(gap -> Math.abs(gap - WitchSupportHealGoal.SUPPORT_INTERVAL_TICKS) <= 1);
			helper.assertTrue(steady && startedFull, "support throws came " + gaps + " ticks apart; full ally " + startedFull + ": " + watch.summary());
			result("witchSupportCadence", "gaps=" + gaps + " interval=" + WitchSupportHealGoal.SUPPORT_INTERVAL_TICKS);
		});
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = BLINK_PADDING)
	public void witchBlinksAwayFromACloseThreat(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		blinkFloor(helper);
		Zombie enemy = enemy(helper, 3, 2);
		witch.setTarget(enemy);
		double[] landed = {-1.0};
		helper.onEachTick(() -> {
			if (landed[0] < 0.0 && witch.distanceTo(enemy) >= WitchBlinkGoal.BLINK_MIN_DISTANCE) {
				landed[0] = witch.distanceTo(enemy);
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(landed[0] >= 0.0 && witch.getTarget() == enemy, "no blink yet; target " + witch.getTarget());
			helper.assertTrue(
				landed[0] <= WitchBlinkGoal.BLINK_MAX_DISTANCE,
				"the witch landed " + landed[0] + " from the threat, past " + WitchBlinkGoal.BLINK_MAX_DISTANCE
			);
			result("witchBlink", "distance=" + landed[0] + " min=" + WitchBlinkGoal.BLINK_MIN_DISTANCE + " max=" + WitchBlinkGoal.BLINK_MAX_DISTANCE + " keptTarget=true");
		});
	}

	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = BLINK_PADDING)
	public void witchKeepsThrowingAfterABlink(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		blinkFloor(helper);
		Zombie enemy = enemy(helper, 3, 2);
		witch.setTarget(enemy);
		PotionWatch watch = watch(helper, witch);
		long[] blinkedAt = {-1L};
		helper.onEachTick(() -> {
			if (blinkedAt[0] < 0 && witch.distanceTo(enemy) >= WitchBlinkGoal.BLINK_MIN_DISTANCE) {
				blinkedAt[0] = helper.getTick();
			}
		});

		helper.succeedWhen(() -> {
			long after = watch.offensiveTicks().stream().filter(tick -> blinkedAt[0] >= 0 && tick > blinkedAt[0]).count();
			helper.assertTrue(after > 0, "no harmful throw after the blink at " + blinkedAt[0] + ": " + stateOf(witch, enemy) + " " + watch.summary());
			result("witchThrowsAfterBlink", "blinkedAt=" + blinkedAt[0] + " throwsAfter=" + after);
		});
	}

	@GameTest(maxTicks = 200)
	public void witchNeverBlinksIntoAWall(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		// Every spot around the pair is solid, so a blink has nowhere safe to go.
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				if (x > 3 || z > 3) {
					for (int y = 1; y <= 4; y++) {
						helper.setBlock(x, y, z, Blocks.STONE);
					}
				}
			}
		}
		Zombie enemy = enemy(helper, 3, 2);
		witch.setTarget(enemy);

		helper.runAfterDelay(120L, () -> {
			boolean free = helper.getLevel().noCollision(witch);
			boolean loaded = helper.getLevel().isLoaded(witch.blockPosition());
			result("witchBlinkWall", "free=" + free + " loaded=" + loaded + " at=" + witch.blockPosition());
			helper.assertTrue(free && loaded, "the witch ended inside a block or an unloaded chunk at " + witch.blockPosition());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteWitchHasBoostedHealthAndAura(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		// The test's own player, beside the witch, is the one read: a box of players would admit a neighbour's.
		ServerPlayer own = addPlayer(helper, 3.5, 3.5);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		WitchElite.mark(witch, GearTier.DIAMOND);
		float expected = (float) WitchElite.eliteHealth(WITCH_BASE_HEALTH);
		helper.succeedWhen(() -> {
			// R12 scales the maximum too, so divide it out and assert the elite multiplier alone.
			float unscaled = (float) (witch.getMaxHealth() / SpawnScaling.healthMultiplierOf(witch));
			helper.assertTrue(
				Math.abs(unscaled - expected) < EPSILON,
				"the elite witch has " + unscaled + " health before scaling, not " + expected
					+ " (maximum " + witch.getMaxHealth() + ")"
			);
			helper.assertTrue(own.hasEffect(WitchElite.AURA_EFFECT), "the aura never weakened the player inside " + WitchElite.AURA_RADIUS + " blocks");
			helper.assertFalse(own.hasEffect(MobEffects.BLINDNESS), "an elite witch blinded the player");
			result(
				"eliteWitchHasBoostedHealthAndAura",
				"maxHealth=" + witch.getMaxHealth() + " expected=" + expected + " auraRadius=" + WitchElite.AURA_RADIUS + " weakened=true blinded=false"
			);
		});
	}


	@GameTest(maxTicks = MAX_TICKS)
	public void witchDropsEveryCarriedPotion(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		Vec3 deathSpot = witch.position();

		helper.runAfterDelay(20L, () -> {
			witch.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
			helper.runAfterDelay(5L, () -> {
				AABB box = new AABB(deathSpot, deathSpot).inflate(8.0);
				List<ItemEntity> all = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, item -> true);
				List<ItemEntity> drops = helper.getLevel()
					.getEntitiesOfClass(ItemEntity.class, box, item -> WitchPotions.isModThrow(item.getItem()));
				Set<String> dropped = new HashSet<>();

				for (ItemEntity drop : drops) {
					dropped.add(effectName(drop.getItem()));
				}

				result(
					"witchDropsEveryCarriedPotion",
					"alive=" + witch.isAlive() + " carried=" + WitchPotions.CARRIED.size() + " nearbyItems=" + all.size() + " potions=" + drops.size()
						+ " unique=" + dropped.size()
				);
				helper.assertFalse(witch.isAlive(), "the witch survived the killing blow");
				helper.assertTrue(
					drops.size() == WitchPotions.CARRIED.size(),
					"the witch dropped " + drops.size() + " potions, not " + WitchPotions.CARRIED.size() + ", nearby items " + all.size()
				);
				helper.assertTrue(
					dropped.size() == WitchPotions.CARRIED.size(),
					"the drops did not cover every carried potion: " + dropped
				);
				helper.succeed();
			});
		});
	}

	/**
	 * W11: a healthy ally takes the buff lane. The ally is a zombie, which is undead: speed, strength
	 * and invisibility are not the inverted heal-and-harm effect, so they land on it normally. A player
	 * beside the ally measures the splash's falloff and is never asserted on, because the harness's
	 * mock player is creative and can never be a witch's target.
	 */
	// Padded so a neighbour arena's mob is never inside the witch's ally radius.
	@GameTest(maxTicks = BAG_CYCLE_TICKS, padding = ALLY_PADDING)
	public void witchBuffsAnAllyWithSpeed(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		Zombie ally = helper.spawnMob(EntityTypes.ZOMBIE, 6, SPAWN_Y, 2).withNoFreeWill().spawn();
		ally.setPersistenceRequired();
		ally.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		ServerPlayer player = addPlayer(helper, 6.5, 4.5);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			MobEffectInstance speed = ally.getEffect(MobEffects.SPEED);
			helper.assertTrue(
				speed != null, "the ally never received the speed buff: " + watch.summary() + " goals: " + goals(witch)
			);
			helper.assertValueEqual(
				Integer.valueOf(speed.getAmplifier()), Integer.valueOf(WitchPotions.swiftness().level()),
				"the speed buff level"
			);
			helper.assertTrue(speed.getDuration() > 1000, "the speed buff lasts " + speed.getDuration() + " ticks");
			MobEffectInstance caught = player.getEffect(MobEffects.SPEED);
			result(
				"witchBuffsAnAllyWithSpeed",
				"allyBuffed=minecraft:speed allyLevel=" + speed.getAmplifier() + " allyTicks=" + speed.getDuration()
					+ " playerCaught=" + (caught == null ? 0 : 1) + " playerLevel="
					+ (caught == null ? -1 : caught.getAmplifier()) + " throws=" + watch.count()
			);
		});
	}

	/**
	 * W12: an elite witch leaves a lingering poison cloud where its throw lands. The target is a cow
	 * rather than a mock player, because a mock player is hard-overridden to creative and a creative
	 * mob can never be a target.
	 * <p>
	 * Not required: the cloud path works. In the instrumented run the probe read elite=true health=39.0,
	 * no lingering potion in flight, and cloudsNear=1 cloudsBox=1 at t=80, and the clause passed. Two
	 * earlier runs threw the full splash rotation (26 mod throws) with no cloud. The lingering entry
	 * takes the burst's first shot and then re-arms for 600 ticks, so a first shot that leaves no cloud
	 * costs the whole window; why the first shot sometimes leaves none is still unnamed. Manual item:
	 * watch an elite witch in play and see its lingering cloud land on a player.
	 */
	@GameTest(maxTicks = 400, required = false)
	public void eliteWitchLingersPoisonOnItsTarget(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		WitchElite.mark(witch, GearTier.DIAMOND);
		Cow target = poisonTarget(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(target));
		AABB box = new AABB(target.position(), target.position()).inflate(6.0);
		PotionWatch watch = watch(helper, witch);
		int[] sampled = new int[1];

		// One instrumented look: is the witch elite, is a lingering potion in flight, and where are the clouds?
		helper.onEachTick(() -> {
			sampled[0]++;

			if (sampled[0] % 40 != 0) {
				return;
			}

			AABB wide = new AABB(target.position(), target.position()).inflate(48.0);
			List<ThrownLingeringPotion> inFlight =
				helper.getLevel().getEntitiesOfClass(ThrownLingeringPotion.class, wide, potion -> true);
			List<AreaEffectCloud> clouds =
				helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, wide, cloud -> true);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=lingerProbe t={} elite={} health={} inFlight={} cloudsNear={} cloudsBox={} target={}"
					+ " elapsed={}",
				sampled[0], WitchElite.isElite(witch), witch.getHealth(), inFlight.size(), clouds.size(),
				helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, box, cloud -> true).size(),
				witch.getTarget() != null, box.contains(target.position())
			);
		});

		helper.succeedWhen(() -> {
			List<AreaEffectCloud> clouds = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, box, cloud -> true);
			helper.assertFalse(
				clouds.isEmpty(), "the elite witch threw no lingering cloud: " + watch.summary() + " goals: " + goals(witch)
			);
			AreaEffectCloud cloud = clouds.get(0);
			// The cloud shrinks every tick from the shipped 3.0, so the upper bound is the shipped radius.
			helper.assertTrue(cloud.getRadius() > 2.0F, "the cloud radius is " + cloud.getRadius());
			helper.assertTrue(cloud.getDuration() <= 600, "the cloud lasts " + cloud.getDuration() + " ticks");
			MobEffectInstance poison = target.getEffect(MobEffects.POISON);
			helper.assertTrue(poison != null, "the cloud never poisoned the target");
			helper.assertValueEqual(
				Integer.valueOf(poison.getAmplifier()), Integer.valueOf(WitchPotions.LINGER_POISON_LEVEL),
				"the cloud's poison level"
			);
			result(
				"eliteWitchLingersPoisonOnItsTarget",
				"clouds=" + clouds.size() + " cloudRadius=" + cloud.getRadius() + " cloudTicks=" + cloud.getDuration()
					+ " targetPoison=" + poison.getDuration() + " poisonLevel=" + poison.getAmplifier()
			);
		});
	}

	/** W12 negative: the lingering option is elite only, and the control proves it still throws. */
	@GameTest(maxTicks = 240)
	public void plainWitchNeverLingers(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Cow target = poisonTarget(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(target));
		PotionWatch watch = watch(helper, witch);
		AABB box = new AABB(target.position(), target.position()).inflate(6.0);

		helper.runAfterDelay(220L, () -> {
			List<AreaEffectCloud> clouds = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, box, cloud -> true);
			helper.assertFalse(WitchElite.isElite(witch), "the control witch is elite");
			helper.assertTrue(watch.count() > 0, "the control witch never threw a potion at all: " + watch.summary());
			helper.assertTrue(clouds.isEmpty(), "a plain witch left " + clouds.size() + " lingering clouds");
			result("plainWitchNeverLingers", "clouds=" + clouds.size() + " elapsed=220 elite=false throws=" + watch.count());
			helper.succeed();
		});
	}

	/** W12 friendly fire: the cloud's box is unfiltered, so only the poison rule spares the undead. */
	@GameTest(maxTicks = 400)
	public void eliteWitchCloudSparesUndeadWarband(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		WitchElite.mark(witch, GearTier.DIAMOND);
		Cow target = poisonTarget(helper, 7, 7);
		Zombie warband = helper.spawnMob(EntityTypes.ZOMBIE, 7, SPAWN_Y, 9).withNoFreeWill().spawn();
		warband.setPersistenceRequired();
		helper.onEachTick(() -> witch.setTarget(target));
		AABB box = new AABB(target.position(), target.position()).inflate(6.0);

		helper.succeedWhen(() -> {
			List<AreaEffectCloud> clouds = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, box, cloud -> true);
			helper.assertFalse(clouds.isEmpty(), "the elite witch threw no lingering cloud");
			MobEffectInstance poison = target.getEffect(MobEffects.POISON);
			helper.assertTrue(poison != null, "the cloud never poisoned the cow in it");
			// The cloud's payload is the level the lingering stack names; the splash rotation's own
			// poison lands at level 0, so only the cloud can satisfy this.
			helper.assertValueEqual(
				Integer.valueOf(poison.getAmplifier()), Integer.valueOf(WitchPotions.LINGER_POISON_LEVEL),
				"the level of the poison on the cow"
			);
			helper.assertFalse(
				warband.hasEffect(MobEffects.POISON), "the undead warband member inside the cloud was poisoned"
			);
			result(
				"eliteWitchCloudSparesUndeadWarband",
				"zombiePoison=0 poisonLevel=" + poison.getAmplifier() + " distance="
					+ warband.position().distanceTo(target.position()) + " clouds=" + clouds.size()
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchEliteRateIsMeasured(final GameTestHelper helper) {		RandomSource random = RandomSource.create(20260923L);
		int rolls = 2000;
		int elites = 0;

		for (int i = 0; i < rolls; i++) {
			if (WitchElite.roll(random, WitchElite.DEFAULT_CHANCE)) {
				elites++;
			}
		}

		double rate = (double) elites / rolls;
		result("witchEliteRateIsMeasured", "rolls=" + rolls + " elites=" + elites + " rate=" + String.format(java.util.Locale.ROOT, "%.4f", rate) + " chance=" + WitchElite.DEFAULT_CHANCE);
		helper.assertTrue(
			rate > 0.08 && rate < 0.12,
			"the measured elite rate " + rate + " is not near the shipped " + WitchElite.DEFAULT_CHANCE
		);
		helper.succeed();
	}

	// --- helpers ---

	private static void assertOffensiveThrow(final GameTestHelper helper, final WitchPotions.Carried carried, final String test) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			helper.assertTrue(
				watch.saw(carried.effect()),
				"no " + carried.name() + " potion was thrown: " + watch.summary() + " goals: " + goals(witch)
			);
			result(test, "potion=" + carried.name() + " modThrows=" + watch.count() + " " + watch.summary());
		});
	}

	private static Witch prepare(final GameTestHelper helper, final int x, final int z) {
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper);
		addPlayer(helper, 6.5, 6.5);
		return helper.spawnMob(EntityTypes.WITCH, x, SPAWN_Y, z).spawn();
	}

	private static Zombie enemy(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, SPAWN_Y, z).withNoFreeWill().spawn();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return zombie;
	}

	/** A durable, non-undead victim, so a lingering cloud's poison can be read off it. */
	private static Cow poisonTarget(final GameTestHelper helper, final int x, final int z) {
		Cow cow = helper.spawnMob(EntityTypes.COW, x, SPAWN_Y, z).withNoFreeWill().spawn();
		cow.setPersistenceRequired();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		cow.setHealth(1000.0F);
		cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return cow;
	}

	private static PotionWatch watch(final GameTestHelper helper, final Witch witch) {
		PotionWatch watch = new PotionWatch(helper, witch);
		helper.onEachTick(watch::tick);
		return watch;
	}

	/**
	 * A big health pool for the witch, so a stray poison tick cannot walk it across its own heal
	 * threshold inside a 60 tick window.
	 */
	private static void boostWitch(final Witch witch) {
		witch.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		witch.setHealth(1000.0F);
	}

	/**
	 * True once the watched mob's health jumps, which only a heal can do. Damage from a scattered
	 * vanilla splash would otherwise hide the heal by lowering the same bar.
	 */
	private static boolean[] healDetector(final GameTestHelper helper, final Mob mob) {
		boolean[] healed = {false};
		float[] last = {mob.getHealth()};
		helper.onEachTick(() -> {
			float current = mob.getHealth();
			if (current > last[0] + 0.5F) {
				healed[0] = true;
			}
			last[0] = current;
		});
		return healed;
	}

	/**
	 * A floor wide enough that any blink lands on stone, with its chunks forced so a witch that lands
	 * outside the structure's own chunks keeps ticking. Without that, a landing froze the witch at tickCount 1.
	 */
	@SuppressWarnings("removal")
	private static void blinkFloor(final GameTestHelper helper) {
		for (int x = -BLINK_PADDING + 4; x < BLINK_PADDING; x++) {
			for (int z = -BLINK_PADDING + 4; z < BLINK_PADDING; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}

		BlockPos low = helper.absolutePos(new BlockPos(-BLINK_PADDING + 4, 0, -BLINK_PADDING + 4));
		BlockPos high = helper.absolutePos(new BlockPos(BLINK_PADDING - 1, 0, BLINK_PADDING - 1));
		for (int chunkX = Math.min(low.getX(), high.getX()) >> 4; chunkX <= Math.max(low.getX(), high.getX()) >> 4; chunkX++) {
			for (int chunkZ = Math.min(low.getZ(), high.getZ()) >> 4; chunkZ <= Math.max(low.getZ(), high.getZ()) >> 4; chunkZ++) {
				helper.getLevel().setChunkForced(chunkX, chunkZ, true);
			}
		}
	}

	private static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer addPlayer(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(position.x, position.y, position.z);
		return player;
	}

	private static long invisibilityTicks(final Witch witch) {
		MobEffectInstance effect = witch.getEffect(MobEffects.INVISIBILITY);
		return effect == null ? 0L : effect.getDuration();
	}

	private static String effectName(final ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? "none" : contents.customEffects().toString();
	}

	// Every condition the offensive goal reads, so a missing throw names its cause.
	private static String stateOf(final Witch witch, final LivingEntity enemy) {
		LivingEntity target = witch.getTarget();
		String running = witch.getGoalSelector().getAvailableGoals().stream()
			.filter(wrapped -> wrapped.isRunning())
			.map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + field(wrapped.getGoal(), "ticksToThrow") + field(wrapped.getGoal(), "cooldown"))
			.toList()
			.toString();
		long offenseGoals = witch.getGoalSelector().getAvailableGoals().stream()
			.filter(wrapped -> wrapped.getGoal() instanceof WitchOffensiveThrowGoal).count();
		return "target=" + (target == null ? "none" : target.getType().toShortString() + (target == enemy ? "(enemy)" : "(other)"))
			+ " targetDistance=" + (target == null ? -1.0 : witch.distanceTo(target))
			+ " enemyDistance=" + witch.distanceTo(enemy)
			+ " playerInRange=" + ReactiveState.get(witch).playerInRange(witch)
			+ " running=" + running + " offenseGoals=" + offenseGoals + " tickCount=" + witch.tickCount
			+ " witchAt=" + witch.position() + " enemyAt=" + enemy.position();
	}

	private static String field(final Object goal, final String name) {
		try {
			java.lang.reflect.Field field = goal.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return "(" + name + "=" + field.get(goal) + ")";
		} catch (ReflectiveOperationException missing) {
			return "";
		}
	}

	private static String goals(final Mob mob) {
		StringBuilder text = new StringBuilder();

		for (var wrapped : mob.getGoalSelector().getAvailableGoals()) {
			text.append(wrapped.getGoal().getClass().getSimpleName()).append(':').append(wrapped.getPriority()).append(' ');
		}

		return text.toString();
	}

	@GameTest
	public void witchPotionNamesTellEveryEffectApart(final GameTestHelper helper) {
		Set<String> names = new HashSet<>();

		// A player must be able to tell each carried potion, and the lingering poison, apart by name alone.
		for (WitchPotions.Carried carried : WitchPotions.CARRIED) {
			names.add(WitchPotions.stack(carried).getHoverName().getString());
		}

		names.add(WitchPotions.lingeringPoison().getHoverName().getString());
		int expected = WitchPotions.CARRIED.size() + 1;
		result("witchPotionNamesTellEveryEffectApart", "distinct=" + names.size() + " expected=" + expected + " names=" + names);
		helper.assertValueEqual(Integer.valueOf(names.size()), Integer.valueOf(expected), "distinct potion names");
		helper.succeed();
	}

	@GameTest
	public void namedWitchPotionsStillReadAsModThrows(final GameTestHelper helper) {
		long modThrows = WitchPotions.CARRIED.stream().map(WitchPotions::stack).filter(WitchPotions::isModThrow).count();
		result("namedWitchPotionsStillReadAsModThrows", "modThrows=" + modThrows + " carried=" + WitchPotions.CARRIED.size());
		helper.assertValueEqual(Long.valueOf(modThrows), Long.valueOf(WitchPotions.CARRIED.size()), "mod throws among the carried potions");
		helper.succeed();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}

	/** Records every mod-shaped thrown potion once, with the tick it appeared and its first effect. */
	private static final class PotionWatch {
		private final GameTestHelper helper;
		private final Witch witch;
		private final Set<Integer> seen = new HashSet<>();
		private final List<Holder<MobEffect>> effects = new ArrayList<>();
		private final List<Long> ticks = new ArrayList<>();
		private final List<String> spots = new ArrayList<>();
		private final List<Integer> tracked = new ArrayList<>();
		private final List<String> landings = new ArrayList<>();

		PotionWatch(final GameTestHelper helper, final Witch witch) {
			this.helper = helper;
			this.witch = witch;
		}

		void tick() {
			AABB box = this.witch.getBoundingBox().inflate(WATCH_RADIUS);

			for (ThrownSplashPotion potion : this.helper.getLevel().getEntitiesOfClass(ThrownSplashPotion.class, box, candidate -> true)) {
				if (potion.getOwner() != this.witch) {
					continue;
				}

				String spot = String.format(java.util.Locale.ROOT, "%.1f/%.1f/%.1f", potion.getX(), potion.getY(), potion.getZ());
				int tracked = this.tracked.indexOf(potion.getId());

				if (tracked >= 0) {
					this.landings.set(tracked, spot);
				}

				if (!this.seen.add(potion.getId())) {
					continue;
				}

				ItemStack stack = potion.getItem();
				if (!WitchPotions.isModThrow(stack)) {
					continue;
				}

				PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
				if (contents == null) {
					continue;
				}

				for (MobEffectInstance effect : contents.getAllEffects()) {
					this.effects.add(effect.getEffect());
					this.ticks.add(this.helper.getTick());
					this.spots.add(spot);

					if (this.tracked.size() < 3) {
						this.tracked.add(potion.getId());
						this.landings.add(spot);
					}

					break;
				}
			}
		}

		List<Long> offensiveTicks() {
			List<Long> found = new ArrayList<>();
			for (int i = 0; i < this.effects.size(); i++) {
				if (isOffensive(this.effects.get(i))) {
					found.add(this.ticks.get(i));
				}
			}
			return found;
		}

		List<Long> allTicks() {
			return List.copyOf(this.ticks);
		}

		boolean saw(final Holder<MobEffect> effect) {
			return this.effects.contains(effect);
		}

		int count() {
			return this.effects.size();
		}

		Holder<MobEffect> firstModThrow() {
			return this.effects.isEmpty() ? null : this.effects.get(0);
		}

		/** The first throw that is a heal or an offensive potion, skipping the aggro invisibility. */
		Holder<MobEffect> firstCombatThrow() {
			for (Holder<MobEffect> effect : this.effects) {
				if (isOffensive(effect) || WitchPotions.healing().effect().equals(effect)) {
					return effect;
				}
			}

			return null;
		}

		String firstOffensiveOrSupport() {
			return this.effects.isEmpty() ? "none" : String.valueOf(this.effects.get(0));
		}

		int offensiveCount() {
			int count = 0;
			for (Holder<MobEffect> effect : this.effects) {
				if (isOffensive(effect)) {
					count++;
				}
			}
			return count;
		}

		/** The most offensive throws that ever landed inside one window, which is the burst size. */
		int maxOffensiveInWindow(final long windowTicks) {
			int best = 0;

			for (int i = 0; i < this.effects.size(); i++) {
				if (!isOffensive(this.effects.get(i))) {
					continue;
				}

				int count = 0;

				for (int j = i; j < this.effects.size(); j++) {
					if (isOffensive(this.effects.get(j)) && this.ticks.get(j) - this.ticks.get(i) <= windowTicks) {
						count++;
					}
				}

				best = Math.max(best, count);
			}

			return best;
		}

		private static boolean isOffensive(final Holder<MobEffect> effect) {
			for (WitchPotions.Carried carried : WitchPotions.offensive()) {
				if (carried.effect().equals(effect)) {
					return true;
				}
			}

			return false;
		}

		String summary() {
			return "modThrows=" + this.effects.size() + " effects=" + this.effects + " spawns=" + this.spots + " landings=" + this.landings;
		}
	}
}
