package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.examplee.wangsword.util.EquippedTickable;
import org.examplee.wangsword.util.WardHandler;

import java.util.List;

/**
 * 护符（Accessories 的 charm 槽）。
 *
 * <p>一枚护符管一种伤害的免疫，外加一点和该伤害同源的小收益。免疫判定在
 * {@link WardHandler} 里统一吃伤害事件，物品这边只负责每 tick 推常驻效果、
 * 摘下时收尾，以及写 tooltip。
 */
public class WardCharmItem extends Item implements Accessory, EquippedTickable {

    /** 基础最大生命加成，和 {@link CharmBlankItem} 一致：铭刻前后不会掉属性 */
    private static final double MAX_HEALTH_BONUS = 10.0D;

    private final WardType ward;

    public WardCharmItem(Settings settings, WardType ward) {
        super(settings);
        this.ward = ward;
    }

    public WardType ward() {
        return ward;
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        builder.addStackable(EntityAttributes.GENERIC_MAX_HEALTH,
                new Identifier("wangsword", "ward_charm"), MAX_HEALTH_BONUS,
                EntityAttributeModifier.Operation.ADDITION);
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        WardHandler.tickWard(player, ward);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        if (reference.entity() instanceof ServerPlayerEntity player) {
            WardHandler.onUnequip(player, ward);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.charm.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.charm." + ward.id() + ".immune")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.charm." + ward.id() + ".extra")
                .formatted(Formatting.DARK_GRAY));
    }
}
