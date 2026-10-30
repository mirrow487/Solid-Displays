package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.model.ModelData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Custom item data components. */
public final class ModComponents {
	/** The design stored on a filled blueprint. */
	public static final DataComponentType<ModelData> MODEL_DATA = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, SolidDisplays.id("model_data"),
			new DataComponentType.Builder<ModelData>()
					.persistent(ModelData.CODEC)
					.networkSynchronized(ModelData.STREAM_CODEC)
					.cacheEncoding()
					.build());

	private ModComponents() {
	}

	public static void init() {
		// Class loading triggers registration.
	}
}
