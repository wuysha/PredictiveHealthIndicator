package dev.phi.predictivehealth.model;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod believes about one opponent.
 *
 * <p>The health is never read from the entity data of the opponent. Servers that
 * protect against health indicators either hide that data or send a fake value,
 * so the only trustworthy source is the sum of the damage we dealt and the
 * healing we saw. The estimate starts at the maximum health and moves with every
 * observable event.
 *
 * <p>The health is kept as a running value instead of a difference of two
 * counters. A counter of healing would grow forever and cancel every later hit,
 * because the healing of a full health bar is thrown away by the server but
 * still counted by the client.
 */
public final class TargetState {
	/** Food points of the opponent, mirrored from the vanilla food data. */
	private static final int MAX_FOOD = 20;
	/**
	 * Saturation a fresh player starts with.
	 *
	 * <p>Vanilla starts with five, the opponent gets three more because a player
	 * that is in a fight has usually eaten something before.
	 */
	private static final double START_SATURATION = 8.0D;
	/** Exhaustion that is removed at once, together with one point of saturation. */
	private static final double EXHAUSTION_DROP = 4.0D;
	/** Ticks between two regenerations that use the saturation. */
	private static final int SATURATED_REGEN_TICKS = 10;
	/** Ticks between two plain regenerations. */
	private static final int REGEN_TICKS = 80;
	/** Food points that are needed for the plain regeneration. */
	private static final int REGEN_FOOD_LEVEL = 18;
	/** Saturation that is used for one regeneration at most. */
	private static final double SATURATION_PER_REGEN = 6.0D;
	/** Absorption points one level of the absorption effect is worth. */
	public static final double ABSORPTION_PER_LEVEL = 4.0D;
	/** Ticks the healing of a potion of instant health is shown. */
	private static final int HEALING_TICKS = 100;
	/** Height of the jump of a heart after a hit, in blocks. */
	private static final double JUMP_HEIGHT = 0.029D;
	/** Ticks the jump of a heart takes. */
	private static final int JUMP_TICKS = 10;

	public final int entityId;
	public String name = "?";

	public double maxHealth = 20.0D;
	public double armor;
	public double toughness;

	/**
	 * Enchantment protection points that work against every damage.
	 *
	 * <p>A piece that carries the blast protection carries no protection
	 * enchantment, so it adds nothing to these points and the opponent takes
	 * more damage from a hit.
	 */
	public int protection;
	/** Enchantment protection points that work only against an explosion. */
	public int blastProtection;
	/** The points that work against an explosion, the general ones and the blast ones. */
	public int explosionProtection;
	/**
	 * The points the armour would have if no piece carried the blast protection.
	 *
	 * <p>The measurement of the blast protection is made against this reference.
	 */
	public int protectionBaseline;
	/** The baseline points of the leggings, the piece the blast protection sits on. */
	public int leggingsBaseline;

	/**
	 * The blast protection the server shows in its attributes.
	 *
	 * <p>The servers that protect against health indicators rewrite the equipment
	 * packets of the players, but they leave the attributes alone. The blast
	 * protection adds a modifier to an attribute of the holder, and that modifier
	 * names the enchantment and the slot it sits in, so the enchantment is still
	 * known even when the equipment says nothing about it.
	 */
	public BlastSignal blastSignal = BlastSignal.EMPTY;

	/** Blocks the opponent falls without taking damage. */
	public double safeFallDistance = 3.0D;
	/** Multiplier of the fall damage of the opponent. */
	public double fallDamageMultiplier = 1.0D;
	/** Protection points the feather falling enchantment adds to a fall. */
	public int fallProtection;

	/** Armour attribute value the server sent to us. */
	public double armorAttribute;
	/** Armour points that the equipment we can see adds. */
	public double armorEquipment;

	/** Predicted health the opponent has right now. */
	public double health = 20.0D;
	/** Absorption points that soak damage before the health. */
	public double absorption;

	public double confidence = 0.25D;
	public boolean alive = true;
	public long lastInteractionTick;
	public long lastHitTick = Long.MIN_VALUE;
	public float lastDamageToHealth;
	public int blockedHits;

