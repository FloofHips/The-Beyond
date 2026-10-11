#version 150

uniform sampler2D Sampler0; // raw item icon
uniform sampler2D Sampler1; // ramp LUT (Nx1)
uniform float Strength;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.004) {
        discard; // keep the transparent silhouette
    }
    float l = clamp(dot(color.rgb, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    // texel-centre sample so u=1 can't wrap to texel 0 under the LUT's GL_REPEAT (N=256)
    float u = l * (255.0 / 256.0) + (0.5 / 256.0);
    vec3 ramp = texture(Sampler1, vec2(u, 0.5)).rgb;
    vec3 graded = mix(color.rgb, ramp, Strength);
    fragColor = vec4(graded, color.a);
}
