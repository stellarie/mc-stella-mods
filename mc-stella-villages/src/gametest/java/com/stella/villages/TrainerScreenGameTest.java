package com.stella.villages;

import com.stella.villages.mission.RpgJobs;
import com.stella.villages.mission.TrainerMenu;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The job menu tells a player what each job does, which stat bonuses it gives, and which innate skill it grants, in
 * lines of at most 24 characters. The numbers repeat the rules of the rpg mod and the spells mod, so a drift in
 * either shows here as a menu that no longer says what the game does.
 */
public final class TrainerScreenGameTest {
	private static final int LINE = DescriptionLines.MAX_CHARS;

	private record Expected(String job, String main, String second, String health, String innate, List<String> facts) {
	}

	private static final List<Expected> JOBS = List.of(
		new Expected("archer", "DEX", "AGI", "1.0", "Falcon Guard", List.of("falcon", "35% on attack (+1% per 5 LUK)", "3 times", "every 2 s once idle 5 s", "Average bow roll", "25% XP")),
		new Expected("fighter", "STR", "VIT", "1.3", "Double Strike", List.of("25%", "hit twice", "crit", "Nausea", "0.5 seconds")),
		new Expected("mage", "INT", "DEX", "0.85", "Arcane Ward", List.of("MATK/100 x 10% max health", "15 s", "On break", "3 blocks", "MATK/100 x 5% max health")),
		new Expected("priest", "VIT", "INT", "1.1", "Sanctuary", List.of("3 blocks", "1.5%", "per second", "hostile"))
	);

	/** A job share that answers no job to everyone, unless another case has already put its own. */
	private static boolean installShare() {
		if (FabricLoader.getInstance().getObjectShare().get(RpgJobs.SHARE_KEY) != null) {
			return false;
		}
		Function<ServerPlayer, String> get = player -> RpgJobs.NONE;
		FabricLoader.getInstance().getObjectShare().put(RpgJobs.SHARE_KEY, Map.of("get", get));
		return true;
	}

	private static Mob trainer(final GameTestHelper helper) {
		Mob trainer = helper.spawn(EntityTypes.PILLAGER, 4, 2, 4);
		trainer.setNoAi(true);
		trainer.setPersistenceRequired();
		PatrolState.attach(trainer, UUID.randomUUID(), PatrolState.Role.TRAINER);
		return trainer;
	}

	private record Opened(ServerPlayer player, TrainerMenu menu) {
	}

