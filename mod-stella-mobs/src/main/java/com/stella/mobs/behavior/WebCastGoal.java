package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * An elite or boss spider casts three webs at its target: one over the target's own feet and two more at
 * random in the 3x3x3 around it. An elite or boss cave spider's cast also withers the target. The webs clear
 * after ten seconds. Flagless, so it never contends with the spider's own movement.
 */
public class WebCastGoal extends Goal {
	public static final int PRIORITY = 5;
	public static final int WEBS = 3;
	public static final int COOLDOWN_TICKS = 160;
	public static final double RANGE = 12.0;
	public static final int WEB_LIFETIME_TICKS = 200;
	public static final int WITHER_TICKS = 100;
	public static final int WITHER_AMPLIFIER = 0;

	/** Test seam: each cast, with the spots it webbed. */
	public interface Recorder {
		void cast(Spider spider, LivingEntity target, List<BlockPos> webs);
	}

	private static volatile Recorder recorder = (spider, target, webs) -> {
	};

	private final Spider spider;
	private int cooldown;

	public WebCastGoal(final Spider spider) {
		this.spider = spider;
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (spider, target, webs) -> {
		} : newRecorder;
	}

	public static boolean casts(final Spider spider) {
		return Elite.isElite(spider) || Bosses.isBoss(spider);
	}

	@Override
	public boolean canUse() {
		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}
		LivingEntity target = this.spider.getTarget();
		return casts(this.spider) && target != null && target.isAlive() && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
			&& this.spider.distanceToSqr(target) <= RANGE * RANGE && this.spider.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		this.cooldown = COOLDOWN_TICKS;
		LivingEntity target = this.spider.getTarget();
		if (target == null || !(this.spider.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		List<BlockPos> webs = new ArrayList<>();
		BlockPos feet = target.blockPosition();
		if (TemporaryBlocks.place(level, feet, Blocks.COBWEB.defaultBlockState(), WEB_LIFETIME_TICKS)) {
			webs.add(feet);
		}
		for (int attempt = 0; attempt < 24 && webs.size() < WEBS; attempt++) {
			BlockPos spot = feet.offset(this.spider.getRandom().nextInt(3) - 1, this.spider.getRandom().nextInt(3) - 1, this.spider.getRandom().nextInt(3) - 1);
			if (!webs.contains(spot) && TemporaryBlocks.place(level, spot, Blocks.COBWEB.defaultBlockState(), WEB_LIFETIME_TICKS)) {
				webs.add(spot);
			}
		}
		if (this.spider instanceof CaveSpider) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), this.spider);
		}
		level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY(0.5), target.getZ(), 20, 1.0, 1.0, 1.0, 0.05);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=webCast CAST spider={} webs={} wither={}", this.spider.getType(), webs.size(), this.spider instanceof CaveSpider);
		recorder.cast(this.spider, target, List.copyOf(webs));
		if (!webs.isEmpty()) {
			SpiderSignal.alert(this.spider, target);
		}
	}
}
