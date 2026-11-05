package dev.phi.predictivehealth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny JSON backed configuration. Everything the user can tweak lives here.
 */
public final class PhiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("predictivehealthindicator.json");
	private static final PhiConfig INSTANCE = new PhiConfig();

	public static final boolean DEFAULT_ENABLED = true;
	public static final boolean DEFAULT_THROUGH_WALLS = false;
	public static final boolean DEFAULT_ESTIMATE_UNKNOWN = true;
	public static final boolean DEFAULT_CALIBRATE = true;
	public static final boolean DEFAULT_DEBUG = false;
	public static final boolean DEFAULT_UPDATE_CHECK = true;
	public static final int DEFAULT_RANGE = 48;
	public static final float DEFAULT_TEXT_SCALE = 1.0F;
	public static final double DEFAULT_OFFSET = 0.0D;

	public boolean enabled = DEFAULT_ENABLED;
	public boolean showThroughWalls = DEFAULT_THROUGH_WALLS;
	public boolean estimateUnknownDamage = DEFAULT_ESTIMATE_UNKNOWN;
	/** Corrects the damage of the model from the death of the opponent. */
	public boolean autoCalibrate = DEFAULT_CALIBRATE;
	/** Shows the numbers behind the prediction, they help to find wrong inputs. */
	public boolean debugOverlay = DEFAULT_DEBUG;
	/** Asks GitHub once per launch whether a newer version of the mod is out. */
	public boolean updateCheck = DEFAULT_UPDATE_CHECK;
	/** Distance in blocks up to which the opponents are shown. */
	public int maxRange = DEFAULT_RANGE;

	/** Size of the text above the head, one is the size of a vanilla name tag. */
	public float textScale = DEFAULT_TEXT_SCALE;

	/** Horizontal shift of the text, in blocks. */
	public double offsetX = DEFAULT_OFFSET;
	/** Vertical shift of the text, in blocks. */
	public double offsetY = DEFAULT_OFFSET;

	private PhiConfig() {
	}

	public static PhiConfig get() {
		return INSTANCE;
	}

	public void load() {
		if (!Files.exists(FILE)) {
			save();
			return;
		}

		try {
			String json = Files.readString(FILE, StandardCharsets.UTF_8);
			PhiConfig parsed = GSON.fromJson(json, PhiConfig.class);
			if (parsed != null) {
				enabled = parsed.enabled;
				showThroughWalls = parsed.showThroughWalls;
				estimateUnknownDamage = parsed.estimateUnknownDamage;
				autoCalibrate = parsed.autoCalibrate;
				debugOverlay = parsed.debugOverlay;
				updateCheck = parsed.updateCheck;
				maxRange = parsed.maxRange;
				textScale = parsed.textScale;
				offsetX = parsed.offsetX;
				offsetY = parsed.offsetY;
			}
		} catch (IOException | RuntimeException e) {
			save();
		}
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// A broken config must never break the game.
		}
	}
}
