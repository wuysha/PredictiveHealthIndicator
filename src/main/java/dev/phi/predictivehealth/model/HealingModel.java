package dev.phi.predictivehealth.model;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns observable actions (eating, drinking, throwing a splash potion) into
 * healing estimates.
 *
 * <p>Everything is derived from the real item data of the consumed stack, so
 * golden apples, enchanted golden apples, suspicious stews, healing potions and
 * their splash variants are handled with their exact vanilla values.
 */
public final class HealingModel {
	private HealingModel() {
	}

	/**
	 * True when the stack heals in any way. A drawn bow or a shield is used as
	 * well, but it does not change the health of the opponent.
	 */
	public static boolean isConsumable(ItemStack stack) {
		return stack.get(DataComponents.FOOD) != null
			|| stack.get(DataComponents.CONSUMABLE) != null
			|| stack.get(DataComponents.POTION_CONTENTS) != null;
	}

	public static void applyConsumable(TargetState state, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);

		if (food != null) {
			// Food points are not healing, they enable the regeneration. The
			// saturation of a golden apple heals fast, exactly like in vanilla.
			state.eatFood(food.nutrition(), food.saturation());
		}

		Consumable consumable = stack.get(DataComponents.CONSUMABLE);

		if (consumable != null) {
			for (ConsumeEffect effect : consumable.onConsumeEffects()) {
				applyConsumeEffect(state, effect);
			}
		}

		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);

		if (potion != null) {
			for (MobEffectInstance instance : potion.getAllEffects()) {
				applyEffect(state, instance, 1.0F);
			}
		}
	}

	/** One of the effects a consumed item can have. */
	private static void applyConsumeEffect(TargetState state, ConsumeEffect effect) {
		if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
			// Some foods have a chance to give their effect, a raw chicken for
			// example. Vanilla rolls that chance every time, so it is rolled here
			// as well.
			if (apply.probability() < 1.0F && ThreadLocalRandom.current().nextFloat() >= apply.probability()) {
				return;
			}

			for (MobEffectInstance instance : apply.effects()) {
				applyEffect(state, instance, 1.0F);
			}
		} else if (effect instanceof ClearAllStatusEffectsConsumeEffect) {
			// A bucket of milk removes every effect, and with it the absorption.
			state.clearEffects();
		} else if (effect instanceof RemoveStatusEffectsConsumeEffect remove) {
			for (Holder<MobEffect> type : remove.effects()) {
				state.removeEffect(type);
			}
		}
	}

	public static void applyEffect(TargetState state, MobEffectInstance instance, float potency) {
		Holder<MobEffect> type = instance.getEffect();
		int amplifier = instance.getAmplifier();
		// A splash potion shortens the effects it carries, the closer the
		// player is to the impact the longer the effect lasts.
		int duration = (int) (potency * instance.getDuration() + 0.5D);

		if (is(type, MobEffects.INSTANT_HEALTH)) {
			// A potion of instant health heals four points per level at once, so
			// the second level restores four hearts in a single moment.
			state.addHealing((int) (potency * (4 << amplifier) + 0.5D), true);
		} else if (is(type, MobEffects.REGENERATION)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.REGENERATION, type, amplifier, duration));
		} else if (is(type, MobEffects.ABSORPTION)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.ABSORPTION, type, amplifier, duration));
			state.addAbsorption(TargetState.ABSORPTION_PER_LEVEL * (amplifier + 1));
		} else if (is(type, MobEffects.RESISTANCE)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.RESISTANCE, type, amplifier, duration));
		} else if (is(type, MobEffects.STRENGTH)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.STRENGTH, type, amplifier, duration));
		} else if (is(type, MobEffects.WEAKNESS)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.WEAKNESS, type, amplifier, duration));
		} else if (is(type, MobEffects.FIRE_RESISTANCE)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.FIRE_RESISTANCE, type, amplifier, duration));
		} else if (is(type, MobEffects.POISON)) {
			state.addEffect(new TimedEffect(TimedEffect.Kind.POISON, type, amplifier, duration));
		}
	}

	/**
	 * Registry entries do not define {@code equals}, so the underlying registry
	 * value is compared instead.
	 */
	private static boolean is(Holder<MobEffect> type, Holder<MobEffect> reference) {
		return reference != null && type != null && type.value() == reference.value();
	}
}
