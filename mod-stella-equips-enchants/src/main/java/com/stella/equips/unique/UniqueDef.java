package com.stella.equips.unique;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.jspecify.annotations.Nullable;

/**
 * One unique item. The sources are entity type ids. The id {@code mod-stella-nether:sovereign} stands for the Blaze
 * that the sovereign share accepts. The tier texts describe the effect at +7, +10, +12 and +15.
 */
public record UniqueDef(
	String id, String name, UniqueJob job, UniqueKind kind,
	List<Identifier> sources,
	String base, String plus7, String plus10, String plus12, String plus15,
	@Nullable ResourceKey<EquipmentAsset> look,
	UniqueEffect effect
) {
}
