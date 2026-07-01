#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// SnapshotGrade.SEPIA stops (#2b1d0e -> #7a5230 -> #f0e0c0), evenly spaced, blended at strength 0.45.
vec3 ramp(float l) {
    vec3 c0 = vec3(0.169, 0.114, 0.055);
    vec3 c1 = vec3(0.478, 0.322, 0.188);
    vec3 c2 = vec3(0.941, 0.878, 0.753);
    if (l < 0.5) return mix(c0, c1, l / 0.5);
    return mix(c1, c2, (l - 0.5) / 0.5);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.004) {
        discard; // keep the transparent silhouette
    }
    float l = clamp(dot(color.rgb, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    vec3 graded = mix(color.rgb, ramp(l), 0.45);
    fragColor = vec4(graded, color.a);
}
