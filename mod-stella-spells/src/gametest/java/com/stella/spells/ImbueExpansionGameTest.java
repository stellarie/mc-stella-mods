package com.stella.spells;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imbuing grown up: arrows and chestplates take spells too, one piece takes more than one spell at a price
 * that grows fivefold, a right-click on a hostile casts the spell at full strength, the spell enchantments on
 * the weapon and the armour strengthen what the weapon casts, and the hotbar shows the spell's cooldown.
 */
public class ImbueExpansionGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.imbue2");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier FROSTBIND = Identifier.parse("mod-stella-spells:frostbind");
	private static final Identifier EMBER_BRAND = Identifier.parse("mod-stella-spells:ember_brand");
	private static final float HIT = 4.0F;
	private static final Map<UUID, List<Identifier>> CASTS = new ConcurrentHashMap<>();

	static {
		SpellImbue.setSecondRecorder((player, spell, victim) -> CASTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell));
	}

	@GameTest
	public void theImbuePriceGrowsFivefoldWithEachSpell(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack once = anvilResult(helper, player, new ItemStack(Items.IRON_SWORD), SPARK_SHOT);
		int first = lastCost;
		ItemStack twice = anvilResult(helper, player, once, FROSTBIND);
		int second = lastCost;
		ItemStack thrice = anvilResult(helper, player, twice, EMBER_BRAND);
		int third = lastCost;
		LOG.info("[SPELLTEST] test=theImbuePriceGrowsFivefoldWithEachSpell RESULT costs={},{},{} spells={}", first, second, third, SpellImbue.spellsOf(thrice));
		helper.assertTrue(first == SpellImbue.IMBUE_XP_COST && second == SpellImbue.IMBUE_XP_COST * 5 && third == SpellImbue.IMBUE_XP_COST * 25, "the imbues cost " + first + ", " + second + ", " + third);
		helper.assertTrue(SpellImbue.spellsOf(thrice).equals(List.of(SPARK_SHOT, FROSTBIND, EMBER_BRAND)), "the thrice imbued sword holds " + SpellImbue.spellsOf(thrice));
		helper.succeed();
	}

	@GameTest
	public void aTwiceImbuedWeaponCastsBothSpellsOnAHit(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT, FROSTBIND);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		List<Identifier> casts = CASTS.getOrDefault(player.getUUID(), List.of());
		LOG.info("[SPELLTEST] test=aTwiceImbuedWeaponCastsBothSpellsOnAHit RESULT casts={}", casts);
		helper.assertTrue(casts.containsAll(List.of(SPARK_SHOT, FROSTBIND)), "the hit cast " + casts);
		helper.assertTrue(zombie.isOnFire() && zombie.hasEffect(MobEffects.SLOWNESS), "the zombie is not both burning and slowed");
		helper.succeed();
	}

	@GameTest
	public void arrowsAndChestplatesTakeASpellAtTheAnvil(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack arrows = anvilResult(helper, player, new ItemStack(Items.ARROW, 16), FROSTBIND);
		ItemStack spectral = anvilResult(helper, player, new ItemStack(Items.SPECTRAL_ARROW, 8), FROSTBIND);
		ItemStack chestplate = anvilResult(helper, player, new ItemStack(Items.IRON_CHESTPLATE), FROSTBIND);
		LOG.info("[SPELLTEST] test=arrowsAndChestplatesTakeASpellAtTheAnvil RESULT arrows={} spectral={} chestplate={}", arrows, spectral, chestplate);
		helper.assertTrue(arrows.is(Items.ARROW) && arrows.getCount() == 16 && SpellImbue.spellsOf(arrows).contains(FROSTBIND), "the arrows came out as " + arrows);
		helper.assertTrue(spectral.is(Items.SPECTRAL_ARROW) && SpellImbue.spellsOf(spectral).contains(FROSTBIND), "the spectral arrows came out as " + spectral);
		helper.assertTrue(chestplate.is(Items.IRON_CHESTPLATE) && SpellImbue.spellsOf(chestplate).contains(FROSTBIND), "the chestplate came out as " + chestplate);
		helper.succeed();
	}

	@GameTest
	public void anImbuedArrowCastsItsSpellOnWhatItStrikes(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper);
		Mob zombie = zombie(helper, 2, 4);
		ItemStack ammo = new ItemStack(Items.ARROW);
		ammo.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(FROSTBIND));
		Arrow arrow = new Arrow(helper.getLevel(), player, ammo, null);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), HIT);
		List<Identifier> casts = CASTS.getOrDefault(player.getUUID(), List.of());
		LOG.info("[SPELLTEST] test=anImbuedArrowCastsItsSpellOnWhatItStrikes RESULT casts={} slowed={}", casts, zombie.hasEffect(MobEffects.SLOWNESS));
		helper.assertTrue(casts.contains(FROSTBIND) && zombie.hasEffect(MobEffects.SLOWNESS), "the imbued arrow cast " + casts);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedChestplateCastsItsSpellOnTheAttacker(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = survivalPlayer(helper, new Vec3(2.5, 1.0, 2.5));
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		chestplate.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(FROSTBIND));
		player.setItemSlot(EquipmentSlot.CHEST, chestplate);
		Mob zombie = zombie(helper, 2, 4);
		// A player who has just joined cannot be hurt for three seconds, so the blow lands after that.
		helper.runAfterDelay(65L, () -> {
			boolean hurt = player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 2.0F);
			boolean slowed = zombie.hasEffect(MobEffects.SLOWNESS);
			List<Identifier> casts = CASTS.getOrDefault(player.getUUID(), List.of());
			helper.getLevel().getServer().getPlayerList().remove(player);
			LOG.info("[SPELLTEST] test=anImbuedChestplateCastsItsSpellOnTheAttacker RESULT hurt={} casts={} attackerSlowed={}", hurt, casts, slowed);
			helper.assertTrue(hurt, "the wearer took no blow, so the case proves nothing");
			helper.assertTrue(casts.contains(FROSTBIND) && slowed, "the imbued chestplate cast " + casts + " and the attacker slowed=" + slowed);
			helper.succeed();
		});
	}

	@GameTest
	public void rightClickingAHostileCastsTheSpellAtFullStrength(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		InteractionResult result = rightClick(helper, player, zombie);
		float taken = zombie.getMaxHealth() - zombie.getHealth();
		LOG.info("[SPELLTEST] test=rightClickingAHostileCastsTheSpellAtFullStrength RESULT result={} taken={} burning={}", result, taken, zombie.isOnFire());
		helper.assertTrue(result == InteractionResult.SUCCESS, "the right-click was " + result);
		helper.assertTrue(Math.abs(taken - SpellCasting.DAMAGE) < 0.001F && zombie.isOnFire(), "the full-strength Spark Shot dealt " + taken);
		helper.succeed();
	}

	@GameTest
	public void rightClickingAVillagerCastsNothing(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob villager = helper.spawnMob(EntityTypes.VILLAGER, 2, 1, 4).withNoFreeWill().spawn();
		InteractionResult result = rightClick(helper, player, villager);
		LOG.info("[SPELLTEST] test=rightClickingAVillagerCastsNothing RESULT result={} burning={}", result, villager.isOnFire());
		helper.assertTrue(result == InteractionResult.PASS && !villager.isOnFire(), "a right-click on a villager cast: " + result);
		helper.succeed();
	}

	@GameTest
	public void potencyOnTheWeaponStrengthensItsImbuedSpell(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Holder<Enchantment> potency = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:potency")));
		player.getMainHandItem().enchant(potency, 5);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		float spell = zombie.getMaxHealth() - zombie.getHealth() - HIT;
		float expected = SpellCasting.DAMAGE * 1.5F * (float) SpellImbue.IMBUE_POWER;
		boolean onSwords = potency.value().canEnchant(new ItemStack(Items.IRON_SWORD));
		LOG.info("[SPELLTEST] test=potencyOnTheWeaponStrengthensItsImbuedSpell RESULT spell={} expected={} canEnchantSword={}", spell, expected, onSwords);
		helper.assertTrue(Math.abs(spell - expected) < 0.001F, "the imbued spell dealt " + spell + " with Potency V, not " + expected);
		helper.assertTrue(onSwords, "Potency cannot be put on a sword");
		helper.succeed();
	}

	@GameTest
	public void theHotbarShowsAnImbuedSpellsCooldown(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		ItemStack sword = player.getMainHandItem();
		boolean cooling = player.getCooldowns().isOnCooldown(sword);
		LOG.info("[SPELLTEST] test=theHotbarShowsAnImbuedSpellsCooldown RESULT cooling={} group={}", cooling, player.getCooldowns().getCooldownGroup(sword));
		helper.assertTrue(cooling, "the imbued sword shows no cooldown after its spell cast");
		helper.succeed();
	}

	private static int lastCost;

	private static ItemStack anvilResult(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final Identifier spell) {
		AnvilMenu anvil = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
		anvil.getSlot(AnvilMenu.INPUT_SLOT).set(left.copy());
		anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(BuiltInRegistries.ITEM.getValue(spell)));
		anvil.createResult();
		lastCost = anvil.getCost();
		return anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
	}

	private static InteractionResult rightClick(final GameTestHelper helper, final ServerPlayer player, final Mob target) {
		return UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, target, new EntityHitResult(target));
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final Identifier... spells) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		if (spells.length > 0) {
			sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(spells)));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		return player;
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper, final Vec3 relative) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
		Vec3 at = helper.absoluteVec(relative);
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	// A durable zombie with no armour, so each hit reads as exactly its damage.
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
