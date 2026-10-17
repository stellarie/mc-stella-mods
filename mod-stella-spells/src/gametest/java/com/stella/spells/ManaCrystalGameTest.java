package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Mana Crystal: amethyst and lapis drop it to a player's fair break, and it makes four scrolls from one craft. */
public class ManaCrystalGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.manacrystal");

	@GameTest
	public void lapisBreakDropsACrystalUnlessSilkTouched(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer miner = survivalMiner(helper);
		ManaCrystal.setDropRoller((player, random, chance) -> true);
		try {
			ItemStack silk = new ItemStack(Items.STONE_PICKAXE);
			silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
			int lapis = breakBlock(helper, miner, target, Blocks.LAPIS_ORE, new ItemStack(Items.STONE_PICKAXE));
			int deepslate = breakBlock(helper, miner, target, Blocks.DEEPSLATE_LAPIS_ORE, new ItemStack(Items.STONE_PICKAXE));
			int amethyst = breakBlock(helper, miner, target, Blocks.AMETHYST_CLUSTER, new ItemStack(Items.STONE_PICKAXE));
			int silked = breakBlock(helper, miner, target, Blocks.LAPIS_ORE, silk);
			int stone = breakBlock(helper, miner, target, Blocks.STONE, new ItemStack(Items.STONE_PICKAXE));
			LOG.info("[SPELLTEST] test=lapisBreakDropsACrystal RESULT lapis={} deepslate={} amethyst={} silk={} stone={}", lapis, deepslate, amethyst, silked, stone);
			helper.assertTrue(lapis == 1 && deepslate == 1 && amethyst == 1, "a fair break dropped lapis=" + lapis + " deepslate=" + deepslate + " amethyst=" + amethyst);
			helper.assertTrue(silked == 0, "a silk touch break dropped " + silked + " crystals");
			helper.assertTrue(stone == 0, "a stone break dropped " + stone + " crystals");
		} finally {
			ManaCrystal.setDropRoller(null);
		}
		helper.succeed();
	}

	@GameTest
	public void aCrystalMakesFourOfEveryScroll(final GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		List<String> wrong = new ArrayList<>();
		for (SpellScrollItem scroll : Spellbooks.SCROLLS) {
			ItemStack material = new ItemStack(scroll.definition().ingredient());
			boolean doubled = Spellbooks.SCROLLS_WITH_DOUBLE_MATERIAL.contains(scroll.definition().id());
			List<ItemStack> stacks = new ArrayList<>(List.of(new ItemStack(Items.PAPER), material, new ItemStack(ManaCrystal.ITEM)));
			if (doubled) {
				stacks.add(1, material.copy());
			}
			CraftingInput input = CraftingInput.of(stacks.size(), 1, stacks);
			ItemStack result = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input))
				.orElse(ItemStack.EMPTY);
			if (!result.is(scroll) || result.getCount() != 4) {
				wrong.add(BuiltInRegistries.ITEM.getKey(scroll) + "=" + result);
			}
		}
		LOG.info("[SPELLTEST] test=aCrystalMakesFourOfEveryScroll RESULT scrolls={} wrong={}", Spellbooks.SCROLLS.size(), wrong.size());
		helper.assertTrue(wrong.isEmpty(), "paper, the material and a crystal did not craft four scrolls: " + wrong);
		helper.succeed();
	}

	/** Breaks the block with the tool as a survival player, and counts the crystals left on the ground. */
	private static int breakBlock(final GameTestHelper helper, final ServerPlayer miner, final BlockPos target, final Block block, final ItemStack tool) {
		ServerLevel level = helper.getLevel();
		miner.setItemInHand(InteractionHand.MAIN_HAND, tool);
		miner.getInventory().setSelectedItem(tool);
		level.setBlockAndUpdate(target, block.defaultBlockState());
		helper.assertTrue(miner.gameMode.destroyBlock(target), block + " did not break");
		int found = 0;
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2.0))) {
			if (drop.getItem().is(ManaCrystal.ITEM)) {
				found += drop.getItem().getCount();
			}
			drop.discard();
		}
		return found;
	}

	private static ServerPlayer survivalMiner(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "crystal-miner"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 4.5));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		return player;
	}
}
