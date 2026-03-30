#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord2;

out vec4 fragColor;

// Gradient map colors (dark to light):
// 0.00 -> #0c0826
// 0.30 -> #31334b
// 0.75 -> #7989a9
// 0.92 -> #87b1da
// 1.00 -> #e8f4ff

vec3 gradientMap(float brightness) {
    vec3 c0 = vec3(0.047, 0.031, 0.149); // #0c0826
    vec3 c1 = vec3(0.192, 0.200, 0.294); // #31334b
    vec3 c2 = vec3(0.475, 0.537, 0.663); // #7989a9
    vec3 c3 = vec3(0.529, 0.694, 0.855); // #87b1da
    vec3 c4 = vec3(0.910, 0.957, 1.000); // #e8f4ff

    if (brightness < 0.30) {
        return mix(c0, c1, brightness / 0.30);
    } else if (brightness < 0.75) {
        return mix(c1, c2, (brightness - 0.30) / 0.45);
    } else if (brightness < 0.92) {
        return mix(c2, c3, (brightness - 0.75) / 0.17);
    } else {
        return mix(c3, c4, (brightness - 0.92) / 0.08);
    }
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }

    // Convert to grayscale (luminance)
    float brightness = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Apply gradient map
    vec3 mapped = gradientMap(brightness);

    // Apply lightmap
    vec4 lightColor = texture(Sampler2, texCoord2);

    fragColor = vec4(mapped, color.a) * lightColor * vertexColor * ColorModulator;
}
