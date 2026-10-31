package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.item.BlueprintItem;
import com.soliddisplays.item.DismantleToolItem;
import com.soliddisplays.item.TestModelItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	public static final Item DESIGN_TABLE = registerBlockItem(ModBlocks.DESIGN_TABLE);

	/** Blank paper that a Design Table can write a design onto. */
	public static final Item EMPTY_BLUEPRINT = register("empty_blueprint", Item::new, new Item.Properties());

	/** Carries a {@link ModComponents#MODEL_DATA} design; reusable, so it does not stack. */
	public static final Item BLUEPRINT = register("blueprint", BlueprintItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	/** Right-click a model part to dismantle the model. */
	public static final Item DISMANTLE_TOOL = register("dismantle_tool", DismantleToolItem::new,
			new Item.Properties().stacksTo(1));

	/**
	 * Debug item that places a fixed test model (no recipe, not in the creative
	 * tab; get it with /give @s solid_displays:test_model).
	 */
	public static final Item TEST_MODEL = register("test_model", TestModelItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SolidDisplays.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBlockItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	public static void init() {
	}
}
