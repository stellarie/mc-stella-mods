package com.stella.rpg.job;

import com.stella.rpg.ItemDescription;
import com.stella.rpg.RpgCombat;
import com.stella.rpg.StellaRpg;
import com.stella.rpg.roll.Affixes;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The four job items and the hooks their perks use. Each perk reads the refine level of the worn or held item.
 * The combat and spell code call the hooks below, so this class holds no damage rule of its own.
 */
public final class JobItems {
	private static final double HEALER_RANGE_SQR = 48.0 * 48.0;
	private static final int REGEN_TICKS = 60;

	private static final ResourceKey<Item> BOW_KEY = key("mechanical_bow");
	private static final ResourceKey<Item> SWORD_KEY = key("knight_sword");
	private static final ResourceKey<Item> HAT_KEY = key("witch_hat");
	private static final ResourceKey<Item> GARBS_KEY = key("priest_garbs");

	private static final ToolMaterial SWORD_MATERIAL = new ToolMaterial(
		BlockTags.INCORRECT_FOR_IRON_TOOL, 800, 6.0F, 2.0F, 14, ItemTags.IRON_TOOL_MATERIALS
	);
	private static final ArmorMaterial HAT_MATERIAL = armorMaterial("witch_hat", 20);
	private static final ArmorMaterial GARBS_MATERIAL = armorMaterial("priest_garbs", 20);

	public static final MechanicalBow MECHANICAL_BOW = Registry.register(BuiltInRegistries.ITEM, BOW_KEY, new MechanicalBow(
		new Item.Properties().setId(BOW_KEY).durability(MechanicalBow.DURABILITY)
			.component(ItemDescription.TYPE, lore("mechanical_bow", 2))
	));
	public static final Item KNIGHT_SWORD = Registry.register(BuiltInRegistries.ITEM, SWORD_KEY, new Item(
		new Item.Properties().setId(SWORD_KEY).sword(SWORD_MATERIAL, 3.0F, -2.4F)
			.component(ItemDescription.TYPE, lore("knight_sword", 2))
	));
	public static final Item WITCH_HAT = Registry.register(BuiltInRegistries.ITEM, HAT_KEY, new Item(
		new Item.Properties().setId(HAT_KEY).humanoidArmor(HAT_MATERIAL, ArmorType.HELMET)
			.component(ItemDescription.TYPE, lore("witch_hat", 1))
	));
	public static final Item PRIEST_GARBS = Registry.register(BuiltInRegistries.ITEM, GARBS_KEY, new Item(
		new Item.Properties().setId(GARBS_KEY).humanoidArmor(GARBS_MATERIAL, ArmorType.CHESTPLATE)
			.component(ItemDescription.TYPE, lore("priest_garbs", 2))
	));

	private static final RpgCombat.Roller FAIR = (attacker, random, chance) -> chance > 0.0 && random.nextDouble() < chance;
	private static volatile RpgCombat.Roller roller = FAIR;

	/** The healer of this game tick, so the garbs perks reach the target of the heal that follows. */
	private static ServerPlayer healer;
	private static long healerTick = -1L;

	private JobItems() {
	}

	public static void register() {
		ItemDescription.register();
		Bleed.register();
		ServerLivingEntityEvents.AFTER_DAMAGE.register(JobItems::afterDamage);
		StellaRpg.LOGGER.debug("Registered the job items {}", BuiltInRegistries.ITEM.getKey(MECHANICAL_BOW));
	}

	/** A test sets a fixed roller. Null restores the fair one. */
	public static void setRoller(final RpgCombat.Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	static boolean roll(final LivingEntity user, final double chance) {
		return roller.roll(user, user.getRandom(), chance);
	}

	/** Gives the item of the job. It goes to the inventory, or to the ground at the player's feet when full. */
	static boolean grant(final ServerPlayer player, final Job job) {
		Item item = switch (job) {
			case ARCHER -> MECHANICAL_BOW;
			case FIGHTER -> KNIGHT_SWORD;
			case MAGE -> WITCH_HAT;
			case PRIEST -> PRIEST_GARBS;
			case NONE -> null;
		};
		if (item == null) {
			return false;
		}
		ItemStack stack = new ItemStack(item);
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
		}
		return true;
	}

	// ----- worn and held items -----

	private static int refine(final LivingEntity entity, final EquipmentSlot slot, final Item item) {
		ItemStack stack = entity.getItemBySlot(slot);
		return stack.is(item) ? Affixes.refineLevel(stack) : -1;
	}

	private static int sword(final LivingEntity entity) {
		return refine(entity, EquipmentSlot.MAINHAND, KNIGHT_SWORD);
	}

	private static int hat(final LivingEntity entity) {
		return refine(entity, EquipmentSlot.HEAD, WITCH_HAT);
	}

