package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A summoned skeleton grows with its master when the RPG mod scales spells: thirty health plus a tenth of the
 * master's, in vanilla units, and a blow worth the master's MATK at 100 percent. Without the RPG mod it keeps
 * its thirty health and its own arrows.
 */
public class SummonScalingGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summonscaling");
	private static final String SHARE = SummonSkeleton.HEALTH_SCALE_SHARE;
	private static final double MASTER_HEALTH = 300.0;
	private static final double SCALE = 15.0;
	private static final double HIT_PER_PERCENT = 0.1;
	private static final float ARROW_HIT = 2.0F;

	/** A stand-in for the RPG mod that records every skill percent it is asked for. */
	private static final class Matk implements RpgBridge.Stats {
		final List<Double> percents = new ArrayList<>();

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double spellDamage(final LivingEntity caster, final double skillPercent) {
			this.percents.add(skillPercent);
			return skillPercent * HIT_PER_PERCENT;
		}
	}

	@GameTest(maxTicks = 100)
	public void summonScales(final GameTestHelper helper) {
		floor(helper);
		Object savedScale = FabricLoader.getInstance().getObjectShare().get(SHARE);
		Matk matk = new Matk();
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = null;
		try {
			FabricLoader.getInstance().getObjectShare().put(SHARE, SCALE);
			RpgBridge.setStats(matk);
			master.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MASTER_HEALTH);
			servant = summon(helper, master);
			double expectedBase = 30.0 + 0.1 * MASTER_HEALTH / SCALE;
			double expectedMax = Math.min(1024.0, expectedBase * (1.0 + threatBonus(helper)));
			double base = servant.getAttributeBaseValue(Attributes.MAX_HEALTH);

			Mob target = zombie(helper);
			matk.percents.clear();
			double arrow = hit(helper, target, arrowFrom(helper, servant));
			double melee = hit(helper, target, helper.getLevel().damageSources().mobAttack(servant));
			Skeleton stranger = stray(helper);
			double other = hit(helper, target, arrowFrom(helper, stranger));
			LOG.info("[SPELLTEST] test=summonScales RESULT base={} max={} expectedMax={} arrow={} melee={} stranger={} asked={}",
				base, servant.getMaxHealth(), expectedMax, arrow, melee, other, matk.percents);
			helper.assertTrue(Math.abs(base - expectedBase) < 1.0E-6, "the servant's base health is " + base + ", not " + expectedBase);
			helper.assertTrue(Math.abs(servant.getMaxHealth() - expectedMax) < 0.01, "the servant's max health is " + servant.getMaxHealth() + ", not " + expectedMax);
			helper.assertTrue(Math.abs(arrow - 100.0 * HIT_PER_PERCENT) < 1.0E-3, "the servant's arrow dealt " + arrow + ", not " + 100.0 * HIT_PER_PERCENT);
			helper.assertTrue(Math.abs(melee - 100.0 * HIT_PER_PERCENT) < 1.0E-3, "the servant's blow dealt " + melee);
			helper.assertTrue(matk.percents.equals(List.of(100.0, 100.0)), "the servant asked the RPG mod for " + matk.percents);
			helper.assertTrue(Math.abs(other - ARROW_HIT) < 1.0E-3, "a stranger's arrow dealt " + other + ", not its own " + ARROW_HIT);
			target.discard();
			stranger.discard();
		} finally {
			RpgBridge.setStats(null);
			restore(savedScale);
			if (servant != null) {
				servant.discard();
			}
			helper.getLevel().getServer().getPlayerList().remove(master);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void summonKeepsFlatWithoutRpg(final GameTestHelper helper) {
		floor(helper);
		RpgBridge.setStats(null);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = null;
		try {
			master.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MASTER_HEALTH);
			servant = summon(helper, master);
			Mob target = zombie(helper);
			double arrow = hit(helper, target, arrowFrom(helper, servant));
			double base = servant.getAttributeBaseValue(Attributes.MAX_HEALTH);
			LOG.info("[SPELLTEST] test=summonKeepsFlatWithoutRpg RESULT base={} arrow={}", base, arrow);
			helper.assertTrue(base == SummonSkeleton.BASE_HEALTH, "the servant's base health is " + base + " without the RPG mod");
			helper.assertTrue(Math.abs(arrow - ARROW_HIT) < 1.0E-3, "the servant's arrow dealt " + arrow + " without the RPG mod");
			target.discard();
		} finally {
			if (servant != null) {
				servant.discard();
			}
			helper.getLevel().getServer().getPlayerList().remove(master);
		}
		helper.succeed();
	}

	private static double threatBonus(final GameTestHelper helper) {
		return FabricLoader.getInstance().isModLoaded("mod-stella-mobs")
			? SummonThreatBridge.healthBonus(SummonThreatBridge.peak(helper.getLevel().getServer())) : 0.0;
	}

	private static void restore(final Object saved) {
		FabricLoader.getInstance().getObjectShare().put(SHARE, saved == null ? Double.valueOf(1.0) : saved);
	}

	private static double hit(final GameTestHelper helper, final Mob target, final DamageSource source) {
		target.setHealth(100.0F);
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		target.hurtServer(helper.getLevel(), source, ARROW_HIT);
		return before - target.getHealth();
	}

	private static DamageSource arrowFrom(final GameTestHelper helper, final Skeleton shooter) {
		Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		arrow.setOwner(shooter);
		return helper.getLevel().damageSources().arrow(arrow, shooter);
	}

	private static Skeleton stray(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 1.0, 5.5));
		Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		skeleton.setNoAi(true);
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	private static Mob zombie(final GameTestHelper helper) {
		helper.setTime(18_000L);
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 6, 1, 6).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static Skeleton summon(final GameTestHelper helper, final ServerPlayer master) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the summon did not raise a servant");
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		servant.teleportTo(at.x, at.y, at.z);
		servant.setNoAi(true);
		return servant;
	}

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
