package com.stella.spells;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A cast opens a context on the server thread. Spell damage, applied effects, attributed heals, and explicit
 * touches mark it. A spell that acts on later ticks takes a {@link PendingCredit}, which pays the book once.
 */
public final class CastHits {
	/** One cast's right to credit its book later. It fires once, and never when the cast hit at cast time. */
	static final class PendingCredit {
		private final UUID caster;
		private final Identifier spell;
		private final AtomicBoolean live = new AtomicBoolean(true);

		private PendingCredit(final UUID caster, final Identifier spell) {
			this.caster = caster;
			this.spell = spell;
		}

		void fire(final MinecraftServer server) {
			if (this.live.compareAndSet(true, false)) {
				Awakening.creditPending(server, this.caster, this.spell);
			}
		}

		private void cancel() {
			this.live.set(false);
		}
	}

	private static final class Context {
		final UUID caster;
		final Identifier spell;
		final boolean countable;
		final List<PendingCredit> pending = new ArrayList<>();
		boolean touched;

		Context(final UUID caster, final Identifier spell, final boolean countable) {
			this.caster = caster;
			this.spell = spell;
			this.countable = countable;
		}
	}

	private static final ThreadLocal<Deque<Context>> OPEN = ThreadLocal.withInitial(ArrayDeque::new);
	/** Each cloud that waits to credit a book, weakly held so a cloud that never touches leaks nothing. */
	private static final Map<AreaEffectCloud, PendingCredit> CLOUDS = Collections.synchronizedMap(new WeakHashMap<>());

	private CastHits() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CastHits::afterDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(CastHits::onServerStopping);
	}

	/** Drops every pending credit, so none outlives the server that queued it. */
	static void onServerStopping(final MinecraftServer server) {
		CLOUDS.clear();
		OPEN.get().clear();
	}

	/** Opens the context of one cast; a scroll or an imbue opens none that counts. */
	static void begin(final ServerPlayer caster, final Identifier spell, final boolean countable) {
		OPEN.get().push(new Context(caster.getUUID(), spell, countable));
	}

	/** Marks the open cast as having hit something, when it is this caster's. */
	static void touch(final ServerPlayer caster) {
		Context context = OPEN.get().peek();
		if (context != null && context.caster.equals(caster.getUUID())) {
			context.touched = true;
		}
	}

	/** Closes the open cast; true when it hit something. Its pending credits are void when it did. */
	static boolean end() {
		return end(true);
	}

	static boolean end(final boolean completed) {
		Context context = OPEN.get().poll();
		if (context == null) {
			return false;
		}
		if (context.touched || !completed) {
			context.pending.forEach(PendingCredit::cancel);
		}
		return completed && context.touched;
	}

	/** A credit for the open cast of this caster, or null when no book cast can earn one. */
	static PendingCredit deferred(final ServerPlayer caster) {
		Context context = OPEN.get().peek();
		if (context == null || !context.countable || !context.caster.equals(caster.getUUID())) {
			return null;
		}
		if (context.pending.isEmpty()) {
			context.pending.add(new PendingCredit(context.caster, context.spell));
		}
		return context.pending.getFirst();
	}

	/** Gives a cloud the credit of the open cast; the cloud pays it on its first touch. */
	static void deferTo(final AreaEffectCloud cloud, final ServerPlayer caster) {
		PendingCredit credit = deferred(caster);
		if (credit != null) {
			CLOUDS.put(cloud, credit);
		}
	}

	/** Called after a successful {@code addEffect}: a touch of the open cast, or the first touch of a waiting cloud. */
	public static void onEffectAdded(final LivingEntity target, final Entity source) {
		if (source instanceof ServerPlayer caster) {
			touch(caster);
		} else if (source instanceof AreaEffectCloud cloud && target.level().getServer() != null) {
			PendingCredit credit = CLOUDS.get(cloud);
			if (credit != null) {
				CLOUDS.remove(cloud);
				credit.fire(target.level().getServer());
			}
		}
	}

	/** Unrelated heals cannot earn cast credit. */
	static boolean heal(final ServerPlayer caster, final LivingEntity recipient, final float amount) {
		float before = recipient.getHealth();
		recipient.heal(amount);
		boolean healed = recipient.getHealth() > before;
		if (healed) {
			touch(caster);
		}
		return healed;
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (damage > 0.0F && source.getEntity() instanceof ServerPlayer caster) {
			touch(caster);
		}
	}
}
