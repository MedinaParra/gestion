package cl.exequiel.royalspin;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Small allocation-conscious particle field for reel impacts and win celebrations. */
public final class ParticleField {
    private static final int MAX_PARTICLES = 220;

    private static final class Particle {
        float x;
        float y;
        float velocityX;
        float velocityY;
        float gravity;
        float size;
        float rotation;
        float rotationSpeed;
        float age;
        float lifetime;
        int color;
        boolean square;
    }

    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    private final Random random = new Random(0xC01DF00DL);

    public void burst(float x, float y, int count, int color, float power) {
        int available = Math.max(0, MAX_PARTICLES - particles.size());
        int amount = Math.min(Math.max(0, count), available);
        for (int i = 0; i < amount; i++) {
            double angle = -Math.PI + random.nextDouble() * Math.PI;
            float velocity = (35f + random.nextFloat() * 105f) * Math.max(0.2f, power);
            Particle particle = new Particle();
            particle.x = x;
            particle.y = y;
            particle.velocityX = (float) Math.cos(angle) * velocity;
            particle.velocityY = (float) Math.sin(angle) * velocity - 25f * power;
            particle.gravity = 105f + random.nextFloat() * 90f;
            particle.size = 1.8f + random.nextFloat() * 4.2f;
            particle.rotation = random.nextFloat() * 360f;
            particle.rotationSpeed = -220f + random.nextFloat() * 440f;
            particle.lifetime = 0.45f + random.nextFloat() * 0.75f;
            particle.color = color;
            particle.square = random.nextBoolean();
            particles.add(particle);
        }
    }

    public void celebration(float centerX, float centerY, double multiplier) {
        float intensity = multiplier >= 20d ? 1.65f : multiplier >= 5d ? 1.2f : 0.8f;
        int count = multiplier >= 20d ? 90 : multiplier >= 5d ? 58 : 34;
        int[] colors = {0xFFF6C453, 0xFFFFE8A5, 0xFF8D6BFF, 0xFF55C9FF, 0xFFFF6F91};
        for (int i = 0; i < colors.length; i++) {
            burst(centerX + (i - 2) * 8f, centerY, count / colors.length, colors[i], intensity);
        }
    }

    public void update(float deltaSeconds) {
        float dt = Math.min(0.05f, Math.max(0f, deltaSeconds));
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.age += dt;
            if (particle.age >= particle.lifetime) {
                iterator.remove();
                continue;
            }
            particle.velocityY += particle.gravity * dt;
            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.rotation += particle.rotationSpeed * dt;
        }
    }

    public void draw(Canvas canvas, Paint paint) {
        Paint.Style previousStyle = paint.getStyle();
        for (Particle particle : particles) {
            float remaining = 1f - particle.age / particle.lifetime;
            int alpha = Math.max(0, Math.min(255, Math.round(255f * remaining)));
            paint.setColor((particle.color & 0x00FFFFFF) | (alpha << 24));
            paint.setStyle(Paint.Style.FILL);
            if (particle.square) {
                canvas.save();
                canvas.rotate(particle.rotation, particle.x, particle.y);
                float half = particle.size * 0.9f;
                canvas.drawRect(particle.x - half, particle.y - half,
                        particle.x + half, particle.y + half, paint);
                canvas.restore();
            } else {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint);
            }
        }
        paint.setStyle(previousStyle);
    }

    public boolean hasParticles() {
        return !particles.isEmpty();
    }

    public void clear() {
        particles.clear();
    }
}
