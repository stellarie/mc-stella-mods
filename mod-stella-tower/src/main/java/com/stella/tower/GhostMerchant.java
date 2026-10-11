package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The wandering ghost merchant: a wandering trader's ghost that cannot be hurt or killed, wears the tower's soul fire, and
 * keeps its goods in a shop menu rather than in trade offers, which two slots of emeralds could not price. It sells only in the
 * tower (see {@link TowerMerchants#buy}). Within {@value TowerMerchants#AURA} blocks of it no mob targets a player.
 */
public class GhostMerchant extends WanderingTrader {
	/** The ticks between two turns of the merchant's glyphs. */
	private static final int GLYPH_TICKS = 4;
	private static final double AMBIENT_CHANCE = 1.0 / 400.0;

	private List<Identifier> goods = new ArrayList<>();

	public GhostMerchant(final EntityType<? extends GhostMerchant> type, final Level level) {
		super(type, level);
		this.setPermanentlyInvulnerable(true);
		this.setPersistenceRequired();
		this.setCustomName(Component.translatable("entity.mod-stella-tower.ghost_merchant").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		this.setCustomNameVisible(true);
	}

	/** A trader with no vanilla goals to flee, drink or wander off to far places: it keeps near its post and trades. */
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
		this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35));
		this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
	}

	/** The goods it offers, as the ids it was stocked with. */
	public List<MerchantGoods.Good> goods() {
		List<MerchantGoods.Good> offered = new ArrayList<>();
		for (Identifier id : this.goods) {
			MerchantGoods.Good good = MerchantGoods.byId(id);
			if (good != null && good.stocked().getAsBoolean()) {
				offered.add(good);
			}
		}
		return offered;
	}

	/** Stocks the merchant with exactly these goods. */
	public void stock(final List<MerchantGoods.Good> goods) {
		this.goods = new ArrayList<>(goods.stream().map(MerchantGoods.Good::id).toList());
	}

	/** Whether it has been stocked at all; a merchant made by a command stocks itself the first time it is spoken to. */
	public boolean isStocked() {
		return !this.goods.isEmpty();
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (!this.isAlive() || this.isTrading() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server && this.level() instanceof ServerLevel) {
			TowerMerchants.talk(this, server);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Its goods are in its shop menu; a trade offer would hold neither the price nor the item. */
	@Override
	protected void updateTrades(final ServerLevel level) {
	}

	/** It takes no damage of any kind. */
	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return true;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			GhostKit.wreathe(level, this);
			if (this.tickCount % GLYPH_TICKS == 0) {
				double angle = this.tickCount * 0.35;
				level.sendParticles(ParticleTypes.ENCHANT, this.getX() + Math.cos(angle) * 0.7, this.getY() + 1.0 + Math.sin(angle * 0.5) * 0.6,
					this.getZ() + Math.sin(angle) * 0.7, 2, 0.05, 0.05, 0.05, 0.4);
			}
			if (this.random.nextDouble() < AMBIENT_CHANCE) {
				level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Goods", Identifier.CODEC.listOf(), this.goods);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.goods = new ArrayList<>(input.read("Goods", Identifier.CODEC.listOf()).orElse(List.of()));
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}
}
