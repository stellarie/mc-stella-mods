package com.stella.learn;

import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import com.stella.spells.SpellCasting;
import com.stella.spells.SpellbookItem;
import com.stella.spells.SummonSkeleton;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The summoned skeleton takes its combat mode from the weapon in its hand, through the mobs mod's role goals.
 * The spells, mobs and learn mods are all loaded here. The staff is a blaze rod, which the test pack lists as one.
 */
public class ServantModesGameTest {
	private static final int SWAP_TICKS = 60;
	private static final int MOVE_TICKS = 160;
	private static final double CLOSE_IN = 3.0;

	@GameTest(maxTicks = 400, padding = LearnArena.PADDING)
	public void theServantTakesTheModeOfItsHeldWeapon(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer master = LearnArena.player(helper, 24.5, 24.5);
		Skeleton servant = servant(helper, master);
		LearnArena.whenReady(helper, List.of(master, servant), () -> swap(helper, servant, new ItemStack(Items.IRON_SWORD), CombatRole.MELEE, false,
			() -> swap(helper, servant, new ItemStack(Items.BOW), CombatRole.RANGED, true,
				() -> swap(helper, servant, new ItemStack(Items.CROSSBOW), CombatRole.RANGED, true,
					() -> swap(helper, servant, new ItemStack(Items.BLAZE_ROD), CombatRole.MAGIC, true,
						() -> swap(helper, servant, ItemStack.EMPTY, CombatRole.MELEE, false, () -> finish(helper, servant)))))));
	}

	@GameTest(maxTicks = 500, padding = LearnArena.PADDING)
	public void aRangedServantKeepsOffAFoeAndASwordServantClosesIn(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer master = LearnArena.player(helper, 24.5, 24.5);
		ServerPlayer stranger = LearnArena.player(helper, 30.5, 24.5);
		Skeleton servant = servant(helper, master);
		Vec3 at = helper.absoluteVec(new Vec3(24.5, LearnArena.SPAWN_Y, 28.5));
		servant.teleportTo(at.x, at.y, at.z);
		Mob foe = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 31).spawn();
		foe.setNoAi(true);
		// A mob never targets an invulnerable body, so the foe is only too tough to kill.
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		foe.setHealth(100_000.0F);
		foe.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		final boolean[] turned = {false};
		helper.onEachTick(() -> {
			turned[0] |= servant.getTarget() instanceof Player;
			stranger.setInvulnerableTime(0);
		});
		LearnArena.whenReady(helper, List.of(master, servant, foe), () -> {
			// A bow in hand, and a foe three blocks off: inside the ranged band's near edge.
			SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			helper.runAfterDelay(5L, () -> servant.hurtServer(helper.getLevel(), stranger.damageSources().playerAttack(stranger), 1.0F));
			LearnArena.pollUntil(helper, () -> servant.distanceTo(foe) >= RoleSpec.RANGED_NEAR - 0.25, MOVE_TICKS, () -> {
				double kept = servant.distanceTo(foe);
				SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				LearnArena.pollUntil(helper, () -> servant.distanceTo(foe) <= CLOSE_IN, MOVE_TICKS, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aRangedServantKeepsOffAFoeAndASwordServantClosesIn RESULT kept={} closed={} turnedOnPlayer={}",
						kept, servant.distanceTo(foe), turned[0]);
					helper.assertFalse(turned[0], "the servant targeted a player");
					finish(helper, servant);
				}, () -> "the servant with a sword stayed " + servant.distanceTo(foe) + " blocks from its foe");
			}, () -> "the servant with a bow stood " + servant.distanceTo(foe) + " blocks from a foe inside its near band");
		});
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the cast raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(26.5, LearnArena.SPAWN_Y, 26.5));
		servant.teleportTo(at.x, at.y, at.z);
		return servant;
	}

	private static void swap(final GameTestHelper helper, final Skeleton servant, final ItemStack weapon, final CombatRole role,
		final boolean holdsBand, final Runnable next) {
		SummonSkeleton.equip(servant, EquipmentSlot.MAINHAND, weapon);
		LearnArena.pollUntil(helper, () -> modeMatches(servant, role, holdsBand), SWAP_TICKS, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theServantTakesTheModeOfItsHeldWeapon RESULT weapon={} role={} band={}",
				weapon.getItem(), role, holdsBand);
			next.run();
		}, () -> "the servant holding " + weapon.getItem() + " has role " + ReactiveState.get(servant).getRole()
			+ " and band goal " + hasBand(servant) + ", wanted " + role + " and " + holdsBand);
	}

	private static boolean modeMatches(final Skeleton servant, final CombatRole role, final boolean holdsBand) {
		return ReactiveState.get(servant).getRole() == role && hasBand(servant) == holdsBand;
	}

	private static boolean hasBand(final Skeleton servant) {
		return servant.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof KeepDistanceGoal<?>);
	}

	private static void finish(final GameTestHelper helper, final Skeleton servant) {
		servant.discard();
		helper.succeed();
	}
}
