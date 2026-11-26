package org.examplee.wangsword.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.entity.ArcSlashEntity;
import org.examplee.wangsword.entity.DeepSeaStormEntity;
import org.examplee.wangsword.entity.FatalisBoltEntity;
import org.examplee.wangsword.entity.FatalisCannonEntity;
import org.examplee.wangsword.entity.HammerShockwaveEntity;
import org.examplee.wangsword.entity.FatalisShellEntity;
import org.examplee.wangsword.entity.WangArrowEntity;

public class EntityRegistry {

    // 提前声明变量
    public static EntityType<ArcSlashEntity> ARC_SLASH;
    /** 金色剑气：誓约胜利之剑专用，渲染器固定用金色贴图，不依赖同步标记 */
    public static EntityType<ArcSlashEntity> ARC_SLASH_GOLD;
    /** 风刃：风王结界专用，渲染器固定用风纹理 */
    public static EntityType<ArcSlashEntity> ARC_SLASH_WIND;
    /** 金色爆炸光柱（誓约胜利之剑的终爆） */
    public static EntityType<org.examplee.wangsword.entity.GoldenBurstEntity> GOLDEN_BURST;

    /** 风王结界的蓝色风筒（同一个实体类，换蓝色贴图并沿朝向横放） */
    public static EntityType<org.examplee.wangsword.entity.GoldenBurstEntity> WIND_BURST;

    /** 烈火大刀的通天火柱（纯视觉，圆柱贴图由 FlamePillarRenderer 画） */
    public static EntityType<org.examplee.wangsword.entity.FlamePillarEntity> FLAME_PILLAR;

    /** 烈火大刀的烈火喷射（纯视觉，横放的火锥，由 FlameJetRenderer 画） */
    public static EntityType<org.examplee.wangsword.entity.FlameJetEntity> FLAME_JET;

    /** 火焰法杖的烈焰弹（飞行火球，命中炸开范围火焰） */
    public static EntityType<org.examplee.wangsword.entity.FlameBoltEntity> FLAME_BOLT;

    /** 火焰法杖的火地 / 熔火领域（纯逻辑 + 粒子） */
    public static EntityType<org.examplee.wangsword.entity.FlamePatchEntity> FLAME_PATCH;

    /** 冰霜法杖的冰冻光束（纯视觉，沿视线的细光柱，由 FrostBeamRenderer 画） */
    public static EntityType<org.examplee.wangsword.entity.FrostBeamEntity> FROST_BEAM;

    /** 雷电法杖的紫电光束（纯视觉，由 ThunderBeamRenderer 用紫雷贴图画） */
    public static EntityType<org.examplee.wangsword.entity.ThunderBeamEntity> THUNDER_BEAM;

    /** 熔核巨兽的火焰激光：attack1 从头部射、attack2 从胸口核心射 */
    public static EntityType<org.examplee.wangsword.entity.CinderBeamEntity> CINDER_BEAM;

    /** 熔核巨兽 melee4 的火焰冲击波：贴地扩散的火环 */
    public static EntityType<org.examplee.wangsword.entity.FlameShockwaveEntity> FLAME_SHOCKWAVE;

    /** 熔核巨兽 attack3 召唤的火球 */
    public static EntityType<org.examplee.wangsword.entity.CinderFireballEntity> CINDER_FIREBALL;

    /** 熔核封岩巨剑右键插地的剑（纯视觉，8 tick 后消失） */
    public static EntityType<org.examplee.wangsword.entity.MoltenBladeEntity> MOLTEN_BLADE;

    /** 冰霜之刃的霜环（纯视觉，贴地扩散的圆环，由 FrostNovaRenderer 画） */
    public static EntityType<org.examplee.wangsword.entity.FrostNovaEntity> FROST_NOVA;

    /** 冰霜之刃的冰棱穿刺（飞行弹体，自己带穿透判定） */
    public static EntityType<org.examplee.wangsword.entity.FrostSpikeEntity> FROST_SPIKE;

