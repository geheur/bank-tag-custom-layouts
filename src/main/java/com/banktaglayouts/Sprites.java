package com.banktaglayouts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.SpriteOverride;

@RequiredArgsConstructor
public enum Sprites implements SpriteOverride
{
    APPLY_PREVIEW(-3192, "confirm_icon.png"), // 3192 is definitely not my bank pin.
    CANCEL_PREVIEW(-3193, "delete.png"),
    AUTO_LAYOUT(-3194, "auto_layout.png"),
    DUPLICATE_MODE(-3195, "duplicate_mode.png"),
    DUPLICATE_MODE_ACTIVE(-3196, "duplicate_mode_active.png"),
    REMOVE_DUPLICATE_MODE(-3197, "remove_duplicate_mode.png"),
    REMOVE_DUPLICATE_MODE_ACTIVE(-3198, "remove_duplicate_mode_active.png"),
    ;

    @Getter
    private final int spriteId;

    @Getter
    private final String fileName;
}