	/** Rolling average of the health damage we managed to deal to this target. */
	public double recentDamage = 6.0D;

	/** Raw damage of the last hit, needed for the vanilla hurt-time rule. */
	public float lastRawDamage;
	/** Cooldown of our last swing against this target. */
	public float lastCooldown = 1.0F;
	/** Points the absorption of the target soaked of our last hit. */
	public float lastAbsorbed;
	/** Tick of the last damage the opponent took from any source. */
	public long lastDamageTick = Long.MIN_VALUE;
	/** Tick of an explosion that hit the opponent and is not resolved yet. */
	public long pendingExplosionTick = Long.MIN_VALUE;
	/** Horizontal knockback an explosion gave the opponent, zero if unknown. */
	public float explosionKnockback;
	/** Raw damage of the explosions, before the armour and the enchantments. */
	public double explosionRawDamage;
	/** Damage of the explosions after the armour, before the enchantments. */
	public double explosionArmourDamage;
	/** Health damage the model took from the explosions. */
	public double explosionModelledDamage;
	/**
	 * Level of the blast protection that was measured from an explosion.
	 *
	 * <p>Zero when nothing was measured, the level of the enchantment is then
	 * still unknown.
	 */
	public int blastProtectionLevel;
	/** True when the level of the blast protection was measured. */
	public boolean blastProtectionMeasured;
	/**
	 * True when the measured level is only a lower bound.
	 *
	 * <p>The vanilla damage pipeline never uses more than twenty protection
	 * points, so a level of four and a level of two look the same there.
	 */
	public boolean blastProtectionCapped;
	/** Velocity of the opponent in the previous tick. */
	public Vec3 previousVelocity = Vec3.ZERO;

	/** Sum of the health that all the hits of this fight took away. */
	public double damageApplied;
	/** Sum of the health damage of the model, before the correction. */
	public double rawDamageApplied;
	/** Sum of the health that all the heals of this fight gave back. */
	public double healingApplied;
	/** Sum of the points the absorption soaked of the damage. */
	public double absorbedApplied;
	/**
	 * Factor that corrects the damage.
	 *
	 * <p>The enchantments of the opponent are hidden by the servers that
	 * protect against health indicators, so the damage of the model is too
	 * high. The factor is learned from the moments where the real health is
	 * known anyway, the death of the opponent and the totem that saved them.
	 */
	public double damageScale = 1.0D;
	/** Tick the mod is on, it is the base of every time stamp of the state. */
	public long currentTick;

	/** Attack damage of the weapon this player holds, used to guess their hits. */
	public float weaponDamage = DamageModel.PLAYER_BASE_ATTACK_DAMAGE;
	/** Sharpness bonus of the weapon this player holds. */
	public float weaponEnchantmentDamage;

	/** Height of the player in the previous tick, used to follow their falls. */
	public double previousY = Double.NaN;
	/** Height the current fall started at. */
	public double fallStartY;
	/** Distance of the last completed fall. */
	public double lastFallDistance;

	/** True when we damaged this player ourselves. */
	public boolean engaged;

	/** Food points of the opponent, mirrored from the vanilla food data. */
	private int foodLevel = MAX_FOOD;
	/** Ticks the opponent is still healing after a potion of instant health. */
	private int healingTicks;
	/** Saturation of the opponent, the source of the fast regeneration. */
	private double saturationLevel = START_SATURATION;
	/** Exhaustion of the opponent, it eats the saturation away. */
	private double exhaustionLevel;
	/** Counter of the vanilla food tick timer. */
	private int foodTickTimer;

	/** Health that is shown, it follows the prediction smoothly. */
	private double shownHealth = Double.NaN;

	/** Index of the heart that jumps after a hit, -1 when nothing jumps. */
	private int jumpingHeart = -1;
	/** Remaining ticks of the jump. */
	private int jumpTicks;

	public final List<TimedEffect> effects = new ArrayList<>();
	public PendingConsume pendingConsume;

	public TargetState(int entityId) {
		this.entityId = entityId;
	}

