package org.examplee.wangsword.block;

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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 熔核唤石的方块物品，实现 {@link GeoItem}。
 *
 * <p>和剑冢祭坛同一套做法：物品模型是 {@code builtin/entity}，GeckoLib 的 mixin 会在原版
 * 走内置渲染分支时把渲染器换成 {@link org.examplee.wangsword.client.MoltenCoreSummonerItemRenderer}，
 * 于是物品栏、手持、掉落物、展示框里显示的都是 geo 模型本身，待机脉动照常在手上跑。
 *
 * <p>如果不写这个类，物品就只是一张 16×16 的平面图，和摆在地上的样子完全对不上。
 *
 * <p>{@code createRenderer} 只在客户端被调用（{@link GeoItem#makeRenderer} 在服务端返回空
 * supplier），所以里面引用客户端渲染器不会在服务端崩。
 */
public class MoltenCoreSummonerBlockItem extends BlockItem implements GeoItem {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public MoltenCoreSummonerBlockItem(Block block, Item.Settings settings) {
        super(block, settings);
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private org.examplee.wangsword.client.MoltenCoreSummonerItemRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new org.examplee.wangsword.client.MoltenCoreSummonerItemRenderer();
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
        // 过渡 2 tick，和方块实体保持一致
        controllers.add(new AnimationController<>(this, "idle", 2, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
