package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 3D 护甲（GeckoLib）的通用基类：把「模型 + idle/walk 动画」这套管线收在一处，
 * 具体每个部位只要给出 {@link #createArmorRenderer()} 和材质就行。
 *
 * <p>数值由传入的 {@link ArmorMaterial} 决定。套装说明由子类覆写
 * {@link #appendSetTooltip(List)} 提供，四件共用同一份文案。
 *
 * <p>动画只用 {@code idle} 与 {@code walk} 两段，模型里的骨骼名要和动画文件对上；
 * 换穿戴者状态靠 {@link DataTickets#ENTITY} 拿实体（GeckoLib 渲染时会塞进去）。
 */
public abstract class WsGeoArmorItem extends ArmorItem implements GeoItem {

    /** 站定 */
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    /** 移动 */
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");

    /** 移动判定阈值，平方过，避免每帧开方 */
    private static final double MOVING_SPEED_SQR = 0.0025D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    protected WsGeoArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    /** 只有客户端会调用：给这个部位创建一个护甲渲染器 */
    protected abstract GeoArmorRenderer<?> createArmorRenderer();

    /**
     * 往物品介绍里追加套装的技能说明。
     *
     * <p>默认什么都不写。属于某套套装的子类覆写它，转调对应 SetHandler 的
     * {@code appendTooltip}，这样四件共用一份文案。
     */
    protected void appendSetTooltip(List<Text> tooltip) {
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        appendSetTooltip(tooltip);
    }

    /** 站定动画，默认 {@code idle} */
    protected RawAnimation idleAnimation() {
        return IDLE_ANIM;
    }

    /** 移动动画，默认 {@code walk}；动画文件里没有 walk 的部位覆写它指回 idle */
    protected RawAnimation walkAnimation() {
        return WALK_ANIM;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            @SuppressWarnings("unchecked")
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(
                    LivingEntity entity, ItemStack stack, EquipmentSlot slot, BipedEntityModel<LivingEntity> base) {
                if (this.renderer == null) {
                    this.renderer = createArmorRenderer();
                }
                this.renderer.prepForRender(entity, stack, slot, base);
                return (BipedEntityModel<LivingEntity>) (Object) this.renderer;
            }
        });
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (state.getData(DataTickets.ENTITY) instanceof LivingEntity living
                    && living.getVelocity().horizontalLengthSquared() > MOVING_SPEED_SQR) {
                return state.setAndContinue(walkAnimation());
            }
            return state.setAndContinue(idleAnimation());
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
