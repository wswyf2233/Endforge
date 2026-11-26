package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.FulgurHelmItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 幽卫钢战盔的模型定义：geo / 贴图 / 动画三份资源。
 *
 * <p>geo 里 armorHead 包着 shell、crest、visor、temple、cheek，
 * 还有一层空骨骼 bipedHead 负责头部高度。
 */
public class FulgurHelmModel extends GeoModel<FulgurHelmItem> {

    @Override
    public Identifier getModelResource(FulgurHelmItem animatable) {
        return new Identifier("wangsword", "geo/fulgur_helm.geo.json");
    }

    @Override
    public Identifier getTextureResource(FulgurHelmItem animatable) {
        return new Identifier("wangsword", "textures/armor/fulgur_helm.png");
    }

    @Override
    public Identifier getAnimationResource(FulgurHelmItem animatable) {
        return new Identifier("wangsword", "animations/fulgur_helm.animation.json");
    }
}
