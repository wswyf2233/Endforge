package org.examplee.wangsword.util;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 需要「戴着就一直在跑」的饰品。
 *
 * <p>效果续期、冲刺加成、常驻小效果都实现这个接口，由 {@link AccessoryTicker} 每 tick
 * 从饰品栏里扫出来调用。
 *
 * <p>为什么不直接实现 {@code Accessory#tick}：那套派发依赖 Accessories 自己的容器状态，
 * 玩家重新登录、换维度、死亡重生之后可能没被派发到，效果就停在那儿，得手动摘下来再戴一次。
 * 这里改成主动扫描，读的是「现在身上到底戴着什么」——只要槽里有东西，就一定跑。
 *
 * <p>实现必须是<b>幂等</b>的：每 tick 都会调用，重复调用不能叠加出两份效果。
 */
public interface EquippedTickable {

    void tickEquipped(ServerPlayerEntity player);
}
