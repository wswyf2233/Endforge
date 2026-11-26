package org.examplee.wangsword;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

import org.examplee.wangsword.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;


import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;

import org.examplee.wangsword.item.unique.WangShadowSwordItem;
import org.examplee.wangsword.util.SkillHandler; // 🌟 新增：导入你的统一调度类

public class Wangsword implements ModInitializer {

    /** 正在用紫金套装滑翔的玩家 */
    public static final java.util.Set<java.util.UUID> GLIDING = new java.util.HashSet<>();

    public static final String MOD_ID = "wangsword";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 伤害修正用的攻速修饰符 ID（持久化，会被写进玩家 NBT，所以放下剑时要记得移除）。
     */
    private static final java.util.UUID FAST_ATTACK_ID =
            java.util.UUID.fromString("7a1f3c52-3d4e-4b6a-9c11-5e8d2f0a6b31");

    /** 长矛"速度加成伤害"的临时修饰符 ID（不写入存档） */
    private static final java.util.UUID LANCE_SPEED_DAMAGE_ID =
            java.util.UUID.fromString("c3d5e7a1-2b4f-49a8-8b6c-1d3e5f7a9b0c");

    /**
     * 长矛被动：
     *   1. 常驻迅捷 V（顶级）
     *   2. 移动速度越快，近战伤害越高（额外攻击力 = 水平速度 × 30，最多 +30）
     */
    private static void handleLancePassive(PlayerEntity player) {
        var damageInstance = player.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);

        boolean holdingLance = !player.isDead()
                && player.getMainHandStack().getItem()
                instanceof org.examplee.wangsword.item.unique.LanceItem;

        if (!holdingLance) {
            if (damageInstance != null && damageInstance.getModifier(LANCE_SPEED_DAMAGE_ID) != null) {
                damageInstance.removeModifier(LANCE_SPEED_DAMAGE_ID);
            }
            return;
        }

