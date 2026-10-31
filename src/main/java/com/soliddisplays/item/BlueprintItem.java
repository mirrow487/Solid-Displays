package com.soliddisplays.item;

import com.soliddisplays.model.ModelData;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.placement.ModelPlacer;
import com.soliddisplays.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A filled blueprint. Its design lives in {@link ModComponents#MODEL_DATA},
 * its chosen rotation in {@link ModComponents#ROTATION}.
 * <p>
 * Right-clicking a block places the model there, paid for with materials
 * from the inventory. The blueprint itself is never used up. The material
 * list in the tooltip and the ghost preview are added on the client.
 */
public class BlueprintItem extends Item {
	public BlueprintItem(Properties properties) {
		super(properties);
	}

	public static ModelData getModel(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MODEL_DATA, ModelData.EMPTY);
	}

	@Override
	public Component getName(ItemStack stack) {
		ModelData model = getModel(stack);
		if (!model.name().isBlank()) {
			return Component.translatable("item.solid_displays.blueprint.named", model.name());
		}
		return super.getName(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return !getModel(stack).isEmpty();
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		if (!BlueprintPlacement.isFilledBlueprint(stack)) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS; // client: swing the arm, the server decides
		}
		BlockPos anchor = BlueprintPlacement.anchorFor(level, context.getClickedPos(), context.getClickedFace());
		ModelPlacer.PlaceResult result = ModelPlacer.placeWithMaterials(level, player, getModel(stack), anchor,
				BlueprintPlacement.rotation(stack));
		if (result.model().isEmpty()) {
			// Chat instead of the action bar: a missing-materials list can be long.
			player.sendSystemMessage(result.error().copy().withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		level.playSound(null, anchor, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		ModelData model = getModel(stack);
		if (model.isEmpty()) {
			tooltip.accept(Component.translatable("tooltip.solid_displays.blueprint.no_design").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.solid_displays.blueprint.parts", model.elements().size())
				.withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.solid_displays.blueprint.rotation", BlueprintPlacement.rotation(stack) * 90)
				.withStyle(ChatFormatting.GRAY));
	}
}
