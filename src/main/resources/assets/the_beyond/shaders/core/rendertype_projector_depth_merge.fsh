#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    float d = texture(Sampler0, gl_FragCoord.xy / ScreenSize).r;
    if (d >= 1.0) {
        discard;
    }
    gl_FragDepth = d;
    fragColor = vec4(0.0);
}
