package org.examplee.wangsword.client;

import org.examplee.wangsword.item.unique.CalderaGreavesItem;

/**
 * 熔岩护腿的护甲渲染器：模型本体交给 GeckoLib，这里只挂模型。
 */
public class CalderaGreavesRenderer extends WsArmorRenderer<CalderaGreavesItem> {

    public CalderaGreavesRenderer() {
        super(new CalderaGreavesModel());
    }
}
