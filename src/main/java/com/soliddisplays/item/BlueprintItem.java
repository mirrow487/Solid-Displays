package com.soliddisplays.item;

import com.soliddisplays.model.ModelData;
import com.soliddisplays.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A filled blueprint. Its design lives in the {@link ModComponents#MODEL_DATA}
 * component. Placement and the material tooltip are added in later stages.
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
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		ModelData model = getModel(stack);
		if (model.isEmpty()) {
			tooltip.accept(Component.translatable("tooltip.solid_displays.blueprint.no_design").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.solid_displays.blueprint.parts", model.elements().size())
				.withStyle(ChatFormatting.GRAY));
	}
}
