package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.EffectHook;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import com.stella.ruins.world.RuinMobs;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.Equippable;

/** The materials, the named weapon, the affix and the banner that only the keep gives. */
public final class KeepRewards {
	public static final String OATH_EFFECT = "oath";
	public static final String RALLY_EFFECT = "rally";
	public static final String SIGIL_AFFIX = "keep_sigil";
	/** The health share below which the oath strikes twice, and the share the second hit adds. */
	public static final float OATH_THRESHOLD = 0.30F;
	public static final float OATH_BONUS = 0.25F;
	public static final int RALLY_RANGE_ALLIES = 6;
	public static final int RALLY_RANGE_HOSTILES = 12;
	public static final int RALLY_TICKS = 40;

	public static Item keyFragment;
	public static Item tarnishedCrest;
	public static Item spectralThread;
	public static Item castellansOath;
	public static Item bannerOfTheKeep;
	public static RuinAffixes.Affix sigil;

	private KeepRewards() {
	}

	public static void register() {
		keyFragment = RuinItems.register("keep_key_fragment", Item::new, new Item.Properties());
		tarnishedCrest = RuinItems.register("tarnished_crest", Item::new, new Item.Properties());
		spectralThread = RuinItems.register("spectral_thread", Item::new, new Item.Properties());

		UniqueEffects.define(OATH_EFFECT, new EffectHook() {
			@Override
			public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
				if (!(target.level() instanceof ServerLevel level) || target.getHealth() <= 0.0F) {
					return;
				}
				if (target.getHealth() < target.getMaxHealth() * OATH_THRESHOLD) {
					target.damageCooldownTime = 0;
					target.hurtServer(level, source, dealt * OATH_BONUS);
				}
			}
		});
		castellansOath = RuinUniques.register("castellans_oath", Item::new, new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F), OATH_EFFECT);

		UniqueEffects.define(RALLY_EFFECT, new EffectHook() {
			@Override
			public void onTick(final LivingEntity user) {
				if (!RALLY_EFFECT.equals(RuinUniques.effectOf(user.getItemBySlot(EquipmentSlot.OFFHAND))) || !(user.level() instanceof ServerLevel level)) {
					return;
				}
				boolean hostile = !level.getEntitiesOfClass(Mob.class, user.getBoundingBox().inflate(RALLY_RANGE_HOSTILES), m -> RuinMobs.isFoe(m) && m.isAlive()).isEmpty();
				if (!hostile) {
					return;
				}
				for (Player ally : level.getEntitiesOfClass(Player.class, user.getBoundingBox().inflate(RALLY_RANGE_ALLIES), p -> p.isAlive() && !p.isSpectator())) {
					if (ally.distanceTo(user) <= RALLY_RANGE_ALLIES) {
						ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_TICKS, 0, true, true));
					}
				}
			}
		});
		bannerOfTheKeep = RuinUniques.register("banner_of_the_keep", Item::new, new Item.Properties().stacksTo(1), RALLY_EFFECT);

		sigil = RuinAffixes.define(SIGIL_AFFIX, new AffixSpec(
			KeepRewards::isArmor,
			Ingredient.of(tarnishedCrest),
			List.of(AttributeBonus.add(Attributes.ARMOR, 2.0), AttributeBonus.add(Attributes.ARMOR_TOUGHNESS, 1.0)),
			null));
	}

	static boolean isArmor(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}
}
