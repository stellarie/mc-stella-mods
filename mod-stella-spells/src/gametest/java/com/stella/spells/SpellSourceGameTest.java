package com.stella.spells;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RPG mod reads penetration and the spell crit mark from the damage source's attacker, so a player spell hit
 * must name its caster. Riftwalk, Gale Step, and the Scroll of Return keep a fixed cooldown.
 */
public class SpellSourceGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.source");
	private static final List<DamageSource> SEEN = new ArrayList<>();
	private static volatile boolean listening;
	private static boolean registered;

	@GameTest(maxTicks = 100)
	public void spellHitNamesTheCaster(final GameTestHelper helper) {
		listen();
		ServerPlayer caster = caster(helper);
		try {
			for (String spell : List.of("spark_shot", "dragonfall", "chainstorm")) {
				SEEN.clear();
				Mob zombie = zombie(helper, 4);
				helper.setBlock(2, 2, 6, Blocks.STONE);
				listening = true;
				cast(helper, caster, spell);
				listening = false;
				helper.setBlock(2, 2, 6, Blocks.AIR);
				zombie.discard();
				boolean named = !SEEN.isEmpty() && SEEN.stream().allMatch(source -> source.getEntity() == caster);
				boolean indirect = !SEEN.isEmpty() && SEEN.stream().allMatch(source -> source.is(DamageTypes.INDIRECT_MAGIC));
				LOG.info("[SPELLTEST] test=spellHitNamesTheCaster RESULT spell={} hits={} named={} indirect={}", spell, SEEN.size(), named, indirect);
				helper.assertTrue(!SEEN.isEmpty(), spell + " hurt nothing");
				helper.assertTrue(named, spell + " did not name its caster as the attacker");
				helper.assertTrue(indirect, spell + " is not indirect magic");
			}
		} finally {
			listening = false;
			helper.getLevel().getServer().getPlayerList().remove(caster);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void utilityCooldownIsFixed(final GameTestHelper helper) {
		ServerPlayer viewer = null;
		ServerPlayer scroller = null;
		boolean waiting = false;
		try {
			RpgBridge.setStats(new RpgBridge.Stats() {
				@Override
				public double power(final Player player) {
					return 1.0;
				}

				@Override
				public double cooldown(final Player player) {
					return 0.5;
				}
			});
			viewer = caster(helper);
			for (String spell : List.of("riftwalk", "gale_step", "spark_shot")) {
				ItemStack book = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell)).getDefaultInstance();
				SpellDefinition definition = ((SpellbookItem) book.getItem()).definition();
				int base = definition.baseCooldownTicks();
				int got = SpellTooltip.modifiers(viewer, book, definition).cooldownTicks();
				boolean fixed = SpellbookCatalog.FIXED_COOLDOWN.contains(spell);
				int expected = fixed ? base : Math.round(base * 0.5F);
				LOG.info("[SPELLTEST] test=utilityCooldownIsFixed RESULT spell={} base={} got={} expected={}", spell, base, got, expected);
				helper.assertTrue(got == expected, spell + " cooldown was " + got + ", not " + expected);
			}
			helper.assertTrue(SpellbookCatalog.FIXED_COOLDOWN.equals(Set.of("riftwalk", "gale_step")), "the fixed set is " + SpellbookCatalog.FIXED_COOLDOWN);

			scroller = survivalPlayer(helper);
			scroller.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_OF_RETURN, 3));
			scroller.getItemInHand(InteractionHand.MAIN_HAND).use(scroller.level(), scroller, InteractionHand.MAIN_HAND);
			ItemStack held = scroller.getItemInHand(InteractionHand.MAIN_HAND);
			ServerPlayer watched = scroller;
			waiting = true;
			helper.runAfterDelay(ReturnScrollItem.COOLDOWN_TICKS * 3L / 5L, () -> {
				boolean resting = watched.getCooldowns().isOnCooldown(held);
				helper.getLevel().getServer().getPlayerList().remove(watched);
				RpgBridge.setStats(null);
				LOG.info("[SPELLTEST] test=utilityCooldownIsFixed RESULT scroll resting at 60 ticks={}", resting);
				helper.assertTrue(resting, "the Scroll of Return rested less than its fixed cooldown");
				helper.succeed();
			});
		} finally {
			if (viewer != null) {
				helper.getLevel().getServer().getPlayerList().remove(viewer);
			}
			if (!waiting) {
				RpgBridge.setStats(null);
				if (scroller != null) {
					helper.getLevel().getServer().getPlayerList().remove(scroller);
				}
			}
		}
	}

	private static void listen() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (listening) {
				SEEN.add(source);
			}
		});
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, spell + " did not cast: " + result);
		// Dragonfall hits when its charge lands; the case reads the hit now.
		DragonCharge.landNow(caster);
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	// No armor, so a spell's damage is its number.
	private static Mob zombie(final GameTestHelper helper, final int z) {
		helper.setTime(18_000L);
		for (int i = 0; i <= 9; i++) {
			helper.setBlock(2, 0, i, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(1000.0F);
		return mob;
	}
}
