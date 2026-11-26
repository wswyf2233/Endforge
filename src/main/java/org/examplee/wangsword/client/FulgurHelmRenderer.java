package org.examplee.wangsword.client;

import org.examplee.wangsword.item.unique.FulgurHelmItem;

/**
 * 幽卫钢战盔的护甲渲染器。
 *
 * <p>物品实现 {@code GeoItem} 后，GeckoLib 的 HumanoidArmorLayer mixin 会自动换用
 * 这里的模型，不需要再往 ArmorFeatureRenderer 里注册。
 */
public class FulgurHelmRenderer extends WsArmorRenderer<FulgurHelmItem> {

    public FulgurHelmRenderer() {
        super(new FulgurHelmModel());
    }
}
