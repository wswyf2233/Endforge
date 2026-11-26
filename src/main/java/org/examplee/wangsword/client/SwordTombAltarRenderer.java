package org.examplee.wangsword.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import org.examplee.wangsword.block.SwordTombAltarBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 祭坛的方块实体渲染器。原版模型在 blockstate 里被换成了空模型，
 * 屏幕上看到的祭坛完全是这张 geo 模型。
 */
public class SwordTombAltarRenderer extends GeoBlockRenderer<SwordTombAltarBlockEntity> {

    public SwordTombAltarRenderer(BlockEntityRendererFactory.Context context) {
        super(new SwordTombAltarModel());
    }
}
