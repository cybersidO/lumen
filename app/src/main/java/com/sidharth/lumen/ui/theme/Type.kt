package com.sidharth.lumen.ui.theme

import androidx.compose.material3.Typography

/**
 * App chrome only. Everything here is in `sp`, so the app UI honours the system font
 * scale (§5, accessibility).
 *
 * The *book's* type is a separate scale that deliberately ignores system font scale —
 * the reader's own font-size control owns it. That arrives with the reader in M2.
 */
internal val LumenTypography = Typography()