	/**
	 * Health the mod shows above the head.
	 *
	 * <p>This is the prediction, never a value read from the server.
	 */
	public double displayHealth() {
		return health;
	}

	/** Maximum health the mod shows above the head. */
	public double displayMaxHealth() {
		// The absorption is a part of the health bar of the vanilla HUD, so a
		// golden apple raises the maximum from twenty to twenty four.
		return maxHealth + absorption;
	}

	/**
	 * Health that is left in total, the absorption included.
	 *
	 * <p>The absorption soaks the damage before the health does, so both values
	 * move together and the sum is what the opponent can still lose.
	 */
	public double effectiveHealth() {
		return health + absorption;
	}

	/**
	 * The health that is drawn right now.
	 *
	 * <p>The prediction itself jumps whenever a hit lands, so the drawn value
	 * runs towards it instead. Damage shows up at once, healing takes a moment.
	 */
	public double smoothHealth(double target, float deltaTicks) {
		if (Double.isNaN(shownHealth)) {
			shownHealth = target;
			return target;
		}

		double seconds = Math.max(0.0D, deltaTicks) / 20.0D;
		double rate = target < shownHealth ? 45.0D : 14.0D;
		shownHealth += (target - shownHealth) * (1.0D - Math.exp(-rate * seconds));

		if (Math.abs(target - shownHealth) < 0.01D) {
			shownHealth = target;
		}

		return shownHealth;
	}

	public double totalHealth() {
		return health + absorption;
	}

	/** Food points of the opponent. */
	public int food() {
		return foodLevel;
	}

	/** Saturation of the opponent. */
	public double saturation() {
		return saturationLevel;
	}

	/** True when the opponent is missing health and can regenerate. */
	public boolean isHurt() {
		return health < maxHealth;
	}

	/**
	 * Applies the vanilla hurt-time rule and returns the damage that actually
	 * reaches the health bar.
	 *
	 * <p>Vanilla keeps the last raw damage of a victim. A hit that lands within
	 * ten ticks of the previous one only deals the difference, and a hit that is
	 * not bigger than the previous one is rejected outright.
	 */
	public float effectiveRawDamage(float raw, long tick) {
		boolean withinWindow = lastDamageTick != Long.MIN_VALUE
			&& tick - lastDamageTick <= DamageModel.HURT_WINDOW_TICKS;
		float effective = withinWindow ? Math.max(0.0F, raw - lastRawDamage) : raw;

		lastRawDamage = raw;
		lastDamageTick = tick;
		return effective;
	}

	public void applyDamage(DamageBreakdown breakdown, long tick) {
		double before = health;
		double lost = breakdown.toHealth() * damageScale;

		absorption = Math.max(0.0D, absorption - breakdown.absorbed());
		health = Math.max(0.0D, health - lost);
		damageApplied += lost;
		rawDamageApplied += breakdown.toHealth();
		absorbedApplied += breakdown.absorbed();
		startJump(before, health);
		lastHitTick = tick;
		lastDamageToHealth = (float) lost;
		lastAbsorbed = breakdown.absorbed();
		confidence = Math.min(1.0D, confidence + 0.03D);
		dropSpentAbsorption();
	}

	/**
	 * Damage from a source we did not cause ourselves, for example a hit of
	 * another player or a fall. The amount is an estimate, so the confidence
	 * drops a little.
	 */
	public void applyExternalDamage(DamageBreakdown breakdown, long tick) {
		double before = health;
		double lost = breakdown.toHealth() * damageScale;

		absorption = Math.max(0.0D, absorption - breakdown.absorbed());
		health = Math.max(0.0D, health - lost);
		damageApplied += lost;
		rawDamageApplied += breakdown.toHealth();
		absorbedApplied += breakdown.absorbed();
		startJump(before, health);
		lastDamageTick = tick;
		confidence = Math.max(0.0D, confidence - 0.05D);
		dropSpentAbsorption();
	}

