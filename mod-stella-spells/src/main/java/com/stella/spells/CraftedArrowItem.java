package com.stella.spells;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An arrow of one {@link ArrowKind}. It looses the vanilla arrow entity with this stack as its pickup, so
 * a bow, a crossbow and a dispenser all treat it as an arrow, and {@link ArrowHits} reads the kind back.
 */
public final class CraftedArrowItem extends ArrowItem {
	private final ArrowKind kind;

	public CraftedArrowItem(final Properties properties, final ArrowKind kind) {
		super(properties);
		this.kind = kind;
	}

	public ArrowKind kind() {
		return this.kind;
	}

	@Override
	public AbstractArrow createArrow(final Level level, final ItemStack ammo, final LivingEntity owner, final ItemStack weapon) {
		AbstractArrow arrow = super.createArrow(level, ammo, owner, weapon);
		ArrowHits.prepare(arrow, this.kind);
		return arrow;
	}

	@Override
	public Projectile asProjectile(final Level level, final Position position, final ItemStack stack, final Direction direction) {
		Projectile projectile = super.asProjectile(level, position, stack, direction);
		if (projectile instanceof AbstractArrow arrow) {
			ArrowHits.prepare(arrow, this.kind);
		}
		return projectile;
	}
}
