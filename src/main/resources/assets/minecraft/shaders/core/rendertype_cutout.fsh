#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 rawColor;       // Original vertex color (without lightmap) for crystal detection
in vec2 texCoord0;
in vec3 worldPos;       // World-space position for continuous shine patterns

out vec4 fragColor;

// ============================================================================
// CRYSTAL DETECTION VIA VERTEX COLOR RATIO  (The Beyond mod)
// ============================================================================

const float CRYSTAL_MARKER_RATIO = 0.9608;  // 245.0 / 255.0
const float CRYSTAL_MARKER_TOLERANCE = 0.015;

bool isCrystalBlock() {
    if (rawColor.r < 0.01) return false;
    float ratio = rawColor.g / rawColor.r;
    return abs(ratio - CRYSTAL_MARKER_RATIO) < CRYSTAL_MARKER_TOLERANCE;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }

    if (isCrystalBlock()) {

        float offset = FogEnd * 0.01;

        // World-space coordinates for continuous patterns across stacked blocks
        vec3 wp = worldPos * 16.0;
        float hAxis = wp.x + wp.z;
        float vAxis = wp.y;

        // Dynamic shift from camera distance -- creates mirror movement
        float dynamicShift = vertexDistance * 0.15;

        float diagonal = mod(hAxis * offset + vAxis * offset + dynamicShift, 16.0);

        // Exponential glow falloff for layered depth
        float glow = exp(-abs(diagonal - 16.0));

        // Sharp line core
        float line = 1.0 - step(8.0, abs(diagonal - 16.0));

        // Shine: sharp core + exponential halo
        vec3 shine = vec3(0.2 + glow, 0.0, 0.2 + glow) * line;
        vec3 halo = vec3(glow * 0.06, 0.0, glow * 0.06);

        vec3 totalShine = (shine + halo) * vertexColor.rgb * ColorModulator.rgb;
        color = vec4(color.rgb + totalShine, color.a);
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
