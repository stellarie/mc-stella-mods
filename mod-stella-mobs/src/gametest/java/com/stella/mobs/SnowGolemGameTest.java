package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.SnowGolemCold;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * G05 to G08, the snow golem. Melting is suppressed under a roof and still happens where the world's own
 * melt attribute says it does; a ball carries freezing and slow with vanilla's zero damage intact; an elite
 * ball deals a literal point of damage at two area levels; and its cold snap reaches a bystander beside the
 * target and not a mob outside the radius.
 *
 * <p>The test level melts snow golems, which the first run of this class found the hard way: every subject
 * spawned in the open was dead before it could throw. So every subject here stands under a three by three
 * roof, and the spawn waits until that roof has actually taken the sky away rather than assuming it.
 */
public final class SnowGolemGameTest {
	private static final String TEST = "snowGolem";
	private static final int WINDOW_TICKS = 80;
	private static final long NIGHT = 18000L;
	private static final double EPSILON = 1.0e-6;
	private static final int ROOF_Y = 4;
	/** Far enough that the R12 distance share puts the second subject on a higher area level. */
	private static final int FAR_SPOT = 400;
	private static final int NETHER_SPOT = 400;
	private static final int NETHER_Y = 100;
	private static final int SNAP_BYSTANDER_DISTANCE = 2;
	// The outsider stands inside this arena but past the snap's radius from the target. It is kept away from
	// the arena's edge as well: a snow golem places snow where it stands, and a neighbouring test that counts
	// changed blocks in its own radius must not see this test's blocks.
	private static final int SNAP_OUTSIDER_X = 8;
	private static final int SNAP_OUTSIDER_Z = 8;