	/**
	 * Corrects the damage of the model with a moment where the real health is
	 * known.
	 *
	 * <p>The enchantments of the opponent are hidden by the servers that protect
	 * against health indicators, so the mitigation of the model is too weak and
	 * the modelled damage is too high. The only moments where the real health
	 * cannot be hidden are the death of the opponent and the totem that saves
	 * them, and both of them are used here to learn the factor of this opponent.
	 *
	 * @param realHealth health the opponent really had in that moment
	 * @return true when the factor was changed
	 */
	public boolean calibrate(double realHealth) {
		if (rawDamageApplied < 1.0D) {
			return false;
		}

		double target = maxHealth + healingApplied - realHealth;
		double scale = target / rawDamageApplied;

		// Only a damage that is too high can be corrected. A hidden enchantment
		// always makes the real damage smaller than the modelled one, so a factor
		// above one would mean that we missed a hit instead.
		if (scale <= 0.05D || scale >= 1.0D) {
			return false;
		}

		damageScale = scale;
		damageApplied = rawDamageApplied * scale;
		health = Math.max(0.0D, Math.min(maxHealth, realHealth));
		return true;
	}

	/**
	 * The absorption effect ends with its points, so the golden hearts of the
	 * opponent disappear as soon as the last one is used up.
	 */
	private void dropSpentAbsorption() {
		if (absorption <= 0.0D) {
			effects.removeIf(effect -> effect.kind == TimedEffect.Kind.ABSORPTION);
		}
	}

	/**
	 * The heart that lost its fill jumps up for a moment.
	 *
	 * @param index index of the heart in the row
	 * @return height of the jump in blocks
	 */
	public double jumpOffset(int index) {
		if (index != jumpingHeart || jumpTicks <= 0) {
			return 0.0D;
		}

		double progress = 1.0D - jumpTicks / (double) JUMP_TICKS;
		return JUMP_HEIGHT * Math.sin(Math.PI * progress);
	}

	/** Counts the jump of the hearts down. */
	public void tickAnimation() {
		if (jumpTicks > 0) {
			jumpTicks--;
		}
	}

	private void startJump(double before, double after) {
		int beforeHearts = (int) Math.ceil(before / 2.0D);
		int afterHearts = (int) Math.ceil(after / 2.0D);

		if (afterHearts < beforeHearts) {
			jumpingHeart = afterHearts;
			jumpTicks = JUMP_TICKS;
		}
	}

	/**
	 * Healing of a potion, a regeneration effect or the food.
	 *
	 * <p>Vanilla heals only up to the maximum health, everything above is
	 * thrown away. The same happens here, so the healing can never inflate the
	 * estimate above the maximum health.
	 *
	 * @param instant true for a potion of instant health, it heals at once
	 */
	public void addHealing(double amount, boolean instant) {
		if (amount <= 0.0D) {
			return;
		}

		if (instant) {
			// A potion of instant health heals at once, so the indicator shows the
			// healing for a moment.
			healingTicks = HEALING_TICKS;
		}

		if (health < maxHealth) {
			double before = health;
			health = Math.min(maxHealth, health + amount);
			healingApplied += health - before;

			if (instant) {
				// A potion of instant health shows up in the health bar at once,
				// there is no slow animation like the regeneration has.
				shownHealth = health + absorption;
			}
		}
	}

	/**
	 * The absorption effect grants absorption points once, they are used up by
	 * the next damage instead of the health.
	 */
	public void addAbsorption(double amount) {
		double before = absorption;
		absorption = Math.max(absorption, amount);

		if (absorption != before) {
			// The golden hearts of the opponent appear at once, so the estimate
			// jumps to the new total as well.
			shownHealth = health + absorption;
		}
	}

	/**
	 * Adds food and saturation the way the vanilla food data does.
	 *
	 * <p>Food points are not healing on their own, they only allow the
	 * regeneration to work.
	 */
	public void eatFood(int nutrition, double saturation) {
		foodLevel = Math.min(MAX_FOOD, foodLevel + nutrition);
		saturationLevel = Math.min(saturation + saturationLevel, foodLevel);
	}

