package org.examplee.wangsword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 雷霆贯穿：右键扔出去的那把三叉戟。
 *
 * <p>和原版 {@code TridentEntity} 的区别：
 * <ul>
 *   <li>直线飞行、无重力，射程约 75 格（原版 2.5 速约 25 格）；</li>
 *   <li>沿途方块直接抹掉，但不产生掉落物；</li>
 *   <li>命中不停止，最多贯穿 5 个目标，每次命中伤害衰减；</li>
 *   <li>每次命中在目标身上和四周砸下 5 道不伤害、不起火的视觉闪电（终点 3 道），并补 8 点雷伤；</li>
 *   <li>飞到尽头（贯穿满或超时）在终点也砸一片闪电，同样对 3 格内补 8 点雷伤；</li>
 *   <li>结束后自动回到主人背包，不用跑去捡。</li>
 * </ul>
 */
public class ThunderTridentEntity extends Entity {

    /** 最多贯穿几个目标 */
    private static final int MAX_PIERCE = 5;
    /** 投掷初速（格/tick），原版三叉戟是 2.5 */
    public static final double THROW_SPEED = 3.0D;
    /** 最长飞行 tick（3.0 × 26 ≈ 78 格） */
    private static final int MAX_LIFE = 26;
    /** 每命中一次，后续伤害乘这个系数 */
    private static final float PIERCE_DECAY = 0.75F;
    /** 落雷溅射半径（格） */
    private static final double THUNDER_RADIUS = 3.0D;
    /** 落雷溅射伤害 */
    private static final float THUNDER_DAMAGE = 8.0F;
    /** 命中时一起落下的闪电道数（1 道打在目标身上，其余撒在四周） */
    private static final int STRIKE_BOLTS = 5;
    /** 命中时多道闪电的散布半径（格） */
    private static final double STRIKE_BOLT_SPREAD = 3.0D;
    /** 飞到尽头时落下的闪电道数 */
    private static final int END_BOLTS = 3;
    /** 破坏半径（格）：以飞行路径为轴挖出一个圆柱形空洞 */
    private static final double DESTROY_RADIUS = 3.0D;
    /** 洞壁起伏幅度（格），让边缘不是刀切一样的光滑圆筒 */
    private static final double DESTROY_JITTER = 1.2D;
    /** 每 tick 最多抹掉几个方块，防止一发把整座山挖穿导致服务端卡顿 */
    private static final int DESTROY_PER_TICK = 140;
    /** 离投掷者这么近的方块不破坏，否则出手瞬间会在自己脚下挖个坑；设 0 关闭 */
    private static final double SAFE_FROM_OWNER = 3.0D;