	private static Opened open(final GameTestHelper helper, final Mob trainer, final int level) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = trainer.position().add(1.0, 0.0, 0.0);
		player.connection.teleport(at.x, at.y, at.z, 0.0F, 0.0F);
		if (player.level() instanceof ServerLevel world) {
			world.getChunkSource().move(player);
		}
		player.experienceLevel = level;
		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, trainer, null);
		helper.assertTrue(result == InteractionResult.SUCCESS && player.containerMenu instanceof TrainerMenu,
			"the trainer opened " + player.containerMenu + " for a player of level " + level);
		return new Opened(player, (TrainerMenu) player.containerMenu);
	}

	private static List<String> lore(final ItemStack icon) {
		List<String> lines = new ArrayList<>();
		for (Component line : icon.get(DataComponents.LORE).lines()) {
			lines.add(line.getString());
		}
		return lines;
	}

	/** What is wrong with the Requires and Desc blocks of an icon: a block starts at a Requires line and holds a Desc line next. */
	private static List<String> blockProblems(final ItemStack icon) {
		List<String> problems = new ArrayList<>();
		List<String> run = new ArrayList<>();
		List<Component> lore = icon.get(DataComponents.LORE).lines();
		for (int i = 0; i <= lore.size(); i++) {
			boolean block = i < lore.size() && DescriptionLines.isBlockLine(lore.get(i));
			String text = i < lore.size() ? lore.get(i).getString() : "";
			if (!block || text.startsWith("Requires:")) {
				checkBlock(run, problems);
				run = new ArrayList<>();
			}
			if (block) {
				run.add(text);
			}
		}
		return problems;
	}

	private static void checkBlock(final List<String> block, final List<String> problems) {
		if (block.isEmpty()) {
			return;
		}
		int desc = -1;
		for (int i = 0; i < block.size() && desc < 0; i++) {
			desc = block.get(i).startsWith("Desc:") ? i : desc;
		}
		if (!block.get(0).startsWith("Requires:")) {
			problems.add("a block starts with " + block);
		} else if (desc < 1) {
			problems.add("a block has no Desc line after Requires: " + block);
		}
		if (block.size() > DescriptionLines.MAX_BLOCK_LINES) {
			problems.add("a block of " + block.size() + " lines: " + block);
		}
	}

	@GameTest(maxTicks = 40, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void eachJobShowsItsDescriptionBonusesAndInnateInShortLines(final GameTestHelper helper) {
		boolean mine = installShare();
		try {
			Mob trainer = trainer(helper);
			Opened opened = open(helper, trainer, RpgJobs.MIN_LEVEL);
			TrainerMenu menu = opened.menu();
			List<String> descriptions = new ArrayList<>();
			for (int i = 0; i < JOBS.size(); i++) {
				Expected job = JOBS.get(i);
				ItemStack icon = menu.getContainer().getItem(TrainerMenu.JOB_SLOTS[i]);
				String name = icon.get(DataComponents.ITEM_NAME).getString();
				List<String> lines = lore(icon);
				String all = String.join(" ", lines);
				helper.assertTrue(RpgJobs.JOBS.get(i).equals(job.job()), "slot " + i + " offers " + RpgJobs.JOBS.get(i) + ", not " + job.job());
				helper.assertTrue(name.length() <= LINE, "the title '" + name + "' is " + name.length() + " characters");
				for (String line : lines) {
					helper.assertTrue(line.length() <= LINE, job.job() + " shows a line of " + line.length() + " characters: '" + line + "'");
				}
				helper.assertTrue(!lines.isEmpty() && !lines.getFirst().isBlank(), job.job() + " opens with no description");
				helper.assertTrue(lines.getFirst().equals("Requires: level " + RpgJobs.MIN_LEVEL),
					job.job() + " opens with '" + lines.getFirst() + "', not its Requires line");
				descriptions.add(lines.stream().filter(line -> line.startsWith("Desc:")).findFirst().orElse(""));
				String title = Character.toUpperCase(job.job().charAt(0)) + job.job().substring(1);
				helper.assertTrue(all.contains("Requires: " + title + " job Desc: " + job.innate() + ":"), job.job() + " has no innate block: " + lines);
				List<String> problems = blockProblems(icon);
				helper.assertTrue(problems.isEmpty(), job.job() + " has a block out of format: " + problems);
				String[] needs = {
					"+2 " + job.main(), "+1 " + job.second(), "per level", "Max health x" + job.health(), "Trial: +5 " + job.main()
				};
				for (String need : needs) {
					helper.assertTrue(all.contains(need), job.job() + " does not say '" + need + "': " + lines);
				}
				for (String fact : job.facts()) {
					helper.assertTrue(all.contains(fact), job.job() + " innate does not say '" + fact + "': " + lines);
				}
				for (Expected other : JOBS) {
					helper.assertTrue(other == job || !all.contains(other.innate()), job.job() + " shows the innate of " + other.job());
				}
				PatrolTestSupport.result("eachJobShowsItsDescriptionBonusesAndInnateInShortLines",
					job.job() + " name='" + name + "' lines=" + lines.size() + " longest=" + lines.stream().mapToInt(String::length).max().orElse(0));
			}
			helper.assertTrue(descriptions.stream().distinct().count() == JOBS.size(), "two jobs share a description: " + descriptions);
			opened.player().closeContainer();
			opened.player().discard();
			trainer.discard();
		} finally {
			if (mine) {
				FabricLoader.getInstance().getObjectShare().remove(RpgJobs.SHARE_KEY);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theTrialButtonShowsItsRequirementAndRewardInShortLines(final GameTestHelper helper) {
		ItemStack icon = TrainerMenu.trialIcon(RpgJobs.MIN_LEVEL);
		List<String> lines = lore(icon);
		String all = String.join(" ", lines);
		PatrolTestSupport.result("theTrialButtonShowsItsRequirementAndRewardInShortLines", "lines=" + lines);
		for (String line : lines) {
			helper.assertTrue(line.length() <= LINE, "the trial shows a line of " + line.length() + " characters: '" + line + "'");
		}
		helper.assertTrue(lines.getFirst().equals("Requires: job, level " + RpgJobs.MIN_LEVEL), "the trial opens with '" + lines.getFirst() + "'");
		helper.assertTrue(blockProblems(icon).isEmpty(), "the trial block is out of format: " + blockProblems(icon));
		for (String fact : List.of("Hunt a level " + RpgJobs.MIN_LEVEL + " boss", "128 to 256 blocks", "+5 to your main stat, +1 to the others", "Click to accept")) {
			helper.assertTrue(all.contains(fact), "the trial does not say '" + fact + "': " + lines);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyOtherLineOfTheMenuFitsToo(final GameTestHelper helper) {
		boolean mine = installShare();
		try {
			Mob trainer = trainer(helper);
			Opened open = open(helper, trainer, RpgJobs.MIN_LEVEL);
			Opened closed = open(helper, trainer, RpgJobs.MIN_LEVEL - 1);
			List<String> shown = new ArrayList<>();
			for (Opened each : List.of(open, closed)) {
				ItemStack info = each.menu().getContainer().getItem(TrainerMenu.INFO_SLOT);
				shown.add(info.get(DataComponents.ITEM_NAME).getString());
				shown.addAll(lore(info));
			}
			PatrolTestSupport.result("everyOtherLineOfTheMenuFitsToo", "shown=" + shown);
			for (String line : shown) {
				helper.assertTrue(line.length() <= LINE, "the info icon shows a line of " + line.length() + " characters: '" + line + "'");
			}
			helper.assertTrue(shown.stream().anyMatch(line -> line.contains("level " + RpgJobs.MIN_LEVEL)),
				"the refusal does not say which level the job needs: " + shown);
			open.player().closeContainer();
			closed.player().closeContainer();
			open.player().discard();
			closed.player().discard();
			trainer.discard();
		} finally {
			if (mine) {
				FabricLoader.getInstance().getObjectShare().remove(RpgJobs.SHARE_KEY);
			}
		}
		helper.succeed();
	}
}
