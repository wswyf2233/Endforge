package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.FulgurGreavesItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 幽卫钢护腿的模型定义：geo / 贴图 / 动画三份资源。
 */
public class FulgurGreavesModel extends GeoModel<FulgurGreavesItem> {

    @Override
    public Identifier getModelResource(FulgurGreavesItem animatable) {
        return new Identifier("wangsword", "geo/fulgur_greaves.geo.json");
    }

    @Override
    public Identifier getTextureResource(FulgurGreavesItem animatable) {
        return new Identifier("wangsword", "textures/armor/fulgur_greaves.png");
    }

    @Override
    public Identifier getAnimationResource(FulgurGreavesItem animatable) {
        return new Identifier("wangsword", "animations/fulgur_greaves.animation.json");
    }
}
