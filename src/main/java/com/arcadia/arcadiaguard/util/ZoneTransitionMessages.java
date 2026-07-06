package com.arcadia.arcadiaguard.util;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class ZoneTransitionMessages {

    public enum Kind { GREETING, FAREWELL }
    private static final String DEFAULT_DISPLAY = "actionbar";

    public record Resolved(String message, String display) {
        public boolean enabled() { return !message.isBlank(); }
    }

    private ZoneTransitionMessages() {}

    public static Resolved resolve(ProtectedZone zone, Kind kind, Function<String, Optional<ProtectedZone>> parentLookup) {
        Function<String, Map<String, Object>> dimLookup = dim -> ArcadiaGuard.dimFlagStore().flags(dim);
        String raw = FlagResolver.resolve(zone, kind == Kind.GREETING ? BuiltinFlags.GREETING : BuiltinFlags.FAREWELL,
            parentLookup, dimLookup);
        return decode(raw);
    }

    public static Resolved decode(String raw) {
        if (raw == null || raw.isBlank()) return new Resolved("", DEFAULT_DISPLAY);
        int sep = raw.indexOf('|');
        if (sep <= 0) return new Resolved(raw, DEFAULT_DISPLAY);
        String display = raw.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        if (!isDisplay(display)) display = DEFAULT_DISPLAY;
        return new Resolved(raw.substring(sep + 1), display);
    }

    private static boolean isDisplay(String value) {
        return "chat".equals(value) || "actionbar".equals(value) || "title".equals(value);
    }

    public static boolean differs(Resolved left, Resolved right) {
        if (left == null && right == null) return false;
        if (left == null || right == null) return true;
        return !Objects.equals(left.message, right.message) || !Objects.equals(left.display, right.display);
    }

    public static void send(ServerPlayer player, ProtectedZone zone, Resolved resolved) {
        if (resolved == null || !resolved.enabled()) return;
        String rendered = resolved.message()
            .replace("{player}", player.getGameProfile().getName())
            .replace("{zone}", zone.name())
            .replace("\\n", "\n");
        switch (resolved.display()) {
            case "chat" -> player.sendSystemMessage(parseLegacy(rendered));
            case "title" -> sendTitle(player, rendered);
            case "actionbar" -> player.displayClientMessage(parseLegacy(rendered.replace('\n', ' ')), true);
            default -> player.displayClientMessage(parseLegacy(rendered.replace('\n', ' ')), true);
        }
    }

    private static void sendTitle(ServerPlayer player, String raw) {
        String[] parts = raw.split("\n", 2);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(parseLegacy(parts[0])));
        if (parts.length > 1) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(parseLegacy(parts[1])));
        } else {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        }
    }

    public static Component parseLegacy(String raw) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                if (i + 7 < raw.length() && raw.charAt(i + 1) == '#') {
                    Integer rgb = parseHex(raw.substring(i + 2, i + 8));
                    if (rgb != null) {
                        append(root, text, style);
                        style = style.withColor(TextColor.fromRgb(rgb));
                        i += 7;
                        continue;
                    }
                }
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (formatting != null) {
                    append(root, text, style);
                    style = apply(style, formatting);
                    i++;
                    continue;
                }
            }
            text.append(c);
        }
        append(root, text, style);
        return root;
    }

    private static Integer parseHex(String hex) {
        try { return Integer.parseInt(hex, 16); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static void append(MutableComponent root, StringBuilder text, Style style) {
        if (text.isEmpty()) return;
        root.append(Component.literal(text.toString()).withStyle(style));
        text.setLength(0);
    }

    private static Style apply(Style style, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) return Style.EMPTY;
        if (formatting.isColor()) return style.withColor(formatting);
        return switch (formatting) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }
}
