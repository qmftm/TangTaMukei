package com.tangtamukei.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

/** MiniMessage 헬퍼. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String input, String... placeholders) {
        if (input == null) return Component.empty();
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolver.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return MM.deserialize(input, resolver.build());
    }

    /** 아이템 이름/설명용: 기본 기울임꼴 제거. */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines) {
        return lines.stream().map(Text::item).toList();
    }

    /** 베드락 폼은 § 색코드 문자열만 받으므로 변환. */
    public static String legacy(String input) {
        return LegacyComponentSerializer.legacySection().serialize(parse(input));
    }
}
