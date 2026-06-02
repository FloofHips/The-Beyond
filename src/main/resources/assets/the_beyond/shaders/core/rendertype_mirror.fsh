#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;

in vec4 vertexColor;

out vec4 fragColor;

// Screen-space lookup: FBO is screen-aligned but at the plane's LOD resolution.
void main() {
    vec4 reflection = texture(Sampler0, gl_FragCoord.xy / ScreenSize);
    fragColor = reflection * vertexColor * ColorModulator;
}
