package com.grihail.minepapers.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

//? if >=26.1 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}

/** Куда картина и иконка пишут вершины: буфер для каждого типа отрисовки. До 26.1 это сам MultiBufferSource, с 26.1 - Recorder (вершины уходят в SubmitNodeCollector) или буфер подсветки блока. */
public interface VertexSink {

    VertexConsumer buffer(RenderType type);
}
