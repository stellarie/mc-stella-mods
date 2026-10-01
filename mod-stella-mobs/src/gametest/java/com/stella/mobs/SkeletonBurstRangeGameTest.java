package com.stella.mobs;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A skeleton shoots from as far as its range. A target 22 blocks away is past the old range of 15 and inside the
 * new range of 24. The first case lets the skeleton choose a player by itself, so its follow range is under test as
 * well as its bow. The second case hands it a target and measures the shots. The third case keeps a target at 27
 * blocks, past the range, and shows the skeleton holds fire until the target comes inside it.
 *
 * <p>Both stand on a pad with open air and forced chunks, because the arena's barrier walls stand eight blocks out.
 * Each case has its own height band. A skeleton sees a player 32 blocks away in any direction, so two pads at one
 * height would let one case's player draw the other's skeleton, whatever the grid puts between them.
 */
public class SkeletonBurstRangeGameTest {
	private static final int ATTACK_DISTANCE = 22;
	/** Past the new range of 24, with room to spare. */
	private static final int BEYOND_DISTANCE = 27;
	private static final int HOLD_TICKS = 60;
	private static final double DISTANCE_TOLERANCE = 1.0;
	/** Past the old range of 15. */
	private static final double OLD_RANGE = 15.0;
	private static final double SPEC_RANGE = 24.0;
	/** Far from every arena and padded test. */
	private static final int PAD_X = -300;
	private static final int PAD_Z = 300;
	/** Height bands: far above arenas, 60 apart. */
	private static final int PLAYER_PAD_Y = 100;
	private static final int GIVEN_PAD_Y = 160;
	private static final int HOLD_PAD_Y = 220;
	private static final int PAD_MARGIN = 3;
	private static final int PAD_HEIGHT = 6;
	private static final float TARGET_HEALTH = 10_000.0F;

	static {
		VolleyRecord.install();
	}

	@GameTest(maxTicks = 400)
	public void aSkeletonAttacksAPlayerTwentyTwoBlocksAway(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Scene scene = new Scene();
		raisePad(helper, PLAYER_PAD_Y, ATTACK_DISTANCE, () -> {
			scene.shooter = skeleton(helper, PLAYER_PAD_Y);
			ServerPlayer player = survivalPlayer(helper);
			Vec3 at = helper.absoluteVec(new Vec3(PAD_X + 0.5, PLAYER_PAD_Y + 1.0, PAD_Z + 0.5 + ATTACK_DISTANCE));
			player.teleportTo(at.x, at.y, at.z);
			scene.victim = player;
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(scene.shooter != null, "the pad is not up yet");
			Skeleton shooter = scene.shooter;
			helper.assertTrue(shooter.getTarget() == scene.victim, "the skeleton has not chosen the player: " + describe(shooter, scene.victim));
			assertAttackedAtRange(helper, shooter, scene.victim, "attackPlayer");
		});
	}

	@GameTest(maxTicks = 400)
	public void aSkeletonLoosesAtAGivenTargetTwentyTwoBlocksAway(final GameTestHelper helper) {
		Scene scene = new Scene();
		raisePad(helper, GIVEN_PAD_Y, ATTACK_DISTANCE, () -> {
			scene.shooter = skeleton(helper, GIVEN_PAD_Y);
			scene.victim = golem(helper, GIVEN_PAD_Y, ATTACK_DISTANCE);
			scene.shooter.setTarget(scene.victim);
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(scene.shooter != null, "the pad is not up yet");
			assertAttackedAtRange(helper, scene.shooter, scene.victim, "attackGiven");
		});
	}

	@GameTest(maxTicks = 300)
	public void aSkeletonHoldsFireBeyondItsRangeUntilTheTargetComesIn(final GameTestHelper helper) {
		Scene scene = new Scene();
		int[] phase = new int[1];
		long[] mark = new long[1];
		raisePad(helper, HOLD_PAD_Y, BEYOND_DISTANCE, () -> {
			scene.shooter = skeleton(helper, HOLD_PAD_Y);
			scene.victim = golem(helper, HOLD_PAD_Y, BEYOND_DISTANCE);
			scene.shooter.setTarget(scene.victim);
			mark[0] = helper.getTick();
		});

		helper.onEachTick(() -> {
			if (scene.shooter == null) {
				return;
			}

			List<VolleyRecord.Release> loosed = new ArrayList<>(VolleyRecord.burstArrows(scene.shooter));
			loosed.addAll(VolleyRecord.shots(scene.shooter));
			if (phase[0] == 0 && helper.getTick() >= mark[0] + HOLD_TICKS) {
				helper.assertTrue(loosed.isEmpty(), "the skeleton loosed " + loosed.size() + " arrows at a target " + BEYOND_DISTANCE + " blocks away");
				helper.assertTrue(scene.shooter.getTarget() == scene.victim, "the skeleton lost its target, so the case proves nothing: " + describe(scene.shooter, scene.victim));
				Vec3 in = helper.absoluteVec(new Vec3(PAD_X + 0.5, HOLD_PAD_Y + 1.0, PAD_Z + 0.5 + ATTACK_DISTANCE));
				scene.victim.snapTo(in.x, in.y, in.z, 0.0F, 0.0F);
				phase[0] = 1;
			} else if (phase[0] == 1 && !loosed.isEmpty()) {
				double first = loosed.get(0).targetDistance();
				helper.assertTrue(Math.abs(first - ATTACK_DISTANCE) <= DISTANCE_TOLERANCE, "the first arrow left from " + first + " blocks, not " + ATTACK_DISTANCE);
				StellaMobs.LOGGER.info("[BEHAVIOR] test=skeletonBurstRange.holdFire RESULT heldFor={} beyond={} firstArrowDistance={}", HOLD_TICKS, BEYOND_DISTANCE, first);
				helper.succeed();
			}
		});
	}

