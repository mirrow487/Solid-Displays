package com.soliddisplays.client.mixin;

import com.soliddisplays.client.blueprint.BlueprintClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift + mouse wheel turns a held blueprint instead of changing the hotbar
 * slot. Fabric has no in-world scroll event, hence this small mixin.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void solid_displays$rotateBlueprint(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (minecraft.gui.screen() != null || minecraft.player == null || !minecraft.player.isShiftKeyDown() || yOffset == 0) {
			return;
		}
		if (BlueprintClient.rotateHeld(minecraft, yOffset > 0 ? 1 : -1)) {
			ci.cancel();
		}
	}
}
