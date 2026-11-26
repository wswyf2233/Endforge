package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.SoundRegistry;
import org.examplee.wangsword.util.CombatTargets;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 飞剑：右键发射出去的一把实体剑，贴图用它自己（手里那把的物品模型）。
 *
 * <p>直飞、穿透最多 {@link #MAX_PIERCE} 个目标，命中凋灵时顺便把它的飞行关掉。
 */
public class FlyingSwordEntity extends Entity {

    /** 存活 tick（3 秒） */
    private static final int MAX_LIFE = 60;
    /** 最多穿透几个目标 */
    private static final int MAX_PIERCE = 3;
    /** 光刃炸开的溅射半径（格） */
    private static final double BURST_RADIUS = 2.5D;
    /**
     * 溅射伤害：飞剑本身 10 点，炸开再补 6 点，贴着站会被叠到 16。
     *
     * <p>这是默认值。剑冢守卫出剑时用 {@link #setBurstDamage} 调成 3——
     * 那个实体的数值要单独平衡，不能跟玩家的技能绑在同一根绳上。
     */
    private static final float BURST_DAMAGE = 6.0F;
    /** 溅射击退 */
    private static final double BURST_KNOCKBACK = 0.5D;

    private static final TrackedData<ItemStack> STACK =
            DataTracker.registerData(FlyingSwordEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    /**
     * 灵体形态：不画物品模型，改成幽蓝光刃（剑冢守卫的出剑用它）。
     *
     * <p>同一个飞剑实体原本是给玩家王剑技能用的，外观直接取手里的物品模型；
     * 守卫没有"手里那把剑"这回事，所以单独走一条纯光效的渲染分支。
     */
    private static final TrackedData<Boolean> SPECTRAL =
            DataTracker.registerData(FlyingSwordEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private LivingEntity owner;
    private float damage = 1.0F;
    /** 炸开时的溅射伤害，默认 {@link #BURST_DAMAGE}，守卫会调低 */
    private float burstDamage = BURST_DAMAGE;
    private int lived = 0;
    private final Set<UUID> pierced = new HashSet<>();
    /**
     * 只打敌对生物：剑阵这种"自动开火"的光刃必须打开，否则一路扫过去把村民和牲畜全清了。
     *
     * <p>玩家自己右键扔出去的飞剑保持 false——那是玩家瞄的，打谁由玩家负责。
     */
    private boolean hostileOnly = false;

    public FlyingSwordEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public FlyingSwordEntity(EntityType<?> type, ServerWorld world, LivingEntity owner,
                             ItemStack stack, float damage, Vec3d velocity) {
        this(type, world, owner, stack, damage, velocity, false);
    }

    public FlyingSwordEntity(EntityType<?> type, ServerWorld world, LivingEntity owner,
                             ItemStack stack, float damage, Vec3d velocity, boolean spectral) {
        this(type, world);
        this.owner = owner;
        this.damage = damage;
        this.dataTracker.set(STACK, stack.copy());
        this.dataTracker.set(SPECTRAL, spectral);
        this.setVelocity(velocity);
    }

    public ItemStack getStack() {
        return this.dataTracker.get(STACK);
    }

    /** 是否是灵体光刃形态（守卫出剑） */
    public boolean isSpectral() {
        return this.dataTracker.get(SPECTRAL);
    }

    /**
     * 覆盖炸开的溅射伤害（链式，方便在 new 后面直接跟上）。
     * 必须在 {@code spawnEntity} 之前调用——它是服务端结算用的本地字段，不走数据同步。
     */
    public FlyingSwordEntity setBurstDamage(float value) {
        this.burstDamage = value;
        return this;
    }

    /**
     * 只对敌对生物生效（链式）。剑阵自动射出的光刃用它，
     * 免得瞄准一个僵尸时把旁边看戏的村民一起炸了。
     */
    public FlyingSwordEntity setHostileOnly(boolean value) {
        this.hostileOnly = value;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d velocity = this.getVelocity();
        if (!this.getWorld().isClient()) {
            // 拖尾：这把剑 18 格/秒，不拖尾在游戏里就是一闪而过
            this.spawnTrail();
            Vec3d from = this.getPos();
            Vec3d to = from.add(velocity);

            // 灵体光刃是"光弹"，撞到方块就该炸开消失，不该一路穿墙飞出去。
            // 玩家王剑那把是实物飞剑，保持原来的穿透手感，所以只对 spectral 生效。
            if (this.isSpectral()) {
                BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(
                        from, to, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, this));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    this.spawnSpectralBurst(blockHit.getPos().add(velocity.multiply(-0.4D)));
                    this.discard();
                    return;
                }
            }

            for (Entity entity : this.getWorld().getOtherEntities(
                    this, this.getBoundingBox().stretch(velocity).expand(0.7D))) {
                if (!(entity instanceof LivingEntity target) || target == owner || !target.isAlive()) {
                    continue;
                }
                if (hostileOnly && !CombatTargets.isHostile(target)) {
                    continue;
                }
                if (pierced.contains(target.getUuid())) {
                    continue;
                }
                if (target.getBoundingBox().expand(0.35D).raycast(from, to).isEmpty()) {
                    continue;
                }
                pierced.add(target.getUuid());
                target.damage(this.getDamageSource(target), damage);
                this.spawnHitEffect(target);
                if (target instanceof WitherEntity wither && wither.isAlive()) {
                    org.examplee.wangsword.util.WitherFlightDisable.disable(wither);
                }
                if (pierced.size() >= MAX_PIERCE) {
                    this.discard();
                    return;
                }
            }
            this.setPosition(to);
            if (++lived > MAX_LIFE) {
                // 光刃飞满 3 秒在空中耗散，给一个小炸，别让它凭空消失
                if (this.isSpectral()) {
                    this.spawnSpectralBurst(this.getPos());
                }
                this.discard();
            }
        }
    }

    /**
     * 飞行拖尾：每 tick 一颗发光的末地烛光点，隔一 tick 再补一朵蓝魂焰。
     *
     * <p>剑速 0.9 格/tick（约 18 格/秒），单帧位移近一格，没有拖尾时在暗处
     * 几乎看不见。粒子留在原地连成光带，玩家才能判断剑从哪来、往哪去。
     */
    private void spawnTrail() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        Vec3d pos = this.getPos();
        serverWorld.spawnParticles(ParticleTypes.END_ROD,
                pos.x, pos.y, pos.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        if (this.age % 2 == 0) {
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y, pos.z, 1, 0.08D, 0.08D, 0.08D, 0.001D);
        }
    }

    /** 命中反馈：横扫弧光 + 魂焰炸开 + 打击音，打没打中一眼就能看出来 */
    private void spawnHitEffect(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        double y = target.getY() + target.getHeight() * 0.5D;
        // 光刃命中就是"光弹炸开"，走爆炸那套；实物飞剑仍是刀刃划过的表现
        if (this.isSpectral()) {
            this.spawnSpectralBurst(new Vec3d(target.getX(), y, target.getZ()));
            return;
        }
        serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), y, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), y, target.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.06D);
        this.playSound(SoundRegistry.SLASH_IMPACT, 0.9F, 1.25F);
    }

    /**
     * 光刃炸开：小范围爆炸伤害 + 爆炸粒子。
     *
     * <p>不调用 {@code world.createExplosion}——那会把地形和身边的草一起掀了。
     * 这里自己按半径结算：伤害走 explosion 伤害源（吃爆炸保护、不吃护甲以外的东西），
     * 再手动推一下，视觉效果和伤害范围就都对得上。
     */
    private void spawnSpectralBurst(Vec3d at) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        // 范围内的生物一律吃一发溅射：直接命中的那个会额外吃到飞剑本身的伤害
        for (Entity entity : serverWorld.getOtherEntities(
                this, new net.minecraft.util.math.Box(at, at).expand(BURST_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || target == this.owner || !target.isAlive()) {
                continue;
            }
            if (hostileOnly && !CombatTargets.isHostile(target)) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (target.getPos().squaredDistanceTo(at) > BURST_RADIUS * BURST_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (target.damage(serverWorld.getDamageSources().explosion(this, this.owner), this.burstDamage)) {
                target.takeKnockback(BURST_KNOCKBACK,
                        at.x - target.getX(), at.z - target.getZ());
            }
        }

        serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverWorld.spawnParticles(ParticleTypes.FLASH,
                at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                at.x, at.y, at.z, 18, 0.45D, 0.45D, 0.45D, 0.08D);
        serverWorld.spawnParticles(ParticleTypes.CLOUD,
                at.x, at.y, at.z, 8, 0.32D, 0.32D, 0.32D, 0.04D);
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.65F, 1.6F);
        this.playSound(SoundRegistry.SLASH_IMPACT, 1.0F, 1.15F);
    }

    private net.minecraft.entity.damage.DamageSource getDamageSource(LivingEntity target) {        if (owner instanceof PlayerEntity player) {
            return this.getWorld().getDamageSources().playerAttack(player);
        }
        return this.getWorld().getDamageSources().magic();
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STACK, ItemStack.EMPTY);
        this.dataTracker.startTracking(SPECTRAL, false);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        NbtCompound stackNbt = new NbtCompound();
        this.getStack().writeNbt(stackNbt);
        nbt.put("Sword", stackNbt);
        nbt.putBoolean("Spectral", this.isSpectral());
        nbt.putFloat("Damage", damage);
        nbt.putFloat("BurstDamage", burstDamage);
        nbt.putBoolean("HostileOnly", hostileOnly);
        nbt.putInt("Lived", lived);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Sword")) {
            this.dataTracker.set(STACK, ItemStack.fromNbt(nbt.getCompound("Sword")));
        }
        this.dataTracker.set(SPECTRAL, nbt.getBoolean("Spectral"));
        damage = nbt.getFloat("Damage");
        if (nbt.contains("BurstDamage")) {
            burstDamage = nbt.getFloat("BurstDamage");
        }
        hostileOnly = nbt.getBoolean("HostileOnly");
        lived = nbt.getInt("Lived");
    }
}
