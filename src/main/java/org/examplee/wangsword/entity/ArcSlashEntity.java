package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.examplee.wangsword.registry.SoundRegistry;

import java.util.HashSet;
import java.util.Set;

public class ArcSlashEntity extends Entity {
    private LivingEntity caster;
    private final float damage;
    private int lifeTick;
    public static final int MAX_LIFE = 60; // 存活 3 秒（20 tick = 1 秒）
    /** 是否破坏方块：Shift+右键的剑气 = true，左键连发的剑气 = false */
    private boolean breakBlocks = true;
    /** 飞行速度（格/tick）：普通剑气 0.8，左键粒子剑气 1.6 */
    private double moveSpeed = 0.8;
    /** 本单位自己的寿命（tick）：普通剑气 60，左键粒子剑气 30 */
    private int maxLife = MAX_LIFE;
    /** 距离施法者这么近的目标交给近战，剑气不再造成伤害（避免抢无敌帧） */
    private static final double MELEE_EXCLUSION = 4.0;
    /** 每道剑气自己的近战排除半径：火刀的十字剑气要能贴脸打 */
    private double meleeExclusion = MELEE_EXCLUSION;

    private static final TrackedData<Float> FORWARD_X = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FORWARD_Y = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FORWARD_Z = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> UP_X = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> UP_Y = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> UP_Z = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 是否是"纯粒子"剑气（左键版）：true 时渲染器不画，改由服务端铺原版粒子 */
    private static final TrackedData<Boolean> PARTICLE_MODE = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 是否用金色贴图（誓约胜利之剑）：true 时渲染器换成 slash_beam_gold */
    private static final TrackedData<Boolean> GOLDEN = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 火焰剑气：烫到生物点火，掠过方块表面也点火（不破坏地形） */
    private static final TrackedData<Boolean> FLAME = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 是否在命中/消散时引爆一根金色光柱（誓约胜利之剑） */
    private static final TrackedData<Boolean> BURST_ON_END = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 绕飞行轴的自转角度（度）：X 形剑气靠这个把两片错开 */
    private static final TrackedData<Float> ROLL_DEGREES = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** 额外放大倍率：蓄力大招用 */
    private static final TrackedData<Float> SCALE_MULT = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 飞行速度（同步到客户端，客户端据此做本地预测，否则渲染会慢半拍） */
    private static final TrackedData<Float> MOVE_SPEED = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 寿命（同步到客户端，渲染的淡出用这个算） */
    private static final TrackedData<Integer> MAX_LIFE_TRACKED = DataTracker.registerData(ArcSlashEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final Set<BlockPos> destroyedBlocks = new HashSet<>();

    public ArcSlashEntity(EntityType<? extends ArcSlashEntity> type, World world) {
        super(type, world);
        this.caster = null;
        this.damage = 0;
    }

    public ArcSlashEntity(EntityType<ArcSlashEntity> type, LivingEntity caster, World world, float damage, Vec3d shootForwardIn, Vec3d shootCameraUpIn) {
        super(type, world);
        this.caster = caster;
        this.damage = damage;
        this.lifeTick = 0;

        if (!world.isClient()) {
            Vec3d forward = shootForwardIn.normalize();
            Vec3d up = shootCameraUpIn.normalize();
            this.dataTracker.set(FORWARD_X, (float) forward.x);
            this.dataTracker.set(FORWARD_Y, (float) forward.y);
            this.dataTracker.set(FORWARD_Z, (float) forward.z);
            this.dataTracker.set(UP_X, (float) up.x);
            this.dataTracker.set(UP_Y, (float) up.y);
            this.dataTracker.set(UP_Z, (float) up.z);
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FORWARD_X, 0.0f);
        this.dataTracker.startTracking(FORWARD_Y, 0.0f);
        this.dataTracker.startTracking(FORWARD_Z, 1.0f);
        this.dataTracker.startTracking(UP_X, 0.0f);
        this.dataTracker.startTracking(UP_Y, 1.0f);
        this.dataTracker.startTracking(UP_Z, 0.0f);
        this.dataTracker.startTracking(PARTICLE_MODE, false);
        this.dataTracker.startTracking(GOLDEN, false);
        this.dataTracker.startTracking(FLAME, false);
        this.dataTracker.startTracking(BURST_ON_END, false);
        this.dataTracker.startTracking(ROLL_DEGREES, 0.0f);
        this.dataTracker.startTracking(SCALE_MULT, 1.0f);
        this.dataTracker.startTracking(MOVE_SPEED, 0.8F);
        this.dataTracker.startTracking(MAX_LIFE_TRACKED, 20);
    }

    /** 剑气能飞出 60 多格，默认按 0.5 格碰撞盒算只有 32 格，飞一半就被判太远 */
    @Override
    public boolean shouldRender(double distance) {
        return distance < 128.0D * 128.0D;
    }

    @Override
    public void tick() {
        super.tick();
        lifeTick++;
        World world = this.getWorld();

        // ===== 客户端：本地预判移动，避免只靠服务端同步导致渲染慢半拍 =====
        if (world.isClient()) {
            if (lifeTick == 1) {
                // 和服务端一致：出生第一帧往前瞬移 1.5 格
                Vec3d fwd = getForwardVector();
                this.setPosition(getX() + fwd.x * 1.5, getY() + fwd.y * 1.5, getZ() + fwd.z * 1.5);
            }
            Vec3d step = getForwardVector().multiply(getMoveSpeed());
            this.setPosition(getX() + step.x, getY() + step.y, getZ() + step.z);
            return;
        }

        // ===== 服务端逻辑 =====
        if (caster == null || caster.isDead()) {
            this.discard();
            return;
        }

        if(lifeTick == 1){
            // 剑气音效：左键的粒子模式已在点击瞬间播放，这里只处理其它形态
            if (!this.isParticleMode()) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        org.examplee.wangsword.registry.SoundRegistry.SWORD_WS,
                        caster.getSoundCategory(),
                        1.0F,
                        1.0F
                );
            }

            // 【解决糊脸问题】在出生的第一帧，直接往前瞬移 1.5 格
            Vec3d forward = new Vec3d(dataTracker.get(FORWARD_X), dataTracker.get(FORWARD_Y), dataTracker.get(FORWARD_Z));
            this.setPosition(this.getX() + forward.x * 1.5, this.getY() + forward.y * 1.5, this.getZ() + forward.z * 1.5);
        }

