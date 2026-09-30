package com.stella.spells;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spell numbers come from the RPG mod's MATK through {@link RpgBridge}. The RPG mod is not in this harness, so a
 * stand-in answers with a number no spell uses on its own, and each test asks what the spell asked it for and
 * what the target lost.
 */
public class SpellMatkGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.matk");
	private static final double TOLERANCE = 1.0E-3;
	/** The stand-in turns a skill percent into this share of a hit, so 150 percent is 15. */
	private static final double DAMAGE_PER_PERCENT = 0.1;
	/** The stand-in turns a heal coefficient into this many hit points, so 2 is 10. */
	private static final double HEAL_PER_COEFFICIENT = 5.0;

	private record Damage(String spell, TargetKind kind, double percent, boolean measured) {
	}

	private enum TargetKind {
		HOSTILE, SELF, BLOCK
	}

	/** Every damage spell, the percent it must ask for, and whether one immediate hit shows in the target's health. */
	private static final List<Damage> DAMAGE = List.of(
		new Damage("spark_shot", TargetKind.HOSTILE, 150.0, true),
		new Damage("static_lash", TargetKind.HOSTILE, 150.0, true),
		new Damage("ice_lance", TargetKind.HOSTILE, 250.0, true),
		new Damage("soul_siphon", TargetKind.HOSTILE, 200.0, true),
		new Damage("venom_dart", TargetKind.HOSTILE, 100.0, true),
		new Damage("ember_brand", TargetKind.HOSTILE, 100.0, true),
		new Damage("wardens_roar", TargetKind.SELF, 300.0, true),
		new Damage("frost_nova", TargetKind.SELF, 150.0, true),
		new Damage("earthspike", TargetKind.BLOCK, 200.0, true),
		new Damage("dragonfall", TargetKind.BLOCK, 500.0, true),
		new Damage("chainstorm", TargetKind.BLOCK, 300.0, true),
		new Damage("flame_ring", TargetKind.SELF, 150.0, false)
	);

	/** The vanilla flat hit of each damage spell, as it shipped before this change. */
	private static double flatOf(final String spell) {
		return switch (spell) {
			case "spark_shot", "static_lash", "frost_nova", "flame_ring" -> 3.0;
			case "ice_lance" -> 5.0;
			case "soul_siphon", "earthspike" -> 4.0;
			case "venom_dart", "ember_brand" -> 2.0;
			case "wardens_roar", "chainstorm" -> 6.0;
			case "dragonfall" -> 10.0;
			default -> throw new IllegalArgumentException(spell);
		};
	}

	private record Heal(String spell, double flat, double coefficient) {
	}

	private static final List<Heal> HEALS = List.of(
		new Heal("mending_word", 4.0, 2.0),
		new Heal("phoenix_mantle", 4.0, 2.0),
		new Heal("life_bloom", 8.0, 4.0)
	);

	/** A stand-in for the RPG mod that records every skill percent and heal coefficient it is asked for. */
	private static final class Matk implements RpgBridge.Stats {
		final List<Double> percents = new ArrayList<>();
		final List<Double> coefficients = new ArrayList<>();

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double spellDamage(final LivingEntity caster, final double skillPercent) {
			this.percents.add(skillPercent);
			return skillPercent * DAMAGE_PER_PERCENT;
		}

		@Override
		public double spellHeal(final LivingEntity caster, final double coefficient) {
			this.coefficients.add(coefficient);
			return coefficient * HEAL_PER_COEFFICIENT;
		}
	}

	@GameTest(maxTicks = 100)
	public void damageSpellsUseMatk(final GameTestHelper helper) {
		Matk matk = new Matk();
		List<ServerPlayer> casters = new ArrayList<>();
		try {
			for (Damage spell : DAMAGE) {
				matk.percents.clear();
				RpgBridge.setStats(matk);
				double drop = castDamage(helper, spell, casters);
				LOG.info("[SPELLTEST] test=damageSpellsUseMatk RESULT spell={} asked={} drop={}", spell.spell(), matk.percents, drop);
				helper.assertTrue(matk.percents.equals(List.of(spell.percent())),
					spell.spell() + " asked the RPG mod for " + matk.percents + ", not " + spell.percent());
				if (spell.measured()) {
					double expected = spell.percent() * DAMAGE_PER_PERCENT;
					helper.assertTrue(Math.abs(drop - expected) < TOLERANCE, spell.spell() + " dealt " + drop + ", not " + expected);
				}
			}
		} finally {
			RpgBridge.setStats(null);
			leave(helper, casters);
		}
		helper.succeed();
	}

	@GameTest
	public void healSpellsUseMatk(final GameTestHelper helper) {
		Matk matk = new Matk();
		List<ServerPlayer> casters = new ArrayList<>();
		try {
			for (Heal spell : HEALS) {
				matk.coefficients.clear();
				RpgBridge.setStats(matk);
				ServerPlayer caster = wounded(helper, casters, 0.5);
				double gained = castSelf(helper, caster, spell.spell());
				LOG.info("[SPELLTEST] test=healSpellsUseMatk RESULT spell={} asked={} gained={}", spell.spell(), matk.coefficients, gained);
				helper.assertTrue(matk.coefficients.equals(List.of(spell.coefficient())),
					spell.spell() + " asked the RPG mod for " + matk.coefficients + ", not " + spell.coefficient());
				double expected = spell.coefficient() * HEAL_PER_COEFFICIENT;
				helper.assertTrue(Math.abs(gained - expected) < TOLERANCE, spell.spell() + " healed " + gained + ", not " + expected);
			}
			leave(helper, casters);
			casters.clear();
			matk.coefficients.clear();
			RpgBridge.setStats(matk);
			ServerPlayer caster = wounded(helper, casters, 0.5);
			ServerPlayer ally = wounded(helper, casters, 2.5);
			double before = ally.getHealth();
			double selfGain = castSelf(helper, caster, "sanctuary");
			double allyGain = ally.getHealth() - before;
			LOG.info("[SPELLTEST] test=healSpellsUseMatk RESULT spell=sanctuary asked={} self={} ally={}", matk.coefficients, selfGain, allyGain);
			helper.assertTrue(matk.coefficients.equals(List.of(3.0, 2.0)), "Sanctuary asked the RPG mod for " + matk.coefficients + ", not [3.0, 2.0]");
			helper.assertTrue(Math.abs(selfGain - 3.0 * HEAL_PER_COEFFICIENT) < TOLERANCE, "Sanctuary healed its caster " + selfGain);
			helper.assertTrue(Math.abs(allyGain - 2.0 * HEAL_PER_COEFFICIENT) < TOLERANCE, "Sanctuary healed an ally " + allyGain);
		} finally {
			RpgBridge.setStats(null);
			leave(helper, casters);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void combos(final GameTestHelper helper) {
		Matk matk = new Matk();
		List<ServerPlayer> casters = new ArrayList<>();
		try {
			// Frostbind deals nothing; Spark Shot 150 percent, then Thermal Shock's bonus 4 x 50.
			double thermal = comboDrop(helper, matk, casters, List.of("frostbind", "spark_shot"), TargetKind.HOSTILE);
			helper.assertTrue(matk.percents.equals(List.of(150.0, 200.0)), "Thermal Shock asked for " + matk.percents);
			helper.assertTrue(Math.abs(thermal - (150.0 + 200.0) * DAMAGE_PER_PERCENT) < TOLERANCE, "Thermal Shock took " + thermal);
			// Static Lash 150 percent, Tidal Push nothing, then Riptide's bonus 3 x 50.
			double riptide = comboDrop(helper, matk, casters, List.of("static_lash", "tidal_push"), TargetKind.HOSTILE);
			helper.assertTrue(matk.percents.equals(List.of(150.0, 150.0)), "Riptide asked for " + matk.percents);
			helper.assertTrue(Math.abs(riptide - (150.0 + 150.0) * DAMAGE_PER_PERCENT) < TOLERANCE, "Riptide took " + riptide);
			// Time Lock nothing, Dragonfall 500 percent, then Frozen Meteor's bonus 5 x 50.
			double meteor = comboDrop(helper, matk, casters, List.of("time_lock", "dragonfall"), TargetKind.BLOCK);
			helper.assertTrue(matk.percents.equals(List.of(500.0, 250.0)), "Frozen Meteor asked for " + matk.percents);
			helper.assertTrue(Math.abs(meteor - (500.0 + 250.0) * DAMAGE_PER_PERCENT) < TOLERANCE, "Frozen Meteor took " + meteor);
			LOG.info("[SPELLTEST] test=combos RESULT thermal={} riptide={} meteor={}", thermal, riptide, meteor);
		} finally {
			RpgBridge.setStats(null);
			leave(helper, casters);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void noRpgKeepsFlat(final GameTestHelper helper) {
		List<ServerPlayer> casters = new ArrayList<>();
		// Once with no RPG mod at all, once with an RPG mod too old to know MATK: both keep the flat number.
		RpgBridge.Stats old = new RpgBridge.Stats() {
			@Override
			public double power(final Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final Player player) {
				return 1.0;
			}
		};
		try {
			for (RpgBridge.Stats stats : new RpgBridge.Stats[] {null, old}) {
				leave(helper, casters);
				casters.clear();
				for (Damage spell : DAMAGE) {
					if (!spell.measured()) {
						continue;
					}
					RpgBridge.setStats(stats);
					double drop = castDamage(helper, spell, casters);
					helper.assertTrue(Math.abs(drop - flatOf(spell.spell())) < TOLERANCE,
						spell.spell() + " dealt " + drop + " without MATK, not its flat " + flatOf(spell.spell()));
				}
				for (Heal spell : HEALS) {
					RpgBridge.setStats(stats);
					double gained = castSelf(helper, wounded(helper, casters, 0.5), spell.spell());
					helper.assertTrue(Math.abs(gained - spell.flat()) < TOLERANCE,
						spell.spell() + " healed " + gained + " without MATK, not its flat " + spell.flat());
				}
				RpgBridge.setStats(stats);
				ServerPlayer caster = wounded(helper, casters, 0.5);
				ServerPlayer ally = wounded(helper, casters, 2.5);
				double before = ally.getHealth();
				double selfGain = castSelf(helper, caster, "sanctuary");
				helper.assertTrue(Math.abs(selfGain - MoreSpells.SANCTUARY_SELF_HEAL) < TOLERANCE, "Sanctuary healed its caster " + selfGain);
				helper.assertTrue(Math.abs(ally.getHealth() - before - MoreSpells.SANCTUARY_ALLY_HEAL) < TOLERANCE, "Sanctuary healed an ally");
			}
			RpgBridge.setStats(null);
			double thermal = comboDropFlat(helper, casters);
			helper.assertTrue(Math.abs(thermal - (SpellCasting.DAMAGE + SpellCombos.THERMAL_SHOCK_DAMAGE)) < TOLERANCE, "Thermal Shock took " + thermal);
			LOG.info("[SPELLTEST] test=noRpgKeepsFlat RESULT thermal={}", thermal);
		} finally {
			RpgBridge.setStats(null);
			leave(helper, casters);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void runesStillGrow(final GameTestHelper helper) {
		Matk matk = new Matk();
		List<ServerPlayer> casters = new ArrayList<>();
		Object saved = growth();
		TowerBridge.setGrowth(new TowerBridge.Growth() {
			@Override
			public double flat(final ItemStack focus) {
				CustomData data = focus.get(DataComponents.CUSTOM_DATA);
				return data == null ? 1.0 : data.copyTag().getDoubleOr("matk_flat", 1.0);
			}

			@Override
			public double resonance(final ItemStack focus, final String spellId) {
				return 1.0;
			}

			@Override
			public int cooldown(final Player caster, final int ticks) {
				return ticks;
			}
		});
		try {
			RpgBridge.setStats(matk);
			ServerPlayer caster = caster(helper, casters);
			Mob zombie = zombie(helper, 4);
			ItemStack book = marked("spark_shot", 3.0);
			caster.setItemInHand(InteractionHand.MAIN_HAND, book);
			float before = zombie.getHealth();
			SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
			double grown = before - zombie.getHealth();
			zombie.discard();

			ServerPlayer patient = wounded(helper, casters, 0.5);
			ItemStack mending = marked("mending_word", 3.0);
			patient.setItemInHand(InteractionHand.MAIN_HAND, mending);
			float health = patient.getHealth();
			SpellCasting.cast(patient, (SpellbookItem) mending.getItem(), InteractionHand.MAIN_HAND);
			double healed = patient.getHealth() - health;
			LOG.info("[SPELLTEST] test=runesStillGrow RESULT damage={} heal={}", grown, healed);
			helper.assertTrue(Math.abs(grown - 150.0 * DAMAGE_PER_PERCENT * 3.0) < TOLERANCE, "a rune x3 Spark Shot dealt " + grown);
			helper.assertTrue(Math.abs(healed - 2.0 * HEAL_PER_COEFFICIENT * 3.0) < TOLERANCE, "a rune x3 Mending Word healed " + healed);
		} finally {
			RpgBridge.setStats(null);
			restoreGrowth(saved);
			leave(helper, casters);
		}
		helper.succeed();
	}

	@GameTest
	public void bridgeBindsTheRpgSpellsSignatures(final GameTestHelper helper) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue("com.stella.rpg.RpgSpells".equals(RpgBridge.RPG_SPELLS_CLASS), "the bridge looks for " + RpgBridge.RPG_SPELLS_CLASS);
			RpgBridge.Stats bound = RpgBridge.find(RpgSpellsStandIn.class.getName());
			helper.assertTrue(Math.abs(bound.spellDamage(caster, 150.0) - RpgSpellsStandIn.damage(150.0)) < TOLERANCE, "spellDamage did not bind");
			helper.assertTrue(Math.abs(bound.spellHeal(caster, 2.0) - RpgSpellsStandIn.heal(2.0)) < TOLERANCE, "spellHeal did not bind");
			helper.assertTrue(bound.power(caster) == 1.0 && bound.cooldown(caster) == 0.5, "power and cooldown did not bind");

			RpgBridge.Stats mismatched = RpgBridge.find(RpgSpellsMismatchedStandIn.class.getName());
			helper.assertTrue(mismatched.power(caster) == 1.0 && mismatched.cooldown(caster) == 0.5, "a mismatched MATK call cost the mod its cooldown");
			helper.assertTrue(Double.isNaN(mismatched.spellDamage(caster, 150.0)), "a spellDamage of another shape still answered");
			helper.assertTrue(Double.isNaN(mismatched.spellHeal(caster, 2.0)), "a spellHeal of another shape still answered");

			RpgBridge.setStats(mismatched);
			ServerPlayer patient = wounded(helper, new ArrayList<>(), 0.5);
			double gained = castSelf(helper, patient, "mending_word");
			helper.assertTrue(Math.abs(gained - 4.0) < TOLERANCE, "a mismatched bridge healed " + gained + ", not the flat 4");
		} finally {
			RpgBridge.setStats(null);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		}
		helper.succeed();
	}

	@GameTest
	public void spellbooksTagListsEveryBook(final GameTestHelper helper) {
		String path = "/data/mod-stella-rpg/tags/item/spellbooks.json";
		Set<String> listed = new HashSet<>();
		JsonObject tag;
		try (var stream = SpellMatkGameTest.class.getResourceAsStream(path)) {
			helper.assertTrue(stream != null, path + " is missing");
			tag = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException failure) {
			helper.fail("could not read " + path + ": " + failure);
			return;
		}
		for (JsonElement value : tag.getAsJsonArray("values")) {
			helper.assertTrue(listed.add(value.getAsString()), value.getAsString() + " is listed twice");
		}
		Set<String> books = new HashSet<>();
		SpellbookCatalog.DEFINITIONS.forEach(definition -> books.add(definition.id().toString()));
		LOG.info("[SPELLTEST] test=spellbooksTagListsEveryBook RESULT listed={} books={}", listed.size(), books.size());
		helper.assertTrue(tag.has("replace") && !tag.get("replace").getAsBoolean(), "the tag must say replace false");
		helper.assertTrue(listed.equals(books), "the tag lists " + listed + " but the spellbooks are " + books);
		helper.assertTrue(listed.stream().noneMatch(id -> id.endsWith("_scroll")), "the tag lists a scroll");
		helper.assertTrue(!listed.contains(Spellbooks.SCROLL_OF_RETURN_ID.toString()), "the tag lists the return scroll");
		helper.succeed();
	}

	private static double castDamage(final GameTestHelper helper, final Damage spell, final List<ServerPlayer> casters) {
		ServerPlayer caster = caster(helper, casters);
		Mob zombie = zombie(helper, 4);
		if (spell.kind() == TargetKind.BLOCK) {
			helper.setBlock(2, 2, 6, Blocks.STONE);
		}
		float before = zombie.getHealth();
		cast(helper, caster, spell.spell());
		double drop = before - zombie.getHealth();
		zombie.discard();
		helper.setBlock(2, 2, 6, Blocks.AIR);
		return drop;
	}

	private static double comboDrop(
		final GameTestHelper helper, final Matk matk, final List<ServerPlayer> casters, final List<String> spells, final TargetKind kind
	) {
		matk.percents.clear();
		RpgBridge.setStats(matk);
		ServerPlayer caster = caster(helper, casters);
		Mob zombie = zombie(helper, 4);
		if (kind == TargetKind.BLOCK) {
			helper.setBlock(2, 2, 6, Blocks.STONE);
		}
		float before = zombie.getHealth();
		for (String spell : spells) {
			cast(helper, caster, spell);
		}
		double drop = before - zombie.getHealth();
		zombie.discard();
		helper.setBlock(2, 2, 6, Blocks.AIR);
		return drop;
	}

	private static double comboDropFlat(final GameTestHelper helper, final List<ServerPlayer> casters) {
		ServerPlayer caster = caster(helper, casters);
		Mob zombie = zombie(helper, 4);
		float before = zombie.getHealth();
		cast(helper, caster, "frostbind");
		cast(helper, caster, "spark_shot");
		double drop = before - zombie.getHealth();
		zombie.discard();
		return drop;
	}

	private static double castSelf(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		float before = caster.getHealth();
		cast(helper, caster, spell);
		return caster.getHealth() - before;
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:" + spell));
		ItemStack stack = book.getDefaultInstance();
		caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, spell + " did not cast: " + result);
	}

	private static ItemStack marked(final String spell, final double flat) {
		ItemStack book = BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:" + spell)).getDefaultInstance();
		CompoundTag tag = new CompoundTag();
		tag.putDouble("matk_flat", flat);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	private static Object growth() {
		try {
			Field field = TowerBridge.class.getDeclaredField("growth");
			field.setAccessible(true);
			return field.get(null);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void restoreGrowth(final Object saved) {
		TowerBridge.setGrowth((TowerBridge.Growth) saved);
	}

	private static ServerPlayer caster(final GameTestHelper helper, final List<ServerPlayer> casters) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		casters.add(player);
		return player;
	}

	// Health well below a high maximum, so a heal shows in full and never runs into the top.
	private static ServerPlayer wounded(final GameTestHelper helper, final List<ServerPlayer> casters, final double x) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		player.setHealth(1.0F);
		casters.add(player);
		return player;
	}

	private static Mob zombie(final GameTestHelper helper, final int z) {
		helper.setTime(18_000L);
		for (int i = 0; i <= 9; i++) {
			helper.setBlock(2, 0, i, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static void leave(final GameTestHelper helper, final List<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}
}
