package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.enchant.UndertowPull;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class UndertowGameTest {
	private static final double TOLERANCE = 1.0E-6;
	private static final double Y = 3.0;
	private static final long THROW_WAIT_TICKS = 12L;

	static {
		UndertowPull.setRecorder(new UndertowPull.Recorder() {
			@Override
			public void queued(final UUID owner, final UUID victim, final double length) {
				StellaEquips.LOGGER.info("[BEHAVIOR] undertow queued owner={} victim={} length={}", owner, victim, length);
			}

			@Override
			public void cancelled(final UUID owner, final UUID victim, final double length) {
				StellaEquips.LOGGER.info("[BEHAVIOR] undertow cancelled owner={} victim={} length={}", owner, victim, length);
			}

			@Override
			public void moved(final UUID owner, final UUID victim, final Vec3 ownerEnd, final Vec3 victimEnd) {
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] undertow moved owner={} victim={} ownerEnd={} victimEnd={}", owner, victim, ownerEnd, victimEnd
				);
			}

			@Override
			public void skipped(final UUID owner, final UUID victim, final String reason) {
				StellaEquips.LOGGER.info("[BEHAVIOR] undertow skipped owner={} victim={} reason={}", owner, victim, reason);
			}
		});
	}

	@GameTest
	public void undertowDirectHitMovesBothToMidpoint(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 1.5);
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 5.5);
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		owner.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
		owner.attack(victim);
		assertMidpointAfterDrain(helper, "direct", owner, victim, ownerStart, victimStart);
	}

	@GameTest
	public void undertowFullThreeDimensionalOffsetPreservesMidpoint(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 1.5);
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 4.5, Y + 3.0, 5.5);
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		owner.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
		owner.attack(victim);
		assertMidpointAfterDrain(helper, "threeDimensional", owner, victim, ownerStart, victimStart);
	}

	@GameTest(maxTicks = 100)
	public void undertowPlayerOwnedTridentMovesBothToMidpoint(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 0.5);
		assertThrownTridentMidpoint(helper, "playerThrown", owner);
	}

	@GameTest(maxTicks = 100)
	public void undertowMobOwnedTridentMovesBothToMidpoint(final GameTestHelper helper) {
		Mob owner = floating(helper, EntityTypes.DROWNED, 1.5, Y, 0.5);
		assertThrownTridentMidpoint(helper, "mobThrown", owner);
	}

	@GameTest(maxTicks = 100)
	public void undertowInvalidHitsDoNotMoveEitherActor(final GameTestHelper helper) {
		ServerPlayer close = playerAt(helper, 1.5, Y, 1.5);
		Mob closeVictim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 1.5 + UndertowPull.GAP * 0.9);
		Vec3 closeStart = close.position();
		Vec3 closeVictimStart = closeVictim.position();
		close.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
		close.attack(closeVictim);
		Mob target = floating(helper, EntityTypes.ZOMBIE, 4.5, Y, 6.5);
		Vec3 targetStart = target.position();
		Vec3 launch = helper.absoluteVec(new Vec3(4.5, Y + 0.8, 1.5));
		ThrownTrident ownerless = new ThrownTrident(helper.getLevel(), launch.x, launch.y, launch.z, undertowTrident(helper));
		ownerless.shoot(0.0, 0.0, 1.0, 2.0F, 0.0F);
		helper.getLevel().addFreshEntity(ownerless);
		helper.runAfterDelay(THROW_WAIT_TICKS, () -> {
			double moved = close.position().distanceTo(closeStart) + closeVictim.position().distanceTo(closeVictimStart)
				+ target.position().distanceTo(targetStart);
			float targetLost = target.getMaxHealth() - target.getHealth();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=undertowInvalid RESULT moved={} ownerlessHitLanded={}", moved, targetLost > 0.0F
			);
			helper.assertTrue(targetLost > 0.0F, "the ownerless trident never hit its target");
			helper.assertTrue(moved < TOLERANCE, "a close or ownerless hit moved an actor by " + moved);
			helper.succeed();
		});
	}

	@GameTest
	public void undertowBlockedMovesAreAtomic(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 1.5);
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 5.5);
		// A block where the owner would land; the victim's path stays clear.
		helper.setBlock(new BlockPos(1, (int) Y, 2), Blocks.STONE);
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		owner.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
		owner.attack(victim);
		helper.runAfterDelay(1L, () -> {
			double moved = owner.position().distanceTo(ownerStart) + victim.position().distanceTo(victimStart);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=undertowBlocked RESULT moved={}", moved);
			helper.assertTrue(moved < TOLERANCE, "a blocked pull moved an actor by " + moved);
			helper.succeed();
		});
	}

	@GameTest
	public void undertowMountedPairsStayUntouched(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 1.5);
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 5.5);
		Mob mount = floating(helper, EntityTypes.PIG, 1.5, Y, 5.5);
		boolean riding = victim.startRiding(mount, true, true);
		// The rider settles onto its seat first, so the starts are read a tick later.
		helper.runAfterDelay(1L, () -> {
			Vec3 ownerStart = owner.position();
			Vec3 victimStart = victim.position();
			owner.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
			owner.attack(victim);
			helper.runAfterDelay(1L, () -> {
				double moved = owner.position().distanceTo(ownerStart) + victim.position().distanceTo(victimStart);
				StellaEquips.LOGGER.info("[BEHAVIOR] test=undertowMounted RESULT riding={} moved={}", riding, moved);
				helper.assertTrue(riding && victim.isPassenger(), "the victim never mounted, so the case did not run");
				helper.assertTrue(moved < TOLERANCE, "a mounted pair moved by " + moved);
				helper.succeed();
			});
		});
	}

	@GameTest
	public void undertowLatestSameTickHitWins(final GameTestHelper helper) {
		ServerPlayer owner = playerAt(helper, 1.5, Y, 1.5);
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 5.5);
		owner.setItemInHand(InteractionHand.MAIN_HAND, undertowTrident(helper));
		// Both hits go through the vanilla hurt and post-attack path, so the attack cooldown cannot drop one.
		hit(helper, owner, victim, 1.0F);
		Vec3 moved = helper.absoluteVec(new Vec3(1.5, Y, 7.5));
		victim.setPos(moved.x, moved.y, moved.z);
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		// A stronger second hit passes the hurt cooldown, as it would in play.
		hit(helper, owner, victim, 2.0F);
		assertMidpointAfterDrain(helper, "latestWins", owner, victim, ownerStart, victimStart);
	}

	private static void hit(final GameTestHelper helper, final ServerPlayer owner, final Mob victim, final float amount) {
		victim.setInvulnerableTime(0);
		DamageSource source = helper.getLevel().damageSources().playerAttack(owner);
		helper.assertTrue(victim.hurtServer(helper.getLevel(), source, amount), "a same-tick hit did not land");
		EnchantmentHelper.doPostAttackEffects(helper.getLevel(), victim, source);
	}

	private static void assertThrownTridentMidpoint(final GameTestHelper helper, final String label, final LivingEntity owner) {
		Mob victim = floating(helper, EntityTypes.ZOMBIE, 1.5, Y, 7.5);
		Vec3 ownerStart = owner.position();
		Vec3 victimStart = victim.position();
		ThrownTrident trident = new ThrownTrident(helper.getLevel(), owner, undertowTrident(helper));
		Vec3 start = helper.absoluteVec(new Vec3(1.5, Y + 0.8, 1.5));
		trident.setPos(start.x, start.y, start.z);
		trident.shoot(0.0, 0.0, 1.0, 2.0F, 0.0F);
		helper.getLevel().addFreshEntity(trident);
		helper.runAfterDelay(THROW_WAIT_TICKS, () -> {
			float lost = victim.getMaxHealth() - victim.getHealth();
			helper.assertTrue(lost > 0.0F, "the " + label + " trident never hit the victim");
			assertAtMidpoint(helper, label, owner, victim, ownerStart, victimStart);
		});
	}

	private static void assertMidpointAfterDrain(
		final GameTestHelper helper, final String label, final LivingEntity owner, final LivingEntity victim,
		final Vec3 ownerStart, final Vec3 victimStart
	) {
		helper.runAfterDelay(1L, () -> assertAtMidpoint(helper, label, owner, victim, ownerStart, victimStart));
	}

	// The shipped rule: both end HALF_GAP either side of the pre-hit midpoint, along the owner-to-victim line.
	private static void assertAtMidpoint(
		final GameTestHelper helper, final String label, final LivingEntity owner, final LivingEntity victim,
		final Vec3 ownerStart, final Vec3 victimStart
	) {
		Vec3 offset = victimStart.subtract(ownerStart);
		Vec3 unit = offset.normalize();
		Vec3 midpoint = ownerStart.add(victimStart).scale(0.5);
		Vec3 ownerEnd = midpoint.subtract(unit.scale(UndertowPull.HALF_GAP));
		Vec3 victimEnd = midpoint.add(unit.scale(UndertowPull.HALF_GAP));
		double ownerError = owner.position().distanceTo(ownerEnd);
		double victimError = victim.position().distanceTo(victimEnd);
		double gap = owner.position().distanceTo(victim.position());
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=undertowMidpoint RESULT case={} startGap={} endGap={} ownerError={} victimError={}",
			label, offset.length(), gap, ownerError, victimError
		);
		helper.assertTrue(
			ownerError < TOLERANCE && victimError < TOLERANCE && Math.abs(gap - UndertowPull.GAP) < TOLERANCE,
			label + ": the owner ended " + ownerError + " and the victim " + victimError + " from their marks, "
				+ gap + " apart"
		);
		helper.succeed();
	}

	private static Mob floating(
		final GameTestHelper helper, final EntityType<? extends Mob> type, final double x, final double y, final double z
	) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		mob.setPos(at.x, at.y, at.z);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setPersistenceRequired();
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "the level refused the " + type);
		return mob;
	}

	// A survival player placed in the level, so the drain can find it by its id.
	private static ServerPlayer playerAt(final GameTestHelper helper, final double x, final double y, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
			new GameProfile(UUID.randomUUID(), "undertow-player"), false
		);
		ServerPlayer player = new ServerPlayer(
			helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()
		);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		return player;
	}

	private static ItemStack undertowTrident(final GameTestHelper helper) {
		Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(
				Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "weapon/undertow")
			));
		ItemStack stack = new ItemStack(Items.TRIDENT);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, 1);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}
}
