package org.examplee.wangsword.block;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 祭坛的方块物品，实现 {@link GeoItem}，让"手里拿的"和"放下来的"是同一个模型。
 *
 * <p>之前物品走的是原版 {@code block/sword_tomb_altar}（一个 16³ 实心方块，三张 16px 面贴图），
 * 而世界里是 GeckoLib 的矮石座 + 剑 + 法阵环，两套东西完全不一样，拿着和放下落差极大。
 *
 * <p>现在物品模型改成 {@code builtin/entity}，GeckoLib 的 mixin 会拦截原版渲染、
 * 换成这里 {@link #createRenderer} 给出的 {@code GeoItemRenderer}，
 * 于是物品栏、手持、掉落物、展示框全部渲染 geo 模型，连 idle 的法阵环都会在手里转。
 *
 * <p>{@code createRenderer} 只会在客户端被调用（{@link GeoItem#makeRenderer} 在服务端
 * 直接返回空 supplier），所以里面引用客户端渲染器类不会在服务端炸。
 */
public class SwordTombAltarBlockItem extends BlockItem implements GeoItem {

    /** 和方块实体用同一段 idle 动画：法阵环匀速转、剑浮动、四个符文错相呼吸 */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public SwordTombAltarBlockItem(Block block, Item.Settings settings) {
        super(block, settings);
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private org.examplee.wangsword.client.SwordTombAltarItemRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new org.examplee.wangsword.client.SwordTombAltarItemRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 过渡 2 tick，和方块实体保持一致，拿在手上时环不会每帧硬跳
        controllers.add(new AnimationController<>(this, "idle", 2, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
