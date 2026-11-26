package org.examplee.wangsword.client;

import net.minecraft.util.Identifier;
import org.examplee.wangsword.block.SwordTombAltarBlockEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 剑冢祭坛的 GeckoLib 模型定义。
 *
 * <p>贴图故意和方块本身的 16×16 贴图分开：Geo 模型用的是 64×64 的
 * {@code sword_tomb_altar_geo.png}，方块物品栏里显示的仍是 cube_bottom_top 那张。
 */
public class SwordTombAltarModel extends GeoModel<SwordTombAltarBlockEntity> {

    private static final Identifier MODEL =
            new Identifier("wangsword", "geo/block/sword_tomb_altar.geo.json");
    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/block/sword_tomb_altar_geo.png");
    private static final Identifier ANIMATIONS =
            new Identifier("wangsword", "animations/block/sword_tomb_altar.animation.json");

    @Override
    public Identifier getModelResource(SwordTombAltarBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(SwordTombAltarBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(SwordTombAltarBlockEntity animatable) {
        return ANIMATIONS;
    }
}
