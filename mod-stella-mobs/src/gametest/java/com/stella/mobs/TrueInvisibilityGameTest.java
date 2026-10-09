package com.stella.mobs;

import com.stella.mobs.behavior.TrueInvisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.Vec3;

/**
 * An invisible player is hidden from every mob that is not undead until the player attacks. The attack reveals the
 * player for {@link TrueInvisibility#REVEAL_TICKS} ticks. Vanilla keeps a two block floor on the sight range, so the
 * close cases sit at {@link #CLOSE_BLOCKS}, inside that floor. Every case counts game test ticks, which advance with
 * the server tick, and never reads the level clock, which barely moves here.
 */
public class TrueInvisibilityGameTest {
	private static final String TEST = "trueInvisibility";
	private static final double CLOSE_BLOCKS = 1.5;
	private static final double NEAR_BLOCKS = 3.0;
	private static final double SCENT_BLOCKS = 6.0;
	private static final int WATCH_TICKS = 200;
	private static final int ACQUIRE_TICKS = 60;
	private static final int SETTLE_TICKS = 25;
	private static final int HIT_TICK = 5;
	private static final int AFTER_REVEAL_TICKS = HIT_TICK + TrueInvisibility.REVEAL_TICKS + 40;
	private static final int EFFECT_TICKS = 100000;
	private static final double PLAYER_X = 3.5;
	private static final double PLAYER_Z = 4.5;
	private static final float OPPONENT_HEALTH = 10000.0F;
	private static final float LOW_HEALTH_SHARE = 0.1F;