    private static final TrackedData<ItemStack> STACK =
            DataTracker.registerData(ThunderTridentEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private UUID ownerUuid;
    /** 首次命中的伤害，之后按 PIERCE_DECAY 递减 */
    private float baseDamage = 10.0F;
    private int pierced;
    private int lived;
    private final Set<UUID> hitTargets = new HashSet<>();

    public ThunderTridentEntity(EntityType<? extends ThunderTridentEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public ThunderTridentEntity(EntityType<? extends ThunderTridentEntity> type, World world,
                                LivingEntity owner, ItemStack stack, float baseDamage) {
        this(type, world);
        this.ownerUuid = owner == null ? null : owner.getUuid();
        this.baseDamage = baseDamage;
        this.dataTracker.set(STACK, stack.copy());
        this.setPosition(owner == null ? Vec3d.ZERO : owner.getPos().add(0.0D, owner.getStandingEyeHeight() - 0.2D, 0.0D));
        this.setVelocity(rotationToVelocity(owner == null ? 0.0F : owner.getPitch(),
                owner == null ? 0.0F : owner.getYaw(), THROW_SPEED));
    }

    /** 由俯仰/偏航算投掷初速，等价于 ProjectileEntity#setVelocity 的算法 */
    public static Vec3d rotationToVelocity(float pitch, float yaw, double speed) {
        float f = -MathHelper.sin(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        float g = -MathHelper.sin(pitch * 0.017453292F);
        float h = MathHelper.cos(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        return new Vec3d(f, g, h).normalize().multiply(speed);
    }

    public ItemStack getStack() {
        return this.dataTracker.get(STACK);
    }

    private Entity resolveOwner() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld) || this.ownerUuid == null) {
            return null;
        }
        return serverWorld.getEntity(this.ownerUuid);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d from = this.getPos();
        Vec3d velocity = this.getVelocity();
        Vec3d to = from.add(velocity);

        if (this.getWorld().isClient()) {
            this.spawnTrailClient();
            return;
        }

        ServerWorld world = (ServerWorld) this.getWorld();

        // 1. 先把路径上的方块抹掉
        this.destroyAlong(world, from, to);

        // 2. 扫过路径上的生物
        Box sweep = this.getBoundingBox().stretch(velocity).expand(0.7D);
        for (Entity entity : world.getOtherEntities(this, sweep)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            if (this.ownerUuid != null && this.ownerUuid.equals(target.getUuid())) {
                continue;
            }
            if (this.hitTargets.contains(target.getUuid())) {
                continue;
            }
            if (target.getBoundingBox().expand(0.35D).raycast(from, to).isEmpty()) {
                continue;
            }
            this.hitTargets.add(target.getUuid());
            this.strike(world, target);
            this.pierced++;
            if (this.pierced >= MAX_PIERCE) {
                this.finish();
                return;
            }
        }

        // 3. 推进
        this.setPosition(to);
        if (++this.lived > MAX_LIFE) {
            this.finish();
        }
    }

    /**
     * 以 from→to 为轴，半径 {@link #DESTROY_RADIUS} 格内的方块全部抹掉，不掉落任何东西。
     *
     * <p>跳过硬度为负的方块（基岩、屏障、命令方块）和带方块实体的方块（箱子、刷怪笼），
     * 后者被 breakBlock 会连带弹出内容物，等于变相刷物品。
     *
     * <p>贴近投掷者的方块不动，避免出手那一瞬间在自己脚下挖个坑。
     */
    private void destroyAlong(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d ownerPos = null;
        if (SAFE_FROM_OWNER > 0.0D) {
            Entity owner = this.resolveOwner();
            if (owner != null) {
                ownerPos = owner.getPos();
            }
        }

        double reach = DESTROY_RADIUS + DESTROY_JITTER;
        int minX = MathHelper.floor(Math.min(from.x, to.x) - reach);
        int maxX = MathHelper.floor(Math.max(from.x, to.x) + reach);
        int minY = MathHelper.floor(Math.min(from.y, to.y) - reach);
        int maxY = MathHelper.floor(Math.max(from.y, to.y) + reach);
        int minZ = MathHelper.floor(Math.min(from.z, to.z) - reach);
        int maxZ = MathHelper.floor(Math.max(from.z, to.z) + reach);

        int destroyed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (destroyed >= DESTROY_PER_TICK) {
                        return;
                    }
                    double cx = x + 0.5D;
                    double cy = y + 0.5D;
                    double cz = z + 0.5D;
                    if (distanceToSegment(cx, cy, cz, from, to) > DESTROY_RADIUS + edgeNoise(x, y, z)) {
                        continue;
                    }
                    if (ownerPos != null && ownerPos.squaredDistanceTo(cx, cy, cz) < SAFE_FROM_OWNER * SAFE_FROM_OWNER) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (state.getBlock().getHardness() < 0.0F) {
                        continue;
                    }
                    if (state.hasBlockEntity()) {
                        continue;
                    }
                    if (world.breakBlock(pos, false, this)) {
                        destroyed++;
                    }
                }
            }
        }
    }

    /** 按坐标做一个稳定的伪随机起伏，范围 ±DESTROY_JITTER/2，保证同一个位置每次算出来一样 */
    private static double edgeNoise(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        h ^= (h >>> 15);
        return ((h & 0xFF) / 255.0D - 0.5D) * DESTROY_JITTER;
    }

    /** 点到线段的最短距离，用来把整段飞行路径当成一根粗轴 */
    private static double distanceToSegment(double px, double py, double pz, Vec3d a, Vec3d b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abz = b.z - a.z;
        double len2 = abx * abx + aby * aby + abz * abz;
        double t = len2 < 1.0E-9D ? 0.0D
                : (((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / len2);
        t = MathHelper.clamp(t, 0.0D, 1.0D);
        double dx = px - (a.x + abx * t);
        double dy = py - (a.y + aby * t);
        double dz = pz - (a.z + abz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 在一个点周围撒 count 道闪电：第 1 道正中，其余在 spread 半径内随机散开。
     *
     * <p>全部 {@code setCosmetic(true)}，只发光不点火、不劈伤方块，伤害统一由本类结算。
     */
    private void spawnBolts(ServerWorld world, Vec3d center, int count, double spread) {
        for (int i = 0; i < count; i++) {
            double dx = 0.0D;
            double dz = 0.0D;
            if (i > 0) {
                double angle = this.random.nextDouble() * Math.PI * 2.0D;
                double dist = spread * Math.sqrt(this.random.nextDouble());
                dx = Math.cos(angle) * dist;
                dz = Math.sin(angle) * dist;
            }
            ThunderBoltEntity.spawn(world, new Vec3d(center.x + dx, center.y, center.z + dz),
                    ThunderBoltEntity.BLUE, true);
        }
    }

    /** 命中一个目标：伤害 + 视觉闪电 + 范围雷伤 */
    private void strike(ServerWorld world, LivingEntity target) {
        float damage = this.baseDamage * (float) Math.pow(PIERCE_DECAY, this.pierced);
        Entity owner = this.resolveOwner();
        // 玩家扔的就吃玩家攻击加成，别的一律魔法伤害
        net.minecraft.entity.damage.DamageSource source = owner instanceof PlayerEntity player
                ? world.getDamageSources().playerAttack(player)
                : world.getDamageSources().magic();
        target.timeUntilRegen = 0;
        target.damage(source, damage);

        // 只发光不伤害的闪电，否则每次命中都会点火、劈出火海
        this.spawnBolts(world, target.getPos(), STRIKE_BOLTS, STRIKE_BOLT_SPREAD);

        // 范围雷伤：已命中过的不再重复吃，避免同一只怪被叠成一击秒杀
        Box thunderBox = target.getBoundingBox().expand(THUNDER_RADIUS);
        for (Entity entity : world.getOtherEntities(this, thunderBox)) {
            if (!(entity instanceof LivingEntity other) || !other.isAlive()) {
                continue;
            }
            if (this.ownerUuid != null && this.ownerUuid.equals(other.getUuid())) {
                continue;
            }
            if (other == target || this.hitTargets.contains(other.getUuid())) {
                continue;
            }
            if (other.squaredDistanceTo(target) > THUNDER_RADIUS * THUNDER_RADIUS) {
                continue;
            }
            other.timeUntilRegen = 0;
            other.damage(world.getDamageSources().magic(), THUNDER_DAMAGE);
        }

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + target.getHeight() * 0.5D, target.getZ(),
                24, 0.35D, 0.45D, 0.35D, 0.35D);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.9F, 1.35F);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.9F, 1.1F);
    }

    /** 飞到头了：在终点再劈一道闪电，顺便把落脚点周围的怪一起带走 */
    private void thunderBurst(ServerWorld world, Vec3d pos) {
        this.spawnBolts(world, pos, END_BOLTS, STRIKE_BOLT_SPREAD);

        Box box = new Box(pos, pos).expand(THUNDER_RADIUS);
        for (Entity entity : world.getOtherEntities(this, box)) {
            if (!(entity instanceof LivingEntity other) || !other.isAlive()) {
                continue;
            }
            if (this.ownerUuid != null && this.ownerUuid.equals(other.getUuid())) {
                continue;
            }
            // 已经吃过一发的不重复结算，免得贯穿满那一下相当于白送双倍
            if (!this.hitTargets.add(other.getUuid())) {
                continue;
            }
            other.timeUntilRegen = 0;
            other.damage(world.getDamageSources().magic(), THUNDER_DAMAGE);
        }

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y + 0.6D, pos.z, 48, 0.6D, 0.6D, 0.6D, 0.45D);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.25F, 0.9F);
    }

    /** 飞行结束：先在终点落一道雷，然后忠诚还在就直接回背包，否则掉在原地 */
    private void finish() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            this.thunderBurst(serverWorld, this.getPos());
        }
        ItemStack stack = this.getStack();
        Entity owner = this.resolveOwner();
        if (!stack.isEmpty()) {
            int loyalty = EnchantmentHelper.getLoyalty(stack);
            if (owner instanceof ServerPlayerEntity player && loyalty > 0) {
                ItemStack give = stack.copy();
                if (!player.getInventory().insertStack(give)) {
                    player.dropItem(give, false);
                }
                player.playSound(SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                this.dropStack(stack.copy());
            }
        }
        this.discard();
    }

    /** 电弧拖尾：客户端沿这一帧的位移撒一串电火花，3 格/tick 的速度才不会断层 */
    private void spawnTrailClient() {
        Vec3d step = this.getVelocity().multiply(-0.25D);
        for (int i = 0; i < 4; i++) {
            Vec3d pos = this.getPos().add(step.multiply(i));
            this.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + (this.random.nextDouble() - 0.5D) * 0.25D,
                    pos.y + (this.random.nextDouble() - 0.5D) * 0.25D,
                    pos.z + (this.random.nextDouble() - 0.5D) * 0.25D,
                    0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STACK, ItemStack.EMPTY);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
        NbtCompound stackNbt = new NbtCompound();
        this.getStack().writeNbt(stackNbt);
        nbt.put("Trident", stackNbt);
        nbt.putFloat("BaseDamage", this.baseDamage);
        nbt.putInt("Pierced", this.pierced);
        nbt.putInt("Lived", this.lived);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
        if (nbt.contains("Trident")) {
            this.dataTracker.set(STACK, ItemStack.fromNbt(nbt.getCompound("Trident")));
        }
        this.baseDamage = nbt.getFloat("BaseDamage");
        this.pierced = nbt.getInt("Pierced");
        this.lived = nbt.getInt("Lived");
    }
}
