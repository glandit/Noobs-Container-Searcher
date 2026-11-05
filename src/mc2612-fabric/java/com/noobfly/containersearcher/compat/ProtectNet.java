package com.noobfly.containersearcher.compat;

import com.noobfly.containersearcher.ProtectClient;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

// Fabric glue for the chest sharing plugin channel: one payload type, a JSON string, both directions
public final class ProtectNet {
	private record Payload(String json) implements CustomPacketPayload {
		private static final Type<Payload> TYPE = new Type<>(Identifier.parse(ProtectClient.CHANNEL));
		private static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.stringUtf8(1 << 16).map(Payload::new, Payload::json);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ProtectNet() {
	}

	public static void register(Consumer<String> onReceive, Runnable onReady, Runnable onDisconnect) {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> onReceive.accept(payload.json()));
		// Paper announces the plugin's channels right after joining
		ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
			if (channels.contains(Payload.TYPE.id())) {
				client.execute(onReady);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(Payload.TYPE)) {
				// Fabric announces our receivers right after JOIN; saying hello later makes sure
				// Paper already knows we listen on the channel when it answers
				client.execute(onReady);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(onDisconnect));
	}

	public static void send(String json) {
		if (ClientPlayNetworking.canSend(Payload.TYPE)) {
			ClientPlayNetworking.send(new Payload(json));
		}
	}
}
