package org.examplee.wangsword.client.registry;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import org.examplee.wangsword.client.render.WangArrowEntityRenderer;
import org.examplee.wangsword.registry.EntityRegistry; // 去掉了后面的注释

public class ClientEntityRenderRegistry {

    public static void registerRenderers() {

        // 2. 注册深海风暴的渲染器
        EntityRendererRegistry.register(EntityRegistry.DEEP_SEA_STORM, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.WANG_ARROW, WangArrowEntityRenderer::new);
    }
}