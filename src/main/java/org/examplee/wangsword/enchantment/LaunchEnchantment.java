package org.examplee.wangsword.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;

/**
 * 击飞：命中时把目标打上天，高度随等级提升。
 *
 * <p>适用于所有近战武器（剑、斧、三叉戟、以及本模组的自定义近战武器）。
 * 附魔台、村民交易均可获得（非宝藏附魔）。
 */
public class LaunchEnchantment extends Enchantment {

    public LaunchEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinPower(int level) {
        return 5 + (level - 1) * 8;
    }

    @Override
    public int getMaxPower(int level) {
        return getMinPower(level) + 20;
    }

    /** 允许所有近战武器，而不只是剑 */
    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    /** 可在附魔台刷出 */
    @Override
    public boolean isAvailableForEnchantedBookOffer() {
        return true;
    }

    /** 非宝藏附魔：村民也能交易 */
    @Override
    public boolean isTreasure() {
        return false;
    }

    /** 每级的起跳初速度 */
    public static double launchVelocity(int level) {
        return switch (level) {
            case 1 -> 0.8D;
            case 2 -> 1.0D;
            default -> 1.2D;
        };
    }
}