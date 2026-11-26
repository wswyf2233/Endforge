package org.examplee.wangsword.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.client.render.WangArrowEntityRenderer;
import org.examplee.wangsword.item.unique.WangBowItem;
import org.examplee.wangsword.registry.BowRegistery;
import org.examplee.wangsword.registry.EntityRegistry;

@Environment(EnvType.CLIENT)
public class BowClientInitializer implements ClientModInitializer {

    /** 右键蓄力的满格 tick 数（3 秒） */
    private static final int PULL_TICKS = 60;

    /** 客户端自己数的蓄力 tick：不依赖物品计时，条的时间一定准 */
    private static int pullTicks = 0;

    @Override
    public void onInitializeClient() {

        // 每 tick 推进蓄力计时，松手/换手立刻归零
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerEntity p = client.player;
            if (p != null && p.isUsingItem()
                    && p.getActiveItem().getItem() instanceof WangBowItem
                    && !WangBowItem.CLIENT_ULT_CHARGING) {
                pullTicks++;
            } else {
                pullTicks = 0;
            }
        });

        // 1. 注册实体渲染器（直接使用原版模型层，不需要额外注册）
        EntityRendererRegistry.register(EntityRegistry.WANG_ARROW, WangArrowEntityRenderer::new);

        // 2. 注册拉弓进度谓词
        ModelPredicateProviderRegistry.register(
                BowRegistery.WS_BOW,
                new Identifier("pull"),
                (itemStack, clientWorld, livingEntity, seed) -> {
                    if (livingEntity == null) {
                        return 0.0F;
                    }
                    // X 蓄力（雷涌）走另一套：MC 的物品使用计时对「强制 setCurrentHand」不起作用，
                    // 用原公式会一直停在 0，所以这里直接读客户端自己数的 tick。
                    if (WangBowItem.CLIENT_ULT_CHARGING) {
                        return WangBowItem.ultPullFor(WangBowItem.CLIENT_ULT_CHARGE_TICKS);
                    }
                    return livingEntity.getActiveItem() != itemStack ? 0.0F :
                            (float)(itemStack.getMaxUseTime() - livingEntity.getItemUseTimeLeft()) / 20.0F;
                }
        );

        ModelPredicateProviderRegistry.register(
                BowRegistery.WS_BOW,
                new Identifier("pulling"),
                (itemStack, clientWorld, livingEntity, seed) -> {
                    return livingEntity != null && livingEntity.isUsingItem() && livingEntity.getActiveItem() == itemStack ? 1.0F : 0.0F;
                }
        );

        // 3. 注册 HUD 蓄力进度条
        //
        // 只负责「拉弓蓄力」这一条条。技能冷却图标不在这里——那是通用的
        // {@link SkillCooldownHud} 的活，所有武器共用。
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            PlayerEntity player = client.player;

            if (player != null && player.isUsingItem() && player.getActiveItem().getItem() instanceof WangBowItem) {
                // 右键：读客户端自己数的 tick（60 = 3 秒）。X 蓄力是另一套分母。
                float progress;
                if (WangBowItem.CLIENT_ULT_CHARGING) {
                    progress = Math.min(WangBowItem.CLIENT_ULT_CHARGE_TICKS
                            / (float) WangBowItem.ULT_CHARGE_TICKS, 1.0f);
                } else {
                    progress = Math.min(pullTicks / (float) PULL_TICKS, 1.0F);
                }

                int screenWidth = drawContext.getScaledWindowWidth();
                int screenHeight = drawContext.getScaledWindowHeight();

                int barWidth = 40;
                int barHeight = 3;
                int x = (screenWidth - barWidth) / 2;
                int y = (screenHeight / 2) + 100;

                drawContext.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0x80000000);
                drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF555555);

                int color = (progress >= 1.0f) ? 0xFFFFAA00 : 0xFF00AAFF;
                drawContext.fill(x, y, x + (int)(barWidth * progress), y + barHeight, color);
            }
        });
    }
}
