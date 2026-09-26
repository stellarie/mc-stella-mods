package com.stella.mobs;

import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.ShieldUseGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One observation arena: a flat floor, a subject mob with deterministic gear, an undying melee opponent,
 * and an undying ranged opponent. The rig forces every target on each tick, so the fight cannot wander
 * off. A zombie is the melee opponent because its attack goal needs no extra code. A skeleton with a bow
 * is the ranged opponent for the same reason. The rig strips the mod's distance, retreat, and shield
 * goals from both opponents, so only the subject is under behavior test.
 */
final class Arena {
	static final int MAX_TICKS = 200;
	static final long NIGHT_TIME = 18000L;
	static final float SUBJECT_HEALTH = 100.0F;
	// 26.3 drops an invulnerable entity from target validation, so an opponent survives on health.
	static final float OPPONENT_HEALTH = 10000.0F;
	private static final double STILL_SPEED = 0.02;
	private static final long STRIP_GOALS_TICK = 2L;
	private static final int SPAWN_Y = 3;
	private static final int FLOOR_Y = 0;
	private static final int MELEE_X = 6;
	private static final int MELEE_Z = 6;
	private static final int RANGED_X = 6;
	private static final int RANGED_Z = 1;

	private final GameTestHelper helper;
	private final Mob subject;
	private final Zombie meleeOpponent;
	private final Skeleton rangedOpponent;
	private final BehaviorLog log;
	private final @Nullable ServerPlayer playerThreat;

	private Arena(
		final GameTestHelper helper,
		final Mob subject,
		final Zombie meleeOpponent,
		final Skeleton rangedOpponent,
		final BehaviorLog log,
		final @Nullable ServerPlayer playerThreat
	) {
		this.helper = helper;
		this.subject = subject;
		this.meleeOpponent = meleeOpponent;
		this.rangedOpponent = rangedOpponent;
		this.log = log;
		this.playerThreat = playerThreat;
	}

	enum Threat {
		MOB_PROP,
		PLAYER
	}

	static Arena build(
		final GameTestHelper helper,
		final String test,
		final String config,
		final EntityType<? extends Mob> subjectType,
		final int subjectX,
		final int subjectZ,
		final boolean meleeChases
	) {
		return build(helper, test, config, subjectType, subjectX, subjectZ, meleeChases, Threat.MOB_PROP);
	}

	static Arena build(
		final GameTestHelper helper,
		final String test,
		final String config,
		final EntityType<? extends Mob> subjectType,
		final int subjectX,
		final int subjectZ,
		final boolean meleeChases,
		final Threat threat
	) {
		GearConfig previousConfig = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previousConfig));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper);
		ServerPlayer player = threat == Threat.PLAYER ? addSurvivalPlayer(helper) : addPlayer(helper);

		if (threat == Threat.PLAYER) {
			// A peaceful level drops every player from canAttack, so the arena needs a fighting difficulty.
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(OPPONENT_HEALTH);
			player.setHealth(OPPONENT_HEALTH);
			Vec3 position = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
			player.setPos(position.x, position.y, position.z);
		}

		Mob subject = helper.spawnMob(subjectType, subjectX, SPAWN_Y, subjectZ).spawn();
		subject.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SUBJECT_HEALTH);
		subject.setHealth(SUBJECT_HEALTH);
		subject.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

		Zombie meleeOpponent = helper.spawnMob(EntityTypes.ZOMBIE, MELEE_X, SPAWN_Y, MELEE_Z).spawn();
		Skeleton rangedOpponent = helper.spawnMob(EntityTypes.SKELETON, RANGED_X, SPAWN_Y, RANGED_Z).spawn();
		rangedOpponent.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		makeOpponent(meleeOpponent, !meleeChases);
		makeOpponent(rangedOpponent, true);

		BehaviorLog log = new BehaviorLog(helper, test, subject, meleeOpponent, rangedOpponent);
		Arena arena = new Arena(helper, subject, meleeOpponent, rangedOpponent, log, threat == Threat.PLAYER ? player : null);
		helper.onEachTick(arena::forceTargets);
		helper.runAfterDelay(STRIP_GOALS_TICK, arena::stripBehaviorGoals);
		return arena;
	}

	static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, FLOOR_Y, z, Blocks.STONE);
			}
		}
	}

	static void contain(final GameTestHelper helper, final Mob mob, final int x, final int z) {
		contain(helper, mob, x, z, 1);
	}

	static void contain(final GameTestHelper helper, final Mob mob, final int x, final int z, final int wallHeight) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}

				for (int y = 1; y <= wallHeight; y++) {
					helper.setBlock(x + dx, y, z + dz, Blocks.STONE);
				}
			}
		}

		mob.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0);
	}

	Mob subject() {
		return this.subject;
	}

	Zombie meleeOpponent() {
		return this.meleeOpponent;
	}

	Skeleton rangedOpponent() {
		return this.rangedOpponent;
	}

	ServerPlayer playerThreat() {
		if (this.playerThreat == null) {
			throw new IllegalStateException("This arena has no player threat");
		}

		return this.playerThreat;
	}

	BehaviorLog log() {
		return this.log;
	}

	long tick() {
		return this.helper.getTick();
	}

	private void forceTargets() {
		// A hurt entity carries immunity frames, and 26.3 drops it from setTarget validation while it does.
		this.meleeOpponent.setInvulnerableTime(0);
		this.rangedOpponent.setInvulnerableTime(0);

		if (this.playerThreat != null) {
			this.playerThreat.setInvulnerableTime(0);
			this.playerThreat.getAbilities().instabuild = false;
		}

		this.subject.setTarget(this.playerThreat != null ? this.playerThreat : this.meleeOpponent);
		this.meleeOpponent.setTarget(this.subject);
		this.rangedOpponent.setTarget(this.subject);
		this.log.tick();
	}

	private void stripBehaviorGoals() {
		for (Mob opponent : new Mob[] {this.meleeOpponent, this.rangedOpponent}) {
			opponent.getGoalSelector().removeAllGoals(goal -> goal instanceof KeepDistanceGoal
				|| goal instanceof RetreatGoal
				|| goal instanceof HoldStandoffGoal
				|| goal instanceof ShieldUseGoal);
		}
	}

	private static void makeOpponent(final Mob opponent, final boolean still) {
		opponent.getAttribute(Attributes.MAX_HEALTH).setBaseValue(OPPONENT_HEALTH);
		opponent.setHealth(OPPONENT_HEALTH);
		opponent.setPersistenceRequired();
		opponent.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

		if (still) {
			opponent.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(STILL_SPEED);
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer addPlayer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(0.5, 1.0, 7.5));
		player.setPos(position.x, position.y, position.z);
		return player;
	}

	static ServerPlayer addSurvivalPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		Vec3 position = helper.absoluteVec(new Vec3(0.5, 1.0, 7.5));
		player.setPos(position.x, position.y, position.z);
		return player;
	}
}
