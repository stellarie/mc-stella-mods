package com.stella.mobs;

import com.stella.mobs.behavior.BloodScent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * A player at low health draws the idle monsters around them. Every monster here has its AI off, so none of them finds the
 * player alone, and a target that one holds came from the scent. The scent reports each event through the recorder, keyed by
 * the player, so concurrent cases never mix. A player of these cases stays in the server after the case ends, so each case
 * restores the full health before it leaves, and the scent finds nothing to draw there later.
 */
public class BloodScentGameTest {
	private static final String TEST = "bloodScent";
	private static final int MAX_TICKS = StanceGameTest.READY_TICKS + 500;
	private static final double PLAYER_X = 6.0;
	private static final double ROW_Z = 28.0;
	private static final double NEAR_BLOCKS = 20.0;
	private static final double EDGE_BLOCKS = 28.0;
	private static final double FAR_BLOCKS = 40.0;
	private static final double LOW_SHARE = 0.08;
	private static final double HIGH_SHARE = 0.90;
	private static final int DRAW_WITHIN_TICKS = 40;
	private static final int WATCH_TICKS = 60;
	private static final int COOLDOWN_SLACK_TICKS = 2 * BloodScent.INTERVAL_TICKS;

	/** One scent event, as the recorder saw it. */
	private record Event(int playerTick, List<Mob> drawn, List<Mob> declined, String message) {
	}

	private static final Map<UUID, List<Event>> EVENTS = new ConcurrentHashMap<>();

