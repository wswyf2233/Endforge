package org.examplee.wangsword.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import org.examplee.wangsword.entity.CinderColossusEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 熔核巨兽的渲染器。
 *
 * <p>模型本身 40 像素高（2.5 格），放大 3 倍后约 7.5 格，站在沉坛里能顶到柱子中段。
 * 碰撞箱按同样的倍数放大到 4.4 × 7.4，见 {@code EntityRegistry}。
 */
public class CinderColossusRenderer extends GeoEntityRenderer<CinderColossusEntity> {

    public CinderColossusRenderer(EntityRendererFactory.Context context) {
        super(context, new CinderColossusModel());
        this.shadowRadius = 3.0F;
        this.withScale(3.0F);
    }
}
