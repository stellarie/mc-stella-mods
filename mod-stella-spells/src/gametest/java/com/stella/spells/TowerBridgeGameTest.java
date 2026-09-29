package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Endless Tower's growth reaches a spell through {@link TowerBridge}. The tower is not in this harness, so a
 * stand-in reads what each test marks on its own book and caster and gives 1.0 to everything unmarked.
 */
public class TowerBridgeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.towerbridge");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final String CHARGES = "tower_bridge_charges";
	/** What each caster was last asked to shorten. */
	private static final ConcurrentHashMap<UUID, Integer> ASKED = new ConcurrentHashMap<>();

	static {
		TowerBridge.setGrowth(new TowerBridge.Growth() {
			@Override
			public double flat(final ItemStack focus) {
				return mark(focus).getDoubleOr("flat", 1.0);
			}

			@Override
			public double resonance(final ItemStack focus, final String spellId) {
				CompoundTag mark = mark(focus);
				return spellId.equals(mark.getStringOr("resonates", "")) ? mark.getDoubleOr("resonance", 1.0) : 1.0;
			}

			@Override
			public int cooldown(final Player caster, final int ticks) {
				ASKED.put(caster.getUUID(), ticks);
				return caster.removeTag(CHARGES) ? ticks / 2 : ticks;
			}
		});
	}

	private static CompoundTag mark(final ItemStack focus) {
		CustomData data = focus.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static ItemStack book(final double flat, final String resonatesWith, final double resonance) {
		ItemStack book = BuiltInRegistries.ITEM.getValue(SPARK_SHOT).getDefaultInstance();
		CompoundTag tag = new CompoundTag();
		tag.putDouble("flat", flat);
		tag.putString("resonates", resonatesWith);
		tag.putDouble("resonance", resonance);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	@GameTest
	public void aBooksGrowthMultipliesTheSpellsDamage(final GameTestHelper helper) {
		float plain = castOnce(helper, book(1.0, "", 1.0));
		float grown = castOnce(helper, book(3.0, "", 1.0));
		LOG.info("[SPELLTEST] test=aBooksGrowthMultipliesTheSpellsDamage RESULT plain={} grown={}", plain, grown);
		helper.assertTrue(Math.abs(plain - SpellCasting.DAMAGE) < 1.0E-4, "a plain Spark Shot took " + plain);
		helper.assertTrue(Math.abs(grown - 3.0F * SpellCasting.DAMAGE) < 1.0E-3, "a book of growth x3 took " + grown);
		helper.succeed();
	}

	@GameTest
	public void resonanceStrengthensOnlyTheSpellItNames(final GameTestHelper helper) {
		float named = castOnce(helper, book(1.0, "spark_shot", 1.5));
		float other = castOnce(helper, book(1.0, "frostbind", 1.5));
		LOG.info("[SPELLTEST] test=resonanceStrengthensOnlyTheSpellItNames RESULT named={} other={}", named, other);
		helper.assertTrue(Math.abs(named - 1.5F * SpellCasting.DAMAGE) < 1.0E-3, "a resonant Spark Shot took " + named);
		helper.assertTrue(Math.abs(other - SpellCasting.DAMAGE) < 1.0E-4, "a Spark Shot resonating with another spell took " + other);
		helper.succeed();
	}

	@GameTest
	public void storedChargesShortenTheCooldownAndAreAskedOncePerCast(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.addTag(CHARGES);
		ItemStack book = book(1.0, "", 1.0);
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		target(helper);
		SpellbookItem item = (SpellbookItem) book.getItem();
		int base = SpellModifiers.resolve(book, ItemStack.EMPTY, ItemStack.EMPTY).cooldownTicks();
		SpellCasting.cast(caster, item, InteractionHand.MAIN_HAND);
		int asked = ASKED.getOrDefault(caster.getUUID(), -1);
		// Half the cooldown has passed at the shortened rest's end: the book is ready, where the full rest would not be.
		for (int i = 0; i < base / 2 + 1; i++) {
			caster.getCooldowns().tick();
		}
		boolean ready = !caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		boolean spent = !caster.removeTag(CHARGES);
		helper.getLevel().getServer().getPlayerList().remove(caster);
		LOG.info("[SPELLTEST] test=storedChargesShortenTheCooldownAndAreAskedOncePerCast RESULT base={} asked={} ready={} spent={}", base, asked, ready, spent);
		helper.assertTrue(asked == base, "the tower was asked to shorten " + asked + ", not the book's " + base);
		helper.assertTrue(ready && spent, "half the rest later the book was " + (ready ? "ready" : "still resting") + " and the charges " + (spent ? "spent" : "kept"));
		helper.succeed();
	}

	@GameTest
	public void anUnmarkedCasterKeepsTheFullCooldown(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ItemStack book = book(1.0, "", 1.0);
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		target(helper);
		SpellbookItem item = (SpellbookItem) book.getItem();
		int base = SpellModifiers.resolve(book, ItemStack.EMPTY, ItemStack.EMPTY).cooldownTicks();
		SpellCasting.cast(caster, item, InteractionHand.MAIN_HAND);
		for (int i = 0; i < base / 2 + 1; i++) {
			caster.getCooldowns().tick();
		}
		boolean resting = caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		helper.getLevel().getServer().getPlayerList().remove(caster);
		LOG.info("[SPELLTEST] test=anUnmarkedCasterKeepsTheFullCooldown RESULT base={} resting={}", base, resting);
		helper.assertTrue(resting, "an unmarked caster's book was ready after half its rest");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedWeaponsGrowthReachesItsSpell(final GameTestHelper helper) {
		ServerPlayer player = caster(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(SPARK_SHOT));
		CompoundTag tag = new CompoundTag();
		tag.putDouble("flat", 4.0);
		sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		Mob zombie = target(helper);
		float hit = 4.0F;
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), hit);
		float taken = zombie.getMaxHealth() - zombie.getHealth();
		float expected = hit + SpellCasting.DAMAGE * (float) SpellImbue.IMBUE_POWER * 4.0F;
		helper.getLevel().getServer().getPlayerList().remove(player);
		LOG.info("[SPELLTEST] test=anImbuedWeaponsGrowthReachesItsSpell RESULT taken={} expected={}", taken, expected);
		helper.assertTrue(Math.abs(taken - expected) < 0.01F, "the grown imbued hit took " + taken + ", not " + expected);
		helper.succeed();
	}

	private static float castOnce(final GameTestHelper helper, final ItemStack book) {
		ServerPlayer caster = caster(helper);
		Mob zombie = target(helper);
		float before = zombie.getHealth();
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
		float lost = before - zombie.getHealth();
		// Gone before the next cast, which would otherwise find the same target.
		zombie.discard();
		helper.getLevel().getServer().getPlayerList().remove(caster);
		return lost;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "towercaster"), false);
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
		return player;
	}

	private static Mob target(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
