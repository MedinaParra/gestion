package cl.exequiel.royalspin;

import java.util.List;

/** Pure animation math. It never changes the board, payout or RNG outcome. */
public final class SymbolAnimationDirector {
    public enum Style { NONE, BELL, BAR, SEVEN, DIAMOND, WILD, CARD }
    public enum Tier { SMALL, BIG, MEGA, ROYAL }

    private SymbolAnimationDirector() { }

    public static Style styleFor(String symbol) {
        if (StakeSlotEngine.BELL.equals(symbol)) return Style.BELL;
        if (StakeSlotEngine.BAR.equals(symbol)) return Style.BAR;
        if (StakeSlotEngine.SEVEN.equals(symbol)) return Style.SEVEN;
        if (StakeSlotEngine.DIAMOND.equals(symbol)) return Style.DIAMOND;
        if (StakeSlotEngine.WILD.equals(symbol)) return Style.WILD;
        if (symbol == null || symbol.isEmpty()) return Style.NONE;
        return Style.CARD;
    }

    public static Tier tierFor(double multiplier) {
        if (multiplier >= 100d) return Tier.ROYAL;
        if (multiplier >= 25d) return Tier.MEGA;
        if (multiplier >= 5d) return Tier.BIG;
        return Tier.SMALL;
    }

    public static StakeSlotEngine.LineWin primaryWin(List<StakeSlotEngine.LineWin> wins) {
        if (wins == null || wins.isEmpty()) return null;
        StakeSlotEngine.LineWin best = wins.get(0);
        for (int i = 1; i < wins.size(); i++) {
            StakeSlotEngine.LineWin candidate = wins.get(i);
            if (candidate.payout > best.payout
                    || (candidate.payout == best.payout && candidate.count > best.count)) {
                best = candidate;
            }
        }
        return best;
    }

    public static Frame frame(String symbol, int count, double multiplier, long elapsedMs) {
        Style style = styleFor(symbol);
        Tier tier = tierFor(multiplier);
        float enter = easeOutBack(window(elapsedMs, 0L, 520L));
        float energy = 0.5f + 0.5f * (float) Math.sin(elapsedMs * 0.014f);
        float ring = window(elapsedMs, 110L, tier == Tier.ROYAL ? 2400L : 1500L);
        float flash = 1f - window(elapsedMs, 0L, 340L);
        float damp = (float) Math.exp(-Math.max(0L, elapsedMs) / 1350f);
        float intensity = 1f + Math.max(0, count - 3) * 0.22f;
        if (tier == Tier.BIG) intensity += 0.22f;
        else if (tier == Tier.MEGA) intensity += 0.48f;
        else if (tier == Tier.ROYAL) intensity += 0.78f;

        float rotation = 0f;
        float scale = 1f + 0.035f * energy;
        float lift = 0f;
        switch (style) {
            case BELL:
                rotation = (float) Math.sin(elapsedMs * 0.025f) * 14f * damp;
                lift = -2.2f * energy;
                break;
            case BAR:
                scale = 0.94f + 0.08f * enter + 0.025f * energy;
                break;
            case SEVEN:
                rotation = -5f + 5f * enter;
                lift = -4f * enter + 1.5f * energy;
                scale = 0.9f + 0.13f * enter;
                break;
            case DIAMOND:
                rotation = (float) Math.sin(elapsedMs * 0.0045f) * 7f;
                lift = -3f * energy;
                scale = 0.93f + 0.11f * enter + 0.025f * energy;
                break;
            case WILD:
                rotation = (float) Math.sin(elapsedMs * 0.003f) * 4f;
                lift = -5f * enter - 2f * energy;
                scale = 0.9f + 0.16f * enter + 0.03f * energy;
                break;
            case CARD:
                lift = -2f * energy;
                scale = 0.96f + 0.07f * enter;
                break;
            default:
                break;
        }
        return new Frame(style, tier, clamp(enter), clamp(ring), clamp(flash), energy,
                rotation, scale, lift, intensity);
    }

    static float window(long elapsedMs, long startMs, long durationMs) {
        if (durationMs <= 0L) return elapsedMs >= startMs ? 1f : 0f;
        return clamp((elapsedMs - startMs) / (float) durationMs);
    }

    static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float q = x - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class Frame {
        public final Style style;
        public final Tier tier;
        public final float enter;
        public final float ring;
        public final float flash;
        public final float energy;
        public final float rotation;
        public final float scale;
        public final float lift;
        public final float intensity;

        Frame(Style style, Tier tier, float enter, float ring, float flash, float energy,
              float rotation, float scale, float lift, float intensity) {
            this.style = style;
            this.tier = tier;
            this.enter = enter;
            this.ring = ring;
            this.flash = flash;
            this.energy = energy;
            this.rotation = rotation;
            this.scale = scale;
            this.lift = lift;
            this.intensity = intensity;
        }
    }
}
