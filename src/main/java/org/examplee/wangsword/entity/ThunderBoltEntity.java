package org.examplee.wangsword.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.EntityRegistry;

/**
 * 带皮肤的原版落雷。
 *
 * <p>直接继承 {@link LightningEntity}，所以伤害、点火、充能苦力怕、引雷针、天空闪白
 * 这些原版逻辑一行都不用重写；只是把 <em>画面</em> 交给
 * {@link org.examplee.wangsword.client.ThunderBoltRenderer} 用贴图重画，
 * 这样才做得出「紫电」「蓝电」。
 *
 * <p><b>为什么能换掉外观：</b>原版闪电根本没有贴图，是
 * {@code RenderLayer.getLightning()} 用顶点色画的四边形（着色器里 {@code samplers} 是空的）。
 * 换个 EntityType 就能换渲染器，比去 mixin 原版渲染器干净。
 *
 * <p><b>寿命：</b>原版 {@code ambientTick} 从 2 开始每 tick 减一，减到 -1 就
 * {@code discard()}，所以原版闪电只活 4 tick（0.2 秒）。这里只在前 2 tick 调
 * {@code super.tick()}（伤害和雷声都在这两 tick 里结算），之后就靠自己的
 * {@link #visualAge} 撑到 {@link #VISUAL_LIFE}，让玩家来得及看清这道雷是什么颜色。
 */
public class ThunderBoltEntity extends LightningEntity {

    /** 紫电：雷电太刀 */
    public static final byte PURPLE = 0;
    /** 蓝电：雷霆三叉戟 */
    public static final byte BLUE = 1;

    private static final TrackedData<Byte> STYLE =
            DataTracker.registerData(ThunderBoltEntity.class, TrackedDataHandlerRegistry.BYTE);

    /** 前两 tick 交给原版：第 1 tick 结算伤害与雷声，第 2 tick 把 ambientTick 递减到 0 */
    private static final int VANILLA_TICKS = 2;

    /** 整道雷的可见时长（tick） */
    public static final int VISUAL_LIFE = 10;

    private int visualAge;

    public ThunderBoltEntity(EntityType<? extends ThunderBoltEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    /**
     * 落一道带皮肤的雷。
     *
     * @param cosmetic {@code true} 只出画面（伤害由调用方自己结算），{@code false} 走原版落雷伤害
     */
    public static ThunderBoltEntity spawn(ServerWorld world, Vec3d at, byte style, boolean cosmetic) {
        ThunderBoltEntity bolt = new ThunderBoltEntity(EntityRegistry.THUNDER_BOLT, world);
        bolt.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
        bolt.setStyle(style);
        bolt.setCosmetic(cosmetic);
        world.spawnEntity(bolt);
        return bolt;
    }

    public void setStyle(byte style) {
        this.dataTracker.set(STYLE, style);
    }

    public byte getStyle() {
        return this.dataTracker.get(STYLE);
    }

    /** 渲染器读它算淡出；不用 {@code age}，因为第 3 tick 之后就不再走 {@code super.tick()} 了 */
    public int getVisualAge() {
        return this.visualAge;
    }

    @Override
    public void tick() {
        if (this.visualAge < VANILLA_TICKS) {
            super.tick();
        }
        this.visualAge++;
        if (this.visualAge > VISUAL_LIFE) {
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STYLE, PURPLE);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putByte("BoltStyle", this.getStyle());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setStyle(nbt.getByte("BoltStyle"));
    }
}
