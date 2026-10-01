package com.stella.spells;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Scroll of Return carries its reader to their respawn point in the Overworld: their bed when they have
 * one, and the world spawn otherwise, from any dimension. It crumbles when read and rests before the next.
 */
public class ScrollOfReturnGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.return");
	/**
	 * How far from the world spawn a player may land: vanilla spreads spawning players over the spawn radius,
	 * and a test area sits millions of blocks away, so this bound still tells a return from staying put.
	 */
	private static final double WORLD_SPAWN_REACH = 32.0;

	@GameTest(maxTicks = 40)
	public void aScrollOfReturnCarriesThePlayerToTheirBed(final GameTestHelper helper) {
		floor(helper);
		BlockPos foot = helper.absolutePos(new BlockPos(1, 1, 7));
		placeBed(helper, foot);
		ServerPlayer player = survivalPlayer(helper, new Vec3(6.5, 1.0, 1.5));
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, foot, 0.0F, 0.0F), true), false);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_OF_RETURN, 2));
		double before = player.position().distanceTo(Vec3.atBottomCenterOf(foot));
		player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		double after = player.position().distanceTo(Vec3.atBottomCenterOf(foot));
		int left = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
		leave(helper, player);
		LOG.info("[SPELLTEST] test=aScrollOfReturnCarriesThePlayerToTheirBed RESULT before={} after={} left={}", before, after, left);
		helper.assertTrue(after <= 2.5, "the reader stands " + after + " blocks from their bed, not beside it");
		helper.assertTrue(left == 1, "reading the scroll left " + left + " of 2");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aScrollOfReturnWithNoBedGoesToTheWorldSpawn(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = survivalPlayer(helper, new Vec3(4.5, 1.0, 4.5));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_OF_RETURN));
		BlockPos spawn = helper.getLevel().getServer().getRespawnData().pos();
		player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		double dx = player.getX() - (spawn.getX() + 0.5);
		double dz = player.getZ() - (spawn.getZ() + 0.5);
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean overworld = player.level().dimension() == Level.OVERWORLD;
		leave(helper, player);
		LOG.info("[SPELLTEST] test=aScrollOfReturnWithNoBedGoesToTheWorldSpawn RESULT distance={} reach={} overworld={}", horizontal, WORLD_SPAWN_REACH, overworld);
		helper.assertTrue(overworld && horizontal <= WORLD_SPAWN_REACH, "with no bed the reader landed " + horizontal + " blocks from the world spawn");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aScrollOfReturnBringsThePlayerHomeFromTheNether(final GameTestHelper helper) {
		floor(helper);
		BlockPos foot = helper.absolutePos(new BlockPos(1, 1, 7));
		placeBed(helper, foot);
		ServerPlayer player = survivalPlayer(helper, new Vec3(6.5, 1.0, 1.5));
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, foot, 0.0F, 0.0F), true), false);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the test server has no Nether, so the case proves nothing");
		player.teleportTo(nether, 0.5, 70.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		boolean wasInNether = player.level() == nether;
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_OF_RETURN));
		player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
		boolean home = player.level().dimension() == Level.OVERWORLD;
		double after = player.position().distanceTo(Vec3.atBottomCenterOf(foot));
		leave(helper, player);
		LOG.info("[SPELLTEST] test=aScrollOfReturnBringsThePlayerHomeFromTheNether RESULT wasInNether={} home={} distance={}", wasInNether, home, after);
		helper.assertTrue(wasInNether, "the reader never reached the Nether, so the case proves nothing");
		helper.assertTrue(home && after <= 2.5, "from the Nether the reader landed in " + player.level().dimension() + " " + after + " blocks from their bed");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aScrollOfReturnRestsBeforeItCanBeReadAgain(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = survivalPlayer(helper, new Vec3(4.5, 1.0, 4.5));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_OF_RETURN, 3));
		player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean resting = player.getCooldowns().isOnCooldown(held);
		leave(helper, player);
		LOG.info("[SPELLTEST] test=aScrollOfReturnRestsBeforeItCanBeReadAgain RESULT resting={} left={} cooldown={}", resting, held.getCount(), ReturnScrollItem.COOLDOWN_TICKS);
		helper.assertTrue(resting, "the scroll can be read again at once");
		helper.succeed();
	}

	@GameTest
	public void theScrollOfReturnIsCrafted(final GameTestHelper helper) {
		var recipe = helper.getLevel().getServer().getRecipeManager()
			.byKey(ResourceKey.create(Registries.RECIPE, Spellbooks.SCROLL_OF_RETURN_ID));
		ItemStack made = recipe.isPresent() && recipe.get().value() instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless
			? shapeless.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(
				new ItemStack(net.minecraft.world.item.Items.PAPER), new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), new ItemStack(net.minecraft.world.item.Items.FEATHER))))
			: ItemStack.EMPTY;
		LOG.info("[SPELLTEST] test=theScrollOfReturnIsCrafted RESULT recipe={} made={}", recipe.isPresent(), made);
		helper.assertTrue(made.is(Spellbooks.SCROLL_OF_RETURN), "paper, an ender pearl and a feather do not craft a Scroll of Return");
		helper.assertTrue(made.getCount() == 16, "the recipe gives " + made.getCount() + " scrolls, not 16");
		helper.succeed();
	}

	private static void placeBed(final GameTestHelper helper, final BlockPos foot) {
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		helper.getLevel().setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.getLevel().setBlockAndUpdate(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper, final Vec3 relative) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
		Vec3 at = helper.absoluteVec(relative);
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
