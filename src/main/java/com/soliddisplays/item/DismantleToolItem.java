package com.soliddisplays.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Right-click any part of a placed model (twice, to confirm) to dismantle the
 * whole model and get its materials back. Without the tool the same works
 * with Shift + right-click. The click itself is handled by the part entity.
 */
public class DismantleToolItem extends Item {
	public DismantleToolItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.solid_displays.dismantle_tool").withStyle(ChatFormatting.GRAY));
	}
}
