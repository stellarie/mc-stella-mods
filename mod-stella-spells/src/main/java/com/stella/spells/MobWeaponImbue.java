package com.stella.spells;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * A hostile mob's melee weapon has a small chance to carry one imbued spell. The roll runs once per mob, a
 * tick after it loads, so a weapon given at spawn by another mod is already in its hand. The spell then casts
 * through the same path as any imbued weapon in a mob's hand: {@link MobImbue}.
 */
final class MobWeaponImbue {
	static final double CHANCE = 0.05;
	static final String ROLLED_TAG = Spellbooks.MOD_ID + ".weapon_imbue_rolled";
	private static final List<Identifier> SPELLS = SpellImbue.MOB_SPELLS.stream().sorted().toList();
	private static final Queue<Mob> PENDING = new ConcurrentLinkedQueue<>();

	private MobWeaponImbue() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Monster mob && !mob.entityTags().contains(ROLLED_TAG)) {
				mob.addTag(ROLLED_TAG);
				PENDING.add(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Mob mob = PENDING.poll(); mob != null; mob = PENDING.poll()) {
				if (mob.isAlive()) {
					roll(mob);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	/** Imbues the mob's melee weapon with one random spell when the roll wins; true when it did. */
	static boolean roll(final Mob mob) {
		ItemStack weapon = mob.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!SpellImbue.isWeapon(weapon) || weapon.getItem() instanceof ProjectileWeaponItem || weapon.has(SpellImbue.IMBUED)) {
			return false;
		}
		if (mob.getRandom().nextDouble() >= CHANCE) {
			return false;
		}
		weapon.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(SPELLS.get(mob.getRandom().nextInt(SPELLS.size()))));
		return true;
	}
}
