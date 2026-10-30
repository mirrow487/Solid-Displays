package com.soliddisplays;

import com.soliddisplays.config.SDConfig;
import com.soliddisplays.registry.ModBlockEntities;
import com.soliddisplays.registry.ModBlocks;
import com.soliddisplays.registry.ModComponents;
import com.soliddisplays.registry.ModCreativeTab;
import com.soliddisplays.registry.ModItems;
import com.soliddisplays.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entry point. Everything that must exist on a
 * dedicated server is registered here; rendering and screens live in the
 * client source set.
 */
public class SolidDisplays implements ModInitializer {
	public static final String MOD_ID = "solid_displays";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SDConfig.load();

		// Order matters: components before items, blocks before block entities/items.
		ModComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModMenus.init();
		ModCreativeTab.init();

		LOGGER.info("Solid Displays initialised");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
