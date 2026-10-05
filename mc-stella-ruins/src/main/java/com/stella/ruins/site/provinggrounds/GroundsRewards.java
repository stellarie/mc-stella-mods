package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.EffectHook;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.SetBonus;
import com.stella.ruins.reward.SetSpec;
import com.stella.ruins.reward.UniqueEffects;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** The Champion set and the Gladius of the Crowd. */
public final class GroundsRewards {
	public static final String SET = "champion";
	public static final String CROWD_EFFECT = "crowd";
	public static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("proving_grounds_repair"));
	/** The 2-piece bonus: more attack damage, as a share of the base. */
	public static final double DAMAGE_BONUS = 0.10;
	/** The 4-piece bonus: more attack speed, as a share of the base. */
	public static final double SPEED_BONUS = 0.10;
	/** The 4-piece bonus: Strength I for this long after each kill. */
	public static final int KILL_STRENGTH_TICKS = 100;
	/** The Gladius: each consecutive hit adds this much damage. */
	public static final double CROWD_STEP = 0.06;
	public static final int CROWD_MAX = 5;
	public static final int CROWD_RESET_TICKS = 60;
	private static final Identifier CROWD_ID = StellaRuins.id("crowd");
	/** The stacks of each player who swings the Gladius, by uuid. */
	private static final Map<UUID, Crowd> CROWDS = new ConcurrentHashMap<>();

	private static RuinSets.SetDefinition set;
	private static Item gladius;

	private GroundsRewards() {
	}

	public static RuinSets.SetDefinition set() {
		return set;
	}

	public static Item gladius() {
		return gladius;
	}

	/** One player's run of hits: the stacks, and the entity tick of the last hit. */
	private static final class Crowd {
		private int stacks;
		private int lastHit;
	}

	public static void register() {
		set = RuinSets.define(SET, SetSpec.builder()
			.durabilityFactor(30)
			.defense(3, 8, 6, 3)
			.toughness(2.0F)
			.knockbackResistance(0.0F)
			.enchantValue(10)
			.repair(REPAIR)
			.equipmentAsset(StellaRuins.id(SET))
			.bonus(new SetBonus(2, List.of(AttributeBonus.multiplyBase(Attributes.ATTACK_DAMAGE, DAMAGE_BONUS))))
			.bonus(new SetBonus(4, List.of(AttributeBonus.multiplyBase(Attributes.ATTACK_SPEED, SPEED_BONUS))))
			.build());
		UniqueEffects.define(CROWD_EFFECT, new CrowdHook());
		gladius = RuinUniques.register("gladius_of_the_crowd", Item::new, new Item.Properties().stacksTo(1).sword(ToolMaterial.IRON, 3.0F, -2.4F), CROWD_EFFECT);
		ServerLivingEntityEvents.AFTER_DEATH.register(GroundsRewards::onKill);
		ServerTickEvents.END_SERVER_TICK.register(server -> expire(server.getPlayerList().getPlayers()));
	}

	/** Four pieces: a kill by the wearer gives Strength I for a while. */
	private static void onKill(final LivingEntity victim, final DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer killer && killer != victim && RuinSets.wornPieces(killer, SET) >= 4) {
			killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, KILL_STRENGTH_TICKS, 0));
		}
	}

	/** The stacks a player holds now, or 0. */
	public static int crowdStacks(final UUID player) {
		Crowd crowd = CROWDS.get(player);
		return crowd == null ? 0 : crowd.stacks;
	}

	private static void expire(final List<ServerPlayer> players) {
		Iterator<Map.Entry<UUID, Crowd>> it = CROWDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Crowd> entry = it.next();
			ServerPlayer owner = null;
			for (ServerPlayer player : players) {
				if (player.getUUID().equals(entry.getKey())) {
					owner = player;
				}
			}
			if (owner == null) {
				it.remove();
			} else if (owner.tickCount - entry.getValue().lastHit >= CROWD_RESET_TICKS || !holds(owner)) {
				apply(owner, 0);
				it.remove();
			}
		}
	}

	private static boolean holds(final LivingEntity user) {
		return CROWD_EFFECT.equals(RuinUniques.effectOf(user.getMainHandItem())) || CROWD_EFFECT.equals(RuinUniques.effectOf(user.getOffhandItem()));
	}

	private static void apply(final LivingEntity user, final int stacks) {
		AttributeInstance damage = user.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage == null) {
			return;
		}
		damage.removeModifier(CROWD_ID);
		if (stacks > 0) {
			damage.addTransientModifier(new AttributeModifier(CROWD_ID, CROWD_STEP * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Each hit adds a stack, to the cap. The stacks raise the damage of the next hit. */
	private static final class CrowdHook implements EffectHook {
		@Override
		public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
			if (!(user instanceof ServerPlayer player)) {
				return;
			}
			Crowd crowd = CROWDS.computeIfAbsent(player.getUUID(), id -> new Crowd());
			crowd.stacks = Math.min(CROWD_MAX, crowd.stacks + 1);
			crowd.lastHit = player.tickCount;
			apply(player, crowd.stacks);
		}
	}
}
