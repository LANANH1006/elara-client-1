package com.elara.client.compat;

import com.elara.client.ui.ElaraMenuScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Nút Config trong Mod Menu mở đúng menu chính của Elara, giống khi bấm phím mở menu trong game.
 * Lớp này chỉ được Mod Menu gọi, nên nếu không cài Mod Menu thì không bị nạp.
 */
public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ElaraMenuScreen(parent);
    }
}
