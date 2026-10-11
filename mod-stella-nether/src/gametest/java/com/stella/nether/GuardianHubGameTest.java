package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A nether portal sends a player to the normal nether, and the Blaze Guardian waits beside the portal they come
 * out of: placed once, on the ground, facing the portal, unharmed, unmoved, and keeping hostile spawns away.
 */
public class GuardianHubGameTest {
	static List<BlazeGuardian> guardiansNear(final ServerLevel level, final Vec3 centre, final double range) {
		return level.getEntitiesOfClass(BlazeGuardian.class, AABB.ofSize(centre, 2.0 * range, 2.0 * range, 2.0 * range));
	}

	static List<GuardianHub.Post> postsWithin(final ServerLevel level, final Vec3 centre, final double range) {
		return GuardianHub.postsIn(level).stream().filter(post -> Vec3.atBottomCenterOf(post.pos()).distanceTo(centre) <= range).toList();
	}

	/** The mean middle of every nether portal block within reach of a spot, and the distance from a point to the nearest one. */
	record Portal(Vec3 centre, double nearest, int blocks) {
	}

	static Portal portalAround(final ServerLevel level, final BlockPos around, final Vec3 from) {
		Vec3 sum = Vec3.ZERO;
		int blocks = 0;
		double nearest = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(around.offset(-6, -6, -6), around.offset(6, 6, 6))) {
			if (level.getBlockState(at).is(Blocks.NETHER_PORTAL)) {
				Vec3 middle = Vec3.atCenterOf(at);
				sum = sum.add(middle);
				blocks++;
				nearest = Math.min(nearest, middle.distanceTo(from));
			}
		}
		return new Portal(blocks == 0 ? Vec3.ZERO : sum.scale(1.0 / blocks), nearest, blocks);
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_arrival", maxTicks = 100)
	public void aPlayerWhoStepsIntoAnOverworldPortalArrivesInTheNormalNetherWithNoRaidPrompt(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = NetherTestSupport.inPortal(helper, 1.5, 1.5);
		BlockPos portal = player.blockPosition();
		TeleportTransition[] transition = new TeleportTransition[1];
		// The step the portal once answered with a prompt: the block's own hook, then the crossing it decides.
		List<GuardianWindow.Open> prompts = NetherTestSupport.capture(() -> {
			NetherTestSupport.step(helper, player);
			transition[0] = NetherTestSupport.portal(helper.getLevel(), player, portal);
			if (transition[0] != null) {
				player.teleport(transition[0]);
			}
		});
		boolean sent = transition[0] != null && transition[0].newLevel().dimension().equals(Level.NETHER);
		boolean inNether = player.level().dimension().equals(Level.NETHER);
		boolean raidOpen = RaidInstance.state(helper.getLevel().getServer(), RaidMode.NORMAL).active() || RaidInstance.state(helper.getLevel().getServer(), RaidMode.HARD).active();
		NetherTestSupport.result("portalToNether", "sent=" + sent + " inNether=" + inNether + " prompts=" + prompts.size() + " raidOpen=" + raidOpen);
		NetherTestSupport.clearPortal(helper, portal);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(sent && inNether && prompts.isEmpty() && !raidOpen,
			"the overworld portal led to " + (transition[0] == null ? null : transition[0].newLevel().dimension().identifier()) + ", arrived=" + inNether
				+ ", prompts=" + prompts.size() + ", a raid opened=" + raidOpen);
		helper.succeed();
	}

