package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An evoker of the tower's ghosts. Its fangs and its casting are the evoker's own; it summons ghost vexes, which stand on the
 * players' side as it does, and it never flees a player or wakes a sheep.
 */
public class GhostEvoker extends Evoker implements Ghostly {
	/** How many ghost vexes a cast summons. */
	public static final int VEXES_PER_CAST = 3;
	private static final int SUMMON_CASTING_TICKS = 100;
	private static final int SUMMON_INTERVAL_TICKS = 340;
	private static final double VEX_COUNT_RANGE = 16.0;

	public GhostEvoker(final EntityType<? extends GhostEvoker> type, final Level level) {
		super(type, level);
		GhostKit.settle(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return GhostKit.paced(Evoker::createAttributes);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		GhostKit.clearVanilla(this.goalSelector, this.targetSelector);
		// The evoker's own summon and wololo are replaced; its casting and fangs stay.
		this.goalSelector.removeAllGoals(goal -> goal instanceof Evoker.EvokerWololoSpellGoal || goal.getClass().getSimpleName().equals("EvokerSummonSpellGoal"));
		this.goalSelector.addGoal(4, new GhostSummonGoal());
		GhostSquads.addSquadGoals(this, this.goalSelector);
		GhostKit.hunt(this, this.targetSelector);
	}

	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		if (!GhostKit.refuses(target)) {
			super.setTarget(target);
		}
	}

	@Override
	public boolean canAttack(final LivingEntity target) {
		return !GhostKit.refuses(target) && super.canAttack(target);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		GhostKit.wreathe(level, this);
	}

	/** A ghost leaves no loot, no gear and no experience. */
	@Override
	protected void dropAllDeathLoot(final ServerLevel level, final DamageSource source) {
	}

	@Override
	public @Nullable ItemEntity spawnAtLocation(final ServerLevel level, final ItemStack itemStack, final Vec3 offset) {
		return null;
	}

	@Override
	public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	/** The evoker's summon, with ghost vexes in place of the vexes of the raids. */
	private final class GhostSummonGoal extends SpellcasterUseSpellGoal {
		@Override
		public boolean canUse() {
			if (!super.canUse()) {
				return false;
			}
			int vexes = GhostEvoker.this.level().getEntitiesOfClass(GhostVex.class,
				GhostEvoker.this.getBoundingBox().inflate(VEX_COUNT_RANGE), GhostVex::isAlive).size();
			return GhostEvoker.this.random.nextInt(8) + 1 > vexes;
		}

		@Override
		protected int getCastingTime() {
			return SUMMON_CASTING_TICKS;
		}

		@Override
		protected int getCastingInterval() {
			return SUMMON_INTERVAL_TICKS;
		}

		@Override
		protected void performSpellCasting() {
			if (!(GhostEvoker.this.level() instanceof ServerLevel level)) {
				return;
			}
			for (int i = 0; i < VEXES_PER_CAST; i++) {
				BlockPos at = GhostEvoker.this.blockPosition().offset(-2 + GhostEvoker.this.random.nextInt(5), 1, -2 + GhostEvoker.this.random.nextInt(5));
				GhostVex vex = GhostSquads.summonVex(level, GhostEvoker.this, at);
				if (vex != null) {
					level.gameEvent(GameEvent.ENTITY_PLACE, at, GameEvent.Context.of(GhostEvoker.this));
				}
			}
		}

		@Override
		protected @Nullable SoundEvent getSpellPrepareSound() {
			return SoundEvents.EVOKER_PREPARE_SUMMON;
		}

		@Override
		protected IllagerSpell getSpell() {
			return IllagerSpell.SUMMON_VEX;
		}
	}
}
