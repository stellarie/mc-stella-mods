package com.stella.spells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A spell tooltip shows what the viewer's own cast would do now. Each test casts the real spell on the server,
 * reads what it did, and compares that with the tooltip line built from the same viewer and stack.
 */
public class SpellTooltipGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.tooltip");
	private static final int EFFECT_LINE = 2;
	private static final int COOLDOWN_LINE = 3;
	private static final int DAMAGE_ARG = 0;
	private static final int HEAL_ARG = 1;
	private static final int DURATION_ARG = 3;
	/** The health scale the stand-in RPG mod divides by, as the real mod publishes it. */
	private static final double K = 15.0;
	/** The stand-in's power multiplier, so a tooltip that drops the power is wrong by this much. */
	private static final double POWER = 1.25;
	/** The stand-in's level plus INT, which a heal multiplies by its coefficient. */
	private static final double HEAL_BASIS = 40.0;
	private static final int CASTS = 20;
	private static final double HALF = 0.5;

	/** The RPG stand-in at a fixed MATK: its damage rolls between the bounds, in vanilla units, as the real one does. */
	private static final class FixedMatk implements RpgBridge.Stats {
		private final RandomSource random = RandomSource.create(20_261_001L);
		private final double cooldown;

		FixedMatk(final double cooldown) {
			this.cooldown = cooldown;
		}

		@Override
		public double power(final Player player) {
			return POWER;
		}

		@Override
		public double cooldown(final Player player) {
			return this.cooldown;
		}

		@Override
		public double spellDamage(final LivingEntity caster, final double skillPercent) {
			double roll = RpgSpellsStandIn.MATK_MIN + this.random.nextDouble() * (RpgSpellsStandIn.MATK_MAX - RpgSpellsStandIn.MATK_MIN);
			return roll * skillPercent / 100.0 / K;
		}

		@Override
		public double spellHeal(final LivingEntity caster, final double coefficient) {
			return HEAL_BASIS * coefficient / K;
		}

		@Override
		public double matkMin(final LivingEntity caster) {
			return RpgSpellsStandIn.matkMin(caster);
		}

		@Override
		public double matkMax(final LivingEntity caster) {
			return RpgSpellsStandIn.matkMax(caster);
		}
	}

	@GameTest
	public void everySpellHasNumbers(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellHasNumbers");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			ServerPlayer viewer = viewer(helper, players);
			Set<String> declared = new HashSet<>(SpellNumbers.paths());
			int shown = 0;
			for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
				SpellNumbers numbers = SpellNumbers.find(definition.id());
				helper.assertTrue(numbers != null, definition.id() + " declares no SpellNumbers");
				helper.assertTrue(declared.remove(definition.id().getPath()), definition.id() + " is declared twice");
				ItemStack book = BuiltInRegistries.ITEM.getValue(definition.id()).getDefaultInstance();
				List<Component> live = SpellTooltip.lines(viewer, book);
				int expected = numbers.isEmpty() ? 1 : 2;
				helper.assertTrue(live.size() == expected, definition.id() + " has " + live.size() + " live lines, not " + expected);
				if (!numbers.isEmpty()) {
					String text = live.get(0).getString();
					helper.assertTrue(live.get(0).getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals(SpellTooltip.liveEffectKey(definition)), definition.id() + " live line has the wrong key");
					helper.assertTrue(!text.equals(SpellTooltip.liveEffectKey(definition)) && !text.contains("%"),
						definition.id() + " live line is untranslated or has an unfilled slot: " + text);
					shown++;
				}
			}
			helper.assertTrue(declared.isEmpty(), "numbers declared for no spell: " + declared);
			LOG.info("[SPELLTEST] test=everySpellHasNumbers RESULT spells={} withLiveLine={}", SpellbookCatalog.DEFINITIONS.size(), shown);
		} finally {
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void matchesCast(final GameTestHelper helper) {
		SpellTestLog.track(helper, "matchesCast");
		List<ServerPlayer> players = new ArrayList<>();
		Object savedScale = FabricLoader.getInstance().getObjectShare().get(SummonSkeleton.HEALTH_SCALE_SHARE);
		try {
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, K);
			RpgBridge.setStats(new FixedMatk(1.0));
			ServerPlayer viewer = viewer(helper, players);
			ItemStack book = book("spark_shot");

			double percent = SpellNumbers.SPARK_SHOT.damage() * RpgBridge.SKILL_PERCENT_PER_FLAT;
			long expectedMin = Math.round(RpgSpellsStandIn.MATK_MIN * percent / 100.0 * POWER);
			long expectedMax = Math.round(RpgSpellsStandIn.MATK_MAX * percent / 100.0 * POWER);
			String damage = argument(SpellTooltip.lines(viewer, book).get(0), DAMAGE_ARG);
			double[] range = range(damage);
			LOG.info("[SPELLTEST] test=matchesCast RESULT tooltipDamage={} expected={}-{}", damage, expectedMin, expectedMax);
			helper.assertTrue(range[0] == expectedMin && range[1] == expectedMax,
				"the tooltip shows " + damage + ", not the stand-in bounds " + expectedMin + "-" + expectedMax);

			double lowest = Double.MAX_VALUE;
			double highest = 0.0;
			for (int i = 0; i < CASTS; i++) {
				double taken = castDamage(helper, players, book("spark_shot")) * K;
				lowest = Math.min(lowest, taken);
				highest = Math.max(highest, taken);
				helper.assertTrue(taken >= range[0] - HALF && taken <= range[1] + HALF,
					"real cast " + i + " dealt " + taken + " shown, outside the tooltip " + damage);
			}
			LOG.info("[SPELLTEST] test=matchesCast RESULT casts={} lowest={} highest={}", CASTS, lowest, highest);

			ServerPlayer patient = wounded(helper, players);
			float before = patient.getHealth();
			cast(helper, patient, book("mending_word"));
			double healed = (patient.getHealth() - before) * K;
			String heal = argument(SpellTooltip.lines(viewer, book("mending_word")).get(0), HEAL_ARG);
			LOG.info("[SPELLTEST] test=matchesCast RESULT tooltipHeal={} realHeal={}", heal, healed);
			helper.assertTrue(Math.abs(Double.parseDouble(heal) - healed) <= HALF, "the tooltip heals " + heal + " but the cast healed " + healed);
		} finally {
			RpgBridge.setStats(null);
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, savedScale == null ? Double.valueOf(1.0) : savedScale);
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void withoutRpg(final GameTestHelper helper) {
		SpellTestLog.track(helper, "withoutRpg");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			RpgBridge.setStats(null);
			helper.assertTrue(!RpgBridge.present(), "an RPG mod is answering in a harness that has none");
			ServerPlayer viewer = viewer(helper, players);
			ItemStack book = enchanted(helper, "spark_shot", "potency", 2);
			double power = SpellModifiers.resolve(book, ItemStack.EMPTY, ItemStack.EMPTY).powerMultiplier();
			helper.assertTrue(power > 1.0, "Potency did not raise the power: " + power);

			String damage = argument(SpellTooltip.lines(viewer, book).get(0), DAMAGE_ARG);
			double expected = SpellNumbers.SPARK_SHOT.damage() * power;
			helper.assertTrue(!damage.contains("-"), "a range shows with no RPG mod: " + damage);
			helper.assertTrue(Math.abs(Double.parseDouble(damage) - expected) <= 0.05 + 1.0e-9, "the tooltip shows " + damage + ", not the flat " + expected);
			double taken = castDamage(helper, players, book);
			LOG.info("[SPELLTEST] test=withoutRpg RESULT tooltip={} flatTimesPower={} realCast={}", damage, expected, taken);
			helper.assertTrue(Math.abs(taken - expected) < 1.0E-3, "the real cast dealt " + taken + ", not " + expected);
			helper.assertTrue(Math.abs(Double.parseDouble(damage) - taken) <= 0.05 + 1.0e-3, "the tooltip " + damage + " is not the real " + taken);

			ItemStack mending = enchanted(helper, "mending_word", "potency", 2);
			String heal = argument(SpellTooltip.lines(viewer, mending).get(0), HEAL_ARG);
			ServerPlayer patient = wounded(helper, players);
			float before = patient.getHealth();
			cast(helper, patient, mending);
			double healed = patient.getHealth() - before;
			helper.assertTrue(Math.abs(Double.parseDouble(heal) - healed) <= 0.05 + 1.0e-3, "the tooltip heals " + heal + " but the cast healed " + healed);
		} finally {
			RpgBridge.setStats(null);
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void cooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "cooldown");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			RpgBridge.setStats(new FixedMatk(HALF));
			ServerPlayer viewer = viewer(helper, players);
			ItemStack book = enchanted(helper, "spark_shot", "quickening", 2);
			List<Component> live = SpellTooltip.lines(viewer, book);
			String seconds = argument(live.get(1), 0);
			int ticks = (int) Math.round(Double.parseDouble(seconds) * 20.0);
			int base = SpellbookCatalog.get(Spellbooks.SPARK_SHOT_ID).baseCooldownTicks();
			helper.assertTrue(ticks != base, "the tooltip still shows the base cooldown " + base + " ticks");
			LOG.info("[SPELLTEST] test=cooldown RESULT tooltipSeconds={} ticks={} base={}", seconds, ticks, base);

			ServerPlayer caster = viewer(helper, players);
			try (SpellLogCapture capture = new SpellLogCapture()) {
				castDamage(helper, caster, book);
				helper.assertTrue(capture.contains("spell_id=" + Spellbooks.SPARK_SHOT_ID, "result=CAST", "cooldown_ticks=" + ticks),
					"a real cast did not start a " + ticks + "-tick cooldown, which the tooltip shows as " + seconds + " s");
				capture.echoOne("result=CAST", "cooldown_ticks=" + ticks);
			}
		} finally {
			RpgBridge.setStats(null);
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void scroll(final GameTestHelper helper) {
		SpellTestLog.track(helper, "scroll");
		List<ServerPlayer> players = new ArrayList<>();
		Object savedScale = FabricLoader.getInstance().getObjectShare().get(SummonSkeleton.HEALTH_SCALE_SHARE);
		try {
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, K);
			RpgBridge.setStats(new FixedMatk(1.0));
			ServerPlayer viewer = viewer(helper, players);
			SpellScrollItem scrollItem = Spellbooks.SCROLLS.stream()
				.filter(candidate -> candidate.definition().id().equals(Spellbooks.SPARK_SHOT_ID)).findFirst().orElseThrow();
			ItemStack scroll = scrollItem.getDefaultInstance();

			List<Component> live = SpellTooltip.lines(viewer, scroll);
			helper.assertTrue(live.size() == 1, "a scroll has " + live.size() + " live lines, not just its effect");
			String damage = argument(live.get(0), DAMAGE_ARG);
			String book = argument(SpellTooltip.lines(viewer, book("spark_shot")).get(0), DAMAGE_ARG);
			helper.assertTrue(damage.equals(book), "the scroll shows " + damage + " but the book shows " + book);

			ItemLore lore = scroll.get(DataComponents.LORE);
			List<Component> tooltip = new ArrayList<>(lore.lines());
			String cooldownBefore = tooltip.get(tooltip.size() - 1).getString();
			SpellTooltip.apply(viewer, scroll, tooltip);
			helper.assertTrue(argument(tooltip.get(EFFECT_LINE), DAMAGE_ARG).equals(damage), "the scroll tooltip kept its static effect line");
			helper.assertTrue(tooltip.size() == lore.lines().size() && tooltip.get(tooltip.size() - 1).getString().equals(cooldownBefore),
				"applying to a scroll changed a line besides the effect");

			double[] range = range(damage);
			ServerPlayer caster = viewer(helper, players);
			Mob zombie = zombie(helper);
			caster.setItemInHand(InteractionHand.MAIN_HAND, scroll);
			float before = zombie.getHealth();
			SpellCasting.CastResult result = SpellCasting.castScroll(caster, scrollItem, InteractionHand.MAIN_HAND);
			double taken = (before - zombie.getHealth()) * K;
			LOG.info("[SPELLTEST] test=scroll RESULT result={} tooltip={} realCast={}", result, damage, taken);
			helper.assertTrue(result == SpellCasting.CastResult.CAST, "the scroll did not cast: " + result);
			helper.assertTrue(taken >= range[0] - HALF && taken <= range[1] + HALF, "the scroll dealt " + taken + ", outside the tooltip " + damage);
		} finally {
			RpgBridge.setStats(null);
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, savedScale == null ? Double.valueOf(1.0) : savedScale);
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest
	public void noViewer(final GameTestHelper helper) {
		SpellTestLog.track(helper, "noViewer");
		ItemStack book = book("spark_shot");
		helper.assertTrue(SpellTooltip.lines(null, book).isEmpty(), "a tooltip with no viewer produced live lines");
		List<Component> lore = book.get(DataComponents.LORE).lines();
		List<Component> tooltip = new ArrayList<>(lore);
		SpellTooltip.apply(null, book, tooltip);
		helper.assertTrue(tooltip.equals(lore) && tooltip.get(EFFECT_LINE) == lore.get(EFFECT_LINE), "a tooltip with no viewer changed the static lore");
		helper.assertTrue(tooltip.get(EFFECT_LINE).getContents() instanceof TranslatableContents contents
			&& contents.getKey().equals("item.mod-stella-spells.spark_shot.effect"), "the static effect line is gone");
		helper.assertTrue(tooltip.get(COOLDOWN_LINE).getString().contains("3"), "the static cooldown line is gone");
		helper.succeed();
	}

	@GameTest
	public void replacesOnlyTheEffectAndCooldownLines(final GameTestHelper helper) {
		SpellTestLog.track(helper, "replacesOnlyTheEffectAndCooldownLines");
		List<ServerPlayer> players = new ArrayList<>();
		try {
			RpgBridge.setStats(null);
			ServerPlayer viewer = viewer(helper, players);
			ItemStack book = book("spark_shot");
			List<Component> lore = book.get(DataComponents.LORE).lines();
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("Spark Shot Spellbook"));
			tooltip.addAll(lore);
			tooltip.add(Component.literal("another mod's line"));
			SpellTooltip.apply(viewer, book, tooltip);
			helper.assertTrue(tooltip.size() == lore.size() + 2, "the tooltip changed length");
			helper.assertTrue(tooltip.get(0) != lore.get(0) && tooltip.get(0).getString().equals("Spark Shot Spellbook"), "the item name changed");
			helper.assertTrue(tooltip.get(tooltip.size() - 1).getString().equals("another mod's line"), "another mod's line changed");
			for (int i = 0; i < lore.size(); i++) {
				boolean live = i == EFFECT_LINE || i == COOLDOWN_LINE;
				helper.assertTrue((tooltip.get(i + 1) != lore.get(i)) == live,
					"lore line " + i + (live ? " was not replaced" : " was replaced but is not an effect or cooldown line"));
			}
			helper.assertTrue(tooltip.get(EFFECT_LINE + 1).getContents() instanceof TranslatableContents effect
				&& effect.getKey().equals("item.mod-stella-spells.spark_shot.effect_live"), "the effect line is not live");
			helper.assertTrue(tooltip.get(COOLDOWN_LINE + 1) != lore.get(COOLDOWN_LINE), "the cooldown line is not live");
		} finally {
			leave(helper, players);
		}
		helper.succeed();
	}

	@GameTest
	public void bridgeBindsTheMatkBounds(final GameTestHelper helper) {
		SpellTestLog.track(helper, "bridgeBindsTheMatkBounds");
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		try {
			RpgBridge.Stats bound = RpgBridge.find(RpgSpellsStandIn.class.getName());
			helper.assertTrue(bound.matkMin(caster) == RpgSpellsStandIn.MATK_MIN, "matkMin did not bind: " + bound.matkMin(caster));
			helper.assertTrue(bound.matkMax(caster) == RpgSpellsStandIn.MATK_MAX, "matkMax did not bind: " + bound.matkMax(caster));
			RpgBridge.Stats mismatched = RpgBridge.find(RpgSpellsMismatchedStandIn.class.getName());
			helper.assertTrue(Double.isNaN(mismatched.matkMin(caster)) && Double.isNaN(mismatched.matkMax(caster)), "a MATK bound of another shape still answered");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(caster);
		}
		helper.succeed();
	}

	private static String argument(final Component line, final int index) {
		TranslatableContents contents = (TranslatableContents) line.getContents();
		return (String) contents.getArgs()[index];
	}

	private static double[] range(final String text) {
		String[] parts = text.split("-");
		return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[parts.length - 1])};
	}

	private static ItemStack book(final String spell) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:" + spell)).getDefaultInstance();
	}

	private static ItemStack enchanted(final GameTestHelper helper, final String spell, final String enchantment, final int level) {
		ItemStack stack = book(spell);
		Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:" + enchantment)));
		stack.enchant(holder, level);
		return stack;
	}

	private static ServerPlayer viewer(final GameTestHelper helper, final List<ServerPlayer> players) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		players.add(player);
		return player;
	}

	private static ServerPlayer wounded(final GameTestHelper helper, final List<ServerPlayer> players) {
		ServerPlayer player = viewer(helper, players);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		player.setHealth(1.0F);
		return player;
	}

	private static Mob zombie(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int i = 0; i <= 9; i++) {
			helper.setBlock(2, 0, i, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 4).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	// Casts the book on a fresh zombie from a fresh caster, so no cooldown carries over; returns the health lost.
	private static double castDamage(final GameTestHelper helper, final List<ServerPlayer> players, final ItemStack book) {
		return castDamage(helper, viewer(helper, players), book);
	}

	private static double castDamage(final GameTestHelper helper, final ServerPlayer caster, final ItemStack book) {
		Mob zombie = zombie(helper);
		float before = zombie.getHealth();
		cast(helper, caster, book);
		double taken = before - zombie.getHealth();
		zombie.discard();
		return taken;
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final ItemStack book) {
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellCasting.CastResult result = SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, book.getItem() + " did not cast: " + result);
	}

	private static void leave(final GameTestHelper helper, final List<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}
}
