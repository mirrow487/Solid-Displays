package com.soliddisplays.network;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.model.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: remember the work-in-progress design in the table (sent when the screen closes). */
public record UpdateDraftPayload(BlockPos pos, ModelData model) implements CustomPacketPayload {
	public static final Type<UpdateDraftPayload> TYPE = new Type<>(SolidDisplays.id("update_draft"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDraftPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, UpdateDraftPayload::pos,
			ModelData.STREAM_CODEC, UpdateDraftPayload::model,
			UpdateDraftPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
