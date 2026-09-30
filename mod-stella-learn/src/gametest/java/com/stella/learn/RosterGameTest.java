package com.stella.learn;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Bot;
import com.stella.learn.gym.BotStats;
import com.stella.learn.gym.GymConfig;
import com.stella.learn.gym.GymEpisode;
import com.stella.learn.gym.Terrain;
import com.stella.learn.gym.brains.Brains;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Each roster brain, against a skeleton that stands still, does the action that names its style. */
public class RosterGameTest {
	private static final int TICKS = 200;
	private static final int SPACING = 40;
	private static final int COLUMNS = 6;
	private static final int BASE_INDEX = 900;
	private static final double APART = 6.0;
	/** Health that a bot cannot take down in the test, so the target stays to be hit. */
	private static final double SPONGE_HEALTH = 100_000.0;

	/** One arena to run: a brain, on a stage and seed, with a label for the report. */
	private record Scenario(String label, String brain, long seed, int stage, boolean place) {
	}

	private record Live(Scenario scenario, Arena arena, GymEpisode episode, Mob mob, Bot bot) {
	}

	private static GymConfig config() {
		return new GymConfig(0, 1, false, 2, 100_000, 1L, 20, Terrain.A, false, false, "none");
	}

	private static List<Scenario> scenarios() {
		List<Scenario> list = new ArrayList<>();
		for (int i = 0; i < Brains.COUNT; i++) {
			list.add(new Scenario(Brains.name(i), Brains.name(i), 11 + i, Terrain.A, true));
		}

		// A wall with a doorway for the holder, a ledge for the shooter, and two more draws of the random mix.
		list.add(new Scenario("DoorwayHolder@wall", "DoorwayHolder", 9, Terrain.RELEASE, false));
		list.add(new Scenario("HighGroundShooter@ledge", "HighGroundShooter", 4, Terrain.RELEASE, false));
		list.add(new Scenario("RandomMix#2", "RandomMix", 31, Terrain.A, true));
		list.add(new Scenario("RandomMix#3", "RandomMix", 47, Terrain.A, true));
		return list;
	}

