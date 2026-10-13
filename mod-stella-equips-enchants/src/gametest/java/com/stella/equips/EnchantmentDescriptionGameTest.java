package com.stella.equips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.equips.tooltip.EnchantmentDescriptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The book descriptions and the Bomb Arrows blast visual. */
public class EnchantmentDescriptionGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final String LANG_PATH = "/assets/" + MOD_ID + "/lang/en_us.json";
	private static final int MAX_DESCRIPTION_LENGTH = 160;
	private static final int GREY = 0xAAAAAA;
	private static final int FLOOR_Y = 2;
	private static final int SPAWN_Y = 3;
	private static final int WALL_Z = 3;
	private static final int WALL_WIDTH = 3;
	private static final int BLAST_SETTLE_TICKS = 10;

	@GameTest
	public void everyCatalogueEnchantmentHasAShortDescription(final GameTestHelper helper) {
		Map<String, String> shipped = shippedDescriptions();
		List<Holder.Reference<Enchantment>> catalogue = catalogue(helper);
		List<String> missing = new ArrayList<>();
		List<String> tooLong = new ArrayList<>();
		List<String> noRequirement = new ArrayList<>();
		int longest = 0;
		for (Holder.Reference<Enchantment> holder : catalogue) {
			if (!shipped.containsKey(EnchantmentDescriptions.requiresKey(holder))) {
				noRequirement.add(EnchantmentDescriptions.requiresKey(holder));
			}
			String key = EnchantmentDescriptions.keyFor(holder.key().identifier());
			String text = shipped.get(key);
			if (text == null || text.isBlank()) {
				missing.add(key);
				continue;
			}

			longest = Math.max(longest, text.length());
			if (text.length() > MAX_DESCRIPTION_LENGTH || text.contains("\n")) {
				tooLong.add(key + "=" + text.length());
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bookDescriptions RESULT catalogue={} describedKeys={} missing={} overLong={} longestChars={}",
			catalogue.size(), shipped.size(), missing.size(), tooLong.size(), longest
		);
		helper.assertTrue(missing.isEmpty(), "catalogue entries with no description: " + missing);
		helper.assertTrue(noRequirement.isEmpty(), "enchantments whose Requires label is not in the lang: " + noRequirement);
		helper.assertTrue(tooLong.isEmpty(), "descriptions over " + MAX_DESCRIPTION_LENGTH + " chars: " + tooLong);
		helper.succeed();
	}

	/** The header of a book tooltip is the book name and one name line for each enchantment. */
	private static final int BOOK_HEADER_LINES = 3;

	@GameTest
	public void theBookTooltipShowsTheWrappedDescriptionOfEachEnchantment(final GameTestHelper helper) {
		Map<String, String> shipped = shippedDescriptions();
		Holder<Enchantment> first = holder(helper, "bow/marksman");
		Holder<Enchantment> second = holder(helper, "tool/blockfeast");
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(first, 2);
		stored.set(second, 4);
		EnchantmentHelper.setEnchantments(book, stored.toImmutable());

		List<Component> lines = tooltipWithDescriptions(helper, book, shipped);
		List<Component> description = lines.subList(BOOK_HEADER_LINES, lines.size());
		boolean greyUpright = true;
		boolean requiresLine = false;
		for (Component line : description) {
			String text = line.getString();
			// The Requires value is white. Every Desc line is grey.
			requiresLine = text.startsWith("Requires:") || (requiresLine && !text.startsWith("Desc:"));
			if (requiresLine) {
				continue;
			}
			Style style = line.getStyle();
			TextColor colour = line.getSiblings().isEmpty() ? style.getColor() : line.getSiblings().getFirst().getStyle().getColor();
			boolean italic = line.getSiblings().isEmpty() ? style.isItalic() : line.getSiblings().getFirst().getStyle().isItalic();
			if (colour == null || colour.getValue() != GREY || italic) {
				greyUpright = false;
			}
		}

		String joined = String.join(" ", description.stream().map(Component::getString).toList());
		String firstExpected = shipped.get(EnchantmentDescriptions.keyFor(identifier("bow/marksman")));
		String secondExpected = shipped.get(EnchantmentDescriptions.keyFor(identifier("tool/blockfeast")));
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bookTooltipDescriptions RESULT enchantments=2 descriptionLines={} greyUpright={} first={} second={}",
			description.size(), greyUpright, joined.contains(firstExpected), joined.contains(secondExpected)
		);
		helper.assertTrue(joined.contains(firstExpected), "the marksman description is missing from the book tooltip: " + joined);
		helper.assertTrue(joined.contains(secondExpected), "the blockfeast description is missing from the book tooltip: " + joined);
		helper.assertTrue(description.size() > 2, "a long description stayed on one line: " + joined);
		helper.assertTrue(greyUpright, "a description line was not grey and unitalicised");
		helper.succeed();
	}

	@GameTest
	public void anEnchantmentWithNoDescriptionAddsNoLine(final GameTestHelper helper) {
		Map<String, String> shipped = shippedDescriptions();
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(holder(helper, "bow/marksman"), 1);
		stored.set(vanillaHolder(helper, "sharpness"), 1);
		EnchantmentHelper.setEnchantments(book, stored.toImmutable());

		List<Component> lines = tooltipWithDescriptions(helper, book, shipped);
		String joined = String.join(" ", lines.subList(BOOK_HEADER_LINES, lines.size()).stream().map(Component::getString).toList());
		String expected = shipped.get(EnchantmentDescriptions.keyFor(identifier("bow/marksman")));

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bookTooltipMissingDescription RESULT enchantments=2 described=1 descriptionText={}", joined
		);
		helper.assertTrue(joined.equals("Requires: bow Desc: " + expected), "a book with one undescribed enchantment showed: " + joined);
		helper.succeed();
	}

	/** The blast must reach the client as a visible particle, not an invisible puff. */
	@SuppressWarnings("removal")
	@GameTest(maxTicks = 200)
	public void bombArrowsBlastSendsAVisibleParticle(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildFloor(helper);
		for (int x = 0; x < WALL_WIDTH; x++) {
			helper.setBlock(x, SPAWN_Y, WALL_Z, Blocks.STONE);
			helper.setBlock(x, SPAWN_Y + 1, WALL_Z, Blocks.STONE);
		}

		ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
		Vec3 origin = helper.absoluteVec(new Vec3(0.5, SPAWN_Y, 0.5));
		shooter.setPos(origin.x, origin.y, origin.z);
		shooter.setYRot(0.0F);
		shooter.setXRot(0.0F);
		PacketRecorder recorder = PacketRecorder.attach(shooter);
		ItemStack bow = enchanted(Items.BOW, holder(helper, "bow/bomb_arrows"), 1);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, bow);
		shooter.getInventory().add(new ItemStack(Items.ARROW, 64));
		Items.BOW.releaseUsing(bow, level, shooter, 0);

		helper.runAfterDelay(BLAST_SETTLE_TICKS, () -> {
			ClientboundExplodePacket blast = null;
			for (Packet<?> packet : recorder.snapshot()) {
				if (packet instanceof ClientboundExplodePacket explode) {
					blast = explode;
					break;
				}
			}

			helper.assertTrue(blast != null, "the bomb arrow sent no explosion packet to the client");
			Identifier particleId = blast.explosionParticle() instanceof ParticleType<?> type
				? BuiltInRegistries.PARTICLE_TYPE.getKey(type)
				: null;
			boolean visible = ParticleTypes.EXPLOSION_EMITTER.equals(blast.explosionParticle());
			int debris = blast.blockParticles().unwrap().size();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=bombArrowsBlastVisual RESULT particle={} visibleEmitter={} blockCount={} blockParticles={} radius={}",
				particleId, visible, blast.blockCount(), debris, blast.radius()
			);
			helper.assertTrue(
				visible && particleId != null && "minecraft:explosion_emitter".equals(particleId.toString()),
				"the blast carried " + particleId + " instead of the explosion emitter"
			);
			helper.assertTrue(debris > 0, "the blast carried no block particle list");
			helper.succeed();
		});
	}

	private static List<Component> tooltipWithDescriptions(
		final GameTestHelper helper, final ItemStack stack, final Map<String, String> shipped
	) {
		Language previous = Language.getInstance();
		try {
			installModLanguage(shipped);
			return stack.getTooltipLines(
				Item.TooltipContext.of(helper.getLevel().registryAccess()), null, TooltipFlag.NORMAL
			);
		} finally {
			Language.inject(previous);
		}
	}

	/**
	 * The server never installs a language, so the hook's key lookup only resolves
	 * once one is injected. Vanilla keys still fall through to the default.
	 */
	private static void installModLanguage(final Map<String, String> shipped) {
		Language vanilla = Language.DEFAULT_INSTANCE;
		Language.inject(new Language() {
			@Override
			public String getOrDefault(final String id, final String fallback) {
				String ours = shipped.get(id);
				return ours != null ? ours : vanilla.getOrDefault(id, fallback);
			}

			@Override
			public boolean has(final String id) {
				return shipped.containsKey(id) || vanilla.has(id);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return vanilla.isDefaultRightToLeft();
			}

			@Override
			public FormattedCharSequence getVisualOrder(final FormattedText text) {
				return vanilla.getVisualOrder(text);
			}
		});
	}

	private static Map<String, String> shippedDescriptions() {
		Map<String, String> all = new LinkedHashMap<>();
		try (InputStream stream = EnchantmentDescriptionGameTest.class.getResourceAsStream(LANG_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("missing resource " + LANG_PATH);
			}

			JsonObject entries = JsonParser.parseReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8)
			).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
				if (entry.getKey().endsWith(EnchantmentDescriptions.KEY_SUFFIX) || entry.getKey().startsWith(EnchantmentDescriptions.REQUIRES_PREFIX)) {
					all.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		} catch (IOException error) {
			throw new IllegalStateException("could not read " + LANG_PATH, error);
		}

		return all;
	}

	private static List<Holder.Reference<Enchantment>> catalogue(final GameTestHelper helper) {
		return enchantments(helper).listElements()
			.filter(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()))
			.sorted(Comparator.comparing(holder -> holder.key().identifier().toString()))
			.toList();
	}

	private static Holder<Enchantment> holder(final GameTestHelper helper, final String path) {
		return enchantments(helper).wrapAsHolder(
			enchantments(helper).getValueOrThrow(ResourceKey.create(Registries.ENCHANTMENT, identifier(path)))
		);
	}

	private static Holder<Enchantment> vanillaHolder(final GameTestHelper helper, final String path) {
		return enchantments(helper).wrapAsHolder(
			enchantments(helper).getValueOrThrow(
				ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path))
			)
		);
	}

	private static Registry<Enchantment> enchantments(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Identifier identifier(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static ItemStack enchanted(final Item item, final Holder<Enchantment> enchantment, final int level) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(enchantment, level);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}

	private static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, FLOOR_Y, z, Blocks.STONE);
			}
		}
	}
}
