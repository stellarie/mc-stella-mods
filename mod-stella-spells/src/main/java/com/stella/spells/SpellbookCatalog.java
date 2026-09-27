package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SpellbookCatalog {
	public static final List<SpellDefinition> DEFINITIONS = List.of(
		definition("spark_shot", Items.FLINT, 60, 4_708_351, TargetMode.HOSTILE, SpellCasting::applySparkShot),
		definition("frostbind", Items.SNOWBALL, 60, 11_789_820, TargetMode.HOSTILE, SpellCasting::applyFrostbind),
		definition("gale_step", Items.FEATHER, 60, 15_527_921, TargetMode.SELF, SpellCasting::applyGaleStep),
		definition("mending_word", Items.GOLD_INGOT, 140, 16_766_287, TargetMode.SELF, SpellCasting::applyMendingWord),
		definition("stoneguard", Items.IRON_INGOT, 160, 9_268_835, TargetMode.SELF, SpellCasting::applyStoneguard),
		definition("static_lash", Items.COPPER_INGOT, 120, 58_879, TargetMode.HOSTILE, SpellCasting::applyStaticLash),
		definition("briar_snare", Items.VINE, 120, 6_732_650, TargetMode.HOSTILE, SpellCasting::applyBriarSnare),
		definition("tidal_push", Items.PRISMARINE_SHARD, 100, 2_541_274, TargetMode.HOSTILE, SpellCasting::applyTidalPush),
		definition("owls_gaze", Items.AMETHYST_SHARD, 200, 8_280_002, TargetMode.SELF, SpellCasting::applyOwlsGaze),
		definition("slimebound", Items.SLIME_BALL, 100, 10_275_941, TargetMode.SELF, SpellCasting::applySlimebound),
		definition("chainstorm", Items.LIGHTNING_ROD.weathering().unaffected(), 300, 16_739_584, TargetMode.BLOCK, SpellCasting::applyChainstorm),
		definition("riftwalk", Items.ENDER_PEARL, 200, 6_174_129, TargetMode.SAFE_TELEPORT, SpellCasting::applyRiftwalk),
		definition("phoenix_mantle", Items.BLAZE_ROD, 300, 16_727_296, TargetMode.SELF, SpellCasting::applyPhoenixMantle),
		definition("life_bloom", Items.GHAST_TEAR, 340, 16_027_569, TargetMode.SELF, SpellCasting::applyLifeBloom),
		definition("void_well", Items.OBSIDIAN, 340, 3_218_322, TargetMode.BLOCK, SpellCasting::applyVoidWell),
		definition("wardens_roar", Items.ECHO_SHARD, 300, 35_195, TargetMode.SELF, SpellCasting::applyWardensRoar),
		definition("aegis_of_the_deep", Items.TURTLE_SCUTE, 400, 8_440_772, TargetMode.SELF, SpellCasting::applyAegisOfTheDeep),
		definition("wither_seal", Items.WITHER_ROSE, 360, 5_125_166, TargetMode.HOSTILE, SpellCasting::applyWitherSeal),
		definition("time_lock", Items.CLOCK, 400, 16_761_095, TargetMode.BLOCK, SpellCasting::applyTimeLock),
		definition("dragonfall", Items.DRAGON_BREATH, 500, 9_315_498, TargetMode.BLOCK, SpellCasting::applyDragonfall)
	);
	private static final Map<Identifier, SpellDefinition> DEFINITIONS_BY_ID = DEFINITIONS.stream()
		.collect(Collectors.toUnmodifiableMap(SpellDefinition::id, Function.identity()));

	private SpellbookCatalog() {
	}

	public static SpellDefinition get(final Identifier id) {
		SpellDefinition definition = DEFINITIONS_BY_ID.get(id);
		if (definition == null) {
			throw new IllegalArgumentException("Unknown spellbook ID: " + id);
		}
		return definition;
	}

	private static SpellDefinition definition(
		final String path, final Item ingredient, final int cooldown, final int tint, final TargetMode targetMode
	) {
		return definition(path, ingredient, cooldown, tint, targetMode, SpellEffect.NO_OP);
	}

	private static SpellDefinition definition(
		final String path, final Item ingredient, final int cooldown, final int tint,
		final TargetMode targetMode, final SpellEffect effect
	) {
		return new SpellDefinition(
			Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path), ingredient, cooldown, tint, targetMode, effect
		);
	}
}