	@GameTest(maxTicks = 700, padding = LearnArena.PADDING)
	public void everyBrainActs(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos start = helper.absolutePos(new BlockPos(0, 20, 0));
		BlockPos base = new BlockPos((start.getX() & ~15) + 8, start.getY(), (start.getZ() & ~15) + 8);
		List<Scenario> scenarios = scenarios();
		List<Live> live = new ArrayList<>();
		List<long[]> forced = new ArrayList<>();
		for (int i = 0; i < scenarios.size(); i++) {
			Scenario scenario = scenarios.get(i);
			BlockPos origin = base.offset((i % COLUMNS) * SPACING, 0, (i / COLUMNS) * SPACING);
			for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + Arena.OPEN) >> 4; cx++) {
				for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + Arena.OPEN) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					forced.add(new long[] {cx, cz});
				}
			}

			Arena arena = new Arena(BASE_INDEX + i, level, origin);
			arena.build();
			GymEpisode episode = new GymEpisode(arena, config());
			episode.start(scenario.seed(), Brains.indexOf(scenario.brain()), scenario.stage());
			Mob mob = episode.mob();
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SPONGE_HEALTH);
			mob.setHealth((float) SPONGE_HEALTH);
			if (scenario.place()) {
				placeBot(episode, mob);
			}

			episode.forceLive();
			live.add(new Live(scenario, arena, episode, mob, episode.bot()));
		}

		Runnable release = () -> {
			for (Live one : live) {
				one.bot().remove();
			}

			for (long[] chunk : forced) {
				level.setChunkForced((int) chunk[0], (int) chunk[1], false);
			}
		};
		helper.runBeforeTestEnd(release);
		List<net.minecraft.world.entity.Entity> watched = new ArrayList<>();
		for (Live one : live) {
			watched.add(one.bot().player());
			watched.add(one.mob());
		}

		LearnArena.whenReady(helper, watched, () -> {
			long from = live.get(0).bot().ticks();
			LearnArena.pollUntil(helper, () -> live.get(0).bot().ticks() - from >= TICKS, TICKS + 100, () -> {
				List<String> failures = new ArrayList<>();
				try {
					for (Live one : live) {
						String problem = check(one);
						StellaLearn.LOGGER.info("[BEHAVIOR] test=everyBrainActs RESULT scenario={} ok={} active={} {}", one.scenario().label(), problem == null,
							one.episode().activeBrains(), one.bot().stats().summary());
						if (problem != null) {
							failures.add(one.scenario().label() + ": " + problem);
						}
					}
				} finally {
					release.run();
				}

				helper.assertTrue(failures.isEmpty(), failures.size() + " brains missed their signature: " + failures);
				helper.succeed();
			}, () -> "the bots did not tick " + TICKS + " times");
		});
	}

	/** Puts the bot APART blocks from the still archer, on the side with room. */
	private static void placeBot(final GymEpisode episode, final Mob mob) {
		Arena arena = episode.arena();
		double relative = mob.getX() - arena.origin().getX();
		double x = relative <= Arena.OPEN / 2.0 ? mob.getX() + APART : mob.getX() - APART;
		Vec3 at = new Vec3(x, mob.getY(), mob.getZ());
		episode.bot().reset(at, Bot.yawTo(at, mob.position()));
	}

	/** Null when the brain did its signature action, else what is missing. */
	private static String check(final Live one) {
		BotStats stats = one.bot().stats();
		Mob mob = one.mob();
		BlockPos origin = one.arena().origin();
		return switch (one.scenario().label()) {
			case "CritRusher" -> stats.fallingAttacks() >= 1 ? null : "no attack while falling: " + stats.summary();
			case "SwordSweeper" -> stats.fullStrengthAttacks() >= 1 ? null : "no full strength attack: " + stats.summary();
			case "AxeBreaker" -> stats.axeHeldTicks() >= 1 && stats.axeSwung() ? null : "no axe held and swung: " + stats.summary();
			case "ShieldTurtle" -> stats.maxBlockingRun() >= 20 ? null : "shield up for " + stats.maxBlockingRun() + " ticks";
			case "StrafeDuelist" -> stats.maxStrafeRun() >= 40 ? null : "strafed " + stats.maxStrafeRun() + " ticks in a row";
			case "BowKiter" -> stats.retreatingReleases() >= 1 ? null : "no release while moving away: " + stats.summary();
			case "CrossbowSniper" -> stats.crossbowArrows() >= 1 ? null : "no crossbow shot: " + stats.summary();
			case "HighGroundShooter", "HighGroundShooter@ledge" -> stats.highReleases() >= 1 ? null : "no release from 3 blocks up, best " + stats.maxReleaseHeight();
			case "PillarUp" -> pillared(stats) ? null : "fewer than 3 blocks under itself: " + stats.placed();
			case "BlockTrapper" -> nextToMob(stats, mob) ? null : "no block next to the mob: " + stats.placed();
			case "DoorwayHolder" -> stats.maxCellRun() >= 60 ? null : "stood in a cell for " + stats.maxCellRun() + " ticks";
			case "DoorwayHolder@wall" -> inDoorway(one, stats, origin) ? null : "not 60 ticks in a doorway: cell " + stats.longestCell() + " run " + stats.maxCellRun();
			case "Burrower" -> stats.broken().size() >= 2 && stats.bowArrows() >= 1 ? null : "hole " + stats.broken().size() + " deep, arrows " + stats.bowArrows();
			case "Bunker" -> bunkered(stats) ? null : "no hole with a roof: broken " + stats.broken() + " placed " + stats.placed();
			case "Tunneler" -> sideways(stats) >= 3 ? null : "sideways blocks " + sideways(stats);
			case "PotionHealer" -> stats.potions() >= 1 ? null : "no potion drunk";
			case "ArmorTank" -> tank(one) && stats.attacks() >= 1 ? null : "not in diamond armor, or no attack: " + stats.summary();
			case "PearlEscaper" -> stats.pearls() >= 1 ? null : "no pearl thrown";
			case "StyleSwitcher" -> one.episode().activeBrains().size() >= 2 ? null : "one style only: " + one.episode().activeBrains();
			case "RandomMix", "RandomMix#2", "RandomMix#3" -> one.episode().activeBrains().size() == 1 && !one.episode().activeBrains().contains("RandomMix")
				? null : "no delegate: " + one.episode().activeBrains();
			default -> "no check for " + one.scenario().label();
		};
	}

	private static boolean pillared(final BotStats stats) {
		List<BlockPos> placed = stats.placed();
		if (placed.size() < 3) {
			return false;
		}

		for (BlockPos pos : placed) {
			if (pos.getX() != placed.get(0).getX() || pos.getZ() != placed.get(0).getZ()) {
				return false;
			}
		}

		return true;
	}

	private static boolean nextToMob(final BotStats stats, final Mob mob) {
		BlockPos at = mob.blockPosition();
		for (BlockPos pos : stats.placed()) {
			if (Math.abs(pos.getX() - at.getX()) + Math.abs(pos.getZ() - at.getZ()) == 1 && pos.getY() == at.getY()) {
				return true;
			}
		}

		return false;
	}

	private static boolean inDoorway(final Live one, final BotStats stats, final BlockPos origin) {
		if (stats.maxCellRun() < 60 || one.arena().terrain().doorways().isEmpty()) {
			return false;
		}

		BlockPos cell = stats.longestCell();
		for (BlockPos door : one.arena().terrain().doorways()) {
			if (Math.abs(cell.getX() - (origin.getX() + door.getX())) + Math.abs(cell.getZ() - (origin.getZ() + door.getZ())) <= 1) {
				return true;
			}
		}

		return false;
	}

	private static boolean bunkered(final BotStats stats) {
		List<BlockPos> broken = stats.broken();
		if (broken.size() < 3 || stats.placed().isEmpty()) {
			return false;
		}

		// The roof goes where the first block of the shaft was.
		return stats.placed().get(0).equals(broken.get(0));
	}

	private static int sideways(final BotStats stats) {
		int count = 0;
		for (int i = 0; i < stats.broken().size(); i++) {
			BlockPos block = stats.broken().get(i);
			BlockPos from = stats.brokenFrom().get(i);
			if (block.getY() == from.getY() && Math.abs(block.getX() - from.getX()) + Math.abs(block.getZ() - from.getZ()) == 1) {
				count++;
			}
		}

		return count;
	}

	private static boolean tank(final Live one) {
		Set<Boolean> pieces = new HashSet<>();
		var player = one.bot().player();
		pieces.add(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET));
		pieces.add(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE));
		pieces.add(player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS));
		pieces.add(player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS));
		return pieces.size() == 1 && pieces.contains(true);
	}
}
