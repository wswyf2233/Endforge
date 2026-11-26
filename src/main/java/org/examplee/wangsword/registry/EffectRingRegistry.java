package org.examplee.wangsword.registry;

import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.Accessory;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.item.unique.EffectRingItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 药水效果戒指的注册表。
 *
 * <p>每个戒指佩戴时提供该效果在游戏内可获得的最高等级。
 * {@code catalyst} 是合成用的材料（对应药水的酿造材料），没有对应药水的自行选取。
 */
public final class EffectRingRegistry {

    /**
     * @param id        物品 id（不含命名空间）
     * @param zhName    中文名
     * @param enName    英文名
     * @param effect    提供的状态效果
     * @param amplifier 等级，0 = I
     * @param catalyst  合成材料
     */
    public record Def(String id, String zhName, String enName,
                      StatusEffect effect, int amplifier, String catalyst) {
    }

    public static final List<Def> DEFS = List.of(
            // 潮涌能量最高 II 级（潮涌核心）
            new Def("ws_redgold_ring_conduit_power", "潮涌能量戒指", "Conduit Power Ring",
                    StatusEffects.CONDUIT_POWER, 1, "minecraft:nautilus_shell"),
            // 海豚的恩惠只有 I 级，无药水
            new Def("ws_redgold_ring_dolphins_grace", "海豚的恩惠戒指", "Dolphin's Grace Ring",
                    StatusEffects.DOLPHINS_GRACE, 0, "minecraft:prismarine_shard"),
            // 抗火只有 I 级
            new Def("ws_redgold_ring_fire_resistance", "抗火戒指", "Fire Resistance Ring",
                    StatusEffects.FIRE_RESISTANCE, 0, "minecraft:magma_cream"),
            // 急迫最高 II 级（信标），无药水
            new Def("ws_redgold_ring_haste", "急迫戒指", "Haste Ring",
                    StatusEffects.HASTE, 1, "minecraft:gold_ingot"),
            // 村庄英雄只有 I 级，无药水
            new Def("ws_redgold_ring_hero_of_the_village", "村庄英雄戒指", "Hero of the Village Ring",
                    StatusEffects.HERO_OF_THE_VILLAGE, 0, "minecraft:emerald"),
            // 隐身只有 I 级
            new Def("ws_redgold_ring_invisibility", "隐身戒指", "Invisibility Ring",
                    StatusEffects.INVISIBILITY, 0, "minecraft:fermented_spider_eye"),
            // 跳跃提升最高 II 级
            new Def("ws_redgold_ring_jump_boost", "跳跃提升戒指", "Jump Boost Ring",
                    StatusEffects.JUMP_BOOST, 1, "minecraft:rabbit_foot"),
            // 幸运只有 I 级，无药水
            new Def("ws_redgold_ring_luck", "幸运戒指", "Luck Ring",
                    StatusEffects.LUCK, 0, "minecraft:golden_apple"),
            // 夜视只有 I 级
            new Def("ws_redgold_ring_night_vision", "夜视戒指", "Night Vision Ring",
                    StatusEffects.NIGHT_VISION, 0, "minecraft:golden_carrot"),
            // 生命恢复最高 II 级
            new Def("ws_redgold_ring_regeneration", "生命恢复戒指", "Regeneration Ring",
                    StatusEffects.REGENERATION, 1, "minecraft:ghast_tear"),
            // 抗性提升最高 III 级（神龟药水）
            new Def("ws_redgold_ring_resistance", "抗性提升戒指", "Resistance Ring",
                    StatusEffects.RESISTANCE, 2, "minecraft:turtle_helmet"),
            // 缓降只有 I 级
            new Def("ws_redgold_ring_slow_falling", "缓降戒指", "Slow Falling Ring",
                    StatusEffects.SLOW_FALLING, 0, "minecraft:phantom_membrane"),
            // 速度最高 II 级
            new Def("ws_redgold_ring_speed", "速度戒指", "Speed Ring",
                    StatusEffects.SPEED, 1, "minecraft:sugar"),
            // 力量最高 II 级
            new Def("ws_redgold_ring_strength", "力量戒指", "Strength Ring",
                    StatusEffects.STRENGTH, 1, "minecraft:blaze_powder"),
            // 水下呼吸只有 I 级
            new Def("ws_redgold_ring_water_breathing", "水下呼吸戒指", "Water Breathing Ring",
                    StatusEffects.WATER_BREATHING, 0, "minecraft:pufferfish")
    );

    public static final List<Item> ALL = new ArrayList<>();

    private EffectRingRegistry() {
    }

    public static void init() {
        for (Def def : DEFS) {
            Item item = Registry.register(
                    Registries.ITEM,
                    new Identifier(Wangsword.MOD_ID, def.id()),
                    new EffectRingItem(new Item.Settings().maxCount(1), def.effect(), def.amplifier()));
            AccessoriesAPI.registerAccessory(item, (Accessory) item);
            ALL.add(item);
        }
    }
}
