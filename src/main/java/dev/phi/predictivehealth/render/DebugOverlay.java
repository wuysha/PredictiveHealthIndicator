package dev.phi.predictivehealth.render;

import dev.phi.predictivehealth.model.TargetState;
import dev.phi.predictivehealth.model.TargetTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Locale;

/**
 * Shows the numbers behind the prediction.
 *
 * <p>The estimate is a model of the vanilla mechanics, so a wrong result is
 * usually a wrong input. This overlay prints the inputs of the damage pipeline
 * of every tracked opponent.
 */
public final class DebugOverlay {
	private DebugOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Font font = Minecraft.getInstance().font;
		int y = 4;

		for (TargetState state : TargetTracker.tracked()) {
			String line = String.format(Locale.ROOT,
				"%s  hp %.1f/%.1f  abs %.1f  armor %.0f %.0f/%.0f  tough %.1f  prot %d blast %d exp %d  raw %.1f  loss %.1f  soaked %.1f  cd %.2f  food %d sat %.1f  scale %.2f  bp %s  sig %s  expl %.1f/%.1f  arm %s",
				state.name,
				state.effectiveHealth(), state.displayMaxHealth(), state.absorption,
				state.armor, state.armorAttribute, state.armorEquipment, state.toughness,
				state.protection, state.blastProtection, state.explosionProtection,
				state.lastRawDamage, state.lastDamageToHealth, state.lastAbsorbed,
				state.lastCooldown, state.food(), state.saturation(),
				state.damageScale,
				blastProtection(state), signal(state),
				state.explosionArmourDamage, state.explosionModelledDamage,
				TargetTracker.armor(state.entityId).describe()
			);

			graphics.text(font, Component.literal(line), 4, y, 0xFFFFFFFF);
			y += 10;
		}
	}

	/**
	 * The level of the blast protection that was measured from an explosion.
	 *
	 * <p>A level that is only a lower bound is marked, because the vanilla
	 * damage pipeline never uses more than twenty protection points.
	 */
	private static String blastProtection(TargetState state) {
		if (!state.blastProtectionMeasured) {
			return "-";
		}

		int level = state.blastProtectionLevel;

		if (level <= 0) {
			return "none";
		}

		return state.blastProtectionCapped ? ">=" + level : String.valueOf(level);
	}

	/**
	 * The blast protection the server shows in the modifiers of its attributes.
	 *
	 * <p>The servers that hide the enchantments rewrite the equipment packets,
	 * but the attributes keep the name of the enchantment and the slot it sits
	 * in, so the level is known even when the equipment says nothing.
	 */
	private static String signal(TargetState state) {
		if (state.blastSignal.isEmpty()) {
			return "-";
		}

		StringBuilder builder = new StringBuilder();

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			int level = state.blastSignal.level(slot);

			if (level <= 0) {
				continue;
			}

			if (builder.length() > 0) {
				builder.append(',');
			}

			builder.append(slot.getName()).append(level);
		}

		return builder.length() == 0 ? "-" : builder.toString();
	}
}
