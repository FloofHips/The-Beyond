#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord2;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }

    float nearDistance = 0.0;
    float farDistance = 16.0;

    //float normalizedDistance = (vertexDistance - nearDistance) / (farDistance - nearDistance);
    //normalizedDistance = clamp(normalizedDistance, 0.0, 1.0);

    vec3 white = vec3(1.0, 1.0, 1.0);
    vec3 gray = vec3(0.5, 0.5, 0.5);

    vec3 depthColor = mix(white, gray, 1-color.a);

    fragColor = vec4(depthColor, color.a) * ColorModulator;
}