package com.tangtamukei.lobby;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;
import java.util.function.IntConsumer;

/** Floodgate 가 설치돼 있을 때만 로드되는 클래스. */
final class FloodgateHook implements BedrockSupport {

    private final FloodgateApi api = FloodgateApi.getInstance();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isBedrock(Player player) {
        return api.isFloodgatePlayer(player.getUniqueId());
    }

    @Override
    public boolean sendButtonForm(Player player, String title, String content,
                                  List<Button> buttons, IntConsumer onClick) {
        if (!isBedrock(player)) return false;

        SimpleForm.Builder form = SimpleForm.builder().title(title).content(content);
        for (Button button : buttons) {
            String image = button.image();
            if (image == null || image.isBlank()) {
                form.button(button.text());
            } else {
                FormImage.Type type = image.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH;
                form.button(button.text(), type, image);
            }
        }
        form.validResultHandler(response -> onClick.accept(response.clickedButtonId()));
        return api.sendForm(player.getUniqueId(), form.build());
    }
}
