package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.FulgurBootsItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 幽卫钢战靴的模型定义：geo / 贴图 / 动画三份资源。
 */
public class FulgurBootsModel extends GeoModel<FulgurBootsItem> {

    @Override
    public Identifier getModelResource(FulgurBootsItem animatable) {
        return new Identifier("wangsword", "geo/fulgur_boots.geo.json");
    }

    @Override
    public Identifier getTextureResource(FulgurBootsItem animatable) {
        return new Identifier("wangsword", "textures/armor/fulgur_boots.png");
    }

    @Override
    public Identifier getAnimationResource(FulgurBootsItem animatable) {
        return new Identifier("wangsword", "animations/fulgur_boots.animation.json");
    }
}
