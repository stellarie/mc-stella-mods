package com.stella.mobs;

import com.stella.mobs.behavior.SpiderBite;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * The spider bite. Every hit heals the spider by a tenth of the damage dealt and slows the victim, and the spider
 * no longer webs the player it fights. Both cases run the spider's real attack against a zombie, and the web case against a survival player.
 */
public class SpiderBiteGameTest {
	private static final String TEST = "spiderBite";
	private static final int MAX_TICKS = 200;
	private static final double PLAYER_X = 20.5;
	private static final double PLAYER_Z = 20.5;
	private static final int SPIDER_X = 20;
	private static final int SPIDER_Z = 18;
	private static final int WATCH_TICKS = 60;
	private static final float HEALED_FROM = 50.0F;
	private static final float TOLERANCE = 0.01F;
	private static final int REACH_BLOCKS = 3;

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void biteHealsATenthAndSlows(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		Zombie victim = scene.mob(EntityTypes.ZOMBIE, SPIDER_X, MeleeKitFloor.SPAWN_Y, SPIDER_Z + 2);
		Spider spider = scene.mob(EntityTypes.SPIDER, SPIDER_X, MeleeKitFloor.SPAWN_Y, SPIDER_Z);
		spider.setNoAi(true);
		victim.setNoAi(true);

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenExecute(() -> {
				spider.setHealth(HEALED_FROM);
				victim.setInvulnerableTime(0);
				float victimBefore = victim.getHealth();
				boolean landed = spider.doHurtTarget(helper.getLevel(), victim);
				float dealt = victimBefore - victim.getHealth();
				float healed = spider.getHealth() - HEALED_FROM;
				MobEffectInstance slow = victim.getEffect(MobEffects.SLOWNESS);
				String seen = "landed=" + landed + " dealt=" + dealt + " healed=" + healed + " slowness=" + slow;
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.biteHealsATenthAndSlows RESULT {}", TEST, seen);
				scene.end();
				helper.assertTrue(landed && dealt > 0.0F, "the bite dealt no damage: " + seen);
				helper.assertTrue(Math.abs(healed - dealt * SpiderBite.LIFESTEAL) < TOLERANCE, "the spider did not heal a tenth of the damage: " + seen);
				helper.assertTrue(slow != null && slow.getAmplifier() == SpiderBite.SLOWNESS_AMPLIFIER, "the victim has no Slowness I: " + seen);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void spiderPlacesNoWebOnAPlayerInReach(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		ServerPlayer player = scene.player(PLAYER_X, PLAYER_Z);
		Spider spider = scene.mob(EntityTypes.SPIDER, SPIDER_X, MeleeKitFloor.SPAWN_Y, SPIDER_Z);
		int[] closest = {Integer.MAX_VALUE};

		helper.onEachTick(() -> {
			spider.setTarget(player);
			closest[0] = Math.min(closest[0], (int) Math.ceil(spider.distanceTo(player)));
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking(), "the scene is not ticking yet"))
			.thenIdle(WATCH_TICKS)
			.thenExecute(() -> {
				int webs = 0;
				BlockPos centre = player.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -1, -3), centre.offset(3, 2, 3))) {
					if (helper.getLevel().getBlockState(pos).is(Blocks.COBWEB)) {
						webs++;
					}
				}

				String seen = "webs=" + webs + " closestBlocks=" + closest[0];
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.spiderPlacesNoWebOnAPlayerInReach RESULT {}", TEST, seen);
				scene.end();
				helper.assertTrue(closest[0] <= REACH_BLOCKS, "the spider never came into reach, so the check proves nothing: " + seen);
				helper.assertTrue(webs == 0, "the spider webbed the player: " + seen);
			})
			.thenSucceed();
	}
}
