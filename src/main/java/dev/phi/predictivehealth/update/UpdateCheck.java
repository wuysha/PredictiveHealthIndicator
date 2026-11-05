package dev.phi.predictivehealth.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phi.predictivehealth.PhiConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks GitHub once per launch whether a newer version of the mod is out.
 *
 * <p>The request goes to the public release API of the project, nothing about
 * the player is sent. When the answer holds a version that is newer than the
 * installed one, a dialog appears on the title screen and opens the release
 * page of the mod in the browser.
 */
public final class UpdateCheck {
	private static final Logger LOG = LoggerFactory.getLogger("predictivehealthindicator");

	private static final String MOD_ID = "predictivehealthindicator";
	private static final String REPO = "wuysha/PredictiveHealthIndicator";
	private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
	private static final String PROJECT = "https://github.com/" + REPO;

	/** The version of the installed mod, taken from the metadata of the mod. */
	public static final String VERSION = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.map(container -> container.getMetadata().getVersion().getFriendlyString())
		.orElse("0.0.0");

	private static boolean started;
	private static boolean shown;
	private static volatile String latest;
	private static volatile String url;

	private UpdateCheck() {
	}

	/** Starts the request, only the first call does anything. */
	public static void start() {
		if (started || !PhiConfig.get().updateCheck) {
			return;
		}

		started = true;
		fetch();
	}

	/** Shows the dialog with the new version, once the answer has arrived. */
	public static void offer(Screen titleScreen) {
		if (latest == null || shown) {
			return;
		}

		shown = true;
		UpdateUi.setScreen(new ConfirmLinkScreen(open -> {
			if (open) {
				UpdateUi.openUrl(url);
			}

			UpdateUi.setScreen(titleScreen);
		},
			Component.translatable("predictivehealthindicator.update.title", latest),
			Component.translatable("predictivehealthindicator.update.message", VERSION)
				.append("\n")
				.append(PROJECT),
			URI.create(url),
			Component.translatable("predictivehealthindicator.update.later"),
			true));
	}

	private static void fetch() {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API))
			.timeout(Duration.ofSeconds(5L))
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "PredictiveHealthIndicator/" + VERSION)
			.build();

		HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5L))
			.build()
			.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				if (response.statusCode() != 200) {
					LOG.debug("Update check: HTTP {}", response.statusCode());
					started = false;
					return;
				}

				JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
				String tag = release.get("tag_name").getAsString();
				if (Versions.newer(tag, VERSION)) {
					url = release.get("html_url").getAsString();
					latest = tag.replaceFirst("^[vV]", "");
				}
			})
			.exceptionally(e -> {
				LOG.debug("Update check failed", e);
				started = false;
				return null;
			});
	}
}
