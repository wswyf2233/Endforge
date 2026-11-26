package org.examplee.wangsword.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import org.examplee.wangsword.entity.CalderaBatEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 熔岩蝠怪的渲染器：模型本体交给 GeckoLib，这里只挂模型和影子大小。
 */
public class CalderaBatRenderer extends GeoEntityRenderer<CalderaBatEntity> {

    public CalderaBatRenderer(EntityRendererFactory.Context context) {
        super(context, new CalderaBatModel());
        // 原模型约 1.6 格高、翼展 2.4 格，整体缩到 0.8 倍（约 1.28 格高、翼展 1.9 格）
        this.withScale(0.8F, 0.8F);
        this.shadowRadius = 0.55F;
    }
}
