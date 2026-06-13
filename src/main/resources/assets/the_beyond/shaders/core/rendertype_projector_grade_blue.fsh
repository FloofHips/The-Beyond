#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// SnapshotGrade.BLUE stops (#0c0826 -> #31334b -> #7989a9 -> #e8f4ff), evenly spaced, blended at strength 0.45.
vec3 ramp(float l) {
    vec3 c0 = vec3(0.047, 0.031, 0.149);
    vec3 c1 = vec3(0.192, 0.200, 0.294);
    vec3 c2 = vec3(0.475, 0.537, 0.663);
    vec3 c3 = vec3(0.910, 0.957, 1.000);
    float p = l * 3.0;
    if (p < 1.0) return mix(c0, c1, p);
    if (p < 2.0) return mix(c1, c2, p - 1.0);
    return mix(c2, c3, p - 2.0);
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
