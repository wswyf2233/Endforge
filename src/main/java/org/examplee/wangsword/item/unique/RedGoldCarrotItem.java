package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * 赤金胡萝卜。
 *
 * <p>吃一次回 18 点饥饿值、20 点饱和度，而且永远不消耗。
 * 不消耗靠重写 {@link #finishUsing} 实现——原版是在 {@code LivingEntity.eatFood} 里扣数量，
 * 这里直接给饱食度并原样返回物品，物品栏里的那个 stack 就不会减少。
 */
public class RedGoldCarrotItem extends Item {

    /** 回复的饥饿值 */
    public static final int HUNGER = 18;

    /** 回复的饱和度 */
    public static final float SATURATION = 20.0F;

    /** 原版公式：实际饱和度 = 饥饿值 × 饱和度系数 × 2 */
    public static final float SATURATION_MODIFIER = SATURATION / (HUNGER * 2.0F);

    public RedGoldCarrotItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient() && user instanceof PlayerEntity player) {
            player.getHungerManager().add(HUNGER, SATURATION_MODIFIER);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS,
                    0.5F, 0.9F + world.random.nextFloat() * 0.2F);
        }
        // 返回原 stack，不调用 decrement，所以永不消耗
        return stack;
    }
}
