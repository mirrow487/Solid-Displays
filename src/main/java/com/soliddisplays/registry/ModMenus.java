package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.menu.DesignTableMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
	/** Opening data: table position and its saved draft. */
	public static final ExtendedMenuType<DesignTableMenu, DesignTableMenu.OpenData> DESIGN_TABLE = Registry.register(
			BuiltInRegistries.MENU, SolidDisplays.id("design_table"),
			new ExtendedMenuType<>(DesignTableMenu::new, DesignTableMenu.OpenData.STREAM_CODEC));

	private ModMenus() {
	}

	public static void init() {
	}
}
