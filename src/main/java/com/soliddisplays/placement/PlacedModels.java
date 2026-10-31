package com.soliddisplays.placement;

import com.mojang.serialization.Codec;
import com.soliddisplays.SolidDisplays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-dimension registry of placed models, saved with the world in
 * {@code data/solid_displays_models.dat}.
 * <p>
 * The entities themselves are saved by vanilla with their chunks; this file is
 * what ties them together and remembers the refund. An entity whose model is
 * no longer listed here is an orphan (its model was dismantled while the
 * entity's chunk was unloaded) and is deleted as soon as it loads.
 */
public class PlacedModels extends SavedData {
	private static final Codec<PlacedModels> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlacedModel.CODEC)
			.xmap(PlacedModels::new, p -> p.models);

	/**
	 * The data-fix type must not be null in 26.3 (the loader calls it
	 * unconditionally). Command storage has no fixers that touch our keys.
	 */
	public static final SavedDataType<PlacedModels> TYPE = new SavedDataType<>(
			SolidDisplays.id("models"), PlacedModels::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, PlacedModel> models;

	public PlacedModels() {
		this(new HashMap<>());
	}

	private PlacedModels(Map<UUID, PlacedModel> models) {
		this.models = new HashMap<>(models);
	}

	public static PlacedModels get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public PlacedModel get(UUID id) {
		return models.get(id);
	}

	public boolean contains(UUID id) {
		return models.containsKey(id);
	}

	public void add(PlacedModel model) {
		models.put(model.id(), model);
		setDirty();
	}

	public PlacedModel remove(UUID id) {
		PlacedModel removed = models.remove(id);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	public long countOwnedBy(UUID owner) {
		return models.values().stream().filter(m -> m.owner().equals(owner)).count();
	}

	public Collection<PlacedModel> all() {
		return models.values();
	}
}
