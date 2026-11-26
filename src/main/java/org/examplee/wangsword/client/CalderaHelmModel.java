package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.CalderaHelmItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔岩战盔的模型定义：geo / 贴图 / 动画三份资源。
 *
 * <p>骨骼层级在 geo 里是 armorHead 包着 shell、furnace、temple、cheek、horn，
 * GeckoLib 的 {@code GeoArmorRenderer} 会把 armorHead 套到玩家头部并跟着转头。
 */
public class CalderaHelmModel extends GeoModel<CalderaHelmItem> {

    @Override
    public Identifier getModelResource(CalderaHelmItem animatable) {
        return new Identifier("wangsword", "geo/caldera_helm.geo.json");
    }

    @Override
    public Identifier getTextureResource(CalderaHelmItem animatable) {
        return new Identifier("wangsword", "textures/armor/caldera_helm.png");
    }

    @Override
    public Identifier getAnimationResource(CalderaHelmItem animatable) {
        return new Identifier("wangsword", "animations/caldera_helm.animation.json");
    }
}
