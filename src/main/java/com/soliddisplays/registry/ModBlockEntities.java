package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.block.DesignTableBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<DesignTableBlockEntity> DESIGN_TABLE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, SolidDisplays.id("design_table"),
			new BlockEntityType<>(DesignTableBlockEntity::new, Set.of(ModBlocks.DESIGN_TABLE)));

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
