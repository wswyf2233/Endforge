package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.FulgurCuirassItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 幽卫钢胸甲的模型定义：geo / 贴图 / 动画三份资源。
 */
public class FulgurCuirassModel extends GeoModel<FulgurCuirassItem> {

    @Override
    public Identifier getModelResource(FulgurCuirassItem animatable) {
        return new Identifier("wangsword", "geo/fulgur_cuirass.geo.json");
    }

    @Override
    public Identifier getTextureResource(FulgurCuirassItem animatable) {
        return new Identifier("wangsword", "textures/armor/fulgur_cuirass.png");
    }

    @Override
    public Identifier getAnimationResource(FulgurCuirassItem animatable) {
        return new Identifier("wangsword", "animations/fulgur_cuirass.animation.json");
    }
}
