package com.thebeyond.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.deafening.Deafening;
import com.thebeyond.util.FovStealth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Debug view of the deafening FOV cone ({@link #ENABLED} + F3 open): draws {@link FovStealth}'s acceptance boundary at each deafened mob's eye, tilted with live head yaw/pitch.
 * Singleplayer/LAN-host only: mob effects don't sync to clients, so deafness is read off the integrated server.
 */
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public final class FovConeDebugRenderer {

    /** Debug switch: flip to true to draw the cones (F3 must also be open). */
    public static boolean ENABLED = true;

    private static final double RANGE = 48.0;  // camera-to-mob cull distance
    private static final double RADIUS = 8.0;  // cone draw radius (blocks)
    private static final double STEP = 7.5;    // arc segment size (degrees)

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.getDebugOverlay().showDebugScreen()) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (mob.distanceToSqr(cam.x, cam.y, cam.z) > RANGE * RANGE) continue;
            if (Deafening.isImmune(mob) || Deafening.sensesViaVibration(mob)) continue;
            // Read the authoritative effect off the server twin (client copies carry no effect instances).
            if (!(serverLevel.getEntity(mob.getUUID()) instanceof LivingEntity twin) || !Deafening.isDeafened(twin)) continue;
            drawCone(pose, lines, mob.getEyePosition(), FovStealth.basis(mob.getYHeadRot(), mob.getXRot()));
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Draws the cone as one closed wireframe frustum in the mob's head frame: rim loop (top green,
     *  sides red, bottom blue), a dimmed half-radius rim for depth, and gray lateral rays. */
    private static void drawCone(PoseStack pose, VertexConsumer lines, Vec3 eye, FovStealth.Basis head) {
        double hh = FovStealth.H_HALF_DEG, up = FovStealth.V_UP_DEG, down = FovStealth.V_DOWN_DEG;

        record Pt(double az, double el, float r, float g, float b) {}
        java.util.List<Pt> rim = new java.util.ArrayList<>();
        for (double az = -hh; az < hh; az += STEP) rim.add(new Pt(az, up, 0.1f, 1.0f, 0.1f));      // top L->R (green)
        for (double el = up; el > -down; el -= STEP) rim.add(new Pt(hh, el, 1.0f, 0.2f, 0.2f));    // right U->D (red)
        for (double az = hh; az > -hh; az -= STEP) rim.add(new Pt(az, -down, 0.25f, 0.5f, 1.0f));  // bottom R->L (blue)
        for (double el = -down; el < up; el += STEP) rim.add(new Pt(-hh, el, 1.0f, 0.2f, 0.2f));   // left D->U (red)

        int n = rim.size();
        for (int i = 0; i < n; i++) {
            Pt a = rim.get(i), b = rim.get((i + 1) % n);
            Vec3 pa = at(eye, head, a.az, a.el), pb = at(eye, head, b.az, b.el);
            line(pose, lines, pa, pb, a.r, a.g, a.b, 1.0f);
            line(pose, lines, eye.add(pa.subtract(eye).scale(0.5)), eye.add(pb.subtract(eye).scale(0.5)),
                    a.r * 0.6f, a.g * 0.6f, a.b * 0.6f, 0.8f);
            if (i % 3 == 0) line(pose, lines, eye, pa, 0.65f, 0.65f, 0.65f, 0.35f); // lateral surface rays
        }
        for (double[] c : new double[][]{{-hh, up}, {hh, up}, {-hh, -down}, {hh, -down}}) { // corner rays
            line(pose, lines, eye, at(eye, head, c[0], c[1]), 0.85f, 0.85f, 0.85f, 0.8f);
        }
        line(pose, lines, eye, at(eye, head, 0, 0), 1.0f, 1.0f, 1.0f, 1.0f); // forward ray (white)
    }

    /**
     * Point at {@code RADIUS} along (azimuth, elevation) in the head frame — the same basis
     * {@link FovStealth#inFovCone} tests against, so the drawn boundary coincides with the thresholds.
     */
    private static Vec3 at(Vec3 eye, FovStealth.Basis head, double azDeg, double elevDeg) {
        double e = Math.toRadians(elevDeg);
        double a = Math.toRadians(azDeg);
        double ce = Math.cos(e);
        Vec3 dir = head.fwd().scale(ce * Math.cos(a))
                .add(head.right().scale(ce * Math.sin(a)))
                .add(head.up().scale(Math.sin(e)));
        return eye.add(dir.scale(RADIUS));
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float r, float g, float bl, float alpha) {
        PoseStack.Pose p = pose.last();
        Vec3 n = b.subtract(a).normalize();
        vc.addVertex(p, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha)
                .setNormal(p, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(p, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha)
                .setNormal(p, (float) n.x, (float) n.y, (float) n.z);
    }
}
