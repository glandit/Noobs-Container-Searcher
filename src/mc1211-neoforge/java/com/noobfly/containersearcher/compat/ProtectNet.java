package com.noobfly.containersearcher.compat;

import com.noobfly.containersearcher.ProtectClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Consumer;

// NeoForge glue for the chest sharing plugin channel. The payload is optional, so servers without
// the plugin (or without NeoForge) are fine; Paper announces the channel with minecraft:register.
public final class ProtectNet {
	private record Payload(String json) implements CustomPacketPayload {
		private static final Type<Payload> TYPE = new Type<>(ResourceLocation.parse(ProtectClient.CHANNEL));
		private static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.stringUtf8(1 << 16).map(Payload::new, Payload::json);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static boolean ready;

	private ProtectNet() {
	}

	public static void register(Consumer<String> onReceive, Runnable onReady, Runnable onDisconnect) {
		Platform.modBus().addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").optional()
			// this NeoForge version has one handler for both directions; as a client mod only replies arrive here
			.playBidirectional(Payload.TYPE, Payload.CODEC, (payload, context) -> onReceive.accept(payload.json())));
		// there is no "server registered a channel" event, so look once per tick until the server announced it
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (!ready && connection != null && connection.hasChannel(Payload.TYPE)) {
				ready = true;
				onReady.run();
			}
		});
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
			ready = false;
			Minecraft.getInstance().execute(onDisconnect);
		});
	}

	public static void send(String json) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null && connection.hasChannel(Payload.TYPE)) {
			connection.send(new ServerboundCustomPayloadPacket(new Payload(json)));
		}
	}
}
