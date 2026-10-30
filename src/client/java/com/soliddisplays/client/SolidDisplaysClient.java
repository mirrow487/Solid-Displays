package com.soliddisplays.client;

import com.soliddisplays.client.screen.DesignTableScreen;
import com.soliddisplays.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client-only entry point: screens, renderers, key bindings. */
public class SolidDisplaysClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.DESIGN_TABLE, DesignTableScreen::new);
	}
}
