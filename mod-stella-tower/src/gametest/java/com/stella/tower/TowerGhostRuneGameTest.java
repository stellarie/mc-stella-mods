package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A ghost rune set into gear: a chestplate phases and evades, a weapon pierces armor and shields. */
public class TowerGhostRuneGameTest {
	private static ServerPlayer player(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = TowerTestSupport.join(helper, x, z);
		TowerTestSupport.vulnerable(player);
		return player;
	}

	private static ServerPlayer phased(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = player(helper, x, z);
		player.setItemSlot(EquipmentSlot.CHEST, GhostRunes.apply(new ItemStack(Items.IRON_CHESTPLATE)));
		return player;
	}

	private static Zombie zombie(final GameTestHelper helper, final double x, final double z) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND));
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		zombie.setHealth(zombie.getMaxHealth());
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static ItemStack anvil(final ServerPlayer player, final ItemStack gear) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(gear);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(TowerItems.GHOST_RUNE));
		menu.createResult();
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}

	/** At the anvil a ghost rune goes into a chestplate or a weapon, and into nothing else. */
	@GameTest
	public void theAnvilSetsAGhostRuneIntoAChestplateOrAWeapon(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 1.5, 1.5);
		ItemStack chest = anvil(player, new ItemStack(Items.IRON_CHESTPLATE));
		ItemStack sword = anvil(player, new ItemStack(Items.DIAMOND_SWORD));
		ItemStack helmet = anvil(player, new ItemStack(Items.IRON_HELMET));
		TowerTestSupport.result("ghostAnvil", "chest=" + GhostRunes.isGhosted(chest) + " sword=" + GhostRunes.isGhosted(sword) + " helmet=" + helmet);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(GhostRunes.isGhosted(chest) && chest.is(Items.IRON_CHESTPLATE), "a chestplate came out as " + chest);
		helper.assertTrue(GhostRunes.isGhosted(sword) && sword.is(Items.DIAMOND_SWORD), "a sword came out as " + sword);
		helper.assertTrue(helmet.isEmpty(), "a helmet took the ghost rune: " + helmet);
		helper.succeed();
	}

	/** A piece that carries a ghost rune takes no second: the anvil would charge a rune and emeralds for nothing. */
	@GameTest
	public void aGhostedPieceTakesNoSecondGhostRune(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 1.5, 1.5);
		ItemStack again = anvil(player, GhostRunes.apply(new ItemStack(Items.IRON_CHESTPLATE)));
		TowerTestSupport.result("ghostTwice", "again=" + again);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(again.isEmpty(), "a ghosted chestplate took a second ghost rune: " + again);
		helper.succeed();
	}

	/** The anvil charges 64 emeralds for a ghost rune, and uses the rune up. */
	@GameTest
	public void theAnvilChargesForAGhostRune(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 1.5, 1.5);
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(TowerItems.GHOST_RUNE));
		menu.createResult();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		boolean poor = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		player.getInventory().add(new ItemStack(Items.EMERALD, TowerRunes.EMERALDS));
		boolean paid = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result.copy());
		int emeralds = player.getInventory().countItem(Items.EMERALD);
		boolean runeUsed = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty();
		TowerTestSupport.result("ghostAnvilPaid", "ghosted=" + GhostRunes.isGhosted(result) + " poor=" + poor + " paid=" + paid + " emeraldsLeft=" + emeralds + " runeUsed=" + runeUsed);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(GhostRunes.isGhosted(result) && !poor && paid, "ghosted=" + GhostRunes.isGhosted(result) + " poor=" + poor + " paid=" + paid);
		helper.assertTrue(emeralds == 0 && runeUsed, "the anvil left " + emeralds + " emeralds and the rune used=" + runeUsed);
		helper.succeed();
	}

	/** A phased wearer evades about three blows in ten, of any kind, measured over many. */
	@GameTest
	public void aGhostChestplateEvadesAboutAThirdOfBlows(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = phased(helper, 1.5, 1.5);
		DamageSource wound = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RuneGuards.DELAYED_WOUND));
		int tries = 2000;
		int evaded = 0;
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
		for (int i = 0; i < tries; i++) {
			// Each blow that lands wears the chestplate; a fresh one each time, so it never breaks mid-count.
			player.setItemSlot(EquipmentSlot.CHEST, chest.copy());
			player.setHealth(player.getMaxHealth());
			float before = player.getHealth();
			player.hurtServer(level, wound, 1.0F);
			evaded += player.getHealth() == before ? 1 : 0;
		}
		double rate = evaded / (double) tries;
		// Six standard deviations of the count, so a true rate fails this well under once in a hundred million runs.
		double bound = 6.0 * Math.sqrt(GhostRunes.EVASION * (1.0 - GhostRunes.EVASION) / tries);
		TowerTestSupport.result("ghostEvasion", "evaded=" + evaded + "/" + tries + " rate=" + rate + " want=" + GhostRunes.EVASION + " bound=" + bound);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(rate - GhostRunes.EVASION) < bound, "a phased wearer evaded " + rate + " of blows, not " + GhostRunes.EVASION);
		helper.succeed();
	}

	/** A phased wearer and a mob pass through each other: neither pushes the other, while a plain player is pushed. */
	@GameTest
	public void aGhostChestplatePassesThroughMobs(final GameTestHelper helper) {
		ServerPlayer ghost = phased(helper, 2.5, 2.5);
		ServerPlayer plain = player(helper, 6.5, 2.5);
		Zombie a = zombie(helper, 2.8, 2.5);
		Zombie b = zombie(helper, 6.8, 2.5);
		ghost.setDeltaMovement(Vec3.ZERO);
		plain.setDeltaMovement(Vec3.ZERO);
		a.setDeltaMovement(Vec3.ZERO);
		b.setDeltaMovement(Vec3.ZERO);
		a.push(ghost);
		ghost.push(a);
		b.push(plain);
		double ghostMoved = ghost.getDeltaMovement().length() + a.getDeltaMovement().length();
		double plainMoved = plain.getDeltaMovement().length();
		TowerTestSupport.result("ghostPhase", "ghostMoved=" + ghostMoved + " plainMoved=" + plainMoved);
		a.discard();
		b.discard();
		TowerTestSupport.leave(helper, ghost);
		TowerTestSupport.leave(helper, plain);
		helper.assertTrue(plainMoved > 0.0, "a plain player was not pushed either, so the case proves nothing");
		helper.assertTrue(ghostMoved == 0.0, "a phased wearer and a zombie pushed each other by " + ghostMoved);
		helper.succeed();
	}

	/** A hostile mob a phased wearer passes through is slowed. */
	@GameTest(maxTicks = 40)
	public void aMobAGhostPassesThroughIsSlowed(final GameTestHelper helper) {
		ServerPlayer ghost = phased(helper, 2.5, 2.5);
		Zombie through = zombie(helper, 2.5, 2.5);
		ghost.teleportTo(helper.getLevel(), through.getX(), through.getY(), through.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
		Zombie apart = zombie(helper, 7.5, 7.5);
		helper.runAfterDelay(5L, () -> {
			boolean slowed = through.hasEffect(MobEffects.SLOWNESS);
			boolean apartSlowed = apart.hasEffect(MobEffects.SLOWNESS);
			TowerTestSupport.result("ghostSlow", "slowed=" + slowed + " apartSlowed=" + apartSlowed);
			through.discard();
			apart.discard();
			TowerTestSupport.leave(helper, ghost);
			helper.assertTrue(slowed && !apartSlowed, "passed through: slowed=" + slowed + "; apart: slowed=" + apartSlowed);
			helper.succeed();
		});
	}

	/** A ghost weapon's blow meets only half the target's armor and toughness. */
	@GameTest
	public void aGhostWeaponPiercesHalfTheArmor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, 1.5, 1.5);
		Zombie plainTarget = zombie(helper, 3.5, 3.5);
		Zombie ghostTarget = zombie(helper, 5.5, 3.5);
		plainTarget.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		ghostTarget.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		// Toughness too, so its half is measured as well as the armor's.
		plainTarget.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
		ghostTarget.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		DamageSource plainBlow = player.damageSources().playerAttack(player);
		float before = plainTarget.getHealth();
		plainTarget.hurtServer(level, plainBlow, 10.0F);
		double plainLoss = before - plainTarget.getHealth();
		player.setItemInHand(InteractionHand.MAIN_HAND, GhostRunes.apply(new ItemStack(Items.DIAMOND_SWORD)));
		DamageSource ghostBlow = player.damageSources().playerAttack(player);
		before = ghostTarget.getHealth();
		ghostTarget.hurtServer(level, ghostBlow, 10.0F);
		double ghostLoss = before - ghostTarget.getHealth();
		float armor = ghostTarget.getArmorValue();
		float toughness = (float) ghostTarget.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double want = CombatRules.getDamageAfterAbsorb(ghostTarget, 10.0F, ghostBlow,
			(float) (armor * (1.0 - GhostRunes.ARMOR_PIERCE)), (float) (toughness * (1.0 - GhostRunes.ARMOR_PIERCE)));
		TowerTestSupport.result("ghostPierce", "plainLoss=" + plainLoss + " ghostLoss=" + ghostLoss + " want=" + want);
		plainTarget.discard();
		ghostTarget.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(plainLoss < want, "armor turned no more of a plain blow, so the case proves nothing");
		helper.assertTrue(Math.abs(ghostLoss - want) < 1.0E-3, "a ghost blow landed " + ghostLoss + ", not " + want);
		helper.succeed();
	}

	/** A ghost weapon's blow passes a raised shield. */
	@GameTest
	public void aGhostWeaponIgnoresShields(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer blocker = player(helper, 2.5, 2.5);
		ServerPlayer striker = player(helper, 2.5, 4.5);
		// Facing south, toward the striker, with a shield raised long enough to block.
		blocker.setYRot(0.0F);
		blocker.setYHeadRot(0.0F);
		blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		blocker.startUsingItem(InteractionHand.OFF_HAND);
		((com.stella.tower.mixin.LivingEntityAccessor) blocker).stellaTower$setUseItemRemaining(blocker.getUseItem().getUseDuration(blocker) - 40);
		striker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		float plain = blocker.applyItemBlocking(level, striker.damageSources().playerAttack(striker), 10.0F);
		striker.setItemInHand(InteractionHand.MAIN_HAND, GhostRunes.apply(new ItemStack(Items.DIAMOND_SWORD)));
		float ghost = blocker.applyItemBlocking(level, striker.damageSources().playerAttack(striker), 10.0F);
		TowerTestSupport.result("ghostShield", "blocking=" + blocker.isBlocking() + " plainBlocked=" + plain + " ghostBlocked=" + ghost);
		TowerTestSupport.leave(helper, blocker);
		TowerTestSupport.leave(helper, striker);
		helper.assertTrue(plain > 0.0F, "the shield blocked no plain blow, so the case proves nothing");
		helper.assertTrue(ghost == 0.0F, "the shield blocked " + ghost + " of a ghost blow");
		helper.succeed();
	}
}
