package org.examplee.wangsword.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import org.examplee.wangsword.entity.SwordTombGuardianEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;


/**
 * 剑冢守卫的渲染器，本体交给 GeckoLib，这里只挂模型。
 */
public class SwordTombGuardianRenderer extends GeoEntityRenderer<SwordTombGuardianEntity> {

    public SwordTombGuardianRenderer(EntityRendererFactory.Context context) {
        super(context, new SwordTombGuardianModel());
        this.shadowRadius = 1.2F;
        // 自发光层：读取 textures/entity/sword_tomb_guardian_glowmask.png，
        // 遮罩上 alpha > 0 的像素会脱离光照变亮，并且带呼吸。没有这张图时这一层什么都不画。
        this.addRenderLayer(new SwordTombGuardianGlowLayer(this));
    }
}
