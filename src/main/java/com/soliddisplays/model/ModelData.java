package com.soliddisplays.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A complete design: a name plus its elements. Stored on filled blueprints
 * (as a data component) and as the in-progress draft of a Design Table.
 * <p>
 * The stream codec caps list and string lengths so a malicious client cannot
 * make the server allocate huge buffers; the real, configurable limits are
 * enforced separately by the server-side validator.
 */
public record ModelData(String name, List<ModelElement> elements) {
	/** Hard network caps (independent of the config, which is checked later). */
	public static final int MAX_NAME_LENGTH = 64;
	public static final int MAX_ELEMENTS_NETWORK = 4096;

	public static final ModelData EMPTY = new ModelData("", List.of());

	public static final Codec<ModelData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.string(0, MAX_NAME_LENGTH).optionalFieldOf("name", "").forGetter(ModelData::name),
			ModelElement.CODEC.sizeLimitedListOf(MAX_ELEMENTS_NETWORK).optionalFieldOf("elements", List.of()).forGetter(ModelData::elements)
	).apply(i, ModelData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ModelData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), ModelData::name,
			ModelElement.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ELEMENTS_NETWORK)), ModelData::elements,
			ModelData::new);

	public ModelData {
		elements = List.copyOf(elements);
	}

	public boolean isEmpty() {
		return elements.isEmpty();
	}

	public ModelData withName(String newName) {
		return new ModelData(newName, elements);
	}

	public ModelData withElements(List<ModelElement> newElements) {
		return new ModelData(name, newElements);
	}
}
