package org.examplee.wangsword.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/** 描边用的渲染层：线条格式 + 关深度测试（穿墙可见）。 */
public final class RelicOutlineLayer {

    public static final RenderLayer LAYER = RenderLayer.of("ws_relic_outline",
            VertexFormats.LINES,
            VertexFormat.DrawMode.LINES, 1536, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .build(false));

    private RelicOutlineLayer() {
    }
}