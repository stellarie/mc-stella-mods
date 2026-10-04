package com.stella.equips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.equips.unique.UniqueBowItem;
import com.stella.equips.unique.UniqueCrossbowItem;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueMaceItem;
import com.stella.equips.unique.Uniques;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The registry of uniques: what an entry holds, how its item is built, which enchantments and tags it takes, and
 * which model and texture files ship for it. Fixtures with a {@code test_} id are skipped.
 */
public class UniqueRegistryGameTest {
	private static final String ASSETS = "/assets/" + StellaEquips.MOD_ID + "/";
	private static final String DATA = "/data/" + StellaEquips.MOD_ID + "/";
	private static final List<String> SLICES = List.of("uniques_af", "uniques_mp");

	@GameTest
	public void theRegistryHoldsBonewhisperBow(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ItemStack stack = new ItemStack(Uniques.item(def));
		boolean fromSource = Uniques.bySource(Identifier.parse("minecraft:skeleton")).contains(def);
		boolean fromStack = Uniques.def(stack) == def;
		boolean listed = Uniques.all().contains(def);
		UniqueTestKit.result("registryBonewhisper", "name=" + def.name() + " job=" + def.job() + " kind=" + def.kind() + " sources=" + def.sources()
			+ " fromSource=" + fromSource + " fromStack=" + fromStack + " listed=" + listed);
		helper.assertTrue(def.name().equals("Bonewhisper Bow"), "the name is " + def.name());
		helper.assertTrue(def.job().name().equals("ARCHER") && def.kind() == UniqueKind.BOW, "job and kind: " + def.job() + " " + def.kind());
		helper.assertTrue(def.sources().equals(List.of(Identifier.parse("minecraft:skeleton"))), "sources " + def.sources());
		helper.assertTrue(fromSource && fromStack && listed, "lookups: source " + fromSource + " stack " + fromStack + " all " + listed);
		helper.assertTrue(Uniques.def(new ItemStack(Items.BOW)) == null && Uniques.def(ItemStack.EMPTY) == null, "a plain bow or an empty stack has a def");
		for (String text : List.of(def.base(), def.plus7(), def.plus10(), def.plus12(), def.plus15())) {
			helper.assertTrue(!text.isBlank(), "an effect text of " + def.id() + " is blank");
		}
		helper.assertTrue(def.effect() != null, "no effect");
		helper.succeed();
	}

