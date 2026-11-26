package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.CalderaCuirassItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔岩胸甲的模型定义：geo / 贴图 / 动画三份资源。
 */
public class CalderaCuirassModel extends GeoModel<CalderaCuirassItem> {

    @Override
    public Identifier getModelResource(CalderaCuirassItem animatable) {
        return new Identifier("wangsword", "geo/caldera_cuirass.geo.json");
    }

    @Override
    public Identifier getTextureResource(CalderaCuirassItem animatable) {
        return new Identifier("wangsword", "textures/armor/caldera_cuirass.png");
    }

    @Override
    public Identifier getAnimationResource(CalderaCuirassItem animatable) {
        return new Identifier("wangsword", "animations/caldera_cuirass.animation.json");
    }
}
