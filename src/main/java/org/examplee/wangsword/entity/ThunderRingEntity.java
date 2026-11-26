package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 雷电太刀 · 雷怒的脚下雷环。
 *
 * <p>纯装饰实体，和雷怒同寿（{@link #LIFE} = 15 秒）。判定、伤害全部在
 * {@link org.examplee.wangsword.util.ThunderKatanaHandler} 里，这里只负责把
 * 「以谁为圆心、半径多少」告诉客户端，由 {@link org.examplee.wangsword.client.ThunderRingRenderer}
 * 把贴图铺成贴地的一圈电环。
 *
 * <p>位置每 tick 跟着主人的脚走（服务端 {@code setPosition}），
 * 但**渲染位置不读实体自己的坐标**：渲染器会按主人本地的插值坐标重算，
 * 这样玩家跑动时环是平滑跟脚的，不会因为网络包慢半拍而在身后拖影子。
 */
public class ThunderRingEntity extends Entity {

    /** 存续 tick：和雷怒一样 15 秒 */
    public static final int LIFE = 300;

    /** 这一圈的最大半径（格） */
    private static final TrackedData<Float> RADIUS =
            DataTracker.registerData(ThunderRingEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 主人的网络实体 id（客户端要靠它找玩家位置，也用它判断主人还在不在） */
    private static final TrackedData<Integer> OWNER_ID =
            DataTracker.registerData(ThunderRingEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** 总寿命，客户端拿它算结尾的淡出 */
    private static final TrackedData<Integer> LIFE_TICKS =
            DataTracker.registerData(ThunderRingEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /**
     * 是否是「钉在地上」的固定环。
     *
     * <p>两种用法共用这一个实体：雷怒的环跟着主人脚走（{@code false}），
     * 万雷的环钉在准心落点不动（{@code true}）。固定环没有主人，
     * 所以 tick 里不能走「找不到主人就消失」那条判断。
     */
    private static final TrackedData<Boolean> FIXED =
            DataTracker.registerData(ThunderRingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 贴图样式：同一个实体按用途换贴图，省得为每一圈单独建实体和渲染器 */
    public static final int STYLE_THUNDER = 0;
    public static final int STYLE_THUNDER_FIELD = 1;
    public static final int STYLE_FIRE = 2;
    /** 血帆弯刀血怒：深红环 */
    public static final int STYLE_BLOOD = 3;

    private static final TrackedData<Integer> STYLE =
            DataTracker.registerData(ThunderRingEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** 服务端自己的计时，防止实体被外部改数据后永远不消失 */
    private int life = LIFE;

    /** 是从存档里读出来的：雷怒状态本来就不进存档，这种环一律作废 */
    private boolean loadedFromDisk;

    public ThunderRingEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setRadius(float radius) {
        this.dataTracker.set(RADIUS, radius);
    }

    public float getRadius() {
        return this.dataTracker.get(RADIUS);
    }

    public void setOwner(Entity owner) {
        this.dataTracker.set(OWNER_ID, owner == null ? -1 : owner.getId());
    }

    public int getOwnerId() {
        return this.dataTracker.get(OWNER_ID);
    }

    public void setLifeTicks(int ticks) {
        this.life = ticks;
        this.dataTracker.set(LIFE_TICKS, ticks);
    }

    /** 钉在地上不跟人（万雷的落点圈）；默认是跟脚的雷怒环 */
    public void setFixed(boolean fixed) {
        this.dataTracker.set(FIXED, fixed);
    }

    public boolean isFixed() {
        return this.dataTracker.get(FIXED);
    }

    /** 贴图样式，见 {@link #STYLE_THUNDER} 等常量 */
    public void setStyle(int style) {
        this.dataTracker.set(STYLE, style);
    }

    public int getStyle() {
        return this.dataTracker.get(STYLE);
    }

    public int getLifeTicks() {
        return this.dataTracker.get(LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (this.loadedFromDisk) {
            this.discard();
            return;
        }
        if (this.age > this.life) {
            this.discard();
            return;
        }
        // 固定环（万雷的落点圈）没有主人，位置由生成方给死，不需要跟
        if (this.isFixed()) {
            return;
        }
        // 主人没了（下线、换维度、被清）就一起消失，别留个环在原地空转
        Entity owner = this.getWorld().getEntityById(this.getOwnerId());
        if (owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }
        // 火刀被动环：主人换手或把刀扔了，物品的 inventoryTick 就不再调用，
        // 只能靠实体自己每 tick 反查一次主手，否则环会永远留在原地
        if (this.getStyle() == STYLE_FIRE && owner instanceof net.minecraft.server.network.ServerPlayerEntity player
                && !(player.getMainHandStack().getItem()
                instanceof org.examplee.wangsword.item.unique.FlameBladeItem)) {
            this.discard();
            return;
        }
        this.setPosition(owner.getX(), owner.getY(), owner.getZ());
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(RADIUS, 3.0F);
        this.dataTracker.startTracking(OWNER_ID, -1);
        this.dataTracker.startTracking(LIFE_TICKS, LIFE);
        this.dataTracker.startTracking(FIXED, false);
        this.dataTracker.startTracking(STYLE, STYLE_THUNDER);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("RingRadius", this.getRadius());
        nbt.putInt("RingLife", this.life);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setRadius(nbt.getFloat("RingRadius"));
        this.setLifeTicks(nbt.getInt("RingLife"));
        this.loadedFromDisk = true;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 256.0D * 256.0D;
    }
}
