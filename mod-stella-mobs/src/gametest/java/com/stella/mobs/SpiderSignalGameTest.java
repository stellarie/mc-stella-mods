package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.SpiderSignal;
import com.stella.mobs.behavior.WebCastGoal;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * An elite spider that webs a player calls the monsters around it onto that player. Every monster here has its AI off,
 * so none of them finds the player alone, and a target that one holds came from the signal. The signal goes
 * through the recorder, keyed by the spider so concurrent tests never mix.
 */
public class SpiderSignalGameTest {
	private static final String TEST = "spiderSignal";
	private static final int MAX_TICKS = 200;
	private static final double PLAYER_X = 8.5;
	private static final double PLAYER_Z = 20.5;
	private static final int SPIDER_X = 8;
	private static final int SPIDER_Z = 18;
	private static final int FLOOR_Y = MeleeKitFloor.FLOOR_TOP_Y;
	private static final int[] CAP_GAPS = {3, 5, 7, 9, 11, 13, 15, 17};
	private static final double STRANGER_RADIUS = SpiderSignal.RADIUS + 8.0;

	private record Signal(LivingEntity target, List<Mob> accepted, List<Mob> declined) {
	}

	private static final Map<UUID, List<Signal>> SIGNALS = new ConcurrentHashMap<>();

