package org.examplee.wangsword.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import org.examplee.wangsword.item.unique.WangShadowSwordItem;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.recipe.Ingredient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.examplee.wangsword.Wangsword;


public class ItemsRegistry {
    // ===================== 【武器数值全部集中在这里】 =====================
    public static final int SWORD_DURABILITY = 6666;   // 耐久
    public static final float SWORD_BASE_ATTACK = 4.0F;   // 材料基础伤害
    public static final int SWORD_ADD_ATTACK = 96;      // 额外追加伤害
    public static final float SWORD_ATTACK_SPEED = -1.4F;  // 攻速修正：总攻速 2.6，冷却 7.7 tick
    public static final int SWORD_ENCHANTABILITY = 55;     // 附魔值
    // =====================================================================

    // 自定义工具材料
    public static final ToolMaterial CUSTOM_NETHERITE_MATERIAL = new ToolMaterial() {
        @Override
        public int getDurability() {
            return SWORD_DURABILITY;
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return 1.0F;
        }

        @Override
        public float getAttackDamage() {
            return SWORD_BASE_ATTACK;
        }

        @Override
        public int getMiningLevel() {
            return 4;
        }

        @Override
        public int getEnchantability() {
            return SWORD_ENCHANTABILITY;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.ofItems(Items.NETHERITE_INGOT);
        }
    };


    // ... 前面不变的代码 ...

