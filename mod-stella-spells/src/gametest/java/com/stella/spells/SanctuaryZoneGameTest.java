package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sanctuary: a fixed zone that regenerates the players in it and burns the undead in it. */
public class SanctuaryZoneGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.sanctuary");
	/** Shrinks the 200-tick zone to 50 ticks, so the test sees it end. */
	private static final double MULTIPLIER = 0.25;
	private static final int ZONE_TICKS = 50;

	@GameTest(maxTicks = 120)
	public void sanctuaryZoneRegeneratesPlayersAndBurnsUndead(final GameTestHelper helper) {
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final Player player) {
				return 1.0;
			}

			@Override
			public double durationMultiplier(final LivingEntity caster) {
				return MULTIPLIER;
			}
		});
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 12; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.setTime(18_000L);
		ServerPlayer caster = player(helper, new Vec3(2.5, 1.0, 1.5));
		ServerPlayer inside = player(helper, new Vec3(3.5, 1.0, 1.5));
		ServerPlayer outside = player(helper, new Vec3(2.5, 1.0, 9.5));
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 3).withNoFreeWill().spawn();
		Mob cow = helper.spawnMob(EntityTypes.COW, 3, 1, 2).withNoFreeWill().spawn();
		for (Mob mob : new Mob[] {zombie, cow}) {
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
			mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			mob.setHealth(100.0F);
		}
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "sanctuary"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Sanctuary did not cast: " + result);
		helper.assertTrue(SpellTimers.sanctuaryZones() == 1, "the cast placed " + SpellTimers.sanctuaryZones() + " zones");

		float[] zombieAtMove = new float[1];
		helper.runAfterDelay(25, () -> {
			boolean regen = inside.hasEffect(MobEffects.REGENERATION);
			boolean outsideRegen = outside.hasEffect(MobEffects.REGENERATION);
			zombieAtMove[0] = zombie.getHealth();
			LOG.info("[SPELLTEST] test=sanctuaryZone RESULT t=25 insideRegen={} outsideRegen={} zombie={} cow={}", regen, outsideRegen, zombie.getHealth(), cow.getHealth());
			helper.assertTrue(regen, "a player inside got no Regeneration");
			helper.assertTrue(!outsideRegen, "a player outside got Regeneration");
			helper.assertTrue(zombie.getHealth() < 100.0F, "a zombie inside lost nothing");
			helper.assertTrue(cow.getHealth() == 100.0F, "a cow inside lost health: " + cow.getHealth());
			caster.removeAllEffects();
			Vec3 away = helper.absoluteVec(new Vec3(2.5, 1.0, 11.5));
			caster.setPos(away.x, away.y, away.z);
		});
		helper.runAfterDelay(45, () -> {
			boolean casterRegen = caster.hasEffect(MobEffects.REGENERATION);
			LOG.info("[SPELLTEST] test=sanctuaryZone RESULT t=45 zombie={} before={} casterRegen={} insideRegen={}", zombie.getHealth(), zombieAtMove[0], casterRegen, inside.hasEffect(MobEffects.REGENERATION));
			helper.assertTrue(!casterRegen, "the zone followed the caster");
			helper.assertTrue(zombie.getHealth() < zombieAtMove[0], "the zone stopped when the caster left");
			helper.assertTrue(inside.hasEffect(MobEffects.REGENERATION), "the zone stopped regenerating the player inside");
		});
		helper.runAfterDelay(ZONE_TICKS + 20, () -> {
			int zones = SpellTimers.sanctuaryZones();
			LOG.info("[SPELLTEST] test=sanctuaryZone RESULT zones after {} ticks={}", ZONE_TICKS + 20, zones);
			helper.assertTrue(zones == 0, "the zone outlived its duration");
			RpgBridge.setStats(null);
			for (ServerPlayer player : new ServerPlayer[] {caster, inside, outside}) {
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	private static ServerPlayer player(final GameTestHelper helper, final Vec3 local) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(local);
		player.setPos(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}
}
