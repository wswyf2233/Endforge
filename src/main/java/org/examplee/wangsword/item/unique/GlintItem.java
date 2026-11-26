package org.examplee.wangsword.item.unique;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 带附魔光效的物品。
 *
 * <p>Minecraft 的物品本身无法照亮世界（只有方块有光照等级），
 * 所以用附魔光效（glint）来表现"发光"的观感：
 * 物品栏、手持、掉落物上都会带一层流动的光泽。
 */
public class GlintItem extends Item {

    public GlintItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}