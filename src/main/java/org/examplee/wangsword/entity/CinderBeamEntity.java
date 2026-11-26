package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * 熔核巨兽的火焰激光。
 *
 * <p>参考远古守卫者的光束：一条从施法者身上某一点射出去的实体，长度由射线检测
 * 决定，途中每 4 tick 结算一次伤害。区别在于这里的激光<b>不跟踪</b>——方向在
 * 开火那一瞬间锁死，之后施法者转身、玩家跑动都不会改变射线。玩家侧移就能躲开，
 * 这是这场 BOSS 战里少有的、能被操作化解的招式。
 *
 * <p>两种形态由 {@link #KIND} 区分：
 * <ul>
 *   <li>{@link #KIND_HEAD}：attack1，从头部射出，细、伤害低、持续 34 tick</li>
 *   <li>{@link #KIND_CORE}：attack2，从胸口核心射出，粗一倍、伤害高、带推力</li>
 * </ul>
 *
 * <p>纯视觉部分（贴图、粗细、双层）在 {@code CinderBeamRenderer} 里，
 * 这个类只负责几何、伤害和粒子。
 */
public class CinderBeamEntity extends Entity {

    /** 从头部射出 */
    public static final int KIND_HEAD = 0;
    /** 从胸口核心射出 */
    public static final int KIND_CORE = 1;

    /** attack1：0.8s 起手到动画结束，共 34 tick */
    public static final int HEAD_LIFE = 34;
    /** attack2：1.1s 起手到动画结束，共 38 tick */
    public static final int CORE_LIFE = 38;

    /**
     * 发射点高度，按 geo 模型的骨骼 pivot 折算：
     * head 在模型 y=37（37/16 × 渲染放大 3 倍 ≈ 6.9 格），
     * heart 在 y=32（≈ 6.0 格）。碰撞箱 7.4 高，两者都对得上。
     */
    public static final double HEAD_HEIGHT = 6.9D;
    public static final double CORE_HEIGHT = 6.0D;

    /** 最大射程 */
    private static final double RANGE = 32.0D;

    /** 每多少 tick 结算一次伤害 */
    private static final int HIT_INTERVAL = 4;

    /** 射线半径：细激光也留一点余量，不然玩家左右挪半步就完全免疫了 */
    private static final double HEAD_RADIUS = 0.75D;
    private static final double CORE_RADIUS = 1.55D;

    /** 每跳伤害 = 施法者攻击力 × 这个系数 */
    private static final float HEAD_DAMAGE_RATIO = 0.22F;
    private static final float CORE_DAMAGE_RATIO = 0.35F;

    private static final int HEAD_FIRE_SECONDS = 3;
    private static final int CORE_FIRE_SECONDS = 5;

    private static final TrackedData<Float> LENGTH =
            DataTracker.registerData(CinderBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> KIND =
            DataTracker.registerData(CinderBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MAX_AGE =
            DataTracker.registerData(CinderBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER_ID =
            DataTracker.registerData(CinderBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** 开火瞬间锁定的方向，只在服务端用 */
    private Vec3d lockedDir;

    public CinderBeamEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
        this.setInvisible(true);
    }

    public void setKind(int kind) {
        this.dataTracker.set(KIND, kind);
    }

    public int getKind() {
        return this.dataTracker.get(KIND);
    }

    public void setMaxAge(int maxAge) {
        this.dataTracker.set(MAX_AGE, Math.max(1, maxAge));
    }

    public int getMaxAge() {
        return this.dataTracker.get(MAX_AGE);
    }

    public void setOwnerId(int id) {
        this.dataTracker.set(OWNER_ID, id);
    }

    public int getOwnerId() {
        return this.dataTracker.get(OWNER_ID);
    }

    public void setLength(float length) {
        this.dataTracker.set(LENGTH, length);
    }

    public float getLength() {
        return this.dataTracker.get(LENGTH);
    }

    /** 渲染用的半宽（格），attack2 的核心激光是 attack1 的近四倍粗 */
    public float getThickness() {
        return this.getKind() == KIND_CORE ? 0.78F : 0.20F;
    }

    /**
     * 生成时就锁定方向。
     *
     * <p>服务端在 spawn 之前调一次，这样实体包发出去的第一帧朝向就是对的；
     * 不这样做的话，客户端会先用默认朝向画一 tick，看起来像激光"甩"了一下。
     */
    public void lockDirection(Vec3d dir, float yaw, float pitch) {
        this.lockedDir = dir;
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (this.age > this.getMaxAge()) {
            this.discard();
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        // 方向只锁一次：之后施法者怎么转都不会拐弯
        if (this.lockedDir == null) {
            this.lockedDir = owner.getRotationVec(1.0F);
            this.setYaw(owner.getYaw());
            this.setPitch(owner.getPitch());
            this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 2.2F, 0.7F);
            this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.6F, 0.55F);
        }

        double height = this.getKind() == KIND_CORE ? CORE_HEIGHT : HEAD_HEIGHT;
        Vec3d start = new Vec3d(
                owner.getX() + this.lockedDir.x * 1.1D,
                owner.getY() + height + this.lockedDir.y * 1.1D,
                owner.getZ() + this.lockedDir.z * 1.1D);
        Vec3d end = start.add(this.lockedDir.multiply(RANGE));

        double length = RANGE;
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            BlockHitResult hit = serverWorld.raycast(new RaycastContext(
                    start, end, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                length = start.distanceTo(hit.getPos());
            }
        }

        this.setPosition(start.x, start.y, start.z);
        this.setLength((float) length);

        if (this.age % HIT_INTERVAL == 0) {
            this.hurtAlongBeam(owner, start, length);
        }
        this.spawnTrail(start, length);
        this.ambientSound();
    }

    /**
     * 沿射线结算伤害。
     *
     * <p>用「目标中心到射线的垂距」判定，而不是包围盒相交：巨兽的激光有固定半径，
     * 玩家侧身擦着边走该躲开就该躲开，用包围盒会让擦边也算命中。
     */
    private void hurtAlongBeam(LivingEntity owner, Vec3d start, double length) {
        if (!(this.getWorld() instanceof ServerWorld world) || this.lockedDir == null) {
            return;
        }
        boolean core = this.getKind() == KIND_CORE;
        double radius = core ? CORE_RADIUS : HEAD_RADIUS;
        float ratio = core ? CORE_DAMAGE_RATIO : HEAD_DAMAGE_RATIO;
        float damage = (float) owner.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * ratio;

        Vec3d end = start.add(this.lockedDir.multiply(length));
        Box zone = new Box(start, end).expand(radius + 1.5D);

        for (Entity entity : world.getOtherEntities(this, zone)) {
            if (!(entity instanceof LivingEntity target) || target == owner || !target.isAlive()) {
                continue;
            }
            if (target instanceof CinderColossusEntity) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            Vec3d center = new Vec3d(target.getX(),
                    target.getY() + target.getHeight() * 0.5D, target.getZ());
            Vec3d rel = center.subtract(start);
            double proj = rel.dotProduct(this.lockedDir);
            if (proj < 0.0D || proj > length) {
                continue;
            }
            double perp = rel.subtract(this.lockedDir.multiply(proj)).length();
            if (perp > radius + target.getWidth() * 0.35D) {
                continue;
            }

            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(owner), damage)) {
                continue;
            }
            target.setOnFireFor(core ? CORE_FIRE_SECONDS : HEAD_FIRE_SECONDS);
            if (core) {
                // 粗激光有推力，细激光没有
                target.takeKnockback(0.22D, -this.lockedDir.x, -this.lockedDir.z);
            }
        }
    }

    /** 沿光束撒火焰粒子，起点再补一团烟 */
    private void spawnTrail(Vec3d start, double length) {
        if (!(this.getWorld() instanceof ServerWorld world) || this.lockedDir == null) {
            return;
        }
        boolean core = this.getKind() == KIND_CORE;
        double step = core ? 1.2D : 1.6D;
        int steps = (int) Math.max(1, length / step);

        for (int i = 0; i <= steps; i++) {
            double t = length * i / steps;
            double x = start.x + this.lockedDir.x * t;
            double y = start.y + this.lockedDir.y * t;
            double z = start.z + this.lockedDir.z * t;
            double spread = core ? 0.35D : 0.18D;

            world.spawnParticles(ParticleTypes.FLAME, x, y, z,
                    1, spread, spread, spread, 0.012D);
            if (i % 2 == 0) {
                world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z,
                        1, spread * 0.8D, spread * 0.8D, spread * 0.8D, 0.008D);
            }
            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.LAVA, x, y, z,
                        1, spread * 0.6D, spread * 0.6D, spread * 0.6D, 0.0D);
            }
            if (core && i % 3 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z,
                        1, spread, spread, spread, 0.01D);
            }
        }

        // 枪口一团火：让"从头部/胸口射出来"这件事看得见
        world.spawnParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y, start.z,
                core ? 6 : 3, 0.25D, 0.25D, 0.25D, 0.02D);
    }

    /** 每 6 tick 补一声燃烧底噪，不然一条 3 秒的激光会显得太静 */
    private void ambientSound() {
        if (this.age % 6 == 0) {
            this.playSound(SoundEvents.BLOCK_FIRE_AMBIENT, 1.6F, 0.75F);
        }
    }

    private LivingEntity getOwner() {
        Entity entity = this.getWorld().getEntityById(this.getOwnerId());
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LENGTH, 0.0F);
        this.dataTracker.startTracking(KIND, KIND_HEAD);
        this.dataTracker.startTracking(MAX_AGE, HEAD_LIFE);
        this.dataTracker.startTracking(OWNER_ID, -1);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("BeamLength", this.getLength());
        nbt.putInt("BeamKind", this.getKind());
        nbt.putInt("BeamMaxAge", this.getMaxAge());
        nbt.putInt("BeamOwner", this.getOwnerId());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setLength(nbt.getFloat("BeamLength"));
        this.setKind(nbt.getInt("BeamKind"));
        this.setMaxAge(nbt.getInt("BeamMaxAge"));
        this.setOwnerId(nbt.getInt("BeamOwner"));
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }

    /** 激光不应该被当作可以推动/踩踏的实体 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }
}
