package com.soliddisplays.command;

import com.soliddisplays.config.SDConfig;
import com.soliddisplays.network.ModNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /soliddisplays reload}: re-reads the config file (operators only). */
public final class ModCommands {
	private ModCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
				Commands.literal("soliddisplays")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("reload").executes(ctx -> {
							SDConfig.load();
							ModNetworking.sendLimitsToAll(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.translatable("message.solid_displays.reloaded"), true);
							return 1;
						}))));
	}
}
