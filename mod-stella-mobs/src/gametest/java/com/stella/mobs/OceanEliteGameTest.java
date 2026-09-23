package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteAuraGoal;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.behavior.Harpooner;
import com.stella.mobs.behavior.OceanSpecials;
import com.stella.mobs.behavior.PufferfishMineGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * P2 to P4 and P6: the ocean elites that are not Monsters. A glow squid's ink veil blinds inside 16
 * blocks, a pufferfish's living mine stings inside 3, and an elite drowned's trident pulls what it
 * hits toward the thrower. The guardian's tether lives in the guardian test class.
 */
public class OceanEliteGameTest {
	private static final String NO_ROLL = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";
	private static final float VICTIM_HEALTH = 1000.0F;
	private static final double EPSILON = 1.0e-6;

	/** P3: the elite glow squid's ink veil is the 16 block blindness aura, and a plain one is dark to none. */
	@GameTest(maxTicks = 200)
	public void inkVeilBlindsNearbyPlayers(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildPool(helper, 4, 4);
			GlowSquid squid = helper.spawnMob(EntityTypes.GLOW_SQUID, 4, 3, 4).spawn();
			squid.setPersistenceRequired();
			// The goal goes on before the mark, so the plain phase reads the goal's own empty record.
			OceanSpecials.install(squid);
			ServerPlayer near = target(helper, 4, 3, 16);
			ServerPlayer far = target(helper, 4, 3, 28);
			pin(helper, squid, 4.5, 3.0, 4.5);

			helper.runAfterDelay(60L, () -> {
				EliteAuraGoal<?> plainAura = auraOf(squid);
				helper.assertTrue(plainAura != null, "the glow squid carries no aura goal");
				helper.assertTrue(plainAura.affected().isEmpty(), "a plain glow squid blinded a player");
				Elite.mark(squid, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			});

			helper.succeedWhen(() -> {
				EliteAuraGoal<?> aura = auraOf(squid);
				helper.assertTrue(Elite.isElite(squid), "the glow squid was not marked elite");
				helper.assertTrue(aura != null, "the elite glow squid carries no aura goal");
				helper.assertTrue(
					aura != null && aura.affected().contains(near.getUUID()),
					"the ink veil never applied to the player inside " + EliteAuraGoal.RADIUS + " blocks"
				);
				helper.assertTrue(
					aura == null || !aura.affected().contains(far.getUUID()),
					"the ink veil applied to the player outside " + EliteAuraGoal.RADIUS + " blocks"
				);
				helper.assertTrue(near.hasEffect(MobEffects.BLINDNESS), "the player inside the veil carries no blindness");
				result(
					"oceanInkVeil",
					"radius=" + EliteAuraGoal.RADIUS + " nearDistance=12 farDistance=24 nearInRecord=true farInRecord=false"
				);
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** P4: the living mine stings inside its widened reach and leaves a target outside it alone. */
	@GameTest(maxTicks = 200)
	public void livingMinePoisonsInsideItsReach(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildFloor(helper);
			Pufferfish puffer = helper.spawnMob(EntityTypes.PUFFERFISH, 3, 3, 3).spawn();
			puffer.setPersistenceRequired();
			Elite.mark(puffer, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			OceanSpecials.install(puffer);
			// Two blocks away puffs the fish and sits inside the mine; eight blocks is outside both.
			// Cows, because a zombie is undead and undead cannot take Poison at all.
			Cow inside = cowVictim(helper, 3, 5);
			Cow outside = cowVictim(helper, 3, 11);
			pin(helper, puffer, 3.5, 3.0, 3.5);
			pin(helper, inside, 3.5, 3.0, 5.5);
			pin(helper, outside, 3.5, 3.0, 11.5);
			int[] sampled = new int[1];
			helper.onEachTick(() -> {
				sampled[0]++;

				if (sampled[0] % 40 == 0) {
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test=mineProbe t={} puff={} insideHealth={} insidePoison={} insideDist={}"
							+ " outsidePoison={} outsideDist={}",
						sampled[0], puffer.getPuffState(), inside.getHealth(),
						inside.hasEffect(MobEffects.POISON), puffer.distanceTo(inside),
						outside.hasEffect(MobEffects.POISON), puffer.distanceTo(outside)
					);
				}
			});

			helper.succeedWhen(() -> {
				MobEffectInstance poison = inside.getEffect(MobEffects.POISON);
				helper.assertTrue(Elite.isElite(puffer), "the pufferfish was not marked elite");
				helper.assertTrue(
					poison != null, "the living mine never poisoned the target inside " + PufferfishMineGoal.MINE_RADIUS
				);
				helper.assertTrue(poison != null && poison.getAmplifier() == 0, "the mine's poison is not level one");
				helper.assertFalse(
					outside.hasEffect(MobEffects.POISON), "the mine reached the target outside "
						+ PufferfishMineGoal.MINE_RADIUS + " blocks"
				);
				result(
					"oceanLivingMine",
					"radius=" + PufferfishMineGoal.MINE_RADIUS + " puff=" + puffer.getPuffState() + " poisonTicks="
						+ (poison == null ? 0 : poison.getDuration()) + " insideDistance=2.0 outsideDistance=8.0"
				);
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/**
	 * P2: an elite drowned's trident pulls the victim it hits toward the thrower. The two hits are
	 * driven through the shipped trident damage source and the velocities are read in the hit's own
	 * tick, so nothing else can move the victims; the plain drowned beside it is the control.
	 */
	@GameTest(maxTicks = 200)
	public void harpoonerPullsWhatTheTridentHits(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildFloor(helper);
			Drowned elite = spawnDrowned(helper, 2, 2, true);
			Zombie eliteVictim = victim(helper, 2, 7);
			Drowned plain = spawnDrowned(helper, 6, 2, false);
			Zombie plainVictim = victim(helper, 6, 7);
			double[] gain = new double[2];

			helper.runAfterDelay(20L, () -> {
				gain[0] = driveTridentHit(helper, elite, eliteVictim);
				gain[1] = driveTridentHit(helper, plain, plainVictim);
			});

			helper.runAfterDelay(60L, () -> {
				helper.assertTrue(Elite.isElite(elite), "the subject drowned is not elite");
				helper.assertFalse(Elite.isElite(plain), "the control drowned is elite");
				helper.assertTrue(
					gain[0] >= Harpooner.PULL_POWER * 0.5,
					"the elite trident added only " + gain[0] + " of the " + Harpooner.PULL_POWER + " pull"
				);
				helper.assertTrue(
					gain[1] < Harpooner.PULL_POWER * 0.5,
					"the plain trident added " + gain[1] + ", near the " + Harpooner.PULL_POWER + " pull"
				);
				helper.assertTrue(
					gain[0] >= gain[1] + Harpooner.PULL_POWER * 0.5,
					"the elite's pull did not beat the plain trident: " + gain[0] + " against " + gain[1]
				);
				result(
					"oceanHarpoonerPull",
					"eliteGain=" + gain[0] + " plainGain=" + gain[1] + " power=" + Harpooner.PULL_POWER
				);
				helper.succeed();
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** One real trident damage source owned by the drowned, and the velocity it adds toward the thrower. */
	private static double driveTridentHit(final GameTestHelper helper, final Drowned drowned, final Zombie victim) {
		ServerLevel level = helper.getLevel();
		ThrownTrident trident = new ThrownTrident(level, drowned, new ItemStack(Items.TRIDENT));
		trident.setPos(victim.getX(), victim.getY() + 1.0, victim.getZ());
		level.addFreshEntity(trident);
		double before = victim.getDeltaMovement().z;
		victim.hurtServer(level, level.damageSources().trident(trident, drowned), 8.0F);
		return before - victim.getDeltaMovement().z;
	}

	/** A drowned with a trident in hand, elite when asked. The trident is what the ranged goal gates on. */
	private static Drowned spawnDrowned(final GameTestHelper helper, final int x, final int z, final boolean elite) {
		Drowned drowned = helper.spawnMob(EntityTypes.DROWNED, x, 3, z).spawn();
		drowned.setPersistenceRequired();
		drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		pin(helper, drowned, x + 0.5, 3.0, z + 0.5);

		if (elite) {
			Elite.mark(drowned, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}

		return drowned;
	}

	/** A durable victim with no AI and no knockback, so only the pull's own push moves it. */
	private static Zombie victim(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, 3, z).withNoFreeWill().spawn();
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
		zombie.setHealth(VICTIM_HEALTH);
		zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return zombie;
	}

	/** The aura this mob carries: the glow squid's ink veil, so a clause reads the record the goal wrote. */
	private static EliteAuraGoal<?> auraOf(final Mob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof EliteAuraGoal<?> aura) {
				return aura;
			}
		}

		return null;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer target(final GameTestHelper helper, final int x, final int y, final int z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
		helper.onEachTick(() -> player.setPos(spot.x, spot.y, spot.z));
		return player;
	}

	private static void pin(final GameTestHelper helper, final Mob mob, final double x, final double y, final double z) {
		Vec3 spot = helper.absoluteVec(new Vec3(x, y, z));
		helper.onEachTick(() -> mob.setPos(spot.x, spot.y, spot.z));
	}

	/** A durable, non-undead victim, so the mine's poison can land and be read. */
	private static Cow cowVictim(final GameTestHelper helper, final int x, final int z) {
		Cow cow = helper.spawnMob(EntityTypes.COW, x, 3, z).withNoFreeWill().spawn();
		cow.setPersistenceRequired();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
		cow.setHealth(VICTIM_HEALTH);
		return cow;
	}

	/** A small water pocket: a glow squid on dry land suffocates before its aura can run. */
	private static void buildPool(final GameTestHelper helper, final int centreX, final int centreZ) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 0; y <= 5; y++) {
					helper.setBlock(centreX + dx, y, centreZ + dz, y == 0 ? Blocks.STONE : Blocks.WATER);
				}
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

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