        ServerWorld sw = (ServerWorld) world;

        // 方块破坏逻辑：范围与月牙外缘契合
        // 左键连发的剑气 breakBlocks = false，这里直接跳过
        // 火焰剑气不破坏地形，只掠过方块表面点火
        if (lifeTick < this.maxLife && (this.breakBlocks || this.isFlame())) {
            // 判定范围跟随视觉缩放（渲染基准 2.2 × scaleMult）
            float scale = this.getScaleMult();
            Box breakAABB = this.getBoundingBox().expand(1.9 * scale, 1.1 * scale, 1.9 * scale);
            BlockPos.stream(breakAABB).forEach(pos -> {
                if (destroyedBlocks.contains(pos)) return;

                if (this.isFlame() && !this.breakBlocks) {
                    BlockPos firePos = pos.up();
                    if (sw.isAir(firePos)) {
                        BlockState fire = AbstractFireBlock.getState(sw, firePos);
                        if (fire.canPlaceAt(sw, firePos)) {
                            sw.setBlockState(firePos, fire);
                            destroyedBlocks.add(pos);
                        }
                    }
                    return;
                }

                BlockState state = sw.getBlockState(pos);
                // 除了基岩，其它方块一律破坏
                if (state.isOf(Blocks.BEDROCK)) {
                    return;
                }

                if (caster instanceof ServerPlayerEntity player) {
                    // false = 破坏但不掉落物品
                    sw.breakBlock(pos, false, player);
                    destroyedBlocks.add(pos);
                }
            });
        }

        // 剑气移动逻辑
        double prevX = this.getX();
        double prevY = this.getY();
        double prevZ = this.getZ();

        Vec3d moveVec = new Vec3d(dataTracker.get(FORWARD_X), dataTracker.get(FORWARD_Y), dataTracker.get(FORWARD_Z)).multiply(this.getMoveSpeed());
        this.setVelocity(moveVec);
        this.setPosition(prevX + moveVec.x, prevY + moveVec.y, prevZ + moveVec.z);

