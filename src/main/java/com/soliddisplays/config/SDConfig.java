package com.soliddisplays.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.soliddisplays.SolidDisplays;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-authoritative settings, stored in {@code config/solid_displays.json}.
 * <p>
 * Every field has a default; missing fields in an older file are filled in and
 * the file is rewritten so new options show up for the admin.
 */
public final class SDConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("solid_displays.json");

	private static SDConfig instance = new SDConfig();

	// ---- Design limits -------------------------------------------------

	/** Maximum number of elements a single model may contain. */
	public int maxElements = 256;
	/** Largest allowed size of one element on any axis, in blocks. */
	public float maxElementSize = 16f;
	/** Smallest allowed size of one element on any axis, in blocks (1/16). */
	public float minElementSize = 0.0625f;
	/** Elements must stay inside a cube of this half-width around the origin, in blocks. */
	public float maxModelRadius = 16f;

	// ---- Placement -----------------------------------------------------

	/** Maximum distance (blocks) between a player and the spot they place a model at. */
	public double maxPlaceDistance = 8.0;
	/** Maximum number of placed models per player (0 = unlimited). */
	public int maxModelsPerPlayer = 0;
	/** If true, only the player who placed a model (or an operator) may dismantle it. */
	public boolean onlyOwnerCanDismantle = true;

	// ---- Material formula ----------------------------------------------
	// amount(block) = max(minPerBlockType, ceil(totalVolume(block) * volumeMultiplier))
	// Volumes are summed per block type before rounding, so many tiny parts of
	// the same block stay fair instead of each costing one full item.

	/** Items charged per cubic block of volume. */
	public double volumeMultiplier = 1.0;
	/** Minimum number of items charged for each distinct block type used. */
	public int minPerBlockType = 1;
	/** If false, models are free (useful for creative servers / testing). */
	public boolean requireMaterials = true;

	public static SDConfig get() {
		return instance;
	}

	/** Loads the config from disk (creating it with defaults if missing). */
	public static void load() {
		SDConfig loaded = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				loaded = GSON.fromJson(reader, SDConfig.class);
			} catch (Exception e) {
				SolidDisplays.LOGGER.error("Could not read {}, using defaults", FILE, e);
			}
		}
		instance = loaded != null ? loaded : new SDConfig();
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			SolidDisplays.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	/** Clamps obviously broken values so a typo cannot crash or exploit the server. */
	private void sanitize() {
		maxElements = Math.clamp(maxElements, 1, 4096);
		minElementSize = Math.clamp(minElementSize, 1f / 64f, 1f);
		maxElementSize = Math.clamp(maxElementSize, minElementSize, 64f);
		maxModelRadius = Math.clamp(maxModelRadius, 1f, 64f);
		maxPlaceDistance = Math.clamp(maxPlaceDistance, 1.0, 64.0);
		maxModelsPerPlayer = Math.max(0, maxModelsPerPlayer);
		volumeMultiplier = Math.clamp(volumeMultiplier, 0.0, 64.0);
		minPerBlockType = Math.clamp(minPerBlockType, 0, 64);
	}
}
