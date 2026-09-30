package com.stella.equips;

import com.stella.equips.enchant.Auras;
import com.stella.equips.enchant.Waveform;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mist Armor and Blaze Armor pulse once a second around a player who wears them, striking the enemies near
 * and sparing the rest. Waveform sends a wide wave from a melee hit, then rests three seconds.
 */
public class AuraEnchantGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.auras");
	/** Long enough to hold at least one pulse, wherever the server clock stands when the test starts. */
	private static final int PULSE_WAIT = Auras.PULSE_TICKS * 2 + 5;
	/** Room around each test wider than a wave, so no test's aura or wave reaches a neighbour's mobs. */
	private static final int PADDING = 40;
	/** The spells mod's summon tag, named here because this mod does not depend on that one. */
	private static final String SUMMONED_TAG = "mod-stella-spells.summoned";
	private static final Map<UUID, List<List<LivingEntity>>> WAVES = new ConcurrentHashMap<>();

	static {
		Waveform.setRecorder((wielder, reached) -> WAVES.computeIfAbsent(wielder.getUUID(), key -> new CopyOnWriteArrayList<>()).add(List.copyOf(reached)));
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void mistArmorChillsAndSlowsTheFoesNearby(final GameTestHelper helper) {
		ServerPlayer wearer = wearer(helper, "armor/mist_armor", 3);
		Mob near = foe(helper, 3, 2, 3);
		// Inside the walls, and more than the radius from the wearer, centre to centre.
		Mob far = foe(helper, 1 + (int) Auras.MIST_RADIUS + 1, 2, 3);
		Mob pig = helper.spawnMob(EntityTypes.PIG, 1, 2, 5).withNoFreeWill().spawn();
		float nearBefore = near.getHealth();
		helper.runAfterDelay(PULSE_WAIT, () -> {
			float lost = nearBefore - near.getHealth();
			int slow = near.hasEffect(MobEffects.SLOWNESS) ? near.getEffect(MobEffects.SLOWNESS).getAmplifier() : -1;
			boolean spared = far.getHealth() == far.getMaxHealth() && !far.hasEffect(MobEffects.SLOWNESS)
				&& pig.getHealth() == pig.getMaxHealth() && !pig.hasEffect(MobEffects.SLOWNESS);
			LOG.info("[BEHAVIOR] test=mistArmorChillsAndSlowsTheFoesNearby RESULT nearLost={} slowAmplifier={} frozen={} spared={} farDistance={}",
				lost, slow, near.getTicksFrozen(), spared, far.distanceTo(wearer));
			helper.assertTrue(lost >= Auras.MIST_SHARE_PER_LEVEL * 3 * wearer.getMaxHealth() - 1.0E-3F && slow == 2, "the near foe lost " + lost + " at slowness " + slow);
			helper.assertTrue(spared, "the mist reached past its radius or struck a pig");
			helper.succeed();
		});
	}

	/** A skeleton the spells mod summoned for a player is that player's servant, and the wearer's aura spares it. */
	@GameTest(maxTicks = 100, padding = PADDING)
	public void mistArmorSparesASummonedServant(final GameTestHelper helper) {
		// Night, or the skeleton burns in daylight and its lost health reads as the mist's.
		helper.setTime(18_000L);
		wearer(helper, "armor/mist_armor", 3);
		Mob servant = helper.spawnMob(EntityTypes.SKELETON, 3, 2, 3).withNoFreeWill().spawn();
		servant.setPersistenceRequired();
		servant.addTag(SUMMONED_TAG);
		Mob foe = foe(helper, 3, 2, 4);
		float foeBefore = foe.getHealth();
		helper.runAfterDelay(PULSE_WAIT, () -> {
			boolean spared = servant.getHealth() == servant.getMaxHealth() && !servant.hasEffect(MobEffects.SLOWNESS);
			float foeLost = foeBefore - foe.getHealth();
			LOG.info("[BEHAVIOR] test=mistArmorSparesASummonedServant RESULT spared={} servantHealth={} foeLost={}",
				spared, servant.getHealth(), foeLost);
			helper.assertTrue(foeLost > 0.0F, "the mist never pulsed, so the servant's health proves nothing");
			helper.assertTrue(spared, "the mist struck the wearer's own summoned servant");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void blazeArmorBurnsTheFoesNearbyAndGuardsItsWearer(final GameTestHelper helper) {
		ServerPlayer wearer = wearer(helper, "armor/blaze_armor", 1);
		Mob near = foe(helper, 3, 2, 3);
		Mob far = foe(helper, 1 + (int) Auras.BLAZE_RADIUS + 2, 2, 3);
		float nearBefore = near.getHealth();
		helper.runAfterDelay(PULSE_WAIT, () -> {
			float lost = nearBefore - near.getHealth();
			boolean guarded = wearer.hasEffect(MobEffects.FIRE_RESISTANCE);
			LOG.info("[BEHAVIOR] test=blazeArmorBurnsTheFoesNearbyAndGuardsItsWearer RESULT nearLost={} farLost={} fireResistance={} farDistance={}",
				lost, far.getMaxHealth() - far.getHealth(), guarded, far.distanceTo(wearer));
			helper.assertTrue(lost >= Auras.BLAZE_SHARE * wearer.getMaxHealth() - 1.0E-3F, "the near foe lost only " + lost);
			helper.assertTrue(far.getHealth() == far.getMaxHealth(), "the heat reached past its radius");
			helper.assertTrue(guarded, "the wearer has no Fire Resistance");
			helper.succeed();
		});
	}

	@GameTest(padding = PADDING)
	public void waveformSlowsAndThrowsBackFoesFarAround(final GameTestHelper helper) {
		ServerPlayer wielder = wielder(helper, 1);
		Mob struck = foe(helper, 3, 2, 2);
		int reach = (int) Waveform.radius(1) - 2;
		Mob distant = foe(helper, 2 + reach, 2, 2);
		Mob beyond = foe(helper, 2 + (int) Waveform.radius(1) + 4, 2, 2);
		struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(wielder), 1.0F);
		List<List<LivingEntity>> waves = WAVES.getOrDefault(wielder.getUUID(), List.of());
		boolean reached = waves.size() == 1 && waves.get(0).contains(distant) && !waves.get(0).contains(beyond);
		int slow = distant.hasEffect(MobEffects.SLOWNESS) ? distant.getEffect(MobEffects.SLOWNESS).getAmplifier() : -1;
		double outward = distant.getDeltaMovement().x;
		LOG.info("[BEHAVIOR] test=waveformSlowsAndThrowsBackFoesFarAround RESULT waves={} reachedDistant={} slow={} outward={} beyondSlowed={}",
			waves.size(), reached, slow, outward, beyond.hasEffect(MobEffects.SLOWNESS));
		helper.assertTrue(reached, "the wave did not reach " + reach + " blocks and stop at the radius: " + waves);
		helper.assertTrue(slow == Waveform.SLOW_AMPLIFIER && outward > 0.0, "the distant foe was slowed at " + slow + " and pushed " + outward);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void waveformRestsThreeSecondsBetweenWaves(final GameTestHelper helper) {
		ServerPlayer wielder = wielder(helper, 1);
		Mob struck = foe(helper, 3, 2, 2);
		struck.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
		struck.setHealth(200.0F);
		for (int hit = 0; hit < 3; hit++) {
			struck.damageCooldownTime = 0;
			struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(wielder), 1.0F);
		}
		int burst = WAVES.getOrDefault(wielder.getUUID(), List.of()).size();
		helper.runAfterDelay(Waveform.COOLDOWN_TICKS + 1, () -> {
			struck.damageCooldownTime = 0;
			struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(wielder), 1.0F);
			int later = WAVES.getOrDefault(wielder.getUUID(), List.of()).size();
			LOG.info("[BEHAVIOR] test=waveformRestsThreeSecondsBetweenWaves RESULT burst={} later={}", burst, later);
			helper.assertTrue(burst == 1 && later == 2, "three quick hits made " + burst + " waves, and a hit after the rest made " + later);
			helper.succeed();
		});
	}

	private static ServerPlayer wearer(final GameTestHelper helper, final String path, final int level) {
		ServerPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.IRON_CHESTPLATE, path, level));
		return player;
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final int level) {
		ServerPlayer player = player(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, "weapon/waveform", level));
		return player;
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, y, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
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
