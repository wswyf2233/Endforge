package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.effect.FrozenEffect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 冰霜之刃 · X 键「冰棱穿刺」的冰棱本体。
 *
 * <p>和霜环、法杖那种「开火瞬间算完伤害」的做法不同，这根冰棱是**真的在飞**：
 * 每 tick 前进 {@link #SPEED} 格，用整段线段做扫掠检测，所以它会迟到、会被墙挡住，
 * 玩家能看着它飞出去——这是"穿刺"这个读法成立的前提。
 *
 * <p><b>穿透</b>：命中生物只结算一次（{@link #hit} 记 UUID），冰棱本身继续飞，
 * 一路穿到墙或射程尽头。所以站成一排的怪会被一根冰棱全部串起来冻住。
 *
 * <p>位置记的是**尾部**，不是中心：这样实体的移动轨迹就是冰棱扫过的路径，
 * 和扫掠判定的起止点完全一致，不会出现"看着没碰到却掉血"。
 */
public class FrostSpikeEntity extends Entity {

    /**
     * 飞行速度（格/tick）。
     *
     * <p>原来 1.35（27 格/秒），配合 12 tick 的存活只有 0.6 秒——从眼前到射程尽头
     * 一眨眼就没了，玩家根本看不清飞过去的是什么。降到 0.92，存活同时拉长，
     * 射程不变，但飞行时间接近 1 秒，肉眼能跟住。
     */
    public static final double SPEED = 0.92D;
    /** 存活 tick：0.92 × 18 ≈ 16.5 格射程（和原来持平），飞行约 0.9 秒 */
    public static final int MAX_LIFE = 18;
    /**
     * 单次命中伤害。
     *
     * <p>原来是 5 点——7 秒冷却打 5 点，四发才够打死一只僵尸，作为 X 技能太廉价。
     * 提到 15：一发打掉僵尸七成血，配合 2.5 秒冰封，补一刀就收。穿透多目标保留，
     * 打一排每只 15 点，这才是控制该有的份量。
     */
    public static final float DAMAGE = 15.0F;
    /** 命中附带冰封：2.5 秒 */
    public static final int FREEZE_TICKS = 50;

    /** 飞行方向（必须同步，否则客户端画出来的朝向不会跟着变） */
    private static final TrackedData<Float> FWD_X =
            DataTracker.registerData(FrostSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Y =
            DataTracker.registerData(FrostSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Z =
            DataTracker.registerData(FrostSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private UUID ownerUuid;
    private int life;
    /** 已经穿过的目标，保证同一根冰棱对同一个目标只结算一次 */
    private final Set<UUID> hit = new HashSet<>();

    public FrostSpikeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setOwner(LivingEntity owner) {
        if (owner != null) {
            this.ownerUuid = owner.getUuid();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }

        this.life++;
        if (this.life > MAX_LIFE) {
            this.discard();
            return;
        }

        Vec3d dir = this.getDirection();
        Vec3d start = this.getPos();
        Vec3d end = start.add(dir.multiply(SPEED));

        // ---------- 1. 方块：沿线采样，撞墙就停在墙前 ----------
        int steps = (int) Math.ceil(SPEED / 0.25D);
        for (int i = 1; i <= steps; i++) {
            Vec3d p = start.add(dir.multiply(i * 0.25D));
            BlockPos bp = BlockPos.ofFloored(p);
            var state = this.getWorld().getBlockState(bp);
            if (!state.isAir() && state.getFluidState().isEmpty()
                    && state.isSolidBlock(this.getWorld(), bp)) {
                this.setPosition(p.x - dir.x * 0.25D, p.y - dir.y * 0.25D, p.z - dir.z * 0.25D);
                this.burst(false);
                return;
            }
        }

        // ---------- 2. 生物：整段线段扫掠，命中只结算不清除（穿透） ----------
        Box sweep = new Box(start, end).expand(0.55D);
        for (Entity entity : this.getWorld().getOtherEntities(this, sweep)) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            // 和霜环保持一致：只打生物，不误伤玩家（联机时一个技能不该把队友穿了）
            if (target instanceof net.minecraft.entity.player.PlayerEntity) {
                continue;
            }
            if (this.ownerUuid != null && target.getUuid().equals(this.ownerUuid)) {
                continue;
            }
            if (this.hit.contains(target.getUuid())) {
                continue;
            }

            // 碰撞箱稍微放大，给冰棱一点体积；必须真被这段线段穿过才算命中
            Box hitbox = target.getBoundingBox().expand(0.30D);
            boolean touched = hitbox.raycast(start, end).isPresent()
                    || hitbox.contains(start)
                    || hitbox.contains(end);
            if (!touched) {
                continue;
            }

            this.hit.add(target.getUuid());

            DamageSource source = this.getDamageSources().thrown(this, this.getOwnerEntity());
            // 主伤害刚被砍过的时候还压着无敌帧，不清零这 5 点会被吞掉
            target.timeUntilRegen = 0;
            target.damage(source, DAMAGE);
            FrozenEffect.freezeTicks(target, FREEZE_TICKS);

            this.hitFeedback(target);
        }

        this.setPosition(end.x, end.y, end.z);
        this.spawnTrail();
    }

    public Vec3d getDirection() {
        Vec3d dir = new Vec3d(
                this.dataTracker.get(FWD_X),
                this.dataTracker.get(FWD_Y),
                this.dataTracker.get(FWD_Z));
        return dir.lengthSquared() < 1.0E-6D ? new Vec3d(0.0D, 0.0D, 1.0D) : dir.normalize();
    }

    public void setDirection(Vec3d dir) {
        if (dir.lengthSquared() > 1.0E-6D) {
            Vec3d nd = dir.normalize();
            this.dataTracker.set(FWD_X, (float) nd.x);
            this.dataTracker.set(FWD_Y, (float) nd.y);
            this.dataTracker.set(FWD_Z, (float) nd.z);
        }
    }

    private LivingEntity getOwnerEntity() {
        if (this.ownerUuid == null || !(this.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        return world.getEntity(this.ownerUuid) instanceof LivingEntity living ? living : null;
    }

    /** 命中单个目标：身上炸一簇碎冰，加一记偏尖的玻璃裂响 */
    private void hitFeedback(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double x = target.getX();
        double y = target.getBodyY(0.5D);
        double z = target.getZ();

        world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 12, 0.26D, 0.32D, 0.26D, 0.10D);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 8, 0.22D, 0.26D, 0.22D, 0.02D);
        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_HIT,
                SoundCategory.PLAYERS, 0.7F, 1.9F);
    }

    /** 飞行拖尾：身后的霜屑，让轨迹能被看见 */
    private void spawnTrail() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d dir = this.getDirection();
        // 从尾部往回补三段，间距跟着速度走，刚好接上上一 tick 的位置。
        // 速度降到 0.92 之后间距也必须跟着缩，否则粒子会稀得连不成一条轨迹。
        for (int i = 0; i < 3; i++) {
            double back = 0.25D + i * 0.35D;
            double x = this.getX() - dir.x * back;
            double y = this.getY() - dir.y * back;
            double z = this.getZ() - dir.z * back;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 3, 0.12D, 0.12D, 0.12D, 0.01D);
        }
        if (this.life % 2 == 0) {
            world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                    this.getX(), this.getY(), this.getZ(), 3, 0.14D, 0.14D, 0.14D, 0.05D);
        }
    }

    /** 收尾：撞墙或耗尽射程，在当前位置散掉 */
    private void burst(boolean hitEntity) {
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                    this.getX(), this.getY(), this.getZ(),
                    hitEntity ? 14 : 7, 0.22D, 0.22D, 0.22D, 0.08D);
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    this.getX(), this.getY(), this.getZ(),
                    hitEntity ? 10 : 5, 0.18D, 0.18D, 0.18D, 0.02D);
            if (hitEntity) {
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 0.6F, 1.8F);
            }
        }
        this.discard();
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FWD_X, 0.0F);
        this.dataTracker.startTracking(FWD_Y, 0.0F);
        this.dataTracker.startTracking(FWD_Z, 1.0F);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
        Vec3d d = this.getDirection();
        nbt.putDouble("DX", d.x);
        nbt.putDouble("DY", d.y);
        nbt.putDouble("DZ", d.z);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
        if (nbt.contains("DX")) {
            this.dataTracker.set(FWD_X, (float) nbt.getDouble("DX"));
            this.dataTracker.set(FWD_Y, (float) nbt.getDouble("DY"));
            this.dataTracker.set(FWD_Z, (float) nbt.getDouble("DZ"));
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }
}
