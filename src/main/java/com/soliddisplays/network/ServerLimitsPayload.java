package com.soliddisplays.network;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.config.SDConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the server's limits and material formula. Sent on join and
 * after {@code /soliddisplays reload}, so the designer, tooltip and ghost use
 * the same numbers as the server that will check them.
 */
public record ServerLimitsPayload(int maxElements, float minElementSize, float maxElementSize, float maxModelRadius,
		double maxPlaceDistance, double volumeMultiplier, int minPerBlockType, boolean requireMaterials)
		implements CustomPacketPayload {
	public static final Type<ServerLimitsPayload> TYPE = new Type<>(SolidDisplays.id("server_limits"));
	public static final StreamCodec<ByteBuf, ServerLimitsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ServerLimitsPayload::maxElements,
			ByteBufCodecs.FLOAT, ServerLimitsPayload::minElementSize,
			ByteBufCodecs.FLOAT, ServerLimitsPayload::maxElementSize,
			ByteBufCodecs.FLOAT, ServerLimitsPayload::maxModelRadius,
			ByteBufCodecs.DOUBLE, ServerLimitsPayload::maxPlaceDistance,
			ByteBufCodecs.DOUBLE, ServerLimitsPayload::volumeMultiplier,
			ByteBufCodecs.VAR_INT, ServerLimitsPayload::minPerBlockType,
			ByteBufCodecs.BOOL, ServerLimitsPayload::requireMaterials,
			ServerLimitsPayload::new);

	public static ServerLimitsPayload of(SDConfig c) {
		return new ServerLimitsPayload(c.maxElements, c.minElementSize, c.maxElementSize, c.maxModelRadius,
				c.maxPlaceDistance, c.volumeMultiplier, c.minPerBlockType, c.requireMaterials);
	}

	/** Copies these values into a config object (the client's in-memory copy). */
	public void applyTo(SDConfig c) {
		c.maxElements = maxElements;
		c.minElementSize = minElementSize;
		c.maxElementSize = maxElementSize;
		c.maxModelRadius = maxModelRadius;
		c.maxPlaceDistance = maxPlaceDistance;
		c.volumeMultiplier = volumeMultiplier;
		c.minPerBlockType = minPerBlockType;
		c.requireMaterials = requireMaterials;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
