package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.entity.GhostCrownWraithEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 裂冠幽灵的模型定义：三个资源路径。
 */
public class GhostCrownWraithModel extends GeoModel<GhostCrownWraithEntity> {

    @Override
    public Identifier getModelResource(GhostCrownWraithEntity animatable) {
        return new Identifier("wangsword", "geo/ghost_crown_wraith.geo.json");
    }

    @Override
    public Identifier getTextureResource(GhostCrownWraithEntity animatable) {
        return new Identifier("wangsword", "textures/entity/ghost_crown_wraith.png");
    }

    @Override
    public Identifier getAnimationResource(GhostCrownWraithEntity animatable) {
        return new Identifier("wangsword", "animations/ghost_crown_wraith.animation.json");
    }
}
