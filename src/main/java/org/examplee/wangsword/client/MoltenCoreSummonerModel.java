package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.block.MoltenCoreSummonerBlockEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔核唤石的 GeckoLib 模型定义。
 *
 * <p>贴图是 192×96 的盒状展开图 {@code cinderstone.png}，和物品栏里那个 32×32 的
 * 小图标是两张完全不同的图——前者只给 geo 模型用，后者只给物品栏用。
 */
public class MoltenCoreSummonerModel extends GeoModel<MoltenCoreSummonerBlockEntity> {

    private static final Identifier MODEL =
            new Identifier("wangsword", "geo/block/molten_core_summoner.geo.json");
    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/block/molten_core_summoner.png");
    private static final Identifier ANIMATIONS =
            new Identifier("wangsword", "animations/block/molten_core_summoner.animation.json");

    @Override
    public Identifier getModelResource(MoltenCoreSummonerBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(MoltenCoreSummonerBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MoltenCoreSummonerBlockEntity animatable) {
        return ANIMATIONS;
    }
}
