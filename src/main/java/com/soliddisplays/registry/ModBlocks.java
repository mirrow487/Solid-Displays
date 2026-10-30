package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.block.DesignTableBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block DESIGN_TABLE = register("design_table", DesignTableBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.instrument(NoteBlockInstrument.BASS)
					.strength(2.5f)
					.sound(SoundType.WOOD)
					.ignitedByLava());

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SolidDisplays.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
