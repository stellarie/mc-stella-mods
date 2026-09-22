package com.stella.mobs.gear;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RoleSpec;
import com.stella.mobs.config.RollSpec;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

public final class GearRoller {
	private static final float SHIELD_CHANCE = 0.70F;
	// Monsters without hands or without a weapon attack cannot equip or use rolled gear.
	private static final Set<EntityType<?>> UNARMED = Set.of(
		EntityTypes.BLAZE, EntityTypes.BREEZE, EntityTypes.CREEPER, EntityTypes.ELDER_GUARDIAN,
		EntityTypes.ENDERMITE, EntityTypes.GUARDIAN, EntityTypes.RAVAGER, EntityTypes.SILVERFISH,
		EntityTypes.WARDEN, EntityTypes.WITHER
	);

	private GearRoller() {
	}

	public static boolean canRoll(final Mob mob) {
		return mob instanceof Monster && !UNARMED.contains(mob.getType());
	}

	public static void roll(
		final Mob mob, final RollSpec spec, final RandomSource random, final Registry<Enchantment> registry
	) {
		CombatRole wanted = configuredWeaponClass(mob);

		for (EquipmentSlot slot : GearTier.armorSlots()) {
			if (random.nextFloat() < spec.rollChance()) {
				GearTier tier = TierRoller.pick(random);
				apply(mob, slot, tier.armorFor(slot), tier, spec, random, registry);
			}
		}

		if (random.nextFloat() >= spec.rollChance()) {
			return;
		}

		GearTier tier = TierRoller.pick(random);
		boolean ranged = switch (wanted) {
			case RANGED -> true;
			case MELEE, MAGIC -> false;
			case null -> mob.canUseNonMeleeWeapon(new ItemStack(tier.rangedWeapon())) && random.nextBoolean();
		};
		Item weapon = ranged ? tier.rangedWeapon() : tier.meleeWeapon();

		if (ranged && !mob.canUseNonMeleeWeapon(new ItemStack(weapon))) {
			return;
		}

		apply(mob, EquipmentSlot.MAINHAND, weapon, tier, spec, random, registry);
		ReactiveState.get(mob).setMainHandRolled(true);

		if (ranged || wanted == CombatRole.MAGIC || !tier.allowsShield()) {
			return;
		}

		if (random.nextFloat() < SHIELD_CHANCE) {
			apply(mob, EquipmentSlot.OFFHAND, Items.SHIELD, tier, spec, random, registry);
		}
	}

	private static @Nullable CombatRole configuredWeaponClass(final Mob mob) {
		if (mob instanceof SpellcasterIllager) {
			return CombatRole.MAGIC;
		}

		Identifier entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		RoleSpec configured = GearConfigStore.get().roleFor(entityType);
		return configured != null ? configured.role() : null;
	}

	private static void apply(
		final Mob mob,
		final EquipmentSlot slot,
		final Item item,
		final GearTier tier,
		final RollSpec spec,
		final RandomSource random,
		final Registry<Enchantment> registry
	) {
		ItemStack stack = EnchantRoller.enchant(new ItemStack(item), random, registry);
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, (float) (tier.dropChance() * spec.dropChanceMultiplier()));
	}
}
