package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chainstorm calls lightning from the sky onto the hostiles around the aimed spot, one strike after
 * another; Riftwalk teleports its caster onto the spot it aims at, and refuses a spot with no room.
 */
public class StormAndRiftGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.stormrift");
	private static final Identifier CHAINSTORM = Identifier.parse("mod-stella-spells:chainstorm");
	private static final Identifier RIFTWALK = Identifier.parse("mod-stella-spells:riftwalk");
	private static final double LAND_TOLERANCE = 0.75;
	private static final Map<UUID, List<int[]>> STRIKES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<UUID>> STRUCK = new ConcurrentHashMap<>();

	static {
		SpellStorms.setRecorder((caster, target, tick) -> {
			STRIKES.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new int[] {tick});
			STRUCK.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(target);
		});
	}

	@GameTest(maxTicks = 100)
	public void chainstormStrikesEveryHostileAroundTheSpotInSequence(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		List<Mob> inside = List.of(zombie(helper, 3, 8), zombie(helper, 1, 8), zombie(helper, 2, 10));
		Mob outside = zombie(helper, 2, 8 + (int) SpellStorms.RADIUS + 3);
		Cow cow = helper.spawnMob(EntityTypes.COW, 3, 1, 9).withNoFreeWill().spawn();
		float cowBefore = cow.getHealth();
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 8))));
		InteractionResult result = cast(helper, caster, CHAINSTORM);
		helper.assertTrue(result == InteractionResult.SUCCESS, "Chainstorm did not cast at the spot: " + result);

		helper.succeedWhen(() -> {
			List<UUID> struck = STRUCK.getOrDefault(caster.getUUID(), List.of());
			helper.assertTrue(struck.size() >= inside.size(), "only " + struck.size() + " strikes so far");
			for (Mob mob : inside) {
				helper.assertTrue(struck.contains(mob.getUUID()) && mob.getHealth() == mob.getMaxHealth() - SpellStorms.STRIKE_DAMAGE, "a hostile in the area took " + (mob.getMaxHealth() - mob.getHealth()));
			}
			helper.assertTrue(!struck.contains(outside.getUUID()) && outside.getHealth() == outside.getMaxHealth(), "the storm struck outside its radius");
			helper.assertTrue(cow.getHealth() == cowBefore && !struck.contains(cow.getUUID()), "the storm struck a cow");
			List<int[]> ticks = STRIKES.get(caster.getUUID());
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < ticks.size(); i++) {
				gaps.add(ticks.get(i)[0] - ticks.get(i - 1)[0]);
			}
			helper.assertTrue(gaps.stream().allMatch(gap -> gap == SpellStorms.STRIKE_INTERVAL_TICKS), "the strikes fell " + gaps + " ticks apart, not in sequence");
			LOG.info("[SPELLTEST] test=chainstormStrikesEveryHostileAroundTheSpotInSequence RESULT strikes={} gaps={}", struck.size(), gaps);
		});
	}

	@GameTest(maxTicks = 100)
	public void chainstormStrikesNoMoreThanItsCap(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		for (int i = 0; i < SpellStorms.MAX_STRIKES + 3; i++) {
			zombie(helper, 1 + i % 3, 7 + i / 3);
		}
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 8))));
		helper.assertTrue(cast(helper, caster, CHAINSTORM) == InteractionResult.SUCCESS, "Chainstorm did not cast");
		helper.runAfterDelay((SpellStorms.MAX_STRIKES + 3L) * SpellStorms.STRIKE_INTERVAL_TICKS, () -> {
			int strikes = STRUCK.getOrDefault(caster.getUUID(), List.of()).size();
			helper.assertTrue(strikes == SpellStorms.MAX_STRIKES, "the storm struck " + strikes + " times, not " + SpellStorms.MAX_STRIKES);
			LOG.info("[SPELLTEST] test=chainstormStrikesNoMoreThanItsCap RESULT strikes={}", strikes);
			helper.succeed();
		});
	}

	@GameTest
	public void chainstormWithNoHostileAroundTheSpotDoesNotCast(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 8))));
		InteractionResult result = cast(helper, caster, CHAINSTORM);
		helper.assertTrue(result != InteractionResult.SUCCESS && !caster.getCooldowns().isOnCooldown(caster.getMainHandItem()), "an empty storm cast: " + result);
		LOG.info("[SPELLTEST] test=chainstormWithNoHostileAroundTheSpotDoesNotCast RESULT result={}", result);
		helper.succeed();
	}

	@GameTest
	public void riftwalkLandsOnTheSpotYouAimAt(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1.0, 10.5));
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, spot);
		helper.assertTrue(cast(helper, caster, RIFTWALK) == InteractionResult.SUCCESS, "Riftwalk did not cast");
		helper.assertTrue(caster.position().distanceTo(spot) <= LAND_TOLERANCE, "Riftwalk landed at " + caster.position() + ", not on " + spot);
		LOG.info("[SPELLTEST] test=riftwalkLandsOnTheSpotYouAimAt RESULT landed={} aimed={}", caster.position(), spot);
		helper.succeed();
	}

	@GameTest
	public void riftwalkAimedAtAWallLandsInFrontOfIt(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		for (int x = 0; x <= 5; x++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(x, y, 9, Blocks.STONE);
			}
		}
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 2.5, 9.0)));
		helper.assertTrue(cast(helper, caster, RIFTWALK) == InteractionResult.SUCCESS, "Riftwalk did not cast at the wall");
		Vec3 local = helper.relativeVec(caster.position());
		helper.assertTrue(local.z > 7.0 && local.z < 9.0 && Math.abs(local.y - 1.0) < 0.01, "Riftwalk landed at " + local + ", not on the floor in front of the wall");
		LOG.info("[SPELLTEST] test=riftwalkAimedAtAWallLandsInFrontOfIt RESULT landed={}", local);
		helper.succeed();
	}

	@GameTest
	public void riftwalkLandsOnTopOfASlab(final GameTestHelper helper) {
		assertLandsOnAPartialFloor(helper, "riftwalkLandsOnTopOfASlab", Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 0.5);
	}

	@GameTest
	public void riftwalkLandsOnACarpet(final GameTestHelper helper) {
		assertLandsOnAPartialFloor(helper, "riftwalkLandsOnACarpet", Blocks.CARPET.white().defaultBlockState(), 1.0 / 16.0);
	}

	@GameTest(maxTicks = 100)
	public void chainstormLeavesCopperAndLightningRodsAlone(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		helper.setBlock(3, 0, 8, Blocks.COPPER_BLOCK.weathering().oxidized());
		helper.setBlock(1, 0, 8, Blocks.LIGHTNING_ROD.weathering().unaffected());
		Mob onCopper = zombie(helper, 3, 8);
		Mob onRod = helper.spawnMob(EntityTypes.ZOMBIE, 1, 2, 8).withNoFreeWill().spawn();
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 8))));
		helper.assertTrue(cast(helper, caster, CHAINSTORM) == InteractionResult.SUCCESS, "Chainstorm did not cast");
		helper.succeedWhen(() -> {
			List<UUID> struck = STRUCK.getOrDefault(caster.getUUID(), List.of());
			helper.assertTrue(struck.contains(onCopper.getUUID()) && struck.contains(onRod.getUUID()), "the storm has not struck both yet");
			// Only this storm's bolts count: a neighbouring test's bolt a few blocks away is not this storm's.
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, onCopper.getBoundingBox().inflate(1.5)).isEmpty()
				&& helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, onRod.getBoundingBox().inflate(1.5)).isEmpty(), "a bolt is still falling on a struck mob");
			helper.assertTrue(helper.getBlockState(new net.minecraft.core.BlockPos(3, 0, 8)).is(Blocks.COPPER_BLOCK.weathering().oxidized()), "a storm bolt scraped the copper to " + helper.getBlockState(new net.minecraft.core.BlockPos(3, 0, 8)));
			helper.assertFalse(helper.getBlockState(new net.minecraft.core.BlockPos(1, 0, 8)).getValue(net.minecraft.world.level.block.LightningRodBlock.POWERED), "a storm bolt powered the lightning rod");
			LOG.info("[SPELLTEST] test=chainstormLeavesCopperAndLightningRodsAlone RESULT copper=oxidized rod=unpowered");
		});
	}

	// Its own batch, because it drops every pending strike on the server.
	@GameTest(environment = "mod-stella-spells:storm_alone", maxTicks = 100)
	public void aStormDropsItsPendingStrikesWhenTheServerStops(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		zombie(helper, 3, 8);
		zombie(helper, 1, 8);
		zombie(helper, 2, 10);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 8))));
		helper.assertTrue(cast(helper, caster, CHAINSTORM) == InteractionResult.SUCCESS, "Chainstorm did not cast");
		int pendingBefore = SpellStorms.pendingCount();
		SpellStorms.onServerStopping(helper.getLevel().getServer());
		int pendingAfter = SpellStorms.pendingCount();
		helper.runAfterDelay(3L * SpellStorms.STRIKE_INTERVAL_TICKS, () -> {
			int strikes = STRUCK.getOrDefault(caster.getUUID(), List.of()).size();
			LOG.info("[SPELLTEST] test=aStormDropsItsPendingStrikesWhenTheServerStops RESULT pendingBefore={} pendingAfter={} strikes={}", pendingBefore, pendingAfter, strikes);
			helper.assertTrue(pendingBefore == 2, "the storm queued " + pendingBefore + " strikes, so the case proves nothing");
			helper.assertTrue(pendingAfter == 0 && strikes == 1, "strikes outlived the server: pending=" + pendingAfter + " strikes=" + strikes);
			helper.succeed();
		});
	}

	@GameTest
	public void riftwalkRefusesASpotWithNoRoom(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 2, 10, Blocks.STONE);
		Vec3 start = caster.position();
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 1.0, 10.5)));
		InteractionResult result = cast(helper, caster, RIFTWALK);
		helper.assertTrue(result != InteractionResult.SUCCESS && caster.position().equals(start), "Riftwalk landed under a ceiling: " + helper.relativeVec(caster.position()));
		LOG.info("[SPELLTEST] test=riftwalkRefusesASpotWithNoRoom RESULT result={}", result);
		helper.succeed();
	}

	@GameTest
	public void riftwalkRefusesWater(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(2, 1, 10, Blocks.WATER);
		Vec3 start = caster.position();
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 1.0, 10.5)));
		InteractionResult result = cast(helper, caster, RIFTWALK);
		helper.assertTrue(result != InteractionResult.SUCCESS && caster.position().equals(start), "Riftwalk landed in water");
		LOG.info("[SPELLTEST] test=riftwalkRefusesWater RESULT result={}", result);
		helper.succeed();
	}

	@GameTest
	public void riftwalkRefusesASpotAMobStandsOn(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		Mob zombie = zombie(helper, 2, 10);
		Vec3 start = caster.position();
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 1.0, 10.5)));
		InteractionResult result = cast(helper, caster, RIFTWALK);
		helper.assertTrue(result != InteractionResult.SUCCESS && caster.position().equals(start), "Riftwalk landed inside " + zombie + " at " + helper.relativeVec(caster.position()));
		LOG.info("[SPELLTEST] test=riftwalkRefusesASpotAMobStandsOn RESULT result={}", result);
		helper.succeed();
	}

	@GameTest
	public void riftwalkAimedAtTheSkyGoesNowhere(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		Vec3 start = caster.position();
		caster.setXRot(-90.0f);
		Vec3 eye = caster.getEyePosition();
		// The test's barrier ceiling is not sky, so open the column above the caster.
		for (int up = 0; up <= SpellModifiers.BASE_TARGET_RANGE_BLOCKS + 1; up++) {
			helper.getLevel().setBlock(net.minecraft.core.BlockPos.containing(eye).above(up), Blocks.AIR.defaultBlockState(), 3);
		}
		var sky = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(0.0, SpellModifiers.BASE_TARGET_RANGE_BLOCKS, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
		helper.assertTrue(sky.getType() == net.minecraft.world.phys.HitResult.Type.MISS, "the sky above the caster is not open: " + sky.getBlockPos() + " " + helper.getLevel().getBlockState(sky.getBlockPos()));
		InteractionResult result = cast(helper, caster, RIFTWALK);
		helper.assertTrue(result != InteractionResult.SUCCESS && caster.position().equals(start) && !caster.getCooldowns().isOnCooldown(caster.getMainHandItem()), "Riftwalk went somewhere from the sky: " + result);
		LOG.info("[SPELLTEST] test=riftwalkAimedAtTheSkyGoesNowhere RESULT result={}", result);
		helper.succeed();
	}

	private static void assertLandsOnAPartialFloor(final GameTestHelper helper, final String test, final net.minecraft.world.level.block.state.BlockState floor, final double top) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper);
		floor(helper);
		helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 10), floor);
		Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1.0 + top, 10.5));
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, spot);
		InteractionResult result = cast(helper, caster, RIFTWALK);
		Vec3 local = helper.relativeVec(caster.position());
		LOG.info("[SPELLTEST] test={} RESULT result={} landed={}", test, result, local);
		helper.assertTrue(result == InteractionResult.SUCCESS, "Riftwalk refused the floor: " + result);
		helper.assertTrue(Math.abs(local.y - (1.0 + top)) < 0.01 && caster.position().distanceTo(spot) <= LAND_TOLERANCE, "Riftwalk landed at " + local + ", not on the floor at " + (1.0 + top));
		helper.succeed();
	}

	private static InteractionResult cast(final GameTestHelper helper, final ServerPlayer caster, final Identifier id) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(id);
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		return book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 16; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	// A durable zombie with no armour, so a strike reads as exactly its damage.
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}
}
