package com.stella.learn;

import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.Contract;
import com.stella.learn.model.LearnedArcherGoal;
import com.stella.learn.model.ModelDriver;
import com.stella.learn.model.PolicyFile;
import com.stella.mobs.StellaMobs;
import java.io.IOException;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What the model game tests share. Tests in one batch run side by side on one server, and the loaded policy is global,
 * so a test takes the policy with {@link #acquire} and the last one to finish puts the default back.
 */
final class ModelTests {
	/** The batch that holds the tests which need a valid policy and no other global setting. */
	static final String ENV = "mod-stella-learn-test:model";
	static final String ENV_MISSING = "mod-stella-learn-test:model_missing";
	static final String ENV_CONTRACT = "mod-stella-learn-test:model_contract";
	static final String ENV_SWITCH = "mod-stella-learn-test:model_switch";
	static final String ENV_PERF = "mod-stella-learn-test:model_perf";
	static final int LONG = 500;
	private static int users;

	private ModelTests() {
	}

	static PolicyFile policy(final String name) {
		try {
			return PolicyFile.loadResource("policies/test/" + name + ".bin", Contract.archer());
		} catch (IOException | PolicyFile.Rejected exception) {
			throw new IllegalStateException("test model " + name + " did not load: " + exception.getMessage(), exception);
		}
	}

	/**
	 * Loads the `forward` test model as the global policy for a test that needs a valid one. `runBeforeTestEnd` runs only
	 * at the timeout, not when a test succeeds, so a test ends through {@link #pass}, which lets go of the policy.
	 */
	static void acquire(final GameTestHelper helper) {
		users++;
		if (!ModelDriver.STATUS_OK.equals(ModelDriver.status())) {
			ModelDriver.useTestPolicy(policy("forward"));
		}
	}

	/** Lets go of the policy the last user took, and passes the test. */
	static void pass(final GameTestHelper helper) {
		if (--users <= 0) {
			users = 0;
			ModelDriver.useNoPolicy();
		}

		helper.succeed();
	}

	/** A skeleton on the floor, kept out of the spawn rolls so it holds exactly what the test gives it. */
	static Mob skeleton(final GameTestHelper helper, final EntityType<? extends Mob> type, final double x, final double z, final boolean bow) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		if (bow) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}

		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	static Mob archer(final GameTestHelper helper, final double x, final double z) {
		return skeleton(helper, EntityTypes.SKELETON, x, z, true);
	}

	/**
	 * Keeps every mob on the player and runs `each` once a tick. A test registers one per-tick callback, and only while it
	 * sets up: `onEachTick` books an entry for every tick left, and booking them inside a delayed callback grows the test's
	 * task map while the game is iterating it, which crashes the server.
	 */
	static void everyTick(final GameTestHelper helper, final ServerPlayer player, final Runnable each, final Mob... mobs) {
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			for (Mob mob : mobs) {
				mob.setTarget(player);
			}

			each.run();
		});
	}

	static boolean has(final Mob mob, final Class<?> goal) {
		return mob.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> goal.isInstance(wrapped.getGoal()));
	}

	static List<String> goalNames(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream().map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority()).sorted().toList();
	}

	static boolean tagged(final Mob mob) {
		return mob.entityTags().contains(StellaMobs.MODEL_DRIVEN_TAG);
	}

	static boolean running(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof LearnedArcherGoal && wrapped.isRunning());
	}

	/** The `learnModel` lines of one verb for one mob, plus any extra text each must hold. */
	static List<String> lines(final Mob mob, final String verb, final String... more) {
		String[] needles = new String[more.length + 2];
		needles[0] = "learnModel " + verb;
		needles[1] = "id=" + mob.getUUID();
		System.arraycopy(more, 0, needles, 2, more.length);
		return LearnTestInit.linesWith(needles);
	}

	/** The value after `key=` in a log line, or null. */
	static String field(final String line, final String key) {
		int start = line.indexOf(" " + key + "=");
		if (start < 0) {
			return null;
		}

		int from = start + key.length() + 2;
		int end = line.indexOf(' ', from);
		return line.substring(from, end < 0 ? line.length() : end);
	}

	static ArcherAction bow(final ArcherAction.Bow bow) {
		return new ArcherAction(0.0F, 0.0F, 0.0F, 0.0F, false, bow);
	}

	/** A source that answers each decision by its index, counted from the first one this source sees. */
	static ActionSource script(final IntFunction<ArcherAction> byDecision) {
		int[] decisions = {0};
		return (mob, observation) -> byDecision.apply(decisions[0]++);
	}

	/** Every arrow this mob owns that is in the world or was seen by the last scan, oldest first. */
	static void collectArrows(final GameTestHelper helper, final Mob owner, final List<AbstractArrow> into) {
		for (AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(AbstractArrow.class, owner.getBoundingBox().inflate(96.0), a -> a.getOwner() == owner)) {
			if (!into.contains(arrow)) {
				into.add(arrow);
			}
		}
	}
}