	/** Exhaustion of the opponent, it eats the saturation away. */
	public void addExhaustion(double amount) {
		exhaustionLevel = Math.min(exhaustionLevel + amount, 40.0D);
	}

	/**
	 * Mirrors {@code FoodData#tick}: the saturation regenerates fast, plain
	 * regeneration is slow and starvation hurts.
	 *
	 * <p>The saturation of the opponent is not sent to us, so it starts with the
	 * value a fresh player has and only changes when we see them eating or
	 * moving.
	 */
	public void tickFood() {
		if (exhaustionLevel > EXHAUSTION_DROP) {
			exhaustionLevel -= EXHAUSTION_DROP;

			if (saturationLevel > 0.0D) {
				saturationLevel = Math.max(0.0D, saturationLevel - 1.0D);
			} else {
				foodLevel = Math.max(0, foodLevel - 1);
			}
		}

		boolean hurt = isHurt();

		if (saturationLevel > 0.0D && foodLevel >= MAX_FOOD && hurt) {
			foodTickTimer++;

			if (foodTickTimer >= SATURATED_REGEN_TICKS) {
				double amount = Math.min(saturationLevel, SATURATION_PER_REGEN);
				addHealing(amount / 6.0D, false);
				addExhaustion(amount);
				foodTickTimer = 0;
			}
		} else if (foodLevel >= REGEN_FOOD_LEVEL && hurt) {
			foodTickTimer++;

			if (foodTickTimer >= REGEN_TICKS) {
				addHealing(1.0D, false);
				addExhaustion(6.0D);
				foodTickTimer = 0;
			}
		} else if (foodLevel <= 0) {
			foodTickTimer++;

			if (foodTickTimer >= REGEN_TICKS) {
				// Vanilla only starves a player that has more than one point of
				// health left.
				if (health > 1.0D) {
					health = Math.max(0.0D, health - 1.0D);
				}

				foodTickTimer = 0;
			}
		} else {
			foodTickTimer = 0;
		}
	}

	public void addEffect(TimedEffect effect) {
		for (int i = 0; i < effects.size(); i++) {
			TimedEffect existing = effects.get(i);

			if (existing.kind != effect.kind) {
				continue;
			}

			int amplifier = Math.max(existing.amplifier, effect.amplifier);
			int remaining = Math.max(existing.remaining, effect.remaining);
			int counter = amplifier == existing.amplifier ? existing.counter : 0;

			effects.set(i, new TimedEffect(effect.kind, effect.type, amplifier, remaining, counter));
			return;
		}

		effects.add(effect);
	}

	public void tickEffects() {
		for (TimedEffect effect : effects) {
			effect.remaining--;

			if (effect.kind == TimedEffect.Kind.REGENERATION) {
				effect.counter++;

				if (effect.counter >= effect.regenInterval()) {
					effect.counter = 0;
					addHealing(1.0D, false);
				}
			} else if (effect.kind == TimedEffect.Kind.POISON) {
				effect.counter++;

				if (effect.counter >= effect.poisonInterval()) {
					effect.counter = 0;

					// Vanilla never lets the poison take the last point of health.
					if (health > 1.0D) {
						health = Math.max(0.0D, health - 1.0D);
					}
				}
			}
		}

		effects.removeIf(effect -> effect.remaining <= 0);

		// The absorption points belong to the absorption effect. When the effect
		// runs out the golden hearts of the opponent are gone as well.
		if (absorption > 0.0D && !hasEffect(TimedEffect.Kind.ABSORPTION)) {
			absorption = 0.0D;
		}

		if (healingTicks > 0) {
			healingTicks--;
		}
	}

	/**
	 * True when the opponent is healing.
	 *
	 * <p>A potion of instant health heals at once, a golden apple gives the
	 * absorption and the regeneration. All of them are read from the actions of
	 * the opponent, the effects of a remote player are never sent.
	 */
	public boolean healing() {
		return healingTicks > 0
			|| hasEffect(TimedEffect.Kind.REGENERATION)
			|| hasEffect(TimedEffect.Kind.ABSORPTION);
	}

