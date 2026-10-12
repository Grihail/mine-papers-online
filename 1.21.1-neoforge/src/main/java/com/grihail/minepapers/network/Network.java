package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Пакеты мода. Обработчики выполняются в главном потоке (сервера или клиента). */
public final class Network {

    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(PacketSaveFrame.TYPE, PacketSaveFrame.CODEC, PacketSaveFrame::handle);
        registrar.playToServer(PacketOfferFiles.TYPE, PacketOfferFiles.CODEC, PacketOfferFiles::handle);
        registrar.playToServer(PacketFileChunk.Upload.TYPE, PacketFileChunk.Upload.CODEC, PacketFileChunk.Upload::handle);
        registrar.playToServer(PacketHashes.Request.TYPE, PacketHashes.Request.CODEC, PacketHashes.Request::handle);
        registrar.playToServer(PacketBreakPicture.TYPE, PacketBreakPicture.CODEC, PacketBreakPicture::handle);
        registrar.playToClient(PacketHashes.Need.TYPE, PacketHashes.Need.CODEC,
            (msg, ctx) -> MinePapers.proxy.onFilesNeeded(msg.hashes()));
        registrar.playToClient(PacketFileChunk.TYPE, PacketFileChunk.CODEC,
            (msg, ctx) -> MinePapers.proxy.onFileChunk(msg.hash(), msg.total(), msg.offset(), msg.data()));
    }
}
