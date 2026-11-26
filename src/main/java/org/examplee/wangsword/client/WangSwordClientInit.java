package org.examplee.wangsword.client;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.item.unique.LanceItem;
import org.examplee.wangsword.item.unique.WangShadowSwordItem;
import org.examplee.wangsword.registry.EntityRegistry;
import org.lwjgl.glfw.GLFW;

public class WangSwordClientInit implements ClientModInitializer {

    /** 本地冷却，避免每个 tick 都发包 */
    private int fireCooldown = 0;

    /** 黑龙弩的本地开火节奏 */
    private int fatalisCooldown = 0;

    /** X 键：已按住的 tick 数 */
    private int ultHold = 0;
    /** 上一次记录的技能武器，用于检测换手 */
    private net.minecraft.item.Item lastUltItem = null;
    /** 上一 tick X 键是否处于按下状态（自己算边沿，避开按键重复） */
    private boolean lastUltDown = false;
    /** 誓约胜利之剑本次蓄力是否已经自动发射过（避免松手再补一刀弱化版） */
    private boolean excaliburAutoFired = false;
    /** 誓约胜利之剑的蓄力音效是否正在播放（提前松手时掐断） */
    private boolean excaliburCharging = false;
    /** 烟花推进节流 */
    private int fireworkCooldown = 0;
    /** 上一 tick 跳跃键是否按下（自己算边沿，避免吃掉普通起跳） */
    private boolean lastJumpDown = false;

    /** 连续离地的 tick 数（防止起跳那一下被当成滑翔触发） */
    private int airTicks = 0;

    /** 自实现滑翔（原版会打断自定义胸甲，只能自己做物理） */
    private boolean glideMode = false;

    /** 能源火箭剩余推进 tick（客户端结算） */

    /** 滑翔请求的本地节流 */
    private int elytraCooldown = 0;

    /** 战锤 X 蓄力时是否由我们强制进入了使用姿势 */
    private static boolean hammerCharging = false;
    private static KeyBinding ultKey;
    private static KeyBinding aoeKey;
    private static KeyBinding avalonKey;
    private static KeyBinding armorSkillKey;
    private boolean lastAoeDown = false;
    /** 雷电法杖左键放电：自己算按下边沿 */
    private boolean lastCageAttackDown = false;

    // 冷却图标要读「玩家当前绑的是哪个键」，但它在按键注册之前就注册了配置，
    // 那时这些字段还是 null。所以对外只暴露访问器，画的时候现取。

    /** X 键（可改）的绑定对象；未初始化时返回 null，调用方自行兜底 */
    public static KeyBinding ultimateKeyBinding() {
        return ultKey;
    }

    public static KeyBinding aoeKeyBinding() {
        return aoeKey;
    }

    public static KeyBinding avalonKeyBinding() {
        return avalonKey;
    }

    /** G 键：熔岩套「熔岩喷发」（可改） */
    public static KeyBinding armorSkillKeyBinding() {
        return armorSkillKey;
    }

