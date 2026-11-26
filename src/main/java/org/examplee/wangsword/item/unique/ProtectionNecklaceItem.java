package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.examplee.wangsword.util.EquippedTickable;
import org.examplee.wangsword.util.NecklaceAttributes;
import org.examplee.wangsword.util.ProtectionNecklaceHandler;

import java.util.List;

/**
 * 保护项链（Accessories 的 necklace 槽）。
 *
 * <p>两层减伤：抗性提升 III（从抗性提升戒指继承），以及一层不吃原版抗性上限的
 * 30% 真减伤。真减伤在 {@code ProtectionNecklaceMixin} 里挂到伤害结算末尾。
 */
public class ProtectionNecklaceItem extends Item implements Accessory, EquippedTickable {

    public ProtectionNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        ProtectionNecklaceHandler.onTick(player);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.protection.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.protection.desc2",
                ProtectionNecklaceHandler.FINAL_REDUCTION_PERCENT).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.protection.desc3").formatted(Formatting.DARK_GRAY));
    }
}