    public static final Item WANG_SWORD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "wang_sword"),
            new WangShadowSwordItem(CUSTOM_NETHERITE_MATERIAL, SWORD_ADD_ATTACK, SWORD_ATTACK_SPEED,
                    new Item.Settings()
            )
    );


    // 合成材料：风之结晶
    public static final Item WS_WIND_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_wind_crystal"),
            new Item(new Item.Settings())
    );
    // 合成材料：紫珀水晶
    public static final Item WS_AMETHYST_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_amethyst_crystal"),
            new Item(new Item.Settings())
    );
    // 升级锻造模板：紫水晶之星
    public static final Item WS_UPGRADE_SMITHING_TEMPLATE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_upgrade_smithing_template"),
            createUpgradeTemplate()
    );

    private static Item createUpgradeTemplate() {
        var vanilla = (net.minecraft.item.SmithingTemplateItem) Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        return new net.minecraft.item.SmithingTemplateItem(
                Text.translatable("item.wangsword.ws_upgrade_smithing_template.applies_to"),
                Text.translatable("item.wangsword.ws_upgrade_smithing_template.ingredients"),
                Text.translatable("item.wangsword.ws_upgrade_smithing_template")
                        .formatted(net.minecraft.util.Formatting.GOLD),
                Text.translatable("item.wangsword.ws_upgrade_smithing_template.base_slot_description"),
                Text.translatable("item.wangsword.ws_upgrade_smithing_template.additions_slot_description"),
                vanilla.getEmptyBaseSlotTextures(),
                vanilla.getEmptyAdditionsSlotTextures()
        );
    }
    // 合成材料：紫金锭
    public static final Item WS_AMETHYST_INGOT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_amethyst_ingot"),
            new Item(new Item.Settings())
    );
    // 合成材料：末地合金碎片（太古残骸烧制）
    public static final Item WS_TAIGU_SHARD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_taigu_shard"),
            new org.examplee.wangsword.item.unique.GlintItem(new Item.Settings())
    );

    // 死神核心碎片：死神镰刀的反噬奖励
    public static final Item WS_DEATHCORESHARD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_deathcoreshard"),
            new Item(new Item.Settings())
    );

    // 死神核心：9 个碎片合成
    public static final Item WS_DEATHCORE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_deathcore"),
            new Item(new Item.Settings())
    );

    // 守卫核心：每次击杀剑冢守卫必掉，与巨剑一同掉落
    public static final Item WS_GUARDIANCORE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_guardiancore"),
            new Item(new Item.Settings())
    );

    // 裂冠残片：裂冠幽灵的专属掉落
    public static final Item WS_CROWN_SHARD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_crown_shard"),
            new Item(new Item.Settings())
    );

    // 药草：草药材料，不能直接放置
    public static final Item WS_HERB = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_herb"),
            new Item(new Item.Settings())
    );

    // 药草种子：放下去长出药草
    public static final Item WS_HERB_SEED = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_herb_seed"),
            new net.minecraft.item.BlockItem(BlocksRegistry.WS_HERB, new Item.Settings())
    );

    // 恢复药：药草 + 玻璃瓶，喝下回 30% 最大生命值，返还玻璃瓶
    public static final Item WS_HEALING_POTION = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_healing_potion"),
            new org.examplee.wangsword.item.unique.HealingPotionItem(
                    new Item.Settings().maxCount(16))
    );

    // 大回复药：蜂蜜瓶 + 恢复药，喝下回 50%
    public static final Item WS_GREATER_HEALING_POTION = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_greater_healing_potion"),
            new org.examplee.wangsword.item.unique.HealingPotionItem(
                    new Item.Settings().maxCount(16), 0.50F)
    );

    // 燃素结晶：燃素矿的产物，只做燃料，不进任何装备配方
    public static final Item WS_PYRON_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_pyron_crystal"),
            new Item(new Item.Settings())
    );

    // 高能煤炭：煤 + 燃素结晶，烧 64 个物品
    public static final Item WS_HIGH_ENERGY_COAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_high_energy_coal"),
            new Item(new Item.Settings())
    );

    // 熔核残息：召唤 BOSS 的祭品，右键熔核唤石时消耗一枚。来源还没定，暂时没有配方
    public static final Item WS_MOLTEN_BREATH = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_molten_breath"),
            new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.EPIC)) {
                @Override
                public void appendTooltip(ItemStack stack, net.minecraft.world.World world,
                                          java.util.List<Text> tooltip,
                                          net.minecraft.client.item.TooltipContext context) {
                    tooltip.add(Text.translatable("tooltip.wangsword.molten_breath.desc"));
                }
            }
    );

    // 大地之心：剑冢深处战利品箱必出的稀有材料，暂时没有配方用途
    public static final Item WS_EARTH_HEART = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_earth_heart"),
            new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.EPIC))
    );

    // 熔岩核心：熔核巨兽必掉，封岩巨剑的最后一道料
    public static final Item WS_MOLTEN_CORE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_molten_core"),
            new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.EPIC))
    );

    // 熔岩锭：熔岩核心 + 一圈赤金锭，一次出 8 个。熔岩套（头盔/胸甲）的主料
    public static final Item WS_MOLTEN_INGOT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_molten_ingot"),
            new Item(new Item.Settings())
    );

    // 幽卫钢锭：剑冢守卫固定掉 3 个（抢夺每级 +1），幽卫钢套四件的主料
    public static final Item WS_GHOST_INGOT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_ghost_ingot"),
            new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.RARE))
    );

    // 熔岩蝠晶：熔岩蝠怪的掉落。带耐久，当作火焰法杖的强化弹「充能」用：
    // 每发强化烈焰弹消耗 1 点，64 点耗尽才碎掉，不是一发一碎。
    public static final Item WS_CALDERA_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_caldera_crystal"),
            new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.UNCOMMON)
                    .maxDamage(64))
    );

    // 终极核心：守卫核心 + 四枚裂冠残片合成
    public static final Item WS_ULTIMATE_CORE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_ultimate_core"),
            new Item(new Item.Settings())
    );

    // 冰之水晶：紫水晶碎片 + 一圈蓝冰合成，后续冰系武器的合成材料
    public static final Item WS_ICE_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_ice_crystal"),
            new Item(new Item.Settings())
    );

    // 残骸搜寻仪：右键扫描附近的太古残骸与下界合金残骸
    public static final Item WS_RELIC_SEEKER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_extracompass"),
            new org.examplee.wangsword.item.unique.RelicSeekerItem(new Item.Settings().maxCount(1))
    );

    // 磁铁：手持时吸附周围掉落物
    public static final Item WS_MAGNET = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_magnet"),
            new org.examplee.wangsword.item.unique.MagnetItem(new Item.Settings().maxCount(1))
    );
    // 合成材料：末地合金锭
    public static final Item WS_TAIGU_INGOT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_taigu_ingot"),
            new org.examplee.wangsword.item.unique.GlintItem(new Item.Settings())
    );

    // 升级锻造模板：末地合金
    public static final Item WS_TAIGU_UPGRADE_SMITHING_TEMPLATE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_taiguupgrade_smithing_template"),
            createTaiguUpgradeTemplate()
    );

    private static Item createTaiguUpgradeTemplate() {
        var vanilla = (net.minecraft.item.SmithingTemplateItem) Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        return new net.minecraft.item.SmithingTemplateItem(
                Text.translatable("item.wangsword.ws_taiguupgrade_smithing_template.applies_to"),
                Text.translatable("item.wangsword.ws_taiguupgrade_smithing_template.ingredients"),
                Text.translatable("item.wangsword.ws_taiguupgrade_smithing_template")
                        .formatted(net.minecraft.util.Formatting.GOLD),
                Text.translatable("item.wangsword.ws_taiguupgrade_smithing_template.base_slot_description"),
                Text.translatable("item.wangsword.ws_taiguupgrade_smithing_template.additions_slot_description"),
                vanilla.getEmptyBaseSlotTextures(),
                vanilla.getEmptyAdditionsSlotTextures()
        );
    }

    // 誓约锻造模板：誓约胜利之剑的专属模板
    public static final Item WS_EXCALIBUR_SMITHING_TEMPLATE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_excalibur_smithing_template"),
            createExcaliburTemplate()
    );

    private static Item createExcaliburTemplate() {
        var vanilla = (net.minecraft.item.SmithingTemplateItem) Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        return new net.minecraft.item.SmithingTemplateItem(
                Text.translatable("item.wangsword.ws_excalibur_smithing_template.applies_to"),
                Text.translatable("item.wangsword.ws_excalibur_smithing_template.ingredients"),
                Text.translatable("item.wangsword.ws_excalibur_smithing_template")
                        .formatted(net.minecraft.util.Formatting.GOLD),
                Text.translatable("item.wangsword.ws_excalibur_smithing_template.base_slot_description"),
                Text.translatable("item.wangsword.ws_excalibur_smithing_template.additions_slot_description"),
                vanilla.getEmptyBaseSlotTextures(),
                vanilla.getEmptyAdditionsSlotTextures()
        );
    }

    public static final Item WS_ENERGY_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_energycrystal"),
            new Item(new Item.Settings())
    );
    // 能源火箭：滑翔推进，消耗耐久不消耗自身
    public static final Item WS_ENERGY_ROCKET = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_energyrocket"),
            new org.examplee.wangsword.item.unique.EnergyRocketItem(
                    new Item.Settings().maxCount(1)
                            .maxDamage(org.examplee.wangsword.item.unique.EnergyRocketItem.DURABILITY))
    );
    // 合成材料：赤金锭
    public static final Item WS_REDGOLD_INGOT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgold_ingot"),
            new Item(new Item.Settings())
    );
    // 赤金饰品
    public static final Item WS_REDGOLD_NECKLACE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgold_necklace"),
            new org.examplee.wangsword.item.unique.RedGoldNecklaceItem(new Item.Settings().maxCount(1))
    );

    // 磁吸项链：戴在 necklace 槽位即获得磁铁效果
    public static final Item WS_MAGNET_NECKLACE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_magnet"),
            new org.examplee.wangsword.item.unique.MagnetNecklaceItem(new Item.Settings().maxCount(1))
    );

    public static final Item WS_REDGOLD_RING = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgold_ring"),
            new org.examplee.wangsword.item.unique.RedGoldRingItem(new Item.Settings().maxCount(1))
    );

    // 承魂项链：挨打攒层换攻击，攒够 4 层可抵一次致命伤
    public static final Item WS_NECKLACE_SOULBEARER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_soulbearer"),
            new org.examplee.wangsword.item.unique.SoulbearerNecklaceItem(new Item.Settings().maxCount(1))
    );

    // 猎魂项链：贴着同一目标叠印记，击杀时炸开
    public static final Item WS_NECKLACE_HUNTSOUL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_huntsoul"),
            new org.examplee.wangsword.item.unique.HuntsoulNecklaceItem(new Item.Settings().maxCount(1))
    );

    // 残响项链：大伤害拆成 4 秒 8 段结算
    public static final Item WS_NECKLACE_ECHO = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_echo"),
            new org.examplee.wangsword.item.unique.EchoNecklaceItem(new Item.Settings().maxCount(1))
    );

    // 迅锋项链：常驻攻速 + 冲刺时提速
    public static final Item WS_NECKLACE_SWIFTEDGE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_swiftedge"),
            new org.examplee.wangsword.item.unique.SwiftedgeNecklaceItem(new Item.Settings().maxCount(1))
    );

    // 保护项链：抗性提升 III + 一层不吃原版 80% 上限的真减伤
    public static final Item WS_NECKLACE_PROTECTION = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_necklace_protection"),
            new org.examplee.wangsword.item.unique.ProtectionNecklaceItem(new Item.Settings().maxCount(1))
    );

    // ── 护符（charm 槽）：一枚管一种伤害的免疫，最多同时戴 3 枚 ──
    // 符坯：空白底子，一枚赤金锭敲出来，没有功能，也装不进槽
    public static final Item WS_CHARM_BLANK = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_blank"),
            new org.examplee.wangsword.item.unique.CharmBlankItem(new Item.Settings().maxCount(16))
    );

    // 焚身符：免疫火焰与熔岩，着火时移动更快
    public static final org.examplee.wangsword.item.unique.WardCharmItem WS_CHARM_EMBER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_ember"),
            new org.examplee.wangsword.item.unique.WardCharmItem(
                    new Item.Settings().maxCount(1),
                    org.examplee.wangsword.item.unique.WardType.EMBER)
    );

    // 渊息符：免疫溺水与冻伤，水下不耗氧气
    public static final org.examplee.wangsword.item.unique.WardCharmItem WS_CHARM_TIDE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_tide"),
            new org.examplee.wangsword.item.unique.WardCharmItem(
                    new Item.Settings().maxCount(1),
                    org.examplee.wangsword.item.unique.WardType.TIDE)
    );

    // 坠星符：免疫坠落/撞墙/尖刺，高处落地砸出冲击波
    public static final org.examplee.wangsword.item.unique.WardCharmItem WS_CHARM_STARFALL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_starfall"),
            new org.examplee.wangsword.item.unique.WardCharmItem(
                    new Item.Settings().maxCount(1),
                    org.examplee.wangsword.item.unique.WardType.STARFALL)
    );

    // 雷殛符：免疫闪电，被劈中时把电弧甩给周围敌人
    public static final org.examplee.wangsword.item.unique.WardCharmItem WS_CHARM_THUNDER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_thunder"),
            new org.examplee.wangsword.item.unique.WardCharmItem(
                    new Item.Settings().maxCount(1),
                    org.examplee.wangsword.item.unique.WardType.THUNDER)
    );

    // 爆裂符：免疫爆炸，并把冲击推回去
    public static final org.examplee.wangsword.item.unique.WardCharmItem WS_CHARM_BLAST = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_charm_blast"),
            new org.examplee.wangsword.item.unique.WardCharmItem(
                    new Item.Settings().maxCount(1),
                    org.examplee.wangsword.item.unique.WardType.BLAST)
    );

    // 赤金胡萝卜：吃一次回 18 饥饿 / 20 饱和度，且永不消耗
    public static final Item WS_REDGOLDEN_CARROT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_carrot"),
            new org.examplee.wangsword.item.unique.RedGoldCarrotItem(new Item.Settings()
                    .maxCount(1)
                    .food(new net.minecraft.item.FoodComponent.Builder()
                            .hunger(org.examplee.wangsword.item.unique.RedGoldCarrotItem.HUNGER)
                            .saturationModifier(
                                    org.examplee.wangsword.item.unique.RedGoldCarrotItem.SATURATION_MODIFIER)
                            .alwaysEdible()
                            .build()))
    );

    // 未激活的雷电宝石：可放置成雷石，右键高压苦力怕可直接激活；不可堆叠
    public static final Item WS_NOLIGHTNINGSTONE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_nolightningstone"),
            new org.examplee.wangsword.block.LightningStoneBlockItem(
                    BlocksRegistry.WS_LIGHTNINGSTONE_BLOCK, new Item.Settings().maxCount(1))
    );

    // 雷电宝石：普通物品，发光由 MoreMcmeta 的叠加层提供
    public static final Item WS_LIGHTNINGSTONE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_lightningstone"),
            new Item(new Item.Settings())
    );

    // 超级雷电宝石：九颗雷电宝石压成，后期材料；拿在手上每秒被电一下
    public static final Item WS_SUPER_LIGHTNINGSTONE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_super_lightningstone"),
            new org.examplee.wangsword.item.SuperLightningStoneItem(new Item.Settings())
    );

    // 誓约胜利之剑：右键风王结界（飓风），X 键蓄力放出光炮
    public static final Item WS_EXCALIBUR = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_excalibur"),
            new org.examplee.wangsword.item.unique.ExcaliburItem(
                    CUSTOM_NETHERITE_MATERIAL, 15, -2.0F, new Item.Settings())
    );

    // 阿瓦隆：放到背包里就生效的剑鞘，Z 键展开理想乡
    public static final Item WS_AVALON = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_avalon"),
            new org.examplee.wangsword.item.unique.AvalonItem(new Item.Settings().maxCount(1))
    );

    // 赤金柄：合成材料
    public static final Item WS_REDGOLD_STICK = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgold_stick"),
            new Item(new Item.Settings())
    );

    // 赤金剑：材质比下界合金略高
    public static final Item WS_REDGOLDEN_SWORD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_sword"),
            new net.minecraft.item.SwordItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    3, -2.4F, new Item.Settings())
    );

    // ===================== 【石骸巨剑 · 未激活】 =====================
    // 从石中剑基座里拔出来的那把剑。还不是完整的剑冢巨剑，没有技能、没有激活效果，
    // 只能当普通大剑左键砍。总伤 = 1(空手) + 5 + 材料 9 = 15，攻速 1.1（和巨剑同档的慢）。
    public static final Item WS_STONE_BONE_GREATSWORD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_stone_bone_greatsword"),
            new net.minecraft.item.SwordItem(
                    org.examplee.wangsword.Material.SwordTombToolMaterial.INSTANCE,
                    5, -2.9F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.RARE)) {
                @Override
                public void appendTooltip(ItemStack stack, net.minecraft.world.World world,
                                          java.util.List<Text> tooltip,
                                          net.minecraft.client.item.TooltipContext context) {
                    tooltip.add(Text.translatable("tooltip.wangsword.stone_bone.dormant"));
                }
            }
    );

    // ===================== 【封岩巨剑 · 已激活】 =====================
    // 石骸巨剑插上大地之心、两侧各嵌一枚裂冠碎片，剑身的岩石裂开露出青蓝光。
    // 总伤 = 1(空手) + 10 + 材料 9 = 20；攻速和巨剑同档（1.1）。
    // 被动「碎岩」追加真实伤害，右键岩崩、X 封岩镇岳。
    public static final Item WS_SEALED_ROCK_GREATSWORD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_sealed_rock_greatsword"),
            new org.examplee.wangsword.item.unique.SealedRockGreatswordItem(
                    org.examplee.wangsword.Material.SwordTombToolMaterial.PLUS,
                    10, -2.9F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC))
    );

    // ===================== 【熔核封岩巨剑 · 升级】 =====================
    // 熔岩核心 + 封岩巨剑 + 两侧火焰水晶。熔岩灌满原本的青蓝裂纹。
    // 总伤 = 1(空手) + 16 + 材料 9 = 26；被动熔壳、右键熔核喷发、X 熔核崩解。
    public static final Item WS_MOLTEN_SEALED_GREATSWORD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_molten_sealed_greatsword"),
            new org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem(
                    org.examplee.wangsword.Material.SwordTombToolMaterial.PLUS,
                    16, -2.9F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC))
    );

    // ===================== 【剑冢巨剑 · 四阶】 =====================
    // 同一张贴图同一套模型，数值逐阶递增；两把同级在合成台合成下一阶。
    // 总伤 = 1(空手) + 额外伤害 + 材料 9 = 16 / 18 / 20 / 22
    public static final Item WS_SWORD_TOMB_GREATSWORD = registerSwordTombGreatsword(
            "sword_tomb_greatsword", 6, -2.9F, 0,
            net.minecraft.util.Rarity.RARE, org.examplee.wangsword.Material.SwordTombToolMaterial.INSTANCE);

    public static final Item WS_SWORD_TOMB_GREATSWORD_PLUS = registerSwordTombGreatsword(
            "sword_tomb_greatsword_plus", 8, -2.85F, 1,
            net.minecraft.util.Rarity.RARE, org.examplee.wangsword.Material.SwordTombToolMaterial.PLUS);

    public static final Item WS_SWORD_TOMB_GREATSWORD_PRO = registerSwordTombGreatsword(
            "sword_tomb_greatsword_pro", 10, -2.8F, 2,
            net.minecraft.util.Rarity.EPIC, org.examplee.wangsword.Material.SwordTombToolMaterial.PRO);

    public static final Item WS_SWORD_TOMB_GREATSWORD_PRO_MAX = registerSwordTombGreatsword(
            "sword_tomb_greatsword_pro_max", 12, -2.75F, 3,
            net.minecraft.util.Rarity.EPIC, org.examplee.wangsword.Material.SwordTombToolMaterial.PRO_MAX);

    // ===================== 【剑冢 · 守誓 · 第五阶（终阶）】 =====================
    // 上下左右各一枚守卫核心围住一把 Pro Max 锻成，攻防一体：
    // 右键格挡（UseAction.BLOCK）、X 键撑开剑冢结界、手持时常驻六把环伺之剑。
    // 总伤 = 1(空手) + 额外 14 + 材料 9 = 24；攻速 1.30。
    // 贴图暂时借 Pro Max 的，用户画好终阶贴图后只改 models/item/ws_sword_tomb_oath.json 一行。
    public static final Item WS_SWORD_TOMB_OATH = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_sword_tomb_oath"),
            new org.examplee.wangsword.item.unique.SwordTombOathItem(
                    org.examplee.wangsword.Material.SwordTombToolMaterial.PRO_MAX,
                    14, -2.7F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC))
    );
    // =========================================================================

    private static Item registerSwordTombGreatsword(String id, int attackDamage, float attackSpeed,
                                                    int tier, net.minecraft.util.Rarity rarity,
                                                    ToolMaterial material) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(Wangsword.MOD_ID, id),
                new org.examplee.wangsword.item.unique.SwordTombGreatswordItem(
                        material, attackDamage, attackSpeed, tier,
                        new Item.Settings().maxCount(1).rarity(rarity))
        );
    }
    // ==============================================================

    // ===================== 【烈火大刀 · 中期单手收集武器】 =====================
    // 纯数值武器，暂时没有技能，也还没有配方（等玩法定了再补 RecipeRegistry）。
    // 总伤 = 1(空手) + 追加 5 + 材料 3 = 9，攻速 1.40：比钻石剑(7 / 1.6)更狠更沉，
    // 又明显够不上赤金剑与剑冢系列，正好卡在中期收集档。
    public static final Item WS_FLAME_BLADE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_flame_blade"),
            new org.examplee.wangsword.item.unique.FlameBladeItem(
                    org.examplee.wangsword.Material.FlameBladeMaterial.INSTANCE,
                    12, -2.6F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.UNCOMMON))
    );

    // ===================== 【冰霜之刃 · 前期单手武器】 =====================
    // 总伤 = 1(空手) + 追加 4 + 材料 3 = 8，攻速 1.6：刚好比钻石剑(7 / 1.6)高一点。
    // 强度不在面板，在命中附带的 2 秒冰封（wangsword:frozen）。
    public static final Item WS_FROST_BLADE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_frost_blade"),
            new org.examplee.wangsword.item.unique.FrostBladeItem(
                    org.examplee.wangsword.Material.FrostBladeMaterial.INSTANCE,
                    4, -2.4F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.COMMON))
    );

    // ===================== 【冰霜法杖 · 远程法杖】 =====================
    // 纯远程：不继承 SwordItem，近战只有空手那点伤害。
    // 右键射出一道射程 48 格的冰霜光束（原版弓箭有效射程约 25 格），
    // 单发 10 点魔法伤害（无视护甲）+ 3 秒冰封，1.5 秒冷却。
    // 开火不扣耐久，频率全由冷却管；maxDamage 只为了不让它堆叠到 64、顺便留个以后
    // 想恢复磨损时的口子（未受损的耐久条不会显示，所以现在看不到那条杠）。
    public static final Item WS_FROST_STAFF = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_frost_staff"),
            new org.examplee.wangsword.item.unique.FrostStaffItem(
                    new Item.Settings().maxDamage(1000).rarity(net.minecraft.util.Rarity.UNCOMMON))
    );

    // ===================== 【雷电法杖 · 远程法杖】 =====================
    // 与冰霜法杖同源：右键射出一道紫电光束，单发 15 点雷伤 + 范围雷溅，
    // 2 秒冷却。X / 被动尚未定。
    public static final Item WS_THUNDER_STAFF = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_thunder_staff"),
            new org.examplee.wangsword.item.unique.ThunderStaffItem(
                    new Item.Settings().maxDamage(1000).rarity(net.minecraft.util.Rarity.RARE))
    );

    // ===================== 【火焰法杖 · 占位】 =====================
    // 技能和配方待定，先只把物品和贴图落地。
    public static final Item WS_FLAME_STAFF = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_flame_staff"),
            new org.examplee.wangsword.item.unique.FlameStaffItem(
                    new Item.Settings().maxDamage(1000).rarity(net.minecraft.util.Rarity.RARE))
    );

    // ===================== 【火焰水晶 · 材料】 =====================
    public static final Item WS_FLAME_CRYSTAL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_flame_crystal"),
            new Item(new Item.Settings())
    );

    // 赤金工具四件套：材质同赤金剑
    public static final Item WS_REDGOLDEN_PICKAXE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_pickaxe"),
            new net.minecraft.item.PickaxeItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    1, -2.8F, new Item.Settings())
    );

    public static final Item WS_REDGOLDEN_AXE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_axe"),
            new net.minecraft.item.AxeItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    5.0F, -3.0F, new Item.Settings())
    );

    public static final Item WS_REDGOLDEN_SHOVEL = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_shovel"),
            new net.minecraft.item.ShovelItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    1.5F, -3.0F, new Item.Settings())
    );

    public static final Item WS_REDGOLDEN_HOE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_redgolden_hoe"),
            new net.minecraft.item.HoeItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    -4, 0.0F, new Item.Settings())
    );

    // 物品组，先声明变量
    public static ItemGroup WANGSWORD_GROUP;

    // 统一注册入口，主类调用
    public static void registerAll() {
        // 【饰品】注册进 Accessories（不注册的话 getAccessory 查不到，物品放不进饰品槽）
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_REDGOLD_NECKLACE,
                (io.wispforest.accessories.api.Accessory) WS_REDGOLD_NECKLACE);
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_REDGOLD_RING,
                (io.wispforest.accessories.api.Accessory) WS_REDGOLD_RING);
        // 新两条项链：承魂 / 猎魂
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_NECKLACE_SOULBEARER,
                (io.wispforest.accessories.api.Accessory) WS_NECKLACE_SOULBEARER);
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_NECKLACE_HUNTSOUL,
                (io.wispforest.accessories.api.Accessory) WS_NECKLACE_HUNTSOUL);
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_NECKLACE_ECHO,
                (io.wispforest.accessories.api.Accessory) WS_NECKLACE_ECHO);
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_NECKLACE_SWIFTEDGE,
                (io.wispforest.accessories.api.Accessory) WS_NECKLACE_SWIFTEDGE);
        io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                WS_NECKLACE_PROTECTION,
                (io.wispforest.accessories.api.Accessory) WS_NECKLACE_PROTECTION);
        // 五枚护符：charm 槽，一种伤害一枚
        for (org.examplee.wangsword.item.unique.WardCharmItem charm
                : new org.examplee.wangsword.item.unique.WardCharmItem[]{
                        WS_CHARM_EMBER, WS_CHARM_TIDE, WS_CHARM_STARFALL,
                        WS_CHARM_THUNDER, WS_CHARM_BLAST}) {
            io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(
                    charm, (io.wispforest.accessories.api.Accessory) charm);
        }
        // 【药水戒指】注册所有效果戒指并挂进 Accessories
        EffectRingRegistry.init();
        // 【赤金盔甲】四件套
        RedGoldArmorRegistry.init();
        // 【末地合金盔甲】四件套
        TaiguArmorRegistry.init();
        // 在方法内注册ItemGroup
        WANGSWORD_GROUP = Registry.register(
                Registries.ITEM_GROUP,
                new Identifier(Wangsword.MOD_ID, "wangsword_group"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(WANG_SWORD))
                        .displayName(Text.translatable("itemgroup.wangsword_group"))
                        .entries((context, entries) -> {
                            // ── 1. 材料与方块 ──
                            entries.add(BlocksRegistry.RUNIC_FORGE_BLOCK);
                            entries.add(BlocksRegistry.WS_ARCHEAN_DEBRIS);
                            entries.add(BlocksRegistry.WS_PYRON_ORE);
                            entries.add(BlocksRegistry.WS_PYRON_ORE_NETHER);
                            entries.add(BlocksRegistry.STONE_SWORD_PEDESTAL);
                            entries.add(BlocksRegistry.MOLTEN_CORE_SUMMONER_ITEM);
                            entries.add(BlocksRegistry.WS_TAIGU_BLOCK);
                            entries.add(BlocksRegistry.WS_AMETHYST_BLOCK);
                            entries.add(BlocksRegistry.WS_REDGOLD_BLOCK);
                            entries.add(BlocksRegistry.SWORD_TOMB_ALTAR_ITEM);
                            entries.add(WS_AMETHYST_CRYSTAL);
                            entries.add(WS_AMETHYST_INGOT);
                            entries.add(WS_TAIGU_SHARD);
                            entries.add(WS_TAIGU_INGOT);
                            entries.add(WS_REDGOLD_INGOT);
                            entries.add(WS_REDGOLD_STICK);
                            entries.add(WS_WIND_CRYSTAL);
                            entries.add(WS_ENERGY_CRYSTAL);
                            entries.add(WS_DEATHCORESHARD);
                            entries.add(WS_DEATHCORE);
                            entries.add(WS_GUARDIANCORE);
                            entries.add(WS_CROWN_SHARD);
                            entries.add(WS_ULTIMATE_CORE);
                            entries.add(WS_EARTH_HEART);
                            entries.add(WS_MOLTEN_CORE);
                            entries.add(WS_MOLTEN_INGOT);
                            entries.add(WS_GHOST_INGOT);
                            entries.add(WS_CALDERA_CRYSTAL);
                            entries.add(WS_MOLTEN_BREATH);
                            entries.add(WS_PYRON_CRYSTAL);
                            entries.add(WS_HIGH_ENERGY_COAL);
                            entries.add(WS_HERB);
                            entries.add(WS_HERB_SEED);
                            entries.add(WS_HEALING_POTION);
                            entries.add(WS_GREATER_HEALING_POTION);
                            entries.add(WS_ICE_CRYSTAL);
                            entries.add(WS_FLAME_CRYSTAL);
                            entries.add(WS_UPGRADE_SMITHING_TEMPLATE);
                            entries.add(WS_TAIGU_UPGRADE_SMITHING_TEMPLATE);
                            entries.add(WS_EXCALIBUR_SMITHING_TEMPLATE);

                            // ── 2. 饰品与特殊道具 ──
                            entries.add(WS_REDGOLD_NECKLACE);
                            entries.add(WS_MAGNET_NECKLACE);
                            entries.add(WS_NECKLACE_SOULBEARER);
                            entries.add(WS_NECKLACE_HUNTSOUL);
                            entries.add(WS_NECKLACE_ECHO);
                            entries.add(WS_NECKLACE_SWIFTEDGE);
                            entries.add(WS_NECKLACE_PROTECTION);
                            entries.add(WS_CHARM_BLANK);
                            entries.add(WS_CHARM_EMBER);
                            entries.add(WS_CHARM_TIDE);
                            entries.add(WS_CHARM_STARFALL);
                            entries.add(WS_CHARM_THUNDER);
                            entries.add(WS_CHARM_BLAST);
                            entries.add(WS_REDGOLD_RING);
                            EffectRingRegistry.ALL.forEach(entries::add);
                            entries.add(WS_REDGOLDEN_CARROT);
                            entries.add(WS_ENERGY_ROCKET);
                            entries.add(WS_MAGNET);
                            entries.add(WS_RELIC_SEEKER);
                            entries.add(WS_LIGHTNINGSTONE);
                            entries.add(WS_SUPER_LIGHTNINGSTONE);
                            entries.add(WS_NOLIGHTNINGSTONE);

                            // ── 3. 特殊武器 ──
                            entries.add(WANG_SWORD);
                            entries.add(TridentRegistry.MY_TRIDENT);
                            entries.add(BowRegistery.WS_BOW);
                            entries.add(BowRegistery.FATALIS_CROSSBOW);
                            entries.add(LanceRegistry.WS_LANCE);
                            entries.add(HammerRegistry.WS_GREATHAMMER);
                            entries.add(ShieldRegistry.WS_SHIELD);
                            entries.add(ScytheRegistry.WS_DEATHSCYTHE);
                            entries.add(DaggerRegistry.WS_POISONDAGGER);
                            entries.add(WitherKillerRegistry.WS_WITHERSPAWN_KILLER);
                            entries.add(MobEggRegistry.SWORD_TOMB_GUARDIAN_EGG);
                            entries.add(MobEggRegistry.GHOST_CROWN_WRAITH_EGG);
                            entries.add(MobEggRegistry.CINDER_COLOSSUS_EGG);
                            entries.add(MobEggRegistry.CALDERA_BAT_EGG);
                            entries.add(WS_STONE_BONE_GREATSWORD);
                            entries.add(WS_SEALED_ROCK_GREATSWORD);
                            entries.add(WS_MOLTEN_SEALED_GREATSWORD);
                            entries.add(WS_SWORD_TOMB_GREATSWORD);
                            entries.add(WS_SWORD_TOMB_GREATSWORD_PLUS);
                            entries.add(WS_SWORD_TOMB_GREATSWORD_PRO);
                            entries.add(WS_SWORD_TOMB_GREATSWORD_PRO_MAX);
                            entries.add(WS_SWORD_TOMB_OATH);
                            entries.add(WS_EXCALIBUR);
                            entries.add(WS_AVALON);
                            entries.add(WS_FLAME_BLADE);
                            entries.add(WS_FROST_BLADE);
                            entries.add(WS_FROST_STAFF);
                            entries.add(WS_THUNDER_STAFF);
                            entries.add(WS_FLAME_STAFF);
                            entries.add(ToolRegistry.WS_THUNDER_KATANA);
                            entries.add(ToolRegistry.WS_BLOODSAIL_CUTLASS);

                            // ── 4. 赤金 ──
                            entries.add(WS_REDGOLDEN_SWORD);
                            entries.add(WS_REDGOLDEN_PICKAXE);
                            entries.add(WS_REDGOLDEN_AXE);
                            entries.add(WS_REDGOLDEN_SHOVEL);
                            entries.add(WS_REDGOLDEN_HOE);
                            entries.add(RedGoldArmorRegistry.REDGOLD_HELMET);
                            entries.add(RedGoldArmorRegistry.REDGOLD_CHESTPLATE);
                            entries.add(RedGoldArmorRegistry.REDGOLD_LEGGINGS);
                            entries.add(RedGoldArmorRegistry.REDGOLD_BOOTS);

                            // ── 5. 紫金 ──
                            entries.add(ToolRegistry.WS_PURPLE_SWORD);
                            entries.add(ToolRegistry.WS_PURPLE_PICKAXE);
                            entries.add(ToolRegistry.WS_PURPLE_AXE);
                            entries.add(ToolRegistry.WS_PURPLE_SHOVEL);
                            entries.add(ToolRegistry.WS_PURPLE_HOE);
                            entries.add(ArmorRegistry.WSARMOR_HELMET);
                            entries.add(ArmorRegistry.WSARMOR_CHESTPLATE);
                            entries.add(ArmorRegistry.WSARMOR_LEGGINGS);
                            entries.add(ArmorRegistry.WSARMOR_BOOTS);
                            entries.add(ArmorRegistry.CALDERA_HELM);
                            entries.add(ArmorRegistry.CALDERA_CUIRASS);
                            entries.add(ArmorRegistry.CALDERA_GREAVES);
                            entries.add(ArmorRegistry.CALDERA_BOOTS);
                            entries.add(ArmorRegistry.FULGUR_HELM);
                            entries.add(ArmorRegistry.FULGUR_CUIRASS);
                            entries.add(ArmorRegistry.FULGUR_GREAVES);
                            entries.add(ArmorRegistry.FULGUR_BOOTS);

                            // ── 6. 末地合金 ──
                            entries.add(ToolRegistry.WS_TAIGU_SWORD);
                            entries.add(ToolRegistry.WS_TAIGU_PICKAXE);
                            entries.add(ToolRegistry.WS_TAIGU_AXE);
                            entries.add(ToolRegistry.WS_TAIGU_SHOVEL);
                            entries.add(ToolRegistry.WS_TAIGU_HOE);
                            entries.add(TaiguArmorRegistry.TAIGU_HELMET);
                            entries.add(TaiguArmorRegistry.TAIGU_CHESTPLATE);
                            entries.add(TaiguArmorRegistry.TAIGU_LEGGINGS);
                            entries.add(TaiguArmorRegistry.TAIGU_BOOTS);
                        })
                        .build()
        );
    }
}
