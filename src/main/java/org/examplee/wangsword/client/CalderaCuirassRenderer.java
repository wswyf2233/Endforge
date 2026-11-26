package org.examplee.wangsword.client;

import org.examplee.wangsword.item.unique.CalderaCuirassItem;

/**
 * 熔岩胸甲的护甲渲染器：模型本体交给 GeckoLib，这里只挂模型。
 */
public class CalderaCuirassRenderer extends WsArmorRenderer<CalderaCuirassItem> {

    public CalderaCuirassRenderer() {
        super(new CalderaCuirassModel());
    }
}
