package org.examplee.wangsword.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import org.examplee.wangsword.registry.EntityRegistry;
// 👇 导入你独立的类
import org.examplee.wangsword.client.registry.ClientEntityRenderRegistry;

public class EntityRenderers implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("====== 客户端初始化成功！ ======");

        // 👇 调用独立的深海风暴注册类
        ClientEntityRenderRegistry.registerRenderers();
    }
}