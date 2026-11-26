package org.examplee.wangsword.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 超级雷电宝石：九个雷电宝石压出来的后期材料。
 *
 * <p>纯粹的材料，本身没有任何功能——麻烦全在「拿在手里」这件事上，
 * 结算逻辑在 {@code util/SuperLightningGemHandler}：
 * 手持每秒掉 1 点血，室外雷雨天还会被真雷劈。
 * 这里只负责把这两条写进说明文字，免得玩家拿着它站着聊天突然暴毙还不知道为什么。
 */
public class SuperLightningStoneItem extends Item {

    public SuperLightningStoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.super_lightningstone.hold")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.super_lightningstone.storm")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.super_lightningstone.hint")
                .formatted(Formatting.DARK_GRAY));
    }
}
