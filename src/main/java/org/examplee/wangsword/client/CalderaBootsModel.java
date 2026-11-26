package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.CalderaBootsItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔岩战靴的模型定义：geo / 贴图 / 动画三份资源。
 */
public class CalderaBootsModel extends GeoModel<CalderaBootsItem> {

    @Override
    public Identifier getModelResource(CalderaBootsItem animatable) {
        return new Identifier("wangsword", "geo/caldera_boots.geo.json");
    }

    @Override
    public Identifier getTextureResource(CalderaBootsItem animatable) {
        return new Identifier("wangsword", "textures/armor/caldera_boots.png");
    }

    @Override
    public Identifier getAnimationResource(CalderaBootsItem animatable) {
        return new Identifier("wangsword", "animations/caldera_boots.animation.json");
    }
}