	static {
		BloodScent.setRecorder((player, drawn, declined, message) ->
			EVENTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>())
				.add(new Event(player.tickCount, List.copyOf(drawn), List.copyOf(declined), message)));
	}

	/** The player and the monsters of one case. */
	private static final class Scene {
		private volatile ServerPlayer player;
		private volatile Zombie near;
		private volatile Zombie edge;
		private volatile Zombie far;
		private volatile Creeper creeper;
		private int ticks;
		private boolean done;

		private boolean ready() {
			return this.creeper != null && !this.done;
		}

		private List<Event> events() {
			return EVENTS.getOrDefault(this.player.getUUID(), List.of());
		}

		private String describe() {
			return "events=" + this.events().stream().map(event -> "tick" + event.playerTick() + ":drawn" + event.drawn().size() + ":declined" + event.declined().size()).toList()
				+ " nearTarget=" + this.near.getTarget() + " edgeTarget=" + this.edge.getTarget() + " farTarget=" + this.far.getTarget()
				+ " creeperTarget=" + this.creeper.getTarget() + " playerShare=" + String.format(Locale.ROOT, "%.2f", this.player.getHealth() / this.player.getMaxHealth());
		}
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void drawsNearbyMonsters(final GameTestHelper helper) {
		Scene scene = scene(helper, LOW_SHARE, false);
		helper.onEachTick(() -> {
			if (!hold(scene, LOW_SHARE) || scene.ticks < WATCH_TICKS) {
				return;
			}

			scene.done = true;
			List<Event> events = scene.events();
			String seen = scene.describe();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.drawsNearbyMonsters RESULT {}", TEST, seen);
			helper.assertTrue(events.size() == 1, "the scent fired " + events.size() + " times in " + WATCH_TICKS + " ticks: " + seen);
			Event event = events.getFirst();
			helper.assertTrue(
				event.drawn().equals(List.of(scene.near, scene.edge)),
				"the scent drew " + event.drawn() + ", expected the monsters at " + NEAR_BLOCKS + " and " + EDGE_BLOCKS + " blocks, nearest first: " + seen
			);
			helper.assertTrue(event.declined().isEmpty(), "the scent declined " + event.declined() + ": " + seen);
			helper.assertTrue(event.playerTick() <= DRAW_WITHIN_TICKS, "the scent came on player tick " + event.playerTick() + ", past " + DRAW_WITHIN_TICKS + ": " + seen);
			helper.assertTrue(BloodScent.WARNING.equals(event.message()), "the player got the line " + event.message() + ", not the warning: " + seen);
			helper.assertTrue(scene.near.getTarget() == scene.player, "the monster at " + NEAR_BLOCKS + " blocks did not take the player: " + seen);
			helper.assertTrue(scene.edge.getTarget() == scene.player, "the monster at " + EDGE_BLOCKS + " blocks did not take the player: " + seen);
			helper.assertTrue(scene.far.getTarget() != scene.player, "the monster at " + FAR_BLOCKS + " blocks took the player: " + seen);
			helper.assertTrue(scene.creeper.getTarget() != scene.player, "the creeper took the player: " + seen);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void healthyPlayerDrawsNothing(final GameTestHelper helper) {
		Scene scene = scene(helper, HIGH_SHARE, false);
		helper.onEachTick(() -> {
			if (!hold(scene, HIGH_SHARE) || scene.ticks < WATCH_TICKS) {
				return;
			}

			scene.done = true;
			String seen = scene.describe();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.healthyPlayerDrawsNothing RESULT {}", TEST, seen);
			helper.assertTrue(scene.events().isEmpty(), "a healthy player drew a scent: " + seen);
			helper.assertTrue(
				scene.near.getTarget() != scene.player && scene.edge.getTarget() != scene.player, "a monster took a healthy player: " + seen
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void creativePlayerDrawsNothing(final GameTestHelper helper) {
		Scene scene = scene(helper, LOW_SHARE, true);
		helper.onEachTick(() -> {
			if (!hold(scene, LOW_SHARE) || scene.ticks < WATCH_TICKS) {
				return;
			}

			scene.done = true;
			String seen = scene.describe();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.creativePlayerDrawsNothing RESULT creative={} {}", TEST, scene.player.isCreative(), seen);
			helper.assertTrue(scene.player.isCreative(), "the control player is not in creative mode: " + seen);
			helper.assertTrue(scene.events().isEmpty(), "a creative player drew a scent: " + seen);
			helper.assertTrue(scene.near.getTarget() != scene.player, "a monster took a creative player: " + seen);
			helper.succeed();
		});
	}

	/**
	 * A second scent waits out the cooldown. The case clears every monster's target after the first scent, so the
	 * monsters qualify again at once, and only the cooldown can hold the second scent back.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void cooldownHoldsTheSecondScent(final GameTestHelper helper) {
		Scene scene = scene(helper, LOW_SHARE, false);
		int[] firstTick = {-1};
		helper.onEachTick(() -> {
			if (!hold(scene, LOW_SHARE)) {
				return;
			}

			List<Event> events = scene.events();
			if (events.isEmpty()) {
				return;
			}

			if (firstTick[0] < 0) {
				firstTick[0] = events.getFirst().playerTick();
			}

			scene.near.setTarget(null);
			scene.edge.setTarget(null);
			int elapsed = scene.player.tickCount - firstTick[0];
			if (elapsed < BloodScent.COOLDOWN_TICKS - BloodScent.INTERVAL_TICKS) {
				helper.assertTrue(events.size() == 1, "the scent fired again after " + elapsed + " ticks, inside its cooldown: " + scene.describe());
				return;
			}

			if (events.size() >= 2) {
				scene.done = true;
				int gap = events.get(1).playerTick() - firstTick[0];
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.cooldownHoldsTheSecondScent RESULT gap={} cooldown={}", TEST, gap, BloodScent.COOLDOWN_TICKS);
				helper.assertTrue(gap >= BloodScent.COOLDOWN_TICKS, "the second scent came after " + gap + " ticks, inside the cooldown of " + BloodScent.COOLDOWN_TICKS);
				helper.assertTrue(gap <= BloodScent.COOLDOWN_TICKS + COOLDOWN_SLACK_TICKS, "the second scent came late, after " + gap + " ticks");
				helper.succeed();
			} else if (elapsed > BloodScent.COOLDOWN_TICKS + COOLDOWN_SLACK_TICKS) {
				scene.done = true;
				helper.fail("no second scent came " + elapsed + " ticks after the first: " + scene.describe());
			}
		});
	}

	private static Scene scene(final GameTestHelper helper, final double share, final boolean creative) {
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		helper.runBeforeTestEnd(() -> {
			if (scene.player != null) {
				scene.player.setHealth(scene.player.getMaxHealth());
			}

			arena.finish();
		});
		StanceGameTest.whenTicking(
			helper,
			List.of(
				new Vec3(PLAYER_X, 1.0, ROW_Z),
				new Vec3(PLAYER_X + EDGE_BLOCKS, 1.0, ROW_Z),
				new Vec3(PLAYER_X + FAR_BLOCKS, 1.0, ROW_Z),
				new Vec3(PLAYER_X, 1.0, ROW_Z - NEAR_BLOCKS)
			),
			() -> {
				scene.player = creative ? creativePlayer(helper, arena) : arena.survivalPlayer(PLAYER_X, ROW_Z);
				scene.player.setHealth((float) (share * scene.player.getMaxHealth()));
				scene.near = idle(arena.spawn(EntityTypes.ZOMBIE, PLAYER_X + NEAR_BLOCKS, ROW_Z, mob -> { }));
				scene.edge = idle(arena.spawn(EntityTypes.ZOMBIE, PLAYER_X + EDGE_BLOCKS, ROW_Z, mob -> { }));
				scene.far = idle(arena.spawn(EntityTypes.ZOMBIE, PLAYER_X + FAR_BLOCKS, ROW_Z, mob -> { }));
				scene.creeper = idle(arena.spawn(EntityTypes.CREEPER, PLAYER_X, ROW_Z - NEAR_BLOCKS, mob -> { }));
			}
		);
		return scene;
	}

	/** The mock player is creative and in the list. */
	@SuppressWarnings("removal")
	private static ServerPlayer creativePlayer(final GameTestHelper helper, final WideArena arena) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = arena.at(PLAYER_X, 1.0, ROW_Z);
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	/** Holds the player at its share. True once up. */
	private static boolean hold(final Scene scene, final double share) {
		if (!scene.ready()) {
			return false;
		}

		WideArena.keepValid(scene.player);
		scene.player.setHealth((float) (share * scene.player.getMaxHealth()));
		scene.ticks++;
		return true;
	}

	private static <M extends Mob> M idle(final M mob) {
		mob.setNoAi(true);
		return mob;
	}
}
