package com.soliddisplays.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * Bookkeeping for one model in the world.
 *
 * @param id       shared id written onto every entity of the model
 * @param owner    player who placed it (only they, or an operator, may dismantle it)
 * @param name     model name, for messages
 * @param anchor   anchor block position
 * @param entities every display and collider entity belonging to the model
 * @param refund   items returned when the model is dismantled
 */
public record PlacedModel(UUID id, UUID owner, String name, BlockPos anchor, List<UUID> entities,
		List<ItemStack> refund) {

	public static final Codec<PlacedModel> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(PlacedModel::id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(PlacedModel::owner),
			Codec.STRING.optionalFieldOf("name", "").forGetter(PlacedModel::name),
			BlockPos.CODEC.fieldOf("anchor").forGetter(PlacedModel::anchor),
			UUIDUtil.CODEC.listOf().fieldOf("entities").forGetter(PlacedModel::entities),
			ItemStack.CODEC.listOf().optionalFieldOf("refund", List.of()).forGetter(PlacedModel::refund)
	).apply(i, PlacedModel::new));

	public PlacedModel {
		entities = List.copyOf(entities);
		refund = refund.stream().map(ItemStack::copy).toList();
	}
}
