package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import java.util.function.LongSupplier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Player max health from level, VIT, and job, after Ragnarok Online. Every formula is in vanilla units.
 * K multiplies the result once, through {@link RpgScale}. Passive regeneration and the spell duration
 * multiplier also live here, so one config block {@code hp} holds every number.
 */
public final class HpModel {
	public static final Identifier MODIFIER_ID = StellaRpg.id("hp_model");
	/** The old per-level modifier of the equips mod. This model replaces it, so it is cancelled out. */
	private static final Identifier EQUIPS_LEVEL_ID =
		Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "power_max_health");

	/** Null means the server tick count. A test sets it to pick the beat. */
	private static volatile LongSupplier clock;

	private HpModel() {
	}

	public static void setClock(final LongSupplier newClock) {
		clock = newClock;
	}

	/** Max health in vanilla units. */
	public static double maxHealth(final int level, final int vit, final Job job) {
		RpgConfig.HpRules rules = RpgConfigStore.current().hp();
		double byLevel = rules.base() + rules.perLevel() * Math.max(0, level - 1);
		return byLevel * (1.0 + rules.vitPercent() * Math.max(0, vit) * StatMilestones.factor(vit)) * rules.jobFactor(job);
	}

	/** The share of max health that one regeneration beat heals. */
	public static double regenFraction(final int vit) {
		RpgConfig.HpRules rules = RpgConfigStore.current().hp();
		return rules.regenBase() + rules.regenPerVit() * Math.max(0, vit);
	}

	/** The spell duration multiplier for an INT value. */
	public static double durationMultiplier(final int intel) {
		RpgConfig.HpRules rules = RpgConfigStore.current().hp();
		return Math.min(rules.durationCap(), 1.0 + Math.max(0, intel) / rules.durationIntDivisor());
	}

	/** Sets the model modifier so base plus modifiers equals the model. A change keeps the health share. */
	public static void refresh(final ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		double target = maxHealth(player.experienceLevel, StatBonuses.effective(player, Stat.VIT), Jobs.of(player));
		AttributeModifier equips = health.getModifier(EQUIPS_LEVEL_ID);
		double amount = target - health.getBaseValue() - (equips == null ? 0.0 : equips.amount());
		AttributeModifier existing = health.getModifier(MODIFIER_ID);
		if (existing != null && existing.amount() == amount) {
			return;
		}
		float max = player.getMaxHealth();
		float fraction = max > 0.0F ? player.getHealth() / max : 0.0F;
		health.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE));
		if (player.isAlive()) {
			player.setHealth(fraction * player.getMaxHealth());
		}
	}

	public static void tick(final MinecraftServer server) {
		LongSupplier source = clock;
		long now = source == null ? server.getTickCount() : source.getAsLong();
		if (now % RpgConfigStore.current().hp().regenInterval() != 0) {
			return;
		}
		server.getPlayerList().getPlayers().forEach(HpModel::regen);
	}

	private static void regen(final ServerPlayer player) {
		if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
			return;
		}
		double amount = player.getMaxHealth() * regenFraction(StatBonuses.effective(player, Stat.VIT));
		// heal() multiplies by K, and the amount is already in K units.
		player.heal((float) (amount / RpgScale.k()));
	}
}
