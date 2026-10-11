package com.stella.equips.guardian;

import com.stella.equips.StellaEquips;
import com.stella.equips.enchant.Foes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

/**
 * The shield of the melee Tower Guardian. Each blow it blocks stacks +5 percent damage, up to 12 stacks. The next
 * melee blow of the holder deals that bonus, spends every stack and throws back the hostile mobs in front.
 */
public final class BoneShield {
	public static final String ID = "bone_shield";
	public static final int DURABILITY = 2000;
	public static final int MAX_STACKS = 12;
	public static final double BONUS_PER_STACK = 0.05;
	public static final double SHOVE_RANGE = 4.0;
	public static final double SHOVE_STRENGTH = 1.2;
	public static final double SHOVE_LIFT = 0.35;
	public static final double SHOVE_CONE_COSINE = 0.5;

	private static final AttachmentType<Integer> STACKS = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "bone_shield_stacks"));
	private static Item item;

	private BoneShield() {
	}

	static void register() {
		Identifier id = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, ID);
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))
			.durability(DURABILITY)
			.rarity(Rarity.EPIC)
			.repairable(Items.BONE_BLOCK)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			// The vanilla shield model draws this as a bone-white shield.
			.component(DataComponents.BASE_COLOR, DyeColor.WHITE)
			.component(DataComponents.ITEM_NAME, Component.translatableWithFallback("item." + StellaEquips.MOD_ID + "." + ID, "Bone Shield"))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, lookup -> new BlocksAttacks(0.25F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(lookup.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)));
		item = Registry.register(BuiltInRegistries.ITEM, id, new Item(properties));
	}

	public static Item item() {
		return item;
	}

	public static boolean is(final ItemStack stack) {
		return item != null && stack.is(item);
	}

	/** The stacks a holder has built up. */
	public static int stacks(final LivingEntity holder) {
		return ((AttachmentTarget) holder).getAttachedOrElse(STACKS, 0);
	}

	/** Called where the game knows how much damage a raised shield blocked. */
	public static void onBlock(final LivingEntity defender, final DamageSource source, final float blocked) {
		if (blocked <= 0.0F || !(defender instanceof ServerPlayer) || !is(defender.getItemBlockingWith())) {
			return;
		}
		((AttachmentTarget) defender).setAttached(STACKS, Math.min(MAX_STACKS, stacks(defender) + 1));
	}

	/** The damage of the holder's melee blow, with the stacks spent. Unchanged when the holder has no shield. */
	static float strike(final LivingEntity holder, final LivingEntity victim, final float amount) {
		int spent = stacks(holder);
		if (spent <= 0 || !(is(holder.getMainHandItem()) || is(holder.getOffhandItem()))) {
			return amount;
		}
		((AttachmentTarget) holder).removeAttached(STACKS);
		shove(holder);
		return amount * (float) (1.0 + BONUS_PER_STACK * spent);
	}

	// A mob is in front when it lies within 60 degrees of where the holder looks.
	private static void shove(final LivingEntity holder) {
		Vec3 look = holder.getLookAngle();
		Vec3 facing = new Vec3(look.x, 0.0, look.z);
		if (facing.lengthSqr() < 1.0e-6) {
			return;
		}
		facing = facing.normalize();
		for (Mob mob : holder.level().getEntitiesOfClass(Mob.class, holder.getBoundingBox().inflate(SHOVE_RANGE), mob -> Foes.isFoe(holder, mob))) {
			Vec3 away = new Vec3(mob.getX() - holder.getX(), 0.0, mob.getZ() - holder.getZ());
			double distance = away.length();
			if (distance < 1.0e-6 || distance > SHOVE_RANGE || away.scale(1.0 / distance).dot(facing) < SHOVE_CONE_COSINE) {
				continue;
			}
			Vec3 push = away.scale(SHOVE_STRENGTH / distance);
			mob.push(push.x, SHOVE_LIFT, push.z);
			mob.syncVelocity = true;
		}
	}
}
