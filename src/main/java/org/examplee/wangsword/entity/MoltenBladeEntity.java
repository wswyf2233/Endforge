package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 熔核封岩巨剑右键时插进地面的那把剑。
 *
 * <p>纯视觉实体：不做碰撞、不吃伤害，活 8 tick 就消失，外观由
 * {@code MoltenBladeRenderer} 用物品模型倒着画出来（和石中剑基座同一套做法）。
 * 之所以不用盔甲架：盔甲架手臂的默认朝向跟模型轴对不齐，调半天还是会歪，
 * 自己画反而只要一个 180° 旋转。
 */
public class MoltenBladeEntity extends Entity {

    private static final int LIFE = 8;

    public MoltenBladeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient() && this.age > LIFE) {
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }
}
