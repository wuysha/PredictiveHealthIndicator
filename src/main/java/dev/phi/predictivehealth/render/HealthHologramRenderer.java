package dev.phi.predictivehealth.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.phi.predictivehealth.PhiConfig;
import dev.phi.predictivehealth.model.EnchantmentReader;
import dev.phi.predictivehealth.model.TargetState;
import dev.phi.predictivehealth.model.TargetTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

import java.util.Locale;

/**
 * Draws the estimated health of every opponent above their head.
 *
 * <p>The text is a hologram that always faces the camera. It shows the health
 * the opponent can still lose, so the absorption of a golden apple is counted
 * and the maximum rises from twenty to twenty four.
 *
 * <p>The text goes through the submit node collector of the game, the same way
 * the vanilla name tags are drawn. That keeps the billboard, the depth test and
 * the layers of the text exactly as they are in vanilla.
 */
public final class HealthHologramRenderer {
	/** Colour of the digits of the health and of the hint above it. */
	private static final int COLOUR_WHITE = 0xFFFFFFFF;
	/** Colour of the heart while the opponent is not healing. */
	private static final int COLOUR_HEART = 0xDD2B2B;
	/** Colour of the heart while the opponent drinks a potion or eats an apple. */
	private static final int COLOUR_HEART_HEALED = 0xFFCF00;

	/** Distance of the hologram above the name tag of the player. */
	private static final double NAME_OFFSET = 0.75D;
	/** The size the vanilla name tags use, one text pixel is this many blocks. */
	private static final float VANILLA_SCALE = 0.025F;
	/** Height of one text line in pixels, the same the vanilla font uses. */
	private static final float LINE_HEIGHT = 9.0F;

	private HealthHologramRenderer() {
	}

	public static void render(LevelRenderContext context) {
		PhiConfig config = PhiConfig.get();

		if (!config.enabled) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			return;
		}

		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		CameraRenderState camera = context.levelState().cameraRenderState;

		if (collector == null || poseStack == null || camera == null) {
			return;
		}

		Font font = client.font;
		float delta = client.getDeltaTracker().getGameTimeDeltaTicks();
		double partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Font.DisplayMode mode = config.showThroughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		double rangeSq = (double) config.maxRange * config.maxRange;
		float scale = VANILLA_SCALE * config.textScale;
		double camX = camera.pos.x;
		double camY = camera.pos.y;
		double camZ = camera.pos.z;

		for (TargetState state : TargetTracker.tracked()) {
			if (!state.alive) {
				continue;
			}

			Entity entity = client.level.getEntity(state.entityId);

			if (!(entity instanceof Player player)) {
				continue;
			}

			double px = Mth.lerp(partial, player.xo, player.getX());
			double py = Mth.lerp(partial, player.yo, player.getY());
			double pz = Mth.lerp(partial, player.zo, player.getZ());

			double dx = px - camX;
			double dy = py - camY;
			double dz = pz - camZ;

			if (dx * dx + dz * dz > rangeSq) {
				continue;
			}

			int light = light(player, mode);

			poseStack.pushPose();
			poseStack.translate(dx + config.offsetX, dy + player.getBbHeight() + NAME_OFFSET + config.offsetY, dz);
			poseStack.rotate(camera.orientation);
			poseStack.scale(scale, -scale, scale);

			submitBlast(collector, font, state, poseStack, mode, light);

			Component text = label(state, delta);
			float width = font.width(text.getVisualOrderText());

			collector.submitText(poseStack, -width / 2.0F, 0.0F, text.getVisualOrderText(), false, mode,
				light, COLOUR_WHITE, 0, 0);

			poseStack.popPose();
		}
	}

	/**
	 * The blast protection of the leggings, shown above the health.
	 *
	 * <p>The check is the one of the blast protection mod: the blast protection
	 * adds a modifier to an attribute of the holder, and the id of that modifier
	 * names the enchantment and the slot it sits in. The servers that hide the
	 * enchantments rewrite the equipment packets, but they leave the attributes
	 * alone, so the enchantment is known even when the equipment says nothing
	 * about it.
	 */
	private static void submitBlast(SubmitNodeCollector collector, Font font,
		TargetState state, PoseStack poseStack, Font.DisplayMode mode, int light) {
		int level = state.blastSignal.level(EquipmentSlot.LEGS);

		if (level <= 0) {
			// The server shows no blast protection on the leggings.
			return;
		}

		Component hint = Component.translatable("predictivehealthindicator.indicator.leggings",
			EnchantmentReader.blastProtectionName(level));
		float width = font.width(hint.getVisualOrderText());

		collector.submitText(poseStack, -width / 2.0F, -LINE_HEIGHT, hint.getVisualOrderText(), false, mode,
			light, COLOUR_WHITE, 0, 0);
	}

	/**
	 * The text above the head of the opponent.
	 *
	 * <p>The digits are white and the heart behind them is red. The heart turns
	 * gold while the opponent is healing, that is while they drink a potion of
	 * instant health or eat a golden apple.
	 */
	private static Component label(TargetState state, float delta) {
		// The estimate follows the prediction, so a hit shows up at once and the
		// healing is a bit slower.
		double health = state.smoothHealth(state.effectiveHealth(), delta);

		MutableComponent text = Component.literal(health(health));
		text.append(Component.literal(" ❤").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(heartColour(state)))));

		return text;
	}

	/** Red while the opponent is not healing, gold while it is. */
	private static int heartColour(TargetState state) {
		return state.healing() ? COLOUR_HEART_HEALED : COLOUR_HEART;
	}

	/** The light of the place the opponent stands in. */
	private static int light(Player player, Font.DisplayMode mode) {
		BlockPos pos = player.blockPosition();
		int block = player.level().getBrightness(LightLayer.BLOCK, pos);
		int sky = player.level().getBrightness(LightLayer.SKY, pos);
		int coords = LightCoordsUtil.pack(block, sky);

		// The vanilla name tags glow a little in the dark, the see through
		// variant keeps the real light of the place.
		return mode == Font.DisplayMode.NORMAL ? LightCoordsUtil.lightCoordsWithEmission(coords, 2) : coords;
	}

	/**
	 * The health, rounded to halves.
	 *
	 * <p>An exact number like 2.4 says more than the estimate can know, so the
	 * value is rounded to the nearest half.
	 */
	private static String health(double value) {
		double rounded = Math.round(value * 2.0D) / 2.0D;
		String text = String.format(Locale.ROOT, "%.1f", rounded);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}
}
