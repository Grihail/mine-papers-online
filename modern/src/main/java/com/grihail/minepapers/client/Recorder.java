package com.grihail.minepapers.client;

//? if >=26.1 {
/*import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/^* С 26.1 сущности и предметы не пишут в буферы сами, а отдают геометрию в SubmitNodeCollector, и нарисует её игра позже. Общий код рисует по-старому, вершина за вершиной: Recorder запоминает их по типам отрисовки и отдаёт каждому типу одним куском. Вершины уже в координатах той позы, с которой их писали, поэтому отдаются как есть. *^/
final class Recorder implements VertexSink {

    private final Map<RenderType, Recording> byType = new LinkedHashMap<RenderType, Recording>();

    @Override
    public VertexConsumer buffer(RenderType type) {
        return byType.computeIfAbsent(type, t -> new Recording());
    }

    /^* Отдать записанное: poseStack - та же поза, с которой писали (игра берёт из неё только порядок и камеру). *^/
    void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        for (Map.Entry<RenderType, Recording> e : byType.entrySet()) {
            Recording r = e.getValue();
            if (r.count > 0) collector.submitCustomGeometry(poseStack, e.getKey(), (pose, out) -> r.replay(out));
        }
    }

    /^* Вершины одного типа: по STRIDE чисел на вершину, целые (цвет, наложение, свет) - битами float. *^/
    private static final class Recording implements VertexConsumer {

        private static final int X = 0, Y = 1, Z = 2, COLOR = 3, U = 4, V = 5, OVERLAY = 6, LIGHT = 7, NX = 8, NY = 9, NZ = 10, WIDTH = 11,
            U3 = 12, V3 = 13;
        private static final int STRIDE = 14;
        private static final int WHITE = -1, FULL_BRIGHT = 0xF000F0;

        private float[] data = new float[STRIDE * 64];
        private int count;
        private int at = -STRIDE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            at = count++ * STRIDE;
            data[at + X] = x;
            data[at + Y] = y;
            data[at + Z] = z;
            data[at + COLOR] = Float.intBitsToFloat(WHITE);
            data[at + U] = 0;
            data[at + V] = 0;
            data[at + OVERLAY] = Float.intBitsToFloat(0);
            data[at + LIGHT] = Float.intBitsToFloat(FULL_BRIGHT);
            data[at + NX] = 0;
            data[at + NY] = 1;
            data[at + NZ] = 0;
            data[at + WIDTH] = 1;
            data[at + U3] = 0;
            data[at + V3] = 0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            data[at + COLOR] = Float.intBitsToFloat(argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[at + U] = u;
            data[at + V] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[at + OVERLAY] = Float.intBitsToFloat(u & 0xFFFF | v << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[at + LIGHT] = Float.intBitsToFloat(u & 0xFFFF | v << 16);
            return this;
        }

        //? if >=26.3 {
        /^@Override
        public VertexConsumer setUv3(float u, float v) {
            data[at + U3] = u;
            data[at + V3] = v;
            return this;
        }
        ^///?}

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[at + NX] = x;
            data[at + NY] = y;
            data[at + NZ] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            data[at + WIDTH] = width;
            return this;
        }

        /^* Записанные вершины - в настоящий буфер; чего нет в его формате, он пропустит сам. *^/
        void replay(VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                int p = i * STRIDE;
                int overlay = Float.floatToRawIntBits(data[p + OVERLAY]), light = Float.floatToRawIntBits(data[p + LIGHT]);
                VertexConsumer v = out.addVertex(data[p + X], data[p + Y], data[p + Z])
                    .setColor(Float.floatToRawIntBits(data[p + COLOR]))
                    .setUv(data[p + U], data[p + V])
                    .setUv1(overlay & 0xFFFF, overlay >>> 16)
                    .setUv2(light & 0xFFFF, light >>> 16)
                    .setNormal(data[p + NX], data[p + NY], data[p + NZ])
                    .setLineWidth(data[p + WIDTH]);
                //? if >=26.3 {
                /^v.setUv3(data[p + U3], data[p + V3]);
                ^///?}
            }
        }
    }
}
*///?}
