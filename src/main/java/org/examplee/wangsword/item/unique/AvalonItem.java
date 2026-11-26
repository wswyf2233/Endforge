package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 阿瓦隆（Avalon）—— 誓约胜利之剑的剑鞘。
 *
 * <p>放在背包里就生效，不需要拿在手上：
 * <ul>
 *   <li>每 2 秒回复 1 点生命；</li>
 *   <li>持续获得抗性提升（减伤）；</li>
 *   <li>免疫中毒与凋零。</li>
 * </ul>
 * 主动【远离尘世的理想乡】由 {@link org.examplee.wangsword.util.AvalonHandler} 负责（Z 键）。
 */
public class AvalonItem extends Item {

    /** 每多少 tick 回 1 点血 */
    public static final int HEAL_INTERVAL = 40;
    /** 抗性提升持续时间 / 刷新间隔 */
    public static final int RESIST_DURATION = 100;
    public static final int RESIST_INTERVAL = 60;

    public AvalonItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient() || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        // 回血
        if (player.age % HEAL_INTERVAL == 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0F);
        }

        // 抗性提升（减伤 20%，不显示图标、不产生粒子）
        if (player.age % RESIST_INTERVAL == 0) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, RESIST_DURATION, 0, true, false, false));
        }

        // 免疫中毒 / 凋零
        if (player.age % 20 == 0) {
            player.removeStatusEffect(StatusEffects.POISON);
            player.removeStatusEffect(StatusEffects.WITHER);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.title").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.passive").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.passive_detail").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.active").formatted(Formatting.YELLOW));
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.active_detail").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.avalon.hint").formatted(Formatting.DARK_GRAY));
    }
}
