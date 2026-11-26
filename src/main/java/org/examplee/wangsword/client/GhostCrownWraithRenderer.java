package org.examplee.wangsword.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import org.examplee.wangsword.entity.GhostCrownWraithEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 裂冠幽灵的渲染器，本体交给 GeckoLib，这里只挂模型。
 */
public class GhostCrownWraithRenderer extends GeoEntityRenderer<GhostCrownWraithEntity> {

    public GhostCrownWraithRenderer(EntityRendererFactory.Context context) {
        super(context, new GhostCrownWraithModel());
        this.shadowRadius = 0.9F;
    }
}
