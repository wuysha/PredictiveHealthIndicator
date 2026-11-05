package dev.phi.predictivehealth.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.phi.predictivehealth.PhiConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen of the mod.
 *
 * <p>It is built with the config library of Mod Menu, so it looks like the
 * screens of the other mods that use it. Every option has a name and a short
 * description of what it does.
 */
public final class PhiConfigScreen {
	private PhiConfigScreen() {
	}

	/** Builds the settings screen on top of the screen that opened it. */
	public static Screen build(Screen parent) {
		PhiConfig config = PhiConfig.get();

		return YetAnotherConfigLib.createBuilder()
			.title(Component.translatable("predictivehealthindicator.config.title"))
			.category(ConfigCategory.createBuilder()
				.name(Component.translatable("predictivehealthindicator.config.category.general"))
				.tooltip(Component.translatable("predictivehealthindicator.config.category.general.tooltip"))
				.option(tickBox()
					.name(Component.translatable("predictivehealthindicator.config.enabled.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.enabled.description")))
					.binding(PhiConfig.DEFAULT_ENABLED, () -> config.enabled, value -> config.enabled = value)
					.controller(TickBoxControllerBuilder::create)
					.build())
				.option(tickBox()
					.name(Component.translatable("predictivehealthindicator.config.through_walls.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.through_walls.description")))
					.binding(PhiConfig.DEFAULT_THROUGH_WALLS, () -> config.showThroughWalls,
						value -> config.showThroughWalls = value)
					.controller(TickBoxControllerBuilder::create)
					.build())
				.option(tickBox()
					.name(Component.translatable("predictivehealthindicator.config.estimate_unknown.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.estimate_unknown.description")))
					.binding(PhiConfig.DEFAULT_ESTIMATE_UNKNOWN, () -> config.estimateUnknownDamage,
						value -> config.estimateUnknownDamage = value)
					.controller(TickBoxControllerBuilder::create)
					.build())
				.option(tickBox()
					.name(Component.translatable("predictivehealthindicator.config.calibrate.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.calibrate.description")))
					.binding(PhiConfig.DEFAULT_CALIBRATE, () -> config.autoCalibrate,
						value -> config.autoCalibrate = value)
					.controller(TickBoxControllerBuilder::create)
					.build())
				.option(tickBox()
					.name(Component.translatable("predictivehealthindicator.config.debug.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.debug.description")))
					.binding(PhiConfig.DEFAULT_DEBUG, () -> config.debugOverlay,
						value -> config.debugOverlay = value)
					.controller(TickBoxControllerBuilder::create)
					.build())
				.option(integer()
					.name(Component.translatable("predictivehealthindicator.config.range.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.range.description")))
					.binding(PhiConfig.DEFAULT_RANGE, () -> config.maxRange, value -> config.maxRange = value)
					.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range(8, 128)
						.step(1))
					.build())
				.option(floater()
					.name(Component.translatable("predictivehealthindicator.config.scale.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.scale.description")))
					.binding(PhiConfig.DEFAULT_TEXT_SCALE, () -> config.textScale,
						value -> config.textScale = value)
					.controller(option -> FloatSliderControllerBuilder.create(option)
						.range(0.5F, 3.0F)
						.step(0.1F))
					.build())
				.option(doubler()
					.name(Component.translatable("predictivehealthindicator.config.offset_x.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.offset_x.description")))
					.binding(PhiConfig.DEFAULT_OFFSET, () -> config.offsetX, value -> config.offsetX = value)
					.controller(option -> DoubleSliderControllerBuilder.create(option)
						.range(-2.0D, 2.0D)
						.step(0.1D))
					.build())
				.option(doubler()
					.name(Component.translatable("predictivehealthindicator.config.offset_y.name"))
					.description(OptionDescription.of(
						Component.translatable("predictivehealthindicator.config.offset_y.description")))
					.binding(PhiConfig.DEFAULT_OFFSET, () -> config.offsetY, value -> config.offsetY = value)
					.controller(option -> DoubleSliderControllerBuilder.create(option)
						.range(-2.0D, 2.0D)
						.step(0.1D))
					.build())
				.build())
			.save(config::save)
			.build()
			.generateScreen(parent);
	}

	/**
	 * The builders of the library need a known type, otherwise the type of the
	 * value is lost between the steps of the chain.
	 */
	private static Option.Builder<Boolean> tickBox() {
		return Option.createBuilder();
	}

	private static Option.Builder<Integer> integer() {
		return Option.createBuilder();
	}

	private static Option.Builder<Float> floater() {
		return Option.createBuilder();
	}

	private static Option.Builder<Double> doubler() {
		return Option.createBuilder();
	}
}
