package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.CalderaGreavesItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔岩护腿的模型定义：geo / 贴图 / 动画三份资源。
 */
public class CalderaGreavesModel extends GeoModel<CalderaGreavesItem> {

    @Override
    public Identifier getModelResource(CalderaGreavesItem animatable) {
        return new Identifier("wangsword", "geo/caldera_greaves.geo.json");
    }

    @Override
    public Identifier getTextureResource(CalderaGreavesItem animatable) {
        return new Identifier("wangsword", "textures/armor/caldera_greaves.png");
    }

    @Override
    public Identifier getAnimationResource(CalderaGreavesItem animatable) {
        return new Identifier("wangsword", "animations/caldera_greaves.animation.json");
    }
}