        // 月牙本体由 ArcSlashEntityRenderer 负责；左键版只在尾部补龙息
        if (this.isParticleMode()) {
            this.spawnTailParticles(sw, this.getX(), this.getY(), this.getZ());
        } else if (this.isFlame()) {
            // 火焰剑气：整条光刃往外燎火星
            float s = this.getScaleMult();
            sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
                    6, 0.9D * s, 0.9D * s, 0.9D * s, 0.02D);
            sw.spawnParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY(), this.getZ(),
                    3, 0.6D * s, 0.6D * s, 0.6D * s, 0.01D);
        }

        // 【终爆】只在命中生物、或飞到寿命尽头时引爆（撞方块不爆：贴着草地/植物飞会被误判）
        if (this.isBurstOnEnd()) {
            boolean struck = false;
            // 引爆判定只贴剑气本体，不能跟着视觉缩放一起放大：
            // 3.5 倍缩放时 expand 出去有 7 格，出生点旁边随便有只怪就当场引爆。
            Box probe = this.getBoundingBox().expand(0.6D, 0.9D, 0.6D);
            for (Entity entity : sw.getOtherEntities(this, probe)) {
                if (entity instanceof LivingEntity living && living != caster) {
                    struck = true;
                    break;
                }
            }
            if (struck || lifeTick >= this.maxLife) {
                goldenExplosion(sw, this.getPos(), this.caster);
                this.discard();
                return;
            }
        }

        // 伤害判定：和月牙的横向尺寸（半径 3.9）对齐，竖直方向略收
        // 距离施法者 4 格以内的目标交给近战，剑气不打，避免抢无敌帧
        // 伤害为 0 表示纯特效剑气（誓约胜利之剑的视觉层），不做任何伤害判定
        Box hitBox = this.getBoundingBox().expand(2.0 * this.getScaleMult(), 2.4 * this.getScaleMult(), 2.0 * this.getScaleMult());
        for (Entity entity : (this.damage > 0.0F ? sw.getOtherEntities(this, hitBox) : java.util.List.<Entity>of())) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), damage);
                continue;
            }
            if (!(entity instanceof LivingEntity target) || target == caster) continue;
            // 4 格内交给近战，避免剑气抢无敌帧
            if (caster != null && target.squaredDistanceTo(caster) < this.meleeExclusion * this.meleeExclusion) continue;
            if (this.isFlame()) {
                target.setOnFireFor(4);
            }
            if (caster instanceof PlayerEntity player) {
                killTarget(target, world.getDamageSources().playerAttack(player), damage);
            }
        }

        if (lifeTick >= this.maxLife) {
            this.discard();
        }
    }

    /** 出生时记录的飞行方向（已同步到客户端，渲染器靠它决定朝向） */
    public Vec3d getForwardVector() {
        return new Vec3d(dataTracker.get(FORWARD_X), dataTracker.get(FORWARD_Y), dataTracker.get(FORWARD_Z));
    }

    /** 出生时记录的摄像机上方方向 */
    public Vec3d getUpVector() {
        return new Vec3d(dataTracker.get(UP_X), dataTracker.get(UP_Y), dataTracker.get(UP_Z));
    }

    /** 设置这道剑气是否会破坏方块（默认 true） */
    public void setBreakBlocks(boolean breakBlocks) {
        this.breakBlocks = breakBlocks;
    }

    /** 是否改用金色贴图（誓约胜利之剑） */
    public void setGolden(boolean golden) {
        this.dataTracker.set(GOLDEN, golden);
    }

    /** 火焰剑气：命中生物点火，掠过方块表面点火 */
    public void setFlame(boolean flame) {
        this.dataTracker.set(FLAME, flame);
    }

    public boolean isFlame() {
        return this.dataTracker.get(FLAME);
    }

    /** 近战排除半径：这个距离内的目标交给近战，剑气不判伤。默认 4 格。 */
    public void setMeleeExclusion(double radius) {
        this.meleeExclusion = radius;
    }

    public boolean isGolden() {
        return this.dataTracker.get(GOLDEN);
    }

    /** 设置是否在命中/消散时炸出金色光柱 */
    public void setBurstOnEnd(boolean burstOnEnd) {
        this.dataTracker.set(BURST_ON_END, burstOnEnd);
    }

    public boolean isBurstOnEnd() {
        return this.dataTracker.get(BURST_ON_END);
    }

    /** 金色终爆：柱状光效 + 大爆炸音效 + 范围伤害（不破坏地形） */
    public static void goldenExplosion(ServerWorld world, Vec3d center, Entity caster) {
        // 先摆一根实体金色光柱（几何面片，比粒子显眼得多）
        org.examplee.wangsword.entity.GoldenBurstEntity pillar =
                new org.examplee.wangsword.entity.GoldenBurstEntity(
                        org.examplee.wangsword.registry.EntityRegistry.GOLDEN_BURST, world);
        pillar.setPosition(center);
        world.spawnEntity(pillar);

        // 柱状光效：24 层圆环叠成一根金色光柱
        for (int layer = 0; layer < 24; layer++) {
            double y = center.y - 3.0D + layer * 0.85D;
            double radius = 4.6D * (1.0D - layer / 34.0D);
            for (int i = 0; i < 18; i++) {
                double angle = Math.PI * 2.0D * i / 18.0D;
                double px = center.x + Math.cos(angle) * radius;
                double pz = center.z + Math.sin(angle) * radius;
                world.spawnParticles(ParticleTypes.END_ROD, px, y, pz, 1, 0.0D, 0.02D, 0.0D, 0.0D);
                if (i % 2 == 0) {
                    world.spawnParticles(ParticleTypes.FIREWORK, px, y, pz, 1, 0.0D, 0.03D, 0.0D, 0.01D);
                }
                if (i % 3 == 0) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, y, pz, 1, 0.0D, 0.03D, 0.0D, 0.01D);
                }
            }
        }
        // 中心竖直光柱 + 地面冲击环
        world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + 1.5D, center.z,
                160, 3.2D, 9.0D, 3.2D, 0.14D);
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0D * i / 48.0D;
            world.spawnParticles(ParticleTypes.CLOUD,
                    center.x + Math.cos(angle) * 8.5D, center.y + 0.2D, center.z + Math.sin(angle) * 8.5D,
                    1, 0.0D, 0.02D, 0.0D, 0.01D);
        }
        world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 1.5D, center.z,
                5, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5D, center.z,
                3, 0.0D, 0.0D, 0.0D, 0.0D);

        // 范围伤害
        net.minecraft.entity.damage.DamageSource source = caster instanceof PlayerEntity player
                ? world.getDamageSources().playerAttack(player)
                : world.getDamageSources().magic();
        for (Entity entity : world.getOtherEntities(caster, new Box(center, center).expand(11.0D))) {
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(world.getDamageSources().magic(), 80.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target) || target == caster) {
                continue;
            }
            target.damage(source, 80.0F);
            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4D) {
                push = push.normalize().multiply(1.6D);
                target.addVelocity(push.x, 0.6D, push.z);
                target.velocityModified = true;
            }
        }

        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0F, 0.8F);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.6F, 1.2F);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.2F, 0.7F);
    }

    /** 设置飞行速度（格/tick） */
    public void setMoveSpeed(double moveSpeed) {
        this.moveSpeed = moveSpeed;
        this.dataTracker.set(MOVE_SPEED, (float) moveSpeed);
    }

    /** 当前飞行速度（客户端读同步值） */
    public double getMoveSpeed() {
        return this.dataTracker.get(MOVE_SPEED);
    }

    /** 设置这道剑气自己的寿命（tick） */
    public void setMaxLife(int maxLife) {
        this.maxLife = maxLife;
        this.dataTracker.set(MAX_LIFE_TRACKED, maxLife);
    }

    /** 这道剑气自己的寿命（tick），渲染器用来算渐隐 */
    public int getMaxLife() {
        return this.dataTracker.get(MAX_LIFE_TRACKED);
    }

    /** 是否走"纯粒子"渲染（左键版） */
    /**
     * 大招必杀：无视部位衰减、无敌阶段、伤害上限，直接打空血量并触发正常死亡（保留掉落）。
     */
    private void killTarget(LivingEntity target, net.minecraft.entity.damage.DamageSource source, float amount) {
        // 末影龙：直接按头部结算，绕开「身体 1/4 伤害」规则
        if (target instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon) {
            dragon.damagePart(dragon.head, source, amount);
            if (dragon.isAlive()) {
                dragon.setHealth(0.0F);
                dragon.onDeath(source);
            }
            return;
        }

        // 普通生物：先正常打一次（走掉落/击杀统计），没死就直接清空血量
        target.damage(source, amount);

        if (target.isAlive() && !target.isInvulnerableTo(source)) {
            target.setHealth(0.0F);
            target.onDeath(source);
        }
    }
    public boolean isParticleMode() {
        return this.dataTracker.get(PARTICLE_MODE);
    }

    public void setParticleMode(boolean particleMode) {
        this.dataTracker.set(PARTICLE_MODE, particleMode);
    }

    /** 绕飞行轴的自转角度（度） */
    public float getRollDegrees() {
        return this.dataTracker.get(ROLL_DEGREES);
    }

    public void setRollDegrees(float rollDegrees) {
        this.dataTracker.set(ROLL_DEGREES, rollDegrees);
    }

    /** 渲染时的额外放大倍率 */
    public float getScaleMult() {
        return this.dataTracker.get(SCALE_MULT);
    }

    public void setScaleMult(float scaleMult) {
        this.dataTracker.set(SCALE_MULT, scaleMult);
    }

    // ===== 【尾部龙息】可调参数 =====
    private static final int TAIL_POINTS = 16;         // 尾部采样点数（越密雾越浓）
    private static final double TAIL_RADIUS = 4.2;     // 贴着月牙内缘的位置
    private static final double TAIL_BACK = 0.6;       // 往飞行方向的反方向错开多少
    private static final double TAIL_SPREAD = 0.28;    // 粒子扩散范围

    /**
     * 只在月牙的"尾部"撒原版龙息粒子：位置在内缘后方一点，
     * 龙息会自己下沉、扩散，从侧面看能补出一点体积。
     */
    private void spawnTailParticles(ServerWorld sw, double cx, double cy, double cz) {
        Vec3d forward = new Vec3d(dataTracker.get(FORWARD_X), dataTracker.get(FORWARD_Y), dataTracker.get(FORWARD_Z));
        if (forward.lengthSquared() < 1.0E-6) return;
        forward = forward.normalize();

        Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        for (int j = 0; j <= TAIL_POINTS; j++) {
            double t = Math.PI * j / TAIL_POINTS; // 0 -> 180°
            double c = Math.cos(t) * TAIL_RADIUS;
            double s = Math.sin(t) * TAIL_RADIUS;

            double ox = right.x * c + forward.x * s - forward.x * TAIL_BACK;
            double oz = right.z * c + forward.z * s - forward.z * TAIL_BACK;

            sw.spawnParticles(ParticleTypes.DRAGON_BREATH,
                    cx + ox, cy, cz + oz,
                    1, TAIL_SPREAD, TAIL_SPREAD * 0.6, TAIL_SPREAD, 0.0);
        }
    }

    // NBT 序列化保持不变
    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        nbt.putFloat("sf_x", dataTracker.get(FORWARD_X));
        nbt.putFloat("sf_y", dataTracker.get(FORWARD_Y));
        nbt.putFloat("sf_z", dataTracker.get(FORWARD_Z));
        nbt.putFloat("cu_x", dataTracker.get(UP_X));
        nbt.putFloat("cu_y", dataTracker.get(UP_Y));
        nbt.putFloat("cu_z", dataTracker.get(UP_Z));
        nbt.putBoolean("break_blocks", this.breakBlocks);
    }

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        dataTracker.set(FORWARD_X, nbt.getFloat("sf_x"));
        dataTracker.set(FORWARD_Y, nbt.getFloat("sf_y"));
        dataTracker.set(FORWARD_Z, nbt.getFloat("sf_z"));
        dataTracker.set(UP_X, nbt.getFloat("cu_x"));
        dataTracker.set(UP_Y, nbt.getFloat("cu_y"));
        dataTracker.set(UP_Z, nbt.getFloat("cu_z"));
        this.breakBlocks = !nbt.contains("break_blocks") || nbt.getBoolean("break_blocks");
    }
}
