package com.soliddisplays.client;

import com.soliddisplays.client.blueprint.BlueprintClient;
import com.soliddisplays.client.blueprint.GhostRenderer;
import com.soliddisplays.client.render.DesignViewRenderer;
import com.soliddisplays.client.screen.DesignTableScreen;
import com.soliddisplays.registry.ModEntities;
import com.soliddisplays.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client-only entry point: screens, renderers, key bindings. */
public class SolidDisplaysClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.DESIGN_TABLE, DesignTableScreen::new);
		// 3D viewport of the designer (drawn off-screen, then blitted into the GUI).
		PictureInPictureRendererRegistry.register(context -> new DesignViewRenderer(context.minecraft()));
		// Colliders are invisible; their box still shows with F3+B.
		EntityRendererRegistry.register(ModEntities.MODEL_COLLIDER, NoopRenderer::new);
		// Holding a blueprint: rotate key, material tooltip, ghost preview.
		BlueprintClient.init();
		GhostRenderer.init();
	}
}
