package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			SolidDisplays.id("main"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.solid_displays"))
					.icon(() -> new ItemStack(ModItems.DESIGN_TABLE))
					.displayItems((params, output) -> {
						output.accept(ModItems.DESIGN_TABLE);
						output.accept(ModItems.EMPTY_BLUEPRINT);
						output.accept(ModItems.TEST_MODEL);
					})
					.build());

	private ModCreativeTab() {
	}

	public static void init() {
	}
}
