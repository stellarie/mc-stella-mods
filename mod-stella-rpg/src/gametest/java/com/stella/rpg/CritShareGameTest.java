package com.stella.rpg;

import com.stella.rpg.api.CritShare;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The critical hit share another mod reads when a hit lands: it answers true for the victim of an RPG critical hit
 * while that hit is in progress, which is when the damage event of that victim fires, and false for any other hit.
 */
public final class CritShareGameTest {
	private static final float HIT = 10.0F;
	/** What the share answered inside the damage event of each victim. */
	private static final Map<UUID, Boolean> AT_DAMAGE_EVENT = new ConcurrentHashMap<>();

	static {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (FabricLoader.getInstance().getObjectShare().get(CritShare.OBJECT_SHARE_KEY) instanceof Function<?, ?> share) {
				@SuppressWarnings("unchecked")
				Function<Object[], Boolean> verdict = (Function<Object[], Boolean>) share;
				AT_DAMAGE_EVENT.put(entity.getUUID(), verdict.apply(new Object[] {entity, source}));
			}
			return true;
		});
	}

	@GameTest
	public void theShareAnswersTrueInsideAnRpgCriticalHit(final GameTestHelper helper) {
		Verdict verdict = hit(helper, true);
		RpgTestSupport.result("theShareAnswersTrueInsideAnRpgCriticalHit", "atDamageEvent=" + verdict.atEvent() + " afterHit=" + verdict.after());
		helper.assertTrue(Boolean.TRUE.equals(verdict.atEvent()), "the share answered " + verdict.atEvent() + " inside a critical hit");
		helper.assertTrue(!verdict.after(), "the share still answered true once the hit was over");
		helper.succeed();
	}

	@GameTest
	public void theShareAnswersFalseInsideAPlainHit(final GameTestHelper helper) {
		Verdict verdict = hit(helper, false);
		RpgTestSupport.result("theShareAnswersFalseInsideAPlainHit", "atDamageEvent=" + verdict.atEvent());
		helper.assertTrue(Boolean.FALSE.equals(verdict.atEvent()), "the share answered " + verdict.atEvent() + " inside a plain hit, or never ran");
		helper.succeed();
	}

	private record Verdict(Boolean atEvent, boolean after) {
	}

	private static Verdict hit(final GameTestHelper helper, final boolean crit) {
		ServerPlayer attacker = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5_000.0);
		victim.setHealth(victim.getMaxHealth());
		RpgCombat.setRoller((who, random, chance) -> crit);
		DamageSource source = helper.getLevel().damageSources().playerAttack(attacker);
		try {
			victim.hurtServer(helper.getLevel(), source, HIT);
		} finally {
			RpgCombat.setRoller(null);
		}
		helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "the hit did no harm, so the case proves nothing");
		@SuppressWarnings("unchecked")
		Function<Object[], Boolean> share = (Function<Object[], Boolean>) FabricLoader.getInstance().getObjectShare().get(CritShare.OBJECT_SHARE_KEY);
		return new Verdict(AT_DAMAGE_EVENT.get(victim.getUUID()), share.apply(new Object[] {victim, source}));
	}
}