        // 迅捷 V（放大器 4）
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 4, true, false), player);

        // 水平移动速度 -> 额外伤害（用速度向量，比位置差可靠）
        net.minecraft.util.math.Vec3d vel = player.getVelocity();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double bonus = Math.min(40.0, speed * 90.0);

        if (damageInstance != null) {
            if (damageInstance.getModifier(LANCE_SPEED_DAMAGE_ID) != null) {
                damageInstance.removeModifier(LANCE_SPEED_DAMAGE_ID);
            }
            damageInstance.addTemporaryModifier(
                    new net.minecraft.entity.attribute.EntityAttributeModifier(
                            LANCE_SPEED_DAMAGE_ID, "lance_speed_damage", bonus,
                            net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public void onInitialize() {
        // 通用技能冷却池：注册下线清理
        org.examplee.wangsword.util.SkillCooldowns.init();
        // 服务器tick结束事件，遍历所有在线玩家
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            // 流星弓：检测箭是否命中，命中处召唤闪电
            org.examplee.wangsword.item.unique.WangBowItem.tickLightningArrows();
            // 流星弓 · 雷涌（X 键）：推进电流扩散动画
            org.examplee.wangsword.item.unique.WangBowItem.tickSurges();
            // 流星弓 · 雷涌：推进「过载」持续伤害
            org.examplee.wangsword.item.unique.WangBowItem.tickOverloads();
            // 流星弓 · 静电护体（被动）：手持时身边敌对生物挨电并被轻推
            org.examplee.wangsword.item.unique.WangBowItem.tickStaticAura(server);
            // 长矛：清理到期残影
            org.examplee.wangsword.item.unique.LanceItem.tickCharges(server);
            // 能源火箭：紫色拖尾 + 信标音效
            org.examplee.wangsword.item.unique.EnergyRocketItem.tickTrail(server);
            // 紫金剑：连击超时清零
            org.examplee.wangsword.item.unique.PurpleSwordItem.tick(server);
                org.examplee.wangsword.item.unique.TaiguSwordItem.tick(server);
                org.examplee.wangsword.util.TaiguStanceHandler.tick(server);
                org.examplee.wangsword.util.BloodPactHandler.tick(server);
                org.examplee.wangsword.util.PoisonDaggerCurse.tick(server);

            // 循环服务器里所有玩家
            for(PlayerEntity player : server.getPlayerManager().getPlayerList()){
                boolean holding = !player.isDead()
                        && player.getMainHandStack().getItem() instanceof WangShadowSwordItem;

                // 【临时测试】不给攻速加成，只清理可能残留在 NBT 里的旧修饰符
                var attackSpeedInst = player.getAttributeInstance(
                        net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED);
                if (attackSpeedInst != null && attackSpeedInst.getModifier(FAST_ATTACK_ID) != null) {
                    attackSpeedInst.removeModifier(FAST_ATTACK_ID);
                }

                // 滑翔中：免摔伤 + 自动清理
                if (GLIDING.contains(player.getUuid())) {
                    if (!ArmorRegistry.isFullSet(player) || player.isOnGround()) {
                        GLIDING.remove(player.getUuid());
                    } else {
                        player.fallDistance = 0.0F;
                    }
                }

                // 【紫金套装被动】穿齐四件紫金盔甲 → 防火
                boolean fullAmethystSet =
                        player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).getItem() == ArmorRegistry.WSARMOR_HELMET
                        && player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem() == ArmorRegistry.WSARMOR_CHESTPLATE
                        && player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS).getItem() == ArmorRegistry.WSARMOR_LEGGINGS
                        && player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET).getItem() == ArmorRegistry.WSARMOR_BOOTS;

                if (fullAmethystSet) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false), player);
                }

                // 【盾牌被动】持有盾牌（主手或副手）：顶级抗性 + 清除破盾冷却
                boolean holdingShield =
                        player.getMainHandStack().getItem() == org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD
                        || player.getOffHandStack().getItem() == org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD;

                if (holdingShield) {
                    // 抗性提升 V（100% 减伤）
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, true, false), player);
                    // 破盾后立刻清除冷却，等于无法被破盾
                    player.getItemCooldownManager().remove(org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD);
                }

                if (!holding) {
                    // 长矛被动：迅捷 + 速度加成伤害
                    handleLancePassive(player);
                    continue;
                }

                // 力量 IX
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 1200, 9, true, false), player);
                // 抗性提升 V（满减伤）
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1200, 4, true, false), player);
                // 生命恢复 VII（回血速度上限）
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1200, 6, true, false), player);
            }
        });

        BlocksRegistry.register();
        ToolRegistry.init();
        EntityRegistry.register(); //实体注册放到这里
        EntityRegistry.registerAttributes();
        EntityRegistry.registerSpawnRestrictions(); // 剑冢结构内的生成规则
        org.examplee.wangsword.util.GhostNightSpawner.register(); // 击杀守卫后的夜间地表刷新
        org.examplee.wangsword.registry.MobEggRegistry.init();
        ItemsRegistry.registerAll();
        // 高能煤炭：烧 64 个物品（12800 tick，普通煤是 1600）
        net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.add(
                ItemsRegistry.WS_HIGH_ENERGY_COAL, 12800);
        org.examplee.wangsword.registry.RecipeRegistry.register(); // 剑冢巨剑升阶配方类型
        SoundRegistry.registerAll();
        org.examplee.wangsword.registry.ParticleRegistry.register();
        TridentRegistry.init();
        LanceRegistry.init();
        HammerRegistry.init();
        ShieldRegistry.init();
        ScytheRegistry.init();
        org.examplee.wangsword.item.unique.BloodsailCutlassItem.init(); // 血帆弯刀：突进与血怒的 tick 驱动
        org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem.init(); // 熔核封岩巨剑：喷发与冲锋的 tick 驱动
        DaggerRegistry.init();
        WitherKillerRegistry.init();
        EnchantmentRegistry.init();
        org.examplee.wangsword.util.MagnetHandler.init();
        org.examplee.wangsword.util.SoulbearerHandler.init();
        org.examplee.wangsword.util.HuntsoulHandler.init();
        org.examplee.wangsword.util.EchoNecklaceHandler.init();
        org.examplee.wangsword.util.WardHandler.init(
                ItemsRegistry.WS_CHARM_EMBER,
                ItemsRegistry.WS_CHARM_TIDE,
                ItemsRegistry.WS_CHARM_STARFALL,
                ItemsRegistry.WS_CHARM_THUNDER,
                ItemsRegistry.WS_CHARM_BLAST);
        org.examplee.wangsword.util.WitherTetherHandler.init();
        org.examplee.wangsword.util.WitherFlightDisable.init();
        org.examplee.wangsword.util.BloodPactHandler.init();
        org.examplee.wangsword.util.AvalonHandler.init();
        org.examplee.wangsword.util.OathBarrierHandler.init();   // 守誓之阵（剑冢 · 守誓 X 键）
        org.examplee.wangsword.util.MoltenSetHandler.init();      // 熔岩套：熔核层 + 熔岩喷发
        org.examplee.wangsword.util.FulgurSetHandler.init();      // 幽卫钢套：持剑增伤 + 火免 + 幽卫飞剑
        org.examplee.wangsword.util.FlameBladeHandler.init();    // 通天火柱 / 烈火喷射（烈火大刀）
        org.examplee.wangsword.util.FrostBladeHandler.init();    // 霜环 / 冰棱穿刺（冰霜之刃）
        org.examplee.wangsword.util.ThunderKatanaHandler.init(); // 雷纹 / 雷引 / 天罚（雷电太刀）
        org.examplee.wangsword.util.SuperLightningGemHandler.init(); // 超级雷电宝石：手持放电 / 雷雨天必被劈
        org.examplee.wangsword.registry.EffectRegistry.init();
        org.examplee.wangsword.util.ExcaliburSynergyHandler.init();
        org.examplee.wangsword.util.AccessoryTicker.init();
        ArmorRegistry.init();
        BowRegistery.register();

        // 【左键剑气】接收客户端发来的开火请求：网络线程 -> 主线程执行
        ServerPlayNetworking.registerGlobalReceiver(WangShadowSwordItem.SLASH_FIRE_PACKET,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> WangShadowSwordItem.handleFireRequest(player)));

        // 【黑龙弩】左键连发开火请求
        ServerPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.item.unique.FatalisCrossbowItem.FIRE_PACKET,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> org.examplee.wangsword.item.unique.FatalisCrossbowItem.handleFire(player)));
        // 【阿瓦隆】Z 键展开请求：背包里有剑鞘才生效
        ServerPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.util.AvalonHandler.ACTIVATE_PACKET,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> org.examplee.wangsword.util.AvalonHandler.handleActivate(player)));

        // 【雷电法杖】囚笼期间左键放电
        ServerPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.item.unique.ThunderStaffItem.CAGE_HIT_PACKET,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> org.examplee.wangsword.item.unique.ThunderStaffItem.onCageHit(player)));

        // 【火焰法杖 · 被动燎原】被点着的目标死亡时炸火星
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, source) ->
                        org.examplee.wangsword.item.unique.FlameStaffItem.onBurnDeath(entity));

        // 【X 键大招】读取 boolean（是否蓄满），再在主线程生成剑气
        ServerPlayNetworking.registerGlobalReceiver(WangShadowSwordItem.ULT_PACKET,
                (server, player, handler, buf, sender) -> {
                    String itemId = buf.readString();
                    boolean charged = buf.readBoolean();
                    server.execute(() -> {
                        // 交给调度中心，并校验手持武器是否一致
                        SkillHandler.handleUltimate(player, charged, itemId);
                    });
                });

        // 【盾牌】反伤：持盾挨打时，把伤害反弹给攻击者
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return true;
                    if (!(source.getAttacker() instanceof net.minecraft.entity.LivingEntity attacker)) return true;
                    if (attacker == player) return true;

                    boolean hasShield =
                            player.getMainHandStack().getItem() == org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD
                            || player.getOffHandStack().getItem() == org.examplee.wangsword.registry.ShieldRegistry.WS_SHIELD;
                    if (!hasShield) return true;

                    float reflect = Math.max(4.0F, amount * 1.5F);
                    attacker.damage(player.getDamageSources().thorns(player), reflect);
                    return true;
                });
        // 【守誓】举剑格挡：原版 damageShield 只认 Items.SHIELD，自定义物品当盾用不会掉耐久。
        // 这里按"正面挡住一次扣 1 点"补上；创造模式由 ItemStack#damage 自行跳过。
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return true;
                    if (!player.isBlocking()) return true;
                    net.minecraft.item.ItemStack active = player.getActiveItem();
                    if (!active.isOf(ItemsRegistry.WS_SWORD_TOMB_OATH)) return true;

                    // 只算正面：角度口径和原版 blockedByShield 对齐，背后挨打不该磨损剑身
                    net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0F);
                    net.minecraft.util.math.Vec3d toSource = source.getPosition().subtract(player.getPos());
                    if (toSource.lengthSquared() < 1.0E-4D
                            || look.dotProduct(toSource.normalize()) < 0.0D) {
                        return true;
                    }
                    active.damage(1, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
                    return true;
                });
        // 【冰霜法杖·霜甲】持杖挨近战：冰封攻击者 1 秒 + 自身 2 秒抗性 I，5 秒内置冷却
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return true;
                    if (!player.getMainHandStack()
                            .isOf(org.examplee.wangsword.registry.ItemsRegistry.WS_FROST_STAFF)) return true;
                    if (!(source.getAttacker() instanceof net.minecraft.entity.LivingEntity attacker)) return true;
                    if (attacker == player) return true;
                    // 只算近战：5 格内
                    if (attacker.squaredDistanceTo(player) > 25.0D) return true;
                    org.examplee.wangsword.item.unique.FrostStaffItem.tryFrostGuard(player, attacker);
                    return true;
                });
        // 【太古残骸】只在末地生成
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheEnd(),
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES,
                net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.PLACED_FEATURE,
                        new net.minecraft.util.Identifier("wangsword", "ws_archean_debris_ore_placed")));
        // 【燃素矿】主世界：深层 y < 0，沙漠额外加密
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                "ws_pyron_ore_placed",
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES);
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                "ws_pyron_ore_deep_placed",
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES);
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(
                        net.minecraft.world.biome.BiomeKeys.DESERT),
                "ws_pyron_ore_desert_placed",
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES);
        // 【下界燃素矿】完全对齐原版下界金矿：
        //   普通下界群系每区块 10 次，玄武岩三角洲单独换成 20 次的版本（不叠加）。
        //   高度取自世界上下边界内缩 10 格，和 ore_gold_nether 一致。
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheNether()
                        .and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.excludeByKey(
                                net.minecraft.world.biome.BiomeKeys.BASALT_DELTAS)),
                "ws_pyron_ore_nether_placed",
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES);
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(
                        net.minecraft.world.biome.BiomeKeys.BASALT_DELTAS),
                "ws_pyron_ore_basalt_placed",
                net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES);
        // 【药草】生成条件与原版草一致：主世界所有生物群系，地表成片
        addPlacedFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                "ws_herb_patch_placed",
                net.minecraft.world.gen.GenerationStep.Feature.VEGETAL_DECORATION);
        // 【紫金胸甲】四件套滑翔：服务端真正启动原版滑翔，客户端自动同步
        ServerPlayNetworking.registerGlobalReceiver(ArmorRegistry.ELYTRA_PACKET,
                (server, player, handler, buf, sender) -> {
                    boolean on = buf.readBoolean();
                    server.execute(() -> {
                        if (!on) {
                            GLIDING.remove(player.getUuid());
                            return;
                        }
                        if (!ArmorRegistry.isFullSet(player)) return;
                        if (player.isOnGround()) return;
                        if (player.getAbilities().flying) return;   // 创造飞行时不启动
                        GLIDING.add(player.getUuid());
                        player.startFallFlying();      // 真正进入滑翔
                        player.fallDistance = 0.0F;
                    });
                });
        // 【紫金滑翔】用烟花推进：原版只在 isFallFlying 时允许，这里自己实现
        ServerPlayNetworking.registerGlobalReceiver(ArmorRegistry.FIREWORK_PACKET,
                (server, player, handler, buf, sender) -> server.execute(() -> {
                    if (!ArmorRegistry.isFullSet(player)) return;
                    if (player.isOnGround()) return;

                    net.minecraft.item.ItemStack stack = player.getMainHandStack();
                    if (!stack.isOf(net.minecraft.item.Items.FIREWORK_ROCKET)) return;

                    net.minecraft.server.world.ServerWorld world =
                            (net.minecraft.server.world.ServerWorld) player.getWorld();

                    // 音效
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

                    // 生成烟花实体（视觉）
                    world.spawnEntity(new net.minecraft.entity.projectile.FireworkRocketEntity(
                            world, stack.copy(), player));

                    // 消耗
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                }));
        // 【火力全开】模式切换
        org.examplee.wangsword.util.AoeHandler.init();
        ServerPlayNetworking.registerGlobalReceiver(
                org.examplee.wangsword.util.AoeHandler.MODE_PACKET,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> org.examplee.wangsword.util.AoeHandler.toggle(player)));
        // 【紫金剑】攻击前判定处决
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
                    if (!world.isClient() && entity instanceof net.minecraft.entity.LivingEntity living) {
                        org.examplee.wangsword.item.unique.PurpleSwordItem.beforeAttack(player, living);
                        org.examplee.wangsword.item.unique.TaiguSwordItem.beforeAttack(player, living);
                    }
                    return net.minecraft.util.ActionResult.PASS;
                });
        LOGGER.info("Wangsword MOD 已加载!");

        // 【黑龙弩】持弩时禁用近战伤害（只允许射箭）
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)
                            && source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity sp
                            && sp.getMainHandStack().getItem()
                            instanceof org.examplee.wangsword.item.unique.FatalisCrossbowItem) {
                        return false;
                    }
                    return true;
                });

        // 【雷电宝石·途径 D】被雷劈时，主手拿着未激活宝石就把它激活
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (entity instanceof net.minecraft.server.network.ServerPlayerEntity sp
                            && source.isOf(net.minecraft.entity.damage.DamageTypes.LIGHTNING_BOLT)) {
                        net.minecraft.item.ItemStack held = sp.getMainHandStack();
                        if (held.isOf(org.examplee.wangsword.registry.ItemsRegistry.WS_NOLIGHTNINGSTONE)) {
                            sp.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
                                    new net.minecraft.item.ItemStack(
                                            org.examplee.wangsword.registry.ItemsRegistry.WS_LIGHTNINGSTONE,
                                            held.getCount()));
                        }
                    }
                    return true;
                });

        // 【凋灵杀手】持有者免疫凋零效果伤害
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (entity.getWorld().isClient()) return true;
                    if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return true;
                    if (!source.isOf(net.minecraft.entity.damage.DamageTypes.WITHER)) return true;
                    return !player.getMainHandStack().isOf(
                            org.examplee.wangsword.registry.WitherKillerRegistry.WS_WITHERSPAWN_KILLER);
                });
        // 【击飞附魔】命中时把目标打上天，高度随等级变化
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (entity.getWorld().isClient()) return true;
                    if (!(source.getAttacker() instanceof net.minecraft.entity.LivingEntity attacker)) return true;
                    if (entity == attacker) return true;

                    int level = net.minecraft.enchantment.EnchantmentHelper.getLevel(
                            org.examplee.wangsword.registry.EnchantmentRegistry.LAUNCH,
                            attacker.getMainHandStack());
                    if (level <= 0) return true;

                    double vy = org.examplee.wangsword.enchantment.LaunchEnchantment.launchVelocity(level);
                    net.minecraft.util.math.Vec3d v = entity.getVelocity();
                    entity.setVelocity(v.x, vy, v.z);
                    // 服务端改了速度要标记，否则不会同步给客户端
                    entity.velocityModified = true;
                    entity.setOnGround(false);
                    if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                                entity.getX(), entity.getY() + 0.2D, entity.getZ(),
                                8, 0.3D, 0.1D, 0.3D, 0.05D);
                    }
                    return true;
                });
        // 【死花短刀】攻击他人时，伤害全部转移到自己身上
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (entity.getWorld().isClient()) return true;
                    if (!(source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return true;
                    if (!sp.getMainHandStack().isOf(
                            org.examplee.wangsword.registry.DaggerRegistry.WS_POISONDAGGER)) return true;
                    if (entity == sp) return true;

                    // 反噬伤害统一走结算器：只按实际扣血计，避免被护甲/吸收挡掉还能刷
                    org.examplee.wangsword.util.PoisonDaggerCurse.applyBacklash(sp, amount);
                    return false;
                });
        // 【末地合金盔甲】套装被动：抗性 III / 免摔落 / 创造飞行 / 濒死护盾
        org.examplee.wangsword.util.TaiguSetHandler.init();
    }

    /** 把一张 placed_feature 注入到指定生物群系的对应生成步 */
    private static void addPlacedFeature(
            java.util.function.Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> selector,
            String placedId,
            net.minecraft.world.gen.GenerationStep.Feature step) {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                selector,
                step,
                net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.PLACED_FEATURE,
                        new net.minecraft.util.Identifier("wangsword", placedId)));
    }
}
