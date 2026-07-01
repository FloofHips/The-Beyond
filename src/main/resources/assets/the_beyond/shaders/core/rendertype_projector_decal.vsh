#version 150

in vec3 Position;

void main() {
    // Position is already NDC (fullscreen quad); pass through. The fragment reconstructs world pos from scene depth.
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
