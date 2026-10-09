package com.stella.spells;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Spectral Quiver lets a bow or a crossbow draw and fire with no arrow carried. The client runs the same
 * {@code use} as the server and refuses a draw it sees no projectile for, so the answer must not depend on a
 * {@code ServerPlayer}. Each case plays the whole draw: use, hold, release, and the arrow that results.
 */
public class SpectralQuiverDrawGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.quiverdraw");
	private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

	@GameTest(maxTicks = 40)
	public void bowDrawsAndFiresAnUnpickableArrowUnderTheQuiverWithNoArrowCarried(final GameTestHelper helper) {
		ServerPlayer player = bare(helper, new ItemStack(Items.BOW), true);
		List<AbstractArrow> fired = drawAndRelease(helper, player);
		AbstractArrow.Pickup pickup = fired.isEmpty() ? null : fired.get(0).pickup;
		LOG.info("[BEHAVIOR] test=bowDrawsAndFiresAnUnpickableArrowUnderTheQuiverWithNoArrowCarried RESULT fired={} pickup={} carried={}",
			fired.size(), pickup, player.getInventory().countItem(Items.ARROW));
		helper.assertTrue(fired.size() == 1, "the bow fired " + fired.size() + " arrows, not one");
		helper.assertTrue(pickup != AbstractArrow.Pickup.ALLOWED, "the quiver arrow can be picked up: a free arrow farm");
		helper.assertTrue(pickup == AbstractArrow.Pickup.CREATIVE_ONLY, "the quiver arrow pickup is " + pickup);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void crossbowLoadsAndFiresUnderTheQuiverWithNoArrowCarried(final GameTestHelper helper) {
		ServerPlayer player = bare(helper, new ItemStack(Items.CROSSBOW), true);
		ItemStack crossbow = player.getItemInHand(HAND);
		InteractionResult draw = crossbow.use(helper.getLevel(), player, HAND);
		boolean drawing = player.isUsingItem();
		// Vanilla loads the bolt in onUseTick once the charge is full, never in releaseUsing.
		int fullCharge = crossbow.getUseDuration(player) - CrossbowItem.getChargeDuration(crossbow, player);
		crossbow.getItem().onUseTick(helper.getLevel(), player, crossbow, fullCharge);
		boolean loaded = crossbow.getItem().releaseUsing(crossbow, helper.getLevel(), player, fullCharge);
		Object charged = crossbow.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
		player.stopUsingItem();
		InteractionResult shoot = crossbow.use(helper.getLevel(), player, HAND);
		List<AbstractArrow> fired = firedBy(helper, player);
		AbstractArrow.Pickup pickup = fired.isEmpty() ? null : fired.get(0).pickup;
		LOG.info("[BEHAVIOR] test=crossbowLoadsAndFiresUnderTheQuiverWithNoArrowCarried RESULT draw={} drawing={} loaded={} charged={} shoot={} fired={} pickup={}",
			draw, drawing, loaded, charged, shoot, fired.size(), pickup);
		helper.assertTrue(draw instanceof InteractionResult.Success && drawing, "the crossbow refused to start loading: " + draw);
		helper.assertTrue(loaded, "the crossbow never loaded: " + charged);
		helper.assertTrue(fired.size() == 1, "the crossbow fired " + fired.size() + " arrows, not one");
		helper.assertTrue(pickup == AbstractArrow.Pickup.CREATIVE_ONLY, "the crossbow quiver arrow pickup is " + pickup);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void bowRefusesTheDrawWithNoArrowAndNoQuiver(final GameTestHelper helper) {
		ServerPlayer player = bare(helper, new ItemStack(Items.BOW), false);
		ItemStack bow = player.getItemInHand(HAND);
		InteractionResult result = bow.use(helper.getLevel(), player, HAND);
		LOG.info("[BEHAVIOR] test=bowRefusesTheDrawWithNoArrowAndNoQuiver RESULT result={} using={}", result, player.isUsingItem());
		helper.assertTrue(result instanceof InteractionResult.Fail, "a bow with no arrow and no quiver answered " + result);
		helper.assertFalse(player.isUsingItem(), "the bow started a draw with nothing to fire");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void quiverAnswersForAPlayerThatIsNotAServerPlayer(final GameTestHelper helper) {
		Player remote = remotePlayer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		remote.setItemInHand(HAND, bow);
		ItemStack without = remote.getProjectile(bow);
		remote.addEffect(new MobEffectInstance(SpellStatus.SPECTRAL_QUIVER, ArcherSkills.SPECTRAL_QUIVER_TICKS, 0));
		ItemStack with = remote.getProjectile(bow);
		InteractionResult result = bow.use(helper.getLevel(), remote, HAND);
		LOG.info("[BEHAVIOR] test=quiverAnswersForAPlayerThatIsNotAServerPlayer RESULT serverPlayer={} without={} with={} result={} using={}",
			remote instanceof ServerPlayer, without.isEmpty(), with, result, remote.isUsingItem());
		helper.assertFalse(remote instanceof ServerPlayer, "the harness built a server player: the case proves nothing");
		helper.assertTrue(without.isEmpty(), "a remote player with no quiver was handed " + without);
		helper.assertFalse(with.isEmpty(), "the quiver answered nothing for a player that is not a server player");
		helper.assertTrue(result instanceof InteractionResult.Success && remote.isUsingItem(),
			"the draw did not start for a player that is not a server player: " + result);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aCarriedArrowIsSpentFirstUnderTheQuiver(final GameTestHelper helper) {
		ServerPlayer player = bare(helper, new ItemStack(Items.BOW), true);
		player.getInventory().setItem(20, new ItemStack(Items.ARROW, 5));
		List<AbstractArrow> fired = drawAndRelease(helper, player);
		AbstractArrow.Pickup pickup = fired.isEmpty() ? null : fired.get(0).pickup;
		int left = player.getInventory().countItem(Items.ARROW);
		LOG.info("[BEHAVIOR] test=aCarriedArrowIsSpentFirstUnderTheQuiver RESULT fired={} left={} pickup={}", fired.size(), left, pickup);
		helper.assertTrue(fired.size() == 1, "the bow fired " + fired.size() + " arrows, not one");
		helper.assertTrue(left == 4, "the carried stack holds " + left + " arrows, not four");
		helper.assertTrue(pickup == AbstractArrow.Pickup.ALLOWED, "a carried arrow lost its pickup: " + pickup);
		helper.succeed();
	}

	/** A survival player with an empty pack and one weapon in hand. */
	private static ServerPlayer bare(final GameTestHelper helper, final ItemStack weapon, final boolean quiver) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.setItemInHand(HAND, weapon);
		if (quiver) {
			player.addEffect(new MobEffectInstance(SpellStatus.SPECTRAL_QUIVER, ArcherSkills.SPECTRAL_QUIVER_TICKS, 0));
		}
		return player;
	}

	/** Uses the bow, holds it past a full draw, releases it, and returns the arrows this player loosed. */
	private static List<AbstractArrow> drawAndRelease(final GameTestHelper helper, final ServerPlayer player) {
		ItemStack bow = player.getItemInHand(HAND);
		InteractionResult started = bow.use(helper.getLevel(), player, HAND);
		helper.assertTrue(started instanceof InteractionResult.Success && player.isUsingItem(),
			"the bow refused to start the draw: " + started);
		bow.releaseUsing(helper.getLevel(), player, bow.getUseDuration(player) - 20);
		return firedBy(helper, player);
	}

	private static List<AbstractArrow> firedBy(final GameTestHelper helper, final ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(16.0),
			arrow -> arrow.getOwner() == player);
	}

	private static Player remotePlayer(final GameTestHelper helper) {
		return new Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "remote")) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
	}
}
