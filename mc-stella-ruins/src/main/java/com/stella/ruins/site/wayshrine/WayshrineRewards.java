package com.stella.ruins.site.wayshrine;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.EffectHook;
import com.stella.ruins.reward.PeriodicEffect;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.SetBonus;
import com.stella.ruins.reward.SetSpec;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

/** The Pilgrim set, its aura, and the Pilgrim censer. */
public final class WayshrineRewards {
	public static final String SET = "pilgrim";
	public static final String CENSER_EFFECT = "censer";
	public static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("wayshrine_repair"));
	/** The 2-piece bonus: more maximum health, as a share of the base. */
	public static final double HEALTH_BONUS = 0.10;
	/** The 4-piece aura: the wearer and every player this near get Regeneration I. */
	public static final double AURA_RANGE = 8.0;
	public static final int AURA_TICKS = 60;
	public static final int AURA_PERIOD = 20;
	/** The censer heals 1 each second below this share of health. */
	public static final float CENSER_BELOW = 0.5F;
	public static final float CENSER_TICK_HEAL = 1.0F;
	public static final float CENSER_KILL_HEAL = 2.0F;

	private static RuinSets.SetDefinition set;
	private static Item censer;

	private WayshrineRewards() {
	}

	public static RuinSets.SetDefinition set() {
		return set;
	}

	public static Item censer() {
		return censer;
	}

	public static void register() {
		set = RuinSets.define(SET, SetSpec.builder()
			.durabilityFactor(33)
			.defense(3, 8, 6, 3)
			.toughness(1.0F)
			.knockbackResistance(0.0F)
			.enchantValue(10)
			.repair(REPAIR)
			.equipmentAsset(StellaRuins.id(SET))
			.bonus(new SetBonus(2, List.of(AttributeBonus.multiplyBase(Attributes.MAX_HEALTH, HEALTH_BONUS))))
			.bonus(new SetBonus(4, List.of(), new PeriodicEffect(MobEffects.REGENERATION, 0, AURA_TICKS)))
			.build());
		UniqueEffects.define(CENSER_EFFECT, new CenserHook());
		censer = RuinUniques.register("pilgrims_censer", Item::new, new Item.Properties().stacksTo(1), CENSER_EFFECT);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % AURA_PERIOD == 0) {
				for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
					aura(wearer);
				}
			}
		});
	}

	/** The set renews the wearer's own Regeneration. This gives it to the players near a 4-piece wearer. */
	static void aura(final ServerPlayer wearer) {
		if (wearer.isSpectator() || !wearer.isAlive() || RuinSets.wornPieces(wearer, SET) < 4) {
			return;
		}
		for (ServerPlayer near : wearer.level().players()) {
			if (near != wearer && !near.isSpectator() && near.isAlive() && near.distanceTo(wearer) <= AURA_RANGE) {
				near.addEffect(new MobEffectInstance(MobEffects.REGENERATION, AURA_TICKS, 0, true, false, true));
			}
		}
	}

	private static boolean holdsCenser(final LivingEntity user) {
		return CENSER_EFFECT.equals(RuinUniques.effectOf(user.getOffhandItem()));
	}

	/** The censer works from the off hand only. */
	private static final class CenserHook implements EffectHook {
		@Override
		public void onTick(final LivingEntity user) {
			if (holdsCenser(user) && user.getHealth() < user.getMaxHealth() * CENSER_BELOW) {
				user.heal(CENSER_TICK_HEAL);
			}
		}

		@Override
		public void onKill(final LivingEntity user, final LivingEntity victim) {
			if (holdsCenser(user)) {
				user.heal(CENSER_KILL_HEAL);
			}
		}
	}
}
