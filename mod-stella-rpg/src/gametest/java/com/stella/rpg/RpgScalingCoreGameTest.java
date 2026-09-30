package com.stella.rpg;

import com.stella.rpg.api.OutgoingDamageShare;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Dice step, debuff resistance, the outgoing damage share, and the poison damage type. */
public final class RpgScalingCoreGameTest {
	private static final class Stub implements MobsBridge.Facts {
		private final Map<Mob, Integer> threats = new IdentityHashMap<>();

		@Override
		public int threat(final Mob mob) {
			return this.threats.getOrDefault(mob, 0);
		}

		@Override
		public String role(final Mob mob) {
			return MobStats.MELEE;
		}
	}

	@GameTest
	public void stepTwentyFive(final GameTestHelper helper) {
		String dice = RpgDice.of(24) + " " + RpgDice.of(25) + " " + RpgDice.of(50) + " " + RpgDice.of(100);
		RpgTestSupport.result("stepTwentyFive", dice);
		helper.assertTrue(dice.equals("1d4 2d4 3d4 5d5"), "dice=" + dice);
		helper.succeed();
	}

	@GameTest
	public void poisonShortened(final GameTestHelper helper) {
		int pure = DebuffResistance.duration(900, 45.0);
		int none = DebuffResistance.duration(900, 0.0);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.setStat(player, Stat.VIT, 30);
		RpgTestSupport.setStat(player, Stat.STR, 30);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 900));
		int applied = player.getEffect(MobEffects.POISON).getDuration();
		int expected = DebuffResistance.duration(900, DebuffResistance.score(player));
		RpgTestSupport.result("poisonShortened", "pure=" + pure + " none=" + none + " applied=" + applied
			+ " score=" + DebuffResistance.score(player));
		helper.assertTrue(pure == 691 && none == 900, "pure=" + pure + " none=" + none);
		helper.assertTrue(applied == expected && applied < 900, "applied=" + applied + " expected=" + expected);
		helper.succeed();
	}

	@GameTest
	public void onlyFiniteHarmful(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		List<MobEffectInstance> kept = List.of(
			new MobEffectInstance(MobEffects.SPEED, 900),
			new MobEffectInstance(MobEffects.BAD_OMEN, 900),
			new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1),
			new MobEffectInstance(MobEffects.POISON, MobEffectInstance.INFINITE_DURATION)
		);
		for (MobEffectInstance effect : kept) {
			MobEffectInstance after = DebuffResistance.apply(player, effect);
			helper.assertTrue(after == effect, effect + " changed to " + after);
		}
		RpgTestSupport.result("onlyFiniteHarmful", "kept=" + kept.size());
		helper.succeed();
	}

	@GameTest
	public void mobResists(final GameTestHelper helper) {
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
			stub.threats.put(zombie, 50);
			zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600));
			int applied = zombie.getEffect(MobEffects.SLOWNESS).getDuration();
			RpgTestSupport.result("mobResists", "applied=" + applied);
			helper.assertTrue(applied < 600 && applied == DebuffResistance.duration(600, DebuffResistance.score(zombie)),
				"a level-50 mob did not resist slowness: " + applied);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void shareMatchesCombat(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OutgoingDamageShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the outgoing damage share is missing: " + shared);
		Function<Object[], Float> share = (Function<Object[], Float>) shared;
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		float viaShare;
		float direct;
		try {
			viaShare = share.apply(new Object[] {victim, source, 5.0F});
			direct = RpgCombat.scaleOutgoing(victim, source, 5.0F);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("shareMatchesCombat", "share=" + viaShare + " direct=" + direct);
		helper.assertTrue(viaShare == direct && viaShare > 5.0F, "share=" + viaShare + " direct=" + direct);
		helper.succeed();
	}

	@GameTest
	public void poisonUsesOwnType(final GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		MobEffects.POISON.value().applyEffectTick(helper.getLevel(), zombie, 0);
		DamageSource last = zombie.getLastDamageSource();
		RpgTestSupport.result("poisonUsesOwnType", "last=" + (last == null ? "none" : last.typeHolder().getRegisteredName()));
		helper.assertTrue(last != null && last.is(RpgDamageTypes.POISON), "the poison tick used " + last);
		helper.succeed();
	}

	@GameTest
	public void poisonKeepsMagicTags(final GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource poison = RpgDamageTypes.poison(zombie);
		List<TagKey<DamageType>> tags = List.of(DamageTypeTags.ALWAYS_TRIGGERS_SILVERFISH, DamageTypeTags.AVOIDS_GUARDIAN_THORNS,
			DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_WOLF_ARMOR, DamageTypeTags.NO_KNOCKBACK,
			DamageTypeTags.PANIC_CAUSES, DamageTypeTags.WITCH_RESISTANT_TO);
		for (TagKey<DamageType> tag : tags) {
			helper.assertTrue(poison.is(tag), "poison is missing " + tag.location());
		}
		RpgTestSupport.result("poisonKeepsMagicTags", "tags=" + tags.size());
		helper.succeed();
	}

	@GameTest
	public void shakeTagContents(final GameTestHelper helper) {
		HolderSet.Named<DamageType> tag = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
			.getOrThrow(RpgDamageTypes.NO_HURT_SHAKE);
		boolean hasPoison = tag.stream().anyMatch(holder -> holder.is(RpgDamageTypes.POISON));
		RpgTestSupport.result("shakeTagContents", "size=" + tag.size() + " poison=" + hasPoison);
		helper.assertTrue(tag.size() == 10 && hasPoison, "size=" + tag.size() + " poison=" + hasPoison);
		helper.succeed();
	}
}
