package com.stella.equips;

import com.stella.equips.enchant.Auras;
import com.stella.equips.enchant.TowerBridge;
import com.stella.equips.enchant.Waveform;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Endless Tower's growth reaches the enchantments written in code through {@link TowerBridge}. The tower is
 * not in this harness, so a stand-in reads what each test marks on its own piece and gives 1.0 to the unmarked.
 */
public class TowerBridgeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.towerbridge");
	private static final int PADDING = 40;

	static {
		TowerBridge.setGrowth(new TowerBridge.Growth() {
			@Override
			public double flat(final ItemStack piece) {
				return mark(piece).getDoubleOr("flat", 1.0);
			}

			@Override
			public double resonance(final ItemStack piece, final String enchantment) {
				CompoundTag mark = mark(piece);
				return enchantment.equals(mark.getStringOr("resonates", "")) ? mark.getDoubleOr("resonance", 1.0) : 1.0;
			}
		});
	}

	private static CompoundTag mark(final ItemStack piece) {
		CustomData data = piece.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static ItemStack marked(final ItemStack piece, final double flat, final String resonates, final double resonance) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("flat", flat);
		tag.putString("resonates", resonates);
		tag.putDouble("resonance", resonance);
		piece.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return piece;
	}

	@GameTest(padding = PADDING)
	public void aChestplatesGrowthMultipliesMistArmorsDamage(final GameTestHelper helper) {
		float plain = mistPulse(helper, 1.0, "", 1.0);
		float grown = mistPulse(helper, 4.0, "mist_armor", 1.5);
		float elsewhere = mistPulse(helper, 4.0, "blaze_armor", 1.5);
		LOG.info("[BEHAVIOR] test=aChestplatesGrowthMultipliesMistArmorsDamage RESULT plain={} grown={} elsewhere={}", plain, grown, elsewhere);
		helper.assertTrue(Math.abs(plain - Auras.MIST_SHARE_PER_LEVEL * 2 * player(helper).getMaxHealth()) < 1.0E-3, "a plain mist pulse took " + plain);
		helper.assertTrue(Math.abs(grown - plain * 4.0F * 1.5F) < 1.0E-2, "growth x4 with resonance x1.5 took " + grown + " from " + plain);
		helper.assertTrue(Math.abs(elsewhere - plain * 4.0F) < 1.0E-2, "resonance with another enchantment changed the mist to " + elsewhere);
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void aChestplatesGrowthMultipliesBlazeArmorsHeat(final GameTestHelper helper) {
		ServerPlayer wearer = player(helper);
		wearer.setItemSlot(EquipmentSlot.CHEST, marked(enchanted(helper, Items.IRON_CHESTPLATE, "armor/blaze_armor", 1), 3.0, "blaze_armor", 2.0));
		Mob foe = foe(helper, 3, 2, 3);
		float before = foe.getHealth();
		Auras.pulse(wearer);
		float lost = before - foe.getHealth();
		LOG.info("[BEHAVIOR] test=aChestplatesGrowthMultipliesBlazeArmorsHeat RESULT lost={} base={}", lost, Auras.BLAZE_SHARE * wearer.getMaxHealth());
		helper.assertTrue(Math.abs(lost - Auras.BLAZE_SHARE * wearer.getMaxHealth() * 3.0F * 2.0F) < 1.0E-2, "a grown blaze pulse took " + lost);
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void resonanceLengthensWaveformsSlow(final GameTestHelper helper) {
		int plain = waveSlow(helper, "", 1.0);
		int resonant = waveSlow(helper, "waveform", 2.0);
		LOG.info("[BEHAVIOR] test=resonanceLengthensWaveformsSlow RESULT plain={} resonant={}", plain, resonant);
		helper.assertTrue(plain > 0 && plain <= Waveform.SLOW_TICKS, "a plain wave slowed for " + plain);
		helper.assertTrue(resonant > Waveform.SLOW_TICKS && resonant <= 2 * Waveform.SLOW_TICKS, "a resonant wave slowed for " + resonant);
		helper.succeed();
	}

	private static float mistPulse(final GameTestHelper helper, final double flat, final String resonates, final double resonance) {
		ServerPlayer wearer = player(helper);
		wearer.setItemSlot(EquipmentSlot.CHEST, marked(enchanted(helper, Items.IRON_CHESTPLATE, "armor/mist_armor", 2), flat, resonates, resonance));
		Mob foe = foe(helper, 3, 2, 3);
		float before = foe.getHealth();
		Auras.pulse(wearer);
		return before - foe.getHealth();
	}

	private static int waveSlow(final GameTestHelper helper, final String resonates, final double resonance) {
		ServerPlayer wielder = player(helper);
		wielder.setItemInHand(InteractionHand.MAIN_HAND, marked(enchanted(helper, Items.IRON_SWORD, "weapon/waveform", 1), 1.0, resonates, resonance));
		Mob struck = foe(helper, 3, 2, 2);
		Mob distant = foe(helper, 5, 2, 2);
		struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(wielder), 1.0F);
		return distant.hasEffect(MobEffects.SLOWNESS) ? distant.getEffect(MobEffects.SLOWNESS).getDuration() : -1;
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, y, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		spider.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500.0);
		spider.setHealth(500.0F);
		return spider;
	}

	private static ItemStack enchanted(final GameTestHelper helper, final Item item, final String path, final int level) {
		Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path)));
		ItemStack stack = new ItemStack(item);
		stack.enchant(holder, level);
		return stack;
	}
}
