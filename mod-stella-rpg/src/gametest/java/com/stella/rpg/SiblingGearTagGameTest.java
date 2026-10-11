package com.stella.rpg;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * The rpg mod lists the gear of its sibling mods in its own bonus tags. Each sibling id is optional, so a pack
 * without that sibling still loads. The data lives here and no sibling mod changes.
 */
public final class SiblingGearTagGameTest {
	private static final Set<String> BOWS = Set.of("hunter_bow", "composite_bow", "gakkung", "great_bow", "rudra_bow", "arbalest",
		"repeater", "ballista", "siege_arbalest", "archmage_staff");
	private static final Set<String> RUIN_WEAPONS = Set.of("deepsteel_sword", "deepsteel_axe", "abyssal_edge", "gladius_of_the_crowd",
		"forgeheart_hammer", "hawkeye_longbow", "silkstring_bow");

	/** The tag files of the main and the test data pack share one path, so every copy on the class path is merged. */
	private static JsonArray values(final GameTestHelper helper, final String tag) throws IOException {
		String path = "data/mod-stella-rpg/tags/item/" + tag + ".json";
		JsonArray merged = new JsonArray();
		Enumeration<URL> copies = SiblingGearTagGameTest.class.getClassLoader().getResources(path);
		while (copies.hasMoreElements()) {
			try (InputStream stream = copies.nextElement().openStream()) {
				merged.addAll(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values"));
			}
		}
		helper.assertTrue(!merged.isEmpty(), "no tag file found at " + path);
		return merged;
	}

	/** The ids of one namespace, which must each be an object with {@code required: false}. */
	private static Set<String> optionalIds(final GameTestHelper helper, final JsonArray values, final String namespace) {
		Set<String> found = new HashSet<>();
		for (JsonElement entry : values) {
			String id = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
			if (!id.startsWith(namespace + ":")) {
				continue;
			}
			JsonObject object = entry.isJsonObject() ? entry.getAsJsonObject() : null;
			helper.assertTrue(object != null && object.has("required") && !object.get("required").getAsBoolean(),
				id + " must be listed with required false, or a pack without " + namespace + " fails to load the tag");
			found.add(id.substring(namespace.length() + 1));
		}
		return found;
	}

	@GameTest
	public void craftedBowsAreListedAsOptional(final GameTestHelper helper) throws IOException {
		Set<String> found = optionalIds(helper, values(helper, "bonus_weapons"), "mod-stella-equips-enchants");
		RpgTestSupport.result("craftedBowsAreListedAsOptional", "found=" + found);
		helper.assertTrue(found.equals(BOWS), "equips weapon ids " + found);
		helper.succeed();
	}

	@GameTest
	public void ruinsGearIsListedAsOptional(final GameTestHelper helper) throws IOException {
		Set<String> weapons = optionalIds(helper, values(helper, "bonus_weapons"), "mod-stella-ruins");
		Set<String> armor = optionalIds(helper, values(helper, "bonus_armor"), "mod-stella-ruins");
		RpgTestSupport.result("ruinsGearIsListedAsOptional", "weapons=" + weapons.size() + " armor=" + armor.size());
		helper.assertTrue(weapons.equals(RUIN_WEAPONS), "ruins weapon ids " + weapons);
		Set<String> expectedArmor = new HashSet<>();
		for (String set : new String[] {"champion", "deepsteel", "hawkeye", "pilgrim", "silkweave", "starweave"}) {
			for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
				expectedArmor.add(set + "_" + piece);
			}
		}
		helper.assertTrue(armor.equals(expectedArmor), "ruins armor ids " + armor);
		helper.succeed();
	}

	@GameTest
	public void thePackLoadsWithoutTheSiblings(final GameTestHelper helper) {
		// None of the sibling mods is in this harness, yet the tags loaded with their entries still listed.
		helper.assertTrue(new net.minecraft.world.item.ItemStack(Items.SHEARS).is(com.stella.rpg.roll.BonusCarrier.WEAPONS),
			"the weapon tag did not load with absent sibling ids");
		helper.assertTrue(new net.minecraft.world.item.ItemStack(Items.CARVED_PUMPKIN).is(com.stella.rpg.roll.BonusCarrier.ARMOR),
			"the armor tag did not load with absent sibling ids");
		helper.succeed();
	}

	/**
	 * The harness loads the rpg mod alone, so a present sibling cannot run here.
	 * By hand: with mod-stella-equips-enchants installed, hold a Hunter Bow granted a bonus and confirm the stat applies.
	 * With mod-stella-ruins installed, wear a deepsteel helmet granted a bonus and confirm the same.
	 */
	@GameTest(required = false)
	public void theSiblingPresentCaseNeedsATwoModRunRequiresManualTest(final GameTestHelper helper) {
		helper.assertTrue(false, "by hand: grant a bonus to a Hunter Bow and a deepsteel helmet in a pack with both siblings");
	}
}