	static {
		SpiderSignal.setRecorder((spider, target, accepted, declined) -> {
			SIGNALS.computeIfAbsent(spider.getUUID(), key -> new CopyOnWriteArrayList<>())
				.add(new Signal(target, List.copyOf(accepted), List.copyOf(declined)));
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void alliesTakeWebbedTarget(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Spider spider = idle(scene.mob(EntityTypes.SPIDER, SPIDER_X, FLOOR_Y, SPIDER_Z));
		Zombie near = idle(scene.mob(EntityTypes.ZOMBIE, 14, FLOOR_Y, 18));
		Skeleton archer = idle(scene.mob(EntityTypes.SKELETON, 20, FLOOR_Y, 18));
		Zombie edge = idle(scene.mob(EntityTypes.ZOMBIE, 30, FLOOR_Y, 18));
		Pig pig = idle(scene.mob(EntityTypes.PIG, 26, FLOOR_Y, 24));
		Zombie busy = idle(scene.mob(EntityTypes.ZOMBIE, 24, FLOOR_Y, 24));
		busy.setTarget(pig);
		Piglin brain = idle(scene.mob(EntityTypes.PIGLIN, 8, FLOOR_Y, 32));
		Zombie far = idle(scene.mob(EntityTypes.ZOMBIE, 38, FLOOR_Y, 18));
		Creeper creeper = idle(scene.mob(EntityTypes.CREEPER, 8, FLOOR_Y, 26));
		Zombie hurt = idle(scene.mob(EntityTypes.ZOMBIE, 8, FLOOR_Y, 10));
		hurt.setHealth(hurt.getMaxHealth() * (float) (RetreatGoal.HEALTH_FRACTION - 0.05));
		Enderman neutral = idle(scene.mob(EntityTypes.ENDERMAN, 14, FLOOR_Y, 24));
		Zombie tagged = idle(scene.mob(EntityTypes.ZOMBIE, 14, FLOOR_Y, 12));
		tagged.addTag(StellaMobs.NO_BEHAVIOR_TAG);

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenExecute(() -> {
				helper.assertTrue(busy.getTarget() == pig, "the busy monster does not hold its own target, so the swap is not tested");
				scene.clearStrangers(spider, STRANGER_RADIUS);
				List<Mob> unwebbed = SpiderSignal.alert(spider, player);
				helper.assertTrue(unwebbed.isEmpty() && !SIGNALS.containsKey(spider.getUUID()), "a spider signalled for a player that no web held");
				helper.getLevel().setBlockAndUpdate(player.blockPosition(), Blocks.COBWEB.defaultBlockState());
				helper.assertTrue(SpiderSignal.isWebbed(player), "the web at the player's feet does not count as webbed");
				List<Mob> accepted = SpiderSignal.alert(spider, player);
				List<Signal> signals = SIGNALS.getOrDefault(spider.getUUID(), List.of());
				String seen = "accepted=" + accepted.size() + " signals=" + signals.size();
				helper.assertTrue(signals.size() == 1, "the spider sent " + signals.size() + " signals");
				Signal signal = signals.getFirst();
				// Nearest first: 6, 12, 17, 22 blocks out.
				helper.assertTrue(
					accepted.equals(List.of(near, archer, busy, edge)) && signal.accepted().equals(accepted),
					"the signal took " + accepted + ", expected the four monsters inside 24 blocks nearest first"
				);
				helper.assertTrue(signal.declined().equals(List.of(brain)), "the signal declined " + signal.declined() + ", expected only the Brain driven piglin");
				helper.assertTrue(signal.target() == player, "the signal named " + signal.target());
				for (Mob ally : accepted) {
					helper.assertTrue(ally.getTarget() == player, ally + " did not take the webbed player");
				}

				for (Mob refused : List.of(far, creeper, hurt, neutral, tagged, brain)) {
					helper.assertTrue(refused.getTarget() != player, refused + " took the target that it must refuse");
				}

				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.alliesTakeWebbedTarget RESULT {}", TEST, seen);
				scene.end();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void signalCapsAtSix(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Spider spider = idle(scene.mob(EntityTypes.SPIDER, SPIDER_X, FLOOR_Y, SPIDER_Z));
		List<Zombie> eligible = new ArrayList<>();
		for (int gap : CAP_GAPS) {
			eligible.add(idle(scene.mob(EntityTypes.ZOMBIE, SPIDER_X + gap, FLOOR_Y, SPIDER_Z)));
		}

		int[] signalTick = {-1};

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenExecute(() -> {
				helper.getLevel().setBlockAndUpdate(player.blockPosition(), Blocks.COBWEB.defaultBlockState());
				signalTick[0] = spider.tickCount;
				scene.clearStrangers(spider, STRANGER_RADIUS);
				List<Mob> accepted = SpiderSignal.alert(spider, player);
				helper.assertTrue(
					accepted.equals(List.copyOf(eligible.subList(0, SpiderSignal.CAP))),
					"the signal took " + accepted.size() + " monsters, expected the " + SpiderSignal.CAP + " nearest in order"
				);
				for (Zombie left : eligible.subList(SpiderSignal.CAP, eligible.size())) {
					helper.assertTrue(left.getTarget() == null, left + " took the target past the cap");
				}

				List<Mob> again = SpiderSignal.alert(spider, player);
				helper.assertTrue(again.isEmpty(), "the spider signalled again inside its cooldown");
				helper.assertTrue(eligible.getLast().getTarget() == null, "a monster took the target inside the cooldown");
			})
			.thenWaitUntil(() -> helper.assertTrue(
				spider.tickCount - signalTick[0] >= SpiderSignal.COOLDOWN_TICKS, "the cooldown has not run out yet"
			))
			.thenExecute(() -> {
				scene.clearStrangers(spider, STRANGER_RADIUS);
				List<Mob> later = SpiderSignal.alert(spider, player);
				String seen = "afterCooldown=" + later.size() + " spiderTick=" + spider.tickCount;
				helper.assertTrue(later.size() == eligible.size() - SpiderSignal.CAP, "the second signal took " + later.size() + " monsters: " + seen);
				helper.assertTrue(
					eligible.getLast().getTarget() == player && eligible.get(eligible.size() - 2).getTarget() == player,
					"the monsters past the cap never took the target after the cooldown: " + seen
				);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.signalCapsAtSix RESULT {}", TEST, seen);
				scene.end();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void webCastSignals(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Spider spider = idle(scene.mob(EntityTypes.SPIDER, SPIDER_X, FLOOR_Y, SPIDER_Z));
		Zombie ally = idle(scene.mob(EntityTypes.ZOMBIE, 14, FLOOR_Y, 18));
		Elite.mark(spider, GearTier.IRON, 1.0);
		spider.setTarget(player);
		WebCastGoal cast = goalOf(spider, WebCastGoal.class);

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenExecute(() -> {
				scene.clearStrangers(spider, STRANGER_RADIUS);
				helper.assertTrue(cast.canUse(), "the elite spider does not want to cast at the player");
				cast.start();
				List<Signal> signals = SIGNALS.getOrDefault(spider.getUUID(), List.of());
				String seen = "signals=" + signals.size() + " allyTarget=" + ally.getTarget();
				helper.assertTrue(signals.size() == 1 && signals.getFirst().accepted().contains(ally), "the web cast sent no signal: " + seen);
				helper.assertTrue(ally.getTarget() == player, "the ally did not take the webbed player: " + seen);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.webCastSignals RESULT {}", TEST, seen);
				scene.end();
			})
			.thenSucceed();
	}

	@SuppressWarnings("removal")
	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void creativePlayerGetsNoSignal(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer creative = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(PLAYER_X, FLOOR_Y, PLAYER_Z));
		creative.setPos(at.x, at.y, at.z);
		Spider spider = idle(scene.mob(EntityTypes.SPIDER, SPIDER_X, FLOOR_Y, SPIDER_Z));
		Zombie ally = idle(scene.mob(EntityTypes.ZOMBIE, 14, FLOOR_Y, 18));

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenExecute(() -> {
				helper.getLevel().setBlockAndUpdate(creative.blockPosition(), Blocks.COBWEB.defaultBlockState());
				helper.assertTrue(creative.isCreative(), "the control player is not in creative mode");
				helper.assertTrue(SpiderSignal.isWebbed(creative), "the web at the creative player's feet does not count as webbed");
				List<Mob> accepted = SpiderSignal.alert(spider, creative);
				helper.assertTrue(accepted.isEmpty() && ally.getTarget() == null, "a creative player drew a signal");
				helper.assertTrue(!SIGNALS.containsKey(spider.getUUID()), "a creative player drew a recorded signal");
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.creativePlayerGetsNoSignal RESULT accepted=0", TEST);
				scene.end();
			})
			.thenSucceed();
	}

	private static <M extends Mob> M idle(final M mob) {
		mob.setNoAi(true);
		return mob;
	}

	private static <G extends Goal> G goalOf(final Mob mob, final Class<G> type) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (type.isInstance(wrapped.getGoal())) {
				return type.cast(wrapped.getGoal());
			}
		}

		throw new IllegalStateException(mob + " has no " + type.getSimpleName());
	}
}