	/** True when the opponent has an effect of the given kind. */
	public boolean hasEffect(TimedEffect.Kind kind) {
		for (TimedEffect effect : effects) {
			if (effect.kind == kind) {
				return true;
			}
		}

		return false;
	}

	/** Removes one effect, a bottle of honey takes the poison away for example. */
	public void removeEffect(Holder<MobEffect> type) {
		effects.removeIf(effect -> effect.type != null && effect.type.value() == type.value());
	}

	/** True when the opponent is immune to fire and lava damage. */
	public boolean fireResistant() {
		return hasEffect(TimedEffect.Kind.FIRE_RESISTANCE);
	}

	/**
	 * Removes every effect of the opponent.
	 *
	 * <p>Milk and the totem of undying do that in vanilla.
	 */
	public void clearEffects() {
		effects.clear();
		absorption = 0.0D;
		shownHealth = health;
	}

	/**
	 * The totem of undying saved the opponent from a lethal hit.
	 *
	 * <p>Vanilla sets the health to one, clears every effect and grants
	 * regeneration two, absorption one and fire resistance.
	 */
	public void onTotem() {
		clearEffects();

		health = 1.0D;
		shownHealth = 1.0D;
		addEffect(new TimedEffect(TimedEffect.Kind.REGENERATION, MobEffects.REGENERATION, 1, 900));
		addEffect(new TimedEffect(TimedEffect.Kind.ABSORPTION, MobEffects.ABSORPTION, 1, 100));
		addEffect(new TimedEffect(TimedEffect.Kind.FIRE_RESISTANCE, MobEffects.FIRE_RESISTANCE, 0, 800));
		addAbsorption(ABSORPTION_PER_LEVEL * 2.0D);
		confidence = Math.min(1.0D, confidence + 0.1D);
	}

	/** Multiplier applied to incoming damage by any active resistance effect. */
	public float resistanceFactor() {
		int amplifier = -1;

		for (TimedEffect effect : effects) {
			if (effect.kind == TimedEffect.Kind.RESISTANCE) {
				amplifier = Math.max(amplifier, effect.amplifier);
			}
		}

		if (amplifier < 0) {
			return 1.0F;
		}

		return (25.0F - (amplifier + 1) * 5.0F) / 25.0F;
	}

	/**
	 * Attack damage bonus of an active Strength effect.
	 *
	 * <p>Vanilla applies effect modifiers on the server only, so the attribute
	 * value of another player never contains the bonus.
	 */
	public float strengthBonus() {
		int amplifier = -1;

		for (TimedEffect effect : effects) {
			if (effect.kind == TimedEffect.Kind.STRENGTH) {
				amplifier = Math.max(amplifier, effect.amplifier);
			}
		}

		if (amplifier < 0) {
			return 0.0F;
		}

		return DamageModel.STRENGTH_PER_LEVEL * (amplifier + 1);
	}

	/**
	 * Attack damage that an active Weakness effect takes away.
	 *
	 * <p>Like the Strength bonus it is a server side attribute modifier, so the
	 * attribute value of another player never contains it.
	 */
	public float weaknessPenalty() {
		int amplifier = -1;

		for (TimedEffect effect : effects) {
			if (effect.kind == TimedEffect.Kind.WEAKNESS) {
				amplifier = Math.max(amplifier, effect.amplifier);
			}
		}

		if (amplifier < 0) {
			return 0.0F;
		}

		return DamageModel.WEAKNESS_PER_LEVEL * (amplifier + 1);
	}

	/**
	 * A living player cannot have less than one point of health. If the estimate
	 * runs into the ground, the anchor is raised to the smallest value that is
	 * still consistent with the player being alive.
	 */
	public void ensurePositive() {
		if (!alive) {
			return;
		}

		if (health < 1.0D) {
			health = 1.0D;
		}
	}

	/**
	 * The opponent died. The health of a dead player is zero, the estimate
	 * starts from scratch when they respawn.
	 */
	public void onDeath() {
		alive = false;
		health = 0.0D;
		confidence = Math.min(1.0D, confidence + 0.25D);
	}
}
