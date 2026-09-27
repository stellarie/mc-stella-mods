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
		definition("dragonfall", Items.DRAGON_BREATH, 500, 9_315_498, TargetMode.BLOCK, SpellCasting::applyDragonfall),
		definition("ice_lance", Items.PACKED_ICE, 100, 10_544_895, TargetMode.HOSTILE, MoreSpells::applyIceLance),
		definition("soul_siphon", Items.SOUL_SOIL, 180, 5_913_994, TargetMode.HOSTILE, MoreSpells::applySoulSiphon),
		definition("venom_dart", Items.SPIDER_EYE, 100, 6_989_903, TargetMode.HOSTILE, MoreSpells::applyVenomDart),
		definition("sapping_hex", Items.FERMENTED_SPIDER_EYE, 240, 8_007_248, TargetMode.SELF, MoreSpells::applySappingHex),
		definition("blinding_flash", Items.GLOW_INK_SAC, 140, 4_186_320, TargetMode.HOSTILE, MoreSpells::applyBlindingFlash),
		definition("ember_brand", Items.MAGMA_CREAM, 120, 14_706_218, TargetMode.HOSTILE, MoreSpells::applyEmberBrand),
		definition("quicksilver", Items.SUGAR, 300, 14_541_800, TargetMode.SELF, MoreSpells::applyQuicksilver),
		definition("tide_lungs", Items.PUFFERFISH, 300, 3_832_789, TargetMode.SELF, MoreSpells::applyTideLungs),
		definition("berserk", Items.BLAZE_POWDER, 400, 11_674_146, TargetMode.SELF, MoreSpells::applyBerserk),
		definition("haste_rune", Items.REDSTONE, 300, 15_158_332, TargetMode.SELF, MoreSpells::applyHasteRune),
		definition("shadow_veil", Items.TINTED_GLASS, 400, 2_829_114, TargetMode.SELF, MoreSpells::applyShadowVeil),
		definition("sanctuary", Items.HONEYCOMB, 400, 15_906_354, TargetMode.SELF, MoreSpells::applySanctuary),
		definition("purify", Items.QUARTZ, 300, 16_052_456, TargetMode.SELF, MoreSpells::applyPurify),
		definition("frost_nova", Items.ICE, 300, 10_475_248, TargetMode.SELF, MoreSpells::applyFrostNova),
		definition("earthspike", Items.POINTED_DRIPSTONE, 240, 9_137_998, TargetMode.BLOCK, MoreSpells::applyEarthspike),
		definition("plague_cloud", Items.POISONOUS_POTATO, 300, 8_364_858, TargetMode.BLOCK, MoreSpells::applyPlagueCloud),
		definition("flame_ring", Items.FIRE_CHARGE, 400, 16_747_546, TargetMode.SELF, MoreSpells::applyFlameRing),
		definition("gravity", Items.PHANTOM_MEMBRANE, 240, 6_180_770, TargetMode.SELF, MoreSpells::applyGravity),
		definition("bulwark_hymn", Items.GLISTERING_MELON_SLICE, 400, 16_766_720, TargetMode.SELF, MoreSpells::applyBulwarkHymn),
		definition("tailwind", Items.RABBIT_FOOT, 300, 12_251_895, TargetMode.SELF, MoreSpells::applyTailwind),
		definition("battle_hymn", Items.GLOWSTONE_DUST, 400, 14_423_100, TargetMode.SELF, MoreSpells::applyBattleHymn)
	);
	private static final Map<Identifier, SpellDefinition> DEFINITIONS_BY_ID = DEFINITIONS.stream()
		.collect(Collectors.toUnmodifiableMap(SpellDefinition::id, Function.identity()));

	private SpellbookCatalog() {
	}

	/** The spell with this id, or null for one the catalogue no longer holds, as on a piece imbued before a removal. */
	public static SpellDefinition find(final Identifier id) {
		return DEFINITIONS_BY_ID.get(id);
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
