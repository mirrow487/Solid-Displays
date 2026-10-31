package com.soliddisplays.registry;

import com.mojang.serialization.Codec;
import com.soliddisplays.SolidDisplays;
import com.soliddisplays.model.ModelData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

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

	/**
	 * Rotation of a blueprint's model in 90° steps (0..3), chosen with R or
	 * Shift+scroll. Stored on the item so the ghost and the server agree.
	 */
	public static final DataComponentType<Integer> ROTATION = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, SolidDisplays.id("rotation"),
			new DataComponentType.Builder<Integer>()
					.persistent(Codec.intRange(0, 3))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.ignoreSwapAnimation()
					.build());

	private ModComponents() {
	}

	public static void init() {
		// Class loading triggers registration.
	}
}
