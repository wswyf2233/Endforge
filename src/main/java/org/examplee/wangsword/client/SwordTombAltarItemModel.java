package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.block.SwordTombAltarBlockItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * 祭坛物品的 Geo 模型定义——刻意和 {@link SwordTombAltarModel} 用同一份 geo / 贴图 / 动画，
 * 这样"手里拿的"和"放下来的"是同一坨像素，只是物品没有召唤动画。
 */
public class SwordTombAltarItemModel extends GeoModel<SwordTombAltarBlockItem> {

    private static final Identifier MODEL =
            new Identifier("wangsword", "geo/block/sword_tomb_altar.geo.json");
    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/block/sword_tomb_altar_geo.png");
    private static final Identifier ANIMATIONS =
            new Identifier("wangsword", "animations/block/sword_tomb_altar.animation.json");

    @Override
    public Identifier getModelResource(SwordTombAltarBlockItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(SwordTombAltarBlockItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(SwordTombAltarBlockItem animatable) {
        return ANIMATIONS;
    }
}
