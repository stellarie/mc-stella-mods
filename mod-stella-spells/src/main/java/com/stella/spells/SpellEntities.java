package com.stella.spells;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;

/**
 * The entity types this mod adds: the Blitz Beat falcon and the summoned wolf. Both are new registry entries
 * drawn with vanilla models, so a client needs this mod to join a server that has it.
 */
public final class SpellEntities {
	private static final int FALCON_TRACKING_RANGE = 10;
	private static final int WOLF_TRACKING_RANGE = 10;

	/** MISC keeps the falcon out of the mob cap. It is never saved: the buff and the owner bring it back. */
	public static final EntityType<FalconEntity> FALCON = register("falcon", EntityType.Builder
		.of(FalconEntity::new, MobCategory.MISC).sized(0.5F, 0.9F).eyeHeight(0.6F).clientTrackingRange(FALCON_TRACKING_RANGE).noSave());
	/** MISC as well: a summon must not crowd the mob cap of the player who raised it. */
	public static final EntityType<SummonedWolf> SUMMONED_WOLF = register("summoned_wolf", EntityType.Builder
		.of(SummonedWolf::new, MobCategory.MISC).sized(0.6F, 0.85F).eyeHeight(0.68F).passengerAttachments(0.85F)
		.clientTrackingRange(WOLF_TRACKING_RANGE));

	private SpellEntities() {
	}

	static void register() {
		FabricDefaultAttributeRegistry.register(FALCON, Parrot.createAttributes());
		FabricDefaultAttributeRegistry.register(SUMMONED_WOLF, Wolf.createAttributes());
	}

	private static <T extends Mob> EntityType<T> register(final String path, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
