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
import org.examplee.wangsword.util.SoulbearerHandler;

import java.util.List;

/**
 * 承魂项链（Accessories 的 necklace 槽）。
 *
 * <p>挨打攒层、层数换攻击、攒够了抵一次致命伤。逻辑全在 {@link SoulbearerHandler}，
 * 物品这边只负责每 tick 推一次衰减检查和摘下时清理状态。
 */
public class SoulbearerNecklaceItem extends Item implements Accessory, EquippedTickable {

    public SoulbearerNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        SoulbearerHandler.tick(player);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        if (reference.entity() instanceof ServerPlayerEntity player) {
            SoulbearerHandler.clear(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.soulbearer.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.soulbearer.desc2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.soulbearer.desc3").formatted(Formatting.DARK_GRAY));
    }
}
