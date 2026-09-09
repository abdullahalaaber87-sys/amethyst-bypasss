package com.example.xray;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class XrayMod implements ClientModInitializer {

    private static final Set<BlockPos> AMETHYST_POSITIONS =
            ConcurrentHashMap.newKeySet();

    private static int scanCooldown = 0;
    private static boolean scannedOnJoin = false;

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player != null && client.world != null) {

                if (!scannedOnJoin) {
                    scanAllLoadedChunks(client);
                    scannedOnJoin = true;
                }

                if (scanCooldown-- <= 0) {
                    scanAllLoadedChunks(client);

                    // 100 ticks = تقريباً 5 ثواني
                    scanCooldown = 100;
                }

            } else {

                scannedOnJoin = false;
                scanCooldown = 0;
                AMETHYST_POSITIONS.clear();
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {

            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player == null || client.world == null) {
                return;
            }

            MatrixStack matrices = context.matrices();

            VertexConsumer lines =
                    context.consumers().getBuffer(RenderLayers.lines());

            Vec3d cameraPos =
                    client.gameRenderer.getCamera().getCameraPos();

            for (BlockPos pos : AMETHYST_POSITIONS) {

                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                if (!client.world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                double x = pos.getX() - cameraPos.x;
                double y = pos.getY() - cameraPos.y;
                double z = pos.getZ() - cameraPos.z;

                VertexRendering.drawOutline(
                        matrices,
                        lines,
                        VoxelShapes.fullCube(),
                        x,
                        y,
                        z,
                        0xFFFF0000,
                        2.0F
                );
            }
        });
    }

    private static void scanAllLoadedChunks(MinecraftClient client) {

        World world = client.world;

        if (world == null || client.player == null) {
            return;
        }

        ChunkPos playerChunk = client.player.getChunkPos();

        int radius = 8;

        for (int cx = playerChunk.x - radius;
             cx <= playerChunk.x + radius;
             cx++) {

            for (int cz = playerChunk.z - radius;
                 cz <= playerChunk.z + radius;
                 cz++) {

                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                Chunk chunk = world.getChunk(cx, cz);

                int minY = world.getBottomY();

                int maxY = Math.min(
                        world.getBottomY() + world.getHeight(),
                        0
                );

                for (int y = minY; y < maxY; y++) {

                    for (int x = 0; x < 16; x++) {

                        for (int z = 0; z < 16; z++) {

                            BlockPos pos = new BlockPos(
                                    cx * 16 + x,
                                    y,
                                    cz * 16 + z
                            );

                            BlockState state = chunk.getBlockState(pos);

                            if (
                                    state.isOf(Blocks.AMETHYST_BLOCK)
                                    || state.isOf(Blocks.BUDDING_AMETHYST)
                            ) {

                                AMETHYST_POSITIONS.add(
                                        pos.toImmutable()
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
