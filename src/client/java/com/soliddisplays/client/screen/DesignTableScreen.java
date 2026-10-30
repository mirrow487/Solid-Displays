package com.soliddisplays.client.screen;

import com.soliddisplays.menu.DesignTableMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Stage 1 placeholder for the Design Table screen: shows the blueprint slot
 * and the player inventory. The full 3D designer replaces this in stage 3.
 * <p>
 * Drawn with flat fills so the mod needs no GUI texture.
 */
public class DesignTableScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<DesignTableMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int BORDER_DARK = 0xFF555555;
	private static final int SLOT_BG = 0xFF8B8B8B;

	public DesignTableScreen(DesignTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 132);
		this.inventoryLabelY = DesignTableMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, BORDER_DARK);
		for (Slot slot : menu.slots) {
			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		graphics.text(font, Component.translatable("gui.solid_displays.design_table.placeholder"),
				DesignTableMenu.SLOT_X + 24, DesignTableMenu.SLOT_Y + 4, 0xFF404040, false);
	}
}
