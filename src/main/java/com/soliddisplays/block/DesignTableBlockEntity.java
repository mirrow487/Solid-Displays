package com.soliddisplays.block;

import com.soliddisplays.menu.DesignTableMenu;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.registry.ModBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores the table's blueprint slot and the draft design being edited, so
 * closing the screen (or the server restarting) never loses work.
 */
public class DesignTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	/** Single slot that holds an empty or filled blueprint. */
	private final SimpleContainer blueprintSlot = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			DesignTableBlockEntity.this.setChanged();
		}
	};

	private ModelData draft = ModelData.EMPTY;

	public DesignTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DESIGN_TABLE, pos, state);
	}

	public SimpleContainer getBlueprintSlot() {
		return blueprintSlot;
	}

	public ModelData getDraft() {
		return draft;
	}

	public void setDraft(ModelData draft) {
		this.draft = draft;
		setChanged();
	}

	// ---- Persistence -----------------------------------------------------

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		blueprintSlot.setItem(0, input.read("blueprint", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		draft = input.read("draft", ModelData.CODEC).orElse(ModelData.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("blueprint", ItemStack.OPTIONAL_CODEC, blueprintSlot.getItem(0));
		output.store("draft", ModelData.CODEC, draft);
	}

	/** Drop the blueprint when the table is broken. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, blueprintSlot);
		}
	}

	// ---- Menu ------------------------------------------------------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.solid_displays.design_table");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new DesignTableMenu(syncId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}
}
