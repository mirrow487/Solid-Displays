package com.soliddisplays.model;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Which vanilla display entity renders an element.
 */
public enum ElementKind implements StringRepresentable {
	/** Rendered with a {@code minecraft:block_display}. */
	BLOCK("block"),
	/** Rendered with a {@code minecraft:item_display}. */
	ITEM("item");

	public static final Codec<ElementKind> CODEC = StringRepresentable.fromEnum(ElementKind::values);
	public static final StreamCodec<ByteBuf, ElementKind> STREAM_CODEC =
			ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);

	private final String serializedName;

	ElementKind(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
