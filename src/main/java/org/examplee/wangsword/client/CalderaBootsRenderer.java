package org.examplee.wangsword.client;

import org.examplee.wangsword.item.unique.CalderaBootsItem;

/**
 * 熔岩战靴的护甲渲染器：模型本体交给 GeckoLib，这里只挂模型。
 */
public class CalderaBootsRenderer extends WsArmorRenderer<CalderaBootsItem> {

    public CalderaBootsRenderer() {
        super(new CalderaBootsModel());
    }
}
