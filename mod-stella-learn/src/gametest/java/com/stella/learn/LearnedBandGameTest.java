package com.stella.learn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RoleSpec;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** An archer keeps farther from a player who rushes, and does not change for one who does not. */
public class LearnedBandGameTest {
	private static final int MAX_TICKS = 200;
	private static final double BASE_NEAR = 5.0;
	private static final double BASE_FAR = 15.0;
	/** A far band that no archer is built with now. */
	private static final double STALE_FAR = 10.0;
	// The game test clock moves a few dozen ticks during a test, which decays a fresh count by about half a percent.
	private static final double TOLERANCE = 0.1;
	private static final String CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": %s, "far": %s } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""".formatted(BASE_NEAR, BASE_FAR);
	/**
	 * A bow and no `roles` entry.
	 * The archer's band is then whatever `RoleSpec` gives.
	 */
	private static final String CURRENT_CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 },
			"riders": { "chance": 0.0 }
		}""";

	private static Mob archer(final GameTestHelper helper, final ServerPlayer player) {
		return archer(helper, player, 2, 2);
	}

	private static Mob archer(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, x, LearnArena.SPAWN_Y, z).spawn();
		LearnArena.keepTargeting(helper, skeleton, player);
		return skeleton;
	}

	/**
	 * An archer tagged out of the mobs mod's level scaling, which the far test arena would set in the thousands.
	 * A scaled archer's arrows kill the player. It then loses its target and strolls away.
	 * The tag skips the gear roll, so the bow is set here, and the tag goes on before the archer joins the level.
	 */
	private static Mob unscaledArcher(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		Mob skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		skeleton.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		skeleton.setPersistenceRequired();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(x, LearnArena.SPAWN_Y, z)));
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(skeleton);
		LearnArena.keepTargeting(helper, skeleton, player);
		return skeleton;
	}

	private static void rush(final ServerPlayer player, final double hits) {
		for (int i = 0; i < hits; i++) {
			Profiles.record(player, Habit.MELEE);
		}
	}

	/**
	 * The mob as an older build saved it.
	 * That build wrote its learned base under this id and in this shape.
	 * The entry is written by hand, so the case ignores what this build persists.
	 */
	private static CompoundTag savedByAnOlderBuild(final GameTestHelper helper, final Mob mob) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		mob.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		CompoundTag base = new CompoundTag();
		base.putDouble("near", BASE_NEAR);
		base.putDouble("far", STALE_FAR);
		CompoundTag attachments = saved.getCompoundOrEmpty("fabric:attachments");
		attachments.put("mod-stella-learn:band_base", base);
		saved.put("fabric:attachments", attachments);
		return saved;
	}

	/** Reads a saved skeleton, as a chunk load does. */
	private static Mob load(final GameTestHelper helper, final CompoundTag saved) {
		Mob mob = EntityTypes.SKELETON.create(helper.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
		mob.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aFullRusherPushesTheNearBandOutByTheGain(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			ReactiveState state = ReactiveState.get(skeleton);
			double want = BASE_NEAR + LearnConfigStore.get().rushBandGain();
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=aFullRusherPushesTheNearBandOutByTheGain RESULT near={} far={} want={}", state.getNearDistance(), state.getFarDistance(), want
			);
			helper.assertTrue(Math.abs(state.getNearDistance() - want) < TOLERANCE, "near band was " + state.getNearDistance());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void theBandKeepsItsWidthWhenItMoves(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			ReactiveState state = ReactiveState.get(skeleton);
			double width = state.getFarDistance() - state.getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theBandKeepsItsWidthWhenItMoves RESULT width={} base={}", width, BASE_FAR - BASE_NEAR);
			helper.assertTrue(Math.abs(width - (BASE_FAR - BASE_NEAR)) < TOLERANCE, "band width was " + width);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void halfARusherPushesHalfAsFar(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation() / 2.0);
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(skeleton).getNearDistance();
			double want = BASE_NEAR + LearnConfigStore.get().rushBandGain() / 2.0;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=halfARusherPushesHalfAsFar RESULT near={} want={}", near, want);
			helper.assertTrue(Math.abs(near - want) < TOLERANCE, "near band was " + near);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aPlayerWhoNeverRushesLeavesTheBandAlone(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		Profiles.record(player, Habit.RANGED);
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(skeleton).getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aPlayerWhoNeverRushesLeavesTheBandAlone RESULT near={}", near);
			helper.assertTrue(Math.abs(near - BASE_NEAR) < TOLERANCE, "near band was " + near);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aForgottenRushReturnsTheBand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(30L, () -> {
			double wide = ReactiveState.get(skeleton).getNearDistance();
			// Ten half lives leave under a thousandth of the count.
			long halfLife = LearnConfigStore.get().profileHalfLifeTicks();
			Profiles.set(player, PlayerProfile.EMPTY.record(
				Habit.MELEE, LearnConfigStore.get().rushSaturation(), LearnClock.now() - halfLife * 10L, halfLife
			));
			helper.runAfterDelay(30L, () -> {
				double near = ReactiveState.get(skeleton).getNearDistance();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aForgottenRushReturnsTheBand RESULT wide={} after={}", wide, near);
				helper.assertTrue(wide > BASE_NEAR + 1.0, "the rush never widened the band, so the case proves nothing");
				helper.assertTrue(near - BASE_NEAR < 0.01, "near band stayed at " + near);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aMeleeMobIsNotGivenABand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, LearnArena.SPAWN_Y, 2).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(zombie).getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMeleeMobIsNotGivenABand RESULT near={}", near);
			helper.assertTrue(near == 0.0, "a zombie was given a near band of " + near);
			helper.succeed();
		});
	}

	/** The mobs mod picks each flee spot at random and rests 40 ticks between tries, so the test waits for one that works. */
	@GameTest(maxTicks = 400, padding = LearnArena.PADDING)
	public void anArcherBacksOffFromARusher(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// Eight blocks: inside the widened near band of 11, outside the plain one of 5, with room to back off.
		ServerPlayer rusher = LearnArena.player(helper, 32.5, 24.5);
		rush(rusher, LearnConfigStore.get().rushSaturation());
		Mob skeleton = unscaledArcher(helper, rusher, 24, 24);
		helper.succeedWhen(() -> {
			double distance = skeleton.distanceTo(rusher);
			if (distance > 10.5) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherBacksOffFromARusher RESULT distance={}", distance);
			}

			helper.assertTrue(distance > 10.5, "the archer is only " + distance + " from a rusher");
		});
	}

	@GameTest(maxTicks = 400, padding = LearnArena.PADDING)
	public void anArcherStaysPutForAPlayerWhoNeverRushes(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer shooter = LearnArena.player(helper, 32.5, 24.5);
		Profiles.record(shooter, Habit.RANGED);
		Mob skeleton = unscaledArcher(helper, shooter, 24, 24);
		double[] farthest = {0.0};
		helper.onEachTick(() -> farthest[0] = Math.max(farthest[0], skeleton.distanceTo(shooter)));
		// The same window the rusher case is given, so a retreat that only needs time would show here too.
		helper.runAfterDelay(300L, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherStaysPutForAPlayerWhoNeverRushes RESULT farthest={}", farthest[0]);
			helper.assertTrue(farthest[0] < 9.0, "the archer backed off to " + farthest[0] + " from a player who never rushed");
			helper.succeed();
		});
	}

	/**
	 * A save from an older build holds a learned base that no archer is built with now. The mobs mod rebuilds the band
	 * of every mob it loads, so the mob must start from that band and not from the saved one, whether it stands idle or
	 * fights a player. Then a change to the band reaches the archers already saved.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void reloadTakesCurrentBand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// Past the follow range: no target of its own.
		ServerPlayer player = LearnArena.player(helper, 44.5, 44.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(CURRENT_CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		RoleSpec current = RoleSpec.of(CombatRole.RANGED);
		helper.assertTrue(current.farDistance() - STALE_FAR > 1.0, "the far band of today is " + current.farDistance() + ", too near the stale " + STALE_FAR + " to tell them apart");
		Mob saved = helper.spawnMob(EntityTypes.SKELETON, 2, LearnArena.SPAWN_Y, 2).spawn();
		CompoundTag tag = savedByAnOlderBuild(helper, saved);
		// A chunk unload removes the mob first.
		saved.discard();
		Mob reloaded = load(helper, tag);
		boolean[] fighting = {false};
		helper.onEachTick(() -> {
			if (fighting[0]) {
				player.setInvulnerableTime(0);
				reloaded.setTarget(player);
			}
		});
		double[] idleFar = {Double.NaN};
		LearnArena.whenReady(helper, List.of(player, reloaded), () -> helper.runAfterDelay(10L, () -> {
			idleFar[0] = ReactiveState.get(reloaded).getFarDistance();
			helper.assertTrue(idleFar[0] == current.farDistance(), "the reloaded archer stood at far " + idleFar[0] + " before any fight, not " + current.farDistance());
			fighting[0] = true;
			LearnArena.pollUntil(helper, () -> ReactiveState.get(reloaded).getNearDistance() > current.nearDistance() + 1.0, 100, () -> {
				ReactiveState state = ReactiveState.get(reloaded);
				LearnedBand.Base base = LearnedBand.baseBand(reloaded);
				double shift = LearnConfigStore.get().rushBandGain() * LearnedBand.rushPressure(player);
				StellaLearn.LOGGER.info(LearnLog.line(
					"reloadTakesCurrentBand", "RESULT", "savedFar=" + STALE_FAR + " idleFar=" + idleFar[0] + " baseFar=" + (base == null ? null : base.far())
						+ " near=" + state.getNearDistance() + " far=" + state.getFarDistance()
						+ " wantNear=" + (current.nearDistance() + shift) + " wantFar=" + (current.farDistance() + shift)
				));
				helper.assertTrue(state.getRole() == CombatRole.RANGED, "the reloaded mob is " + state.getRole() + ", so the case proves nothing");
				helper.assertTrue(base != null && base.far() == current.farDistance(), "the mob kept the base it was saved with: " + base);
				helper.assertTrue(Math.abs(state.getFarDistance() - (current.farDistance() + shift)) < TOLERANCE, "far band was " + state.getFarDistance());
				helper.assertTrue(Math.abs(state.getNearDistance() - (current.nearDistance() + shift)) < TOLERANCE, "near band was " + state.getNearDistance());
				helper.succeed();
			}, () -> "the reloaded mob was never given a band");
		}));
	}
}
