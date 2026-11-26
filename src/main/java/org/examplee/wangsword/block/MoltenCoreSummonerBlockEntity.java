package org.examplee.wangsword.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.CalderaBatEntity;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.registry.BlocksRegistry;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 熔核唤石的方块实体：待机时一直播 {@code idle}（熔核明暗脉动），
 * 右键触发一次 {@code summon}，播完由控制器自己回落到 idle。
 */
public class MoltenCoreSummonerBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** 召唤脉动全长 1.2 秒 = 24 tick，服务端拿它当冷却，防止连点 */
    public static final int SUMMON_TICKS = 24;

    /** 蝠怪补员间隔（tick）：4 秒看一次 */
    private static final int BAT_INTERVAL = 80;
    /** 沉坛范围内最多同时存在几只 */
    private static final int BAT_CAP = 8;
    /** 补员半径（水平），50×50 的建筑连外墙外一圈都盖住 */
    private static final int BAT_RADIUS = 26;
    /** 离唤石至少这么远，别贴着方块生成 */
    private static final int BAT_MIN_DIST = 4;
    /** 只有玩家在附近才补员，空转没有意义 */
    private static final double BAT_ACTIVE_RANGE = 48.0D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("summon");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 召唤剩余 tick；-1 表示没在召唤 */
    private int summonTicks = -1;
    /** 蝠怪补员计时 */
    private int batTimer;

    public MoltenCoreSummonerBlockEntity(BlockPos pos, BlockState state) {
        super(BlocksRegistry.MOLTEN_CORE_SUMMONER_BE, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 过渡 3 tick：idle 和 summon 之间不要硬切
        controllers.add(new AnimationController<>(this, "core", 3, state -> state.setAndContinue(IDLE))
                .triggerableAnim("summon", SUMMON));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public boolean isSummoning() {
        return this.summonTicks >= 0;
    }

    public void beginSummon() {
        this.summonTicks = SUMMON_TICKS;
        this.triggerAnim("core", "summon");
        this.markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, MoltenCoreSummonerBlockEntity core) {
        if (world.isClient()) {
            return;
        }
        // 补员和召唤脉动是两件独立的事，先各走各的
        if (++core.batTimer >= BAT_INTERVAL) {
            core.batTimer = 0;
            if (world instanceof ServerWorld serverWorld) {
                core.trySpawnBat(serverWorld, pos);
            }
        }
        if (core.summonTicks < 0) {
            return;
        }
        if (--core.summonTicks <= 0) {
            core.summonTicks = -1;
            core.markDirty();
        }
    }

    /**
     * 在沉坛范围内补一只熔岩蝠怪。
     *
     * <p>随机挑一根柱子：要求生成点是空气、上面一格也是空气、脚下是实心方块，
     * 这样它只会落在楼板、走廊地面和建筑外的地面上，不会刷进墙里。
     * 纵向只在唤石下方 1~10 格：一层房间、回廊和建筑外的地面，二层平台不刷。
     */
    private void trySpawnBat(ServerWorld world, BlockPos pos) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        if (!world.isPlayerInRange(cx, cy, cz, BAT_ACTIVE_RANGE)) {
            return;
        }
        int alive = world.getEntitiesByClass(CalderaBatEntity.class,
                new Box(pos).expand(BAT_RADIUS + 8), Entity::isAlive).size();
        if (alive >= BAT_CAP) {
            return;
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = world.random.nextInt(BAT_RADIUS * 2 + 1) - BAT_RADIUS;
            int dz = world.random.nextInt(BAT_RADIUS * 2 + 1) - BAT_RADIUS;
            // 唤石在二层，往下 10 格到 1 格：一层房间、回廊、外墙外的地面都在范围内，
            // 唯独不刷在二层平台上，免得 BOSS 战里满台子都是蝙蝠
            int dy = -10 + world.random.nextInt(10);
            BlockPos spot = pos.add(dx, dy, dz);
            if (spot.getSquaredDistance(pos) < (double) (BAT_MIN_DIST * BAT_MIN_DIST)) {
                continue;
            }
            if (!world.isAir(spot) || !world.isAir(spot.up())) {
                continue;
            }
            BlockState floor = world.getBlockState(spot.down());
            if (floor.isAir() || !floor.getFluidState().isEmpty()) {
                continue;
            }
            CalderaBatEntity bat = EntityRegistry.CALDERA_BAT.create(world);
            if (bat == null) {
                return;
            }
            bat.refreshPositionAndAngles(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    world.random.nextFloat() * 360.0F, 0.0F);
            world.spawnEntity(bat);
            world.spawnParticles(ParticleTypes.FLAME,
                    spot.getX() + 0.5D, spot.getY() + 0.3D, spot.getZ() + 0.5D,
                    8, 0.3D, 0.25D, 0.3D, 0.02D);
            world.playSound(null, spot, SoundEvents.ENTITY_BLAZE_BURN,
                    SoundCategory.HOSTILE, 0.6F, 0.8F);
            return;
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("SummonTicks", this.summonTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.summonTicks = nbt.contains("SummonTicks") ? nbt.getInt("SummonTicks") : -1;
    }
}