	private static final List<EntityType<? extends Mob>> HUNTERS = List.of(EntityTypes.SPIDER, EntityTypes.VINDICATOR, EntityTypes.PILLAGER);
	private static final List<EntityType<? extends Mob>> UNDEAD = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON);

	/** A player and the mobs that watch it. */
	private static final class Scene {
		private final ServerPlayer player;
		private final List<Mob> mobs = new ArrayList<>();

		private Scene(final ServerPlayer player) {
			this.player = player;
		}

		private String describe() {
			StringBuilder out = new StringBuilder("invisible=" + this.player.hasEffect(MobEffects.INVISIBILITY)
				+ " revealed=" + TrueInvisibility.isRevealed(this.player));
			for (Mob mob : this.mobs) {
				out.append(' ').append(mob.getType().toShortString()).append("->")
					.append(mob.getTarget() == this.player ? "player" : mob.getTarget());
			}

			return out.toString();
		}
	}

	private static Scene scene(final GameTestHelper helper, final boolean invisible) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(OPPONENT_HEALTH);
		player.setHealth(OPPONENT_HEALTH);
		Vec3 position = helper.absoluteVec(new Vec3(PLAYER_X, 1.0, PLAYER_Z));
		player.setPos(position.x, position.y, position.z);
		if (invisible) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0, false, false));
		}

		helper.onEachTick(() -> player.setInvulnerableTime(0));
		return new Scene(player);
	}

	private static Mob place(
		final GameTestHelper helper, final Scene scene, final EntityType<? extends Mob> type, final double distance, final double angle
	) {
		Mob mob = helper.spawnMob(type, 6, 2, 4).spawn();
		Arena.neutralThreat(mob);
		mob.setPersistenceRequired();
		mob.setPos(
			scene.player.getX() + distance * Math.cos(angle),
			scene.player.getY(),
			scene.player.getZ() + distance * Math.sin(angle)
		);
		scene.mobs.add(mob);
		return mob;
	}

	private static Scene crowd(
		final GameTestHelper helper, final boolean invisible, final List<EntityType<? extends Mob>> types, final double distance
	) {
		Scene scene = scene(helper, invisible);
		for (EntityType<? extends Mob> type : types) {
			// Each mob stands on its own side, so none blocks another's sight line.
			place(helper, scene, type, distance, scene.mobs.size() * 2.0);
		}

		return scene;
	}

	/** Counts, per mob, the ticks it holds the player as its target, from the tick {@code from} on. */
	private static int[] watch(final GameTestHelper helper, final Scene scene, final int from) {
		int[] held = new int[scene.mobs.size()];
		helper.onEachTick(() -> {
			if (helper.getTick() < from) {
				return;
			}

			for (int index = 0; index < held.length; index++) {
				if (scene.mobs.get(index).getTarget() == scene.player) {
					held[index]++;
				}
			}
		});
		return held;
	}

	private static int sum(final int[] counts) {
		int total = 0;
		for (int count : counts) {
			total += count;
		}

		return total;
	}

	private static void attack(final Scene scene, final Cow victim) {
		ServerLevel level = (ServerLevel) victim.level();
		victim.hurtServer(level, level.damageSources().playerAttack(scene.player), 1.0F);
	}

	private static Cow cow(final GameTestHelper helper) {
		Cow victim = helper.spawnMob(EntityTypes.COW, 1, 2, 1).spawn();
		victim.setNoAi(true);
		victim.setPersistenceRequired();
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(OPPONENT_HEALTH);
		victim.setHealth(OPPONENT_HEALTH);
		return victim;
	}

	private static boolean synced(final Scene scene) {
		return ((AttachmentTarget) scene.player).getAttachedOrElse(TrueInvisibility.REVEALED, false);
	}

	/** The harness check: a visible player is acquired, so the invisible cases below can fail. */
	@GameTest(maxTicks = WATCH_TICKS)
	public void visiblePlayerIsAcquiredByEveryHunter(final GameTestHelper helper) {
		Scene scene = crowd(helper, false, HUNTERS, NEAR_BLOCKS);
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(ACQUIRE_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.visibleControl RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			for (int index = 0; index < held.length; index++) {
				helper.assertTrue(held[index] > 0, scene.mobs.get(index).getType().toShortString() + " never took a visible player: " + scene.describe());
			}

			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS + 40)
	public void hunterNeverAcquiresInvisiblePlayerAtThreeBlocks(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, HUNTERS, NEAR_BLOCKS);
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.hiddenAtThree RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(sum(held) == 0, "a hunter took the invisible player at " + NEAR_BLOCKS + " blocks, ticks held " + Arrays.toString(held) + ": " + scene.describe());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS + 40)
	public void hunterNeverAcquiresInvisiblePlayerInsideTheSightFloor(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, HUNTERS, CLOSE_BLOCKS);
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.hiddenInsideFloor RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(sum(held) == 0, "a hunter took the invisible player at " + CLOSE_BLOCKS + " blocks, ticks held " + Arrays.toString(held) + ": " + scene.describe());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS)
	public void hunterDropsTheTargetWhenThePlayerTurnsInvisible(final GameTestHelper helper) {
		Scene scene = crowd(helper, false, HUNTERS, CLOSE_BLOCKS);
		for (Mob mob : scene.mobs) {
			mob.setTarget(scene.player);
		}

		helper.runAfterDelay(HIT_TICK, () -> scene.player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0, false, false)));
		int[] held = watch(helper, scene, HIT_TICK + SETTLE_TICKS);
		helper.runAfterDelay(HIT_TICK + SETTLE_TICKS + 40, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.dropsTarget RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(sum(held) == 0, "a hunter kept the player after it turned invisible, ticks held " + Arrays.toString(held) + ": " + scene.describe());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS)
	public void undeadAcquiresInvisiblePlayer(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, UNDEAD, NEAR_BLOCKS);
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(ACQUIRE_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.undead RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			for (int index = 0; index < held.length; index++) {
				helper.assertTrue(held[index] > 0, scene.mobs.get(index).getType().toShortString() + " never took the invisible player: " + scene.describe());
			}

			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS)
	public void attackRevealsAndSyncsTheState(final GameTestHelper helper) {
		Scene scene = scene(helper, true);
		Cow victim = cow(helper);
		boolean[] before = new boolean[2];
		helper.runAfterDelay(HIT_TICK, () -> {
			before[0] = TrueInvisibility.isRevealed(scene.player);
			before[1] = synced(scene);
			attack(scene, victim);
		});
		helper.runAfterDelay(HIT_TICK + SETTLE_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.reveal RESULT before={} revealed={} synced={}", TEST, Arrays.toString(before), TrueInvisibility.isRevealed(scene.player), synced(scene));
			helper.assertTrue(!before[0] && !before[1], "the player was revealed before any attack");
			helper.assertTrue(TrueInvisibility.isRevealed(scene.player), "the attack did not reveal the player");
			helper.assertTrue(synced(scene), "the synced reveal state stayed off after the attack");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = AFTER_REVEAL_TICKS + 20)
	public void revealEndsAfterTheWindow(final GameTestHelper helper) {
		Scene scene = scene(helper, true);
		Cow victim = cow(helper);
		boolean[] midway = new boolean[2];
		helper.runAfterDelay(HIT_TICK, () -> attack(scene, victim));
		helper.runAfterDelay(HIT_TICK + TrueInvisibility.REVEAL_TICKS - SETTLE_TICKS, () -> {
			midway[0] = TrueInvisibility.isRevealed(scene.player);
			midway[1] = synced(scene);
		});
		helper.runAfterDelay(AFTER_REVEAL_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.revealEnds RESULT midway={} revealed={} synced={}", TEST, Arrays.toString(midway), TrueInvisibility.isRevealed(scene.player), synced(scene));
			helper.assertTrue(midway[0] && midway[1], "the reveal ended before its window");
			helper.assertTrue(!TrueInvisibility.isRevealed(scene.player), "the reveal outlived its window");
			helper.assertTrue(!synced(scene), "the synced reveal state outlived its window");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = WATCH_TICKS)
	public void revealedPlayerIsAcquiredAtThreeBlocks(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, HUNTERS, NEAR_BLOCKS);
		Cow victim = cow(helper);
		helper.runAfterDelay(HIT_TICK, () -> attack(scene, victim));
		int[] held = watch(helper, scene, HIT_TICK);
		helper.runAfterDelay(HIT_TICK + ACQUIRE_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.revealedAcquired RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			for (int index = 0; index < held.length; index++) {
				helper.assertTrue(held[index] > 0, scene.mobs.get(index).getType().toShortString() + " never took the revealed player: " + scene.describe());
			}

			helper.succeed();
		});
	}

	@GameTest(maxTicks = AFTER_REVEAL_TICKS + 80)
	public void hiddenAgainWhenTheRevealEnds(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, HUNTERS, CLOSE_BLOCKS);
		Cow victim = cow(helper);
		helper.runAfterDelay(HIT_TICK, () -> attack(scene, victim));
		int[] held = watch(helper, scene, AFTER_REVEAL_TICKS);
		helper.runAfterDelay(AFTER_REVEAL_TICKS + 40, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.hiddenAgain RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(sum(held) == 0, "a hunter held the player after the reveal ended, ticks held " + Arrays.toString(held) + ": " + scene.describe());
			helper.succeed();
		});
	}

	/** Every custom caller of setTarget reaches the mob through this one call. */
	@GameTest(maxTicks = WATCH_TICKS)
	public void setTargetRefusesTheInvisiblePlayerForHunters(final GameTestHelper helper) {
		Scene scene = crowd(helper, true, HUNTERS, NEAR_BLOCKS);
		Scene undead = new Scene(scene.player);
		for (EntityType<? extends Mob> type : UNDEAD) {
			place(helper, undead, type, NEAR_BLOCKS, undead.mobs.size() * 2.0 + 1.0);
		}

		helper.runAfterDelay(HIT_TICK, () -> {
			for (Mob mob : scene.mobs) {
				mob.setTarget(scene.player);
			}

			for (Mob mob : undead.mobs) {
				mob.setTarget(scene.player);
			}
		});
		helper.runAfterDelay(HIT_TICK + 1, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.setTarget RESULT hunters={} undead={}", TEST, scene.describe(), undead.describe());
			for (Mob mob : scene.mobs) {
				helper.assertTrue(mob.getTarget() != scene.player, mob.getType().toShortString() + " accepted the invisible player through setTarget");
			}

			for (Mob mob : undead.mobs) {
				helper.assertTrue(mob.getTarget() == scene.player, mob.getType().toShortString() + " refused the invisible player through setTarget");
			}

			helper.succeed();
		});
	}

	/** The blood scent calls monsters in from afar without a sight test, so it is the riskiest custom path. */
	@GameTest(maxTicks = WATCH_TICKS)
	public void bloodScentSkipsTheInvisiblePlayer(final GameTestHelper helper) {
		Scene scene = scene(helper, true);
		place(helper, scene, EntityTypes.VINDICATOR, SCENT_BLOCKS, 0.0).setNoAi(true);
		helper.onEachTick(() -> scene.player.setHealth(scene.player.getMaxHealth() * LOW_HEALTH_SHARE));
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(ACQUIRE_TICKS + 20, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.bloodScentInvisible RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(held[0] == 0, "the blood scent drew a monster to an invisible player: " + scene.describe());
			helper.succeed();
		});
	}

	/** The harness check for the case above: the same scene with a visible player draws the monster. */
	@GameTest(maxTicks = WATCH_TICKS)
	public void bloodScentDrawsTheVisiblePlayer(final GameTestHelper helper) {
		Scene scene = scene(helper, false);
		place(helper, scene, EntityTypes.VINDICATOR, SCENT_BLOCKS, 0.0).setNoAi(true);
		helper.onEachTick(() -> scene.player.setHealth(scene.player.getMaxHealth() * LOW_HEALTH_SHARE));
		int[] held = watch(helper, scene, 0);
		helper.runAfterDelay(ACQUIRE_TICKS + 20, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.bloodScentVisible RESULT held={} {}", TEST, Arrays.toString(held), scene.describe());
			helper.assertTrue(held[0] > 0, "the blood scent did not draw a monster to a visible player: " + scene.describe());
			helper.succeed();
		});
	}
}
