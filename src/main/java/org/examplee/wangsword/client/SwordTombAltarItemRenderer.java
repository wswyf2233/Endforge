package org.examplee.wangsword.client;

import org.examplee.wangsword.block.SwordTombAltarBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 祭坛物品的 GeckoLib 渲染器。
 *
 * <p>不用手动注册 {@code BuiltinItemRendererRegistry}：GeckoLib 4.8 的
 * {@code MixinItemRenderer} 会在原版走 BEWLR 分支时把渲染器换成本类，
 * 前提是物品实现了 {@code GeoItem} 且物品模型 parent 是 {@code builtin/entity}。
 *
 * <p>如果进游戏发现物品栏里模型偏大/偏小/偏出格子，在这里链一个
 * {@code withScale(...)} 微调即可，不用改 geo。
 */
public class SwordTombAltarItemRenderer extends GeoItemRenderer<SwordTombAltarBlockItem> {

    public SwordTombAltarItemRenderer() {
        super(new SwordTombAltarItemModel());
    }
}
