package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servant keeps every piece of its kit across a death and a resummon: all six equipment slots, and
 * every stack it had gathered into its pack, by item and by count.
 */
public class SummonKitGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summonkit");

	/** One distinct item per equipment slot, so a slot that comes back wrong cannot hide behind another. */
	private static final Item[] GEAR_ITEMS = {
		Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
		Items.DIAMOND_SWORD, Items.SHIELD
	};
	/** Distinct stacks with counts above one, so a copy that loses its size is caught. */
	private static final Item[] PACK_ITEMS = {
		Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.BONE, Items.ARROW,
		Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.COPPER_INGOT, Items.QUARTZ, Items.AMETHYST_SHARD,
		Items.FLINT, Items.PRISMARINE_SHARD, Items.GLOWSTONE_DUST, Items.SLIME_BALL, Items.STRING, Items.FEATHER
	};

	@GameTest(maxTicks = 100)
	public void aServantKeepsEverySlotAndStackAcrossAResummon(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aServantKeepsEverySlotAndStackAcrossAResummon");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton first = servant(helper, master, 3, 3);
		List<String> wanted = new ArrayList<>();
		for (int i = 0; i < SkeletonMenu.GEAR.length; i++) {
			ItemStack stack = new ItemStack(GEAR_ITEMS[i]);
			SummonSkeleton.equip(first, SkeletonMenu.GEAR[i], stack);
			wanted.add(SkeletonMenu.GEAR[i] + "=" + describe(stack));
		}
		List<ItemStack> pack = new ArrayList<>();
		for (int i = 0; i < SummonSkeleton.PACK_SIZE; i++) {
			ItemStack stack = new ItemStack(PACK_ITEMS[i], i + 2);
			pack.add(stack);
			wanted.add("slot" + i + "=" + describe(stack));
		}
		SummonSkeleton.setPack(first, pack);
		List<String> before = read(first);

		first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(!first.isAlive(), "the servant survived a lethal blow, so the case proves nothing");
		clear(helper, master);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton second = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(second != null && second != first, "the second cast raised no new servant");
		List<String> after = read(second);

		List<String> lost = new ArrayList<>();
		for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
			String was = i < before.size() ? before.get(i) : "missing";
			String now = i < after.size() ? after.get(i) : "missing";
			if (!was.equals(now)) {
				lost.add((i < SkeletonMenu.GEAR.length ? "gear:" + SkeletonMenu.GEAR[i] : "pack:" + (i - SkeletonMenu.GEAR.length))
					+ " was " + was + " now " + now);
			}
		}
		LOG.info("[BEHAVIOR] test=aServantKeepsEverySlotAndStackAcrossAResummon RESULT equipped={} packed={} kept={} lost={}",
			SkeletonMenu.GEAR.length, SummonSkeleton.PACK_SIZE, after.size() - lost.size(), lost);
		helper.assertTrue(wanted.size() == SkeletonMenu.GEAR.length + SummonSkeleton.PACK_SIZE,
			"the case did not stock every slot: " + wanted.size());
		helper.assertTrue(lost.isEmpty(), "the resummoned servant lost " + lost.size() + " of its kit: " + lost);
		second.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void theServantLevelShareAnswersTheTrueLevel(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theServantLevelShareAnswersTheTrueLevel");
		helper.setTime(18_000L);
		floor(helper);
		Object shared = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare()
			.get(SummonSkeleton.LEVEL_SHARE_KEY);
		helper.assertTrue(shared instanceof java.util.function.Function<?, ?>,
			"no level function at " + SummonSkeleton.LEVEL_SHARE_KEY + ": " + shared);
		@SuppressWarnings("unchecked")
		java.util.function.Function<net.minecraft.world.entity.Entity, Integer> levelOf =
			(java.util.function.Function<net.minecraft.world.entity.Entity, Integer>) shared;
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		int trueAtSpawn = SummonSkeleton.level(servant);
		int atSpawn = levelOf.apply(servant);
		Mob stranger = foe(helper, 5, 5);
		int forStranger = levelOf.apply(stranger);
		SummonSkeleton.gain(servant, master, SummonSkeleton.xpNeeded(servant));
		int trueAfter = SummonSkeleton.level(servant);
		int afterLevel = levelOf.apply(servant);
		LOG.info("[BEHAVIOR] test=theServantLevelShareAnswersTheTrueLevel RESULT atSpawn={} trueAtSpawn={} forStranger={} afterLevel={} trueAfter={}",
			atSpawn, trueAtSpawn, forStranger, afterLevel, trueAfter);
		helper.assertTrue(atSpawn == trueAtSpawn && atSpawn == 0,
			"the share called a fresh servant level " + atSpawn + " for a true level of " + trueAtSpawn);
		helper.assertTrue(forStranger == -1, "the share answered " + forStranger + " for a mob that is not a servant");
		helper.assertTrue(afterLevel == trueAfter && afterLevel == 1,
			"after one level the share answered " + afterLevel + " for a true level of " + trueAfter);
		servant.discard();
		stranger.discard();
		leave(helper, master);
		helper.succeed();
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int z) {
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a control mob");
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** Every slot of the kit as "itemxcount", gear first, so two servants compare piece by piece. */
	private static List<String> read(final Skeleton skeleton) {
		List<String> out = new ArrayList<>();
		for (EquipmentSlot slot : SkeletonMenu.GEAR) {
			out.add(describe(skeleton.getItemBySlot(slot)));
		}
		for (ItemStack stack : SummonSkeleton.pack(skeleton)) {
			out.add(describe(stack));
		}
		return out;
	}

	private static String describe(final ItemStack stack) {
		if (stack.isEmpty()) {
			return "empty";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
	}

	private static void clear(final GameTestHelper helper, final ServerPlayer master) {
		master.getCooldowns().removeCooldown(master.getCooldowns().getCooldownGroup(master.getMainHandItem()));
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master, final int x, final int z) {
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the summon raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		servant.teleportTo(at.x, at.y, at.z);
		servant.setNoAi(false);
		return servant;
	}

	private static SpellbookItem book() {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
	}

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
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
