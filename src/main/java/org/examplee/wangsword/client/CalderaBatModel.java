package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.entity.CalderaBatEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔岩蝠怪的模型定义：geo / 贴图 / 动画三份资源。
 */
public class CalderaBatModel extends GeoModel<CalderaBatEntity> {

    @Override
    public Identifier getModelResource(CalderaBatEntity animatable) {
        return new Identifier("wangsword", "geo/caldera.geo.json");
    }

    @Override
    public Identifier getTextureResource(CalderaBatEntity animatable) {
        return new Identifier("wangsword", "textures/entity/caldera.png");
    }

    @Override
    public Identifier getAnimationResource(CalderaBatEntity animatable) {
        return new Identifier("wangsword", "animations/caldera.animation.json");
    }
}
