package org.examplee.wangsword.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.block.MoltenCoreSummonerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 熔核唤石的渲染器。
 *
 * <p>方块本体在 blockstate 里指向空模型，所以屏幕上看到的整块石头都是这张 geo 模型。
 * 用 cutout 而不是 solid：贴图里岩石边缘和熔核周围有硬透明像素，solid 会把它们画成黑块；
 * 又不能用 translucent，那样会关掉深度写入，石头的前后层会互相穿透。
 */
public class MoltenCoreSummonerRenderer extends GeoBlockRenderer<MoltenCoreSummonerBlockEntity> {

    public MoltenCoreSummonerRenderer(BlockEntityRendererFactory.Context context) {
        super(new MoltenCoreSummonerModel());
    }

    @Override
    public RenderLayer getRenderType(MoltenCoreSummonerBlockEntity animatable, Identifier texture,
                                     VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }
}
