package org.examplee.wangsword.client;

import org.examplee.wangsword.block.MoltenCoreSummonerBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 熔核唤石物品的 GeckoLib 渲染器。
 *
 * <p>不需要手动注册 BEWLR：物品实现了 {@code GeoItem}、物品模型 parent 是
 * {@code builtin/entity} 时，GeckoLib 的 mixin 会自己接管。
 *
 * <p>物品栏里偏大/偏小/偏出格子的话，在这里链 {@code withScale(...)} 微调。
 */
public class MoltenCoreSummonerItemRenderer extends GeoItemRenderer<MoltenCoreSummonerBlockItem> {

    public MoltenCoreSummonerItemRenderer() {
        super(new MoltenCoreSummonerItemModel());
    }
}
