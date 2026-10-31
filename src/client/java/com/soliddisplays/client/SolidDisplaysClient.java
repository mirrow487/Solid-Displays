package com.soliddisplays.client;

import com.soliddisplays.client.screen.DesignTableScreen;
import com.soliddisplays.registry.ModEntities;
import com.soliddisplays.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client-only entry point: screens, renderers, key bindings. */
public class SolidDisplaysClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.DESIGN_TABLE, DesignTableScreen::new);
		// Colliders are invisible; their box still shows with F3+B.
		EntityRendererRegistry.register(ModEntities.MODEL_COLLIDER, NoopRenderer::new);
	}
}