    /** 雷电太刀 · 雷怒的脚下雷环（纯视觉，跟脚走的贴地圆环，由 ThunderRingRenderer 画） */
    public static EntityType<org.examplee.wangsword.entity.ThunderRingEntity> THUNDER_RING;

    /**
     * 带皮肤的落雷：继承原版 LightningEntity，紫电 / 蓝电靠这个类型的渲染器画。
     *
     * <p>伤害、点火、引雷针全部沿用原版逻辑，只有外观换成了贴图。
     */
    public static EntityType<org.examplee.wangsword.entity.ThunderBoltEntity> THUNDER_BOLT;

    /** 凋灵杀手的飞剑：渲染时直接用物品模型 */
    public static final EntityType<org.examplee.wangsword.entity.FlyingSwordEntity> FLYING_SWORD = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "flying_sword"),
            FabricEntityTypeBuilder.<org.examplee.wangsword.entity.FlyingSwordEntity>create(
                            SpawnGroup.MISC, org.examplee.wangsword.entity.FlyingSwordEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
                    .trackRangeBlocks(64).trackedUpdateRate(1)
                    .build()
    );

    /**
     * 环伺之剑：剑冢巨剑右键召出来的那一圈浮剑。
     *
     * <p>位置每 tick 由服务端推一次，所以要 trackedUpdateRate(1)；
     * 跟踪范围给 48 是因为剑阵本体只绕在主人身边 2.3 格内，
     * 主人能看见的地方剑一定也在。
     */
    public static final EntityType<org.examplee.wangsword.entity.SentinelSwordEntity> SENTINEL_SWORD =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "sentinel_sword"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.SentinelSwordEntity>create(
                                    SpawnGroup.MISC, org.examplee.wangsword.entity.SentinelSwordEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6F, 0.6F))
                            .trackRangeBlocks(48).trackedUpdateRate(1)
                            .build()
            );

    /** 剑冢守卫（GeckoLib 动画生物） */
    public static final EntityType<org.examplee.wangsword.entity.SwordTombGuardianEntity> SWORD_TOMB_GUARDIAN =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "sword_tomb_guardian"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.SwordTombGuardianEntity>create(
                                    SpawnGroup.MONSTER, org.examplee.wangsword.entity.SwordTombGuardianEntity::new)
                            // 尺寸取自 geo 模型实际包围盒：主体+头约 4.4 格高、2.5 格宽
                            // （头顶上方漂浮的六把剑到 5.9 格，不纳入碰撞箱，避免卡门框）
                            .dimensions(EntityDimensions.fixed(2.2F, 4.4F))
                            .trackRangeBlocks(48).trackedUpdateRate(3)
                            .build()
            );

    /** 裂冠幽灵（GeckoLib 动画生物，精英级亡灵） */
    public static final EntityType<org.examplee.wangsword.entity.GhostCrownWraithEntity> GHOST_CROWN_WRAITH =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "ghost_crown_wraith"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.GhostCrownWraithEntity>create(
                                    SpawnGroup.MONSTER, org.examplee.wangsword.entity.GhostCrownWraithEntity::new)
                            // geo 模型实测 0.9 格宽、1.7 格高，尾巴往后拖 1.4 格不计入碰撞箱
                            .dimensions(EntityDimensions.fixed(1.0F, 1.9F))
                            .trackRangeBlocks(64).trackedUpdateRate(3)
                            .build()
            );

    /** 熔核巨兽（下界 BOSS，GeckoLib 动画生物，亡灵） */
    public static final EntityType<org.examplee.wangsword.entity.CinderColossusEntity> CINDER_COLOSSUS =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "cinder_colossus"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.CinderColossusEntity>create(
                                    SpawnGroup.MONSTER, org.examplee.wangsword.entity.CinderColossusEntity::new)
                            // geo 模型 40 像素高（2.5 格），渲染放大 3 倍 → 约 7.5 格
                            .dimensions(EntityDimensions.fixed(4.4F, 7.4F))
                            .trackRangeBlocks(64).trackedUpdateRate(3)
                            .build()
            );

    /** 熔岩蝠怪：熔核沉坛里成群的低空飞行精英怪 */
    public static final EntityType<org.examplee.wangsword.entity.CalderaBatEntity> CALDERA_BAT =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "caldera_bat"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.CalderaBatEntity>create(
                                    SpawnGroup.MONSTER, org.examplee.wangsword.entity.CalderaBatEntity::new)
                            // 模型 0.8 倍渲染后约 1.28 格高，碰撞箱按此收紧
                            .dimensions(EntityDimensions.fixed(1.12F, 1.3F))
                            .trackRangeBlocks(64).trackedUpdateRate(3)
                            .build()
            );

    /** 裂冠幽灵的光弹（纯投射物，命中爆炸） */
    public static final EntityType<org.examplee.wangsword.entity.GhostOrbEntity> GHOST_ORB =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "ghost_orb"),
                    FabricEntityTypeBuilder.<org.examplee.wangsword.entity.GhostOrbEntity>create(
                                    SpawnGroup.MISC, org.examplee.wangsword.entity.GhostOrbEntity::new)
                            .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
                            .trackRangeBlocks(64).trackedUpdateRate(1)
                            .build()
            );

    /** 注册生物属性（必须在实体注册之后调用） */
    public static void registerAttributes() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                SWORD_TOMB_GUARDIAN,
                org.examplee.wangsword.entity.SwordTombGuardianEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                GHOST_CROWN_WRAITH,
                org.examplee.wangsword.entity.GhostCrownWraithEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                CINDER_COLOSSUS,
                org.examplee.wangsword.entity.CinderColossusEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                CALDERA_BAT,
                org.examplee.wangsword.entity.CalderaBatEntity.createAttributes());
    }

    /**
     * 生成规则。结构 json 里的 spawn_overrides 只负责"在剑冢范围内把怪物加进刷怪表"，
     * 真正决定"能不能站在这个方块上"的是这里。不注册的话怪物可能刷在半空或方块里。
     */
    public static void registerSpawnRestrictions() {
        SpawnRestriction.register(
                SWORD_TOMB_GUARDIAN,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                (type, world, spawnReason, pos, random) -> true);

        // 裂冠幽灵：剑冢专属。结构 json 的 spawn_overrides 只决定"剑冢里刷什么"，
        // 具体能不能落在这里由下面这几条把关。
        SpawnRestriction.register(
                GHOST_CROWN_WRAITH,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                (type, world, spawnReason, pos, random) -> {
                    // 只在暗处出现，白天露天的剑冢不刷
                    if (world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos) > 7) {
                        return false;
                    }
                    // 脚下得是实心方块，别刷在半空
                    net.minecraft.util.math.BlockPos below = pos.down();
                    if (!world.getBlockState(below).isSolidBlock(world, below)) {
                        return false;
                    }
                    if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
                        return true;
                    }
                    // 16 格内最多 2 只：避免一群幽灵同时往脸上丢光弹
                    int same = serverWorld.getEntitiesByType(
                            net.minecraft.util.TypeFilter.instanceOf(
                                    org.examplee.wangsword.entity.GhostCrownWraithEntity.class),
                            new net.minecraft.util.math.Box(pos).expand(16.0D),
                            living -> living.isAlive()).size();
                    if (same >= 2) {
                        return false;
                    }
                    // 玩家 24 格内不刷，别贴脸生成
                    return serverWorld.getClosestPlayer(
                            pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 24.0D, false) == null;
                });
    }
    public static EntityType<DeepSeaStormEntity> DEEP_SEA_STORM;
    public static final EntityType<WangArrowEntity> WANG_ARROW = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "wang_arrow"),
            FabricEntityTypeBuilder.<WangArrowEntity>create(SpawnGroup.MISC, WangArrowEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
                    // 追踪范围必须覆盖整条射程：默认 4（约 64 格）时箭飞出 1 秒就被 destroy，激光整条消失
                    .trackRangeBlocks(32).trackedUpdateRate(10)
                    .build()
    );
    /** 雷霆贯穿：右键扔出的三叉戟，贯穿 5 个目标并抹掉路径方块 */
    public static final EntityType<org.examplee.wangsword.entity.ThunderTridentEntity> THUNDER_TRIDENT =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("wangsword", "thunder_trident"),
                    EntityType.Builder.<org.examplee.wangsword.entity.ThunderTridentEntity>create(
                                    org.examplee.wangsword.entity.ThunderTridentEntity::new,
                                    SpawnGroup.MISC)
                            .setDimensions(0.5F, 0.5F)
                            .maxTrackingRange(128)
                            .trackingTickInterval(1)
                            .build("thunder_trident")
            );

    public static final EntityType<HammerShockwaveEntity> HAMMER_SHOCKWAVE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "hammer_shockwave"),
            EntityType.Builder.<HammerShockwaveEntity>create(HammerShockwaveEntity::new, SpawnGroup.MISC)
                    .setDimensions(1.0F, 1.0F)
                    .build("hammer_shockwave")
    );

    public static final EntityType<FatalisBoltEntity> FATALIS_BOLT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "fatalis_bolt"),
            EntityType.Builder.<FatalisBoltEntity>create(FatalisBoltEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.4F, 0.4F)
                    .build("fatalis_bolt")
    );

    public static final EntityType<FatalisShellEntity> FATALIS_SHELL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "fatalis_shell"),
            EntityType.Builder.<FatalisShellEntity>create(FatalisShellEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.6F, 0.6F)
                    .build("fatalis_shell")
    );

    public static final EntityType<FatalisCannonEntity> FATALIS_CANNON = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("wangsword", "fatalis_cannon"),
            EntityType.Builder.<FatalisCannonEntity>create(FatalisCannonEntity::new, SpawnGroup.MISC)
                    .setDimensions(1.2F, 1.2F)
                    .build("fatalis_cannon")
    );

    // 统一在这里注册，保证两个实体绝不分家，绝对不会因为加载顺序导致崩溃！
    public static void register() {
        // 1. 注册剑气
        ARC_SLASH = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "arc_slash"),
                EntityType.Builder.<ArcSlashEntity>create(
                                (entityType, world) -> new ArcSlashEntity(entityType, world),
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.5F, 0.5F)
                        .maxTrackingRange(32)
                        .trackingTickInterval(1)
                        .build("arc_slash")
        );

        // 1.5 金色剑气（誓约胜利之剑）：跟踪范围给大，64 格的光炮全程可见
        ARC_SLASH_GOLD = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "arc_slash_gold"),
                EntityType.Builder.<ArcSlashEntity>create(
                                (entityType, world) -> new ArcSlashEntity(entityType, world),
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.5F, 0.5F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("arc_slash_gold")
        );

        // 1.6 风刃（风王结界）
        ARC_SLASH_WIND = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "arc_slash_wind"),
                EntityType.Builder.<ArcSlashEntity>create(
                                (entityType, world) -> new ArcSlashEntity(entityType, world),
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.5F, 0.5F)
                        .maxTrackingRange(64)
                        .trackingTickInterval(1)
                        .build("arc_slash_wind")
        );

        // 1.7 金色爆炸光柱（纯视觉实体）
        GOLDEN_BURST = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "golden_burst"),
                EntityType.Builder.<org.examplee.wangsword.entity.GoldenBurstEntity>create(
                                org.examplee.wangsword.entity.GoldenBurstEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("golden_burst")
        );

        WIND_BURST = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "wind_burst"),
                EntityType.Builder.<org.examplee.wangsword.entity.GoldenBurstEntity>create(
                                org.examplee.wangsword.entity.GoldenBurstEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("wind_burst")
        );

        // 烈火大刀 · 通天火柱（纯视觉实体，半径比金色爆炸小、柱子更长）
        FLAME_PILLAR = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "flame_pillar"),
                EntityType.Builder.<org.examplee.wangsword.entity.FlamePillarEntity>create(
                                org.examplee.wangsword.entity.FlamePillarEntity::new,
                                SpawnGroup.MISC
                        )
                        // 柱子有 30 格高、最外层光晕横向到 4.8 格；盒子按这个撑开，
                        // 否则站在柱底抬头看柱顶时整根柱子会被裁掉（原值 0.2×0.2）。
                        .setDimensions(10.0F, 30.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("flame_pillar")
        );

        // 烈火大刀 · 烈火喷射（纯视觉，沿视线横放的火锥）
        FLAME_JET = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "flame_jet"),
                EntityType.Builder.<org.examplee.wangsword.entity.FlameJetEntity>create(
                                org.examplee.wangsword.entity.FlameJetEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("flame_jet")
        );

        // 火焰法杖 · 烈焰弹（飞行火球）
        FLAME_BOLT = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "flame_bolt"),
                EntityType.Builder.<org.examplee.wangsword.entity.FlameBoltEntity>create(
                                org.examplee.wangsword.entity.FlameBoltEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.6F, 0.6F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("flame_bolt")
        );

        // 火焰法杖 · 火地 / 熔火领域（半径 6，包围盒撑大免得被视锥裁掉）
        FLAME_PATCH = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "flame_patch"),
                EntityType.Builder.<org.examplee.wangsword.entity.FlamePatchEntity>create(
                                org.examplee.wangsword.entity.FlamePatchEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(14.0F, 2.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(20)
                        .build("flame_patch")
        );

        // 冰霜法杖 · 冰冻光束（纯视觉，射程内沿视线的细光柱）
        FROST_BEAM = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "frost_beam"),
                EntityType.Builder.<org.examplee.wangsword.entity.FrostBeamEntity>create(
                                org.examplee.wangsword.entity.FrostBeamEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("frost_beam")
        );

        THUNDER_BEAM = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "thunder_beam"),
                EntityType.Builder.<org.examplee.wangsword.entity.ThunderBeamEntity>create(
                                org.examplee.wangsword.entity.ThunderBeamEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("thunder_beam")
        );

        // 熔核巨兽 · 火焰激光（两种形态共用一套实体，用 KIND 区分粗细与伤害）
        CINDER_BEAM = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "cinder_beam"),
                EntityType.Builder.<org.examplee.wangsword.entity.CinderBeamEntity>create(
                                org.examplee.wangsword.entity.CinderBeamEntity::new,
                                SpawnGroup.MISC
                        )
                        // 和冰霜/紫电光束一样给个不参与碰撞的小盒子；
                        // 实际渲染长度由实体自己按射线算，不受盒子限制
                        .setDimensions(0.2F, 0.2F)
                        .maxTrackingRange(160)
                        .trackingTickInterval(1)
                        .build("cinder_beam")
        );

        // 熔核巨兽 · melee4 双臂连砸的火焰冲击波
        FLAME_SHOCKWAVE = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "flame_shockwave"),
                EntityType.Builder.<org.examplee.wangsword.entity.FlameShockwaveEntity>create(
                                org.examplee.wangsword.entity.FlameShockwaveEntity::new,
                                SpawnGroup.MISC
                        )
                        // 环最大画到 9.5 格，盒子必须盖住整个圆盘，
                        // 否则目标点一滑出视锥，引擎就判定不可见、整圈不画
                        .setDimensions(24.0F, 4.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("flame_shockwave")
        );

        // 熔核巨兽 · attack3 召唤的火球
        CINDER_FIREBALL = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "cinder_fireball"),
                EntityType.Builder.<org.examplee.wangsword.entity.CinderFireballEntity>create(
                                org.examplee.wangsword.entity.CinderFireballEntity::new,
                                SpawnGroup.MISC
                        )
                        // 继承原版小火球，追踪范围给足，跨半场也要看得见
                        .setDimensions(0.9F, 0.9F)
                        .maxTrackingRange(160)
                        .trackingTickInterval(2)
                        .build("cinder_fireball")
        );

        // 熔核封岩巨剑 · 插地的那把剑（纯视觉）
        MOLTEN_BLADE = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "molten_blade"),
                EntityType.Builder.<org.examplee.wangsword.entity.MoltenBladeEntity>create(
                                org.examplee.wangsword.entity.MoltenBladeEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.6F, 2.0F)
                        .maxTrackingRange(64)
                        .trackingTickInterval(1)
                        .build("molten_blade")
        );

        // 冰霜之刃 · 霜环（纯视觉，贴地扩散的圆环）
        FROST_NOVA = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "frost_nova"),
                EntityType.Builder.<org.examplee.wangsword.entity.FrostNovaEntity>create(
                                org.examplee.wangsword.entity.FrostNovaEntity::new,
                                SpawnGroup.MISC
                        )
                        // 霜环实际画到半径 7 格（NOVA_RADIUS），盒子必须盖住整个圆盘：
                        // 原来 0.2×0.2 时，第一人称视线朝前，脚下这个点一滑出画面，
                        // 引擎就判定"不可见"，整圈冰环不画 —— 表现为移动闪烁、跑起来消失。
                        .setDimensions(15.0F, 3.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("frost_nova")
        );

        // 冰霜之刃 · 冰棱穿刺（飞行弹体，每 tick 自己算扫掠命中）
        FROST_SPIKE = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "frost_spike"),
                EntityType.Builder.<org.examplee.wangsword.entity.FrostSpikeEntity>create(
                                org.examplee.wangsword.entity.FrostSpikeEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.4F, 0.4F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("frost_spike")
        );

        // 雷电太刀 · 雷怒的脚下雷环（纯视觉，跟脚走 15 秒的贴地圆环）
        THUNDER_RING = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "thunder_ring"),
                EntityType.Builder.<org.examplee.wangsword.entity.ThunderRingEntity>create(
                                org.examplee.wangsword.entity.ThunderRingEntity::new,
                                SpawnGroup.MISC
                        )
                        // 裁切盒要盖住实际画出来的范围：环最大半径 7 格（万雷）。
                        // 原来写 0.2×0.2，第一人称移动时整圈会被视锥体裁掉。
                        // noClip = true，盒子变大不影响碰撞。
                        .setDimensions(14.0F, 3.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("thunder_ring")
        );

        // 带皮肤的落雷（紫电 / 蓝电）。
        // 高度给 18：包围盒要能罩住整根雷柱，否则玩家抬头看雷、脚下那个点已经出了视锥，
        // 整道雷会被剔除掉不画。宽度 0.6 只是占位，实体本身 noClip 不参与碰撞。
        THUNDER_BOLT = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "thunder_bolt"),
                EntityType.Builder.<org.examplee.wangsword.entity.ThunderBoltEntity>create(
                                org.examplee.wangsword.entity.ThunderBoltEntity::new,
                                SpawnGroup.MISC
                        )
                        .setDimensions(0.6F, 18.0F)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build("thunder_bolt")
        );

        // 2. 注册深海风暴
        DEEP_SEA_STORM = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("wangsword", "deep_sea_storm"),
                EntityType.Builder.<DeepSeaStormEntity>create(DeepSeaStormEntity::new, SpawnGroup.MISC)
                        .setDimensions(1.0F, 1.0F)
                        .build("deep_sea_storm")
        );
    }
}
