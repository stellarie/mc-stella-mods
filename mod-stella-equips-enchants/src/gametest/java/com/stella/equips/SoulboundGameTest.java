package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.enchant.Soulbound;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Soulbound: an enchanted item stays with its owner through death and returns on respawn, even across
 * a save between the two, while an ordinary item still drops where the owner fell.
 */
public class SoulboundGameTest {
	private static final double DROP_SEARCH = 3.0;
	/** Where Fabric writes an entity's persistent attachments in its saved data. */
	private static final String FABRIC_ATTACHMENTS = "fabric:attachments";
	// Far past any health, but finite: an infinite hit turns health into NaN, which never reads as dead.
	private static final float LETHAL_DAMAGE = 1000.0F;

	@GameTest(maxTicks = 40)
	public void aSoulboundItemComesBackAfterDeath(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.getInventory().setItem(0, soulbound(helper, new ItemStack(Items.DIAMOND_SWORD)));
		player.setItemSlot(EquipmentSlot.CHEST, soulbound(helper, new ItemStack(Items.IRON_CHESTPLATE)));
		die(helper, player);
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		boolean sword = reborn.getInventory().contains(stack -> stack.is(Items.DIAMOND_SWORD));
		boolean chest = reborn.getInventory().contains(stack -> stack.is(Items.IRON_CHESTPLATE)) || reborn.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE);
		helper.assertTrue(sword && chest, "after respawn: sword=" + sword + " chestplate=" + chest);
		result("returns", "sword=true chestplate=true");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void anOrdinaryItemStillDropsWhereTheOwnerFell(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.getInventory().setItem(0, soulbound(helper, new ItemStack(Items.DIAMOND_SWORD)));
		player.getInventory().setItem(1, new ItemStack(Items.BLAZE_ROD, 3));
		Vec3 fell = player.position();
		die(helper, player);
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(fell, fell).inflate(DROP_SEARCH));
		boolean rodDropped = drops.stream().anyMatch(drop -> drop.getItem().is(Items.BLAZE_ROD) && drop.getItem().getCount() == 3);
		boolean swordDropped = drops.stream().anyMatch(drop -> drop.getItem().is(Items.DIAMOND_SWORD));
		boolean rodKept = reborn.getInventory().contains(stack -> stack.is(Items.BLAZE_ROD));
		helper.assertTrue(rodDropped && !rodKept && !swordDropped, "rodDropped=" + rodDropped + " rodKept=" + rodKept + " swordDropped=" + swordDropped);
		result("ordinaryDrops", "rodDropped=true swordDropped=false");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theHeldBackItemsSurviveASaveBeforeRespawn(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.getInventory().setItem(0, soulbound(helper, new ItemStack(Items.NETHERITE_PICKAXE)));
		die(helper, player);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		// Read back from the saved data itself, the way a restarted server would find it.
		Tag attachments = saved.getCompoundOrEmpty(FABRIC_ATTACHMENTS).get(Soulbound.STASH.identifier().toString());
		List<ItemStack> stash = attachments == null ? List.of() : ItemStack.CODEC.listOf()
			.parse(helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), attachments)
			.result().orElse(List.of());
		helper.assertTrue(stash.stream().anyMatch(stack -> stack.is(Items.NETHERITE_PICKAXE)), "the saved player data holds no held-back pickaxe: " + stash + " in " + saved.keySet());
		result("survivesSave", "stash=" + stash.size());
		helper.succeed();
	}

	private static void die(final GameTestHelper helper, final ServerPlayer player) {
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), LETHAL_DAMAGE);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
	}

	private static ItemStack soulbound(final GameTestHelper helper, final ItemStack stack) {
		var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Soulbound.ID)), 1);
		return stack;
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "soulbound-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		// A real client reports it has loaded; until then the server keeps the player from all harm.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	private static void result(final String test, final String detail) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test=soulbound.{} RESULT {}", test, detail);
	}
}
