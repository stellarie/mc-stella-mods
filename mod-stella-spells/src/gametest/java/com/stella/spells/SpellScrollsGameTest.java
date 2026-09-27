package com.stella.spells;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single-use scroll for every spell. It casts through the same path as its book, crumbles only when
 * the spell lands, keeps its own short cooldown apart from the book's, turns up in a large share of
 * vanilla chests, and crafts from one paper and the spell's material.
 */
public class SpellScrollsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.scrolls");
	private static final int CHEST_SAMPLES = 10_000;
	private static final long CHEST_SEED = 20_260_927L;
	private static final double RATE_TOLERANCE = 0.02;
	private static final Identifier CHEST = Identifier.parse("minecraft:chests/simple_dungeon");

	@GameTest
	public void everySpellHasAScrollOfItsOwn(final GameTestHelper helper) {
		// Every spell but a book-only one, in catalogue order.
		List<SpellDefinition> scrolled = SpellbookCatalog.DEFINITIONS.stream().filter(definition -> !Spellbooks.BOOK_ONLY.contains(definition.id())).toList();
		helper.assertTrue(Spellbooks.SCROLLS.size() == scrolled.size(), Spellbooks.SCROLLS.size() + " scrolls for " + scrolled.size() + " scrolled spells");
		for (int i = 0; i < Spellbooks.SCROLLS.size(); i++) {
			helper.assertTrue(Spellbooks.SCROLLS.get(i).definition() == scrolled.get(i), "scroll " + i + " casts the wrong spell");
		}
		helper.assertTrue(Spellbooks.BOOK_ONLY.stream().noneMatch(id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Spellbooks.scrollId(id))), "a book-only spell has a scroll");
		LOG.info("[SPELLTEST] test=everySpellHasAScrollOfItsOwn RESULT scrolls={}", Spellbooks.SCROLLS.size());
		helper.succeed();
	}

	@GameTest
	public void aScrollCastsItsSpellAndCrumbles(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = survivalCaster(helper);
		Mob zombie = target(helper);
		float before = zombie.getHealth();
		SpellScrollItem scroll = scroll(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scroll, 2));
		InteractionResult result = scroll.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS && zombie.getHealth() < before, "the scroll did not cast: " + result + " health " + before + " -> " + zombie.getHealth());
		helper.assertTrue(caster.getMainHandItem().getCount() == 1, "the scroll did not crumble: " + caster.getMainHandItem().getCount() + " left");
		LOG.info("[SPELLTEST] test=aScrollCastsItsSpellAndCrumbles RESULT damage={} left=1", before - zombie.getHealth());
		helper.succeed();
	}

	@GameTest
	public void aMissedScrollIsKept(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = survivalCaster(helper);
		SpellScrollItem scroll = scroll(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scroll, 2));
		InteractionResult result = scroll.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result != InteractionResult.SUCCESS && caster.getMainHandItem().getCount() == 2, "a scroll with nothing to hit was used up: " + result);
		LOG.info("[SPELLTEST] test=aMissedScrollIsKept RESULT result={} left=2", result);
		helper.succeed();
	}

	@GameTest
	public void aScrollIgnoresItsBooksCooldown(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = survivalCaster(helper);
		target(helper);
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		helper.assertTrue(book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "the book did not cast");
		SpellScrollItem scroll = scroll(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scroll, 1));
		InteractionResult result = scroll.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the scroll waited on the book's cooldown: " + result);
		LOG.info("[SPELLTEST] test=aScrollIgnoresItsBooksCooldown RESULT scroll={}", result);
		helper.succeed();
	}

	/** The rate a player finds scrolls: the share of vanilla chests that hold at least one. */
	@GameTest
	public void scrollsTurnUpInManyChests(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(CHEST_SEED);
		int withScroll = 0;
		int scrolls = 0;
		for (int i = 0; i < CHEST_SAMPLES; i++) {
			List<ItemStack> drops = new ArrayList<>();
			SpellbookLoot.modifyChestDrops(CHEST, true, random, drops);
			int found = drops.stream().filter(stack -> stack.getItem() instanceof SpellScrollItem).mapToInt(ItemStack::getCount).sum();
			withScroll += found > 0 ? 1 : 0;
			scrolls += found;
		}
		double rate = (double) withScroll / CHEST_SAMPLES;
		LOG.info("[SPELLTEST] test=scrollsTurnUpInManyChests RESULT chests={} rate={} scrolls={} chance={}", CHEST_SAMPLES, rate, scrolls, SpellbookLoot.SCROLL_CHEST_CHANCE);
		helper.assertTrue(rate > 0.0 && Math.abs(rate - SpellbookLoot.SCROLL_CHEST_CHANCE) <= RATE_TOLERANCE, "scrolls turned up in " + rate + " of chests, not " + SpellbookLoot.SCROLL_CHEST_CHANCE);
		helper.succeed();
	}

	@GameTest
	public void everyScrollCraftsFromPaperAndItsSpellsMaterial(final GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		List<String> wrong = new ArrayList<>();
		for (SpellScrollItem scroll : Spellbooks.SCROLLS) {
			ItemStack material = new ItemStack(scroll.definition().ingredient());
			boolean doubled = Spellbooks.SCROLLS_WITH_DOUBLE_MATERIAL.contains(scroll.definition().id());
			CraftingInput input = doubled
				? CraftingInput.of(3, 1, List.of(new ItemStack(Items.PAPER), material, material.copy()))
				: CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), material));
			ItemStack result = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input))
				.orElse(ItemStack.EMPTY);
			if (!result.is(scroll) || result.getCount() < 2) {
				wrong.add(BuiltInRegistries.ITEM.getKey(scroll) + "=" + result);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "paper and the material did not craft the scroll: " + wrong);
		LOG.info("[SPELLTEST] test=everyScrollCraftsFromPaperAndItsSpellsMaterial RESULT scrolls={}", Spellbooks.SCROLLS.size());
		helper.succeed();
	}

	/** The client reads an item's tint from its item definition, so each book and scroll carries its spell's colour there. */
	@GameTest
	public void everyBookAndScrollCarriesItsTintWhereTheClientReadsIt(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			List<Identifier> items = Spellbooks.BOOK_ONLY.contains(definition.id()) ? List.of(definition.id()) : List.of(definition.id(), Spellbooks.scrollId(definition.id()));
			for (Identifier item : items) {
				Integer tint = constantTint(item);
				if (tint == null || tint != definition.tintRgb()) {
					wrong.add(item + "=" + tint);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "item definitions without their spell's tint: " + wrong);
		LOG.info("[SPELLTEST] test=everyBookAndScrollCarriesItsTintWhereTheClientReadsIt RESULT items={}", SpellbookCatalog.DEFINITIONS.size() * 2);
		helper.succeed();
	}

	private static Integer constantTint(final Identifier item) {
		String path = "/assets/" + item.getNamespace() + "/items/" + item.getPath() + ".json";
		try (InputStream in = Spellbooks.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model");
			JsonArray tints = model == null ? null : model.getAsJsonArray("tints");
			if (tints == null || tints.isEmpty()) {
				return null;
			}
			JsonObject tint = tints.get(0).getAsJsonObject();
			return "minecraft:constant".equals(tint.get("type").getAsString()) ? tint.get("value").getAsInt() : null;
		} catch (java.io.IOException | RuntimeException error) {
			return null;
		}
	}

	private static SpellScrollItem scroll(final Identifier spell) {
		Item item = BuiltInRegistries.ITEM.getValue(Spellbooks.scrollId(spell));
		return (SpellScrollItem) item;
	}

	// A durable zombie six blocks ahead, on a floor so it stays in the aim line.
	private static Mob target(final GameTestHelper helper) {
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}

	// A survival caster, because a creative player never uses up a consumable.
	private static ServerPlayer survivalCaster(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "scroll-caster"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}
}
