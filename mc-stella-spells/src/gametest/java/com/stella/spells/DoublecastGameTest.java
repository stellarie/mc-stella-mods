package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Doublecast, on a helmet: half the casts land twice, on the same target. */
public class DoublecastGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.doublecast");
	/** Each caster's forced roll; a caster not listed rolls the shipped odds. */
	private static final ConcurrentHashMap<UUID, Boolean> FORCED = new ConcurrentHashMap<>();
	private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

	static {
		SpellCasting.setDoublecastRoll(caster -> {
			ASKED.add(caster.getUUID());
			Boolean forced = FORCED.get(caster.getUUID());
			return forced != null ? forced : caster.getRandom().nextFloat() < SpellCasting.DOUBLECAST_CHANCE;
		});
	}

	@GameTest
	public void aDoublecastLandsTheSpellTwice(final GameTestHelper helper) {
		float lost = castOnce(helper, true, true);
		LOG.info("[SPELLTEST] test=aDoublecastLandsTheSpellTwice RESULT lost={} single={}", lost, SpellCasting.DAMAGE);
		helper.assertTrue(Math.abs(lost - 2.0F * SpellCasting.DAMAGE) < 1.0E-4, "a doublecast Spark Shot took " + lost);
		helper.succeed();
	}

	@GameTest
	public void aFailedRollLandsItOnce(final GameTestHelper helper) {
		float lost = castOnce(helper, true, false);
		LOG.info("[SPELLTEST] test=aFailedRollLandsItOnce RESULT lost={}", lost);
		helper.assertTrue(Math.abs(lost - SpellCasting.DAMAGE) < 1.0E-4, "a single Spark Shot took " + lost);
		helper.succeed();
	}

	@GameTest
	public void noHelmetNoRoll(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper, false);
		FORCED.put(caster.getUUID(), true);
		Mob zombie = target(helper);
		float before = zombie.getHealth();
		SpellbookItem book = (SpellbookItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		boolean asked = ASKED.contains(caster.getUUID());
		helper.getLevel().getServer().getPlayerList().remove(caster);
		LOG.info("[SPELLTEST] test=noHelmetNoRoll RESULT asked={} lost={}", asked, before - zombie.getHealth());
		helper.assertTrue(!asked && Math.abs(before - zombie.getHealth() - SpellCasting.DAMAGE) < 1.0E-4, "a bare head rolled a doublecast");
		helper.succeed();
	}

	@GameTest
	public void theShippedRollIsHalf(final GameTestHelper helper) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(4242L);
		int twice = 0;
		int trials = 10_000;
		for (int i = 0; i < trials; i++) {
			twice += random.nextFloat() < SpellCasting.DOUBLECAST_CHANCE ? 1 : 0;
		}
		double rate = twice / (double) trials;
		LOG.info("[SPELLTEST] test=theShippedRollIsHalf RESULT rate={} chance={}", rate, SpellCasting.DOUBLECAST_CHANCE);
		helper.assertTrue(SpellCasting.DOUBLECAST_CHANCE == 0.5F && Math.abs(rate - 0.5) < 4 * Math.sqrt(0.25 / trials), "the doublecast chance is " + rate);
		helper.succeed();
	}

	private static float castOnce(final GameTestHelper helper, final boolean helmet, final boolean twice) {
		ServerPlayer caster = caster(helper, helmet);
		FORCED.put(caster.getUUID(), twice);
		Mob zombie = target(helper);
		float before = zombie.getHealth();
		SpellbookItem book = (SpellbookItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:spark_shot"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.getLevel().getServer().getPlayerList().remove(caster);
		return before - zombie.getHealth();
	}

	private static ServerPlayer caster(final GameTestHelper helper, final boolean helmet) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "doublecaster"), false);
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
		if (helmet) {
			ItemStack head = new ItemStack(Items.IRON_HELMET);
			head.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:doublecast"))), 1);
			player.setItemSlot(EquipmentSlot.HEAD, head);
		}
		return player;
	}

	private static Mob target(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
