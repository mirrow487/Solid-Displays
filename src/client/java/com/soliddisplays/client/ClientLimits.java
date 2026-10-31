package com.soliddisplays.client;

import com.soliddisplays.config.SDConfig;
import com.soliddisplays.network.ServerLimitsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Uses the server's limits while connected (designer, tooltip and ghost all
 * read {@link SDConfig}); goes back to the local file after disconnecting.
 */
public final class ClientLimits {
	private ClientLimits() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ServerLimitsPayload.TYPE,
				(payload, context) -> payload.applyTo(SDConfig.get()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SDConfig.load());
	}
}