	/** The first arrow left at range, and one hit. */
	private static void assertAttackedAtRange(
		final GameTestHelper helper, final Skeleton shooter, final LivingEntity victim, final String name
	) {
		List<VolleyRecord.Release> loosed = new ArrayList<>(VolleyRecord.burstArrows(shooter));
		loosed.addAll(VolleyRecord.shots(shooter));
		helper.assertTrue(!loosed.isEmpty(), "the skeleton has loosed no arrow yet: " + describe(shooter, victim));
		double first = loosed.stream().min((a, b) -> Integer.compare(a.serverTick(), b.serverTick())).orElseThrow().targetDistance();
		helper.assertTrue(
			Math.abs(first - ATTACK_DISTANCE) <= DISTANCE_TOLERANCE,
			"the first arrow left from " + first + " blocks, not " + ATTACK_DISTANCE + ", so the skeleton did not fire from where it stood"
		);
		helper.assertTrue(first > OLD_RANGE && first <= SPEC_RANGE, "the distance " + first + " is not between the old range and the new one");
		helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "no arrow has hurt the target, " + loosed.size() + " loosed");
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=skeletonBurstRange.{} RESULT firstArrowDistance={} arrows={} targetHealth={}", name, first, loosed.size(), victim.getHealth()
		);
	}

	/** The shooter's state, for a failure message. */
	private static String describe(final Skeleton shooter, final LivingEntity victim) {
		StringBuilder goals = new StringBuilder();
		for (WrappedGoal wrapped : shooter.getGoalSelector().getAvailableGoals()) {
			goals.append(wrapped.getGoal().getClass().getSimpleName()).append(wrapped.isRunning() ? ":run " : ":idle ");
		}

		return "removed=" + shooter.isRemoved() + " alive=" + shooter.isAlive() + " ticks=" + shooter.tickCount
			+ " holdsBow=" + shooter.isHolding(Items.BOW) + " target=" + shooter.getTarget() + " rawTarget=" + shooter.getTargetUnchecked()
			+ " victimRemoved=" + victim.isRemoved() + " victimAlive=" + victim.isAlive() + " victimTicks=" + victim.tickCount
			+ " distance=" + shooter.distanceTo(victim) + " sees=" + shooter.getSensing().hasLineOfSight(victim)
			+ " difficulty=" + shooter.level().getDifficulty() + " goals=" + goals;
	}

	private static IronGolem golem(final GameTestHelper helper, final int padY, final int distance) {
		IronGolem golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.EVENT);
		Vec3 at = helper.absoluteVec(new Vec3(PAD_X + 0.5, padY + 1.0, PAD_Z + 0.5 + distance));
		golem.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		golem.setNoAi(true);
		golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		golem.setHealth(TARGET_HEALTH);
		golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		golem.setPersistenceRequired();
		helper.getLevel().addFreshEntity(golem);
		return golem;
	}

	private static Skeleton skeleton(final GameTestHelper helper, final int padY) {
		Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(PAD_X + 0.5, padY + 1.0, PAD_Z + 0.5));
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		// Tagged out of the spawn rolls, so the weapon in its hand is exactly the one the test gives it.
		skeleton.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		skeleton.setPersistenceRequired();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "burst-range-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		// A real client reports it has loaded.
		// Until then the player cannot be hurt.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		player.setHealth(TARGET_HEALTH);
		player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return player;
	}

	/**
	 * Forces the chunks under the long line, builds an open stone strip, and runs `populate` on the first tick
	 * when every one of those chunks ticks entities. A spawn into a chunk that does not tick would stand still.
	 * Registered from the test body, because a tick hook added from a delayed callback corrupts the framework.
	 */
	private static void raisePad(final GameTestHelper helper, final int padY, final int length, final Runnable populate) {
		helper.setTime(Arena.NIGHT_TIME);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(new BlockPos(PAD_X - PAD_MARGIN, padY, PAD_Z - PAD_MARGIN));
		BlockPos to = helper.absolutePos(new BlockPos(PAD_X + PAD_MARGIN, padY, PAD_Z + length + PAD_MARGIN));
		List<ChunkPos> chunks = new ArrayList<>();
		for (int x = from.getX() >> 4; x <= to.getX() >> 4; x++) {
			for (int z = from.getZ() >> 4; z <= to.getZ() >> 4; z++) {
				chunks.add(new ChunkPos(x, z));
				level.setChunkForced(x, z, true);
			}
		}

		helper.runBeforeTestEnd(() -> chunks.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false)));
		boolean[] raised = new boolean[1];
		helper.onEachTick(() -> {
			if (raised[0] || !chunks.stream().allMatch(chunk -> level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX() + 8, from.getY(), chunk.getMinBlockZ() + 8)))) {
				return;
			}

			raised[0] = true;
			for (int x = -PAD_MARGIN; x <= PAD_MARGIN; x++) {
				for (int z = -PAD_MARGIN; z <= length + PAD_MARGIN; z++) {
					helper.setBlock(PAD_X + x, padY, PAD_Z + z, Blocks.STONE);
					for (int y = 1; y <= PAD_HEIGHT; y++) {
						helper.setBlock(PAD_X + x, padY + y, PAD_Z + z, Blocks.AIR);
					}
				}
			}

			try {
				populate.run();
			} catch (RuntimeException e) {
				// A plain exception here stops the server.
				// A test failure stops only this test.
				helper.fail("the pad could not be set up: " + e);
			}
		});
	}

	/** What the pad holds once it is up. */
	private static final class Scene {
		private volatile Skeleton shooter;
		private volatile LivingEntity victim;
	}
}
