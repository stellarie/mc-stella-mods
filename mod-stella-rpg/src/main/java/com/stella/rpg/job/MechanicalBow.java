package com.stella.rpg.job;

import com.stella.rpg.roll.Affixes;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A bow that draws fast, sometimes fires a free arrow, and gains perks with refine. */
public final class MechanicalBow extends BowItem {
	public static final int DURABILITY = 1000;
	public static final String SHOTS_KEY = "stella_bow_shots";
	private static final float EXTRA_SPREAD = 4.0F;

	public MechanicalBow(final Properties properties) {
		super(properties);
	}

	/** The factor on the held ticks, so a full draw takes fewer ticks than vanilla. */
	public static double drawFactor(final ItemStack stack) {
		return (double) JobPerks.VANILLA_DRAW_TICKS / JobPerks.bowDrawTicks(Affixes.refineLevel(stack));
	}

	@Override
	protected void shoot(
		final ServerLevel level, final LivingEntity shooter, final InteractionHand hand, final ItemStack weapon,
		final List<ItemStack> projectiles, final float power, final float uncertainty, final boolean crit, final LivingEntity target
	) {
		int refine = Affixes.refineLevel(weapon);
		int shot = nextShot(weapon);
		int triple = JobPerks.bowTripleEvery(refine);
		int extras = 0;
		if (triple > 0 && shot % triple == 0) {
			extras = 2;
		} else if (JobItems.roll(shooter, JobPerks.bowFreeArrow(refine))) {
			extras = 1;
		}
		ItemStack ammo = projectiles.isEmpty() ? ItemStack.EMPTY : projectiles.getFirst().copyWithCount(1);
		super.shoot(level, shooter, hand, weapon, projectiles, power, uncertainty, crit, target);
		if (ammo.isEmpty()) {
			return;
		}
		for (int i = 0; i < extras; i++) {
			Projectile projectile = createProjectile(level, shooter, weapon.isEmpty() ? new ItemStack(this) : weapon, ammo, crit);
			if (projectile instanceof AbstractArrow arrow) {
				arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			}
			float angle = (i % 2 == 0 ? 1 : -1) * EXTRA_SPREAD * (i / 2 + 1);
			shootProjectile(shooter, projectile, i, power, uncertainty, angle, target);
			level.addFreshEntity(projectile);
		}
	}

	@Override
	protected Projectile createProjectile(
		final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack ammo, final boolean crit
	) {
		Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, crit);
		int pierce = JobPerks.bowExtraPierce(Affixes.refineLevel(weapon));
		if (pierce > 0 && projectile instanceof AbstractArrow arrow) {
			((com.stella.rpg.mixin.AbstractArrowPierceInvoker) arrow).stellaRpg$setPierce((byte) (arrow.getPierceLevel() + pierce));
		}
		return projectile;
	}

	/** Counts the shots on the stack and returns the number of this one. */
	private static int nextShot(final ItemStack weapon) {
		CustomData data = weapon.get(DataComponents.CUSTOM_DATA);
		int shot = (data == null ? 0 : data.copyTag().getIntOr(SHOTS_KEY, 0)) + 1;
		CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putInt(SHOTS_KEY, shot));
		return shot;
	}
}
