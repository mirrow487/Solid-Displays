package com.soliddisplays.network;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.model.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: write this design onto the blueprint in the table at {@code pos}. */
public record SaveBlueprintPayload(BlockPos pos, ModelData model) implements CustomPacketPayload {
	public static final Type<SaveBlueprintPayload> TYPE = new Type<>(SolidDisplays.id("save_blueprint"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SaveBlueprintPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SaveBlueprintPayload::pos,
			ModelData.STREAM_CODEC, SaveBlueprintPayload::model,
			SaveBlueprintPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