	private static int garbs(final LivingEntity entity) {
		return refine(entity, EquipmentSlot.CHEST, PRIEST_GARBS);
	}

	// ----- bow -----

	/** The draw speed factor of a held bow. Other items draw at the vanilla speed. */
	public static double drawFactor(final ItemStack stack) {
		return stack.getItem() instanceof MechanicalBow ? MechanicalBow.drawFactor(stack) : 1.0;
	}

	// ----- sword -----

	/** The extra armor the knight sword ignores on a melee hit. */
	public static double armorPenetration(final LivingEntity attacker) {
		int refine = sword(attacker);
		return refine < 0 ? 0.0 : JobPerks.swordArmorPen(refine);
	}

	/** The damage factor of a melee hit with the sword: more against a bleeding target at +15. */
	public static double meleeFactor(final LivingEntity attacker, final LivingEntity victim) {
		int refine = sword(attacker);
		if (refine < 0 || !Bleed.isBleeding(victim)) {
			return 1.0;
		}
		return 1.0 + JobPerks.swordBleedingBonus(refine);
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked
	) {
		if (blocked || damageTaken <= 0.0F || DoubleStrike.striking() || !source.is(DamageTypes.PLAYER_ATTACK)
			|| !(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker
			|| attacker == victim || !victim.isAlive()) {
			return;
		}
		int refine = sword(attacker);
		if (refine >= 0 && roll(attacker, JobPerks.swordBleedChance(refine))) {
			Bleed.apply(victim, JobPerks.swordBleedShare(refine));
		}
	}

	// ----- hat -----

	/** The factor on the spell damage of a wearer of the hat. */
	public static double spellDamageFactor(final LivingEntity caster) {
		int refine = hat(caster);
		return refine < 0 ? 1.0 : 1.0 + JobPerks.hatSpellDamage(refine);
	}

	public static double magicPenetration(final LivingEntity caster) {
		int refine = hat(caster);
		return refine < 0 ? 0.0 : JobPerks.hatMagicPenetration(refine);
	}

	public static double cooldownFactor(final LivingEntity caster) {
		int refine = hat(caster);
		return refine < 0 ? 1.0 : JobPerks.hatCooldown(refine);
	}

	public static double spellCritBonus(final LivingEntity caster) {
		int refine = hat(caster);
		return refine < 0 ? 0.0 : JobPerks.hatSpellCrit(refine);
	}

	// ----- garbs -----

	/** The factor on a hit that an undead attacker lands on a wearer of the garbs. */
	public static double incomingFactor(final LivingEntity victim, final DamageSource source) {
		int refine = garbs(victim);
		if (refine < 0 || !(source.getEntity() instanceof LivingEntity attacker) || !attacker.is(EntityTypeTags.UNDEAD)) {
			return 1.0;
		}
		return 1.0 - JobPerks.garbsUndeadReduction(refine);
	}

	/** The factor on the healing a wearer of the garbs does. Also marks the healer for the target perks. */
	public static double healingFactor(final LivingEntity healerEntity) {
		int refine = garbs(healerEntity);
		if (refine < 0) {
			return 1.0;
		}
		if (healerEntity instanceof ServerPlayer player && (JobPerks.garbsCleanse(refine) || JobPerks.garbsRegeneration(refine))) {
			healer = player;
			healerTick = player.level().getGameTime();
		}
		return 1.0 + JobPerks.garbsHealing(refine);
	}

	/** Runs when an entity heals. A heal in the tick of a garbs healer, near that healer, gets the target perks. */
	public static void onHeal(final LivingEntity target) {
		ServerPlayer source = healer;
		if (source == null || healerTick != target.level().getGameTime() || source.level() != target.level()
			|| source.distanceToSqr(target) > HEALER_RANGE_SQR) {
			return;
		}
		int refine = garbs(source);
		if (refine < 0) {
			return;
		}
		if (JobPerks.garbsCleanse(refine)) {
			target.getActiveEffects().stream()
				.filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
				.findFirst()
				.ifPresent(effect -> target.removeEffect(effect.getEffect()));
		}
		if (JobPerks.garbsRegeneration(refine)) {
			target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));
		}
	}

	// ----- registration helpers -----

	private static ResourceKey<Item> key(final String path) {
		return ResourceKey.create(Registries.ITEM, StellaRpg.id(path));
	}

	private static ArmorMaterial armorMaterial(final String asset, final int durabilityFactor) {
		return new ArmorMaterial(
			durabilityFactor, ArmorMaterials.makeDefense(2, 5, 6, 2, 5), 14, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F,
			ItemTags.REPAIRS_IRON_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, StellaRpg.id(asset))
		);
	}

	private static ItemLore lore(final String item, final int lines) {
		List<Component> out = new java.util.ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			out.add(Component.translatable("item.mod-stella-rpg." + item + ".lore" + i));
		}
		return new ItemLore(out);
	}
}