	/**
	 * The guardian a post names, once the level can see it. A guardian beside a portal can stand in the next chunk
	 * over, and a chunk the level has only just loaded shows no entity to a lookup until it is tracked; each wait
	 * loads the chunk again and gives its worker threads a moment.
	 */
	static BlazeGuardian visible(final ServerLevel nether, final GuardianHub.Post post) {
		BlazeSovereignGameTest.hold(nether, post.pos());
		if (nether.getEntity(post.id()) instanceof BlazeGuardian standing) {
			return standing;
		}
		try {
			Thread.sleep(SovereignSpecialsGameTest.WAIT_MILLIS_PER_TICK);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_beside", maxTicks = 300)
	public void aGuardianStandsBesideTheNetherPortalAndASecondArrivalAddsNone(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		ServerPlayer first = NetherTestSupport.inPortal(helper, 1.5, 1.5);
		BlockPos overworldPortal = first.blockPosition();
		NetherTestSupport.crossToNether(helper, first);
		BlockPos portal = GuardianHub.portalNear(nether, first.blockPosition());
		helper.assertTrue(portal != null, "no nether portal near the arrival at " + first.blockPosition());
		Portal shape = portalAround(nether, portal, Vec3.atCenterOf(portal));
		// The record is the count: it holds whichever chunks are loaded.
		List<GuardianHub.Post> posts = postsWithin(nether, shape.centre(), GuardianHub.SPACING);
		helper.assertTrue(posts.size() == 1, posts.size() + " guardians are posted within " + GuardianHub.SPACING + " blocks of the nether portal at " + portal);
		GuardianHub.Post post = posts.get(0);
		BlazeGuardian[] standing = new BlazeGuardian[1];
		ServerPlayer[] second = new ServerPlayer[1];
		List<GuardianHub.Post>[] postsAfter = new List[1];
		helper.startSequence()
			.thenWaitUntil(() -> {
				standing[0] = visible(nether, post);
				helper.assertTrue(standing[0] != null, "the posted guardian does not stand in the level at " + post.pos());
			})
			.thenExecute(() -> {
				BlazeGuardian guardian = standing[0];
				double apart = portalAround(nether, portal, guardian.position().add(0.0, 1.0, 0.0)).nearest();
				Vec3 toPortal = new Vec3(shape.centre().x - guardian.getX(), 0.0, shape.centre().z - guardian.getZ()).normalize();
				double facing = Vec3.directionFromRotation(0.0F, guardian.getYRot()).dot(toPortal);
				BlockPos under = guardian.blockPosition().below();
				boolean grounded = nether.getBlockState(under).isFaceSturdy(nether, under, Direction.UP) && Math.abs(guardian.getY() - guardian.blockPosition().getY()) < 1.0E-6;
				boolean clear = nether.noCollision(guardian.getBoundingBox());
				NetherTestSupport.result("guardianBeside", "posted=" + posts.size() + " apart=" + String.format("%.2f", apart) + " facing=" + String.format("%.2f", facing)
					+ " grounded=" + grounded + " clear=" + clear);
				helper.assertTrue(apart >= 2.0 && apart <= 4.0, "the guardian stands " + apart + " blocks from the nearest portal block");
				helper.assertTrue(facing > 0.8, "the guardian faces the portal by " + facing);
				helper.assertTrue(grounded && clear, "the guardian grounded=" + grounded + " clear=" + clear);
				// A second player through the same portal finds the first one's guardian.
				second[0] = NetherTestSupport.inPortal(helper, 1.5, 1.5);
				NetherTestSupport.crossToNether(helper, second[0]);
				postsAfter[0] = postsWithin(nether, shape.centre(), GuardianHub.SPACING);
				NetherTestSupport.result("guardianBesideSecond", "postedAfterSecond=" + postsAfter[0].size());
				helper.assertTrue(postsAfter[0].size() == 1 && postsAfter[0].get(0).id().equals(post.id()), "a second arrival left " + postsAfter[0].size() + " posts");
			})
			.thenWaitUntil(() -> helper.assertTrue(visible(nether, post) != null, "the guardian does not stand in the level after the second arrival"))
			.thenExecute(() -> {
				List<BlazeGuardian> afterSecond = guardiansNear(nether, shape.centre(), GuardianHub.SPACING);
				NetherTestSupport.result("guardianBesideStanding", "standingAfterSecond=" + afterSecond.size());
				NetherTestSupport.clearPortal(helper, overworldPortal);
				NetherTestSupport.leave(helper, first);
				NetherTestSupport.leave(helper, second[0]);
				helper.assertTrue(afterSecond.size() == 1 && afterSecond.get(0).getUUID().equals(post.id()), "a second arrival left " + afterSecond.size() + " guardians standing");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_look", maxTicks = 100)
	public void theGuardianIsAGreenGlowingBlazeAtOnePointFourTimesTheSize(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		double scale = guardian.getAttributeValue(Attributes.SCALE);
		double height = guardian.getBbHeight();
		double expected = EntityTypes.BLAZE.getHeight() * BlazeGuardian.SCALE;
		NetherTestSupport.result("guardianLook", "scale=" + scale + " height=" + height + " expected=" + expected + " glowing=" + guardian.isCurrentlyGlowing()
			+ " colour=" + Integer.toHexString(guardian.getTeamColor()));
		helper.assertTrue(Math.abs(scale - 1.4) < 1.0E-9 && BlazeGuardian.SCALE == 1.4 && Math.abs(height - expected) < 0.01,
			"the guardian is scaled " + scale + " and stands " + height + " of " + expected);
		helper.assertTrue(guardian.isCurrentlyGlowing() && guardian.getTeamColor() == BlazeGuardian.GREEN && guardian.getTeamColor() != 0xFFFFFF,
			"the guardian glows=" + guardian.isCurrentlyGlowing() + " in " + Integer.toHexString(guardian.getTeamColor()));
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_unharmed", maxTicks = 100)
	public void theGuardianTakesNoDamageFromAnySourceButTheKillCommand(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer creative = NetherTestSupport.atGuardian(helper, guardian);
		creative.setGameMode(GameType.CREATIVE);
		Zombie zombie = EntityTypes.ZOMBIE.create(nether, EntitySpawnReason.MOB_SUMMONED);
		DamageSource[] sources = {
			nether.damageSources().generic(), nether.damageSources().mobAttack(zombie), nether.damageSources().inFire(), nether.damageSources().lava(),
			nether.damageSources().magic(), nether.damageSources().explosion(null, null), nether.damageSources().fall(), nether.damageSources().drown(),
			nether.damageSources().playerAttack(creative)
		};
		float before = guardian.getHealth();
		int hurt = 0;
		for (DamageSource source : sources) {
			hurt += guardian.hurtServer(nether, source, 1000.0F) ? 1 : 0;
		}
		boolean unharmed = guardian.getHealth() == before && guardian.isAlive() && hurt == 0;
		// The one exception, so the guard is shown to tell damage from a kill: the kill command removes it, and its post.
		BlockPos post = guardian.blockPosition();
		guardian.kill(nether);
		boolean killed = !guardian.isAlive();
		boolean forgotten = GuardianHub.nearest(nether, Vec3.atBottomCenterOf(post), 1.0) == null;
		NetherTestSupport.result("guardianUnharmed", "sources=" + sources.length + " hurt=" + hurt + " health=" + guardian.getHealth() + "/" + before + " killedByCommand=" + killed + " postForgotten=" + forgotten);
		NetherTestSupport.leave(helper, creative);
		helper.assertTrue(unharmed, hurt + " of " + sources.length + " damage sources hurt the guardian, health " + guardian.getHealth() + " of " + before);
		helper.assertTrue(killed && forgotten, "the kill command left the guardian alive=" + !killed + ", post forgotten=" + forgotten);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_still", maxTicks = 100)
	public void theGuardianDoesNotMoveWhateverPushesIt(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		Vec3 start = guardian.position();
		guardian.setDeltaMovement(0.6, 0.6, 0.6);
		guardian.push(0.5, 0.5, 0.5);
		guardian.move(MoverType.PISTON, new Vec3(2.0, 0.0, 0.0));
		// Ticked by hand, as the level does: the count first, then the tick.
		int ticks = guardian.tickCount;
		for (int i = 0; i < 40; i++) {
			guardian.tickCount++;
			guardian.tick();
		}
		double drift = guardian.position().distanceTo(start);
		NetherTestSupport.result("guardianStill", "ticked=" + (guardian.tickCount - ticks) + " drift=" + drift + " pushable=" + guardian.isPushable());
		helper.assertTrue(drift < 1.0E-9 && !guardian.isPushable(), "the guardian drifted " + drift + " blocks");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:guardian_quiet", maxTicks = 100)
	public void noHostileMobSpawnsNaturallyWithinSixteenBlocksOfAGuardian(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		BlockPos at = guardian.blockPosition();
		RandomSource random = RandomSource.create(7L);
		int range = (int) GuardianHub.NO_SPAWN_RANGE;
		StringBuilder wrong = new StringBuilder();
		// The reasons a world fills itself by are refused near it; a summon or a spawner is not a natural spawn.
		for (EntitySpawnReason reason : new EntitySpawnReason[] {EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.PATROL, EntitySpawnReason.REINFORCEMENT}) {
			for (var type : List.of(EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.WITHER_SKELETON, EntityTypes.BLAZE)) {
				boolean nearAllowed = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, nether, reason, at.offset(range - 2, 0, 0), random);
				boolean nearBelow = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, nether, reason, at.offset(0, -2, range - 3), random);
				boolean farAllowed = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, nether, reason, at.offset(range + 6, 0, 0), random);
				if (nearAllowed || nearBelow) {
					wrong.append(' ').append(reason).append('/').append(type.getDescriptionId()).append(" spawned near");
				}
				if (!farAllowed) {
					wrong.append(' ').append(reason).append('/').append(type.getDescriptionId()).append(" refused far");
				}
			}
		}
		boolean summonNear = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIFIED_PIGLIN, nether, EntitySpawnReason.SPAWNER, at.offset(range - 2, 0, 0), random);
		NetherTestSupport.result("guardianQuiet", "range=" + range + " problems=[" + wrong + " ] spawnerNear=" + summonNear + " difficulty=" + nether.getDifficulty());
		helper.assertTrue(wrong.isEmpty(), "spawn rules near a guardian:" + wrong);
		helper.assertTrue(summonNear, "a spawner's spawn was refused near the guardian too");
		helper.succeed();
	}
}
