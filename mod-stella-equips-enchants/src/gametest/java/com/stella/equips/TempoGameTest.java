package com.stella.equips;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class TempoGameTest {
	private static final String TEMPO = "weapon/tempo";
	// Both effects last two seconds: the effect command and apply_mob_effect count seconds.
	private static final int EFFECT_TICKS = 40;
	private static final int REFRESH_GAP_TICKS = 10;
	private static final float SPAWN_Y = 2.0F;
	private static final int THROW_FLOOR_Y = 2;
	private static final int THROW_Y = 3;

	@GameTest
	public void tempoDirectPlayerHitAppliesBothEffects(final GameTestHelper helper) {
		assertBothEffects(helper, Items.DIAMOND_SWORD, "sword");
	}

	@GameTest
	public void tempoHeldTridentCountsAsDirect(final GameTestHelper helper) {
		assertBothEffects(helper, Items.TRIDENT, "heldTrident");
	}

	@GameTest
	public void tempoUnenchantedHitAppliesNoEffects(final GameTestHelper helper) {
		Mob victim = victim(helper);
		ServerPlayer attacker = attackerBeside(helper, victim, new ItemStack(Items.DIAMOND_SWORD));
		attacker.attack(victim);
		String seen = effects(victim, attacker);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=tempoUnenchanted RESULT {}", seen);
		helper.assertTrue(
			!victim.hasEffect(MobEffects.SLOWNESS) && !attacker.hasEffect(MobEffects.SPEED),
			"a plain sword applied tempo effects: " + seen
		);
		helper.succeed();
	}

	@GameTest
	public void tempoMobAttackerDoesNotReceiveSpeed(final GameTestHelper helper) {
		Mob victim = victim(helper);
		Mob attacker = helper.spawnMob(EntityTypes.ZOMBIE, 2.0F, SPAWN_Y, 3.0F).withNoFreeWill().spawn();
		attacker.setItemInHand(InteractionHand.MAIN_HAND, tempo(helper, Items.DIAMOND_SWORD));
		boolean hit = attacker.doHurtTarget(helper.getLevel(), victim);
		String seen = effects(victim, attacker);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=tempoMobAttacker RESULT hit={} {}", hit, seen);
		helper.assertTrue(
			hit && victim.hasEffect(MobEffects.SLOWNESS) && !attacker.hasEffect(MobEffects.SPEED),
			"a mob's tempo hit landed " + hit + " and left " + seen
		);
		helper.succeed();
	}

	@GameTest
	public void tempoThrownTridentDoesNotTrigger(final GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, THROW_FLOOR_Y, z, Blocks.STONE);
			}
		}

		ServerPlayer thrower = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		PacketRecorder.attach(thrower);
		Vec3 origin = helper.absoluteVec(new Vec3(0.5, THROW_Y, 0.5));
		thrower.setPos(origin.x, origin.y, origin.z);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 0, THROW_Y, 4).withNoFreeWill().spawn();
		ThrownTrident trident = new ThrownTrident(helper.getLevel(), thrower, tempo(helper, Items.TRIDENT));
		Vec3 start = helper.absoluteVec(new Vec3(0.5, THROW_Y + 1.0, 0.5));
		trident.setPos(start.x, start.y, start.z);
		trident.shoot(0.0, 0.0, 1.0, 2.0F, 0.0F);
		helper.getLevel().addFreshEntity(trident);
		helper.runAfterDelay(8L, () -> {
			DamageSource last = target.getLastDamageSource();
			boolean tridentHit = last != null && last.getDirectEntity() instanceof ThrownTrident;
			String seen = effects(target, thrower);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=tempoThrownTrident RESULT tridentHit={} lost={} {}",
				tridentHit, target.getMaxHealth() - target.getHealth(), seen
			);
			helper.assertTrue(tridentHit, "the thrown trident did not hit the target");
			helper.assertTrue(
				!target.hasEffect(MobEffects.SLOWNESS) && !thrower.hasEffect(MobEffects.SPEED),
				"a thrown tempo trident applied " + seen
			);
			helper.succeed();
		});
	}

	@GameTest
	public void tempoRepeatHitRefreshesEffects(final GameTestHelper helper) {
		Mob victim = victim(helper);
		ServerPlayer attacker = attackerBeside(helper, victim, tempo(helper, Items.DIAMOND_SWORD));
		attacker.attack(victim);
		int first = duration(victim, MobEffects.SLOWNESS);
		helper.runAfterDelay(REFRESH_GAP_TICKS, () -> {
			int decayed = duration(victim, MobEffects.SLOWNESS);
			victim.setInvulnerableTime(0);
			attacker.resetAttackStrengthTicker();
			attacker.attack(victim);
			int refreshed = duration(victim, MobEffects.SLOWNESS);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=tempoRefresh RESULT first={} decayed={} refreshed={}", first, decayed, refreshed
			);
			helper.assertTrue(
				first == EFFECT_TICKS && decayed < first && refreshed == EFFECT_TICKS,
				"slowness ran " + first + ", decayed to " + decayed + " and refreshed to " + refreshed
			);
			helper.succeed();
		});
	}

	private static void assertBothEffects(final GameTestHelper helper, final Item weapon, final String label) {
		Mob victim = victim(helper);
		ServerPlayer attacker = attackerBeside(helper, victim, tempo(helper, weapon));
		attacker.attack(victim);
		MobEffectInstance slowness = victim.getEffect(MobEffects.SLOWNESS);
		MobEffectInstance speed = attacker.getEffect(MobEffects.SPEED);
		String seen = effects(victim, attacker);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=tempoDirect RESULT weapon={} {}", label, seen);
		helper.assertTrue(
			slowness != null && slowness.getAmplifier() == 0 && slowness.getDuration() == EFFECT_TICKS
				&& speed != null && speed.getAmplifier() == 0 && speed.getDuration() == EFFECT_TICKS,
			"a direct " + label + " hit left " + seen
		);
		helper.succeed();
	}

	private static Mob victim(final GameTestHelper helper) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2.0F, SPAWN_Y, 2.0F).withNoFreeWill().spawn();
		zombie.setInvulnerableTime(0);
		return zombie;
	}

	private static ServerPlayer attackerBeside(final GameTestHelper helper, final Mob victim, final ItemStack weapon) {
		ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		PacketRecorder.attach(attacker);
		attacker.setPos(victim.getX(), victim.getY(), victim.getZ() + 1.0);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return attacker;
	}

	private static ItemStack tempo(final GameTestHelper helper, final Item item) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> holder = registry.getOrThrow(ResourceKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, TEMPO)
		));
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, 1);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}

	private static int duration(final LivingEntity entity, final Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? 0 : instance.getDuration();
	}

	private static String effects(final LivingEntity victim, final LivingEntity attacker) {
		return "victimSlowness=" + duration(victim, MobEffects.SLOWNESS) + " attackerSpeed=" + duration(attacker, MobEffects.SPEED);
	}
}
