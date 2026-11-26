package org.examplee.wangsword.item.unique;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.BlocksRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 残骸搜寻仪：右键扫描周围一定范围内的太古残骸与下界合金残骸，
 * 把位置发给客户端做穿墙描边。
 */
public class RelicSeekerItem extends Item {

    /** 扫描半径 */
    public static final int RADIUS = 24;
    /** 最多标记数量 */
    public static final int MAX_MARKS = 64;
    /** 标记持续时间（tick） */
    public static final int MARK_DURATION = 300;
    /** 冷却（tick） */
    public static final int COOLDOWN = 60;
    /** 技能 ID：冷却按技能记，改用技能池后才有右侧图标 */
    public static final String SKILL_SEEK = "relic_seek";

    public static final Identifier SCAN_PACKET = new Identifier("wangsword", "relic_scan");

    public RelicSeekerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_SEEK)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_SEEK, "远古探寻");
            return TypedActionResult.fail(stack);
        }

        ServerWorld serverWorld = (ServerWorld) world;
        BlockPos center = player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -RADIUS, -RADIUS),
                center.add(RADIUS, RADIUS, RADIUS))) {
            if (found.size() >= MAX_MARKS) {
                break;
            }
            BlockState state = serverWorld.getBlockState(pos);
            Block block = state.getBlock();
            if (block == BlocksRegistry.WS_ARCHEAN_DEBRIS
                    || state.isOf(net.minecraft.block.Blocks.ANCIENT_DEBRIS)) {
                found.add(pos.toImmutable());
            }
        }

        PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeVarInt(found.size());
        for (BlockPos pos : found) {
            buf.writeBlockPos(pos);
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, SCAN_PACKET, buf);

        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
        player.sendMessage(Text.translatable("message.wangsword.relic_seeker.result", found.size())
                .formatted(found.isEmpty() ? Formatting.GRAY : Formatting.AQUA), true);

        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_SEEK, COOLDOWN);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.relic_seeker.desc", RADIUS).formatted(Formatting.GRAY));
    }
}