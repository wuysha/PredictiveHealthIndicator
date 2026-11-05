package dev.phi.predictivehealth;

import dev.phi.predictivehealth.model.TargetTracker;
import dev.phi.predictivehealth.render.DebugOverlay;
import dev.phi.predictivehealth.render.HealthHologramRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;

public class PredictiveHealthIndicatorClient implements ClientModInitializer {
	/** The place of the debug output in the screen of the game. */
	private static final Identifier DEBUG_OVERLAY =
		Identifier.fromNamespaceAndPath("predictivehealthindicator", "debug_overlay");

	@Override
	public void onInitializeClient() {
		PhiConfig.get().load();
		TargetTracker.reset();

		ClientTickEvents.END_CLIENT_TICK.register(client -> TargetTracker.tick());

		// The text has to be queued before the game renders the entities, the
		// submit nodes are collected at the end of the feature collection.
		LevelRenderEvents.COLLECT_SUBMITS.register(HealthHologramRenderer::render);

		HudElementRegistry.addLast(DEBUG_OVERLAY, (graphics, deltaTracker) -> {
			if (PhiConfig.get().debugOverlay) {
				DebugOverlay.render(graphics);
			}
		});
	}
}
