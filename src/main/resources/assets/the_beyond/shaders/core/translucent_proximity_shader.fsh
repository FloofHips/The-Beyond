#version 150

uniform vec2 iResolution;     // Resolution of the screen
uniform sampler2D Sampler0;  // The texture to which the highlight mask will be applied

in vec2 texCoord0;           // Texture coordinates input
out vec4 fragColor;          // Final fragment color output

// Hash function to create a pseudo-random value
float Hash(vec2 p) {
    vec3 p2 = vec3(p.xy, 1.0);
    return fract(sin(dot(p2, vec3(37.1, 61.7, 12.4))) * 758.5453123);
}

// 2D noise function using the hash function
float noise(in vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f *= f * (3.0 - 2.0 * f);

    return mix(mix(Hash(i + vec2(0., 0.)), Hash(i + vec2(1., 0.)), f.x),
    mix(Hash(i + vec2(0., 1.)), Hash(i + vec2(1., 1.)), f.x),
    f.y);
}

// Fractal Brownian Motion function for more complexity in noise
float fbm(vec2 p) {
    float v = 0.0;
    v += noise(p * 1.0) * 0.1;   // Frequency 1
    v += noise(p * 2.0) * 0.025;  // Frequency 2
    v += noise(p * 4.0) * 0.125;  // Frequency 3
    v += noise(p * 8.0) * 0.0625; // Frequency 4
    return v;
}

void main() {
    // Normalize pixel coordinates
    vec2 uv = texCoord0; // Use the incoming texture coordinates

    // Calculate the distance from the center (0.5, 0.5)
    float dist = distance(uv, vec2(0.5)); // Center of the texture

    // Scale the distance for noise input (adjusted for a smaller highlight effect)
    float scaledDist = dist * 15.0; // Increase the multiplier for a smaller effect

    // Calculate the noise value based on the scaled distance
    float k = clamp(fbm(vec2(scaledDist)), 0.1, 1.0) - 0.1; // Calculate noise value and clamp
    k = k * 2.0; // Scale the noise down

    // Generate a color based on the noise value
    vec3 col = vec3(k, k, k); // Grayscale color for highlight

    // Sample the original texture (optional)
    vec4 textureColor = texture(Sampler0, uv);

    // Output the highlight mask (you can blend this with the original texture if needed)
    fragColor = vec4(textureColor.rgb + col * 0.5, 1.0); // Add highlight to the original texture color
}
