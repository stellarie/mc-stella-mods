package com.stella.equips.mastery;

import com.stella.equips.StellaEquips;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/**
 * Surprise, equips: Weapon Mastery. A melee weapon remembers the hostiles it has killed, and at 10, 50,
 * 150 and 400 kills it reaches Mastery I to IV, each tier adding half a point of attack damage. The count
 * lives in the stack's own custom data and the bonus is an ordinary attribute modifier, so the weapon
 * keeps both through trades, anvils and restarts, and a vanilla client shows the damage in its tooltip.
 */
public final class WeaponMastery {
	public static final int[] THRESHOLDS = {10, 50, 150, 400};
	public static final double DAMAGE_PER_TIER = 0.5;
	public static final String KILLS_KEY = "stella_mastery_kills";
	public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "weapon_mastery");
	private static final String[] NUMERALS = {"", "I", "II", "III", "IV"};
	private static final String LORE_PREFIX = "Mastery";

	/** Test seam: each counted kill and each tier reached, keyed by the wielder. */
	public interface Recorder {
		default void counted(final ServerPlayer player, final ItemStack weapon, final int kills) {
		}

		default void tierReached(final ServerPlayer player, final ItemStack weapon, final int tier) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private WeaponMastery() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(WeaponMastery::afterDeath);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Swords, axes, spears, the trident and the mace: the weapons a player fights with in hand. */
	public static boolean isWeapon(final ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
			|| stack.is(Items.TRIDENT) || stack.is(Items.MACE);
	}

	public static int kills(final ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getIntOr(KILLS_KEY, 0);
	}

	public static int tier(final int kills) {
		int tier = 0;
		for (int threshold : THRESHOLDS) {
			if (kills >= threshold) {
				tier++;
			}
		}
		return tier;
	}

	/** Writes a kill count and brings the tier's modifier and tooltip line in line with it. */
	public static void setKills(final ItemStack stack, final int kills) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KILLS_KEY, kills));
		int tier = tier(kills);
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (!entry.modifier().id().equals(MODIFIER_ID)) {
				builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
			}
		}
		if (tier > 0) {
			builder.add(
				Attributes.ATTACK_DAMAGE,
				new AttributeModifier(MODIFIER_ID, tier * DAMAGE_PER_TIER, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		stack.set(DataComponents.LORE, withMasteryLine(stack, kills, tier));
	}

	private static ItemLore withMasteryLine(final ItemStack stack, final int kills, final int tier) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> lines = new ArrayList<>();
		for (Component line : lore.lines()) {
			if (!line.getString().startsWith(LORE_PREFIX)) {
				lines.add(line);
			}
		}
		String next = tier < THRESHOLDS.length ? ", " + THRESHOLDS[tier] + " for " + NUMERALS[tier + 1] : "";
		String name = tier == 0 ? LORE_PREFIX + " -" : LORE_PREFIX + " " + NUMERALS[tier];
		MasteryPerks.Perk perk = tier >= MasteryPerks.PERK_TIER ? MasteryPerks.perkFor(stack) : null;
		if (perk != null) {
			name += ": " + perk.title();
		}
		lines.add(Component.literal(name + " (" + kills + " kills" + next + ")")
			.withStyle(style -> style.withColor(tier == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD).withItalic(false)));
		return new ItemLore(lines);
	}

	/** A hostile killed in melee by a player counts for the weapon in that player's main hand. */
	static void afterDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) {
			return;
		}

		ItemStack weapon = player.getMainHandItem();
		if (!isWeapon(weapon)) {
			return;
		}

		int before = tier(kills(weapon));
		int kills = kills(weapon) + 1;
		setKills(weapon, kills);
		recorder.counted(player, weapon, kills);
		int tier = tier(kills);
		if (tier > before) {
			recorder.tierReached(player, weapon, tier);
			player.sendSystemMessage(Component.literal(weapon.getHoverName().getString() + " reached Mastery " + NUMERALS[tier])
				.withStyle(ChatFormatting.GOLD), true);
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=weaponMastery TIER player={} kills={} tier={}", player.getUUID(), kills, tier);
		}
	}
}
