package org.examplee.wangsword.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.examplee.wangsword.registry.BlocksRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 石中剑基座的方块实体，只存一件「插在石头里的物品」。
 *
 * <p>这里没有实现 {@code Inventory}：外部（漏斗、比较器、槽位 API）一律摸不到它，
 * 天然就无法抽取，省掉一层「漏斗防刷」的判断。
 *
 * <p>存的是完整的 {@link ItemStack}，所以以后把钻石剑换成大地核心、封烬巨剑，
 * 只改 {@link #defaultStack()} 一个方法，渲染那边自动跟着变。
 */
public class StoneSwordPedestalBlockEntity extends BlockEntity {

    private static final String KEY_STACK = "StoredItem";
    /** 显式记录「槽位是否为空」。只靠 KEY_STACK 是否存在来推断的话，
     *  拔走剑之后重进游戏会读不到键、退回默认的钻石剑，剑就自己长回来了。 */
    private static final String KEY_HAS = "HasItem";

    /** 插着的物品。默认是钻石剑（这一版拿它做实验） */
    private ItemStack storedItem = defaultStack();

    public StoneSwordPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(BlocksRegistry.STONE_SWORD_PEDESTAL_BE, pos, state);
    }

    /** 插在基座里的那把剑。以后要换成别的，只改这里 */
    public static ItemStack defaultStack() {
        return new ItemStack(org.examplee.wangsword.registry.ItemsRegistry.WS_STONE_BONE_GREATSWORD);
    }

    public ItemStack getStoredItem() {
        return this.storedItem;
    }

    public void setStoredItem(ItemStack stack) {
        this.storedItem = stack.copy();
        this.markDirty();
        if (this.world != null && !this.world.isClient()) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        boolean has = !this.storedItem.isEmpty();
        nbt.putBoolean(KEY_HAS, has);
        if (has) {
            nbt.put(KEY_STACK, this.storedItem.writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains(KEY_STACK)) {
            this.storedItem = ItemStack.fromNbt(nbt.getCompound(KEY_STACK));
        } else if (nbt.contains(KEY_HAS)) {
            // 明确写着「已经空了」，就是空的，不要退回默认物品
            this.storedItem = nbt.getBoolean(KEY_HAS) ? defaultStack() : ItemStack.EMPTY;
        } else {
            // 老存档没有这两个键，按默认的插剑状态处理
            this.storedItem = defaultStack();
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        this.writeNbt(nbt);
        return nbt;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
