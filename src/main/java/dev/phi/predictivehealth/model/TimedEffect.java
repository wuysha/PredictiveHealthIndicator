package dev.phi.predictivehealth.model;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * A status effect we believe the opponent currently has. Remote players do not
 * sync their potion effects, so every effect is inferred from observed actions
 * (drinking, eating, thrown splash potions).
 */
public final class TimedEffect {
	public enum Kind {
		REGENERATION,
		ABSORPTION,
		RESISTANCE,
		STRENGTH,
		INSTANT_HEALTH,
		WEAKNESS,
		FIRE_RESISTANCE,
		POISON,
		OTHER
	}

	public final Kind kind;
	public final Holder<MobEffect> type;
	public final int amplifier;
	public int remaining;
	public int counter;

	public TimedEffect(Kind kind, Holder<MobEffect> type, int amplifier, int remaining) {
		this(kind, type, amplifier, remaining, 0);
	}

	public TimedEffect(Kind kind, Holder<MobEffect> type, int amplifier, int remaining, int counter) {
		this.kind = kind;
		this.type = type;
		this.amplifier = amplifier;
		this.remaining = remaining;
		this.counter = counter;
	}

	/** Regeneration heals one point every {@code 50 >> amplifier} ticks. */
	public int regenInterval() {
		int interval = 50 >> amplifier;
		return interval > 0 ? interval : 1;
	}

	/** Poison takes one point of health every {@code 25 >> amplifier} ticks. */
	public int poisonInterval() {
		int interval = 25 >> amplifier;
		return interval > 0 ? interval : 1;
	}
}
