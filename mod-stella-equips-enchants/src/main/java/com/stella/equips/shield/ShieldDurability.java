package com.stella.equips.shield;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * Every blocked hit costs a shield one durability point, small or large. Vanilla charges 1 plus the blocked damage,
 * and nothing for a hit under 3. This replaces the item damage rule of every item that blocks attacks.
 */
public final class ShieldDurability {
	// The game charges every hit on a raised shield,
	// even a miss with 0 blocked damage.
	// A threshold above 0 keeps that miss free.
	private static final float THRESHOLD = Math.nextUp(0.0F);
	public static final BlocksAttacks.ItemDamageFunction ONE_PER_BLOCK = new BlocksAttacks.ItemDamageFunction(THRESHOLD, 1.0F, 0.0F);

	private ShieldDurability() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> true, (builder, item) -> {
			BlocksAttacks blocks = builder.get(DataComponents.BLOCKS_ATTACKS);
			if (blocks != null) {
				builder.set(DataComponents.BLOCKS_ATTACKS, withOnePointPerBlock(blocks));
			}
		}));
	}

	static BlocksAttacks withOnePointPerBlock(final BlocksAttacks blocks) {
		return new BlocksAttacks(blocks.blockDelaySeconds(), blocks.disableCooldownScale(), blocks.damageReductions(), ONE_PER_BLOCK,
			blocks.bypassedBy(), blocks.blockSound(), blocks.disableSound());
	}
}
