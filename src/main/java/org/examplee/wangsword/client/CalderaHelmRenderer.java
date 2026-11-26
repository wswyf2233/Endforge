package org.examplee.wangsword.client;

import org.examplee.wangsword.item.unique.CalderaHelmItem;

/**
 * 熔岩战盔的护甲渲染器：模型本体交给 GeckoLib，这里只挂模型。
 *
 * <p>不需要手动注册到 ArmorFeatureRenderer——物品实现 {@code GeoItem} 后，
 * GeckoLib 的 HumanoidArmorLayer mixin 会自动换用这里的模型。
 */
public class CalderaHelmRenderer extends WsArmorRenderer<CalderaHelmItem> {

    public CalderaHelmRenderer() {
        super(new CalderaHelmModel());
    }
}
