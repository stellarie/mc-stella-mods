package com.stella.villages;

import com.stella.mobs.behavior.CrossbowCadenceGoal;
import com.stella.mobs.behavior.ShieldUseGoal;
import com.stella.villages.patrol.PatrolState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Village defenders run the mobs mod's enhanced AI. A ranged guard fires its crossbow at a hostile mob twenty blocks
 * away, and a melee guard raises its shield at an incoming arrow. The village keeps both goals: it replaces neither.
 */
public class DefenderAiGameTest {
	private static final int HOSTILE_DISTANCE = 20;
	private static final int FIRE_WINDOW_TICKS = 260;
	private static final float HOSTILE_HEALTH = 10_000.0F;
	// Wider than the crossbow's spread,
	// narrower than a wild shot.
	private static final double AIM_COSINE = Math.cos(Math.toRadians(25.0));
	private static final double ARROW_SPEED = 0.2;
	private static final double ARROW_DISTANCE = 6.0;

	@GameTest(maxTicks = FIRE_WINDOW_TICKS + 20, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rangedGuardFiresAtHostileTwentyBlocksAway(final GameTestHelper helper) {
		String test = "rangedGuardFiresAtHostileTwentyBlocksAway";
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		BlockPos bell = PatrolTestSupport.anchor(helper);
		VillagePatrolGameTest.prepareMovementLane(helper, bell, HOSTILE_DISTANCE + 10);
		Mob guard = PatrolTestSupport.role(helper, test, PatrolState.Role.RANGED_GUARD.name());
		helper.assertTrue(guard != null, "the village has no ranged guard");
		helper.assertTrue(hasGoal(guard, CrossbowCadenceGoal.class), "the ranged guard lost the mobs mod's crossbow goal: " + goals(guard));
		helper.assertFalse(hasGoal(guard, RangedCrossbowAttackGoal.class), "the ranged guard kept vanilla's crossbow goal beside the mobs mod's: " + goals(guard));
		for (Mob other : PatrolTestSupport.patrols(helper)) {
			if (other != guard) {
				other.setNoAi(true);
			}
		}
		// Held on its block: the beat cannot move it.
		guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		Vec3 guardAt = Vec3.atBottomCenterOf(bell.offset(-6, 0, 1));
		guard.snapTo(guardAt.x, guardAt.y, guardAt.z, 0.0F, 0.0F);
		Mob hostile = hostile(helper, guardAt.add(HOSTILE_DISTANCE, 0.0, 0.0));
		AABB lane = new AABB(guardAt.x - 4.0, guardAt.y - 2.0, guardAt.z - 6.0, guardAt.x + HOSTILE_DISTANCE + 8.0, guardAt.y + 8.0, guardAt.z + 6.0);
		Set<UUID> arrows = new HashSet<>();
		double[] bestAim = {-1.0};
		boolean[] targeted = {false};
		helper.onEachTick(() -> {
			targeted[0] |= guard.getTarget() == hostile;
			Vec3 line = hostile.position().subtract(guard.position()).normalize();
			for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, lane, candidate -> candidate.getOwner() == guard)) {
				if (arrows.add(arrow.getUUID())) {
					bestAim[0] = Math.max(bestAim[0], arrow.getDeltaMovement().normalize().dot(line));
				}
			}
		});
		helper.succeedWhen(() -> {
			String state = "distance=" + guard.distanceTo(hostile) + " target=" + guard.getTarget() + " charging=" + guard.isUsingItem() + " goals=" + goals(guard);
			helper.assertTrue(targeted[0], "the ranged guard never targeted the hostile: " + state);
			helper.assertTrue(!arrows.isEmpty(), "the ranged guard fired nothing: " + state);
			helper.assertTrue(bestAim[0] >= AIM_COSINE, "no arrow flew toward the hostile: best cosine " + bestAim[0]);
			PatrolTestSupport.result(test, "distance=" + HOSTILE_DISTANCE + " arrows=" + arrows.size() + " bestAimCosine=" + bestAim[0] + " tick=" + helper.getTick());
		});
	}

	@GameTest(maxTicks = 120, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void meleeGuardRaisesItsShieldAtAnIncomingArrow(final GameTestHelper helper) {
		String test = "meleeGuardRaisesItsShieldAtAnIncomingArrow";
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob guard = PatrolTestSupport.role(helper, test, PatrolState.Role.PILLAGER.name());
		helper.assertTrue(guard != null, "the village has no melee guard");
		helper.assertTrue(hasGoal(guard, ShieldUseGoal.class), "the melee guard has no shield goal: " + goals(guard));
		for (Mob other : PatrolTestSupport.patrols(helper)) {
			if (other != guard) {
				other.setNoAi(true);
			}
		}
		guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		// The shield goal runs only near a player.
		// It scans for arrows once per run.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(guard.getX() - 2.0, guard.getY(), guard.getZ());
		Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
		arrow.snapTo(guard.getX() + ARROW_DISTANCE, guard.getY() + 1.0, guard.getZ(), 0.0F, 0.0F);
		arrow.setDeltaMovement(-ARROW_SPEED, 0.0, 0.0);
		level.addFreshEntity(arrow);
		helper.runBeforeTestEnd(arrow::discard);
		boolean[] raised = {false};
		boolean[] blocking = {false};
		helper.onEachTick(() -> {
			raised[0] |= guard.isUsingItem() && guard.getUsedItemHand() == InteractionHand.OFF_HAND;
			blocking[0] |= guard.isBlocking();
		});
		helper.succeedWhen(() -> {
			String state = "target=" + guard.getTarget() + " offHand=" + guard.getOffhandItem() + " arrowAlive=" + arrow.isAlive() + " goals=" + goals(guard);
			helper.assertTrue(raised[0], "the melee guard never raised its shield at the incoming arrow: " + state);
			helper.assertTrue(blocking[0], "the shield was raised but never blocked: " + state);
			PatrolTestSupport.result(test, "raised=" + raised[0] + " blocking=" + blocking[0]);
		});
	}

	/** A hostile that stands still and lives. */
	private static Mob hostile(final GameTestHelper helper, final Vec3 at) {
		Mob husk = EntityTypes.HUSK.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		husk.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HOSTILE_HEALTH);
		husk.setHealth(HOSTILE_HEALTH);
		husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(husk);
		return husk;
	}

	private static boolean hasGoal(final Mob mob, final Class<?> type) {
		return mob.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> type.isInstance(wrapped.getGoal()));
	}

	private static List<String> goals(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority() + (wrapped.isRunning() ? "*" : ""))
			.toList();
	}

	/**
	 * A guard's stray arrow spares the village: a villager, an iron golem and a bystander player take no damage. The
	 * player the guard fights as a threat still takes the hit.
	 */
	@GameTest(maxTicks = 40, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void strayHitsSpareTheVillage(final GameTestHelper helper) {
		String test = "strayHitsSpareTheVillage";
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob guard = PatrolTestSupport.role(helper, test, PatrolState.Role.RANGED_GUARD.name());
		helper.assertTrue(guard != null, "the village has no ranged guard");
		Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(1.5, 2.0, 1.5));
		Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(3.5, 2.0, 1.5));
		ServerPlayer bystander = loadedSurvivalPlayer(helper, new Vec3(1.5, 2.0, 3.5));
		ServerPlayer threat = loadedSurvivalPlayer(helper, new Vec3(3.5, 2.0, 3.5));
		guard.setTarget(threat);
		Arrow arrow = new Arrow(level, guard, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
		float[] lost = new float[4];
		LivingEntityHit[] hits = {
			new LivingEntityHit(villager, 0), new LivingEntityHit(golem, 1), new LivingEntityHit(bystander, 2), new LivingEntityHit(threat, 3)
		};
		for (LivingEntityHit hit : hits) {
			float before = hit.victim().getHealth();
			hit.victim().hurtServer(level, level.damageSources().arrow(arrow, guard), 4.0F);
			lost[hit.index()] = before - hit.victim().getHealth();
		}
		PatrolTestSupport.result(test, "villager=" + lost[0] + " golem=" + lost[1] + " bystander=" + lost[2] + " threat=" + lost[3]);
		helper.assertTrue(lost[0] == 0.0F && lost[1] == 0.0F && lost[2] == 0.0F,
			"a stray arrow hurt the village: villager=" + lost[0] + " golem=" + lost[1] + " bystander=" + lost[2]);
		helper.assertTrue(lost[3] > 0.0F, "the guard's arrow did not hurt the threat it fights: " + lost[3]);
		helper.succeed();
	}

	private record LivingEntityHit(net.minecraft.world.entity.LivingEntity victim, int index) {
	}

	private static ServerPlayer loadedSurvivalPlayer(final GameTestHelper helper, final Vec3 at) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
			net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		Vec3 spot = helper.absoluteVec(at);
		player.teleportTo(spot.x, spot.y, spot.z);
		return player;
	}
}
