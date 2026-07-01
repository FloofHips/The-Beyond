package com.thebeyond.compat.sable.client;

import com.thebeyond.client.renderer.blockentities.ProjectorRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/** Sable-only; load only when Sable present. Plain {@code level.clip} projects from/to independently and returns plot-LOCAL hits, so we ray in host space and raw-clip each crossed frame. */
public final class ProjectorSableFrame {
    private ProjectorSableFrame() {
    }

    public static void install() {
        ProjectorRenderer.subLevelClip = ProjectorSableFrame::clipAcrossFrames;
        ProjectorRenderer.meshOcc = ProjectorSableFrame::meshOcc;
        ProjectorRenderer.onContraption = ProjectorSableFrame::isOnContraption;
        ProjectorRenderer.contraptionFrame = ProjectorSableFrame::contraptionFrame;
        ProjectorRenderer.intersectingFrames = ProjectorSableFrame::intersectingFrames;
    }

    /** {@code m} maps {@code (gridCoord - rotationPoint)} to camera-relative space; getTransformation bakes in {@code -camPos}. */
    private static ProjectorRenderer.ContraptionFrame contraptionFrame(Level level, BlockPos pos, Vec3 camPos, float partialTick) {
        try {
            return Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel csl ? frameOf(csl, camPos, partialTick) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<ProjectorRenderer.ContraptionFrame> intersectingFrames(Level level, AABB worldBounds, Vec3 camPos, float partialTick) {
        List<ProjectorRenderer.ContraptionFrame> out = new ArrayList<>();
        try {
            BoundingBox3d box = new BoundingBox3d(
                    new Vec3(worldBounds.minX, worldBounds.minY, worldBounds.minZ),
                    new Vec3(worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ));
            for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, box)) {
                if (sub instanceof ClientSubLevel csl) {
                    out.add(frameOf(csl, camPos, partialTick));
                }
            }
        } catch (Throwable t) {
            return out;
        }
        return out;
    }

    private static ProjectorRenderer.ContraptionFrame frameOf(ClientSubLevel csl, Vec3 camPos, float partialTick) {
        csl.renderPose(partialTick); // populates the interpolated-pose cache; do not drop
        Vector3dc rp = csl.renderPose().rotationPoint();
        Matrix4f m = csl.getRenderData().getTransformation(camPos.x, camPos.y, camPos.z, new Matrix4f());
        Matrix4f minv = new Matrix4f(m).invert();
        return new ProjectorRenderer.ContraptionFrame(m, minv, rp.x(), rp.y(), rp.z());
    }

    private static boolean isOnContraption(Level level, BlockPos pos) {
        try {
            return Sable.HELPER.getContaining(level, pos) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static double meshOcc(Level level, Vec3 eye, Vec3 p, BlockPos self) {
        try {
            Vec3 rel = p.subtract(eye);
            double dist = rel.length();
            if (dist < 1.0e-4) {
                return 1.0;
            }
            BlockHitResult hit = rawClip(level, eye, p);
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(self)) {
                return 1.0;
            }
            return hit.getLocation().distanceTo(eye) < dist - 0.06 ? 0.0 : 1.0; // 0.06 = ProjectorRenderer.OCC_EPS
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private static double clipAcrossFrames(Level level, BlockPos pos, Vec3 eye, Vec3 forward, double maxThrow) {
        try {
            // Raw matrix transform, not projectOutOfSubLevel: this works past the plot edge.
            SubLevel own = Sable.HELPER.getContaining(level, pos);
            Vec3 eyeHost;
            Vec3 endHost;
            if (own != null) {
                Pose3dc pose = poseOf(level, own);
                eyeHost = JOMLConversion.toMojang(pose.transformPosition(JOMLConversion.toJOML(eye)));
                endHost = JOMLConversion.toMojang(pose.transformPosition(JOMLConversion.toJOML(eye.add(forward.scale(maxThrow)))));
            } else {
                eyeHost = eye;
                endHost = eye.add(forward.scale(maxThrow));
            }

            double best = -1.0; // sentinel: nothing hit

            BlockHitResult hostHit = rawClip(level, eyeHost, endHost);
            if (hostHit.getType() != HitResult.Type.MISS) {
                best = hostHit.getLocation().distanceTo(eyeHost);
            }

            BoundingBox3d bounds = new BoundingBox3d(eyeHost, endHost);
            for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, bounds)) {
                Pose3dc pose = poseOf(level, sub);
                Vec3 fromL = JOMLConversion.toMojang(pose.transformPositionInverse(JOMLConversion.toJOML(eyeHost)));
                Vec3 toL = JOMLConversion.toMojang(pose.transformPositionInverse(JOMLConversion.toJOML(endHost)));
                if (Sable.HELPER.getContaining(level, fromL) != sub) {
                    continue;
                }
                BlockHitResult subHit = rawClip(sub.getLevel(), fromL, toL);
                if (subHit.getType() != HitResult.Type.MISS) {
                    double d = subHit.getLocation().distanceTo(fromL); // rigid pose: plot-local distance == visible distance
                    if (best < 0.0 || d < best) {
                        best = d;
                    }
                }
            }
            return best;
        } catch (Throwable t) {
            return -1.0;
        }
    }

    private static Pose3dc poseOf(Level level, SubLevel sub) {
        if (level instanceof LevelPoseProviderExtension ext) {
            return ext.sable$getPose(sub);
        }
        return sub.logicalPose();
    }

    /** {@code doNotProject} makes Sable's overwritten clip skip recursion and clip raw single-frame blocks. */
    private static BlockHitResult rawClip(Level level, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        ((ClipContextExtension) ctx).sable$setDoNotProject(true);
        return level.clip(ctx);
    }
}
