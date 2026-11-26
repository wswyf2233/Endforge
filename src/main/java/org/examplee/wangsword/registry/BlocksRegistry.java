package org.examplee.wangsword.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.examplee.wangsword.Wangsword;

public class BlocksRegistry {

    public static final Block RUNIC_FORGE_BLOCK = registerBlock("runic_forge_block",
            new Block(AbstractBlock.Settings.create()
                    .strength(50f, 1200f)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 8)
            )
    );

    // 太古残骸：类似远古残骸，只生成在末地
    public static final Block WS_ARCHEAN_DEBRIS = registerBlock("ws_archean_debris",
            new Block(AbstractBlock.Settings.create()
                    .strength(30.0F, 1200.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.ANCIENT_DEBRIS)
                    .luminance(state -> 13)
            )
    );
    // 末地合金块：会发光
    public static final Block WS_TAIGU_BLOCK = registerBlock("ws_taigu_block",
            new Block(AbstractBlock.Settings.create()
                    .strength(50.0F, 1200.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.NETHERITE)
                    .luminance(state -> 12)
            )
    );

    // 紫金块
    public static final Block WS_AMETHYST_BLOCK = registerBlock("ws_amethyst_block",
            new Block(AbstractBlock.Settings.create()
                    .strength(50.0F, 1200.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.NETHERITE)
            )
    );
    // 赤金块：9 个赤金锭压成一块
    public static final Block WS_REDGOLD_BLOCK = registerBlock("ws_redgold_block",
            new Block(AbstractBlock.Settings.create()
                    .strength(5.0F, 6.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.METAL)
            )
    );

    // 雷石：未激活雷电宝石放置后的形态
    public static final Block WS_LIGHTNINGSTONE_BLOCK = Registry.register(
            Registries.BLOCK,
            new Identifier(Wangsword.MOD_ID, "ws_lightningstone"),
            new org.examplee.wangsword.block.LightningStoneBlock(AbstractBlock.Settings.create()
                    .strength(0.5F)
                    .luminance(state -> 7)
                    .nonOpaque()
                    .sounds(net.minecraft.sound.BlockSoundGroup.AMETHYST_BLOCK)
            )
    );

    // 剑冢祭坛：把钻石剑献祭上去，唤出一只剑冢守卫
    // 注意：这里不能用 registerBlock（它会顺手注册一个普通 BlockItem），
    // 祭坛的物品必须是 SwordTombAltarBlockItem（实现了 GeoItem），否则手里拿到的又是一个方块。
    public static final Block SWORD_TOMB_ALTAR = Registry.register(
            Registries.BLOCK,
            new Identifier(Wangsword.MOD_ID, "sword_tomb_altar"),
            new org.examplee.wangsword.block.SwordTombAltarBlock(AbstractBlock.Settings.create()
                    // 硬度 -1：和基岩一样挖不动，祭坛是剑冢的固定节点，不能被搬走
                    .strength(-1.0F, 3600000.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.NETHERITE)
                    .luminance(state -> 9)
            )
    );

    /** 祭坛物品：走 GeckoLib 的 geo 模型，和放下来之后的样子完全一致 */
    public static final Item SWORD_TOMB_ALTAR_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "sword_tomb_altar"),
            new org.examplee.wangsword.block.SwordTombAltarBlockItem(SWORD_TOMB_ALTAR, new Item.Settings())
    );

    /** 祭坛的方块实体：承载 GeckoLib 的 idle / summon 动画和召唤倒计时 */
    public static final BlockEntityType<org.examplee.wangsword.block.SwordTombAltarBlockEntity> SWORD_TOMB_ALTAR_BE =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(Wangsword.MOD_ID, "sword_tomb_altar"),
                    BlockEntityType.Builder.create(
                            org.examplee.wangsword.block.SwordTombAltarBlockEntity::new,
                            SWORD_TOMB_ALTAR).build(null)
            );

    // 燃素矿：主世界深层（y < 0）与下界都能生成，挖出燃素结晶。掉落逻辑与煤矿一致
    public static final Block WS_PYRON_ORE = registerBlock("ws_pyron_ore",
            new net.minecraft.block.ExperienceDroppingBlock(
                    AbstractBlock.Settings.create()
                            .strength(3.0F, 3.0F)
                            .requiresTool()
                            .sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE)
                            .luminance(state -> 4),
                    net.minecraft.util.math.intprovider.UniformIntProvider.create(1, 3))
    );

    // 下界燃素矿：下界岩底，同一个东西，只是岩层不同
    public static final Block WS_PYRON_ORE_NETHER = registerBlock("ws_pyron_ore_nether",
            new net.minecraft.block.ExperienceDroppingBlock(
                    AbstractBlock.Settings.create()
                            .strength(3.0F, 3.0F)
                            .requiresTool()
                            .sounds(net.minecraft.sound.BlockSoundGroup.NETHER_ORE)
                            .luminance(state -> 4),
                    net.minecraft.util.math.intprovider.UniformIntProvider.create(1, 3))
    );

    // 石中剑基座：插着一把剑的石头，空手右键拔出来（一次性）
    public static final Block STONE_SWORD_PEDESTAL = registerBlock("stone_sword_pedestal",
            new org.examplee.wangsword.block.StoneSwordPedestalBlock(AbstractBlock.Settings.create()
                    .strength(1.5F, 6.0F)
                    .requiresTool()
                    .sounds(net.minecraft.sound.BlockSoundGroup.STONE)
                    .luminance(state -> 5)
            )
    );

    /** 石中剑基座的方块实体：存那件插在石头里的物品，供渲染器画出真剑 */
    public static final BlockEntityType<org.examplee.wangsword.block.StoneSwordPedestalBlockEntity> STONE_SWORD_PEDESTAL_BE =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(Wangsword.MOD_ID, "stone_sword_pedestal"),
                    BlockEntityType.Builder.create(
                            org.examplee.wangsword.block.StoneSwordPedestalBlockEntity::new,
                            STONE_SWORD_PEDESTAL).build(null)
            );

    // 熔核唤石：熔核沉坛正中央那块石头，右键召唤 BOSS。
    // 硬度 -1 和基岩同档，生存模式挖不掉；外形完全由 GeckoLib 的 geo 模型画，
    // blockstate 指向的是没有 elements 的占位模型。
    public static final Block MOLTEN_CORE_SUMMONER = Registry.register(
            Registries.BLOCK,
            new Identifier(Wangsword.MOD_ID, "molten_core_summoner"),
            new org.examplee.wangsword.block.MoltenCoreSummonerBlock(AbstractBlock.Settings.create()
                    .strength(-1.0F, 3600000.0F)
                    .sounds(net.minecraft.sound.BlockSoundGroup.BASALT)
                    .luminance(state -> 10)
            )
    );

    /**
     * 物品必须用自定义的 BlockItem：它实现了 GeoItem，物品栏里才能渲染 geo 模型，
     * 而不是一张 16×16 平面图。所以这里不能用 registerBlock，那条路只会生成普通 BlockItem。
     */
    public static final Item MOLTEN_CORE_SUMMONER_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "molten_core_summoner"),
            new org.examplee.wangsword.block.MoltenCoreSummonerBlockItem(
                    MOLTEN_CORE_SUMMONER, new Item.Settings())
    );

    /** 熔核唤石的方块实体：承载 idle / summon 两段动画和召唤冷却 */
    public static final BlockEntityType<org.examplee.wangsword.block.MoltenCoreSummonerBlockEntity> MOLTEN_CORE_SUMMONER_BE =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(Wangsword.MOD_ID, "molten_core_summoner"),
                    BlockEntityType.Builder.create(
                            org.examplee.wangsword.block.MoltenCoreSummonerBlockEntity::new,
                            MOLTEN_CORE_SUMMONER).build(null)
            );

    // 药草：草状植物，掉落药草与种子。物品是单独的 WS_HERB_SEED（BlockItem），
    // 所以这里不能用 registerBlock，否则会多出一个和药草物品重名的方块物品。
    public static final Block WS_HERB = Registry.register(
            Registries.BLOCK,
            new Identifier(Wangsword.MOD_ID, "ws_herb"),
            new org.examplee.wangsword.block.HerbBlock(AbstractBlock.Settings.create()
                    .noCollision()
                    .breakInstantly()
                    .ticksRandomly()
                    .sounds(net.minecraft.sound.BlockSoundGroup.GRASS)
                    .offset(AbstractBlock.OffsetType.XZ)
                    .nonOpaque()
                    .replaceable()
            )
    );

    private static <T extends Block> T registerBlock(String blockId, T block) {        Registry.register(Registries.BLOCK, new Identifier(Wangsword.MOD_ID, blockId), block);
        Registry.register(Registries.ITEM, new Identifier(Wangsword.MOD_ID, blockId),
                new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void register() {
        Wangsword.LOGGER.info("Registering Custom Blocks");
    }
}