    @Override
    public void onInitializeClient() {
        // 通用技能冷却图标（物品栏右侧）+ 各武器的技能显示配置。
        // 一件武器想挂几个技能就写几行；显示顺序 = 注册顺序，所以按「右键 → X」排。
        // 键位用 KeyRef 声明（USE = 右键、ULT = X），画的时候读玩家的实时绑定，写死会错。
        SkillCooldownHud.init();
        SkillCooldownHud.register(org.examplee.wangsword.registry.BowRegistery.WS_BOW,
                org.examplee.wangsword.item.unique.WangBowItem.SKILL_SURGE, "雷涌",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.WangBowItem.SURGE_COOLDOWN, 0xFF39E0CC);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ToolRegistry.WS_THUNDER_KATANA,
                org.examplee.wangsword.util.ThunderKatanaHandler.SKILL_VOLLEY, "万雷",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.util.ThunderKatanaHandler.VOLLEY_COOLDOWN, 0xFF8E7CFF);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ToolRegistry.WS_THUNDER_KATANA,
                org.examplee.wangsword.util.ThunderKatanaHandler.SKILL_WRATH, "雷怒",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.util.ThunderKatanaHandler.WRATH_COOLDOWN, 0xFFC36BFF);
        // 火刀：右键烈焰十字 / X 烈火喷射
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FLAME_BLADE,
                org.examplee.wangsword.util.FlameBladeHandler.SKILL_PILLAR, "烈焰十字",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.util.FlameBladeHandler.PILLAR_COOLDOWN, 0xFFFF7A3D);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FLAME_BLADE,
                org.examplee.wangsword.util.FlameBladeHandler.SKILL_JET, "烈火喷射",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.util.FlameBladeHandler.JET_COOLDOWN, 0xFFFFB13D);
        // 冰刀：右键霜环 / X 冰棱穿刺
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FROST_BLADE,
                org.examplee.wangsword.util.FrostBladeHandler.SKILL_NOVA, "霜环",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.util.FrostBladeHandler.NOVA_COOLDOWN, 0xFF5FC8FF);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FROST_BLADE,
                org.examplee.wangsword.util.FrostBladeHandler.SKILL_SPIKE, "冰棱穿刺",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.util.FrostBladeHandler.SPIKE_COOLDOWN, 0xFF9FE6FF);
        // 冰霜法杖：X 绝对零度
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FROST_STAFF,
                org.examplee.wangsword.item.unique.FrostStaffItem.SKILL_ULT, "绝对零度",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.FrostStaffItem.ULT_COOLDOWN, 0xFF8FE3FF);
        // 雷电法杖：右键紫电射线 / X 雷锁囚笼
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_THUNDER_STAFF,
                org.examplee.wangsword.item.unique.ThunderStaffItem.SKILL_BEAM, "紫电射线",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.ThunderStaffItem.COOLDOWN, 0xFFB57CFF);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_THUNDER_STAFF,
                org.examplee.wangsword.item.unique.ThunderStaffItem.SKILL_CAGE, "雷锁囚笼",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.ThunderStaffItem.ULT_COOLDOWN, 0xFFD279FF);
        // 火焰法杖：X 熔火领域（右键 0.8 秒太短，不上图标）
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_FLAME_STAFF,
                org.examplee.wangsword.item.unique.FlameStaffItem.SKILL_FIELD, "熔火领域",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.FlameStaffItem.ULT_COOLDOWN, 0xFFFF6A2D);
        // 死镰：右键灵魂收割 / X 血契（原先共用一个物品冷却槽，放完血契右键要等 20 秒）
        SkillCooldownHud.register(org.examplee.wangsword.registry.ScytheRegistry.WS_DEATHSCYTHE,
                org.examplee.wangsword.item.unique.DeathScytheItem.SKILL_REAP, "灵魂收割",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.DeathScytheItem.ULT_COOLDOWN, 0xFFB03040);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ScytheRegistry.WS_DEATHSCYTHE,
                org.examplee.wangsword.item.unique.DeathScytheItem.SKILL_PACT, "血契",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.DeathScytheItem.PACT_COOLDOWN, 0xFFE05050);
        // 血帆弯刀：右键突进 / X 血怒
        SkillCooldownHud.register(org.examplee.wangsword.registry.ToolRegistry.WS_BLOODSAIL_CUTLASS,
                org.examplee.wangsword.item.unique.BloodsailCutlassItem.SKILL_RUSH, "血刃突袭",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.BloodsailCutlassItem.RUSH_COOLDOWN, 0xFFD04848);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ToolRegistry.WS_BLOODSAIL_CUTLASS,
                org.examplee.wangsword.item.unique.BloodsailCutlassItem.SKILL_FURY, "血债",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.BloodsailCutlassItem.FURY_COOLDOWN, 0xFFFF6A6A);
        // 封岩巨剑：右键岩崩 / X 封岩镇岳
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_SEALED_ROCK_GREATSWORD,
                org.examplee.wangsword.item.unique.SealedRockGreatswordItem.SKILL_ROCKFALL, "岩崩",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.SealedRockGreatswordItem.ROCKFALL_COOLDOWN, 0xFF7FC4E8);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_SEALED_ROCK_GREATSWORD,
                org.examplee.wangsword.item.unique.SealedRockGreatswordItem.SKILL_SEAL, "封岩镇岳",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.SealedRockGreatswordItem.SEAL_COOLDOWN, 0xFF4E9CC8);
        // 熔核封岩巨剑：右键熔核喷发 / X 熔核崩解
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_MOLTEN_SEALED_GREATSWORD,
                org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem.SKILL_ERUPTION, "熔核喷发",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem.ERUPTION_COOLDOWN, 0xFFFF7A2D);
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_MOLTEN_SEALED_GREATSWORD,
                org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem.SKILL_BREAK, "熔核崩解",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem.BREAK_COOLDOWN, 0xFFE8401C);
        // 骑枪：X 冲锋（右键的蓄力突进只有 0.5 秒防抖，不占图标）
        SkillCooldownHud.register(org.examplee.wangsword.registry.LanceRegistry.WS_LANCE,
                org.examplee.wangsword.item.unique.LanceItem.SKILL_CHARGE, "骑士冲锋",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.LanceItem.ULT_COOLDOWN, 0xFF9FB4C8);
        // 三叉戟：X 雷霆风暴
        SkillCooldownHud.register(org.examplee.wangsword.registry.TridentRegistry.MY_TRIDENT,
                org.examplee.wangsword.item.unique.MyTridentItem.SKILL_THUNDER, "雷霆风暴",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.MyTridentItem.SKILL_COOLDOWN, 0xFF4A7BD0);
        // 泰古剑：X 姿态
        SkillCooldownHud.register(org.examplee.wangsword.registry.ToolRegistry.WS_TAIGU_SWORD,
                org.examplee.wangsword.item.unique.TaiguSwordItem.SKILL_STANCE, "太古道·姿态",
                SkillCooldownHud.KeyRef.ULT,
                org.examplee.wangsword.item.unique.TaiguSwordItem.ULT_COOLDOWN, 0xFFC8A24A);
        // 遗物探寻：右键扫描
        SkillCooldownHud.register(org.examplee.wangsword.registry.ItemsRegistry.WS_RELIC_SEEKER,
                org.examplee.wangsword.item.unique.RelicSeekerItem.SKILL_SEEK, "远古探寻",
                SkillCooldownHud.KeyRef.USE,
                org.examplee.wangsword.item.unique.RelicSeekerItem.COOLDOWN, 0xFF4FD1C5);
        // 剑冢巨剑四阶共用一个类，注册要覆盖全部四个物品
        for (net.minecraft.item.Item tomb : new net.minecraft.item.Item[]{
                org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD,
                org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PLUS,
                org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO,
                org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO_MAX}) {
            SkillCooldownHud.register(tomb,
                    org.examplee.wangsword.item.unique.SwordTombGreatswordItem.SKILL_SENTINEL, "环伺之剑",
                    SkillCooldownHud.KeyRef.USE,
                    org.examplee.wangsword.item.unique.SwordTombGreatswordItem.SENTINEL_COOLDOWN, 0xFFB8C4D6);
            SkillCooldownHud.register(tomb,
                    org.examplee.wangsword.item.unique.SwordTombGreatswordItem.SKILL_ULT, "万剑倾泻",
                    SkillCooldownHud.KeyRef.ULT,
                    org.examplee.wangsword.item.unique.SwordTombGreatswordItem.ULT_COOLDOWN, 0xFFE8EDF5);
        }
        // 阿瓦隆：背包道具，按 Z 展开。不绑手持物品，只在 90 秒冷却中显示
        SkillCooldownHud.registerGlobal(
                org.examplee.wangsword.util.AvalonHandler.SKILL_AVALON, "阿瓦隆",
                SkillCooldownHud.KeyRef.AVALON,
                org.examplee.wangsword.util.AvalonHandler.COOLDOWN, 0xFFFFD86B);
        // 熔岩套：穿齐四件后按 G（可改）放「熔岩喷发」，同样只在冷却中占位
        SkillCooldownHud.registerGlobal(
                org.examplee.wangsword.util.MoltenSetHandler.SKILL_ERUPTION, "熔岩喷发",
                SkillCooldownHud.KeyRef.ARMOR,
                org.examplee.wangsword.util.MoltenSetHandler.ERUPTION_COOLDOWN, 0xFFFF6A1E);
        // 幽卫钢套：同一个键，穿齐幽卫钢四件时按 G 打「幽卫飞剑」
        SkillCooldownHud.registerGlobal(
                org.examplee.wangsword.util.FulgurSetHandler.SKILL_VOLLEY, "幽卫飞剑",
                SkillCooldownHud.KeyRef.ARMOR,
                org.examplee.wangsword.util.FulgurSetHandler.VOLLEY_COOLDOWN, 0xFF6BE3FF);

        // 只注册渲染器，不需要注册模型层了
        EntityRendererRegistry.register(EntityRegistry.ARC_SLASH, ArcSlashEntityRenderer::new);
        // 誓约胜利之剑的金色剑气：固定金色贴图
        EntityRendererRegistry.register(EntityRegistry.ARC_SLASH_GOLD,
                context -> new ArcSlashEntityRenderer(context, ArcSlashEntityRenderer.GOLD_TEXTURE));
        // 风王结界的风刃：固定风纹理
        EntityRendererRegistry.register(EntityRegistry.ARC_SLASH_WIND,
                context -> new ArcSlashEntityRenderer(context, ArcSlashEntityRenderer.WIND_TEXTURE));
        // 誓约胜利之剑的金色爆炸光柱
        EntityRendererRegistry.register(EntityRegistry.GOLDEN_BURST,
                org.examplee.wangsword.client.GoldenBurstRenderer::new);
        // 风王结界的蓝色风筒：同一套圆柱渲染，换蓝色贴图并沿视线横放
        // 生命只给 10 tick：靠每 5 tick 重生一根，这样转头时风筒会跟着准星走
        EntityRendererRegistry.register(EntityRegistry.WIND_BURST,
                context -> new org.examplee.wangsword.client.GoldenBurstRenderer(
                        context, ArcSlashEntityRenderer.WIND_TEXTURE, true, 10.0F));
        // 烈火大刀的通天火柱：自绘火焰贴图铺成圆柱，贴图向上滚动
        EntityRendererRegistry.register(EntityRegistry.FLAME_PILLAR, FlamePillarRenderer::new);
        // 烈火大刀的烈火喷射：同一张火焰贴图，横放成朝准星喷出去的火锥
        EntityRendererRegistry.register(EntityRegistry.FLAME_JET, FlameJetRenderer::new);
        // 火焰法杖的烈焰弹：正对镜头的火团面片
        EntityRendererRegistry.register(EntityRegistry.FLAME_BOLT, FlameBoltRenderer::new);
        // 火焰法杖的火地 / 熔火领域：贴地圆盘（焦土 / 法阵两种贴图）
        EntityRendererRegistry.register(EntityRegistry.FLAME_PATCH, FlamePatchRenderer::new);
        // 冰霜法杖的冰冻光束：一条细光柱，贴图沿长度滚动
        EntityRendererRegistry.register(EntityRegistry.FROST_BEAM, FrostBeamRenderer::new);
        // 雷电法杖的紫电光束：紫雷贴图铺成抖动的电弧，独立于冰霜链路
        EntityRendererRegistry.register(EntityRegistry.THUNDER_BEAM, ThunderBeamRenderer::new);
        // 熔核巨兽的火焰激光：橙红贴图、不透明硬切，attack2 更粗
        EntityRendererRegistry.register(EntityRegistry.CINDER_BEAM, CinderBeamRenderer::new);
        // 冰霜之刃的霜环：贴地铺一张环形贴图，随 age 扩散并淡出
        EntityRendererRegistry.register(EntityRegistry.FROST_NOVA, FrostNovaRenderer::new);
        // 冰霜之刃的冰棱穿刺：十字交叉平面，按飞行方向拉长
        EntityRendererRegistry.register(EntityRegistry.FROST_SPIKE, FrostSpikeRenderer::new);
        // 雷电太刀 · 雷怒的脚下雷环：贴地一圈电环，跟脚走、脉冲呼吸
        EntityRendererRegistry.register(EntityRegistry.THUNDER_RING, ThunderRingRenderer::new);
        // 紫电 / 蓝电：雷电太刀与雷霆三叉戟的落雷，用两张自绘雷柱贴图代替原版白线
        EntityRendererRegistry.register(EntityRegistry.THUNDER_BOLT, ThunderBoltRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.FATALIS_CANNON, FatalisCannonRenderer::new);        EntityRendererRegistry.register(EntityRegistry.FATALIS_SHELL, FatalisShellRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.FATALIS_BOLT, FatalisBoltRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.HAMMER_SHOCKWAVE, HammerShockwaveRenderer::new);
        // 熔核巨兽 melee4 的火焰冲击波：贴地双环 + 一圈竖直火舌
        EntityRendererRegistry.register(EntityRegistry.FLAME_SHOCKWAVE, FlameShockwaveRenderer::new);
        // 熔核巨兽 attack3 的火球：十字火团面片 + 加色辉光
        EntityRendererRegistry.register(EntityRegistry.CINDER_FIREBALL, CinderFireballRenderer::new);
        // 熔核封岩巨剑插地的剑：直接画物品模型并倒转 180°
        EntityRendererRegistry.register(EntityRegistry.MOLTEN_BLADE, MoltenBladeRenderer::new);
        // 凋灵杀手的飞剑：直接画物品模型
        EntityRendererRegistry.register(EntityRegistry.FLYING_SWORD, FlyingSwordEntityRenderer::new);
        // 雷霆贯穿：右键扔出去的三叉戟飞行体
        EntityRendererRegistry.register(EntityRegistry.THUNDER_TRIDENT, ThunderTridentRenderer::new);
        // 剑冢巨剑的环伺之剑：也是画物品模型，但要立着绕轴转
        EntityRendererRegistry.register(EntityRegistry.SENTINEL_SWORD, SentinelSwordRenderer::new);
        // 剑冢守卫：GeckoLib 渲染
        EntityRendererRegistry.register(EntityRegistry.SWORD_TOMB_GUARDIAN, SwordTombGuardianRenderer::new);
        // 裂冠幽灵：GeckoLib 渲染
        EntityRendererRegistry.register(EntityRegistry.GHOST_CROWN_WRAITH, GhostCrownWraithRenderer::new);
        // 熔岩蝠怪：下界熔核沉坛的飞行精英怪
        EntityRendererRegistry.register(EntityRegistry.CALDERA_BAT, CalderaBatRenderer::new);
        // 熔核巨兽：GeckoLib 渲染
        EntityRendererRegistry.register(EntityRegistry.CINDER_COLOSSUS, CinderColossusRenderer::new);
        // 裂冠幽灵的光弹：一张正对镜头的光团面片
        EntityRendererRegistry.register(EntityRegistry.GHOST_ORB, GhostOrbRenderer::new);
        // 剑冢祭坛：方块本体交给 GeckoLib 的 geo 模型渲染
        BlockEntityRendererRegistry.register(org.examplee.wangsword.registry.BlocksRegistry.SWORD_TOMB_ALTAR_BE,
                SwordTombAltarRenderer::new);
        // 石中剑基座：台座走 JSON 模型，插着的剑由渲染器用物品模型画出来
        BlockEntityRendererRegistry.register(org.examplee.wangsword.registry.BlocksRegistry.STONE_SWORD_PEDESTAL_BE,
                org.examplee.wangsword.client.StoneSwordPedestalRenderer::new);
        // 熔核唤石：整块石头由 geo 模型渲染，blockstate 里是空模型
        BlockEntityRendererRegistry.register(org.examplee.wangsword.registry.BlocksRegistry.MOLTEN_CORE_SUMMONER_BE,
                MoltenCoreSummonerRenderer::new);

        // 【雷石】十字模型需要 cutout 渲染层，否则透明区域会变成黑底
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                org.examplee.wangsword.registry.BlocksRegistry.WS_LIGHTNINGSTONE_BLOCK,
                net.minecraft.client.render.RenderLayer.getCutout());

        // 【药草】十字模型，同样要 cutout
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                org.examplee.wangsword.registry.BlocksRegistry.WS_HERB,
                net.minecraft.client.render.RenderLayer.getCutout());

        // 【石中剑基座】剑身是带透明像素的面片，必须 cutout，否则透明区域会变黑
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                org.examplee.wangsword.registry.BlocksRegistry.STONE_SWORD_PEDESTAL,
                net.minecraft.client.render.RenderLayer.getCutout());

        // 【剑冢守卫·巨剑音爆】自定义粒子：蓝白冲击环，替掉原版 SONIC_BOOM
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
                .register(org.examplee.wangsword.registry.ParticleRegistry.SWORD_WAVE,
                        SwordWaveParticle.Factory::new);

        // 【血帆弯刀】突进刀光 + 命中血花
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
                .register(org.examplee.wangsword.registry.ParticleRegistry.CUTLASS_SLASH,
                        CutlassSlashParticle.Factory::new);
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
                .register(org.examplee.wangsword.registry.ParticleRegistry.BLOOD_BURST,
                        BloodBurstParticle.Factory::new);

        // 【末地合金盔甲】发光层改由 ArmorGlowMixin 处理（挂在原版盔甲渲染末尾）



        // 【紫金工具】物品提示：文本全部走语言文件，和盔甲保持一致
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            net.minecraft.item.Item item = stack.getItem();

            if (item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_SWORD) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_sword.title"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_sword.stats"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_sword.combo"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_sword.execute"));
                return;
            }

            String actionKey = null;
            boolean isPickaxe = item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_PICKAXE;

            if (isPickaxe || item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_SHOVEL) {
                actionKey = "tooltip.wangsword.purple_tool.action.mine";
            } else if (item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_AXE) {
                actionKey = "tooltip.wangsword.purple_tool.action.chop";
            } else if (item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_HOE) {
                actionKey = "tooltip.wangsword.purple_tool.action.till";
            }
            if (actionKey == null) return;

            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_tool.title"));
            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_tool.stats"));
            lines.add(net.minecraft.text.Text.empty());

            boolean isAxe = item == org.examplee.wangsword.registry.ToolRegistry.WS_PURPLE_AXE;

            if (isPickaxe) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_pickaxe.skill"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_pickaxe.bonus"));
            } else if (isAxe) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_axe.skill"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_axe.bonus"));
            } else {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_tool.skill"));
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.purple_tool.range",
                        net.minecraft.text.Text.translatable(actionKey)));
            }
        });
        // 【末地合金工具】技能说明
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            net.minecraft.item.Item tool = stack.getItem();
            boolean pickaxe = tool == org.examplee.wangsword.registry.ToolRegistry.WS_TAIGU_PICKAXE;
            boolean axe = tool == org.examplee.wangsword.registry.ToolRegistry.WS_TAIGU_AXE;
            boolean shovel = tool == org.examplee.wangsword.registry.ToolRegistry.WS_TAIGU_SHOVEL;
            boolean hoe = tool == org.examplee.wangsword.registry.ToolRegistry.WS_TAIGU_HOE;
            if (!pickaxe && !axe && !shovel && !hoe) return;

            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.title"));
            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.stats"));
            lines.add(net.minecraft.text.Text.empty());
            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.skill"));
            if (pickaxe) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.pickaxe"));
            } else if (axe) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.axe"));
            } else if (shovel) {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.shovel"));
            } else {
                lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.hoe"));
            }
            lines.add(net.minecraft.text.Text.translatable("tooltip.wangsword.taigu_tool.resonance"));
        });

        // 【武器技能说明】黑龙弩 / 迅捷骑枪 / 紫水晶巨锤 / 紫金盾
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            net.minecraft.item.Item item = stack.getItem();

            if (item == org.examplee.wangsword.registry.BowRegistery.FATALIS_CROSSBOW) {
                addTooltip(lines, "fatalis");
            } else if (item == org.examplee.wangsword.registry.LanceRegistry.WS_LANCE) {
                addTooltip(lines, "lance");
            } else if (item == org.examplee.wangsword.registry.HammerRegistry.WS_GREATHAMMER) {
                addTooltip(lines, "hammer");
            } else if (item == org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD) {
                addTooltip(lines, "shield");
            }
        });

        // 【终焉姿态】攻击距离 +1.5（依赖 Better Combat，未安装则跳过）
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bettercombat")) {
            org.examplee.wangsword.client.BetterCombatRangeHook.register();
        }


        // 【残骸搜寻仪】网络接收 + 世界内描边
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.item.unique.RelicSeekerItem.SCAN_PACKET,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readVarInt();
                    java.util.List<net.minecraft.util.math.BlockPos> positions = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        positions.add(buf.readBlockPos());
                    }
                    client.execute(() -> org.examplee.wangsword.client.RelicSeekerClient.accept(
                            positions, org.examplee.wangsword.item.unique.RelicSeekerItem.MARK_DURATION));
                });

        // 【阿瓦隆】受服务端包驱动：开始 / 结束
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.util.AvalonHandler.START_PACKET,
                (client, handler, buf, responseSender) -> {
                    int duration = buf.readVarInt();
                    client.execute(() -> org.examplee.wangsword.client.AvalonClient.start(duration));
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.util.AvalonHandler.END_PACKET,
                (client, handler, buf, responseSender) ->
                        client.execute(org.examplee.wangsword.client.AvalonClient::stop));

        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context ->
                org.examplee.wangsword.client.RelicSeekerClient.render(
                        context.matrixStack(),
                        net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        context.camera().getPos()));

        // 【阿瓦隆护罩】展开期间的金色球壳
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context ->
                org.examplee.wangsword.client.AvalonClient.render(
                        context.matrixStack(),
                        net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        context.camera().getPos()));
        // 【击飞附魔】附魔说明（原版不显示自定义附魔描述）
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (net.minecraft.enchantment.EnchantmentHelper.getLevel(
                    org.examplee.wangsword.registry.EnchantmentRegistry.LAUNCH, stack) > 0) {
                lines.add(net.minecraft.text.Text.translatable("enchantment.wangsword.launch.desc")
                        .formatted(net.minecraft.util.Formatting.GRAY));
            }
        });
        // 【X 键大招】默认 X，可在按键设置里改
        ultKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wangsword.ultimate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "category.wangsword"));

        // 【V 键】切换火力全开模式
        aoeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wangsword.aoe",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.wangsword"));

        // 【Z 键】展开阿瓦隆（剑鞘在背包里就能用）
        avalonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wangsword.avalon",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.wangsword"));

        // 【G 键】熔岩套的「熔岩喷发」，不跟手持物品绑定，穿齐四件就能按
        armorSkillKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wangsword.armor_skill",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.wangsword"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleLeftClickFire(client);
            handleFatalisFire(client);
            handleCageFire(client);
            handleAoeToggle(client);
            handleElytra(client);
            handleUltimate(client);
            handleAvalon(client);
            handleArmorSkill(client);
            // 蓄力/发射动作的计时
            if (org.examplee.wangsword.item.unique.ExcaliburItem.CHARGING) {
                org.examplee.wangsword.item.unique.ExcaliburItem.CHARGE_ELAPSED++;
            } else if (org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM <= 0) {
                org.examplee.wangsword.item.unique.ExcaliburItem.CHARGE_ELAPSED = 0;
            }
            if (org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM > 0) {
                org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM--;
                if (org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM == 0) {
                    org.examplee.wangsword.item.unique.ExcaliburItem.CHARGE_ELAPSED = 0;
                }
            }
            // 蓄力/下劈动作由 PlayerChargePoseMixin 直接读 charge_slash.json 摆骨骼
            // 同时交给 PlayerAnimator 播一遍，第一人称才能看到身体
            org.examplee.wangsword.client.ExcaliburAnimation.tick(client);
        });

        // 蓄力时切换王者之剑的模型（贴图换成 model_02）
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                new net.minecraft.util.Identifier("wangsword", "charging"),
                (stack, world, entity, seed) ->
                        org.examplee.wangsword.item.unique.WangShadowSwordItem.CHARGING ? 1.0F : 0.0F);

        // 【誓约胜利之剑】按住 X 蓄力时，把透明的风王结界剑身换成发光剑身
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.ItemsRegistry.WS_EXCALIBUR,
                new net.minecraft.util.Identifier("wangsword", "excalibur_charging"),
                (stack, world, entity, seed) ->
                        org.examplee.wangsword.item.unique.ExcaliburItem.CHARGING ? 1.0F : 0.0F);

        // 【誓约胜利之剑】按住右键放风王结界时，换成那把风的剑身
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.ItemsRegistry.WS_EXCALIBUR,
                new net.minecraft.util.Identifier("wangsword", "excalibur_wind"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);

        // 【盾牌】格挡状态谓词：举盾时切到另一套模型，做出"盾牌举起"的样子
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD,
                new net.minecraft.util.Identifier("wangsword", "blocking"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);

        // 【守誓】右键格挡时换一套第一人称参数，把双头刀横到身前
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_OATH,
                new net.minecraft.util.Identifier("wangsword", "blocking"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);

        // 【雷霆三叉戟】按住右键蓄力投掷时换一套第三人称参数，让矛头朝前
        // 原版三叉戟是 3D 模型（ItemRenderer 里按 Items.TRIDENT 特判走 BEWLR），
        // 显示参数是恒等的、方向完全跟随手臂；我们是 2D 贴图，固定旋转在抬臂后就歪了。
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.TridentRegistry.MY_TRIDENT,
                new net.minecraft.util.Identifier("wangsword", "throwing"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);
        // 【长矛蓄力条】蓄力时在准星下方画一条进度条
        HudRenderCallback.EVENT.register((context, tickDelta) -> renderLanceChargeBar(context));

        // 【长矛蓄力贴图】像弓一样根据蓄力进度切换模型
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.LanceRegistry.WS_LANCE,
                new net.minecraft.util.Identifier("pulling"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);

        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                org.examplee.wangsword.registry.LanceRegistry.WS_LANCE,
                new net.minecraft.util.Identifier("pull"),
                (stack, world, entity, seed) -> {
                    if (entity == null || entity.getActiveItem() != stack) return 0.0F;
                    int elapsed = stack.getMaxUseTime() - entity.getItemUseTimeLeft();
                    return MathHelper.clamp(elapsed / (float) LanceItem.MAX_CHARGE, 0.0F, 1.0F);
                });

    }

    /** 按前缀批量添加技能说明行（文本都在语言文件里） */
    private static void addTooltip(java.util.List<net.minecraft.text.Text> lines, String prefix) {
        String base = "tooltip.wangsword." + prefix + ".";
        addIfPresent(lines, base + "title");
        for (String key : new String[]{"fire", "arrow", "shell", "ult",
                "dash", "hit", "slam", "radius", "slow", "block", "passive", "thorns"}) {
            addIfPresent(lines, base + key);
        }
    }

    private static void addIfPresent(java.util.List<net.minecraft.text.Text> lines, String key) {
        if (net.minecraft.client.resource.language.I18n.hasTranslation(key)) {
            lines.add(net.minecraft.text.Text.translatable(key));
        }
    }

    /** 蓄力条：长矛 / 黑龙弩共用，满蓄力时变成金色 */
    private static void renderLanceChargeBar(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        if (!client.player.isUsingItem()) return;

        ItemStack stack = client.player.getActiveItem();

        int chargeTicks;
        if (stack.getItem() instanceof LanceItem) {
            chargeTicks = LanceItem.MAX_CHARGE;
        } else if (stack.getItem() instanceof org.examplee.wangsword.item.unique.FatalisCrossbowItem) {
            chargeTicks = org.examplee.wangsword.item.unique.FatalisCrossbowItem.MAX_CHARGE;
        } else if (stack.getItem() instanceof org.examplee.wangsword.item.unique.GreatHammerItem) {
            // 大招蓄力 30 tick，右键震地 20 tick，按当前模式取对应值
            chargeTicks = hammerCharging
                    ? org.examplee.wangsword.item.unique.GreatHammerItem.ULT_CHARGE_TICKS
                    : org.examplee.wangsword.item.unique.GreatHammerItem.MAX_CHARGE;
        } else {
            return;
        }

        int max = stack.getMaxUseTime();
        if (max <= 0) return;

        int elapsed = max - client.player.getItemUseTimeLeft();
        float progress = MathHelper.clamp(elapsed / (float) chargeTicks, 0.0F, 1.0F);
        if (progress <= 0.02F) return;

        int width = 100;
        int height = 6;
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = context.getScaledWindowHeight() / 2 + 18;

        boolean full = progress >= 1.0F;

        // 外框 + 背景
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        context.fill(x, y, x + width, y + height, 0x80000000);

        // 进度
        int filled = (int) (width * progress);
        int color = full ? 0xFFFFD166 : 0xFF55DDFF;
        context.fill(x, y, x + filled, y + height, color);

        // 顶部高光
        context.fill(x, y, x + filled, y + 1, 0x80FFFFFF);
    }

    /** 紫金四件套滑翔：自实现物理（原版会强制打断自定义胸甲） */
    private void handleElytra(MinecraftClient client) {
        if (elytraCooldown > 0) elytraCooldown--;
        if (fireworkCooldown > 0) fireworkCooldown--;

        if (client.player == null || client.world == null) {
            glideMode = false;
            lastJumpDown = false;
            return;
        }

        // 跳跃键边沿检测：每 tick 都要更新，避免状态残留
        boolean jumpDown = client.options.jumpKey.isPressed();
        boolean freshPress = jumpDown && !lastJumpDown;
        lastJumpDown = jumpDown;

        // 离地计时：起跳那一 tick 结束时人已离地，必须排除掉
        if (client.player.isOnGround()) {
            airTicks = 0;
        } else {
            airTicks++;
        }

        // 创造模式飞行时不触发滑翔（否则会被滑翔物理推着走）
        if (client.player.getAbilities().flying) {
            if (glideMode) {
                glideMode = false;
                sendGlideState(false);
            }
            return;
        }

        boolean fullSet = org.examplee.wangsword.registry.ArmorRegistry.isFullSet(client.player);

        // 落地 / 入水 / 脱装备 → 收起
        if (!fullSet || client.player.isOnGround() || client.player.isTouchingWater()) {
            if (glideMode) {
                glideMode = false;
                sendGlideState(false);
            }
            return;
        }

        // 空中重新按跳跃就展开（和原版鞘翅一致，上升下降都行）
        if (!glideMode && freshPress && airTicks >= 3) {
            glideMode = true;
            sendGlideState(true);
        }

        if (!glideMode) return;
        // 原版鞘翅已经在飞（Mixin 生效）→ 交给原版处理
        if (client.player.isFallFlying()) return;
        // ===== 原版 LivingEntity.travel() 鞘翅物理，逐条移植 =====
        net.minecraft.util.math.Vec3d vel = client.player.getVelocity();
        net.minecraft.util.math.Vec3d look = client.player.getRotationVector();

        float pitchRad = client.player.getPitch() * 0.017453292F;

        double lookHoriz = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double lookLen = look.length();

        // 升力系数：cos²(pitch) * min(1, |look| / 0.4)
        double cosPitch = Math.cos(pitchRad);
        cosPitch = cosPitch * cosPitch * Math.min(1.0D, lookLen / 0.4D);

        // 重力 + 升力
        double vx = vel.x;
        // +0.08 抵消下一 tick 原版玩家重力（我们是在 tick 末设置速度的）
        double vy = vel.y + 0.08D * (-1.0D + cosPitch * 0.75D) + 0.08D;
        double vz = vel.z;

        // 下落时把垂直速度转成沿视线方向的前进速度
        if (vy < 0.0D && lookHoriz > 0.0D) {
            double d = vy * -0.1D * cosPitch;
            vx += look.x * d / lookHoriz;
            vz += look.z * d / lookHoriz;
            vy += d;
        }

        // 抬头：把水平速度转成爬升
        if (pitchRad < 0.0F && lookHoriz > 0.0D) {
            double d = horizSpeed * (-net.minecraft.util.math.MathHelper.sin(pitchRad)) * 0.04D;
            vx += -look.x * d / lookHoriz;
            vy += d * 3.2D;
            vz += -look.z * d / lookHoriz;
        }

        // 转向：把水平速度朝视线方向拉
        if (lookHoriz > 0.0D) {
            vx += (look.x / lookHoriz * horizSpeed - vx) * 0.1D;
            vz += (look.z / lookHoriz * horizSpeed - vz) * 0.1D;
        }

        // 前向动力：没有俯冲也能持续前进（原版靠俯冲换速度，这里补一点基础推力）
        double hs = Math.sqrt(vx * vx + vz * vz);
        if (lookHoriz > 0.0D && hs < 0.6D) {
            double push = Math.min(0.03D, 0.6D - hs);
            vx += look.x / lookHoriz * push;
            vz += look.z / lookHoriz * push;
        }

        // 阻力交给原版 tick 处理，这里不再乘，避免双重衰减

        client.player.setVelocity(vx, vy, vz);
        client.player.velocityModified = true;
        client.player.fallDistance = 0.0F;

    }

    /** 同步滑翔状态给服务端 */
    private void sendGlideState(boolean on) {
        net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ClientPlayNetworking.send(org.examplee.wangsword.registry.ArmorRegistry.ELYTRA_PACKET, buf);
    }
    /** 黑龙弩：按住左键连发，每 FIRE_INTERVAL tick 发一次请求 */
    /** V 键：切换火力全开模式 */
    private void handleAoeToggle(MinecraftClient client) {
        boolean down = aoeKey.isPressed();
        boolean fresh = down && !lastAoeDown;
        lastAoeDown = down;

        if (!fresh) return;
        if (client.player == null) return;

        ClientPlayNetworking.send(
                org.examplee.wangsword.util.AoeHandler.MODE_PACKET,
                PacketByteBufs.empty());
    }

    /** 雷电法杖：囚笼期间左键放电，所有被锁单位同步吃电击 */
    private void handleCageFire(MinecraftClient client) {
        boolean down = client.player != null && client.world != null
                && client.currentScreen == null
                && client.player.getMainHandStack().getItem()
                instanceof org.examplee.wangsword.item.unique.ThunderStaffItem
                && !client.player.isSpectator()
                && client.options.attackKey.isPressed();
        if (down && !lastCageAttackDown) {
            ClientPlayNetworking.send(
                    org.examplee.wangsword.item.unique.ThunderStaffItem.CAGE_HIT_PACKET,
                    PacketByteBufs.empty());
        }
        lastCageAttackDown = down;
    }

    private void handleFatalisFire(MinecraftClient client) {
        if (fatalisCooldown > 0) fatalisCooldown--;

        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        if (!(client.player.getMainHandStack().getItem()
                instanceof org.examplee.wangsword.item.unique.FatalisCrossbowItem)) return;

        // 持弩时取消挥手臂动作（只保留开火表现）
        client.player.handSwinging = false;
        client.player.handSwingTicks = 0;

        if (!client.options.attackKey.isPressed()) return;
        if (client.player.isSpectator()) return;
        if (fatalisCooldown > 0) return;

        fatalisCooldown = org.examplee.wangsword.item.unique.FatalisCrossbowItem.FIRE_INTERVAL;
        ClientPlayNetworking.send(
                org.examplee.wangsword.item.unique.FatalisCrossbowItem.FIRE_PACKET,
                PacketByteBufs.empty());
    }

    /** 左键连发：按住左键 -> 按攻速换算出的间隔，向服务端发一次开火请求 */
    private void handleLeftClickFire(MinecraftClient client) {
        if (fireCooldown > 0) fireCooldown--;

        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;          // 开着界面（背包/聊天）时不发射
        if (!(client.player.getMainHandStack().getItem() instanceof WangShadowSwordItem)) return;
        if (!client.options.attackKey.isPressed()) return; // 没按左键
        if (client.player.isSpectator()) return;
        if (fireCooldown > 0) return;

        fireCooldown = WangShadowSwordItem.getFireIntervalTicks(client.player);
        ClientPlayNetworking.send(WangShadowSwordItem.SLASH_FIRE_PACKET, PacketByteBufs.empty());
        // 【临时测试】关闭挥手，验证它是否导致 Better Combat 重置攻击冷却
        // client.player.swingHand(Hand.MAIN_HAND);
    }

    /** Z 键：请求展开阿瓦隆（背包里有剑鞘才生效，服务端校验） */
    private void handleAvalon(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        if (avalonKey.wasPressed()) {
            ClientPlayNetworking.send(
                    org.examplee.wangsword.util.AvalonHandler.ACTIVATE_PACKET,
                    PacketByteBufs.create());
        }
    }

    /**
     * G 键：套装技能。
     *
     * <p>一个键带两套技能，按当前穿齐的套装分派：熔岩套 → 熔岩喷发，
     * 幽卫钢套 → 幽卫飞剑。两套都没齐就在本地动作栏提示，不发包。
     * 件数、冷却、结算仍然全部由服务端校验。
     */
    private void handleArmorSkill(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        if (client.player.isSpectator()) {
            return;
        }
        if (armorSkillKey == null || !armorSkillKey.wasPressed()) {
            return;
        }

        int molten = org.examplee.wangsword.util.MoltenSetHandler.pieces(client.player);
        int fulgur = org.examplee.wangsword.util.FulgurSetHandler.pieces(client.player);

        if (molten >= org.examplee.wangsword.util.MoltenSetHandler.MAX_PIECES) {
            ClientPlayNetworking.send(
                    org.examplee.wangsword.util.MoltenSetHandler.ERUPTION_PACKET,
                    PacketByteBufs.create());
        } else if (fulgur >= org.examplee.wangsword.util.FulgurSetHandler.MAX_PIECES) {
            ClientPlayNetworking.send(
                    org.examplee.wangsword.util.FulgurSetHandler.VOLLEY_PACKET,
                    PacketByteBufs.create());
        } else {
            client.player.sendMessage(
                    net.minecraft.text.Text.translatable("message.wangsword.set.need_full")
                            .formatted(net.minecraft.util.Formatting.RED), true);
        }
    }

    /**
     * X 键大招：
     *   轻按（不到 CHARGE_TICKS）  -> 十字剑气
     *   按住超过 CHARGE_TICKS 松手 -> 蓄力斩
     */
    /** 掐断誓约胜利之剑的蓄力音效 */
    private void stopExcaliburSound(MinecraftClient client) {
        if (!excaliburCharging) {
            return;
        }
        client.getSoundManager().stopSounds(
                org.examplee.wangsword.registry.SoundRegistry.EXCALIBUR.getId(),
                net.minecraft.sound.SoundCategory.PLAYERS);
        excaliburCharging = false;
    }

    private void handleUltimate(MinecraftClient client) {
        // 每 tick 先清掉「X 蓄力中」标记：后面任何一条提前 return 都不会留下脏状态
        org.examplee.wangsword.item.unique.WangBowItem.CLIENT_ULT_CHARGING = false;
        org.examplee.wangsword.item.unique.WangBowItem.CLIENT_ULT_CHARGE_TICKS = 0;
        if (client.player == null || client.world == null) { ultHold = 0; stopExcaliburSound(client); return; }
        if (client.currentScreen != null) { ultHold = 0; stopExcaliburSound(client); return; }
        if (client.player.isSpectator()) { ultHold = 0; stopExcaliburSound(client); return; }

        net.minecraft.item.ItemStack held = client.player.getMainHandStack();

        // 自己算按下边沿：GLFW 的按键重复事件会让 wasPressed() 每 tick 都返回 true
        boolean ultDown = ultKey.isPressed();
        boolean ultPressedEdge = ultDown && !lastUltDown;
        lastUltDown = ultDown;
        if (!ultDown) {
            excaliburAutoFired = false;
        }

        // 换武器就清空蓄力进度，避免拿着上一把武器的蓄力去触发新武器
        if (held.getItem() != lastUltItem) {
            lastUltItem = held.getItem();
            ultHold = 0;
            excaliburAutoFired = false;
            stopExcaliburSound(client);
            if (hammerCharging) {
                client.player.stopUsingItem();
                hammerCharging = false;
            }
        }

        if (!(held.getItem() instanceof org.examplee.wangsword.item.ISkillWeapon)) { ultHold = 0; return; }

        boolean isSword = held.getItem() instanceof WangShadowSwordItem;
        // 蓄力贴图：拿着剑按住 X 时切换模型
        org.examplee.wangsword.item.unique.WangShadowSwordItem.CHARGING = isSword && ultDown;
        boolean isExcalibur = held.getItem() instanceof org.examplee.wangsword.item.unique.ExcaliburItem;
        org.examplee.wangsword.item.unique.ExcaliburItem.CHARGING = isExcalibur && ultDown && !excaliburAutoFired;
        // 誓约胜利之剑：蓄力开始就把大招音效放出来，音效结束刚好蓄满
        if (isExcalibur && ultPressedEdge) {
            client.player.playSound(
                    org.examplee.wangsword.registry.SoundRegistry.EXCALIBUR, 1.6F, 1.0F);
            excaliburCharging = true;
        }
        boolean isCannon = held.getItem() instanceof org.examplee.wangsword.item.unique.FatalisCrossbowItem;
        boolean isHammer = held.getItem() instanceof org.examplee.wangsword.item.unique.GreatHammerItem;
        // 流星弓的雷涌要蓄力 1.2 秒，不是按下即发
        boolean isBow = held.getItem() instanceof org.examplee.wangsword.item.unique.WangBowItem;
        // 告诉拉弓贴图谓词：「现在的蓄力分母是 24 还是 60」
        org.examplee.wangsword.item.unique.WangBowItem.CLIENT_ULT_CHARGING = isBow && ultDown;

        // 其它即时技能（例如雷霆三叉戟）：按下即触发
        if (!isSword && !isCannon && !isHammer && !isExcalibur && !isBow) {
            if (ultPressedEdge) {
                if (held.getItem() instanceof org.examplee.wangsword.item.unique.TaiguSwordItem) {
                    org.examplee.wangsword.client.TaiguStanceClient.start();
                }
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString());
                buf.writeBoolean(false);
                ClientPlayNetworking.send(WangShadowSwordItem.ULT_PACKET, buf);
            }
            return;
        }

        int chargeTicks;
        if (isSword) {
            chargeTicks = WangShadowSwordItem.CHARGE_TICKS;
        } else if (isCannon) {
            chargeTicks = org.examplee.wangsword.item.unique.FatalisCrossbowItem.CHARGE_TICKS;
        } else if (isExcalibur) {
            chargeTicks = org.examplee.wangsword.item.unique.ExcaliburItem.CHARGE_TICKS;
        } else if (isBow) {
            chargeTicks = org.examplee.wangsword.item.unique.WangBowItem.ULT_CHARGE_TICKS;
        } else {
            chargeTicks = org.examplee.wangsword.item.unique.GreatHammerItem.ULT_CHARGE_TICKS;
        }

        if (ultKey.isPressed()) {
            // 誓约胜利之剑已经自动发射过了：按住期间不再累计，松手也不会补刀
            if (excaliburAutoFired) {
                return;
            }
            ultHold++;
            // 把「已经蓄了多少 tick」交给贴图谓词和 HUD 条：
            // 强制拉弓状态下 MC 自己的计时不推进，只能由这里喂。
            org.examplee.wangsword.item.unique.WangBowItem.CLIENT_ULT_CHARGE_TICKS = ultHold;

            // X 蓄力时的持械姿势：战锤举锤（UseAction.SPEAR），弓直接拉弓（UseAction.BOW）。
            // 弓这里等于「照搬右键动画」——按住 X 看到的就是自己把弓拉满；
            // 原版 20 tick 拉满并保持，雷涌蓄力 24 tick，视觉和数值基本同步。
            // 只在客户端 setCurrentHand，不发交互包，所以不会真的射出一支箭。
            if ((isHammer || isBow) && !client.player.isUsingItem()) {
                client.player.setCurrentHand(net.minecraft.util.Hand.MAIN_HAND);
                hammerCharging = true;
            }

            // 蓄力粒子：剑用龙息（黑），炮用火焰（紫红），弓用电火花（青白）
            int interval = ultHold >= chargeTicks ? 1 : 3;
            if (!isExcalibur && ultHold % interval == 0) {
                double r = 0.7;
                double px = client.player.getX() + (client.world.random.nextDouble() - 0.5) * r;
                double py = client.player.getY() + 0.9 + client.world.random.nextDouble() * 0.8;
                double pz = client.player.getZ() + (client.world.random.nextDouble() - 0.5) * r;

                client.world.addParticle(
                        isSword ? ParticleTypes.DRAGON_BREATH
                                : isBow ? ParticleTypes.ELECTRIC_SPARK
                                : ParticleTypes.FLAME,
                        px, py, pz, 0.0, 0.03, 0.0);
            }

            // 动作栏进度条（誓约胜利之剑不显示，靠音效给反馈）
            //
            // 弓也不显示：这支弓的蓄力语言统一成「拉弓」——弓拉满就是蓄力满，
            // 和右键「流星」完全是同一套表达，不额外占用动作栏。
            if (!isExcalibur && !isBow && ultHold % 2 == 0) {
                int pct = Math.min(100, ultHold * 100 / chargeTicks);
                Text msg;
                if (pct >= 100) {
                    String key;
                    if (isSword) {
                        key = "hint.wangsword.charge.full.sword";
                    } else if (isExcalibur) {
                        key = "hint.wangsword.charge.full.excalibur";
                    } else if (isCannon) {
                        key = "hint.wangsword.charge.full.cannon";
                    } else {
                        key = "hint.wangsword.charge.full.quake";
                    }
                    msg = Text.translatable(key);
                } else {
                    msg = Text.translatable("hint.wangsword.charge.progress", pct);
                }
                client.player.sendMessage(msg, true);
            }

            // 节拍音（誓约胜利之剑有自己的长音效，不放节拍）
            if (!isExcalibur && ultHold % 5 == 0) {
                float pitch = 0.8F + 0.8F * Math.min(1.0F, ultHold / (float) chargeTicks);
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.5F, pitch);
            }

            if (ultHold == chargeTicks) {
                client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 1.5F);
            }

            // 誓约胜利之剑：蓄满 100% 立刻自动发射（蓄力时长固定，和音效结尾对齐）
            if (isExcalibur && ultHold >= chargeTicks) {
                PacketByteBuf fired = PacketByteBufs.create();
                fired.writeString(net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString());
                fired.writeBoolean(true);
                ClientPlayNetworking.send(WangShadowSwordItem.ULT_PACKET, fired);
                org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM =
                        org.examplee.wangsword.item.unique.ExcaliburItem.FIRE_ANIM_TICKS;
                // 第一人称那一下挥砍：直接用原版挥手
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                client.player.sendMessage(Text.empty(), true);
                ultHold = 0;
                excaliburAutoFired = true;
                excaliburCharging = false;
                // 动画自己会在 2.95 秒走到下劈段，这里不重复播放，只等它播完再收层
            }
        } else if (ultHold > 0) {
            boolean charged = ultHold >= chargeTicks;
            // 誓约胜利之剑：没蓄满就什么都不放，音效立刻掐断
            if (isExcalibur) {
                stopExcaliburSound(client);
                client.player.sendMessage(Text.empty(), true);
                ultHold = 0;
                if (hammerCharging) {
                    client.player.stopUsingItem();
                    hammerCharging = false;
                }
                return;
            }
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString());
            buf.writeBoolean(charged);
            ClientPlayNetworking.send(WangShadowSwordItem.ULT_PACKET, buf);
            client.player.sendMessage(Text.empty(), true);
            ultHold = 0;

            // 收掉蓄力姿势
            if (hammerCharging) {
                client.player.stopUsingItem();
                hammerCharging = false;
            }
        }
    }

    /** 蓄力进度条：10 格 */
    private static String buildChargeBar(int pct) {
        int filled = Math.min(10, pct / 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? "§d█" : "§8░");
        }
        return sb.toString();
    }
}