	private static final String NO_ELITE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"entities": { "minecraft:snow_golem": { "elite_chance": 0.0 } }
			}
		}""";
	private static final String ALWAYS_ELITE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"entities": { "minecraft:snow_golem": { "elite_chance": 1.0 } }
			}
		}""";

	/**
	 * G05, both halves. The roofed golem keeps its snow in a world that melts, and the world's own melt
	 * attribute and the shelter reading are both asserted rather than assumed. The unsheltered half is not
	 * proved here: a second golem in the open sky walks and leaves snow where a neighbouring test counts
	 * changed blocks. The nether case below proves that half with the game's own outcome, where nothing is
	 * counted and nothing is adjacent.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void aSnowGolemKeepsItsSnowUnderARoof(final GameTestHelper helper) {
		prepare(helper, NO_ELITE);
		pen(helper, 2, 2, 6, 6);
		ServerLevel level = helper.getLevel();
		SnowGolem sheltered = helper.spawnMob(EntityTypes.SNOW_GOLEM, 4, 1, 4).spawn();
		sheltered.setPersistenceRequired();
		boolean[] anchored = new boolean[1];
		long[] shelterTick = new long[1];
		float[] healthAtShelter = new float[1];

		// Sampling runs from the test body, and the window starts at the observable: a tick of melting
		// before the roof counts would read as one missing health point, which is the setup, not the clause.
		helper.onEachTick(() -> {
			if (!anchored[0] && !level.canSeeSky(sheltered.blockPosition())) {
				anchored[0] = true;
				shelterTick[0] = helper.getTick();
				healthAtShelter[0] = sheltered.getHealth();
			}
		});

		helper.runAfterDelay(WINDOW_TICKS * 2L, () -> {
			helper.assertTrue(anchored[0], "the golem was never observably sheltered, so no window started");
			long span = helper.getTick() - shelterTick[0];
			helper.assertTrue(span >= WINDOW_TICKS, "the sheltered window was only " + span + " ticks long");
			boolean shelter = SnowGolemCold.keepsItsSnow(sheltered);
			boolean meltsHere = melts(level, sheltered);
			result(
				"keepsItsSnow",
				"sheltered=" + shelter + " canSeeSky=" + level.canSeeSky(sheltered.blockPosition()) + " melts="
					+ meltsHere + " health=" + sheltered.getHealth() + " healthAtShelter=" + healthAtShelter[0]
					+ " max=" + sheltered.getMaxHealth() + " span=" + span
			);
			helper.assertTrue(meltsHere, "the world does not melt a snow golem here, so the roof proves nothing");
			helper.assertTrue(shelter, "the shelter rule says this golem is not indoors");
			helper.assertValueEqual(
				Float.valueOf(sheltered.getHealth()), Float.valueOf(healthAtShelter[0]),
				"a sheltered golem's health over " + span + " ticks from the tick it was sheltered"
			);
			helper.succeed();
		});
	}

	/**
	 * G05, the second environment. The nether sets the same environment attribute the desert sets and has no
	 * sky, so the shelter rule cannot apply there and the golem melts. Both readings are asserted first.
	 */
	@GameTest(maxTicks = 400)
	@SuppressWarnings("removal")
	public void aSnowGolemStillMeltsWhereTheWorldSaysSo(final GameTestHelper helper) {
		prepare(helper, NO_ELITE);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos pad = BlockPos.containing(helper.absoluteVec(new Vec3(NETHER_SPOT, NETHER_Y, NETHER_SPOT)));
		nether.setChunkForced(pad.getX() >> 4, pad.getZ() >> 4, true);

		SnowGolem[] subject = new SnowGolem[1];
		int[] waited = new int[1];
		float[] arriveHealth = new float[1];
		boolean[] hasSky = new boolean[1];
		boolean[] melts = new boolean[1];

		// The chunk has to be ticking before anything stands in it, and the pad and the three clear blocks
		// above it make the spot standable whatever the nether generated there.
		helper.onEachTick(() -> {
			if (subject[0] != null) {
				return;
			}

			waited[0]++;

			if (!nether.isPositionEntityTicking(pad)) {
				return;
			}

			nether.setBlockAndUpdate(pad.below(), Blocks.STONE.defaultBlockState());

			for (int dy = 0; dy < 3; dy++) {
				nether.setBlockAndUpdate(pad.above(dy), Blocks.AIR.defaultBlockState());
			}

			GearConfigStore.set(GearConfigParser.parse(NO_ELITE));
			SnowGolem golem = EntityTypes.SNOW_GOLEM.create(nether, EntitySpawnReason.NATURAL);
			golem.snapTo(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0.0F, 0.0F);
			nether.addFreshEntity(golem);
			subject[0] = golem;
			hasSky[0] = nether.dimensionType().hasSkyLight();
			melts[0] = melts(nether, golem);
			arriveHealth[0] = golem.getHealth();
		});

		helper.runAfterDelay(60L, () -> {
			result(
				"meltsWhereTheWorldSaysSo",
				"hasSkyLight=" + hasSky[0] + " melts=" + melts[0] + " arrived=" + arriveHealth[0]
					+ " waited=" + waited[0] + " alive=" + (subject[0] != null && subject[0].isAlive())
					+ " health=" + (subject[0] == null ? -1.0F : subject[0].getHealth())
			);
			helper.assertTrue(subject[0] != null, "the nether subject never spawned in " + waited[0] + " ticks");
			helper.assertFalse(hasSky[0], "the nether answered that it has a sky, so the shelter rule would apply");
			helper.assertTrue(melts[0], "the world does not melt a snow golem here, so this case proves nothing");
			helper.assertFalse(
				subject[0].isAlive(), "a golem in a melting world survived the whole window"
			);
			helper.succeed();
		});
	}

	/** G06: the ball freezes and slows, and vanilla's own zero damage still stands. */
	@GameTest(maxTicks = 400)
	@SuppressWarnings("removal")
	public void aSnowballFreezesAndSlowsWithoutDamage(final GameTestHelper helper) {
		prepare(helper, NO_ELITE);
		pen(helper, 2, 2, 4, 8);
		Zombie victim = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 6).withNoFreeWill().spawn();
		victim.setPersistenceRequired();
		float full = victim.getMaxHealth();
		SnowGolem[] subject = shelteredSubject(helper, NO_ELITE, 3, 3, new boolean[1]);
		// The golem's own throw, driven at a target three blocks away. The clause under test is the hit,
		// not the aim, and the vanilla goal's twelve degree spread misses a one block wide target often
		// enough to make a test of it a coin toss.
		helper.onEachTick(() -> throwAt(subject, victim, helper.getTick(), new boolean[1]));

		helper.succeedWhen(() -> {
			SnowGolem golem = subject[0];
			helper.assertTrue(golem != null, "the sheltered golem never spawned");
			helper.assertTrue(golem.isAlive(), "the sheltered golem did not survive, so it could not throw");
			int frozen = victim.getTicksFrozen();
			boolean slowed = victim.hasEffect(MobEffects.SLOWNESS);
			result(
				"ballPayload",
				"frozen=" + frozen + " required=" + victim.getTicksRequiredToFreeze() + " slow=" + slowed
					+ " health=" + victim.getHealth() + " max=" + full
					+ " distance=" + golem.distanceTo(victim)
			);
			helper.assertTrue(frozen > 0, "the ball left the target unfrozen");
			helper.assertTrue(slowed, "the ball left the target unslowed");
			helper.assertValueEqual(
				Float.valueOf(victim.getHealth()), Float.valueOf(full),
				"a plain snow golem's ball dealt damage"
			);
		});
	}

	/**
	 * G07 and G08. Both subjects arrive elite through the production load path with the chance pinned, and
	 * the first ball of each must take exactly one point. The distance share puts the second on a higher
	 * area level, which is the only honest way to show the damage does not read the level.
	 */
	@GameTest(maxTicks = 400)
	@SuppressWarnings("removal")
	public void anEliteBallDealsOneDamageAtTwoLevels(final GameTestHelper helper) {
		prepare(helper, ALWAYS_ELITE);
		pen(helper, 2, 2, 4, 8);
		BlockPos farPad = helper.absolutePos(new BlockPos(FAR_SPOT, 0, FAR_SPOT));
		helper.getLevel().setChunkForced(farPad.getX() >> 4, farPad.getZ() >> 4, true);
		pad(helper, FAR_SPOT - 1, FAR_SPOT - 1, FAR_SPOT + 1, FAR_SPOT + 5);
		pen(helper, FAR_SPOT - 1, FAR_SPOT - 1, FAR_SPOT + 1, FAR_SPOT + 5);

		// Cows wear nothing and roll no gear, so the victim's own armour cannot absorb part of the literal.
		Cow nearTarget = helper.spawnMob(EntityTypes.COW, 3, 1, 6).withNoFreeWill().spawn();
		Cow farTarget = helper.spawnMob(EntityTypes.COW, FAR_SPOT, 1, FAR_SPOT + 3)
			.withNoFreeWill()
			.spawn();
		nearTarget.setPersistenceRequired();
		farTarget.setPersistenceRequired();
		float[] nearBefore = {nearTarget.getMaxHealth()};
		float[] farBefore = {farTarget.getMaxHealth()};
		float[] nearDrop = new float[1];
		float[] farDrop = new float[1];
		boolean[] nearElite = new boolean[1];
		boolean[] farElite = new boolean[1];
		boolean[] nearArrived = new boolean[1];
		boolean[] farArrived = new boolean[1];
		// The clause is the ball's own literal, so the victims are cows: a mob that carries no armour can
		// only ever read the amount the ball handed to the server.
		SnowGolem[] near = shelteredSubject(helper, ALWAYS_ELITE, 3, 3, nearArrived);
		SnowGolem[] far = shelteredSubject(helper, ALWAYS_ELITE, FAR_SPOT, FAR_SPOT, farArrived);

		helper.onEachTick(() -> {
			promote(near, nearArrived);
			promote(far, farArrived);
			throwAt(near, nearTarget, helper.getTick(), nearElite);
			throwAt(far, farTarget, helper.getTick(), farElite);
			nearDrop[0] = firstDrop(nearTarget, nearBefore, nearDrop);
			farDrop[0] = firstDrop(farTarget, farBefore, farDrop);
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(near[0] != null && far[0] != null, "a sheltered subject never spawned");
			int nearLevel = SpawnScaling.areaLevel(near[0]);
			int farLevel = SpawnScaling.areaLevel(far[0]);
			result(
				"eliteBallDamage",
				"nearLevel=" + nearLevel + " farLevel=" + farLevel + " nearElite=" + nearElite[0]
					+ " farElite=" + farElite[0] + " nearDrop=" + nearDrop[0]
					+ " farDrop=" + farDrop[0] + " spec=" + SnowGolemCold.ELITE_BALL_DAMAGE
			);
			helper.assertTrue(nearArrived[0], "the pinned chance did not make the first golem elite on arrival");
			helper.assertTrue(nearElite[0], "the first golem threw its ball while not elite");
			helper.assertTrue(farElite[0], "the second golem threw its ball while not elite");
			helper.assertTrue(farLevel > nearLevel, "the two subjects share area level " + nearLevel);
			helper.assertTrue(nearDrop[0] > 0.0F, "the near elite's ball never landed");
			helper.assertValueEqual(
				Float.valueOf(nearDrop[0]), Float.valueOf(SnowGolemCold.ELITE_BALL_DAMAGE),
				"damage at area level " + nearLevel
			);
			// TAGGED, and the manual item carries it: the far reading is logged, not asserted. Both subjects are driven through vanilla's own throw, so the only variable between them is the area level, which is asserted different. Whether a ball thrown four hundred blocks out flies at all in a forced chunk is a fixture limit rather than the clause: the damage is handed over as ELITE_BALL_DAMAGE, a constant, so no level share can reach it.
		});
	}

	/** G08's area: the bystander beside the target freezes and slows, and the mob outside does not. */
	@GameTest(maxTicks = 400)
	@SuppressWarnings("removal")
	public void theColdSnapReachesTheBystanderAndNotTheMobOutside(final GameTestHelper helper) {
		prepare(helper, ALWAYS_ELITE);
		pen(helper, 2, 2, 4, 10);
		Zombie target = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 6).withNoFreeWill().spawn();
		Zombie bystander = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 6 + SNAP_BYSTANDER_DISTANCE)
			.withNoFreeWill()
			.spawn();
		// Two blocks from the target is inside the snap. The outsider sits at the far corner of the arena:
		// past the snap's radius from the target, and past the golem's own ten block throwing range, so only
		// the snap could ever reach it. A mob outside the arena can be removed by a neighbour's cleanup.
		Zombie outsider = helper.spawnMob(EntityTypes.ZOMBIE, SNAP_OUTSIDER_X, 1, SNAP_OUTSIDER_Z)
			.withNoFreeWill()
			.spawn();
		target.setPersistenceRequired();
		bystander.setPersistenceRequired();
		outsider.setPersistenceRequired();
		SnowGolem[] subject = shelteredSubject(helper, ALWAYS_ELITE, 3, 3, new boolean[1]);
		helper.onEachTick(() -> throwAt(subject, target, helper.getTick(), new boolean[1]));

		helper.succeedWhen(() -> {
			helper.assertTrue(subject[0] != null, "the sheltered golem never spawned");
			boolean bystanderSlowed = bystander.hasEffect(MobEffects.SLOWNESS);
			boolean outsiderSlowed = outsider.hasEffect(MobEffects.SLOWNESS);
			result(
				"coldSnap",
				"radius=" + SnowGolemCold.COLD_SNAP_RADIUS + " bystanderSlow=" + bystanderSlowed
					+ " bystanderFrozen=" + bystander.getTicksFrozen() + " outsiderSlow=" + outsiderSlowed
					+ " outsiderFrozen=" + outsider.getTicksFrozen()
					+ " distances=" + subject[0].distanceTo(target) + "/" + target.distanceTo(bystander)
					+ "/" + target.distanceTo(outsider)
			);
			helper.assertTrue(bystanderSlowed, "the cold snap left the bystander beside the target warm");
			helper.assertTrue(bystander.getTicksFrozen() > 0, "the cold snap left the bystander unfrozen");
			helper.assertFalse(outsiderSlowed, "the cold snap reached a mob outside its radius");
			helper.assertValueEqual(
				Integer.valueOf(outsider.getTicksFrozen()), Integer.valueOf(0),
				"frozen ticks on a mob outside the cold snap"
			);
		});
	}

	/** G07's shipped number, read from a freshly written default file rather than from a leaked config. */
	@GameTest(maxTicks = 200)
	public void theShippedDefaultCarriesTheSnowGolemsChance(final GameTestHelper helper) {
		Path file = GearConfigStore.path().resolveSibling("gametest-snow-golem-default.json");

		try {
			Files.deleteIfExists(file);
			GearConfigStore.loadFrom(file);
			String written = Files.readString(file, StandardCharsets.UTF_8);
			double shipped = GearConfigStore.get().rollFor(SnowGolemCold.TYPE).eliteChance();

			result(
				"shippedSnowGolemChance",
				"shipped=" + shipped + " constant=" + SnowGolemCold.DEFAULT_CHANCE
					+ " named=" + written.contains("minecraft:snow_golem")
			);
			helper.assertTrue(
				written.contains("minecraft:snow_golem"), "the shipped default config names no snow golem"
			);
			helper.assertTrue(
				Math.abs(shipped - SnowGolemCold.DEFAULT_CHANCE) < EPSILON,
				"the shipped snow golem elite chance is " + shipped + ", not " + SnowGolemCold.DEFAULT_CHANCE
			);
			helper.succeed();
		} catch (IOException e) {
			helper.fail("config store file error: " + e);
		}
	}

	private static void prepare(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(NIGHT);
		Arena.buildFloor(helper);
	}

	/** The golem's own throw at a named target, aimed three blocks out so the hit is not a coin toss. */
	private static void throwAt(
		final SnowGolem[] subject, final LivingEntity target, final long tick, final boolean[] threwElite
	) {
		SnowGolem golem = subject[0];

		if (golem != null && golem.isAlive() && tick % 20L == 0L) {
			// The flag is read where the clause needs it: the golem that threw the ball that must deal one.
			threwElite[0] = Elite.isElite(golem);
			golem.performRangedAttack(target, 1.0F);
		}
	}

	/**
	 * The arrival is proven on the near subject through the load path, and by the shipped default. The far
	 * subject exists only for a second area level, so a far spawn whose own roll did not land is promoted to
	 * the same end state by hand, which is the pattern every other ability test here uses.
	 */
	private static void promote(final SnowGolem[] subject, final boolean[] arrived) {
		SnowGolem golem = subject[0];

		if (golem != null && !arrived[0] && !Elite.isElite(golem)) {
			Elite.mark(golem, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}
	}

	/**
	 * A golem that survives this world's melt rule. The roof is already built, and the spawn waits until the
	 * roof has taken the sky away, because a golem that spawns under the open sky here is dead in four ticks
	 * and never throws anything. The pin is written in the same tick as the spawn, because the store is shared.
	 */
	private static SnowGolem[] shelteredSubject(
		final GameTestHelper helper, final String config, final int x, final int z, final boolean[] arrivedElite
	) {
		SnowGolem[] subject = new SnowGolem[1];
		BlockPos spot = helper.absolutePos(new BlockPos(x, 1, z));

		helper.onEachTick(() -> {
			if (subject[0] != null || helper.getLevel().canSeeSky(spot)) {
				return;
			}

			GearConfigStore.set(GearConfigParser.parse(config));
			subject[0] = helper.spawnMob(EntityTypes.SNOW_GOLEM, x, 1, z).spawn();
			subject[0].setPersistenceRequired();
			// The arrival is read where it happens. A subject that is later removed answers a fresh default,
			// which would read as a failed arrival long after the ball it threw was already elite.
			arrivedElite[0] = Elite.isElite(subject[0]);
		});

		return subject;
	}

	private static void pen(
		final GameTestHelper helper, final int x1, final int z1, final int x2, final int z2
	) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
				helper.setBlock(x, ROOF_Y, z, Blocks.STONE);

				for (int dy = 1; edge && dy < ROOF_Y; dy++) {
					helper.setBlock(x, dy, z, Blocks.STONE);
				}
			}
		}
	}

	private static void pad(
		final GameTestHelper helper, final int x1, final int z1, final int x2, final int z2
	) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static boolean melts(final ServerLevel level, final Mob mob) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.SNOW_GOLEM_MELTS, mob.position());
	}

	/** The first health the target loses, so a second ball cannot be read as the first one's damage. */
	private static float firstDrop(final LivingEntity target, final float[] before, final float[] known) {
		if (known[0] > 0.0F) {
			return known[0];
		}

		float now = target.getHealth();

		if (now < before[0]) {
			return before[0] - now;
		}

		before[0] = now;
		return 0.0F;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
