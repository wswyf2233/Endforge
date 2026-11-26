package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.block.MoltenCoreSummonerBlockItem;
import software.bernie.geckolib.model.GeoModel;

/** 熔核唤石物品的 Geo 模型：和方块实体用同一份 geo / 贴图 / 动画 */
public class MoltenCoreSummonerItemModel extends GeoModel<MoltenCoreSummonerBlockItem> {

    private static final Identifier MODEL =
            new Identifier("wangsword", "geo/block/molten_core_summoner.geo.json");
    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/block/molten_core_summoner.png");
    private static final Identifier ANIMATIONS =
            new Identifier("wangsword", "animations/block/molten_core_summoner.animation.json");

    @Override
    public Identifier getModelResource(MoltenCoreSummonerBlockItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(MoltenCoreSummonerBlockItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MoltenCoreSummonerBlockItem animatable) {
        return ANIMATIONS;
    }
}