	@GameTest
	public void everyUniqueIsBuiltFromItsKind(final GameTestHelper helper) {
		int checked = 0;
		for (UniqueDef def : realUniques()) {
			ItemStack stack = new ItemStack(Uniques.item(def));
			Item item = stack.getItem();
			Identifier id = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, def.id());
			helper.assertTrue(BuiltInRegistries.ITEM.getValue(id) == item, def.id() + " is not registered under " + id);
			helper.assertTrue(stack.getRarity() == Rarity.EPIC, def.id() + " has rarity " + stack.getRarity());
			helper.assertTrue(stack.getHoverName().getString().equals(def.name()), def.id() + " is named " + stack.getHoverName().getString());
			helper.assertTrue(stack.get(DataComponents.ENCHANTABLE) != null && stack.get(DataComponents.ENCHANTABLE).value() == 10,
				def.id() + " enchantability " + stack.get(DataComponents.ENCHANTABLE));
			helper.assertTrue(stack.get(DataComponents.REPAIRABLE) != null && stack.get(DataComponents.REPAIRABLE).isValidRepairItem(new ItemStack(Items.DIAMOND))
				&& !stack.get(DataComponents.REPAIRABLE).isValidRepairItem(new ItemStack(Items.IRON_INGOT)), def.id() + " does not repair with diamond alone");
			helper.assertTrue(stack.getMaxStackSize() == 1, def.id() + " stacks to " + stack.getMaxStackSize());
			int expectedDurability = switch (def.kind()) {
				case BOW -> 768;
				case CROSSBOW -> 930;
				case MACE -> 1561;
				default -> new ItemStack(reference(def.kind())).getMaxDamage();
			};
			helper.assertTrue(stack.getMaxDamage() == expectedDurability, def.id() + " durability " + stack.getMaxDamage() + " expected " + expectedDurability);
			switch (def.kind()) {
				case BOW -> helper.assertTrue(item instanceof UniqueBowItem, def.id() + " is " + item.getClass());
				case CROSSBOW -> helper.assertTrue(item instanceof UniqueCrossbowItem, def.id() + " is " + item.getClass());
				case MACE -> helper.assertTrue(item instanceof UniqueMaceItem, def.id() + " is " + item.getClass());
				default -> {
				}
			}
			if (def.kind() == UniqueKind.MACE) {
				helper.assertTrue(stack.getMaxDamage() == 1561, def.id() + " mace durability " + stack.getMaxDamage());
			}
			if (def.kind() != UniqueKind.BOW && def.kind() != UniqueKind.CROSSBOW) {
				ItemStack diamond = new ItemStack(reference(def.kind()));
				for (Holder<Attribute> attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
					double have = attributeSum(stack, attribute);
					double want = attributeSum(diamond, attribute);
					helper.assertTrue(have == want, def.id() + " " + attribute.getRegisteredName() + " " + have + " expected " + want);
				}
			}
			if (def.kind().isArmor()) {
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				helper.assertTrue(equippable != null && equippable.slot() == def.kind().slot(), def.id() + " wears in " + equippable);
				ResourceKey<?> asset = equippable.assetId().orElse(null);
				ResourceKey<?> expected = def.look() != null ? def.look() : new ItemStack(Items.DIAMOND_HELMET).get(DataComponents.EQUIPPABLE).assetId().orElse(null);
				helper.assertTrue(expected.equals(asset), def.id() + " worn model " + asset + " expected " + expected);
			}
			checked++;
		}
		UniqueTestKit.result("registryBuild", "checked=" + checked);
		helper.assertTrue(checked >= 1, "no unique was checked");
		helper.succeed();
	}

	@GameTest
	public void everyUniqueAcceptsAnEnchantmentOfItsKind(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int checked = 0;
		for (UniqueDef def : realUniques()) {
			ItemStack stack = new ItemStack(Uniques.item(def));
			List<ResourceKey<Enchantment>> keys = switch (def.kind()) {
				case BOW -> List.of(Enchantments.POWER, Enchantments.UNBREAKING);
				case CROSSBOW -> List.of(Enchantments.QUICK_CHARGE, Enchantments.UNBREAKING);
				case SWORD -> List.of(Enchantments.SHARPNESS, Enchantments.SWEEPING_EDGE, Enchantments.UNBREAKING);
				case AXE -> List.of(Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING);
				case MACE -> List.of(Enchantments.DENSITY, Enchantments.UNBREAKING);
				case SPEAR -> List.of(Enchantments.LUNGE, Enchantments.SHARPNESS, Enchantments.UNBREAKING);
				default -> List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING);
			};
			for (ResourceKey<Enchantment> key : keys) {
				Holder<Enchantment> enchantment = UniqueTestKit.enchantment(level, key);
				helper.assertTrue(enchantment.value().isSupportedItem(stack), def.id() + " does not accept " + key.identifier());
			}
			checked++;
		}
		UniqueTestKit.result("registryEnchant", "checked=" + checked);
		helper.assertTrue(checked >= 1, "no unique was checked");
		helper.succeed();
	}

	@GameTest
	public void everyUniqueJoinsTheTagsThatDriveItsKind(final GameTestHelper helper) {
		Map<UniqueKind, List<String>> required = Map.of(
			UniqueKind.BOW, List.of("enchantable/bow", "enchantable/durability"),
			UniqueKind.CROSSBOW, List.of("enchantable/crossbow", "enchantable/durability"),
			UniqueKind.SWORD, List.of("swords", "enchantable/sweeping", "enchantable/sharp_weapon", "enchantable/weapon", "enchantable/durability"),
			UniqueKind.AXE, List.of("axes", "enchantable/sharp_weapon", "enchantable/mining", "enchantable/weapon", "enchantable/durability"),
			UniqueKind.MACE, List.of("enchantable/mace", "enchantable/weapon", "enchantable/fire_aspect", "enchantable/durability"),
			UniqueKind.SPEAR, List.of("spears", "enchantable/lunge", "enchantable/weapon", "enchantable/durability"),
			UniqueKind.HELMET, List.of("head_armor", "enchantable/head_armor", "enchantable/armor", "enchantable/durability", "trimmable_armor"),
			UniqueKind.CHESTPLATE, List.of("chest_armor", "enchantable/chest_armor", "enchantable/armor", "enchantable/durability", "trimmable_armor"),
			UniqueKind.LEGGINGS, List.of("leg_armor", "enchantable/leg_armor", "enchantable/armor", "enchantable/durability", "trimmable_armor"),
			UniqueKind.BOOTS, List.of("foot_armor", "enchantable/foot_armor", "enchantable/armor", "enchantable/durability", "trimmable_armor")
		);
		int checked = 0;
		for (UniqueDef def : realUniques()) {
			ItemStack stack = new ItemStack(Uniques.item(def));
			for (String tag : required.get(def.kind())) {
				helper.assertTrue(stack.is(item("minecraft", tag)), def.id() + " is not in the vanilla tag " + tag);
			}
			boolean inSlice = false;
			for (String slice : SLICES) {
				inSlice |= stack.is(item(StellaEquips.MOD_ID, slice + "/" + def.kind().tagName()));
			}
			helper.assertTrue(inSlice, def.id() + " is in no slice tag of kind " + def.kind().tagName());
			helper.assertTrue(stack.is(item(StellaEquips.MOD_ID, "uniques")), def.id() + " is not in the uniques tag");
			helper.assertTrue(stack.is(item("mod-stella-rpg", "multi_roll")), def.id() + " is not in the multi_roll tag");
			checked++;
		}
		UniqueTestKit.result("registryTags", "checked=" + checked);
		helper.assertTrue(checked >= 1, "no unique was checked");
		helper.succeed();
	}

	@GameTest
	public void everyUniqueHasItsModelAndTextures(final GameTestHelper helper) throws IOException {
		int checked = 0;
		for (UniqueDef def : realUniques()) {
			JsonObject definition = json(helper, ASSETS + "items/" + def.id() + ".json");
			helper.assertTrue(definition.has("model"), def.id() + " item definition has no model");
			for (String model : modelFilesOf(def)) {
				JsonObject modelJson = json(helper, ASSETS + "models/item/" + model + ".json");
				helper.assertTrue(modelJson.has("parent"), model + " has no parent");
			}
			for (String texture : modelsOf(def)) {
				BufferedImage image = png(helper, ASSETS + "textures/item/" + texture + ".png");
				helper.assertTrue(image.getWidth() == 16 && image.getHeight() == 16, texture + " is " + image.getWidth() + "x" + image.getHeight());
				int opaque = 0;
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						if ((image.getRGB(x, y) >>> 24) != 0) {
							opaque++;
						}
					}
				}
				helper.assertTrue(opaque >= 12, texture + " has only " + opaque + " opaque pixels");
			}
			for (String reference : referencedModels(definition)) {
				String path = reference.substring(reference.indexOf(':') + 1);
				helper.assertTrue(resource(ASSETS + "models/" + path + ".json") != null, def.id() + " item definition points at the missing model " + reference);
			}
			checked++;
		}
		UniqueTestKit.result("registryAssets", "checked=" + checked);
		helper.assertTrue(checked >= 1, "no unique was checked");
		helper.succeed();
	}

	@GameTest
	public void theSharedSliceFilesExist(final GameTestHelper helper) throws IOException {
		for (String slice : SLICES) {
			for (UniqueKind kind : UniqueKind.values()) {
				JsonObject tag = json(helper, DATA + "tags/item/" + slice + "/" + kind.tagName() + ".json");
				helper.assertTrue(tag.has("values"), slice + "/" + kind.tagName() + " has no values");
			}
		}
		JsonArray members = json(helper, DATA + "tags/item/uniques.json").getAsJsonArray("values");
		for (String slice : SLICES) {
			for (UniqueKind kind : UniqueKind.values()) {
				String entry = "#" + StellaEquips.MOD_ID + ":" + slice + "/" + kind.tagName();
				helper.assertTrue(contains(members, entry), "the uniques tag does not list " + entry);
			}
		}
		JsonArray multiRoll = json(helper, "/data/mod-stella-rpg/tags/item/multi_roll.json").getAsJsonArray("values");
		helper.assertTrue(contains(multiRoll, "#" + StellaEquips.MOD_ID + ":uniques"), "the multi_roll tag does not list the uniques tag");
		for (String name : List.of("mod-stella-equips-enchants.uniques-af.mixins.json", "mod-stella-equips-enchants.uniques-mp.mixins.json")) {
			helper.assertTrue(resource("/" + name) != null, "the mixin config " + name + " is missing");
		}
		Path manifestPath = FabricLoader.getInstance().getModContainer(StellaEquips.MOD_ID).orElseThrow().findPath("fabric.mod.json").orElseThrow();
		String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
		helper.assertTrue(manifest.contains("uniques-af.mixins.json") && manifest.contains("uniques-mp.mixins.json"), "fabric.mod.json lists no slice mixin config");
		UniqueTestKit.result("registrySliceFiles", "tags=" + (SLICES.size() * UniqueKind.values().length) + " configs=2");
		helper.succeed();
	}

	static List<UniqueDef> realUniques() {
		List<UniqueDef> real = new ArrayList<>();
		for (UniqueDef def : Uniques.all()) {
			if (!def.id().startsWith(UniqueFixtures.PREFIX)) {
				real.add(def);
			}
		}
		return real;
	}

	/** The model files of a kind. A spear also has the model of the spear in hand. */
	static List<String> modelFilesOf(final UniqueDef def) {
		return def.kind() == UniqueKind.SPEAR ? List.of(def.id(), def.id() + "_in_hand") : modelsOf(def);
	}

	/** The texture names of a kind: the item itself, then its frames. */
	static List<String> modelsOf(final UniqueDef def) {
		String id = def.id();
		return switch (def.kind()) {
			case BOW -> List.of(id, id + "_pulling_0", id + "_pulling_1", id + "_pulling_2");
			case CROSSBOW -> List.of(id, id + "_pulling_0", id + "_pulling_1", id + "_pulling_2", id + "_arrow", id + "_firework");
			default -> List.of(id);
		};
	}

	private static Item reference(final UniqueKind kind) {
		return switch (kind) {
			case SWORD -> Items.DIAMOND_SWORD;
			case AXE -> Items.DIAMOND_AXE;
			case SPEAR -> Items.DIAMOND_SPEAR;
			case MACE -> Items.MACE;
			case HELMET -> Items.DIAMOND_HELMET;
			case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
			case LEGGINGS -> Items.DIAMOND_LEGGINGS;
			case BOOTS -> Items.DIAMOND_BOOTS;
			case BOW -> Items.BOW;
			case CROSSBOW -> Items.CROSSBOW;
		};
	}

	private static double attributeSum(final ItemStack stack, final Holder<Attribute> attribute) {
		double sum = 0.0;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(attribute)) {
				sum += entry.modifier().amount();
			}
		}
		return sum;
	}

	private static TagKey<Item> item(final String namespace, final String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
	}

	private static InputStream resource(final String path) {
		return StellaEquips.class.getResourceAsStream(path);
	}

	private static JsonObject json(final GameTestHelper helper, final String path) {
		InputStream stream = resource(path);
		helper.assertTrue(stream != null, "the resource " + path + " is missing");
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException(path, e);
		}
	}

	private static BufferedImage png(final GameTestHelper helper, final String path) throws IOException {
		InputStream stream = resource(path);
		helper.assertTrue(stream != null, "the texture " + path + " is missing");
		try (stream) {
			return ImageIO.read(stream);
		}
	}

	private static boolean contains(final JsonArray values, final String entry) {
		for (JsonElement element : values) {
			String text = element.isJsonObject() ? element.getAsJsonObject().get("id").getAsString() : element.getAsString();
			if (text.equals(entry)) {
				return true;
			}
		}
		return false;
	}

	/** Every "model" string in an item definition, at any depth. */
	private static List<String> referencedModels(final JsonElement element) {
		List<String> found = new ArrayList<>();
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
					found.add(entry.getValue().getAsString());
				} else {
					found.addAll(referencedModels(entry.getValue()));
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				found.addAll(referencedModels(child));
			}
		}
		return found;
	}
}
