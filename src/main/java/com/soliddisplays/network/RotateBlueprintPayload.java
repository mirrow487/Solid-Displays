package com.soliddisplays.network;

import com.soliddisplays.SolidDisplays;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: turn the blueprint in the main hand by {@code steps} × 90°. */
public record RotateBlueprintPayload(int steps) implements CustomPacketPayload {
	public static final Type<RotateBlueprintPayload> TYPE = new Type<>(SolidDisplays.id("rotate_blueprint"));
	public static final StreamCodec<ByteBuf, RotateBlueprintPayload> CODEC =
			ByteBufCodecs.VAR_INT.map(RotateBlueprintPayload::new, RotateBlueprintPayload::steps);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
