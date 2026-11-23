package py.crz.flowlauncher;

/** Temas de colores del launcher. */
final class LauncherTheme {
    final String name;
    final int bgTop, bgBottom, accent, onAccent, card, text, subtext;

    LauncherTheme(String name, int bgTop, int bgBottom, int accent, int onAccent,
                  int card, int text, int subtext) {
        this.name = name;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.accent = accent;
        this.onAccent = onAccent;
        this.card = card;
        this.text = text;
        this.subtext = subtext;
    }

    static final LauncherTheme[] ALL = {
            new LauncherTheme("Azul Flow", 0xFF0B2250, 0xFF02060F, 0xFF00A3FF, 0xFFFFFFFF,
                    0x2EFFFFFF, 0xFFFFFFFF, 0xB3FFFFFF),
            new LauncherTheme("Noche", 0xFF1E1E1E, 0xFF000000, 0xFFFFC107, 0xFF111111,
                    0x26FFFFFF, 0xFFFFFFFF, 0xA6FFFFFF),
            new LauncherTheme("Esmeralda", 0xFF064635, 0xFF01110C, 0xFF2EE59D, 0xFF04241A,
                    0x2EFFFFFF, 0xFFFFFFFF, 0xB3FFFFFF),
            new LauncherTheme("Púrpura", 0xFF2E0B52, 0xFF0B0214, 0xFFC77DFF, 0xFF1A0530,
                    0x2EFFFFFF, 0xFFFFFFFF, 0xB3FFFFFF),
            new LauncherTheme("Rojo", 0xFF4F0D12, 0xFF120203, 0xFFFF5A5F, 0xFFFFFFFF,
                    0x2EFFFFFF, 0xFFFFFFFF, 0xB3FFFFFF),
            new LauncherTheme("Claro", 0xFFEEF2F8, 0xFFC9D4E3, 0xFF0B63CE, 0xFFFFFFFF,
                    0x1A000000, 0xFF0D1520, 0xB30D1520),
    };

    static LauncherTheme get(int index) {
        if (index < 0 || index >= ALL.length) return ALL[0];
        return ALL[index];
    }

    static String[] names() {
        String[] n = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) n[i] = ALL[i].name;
        return n;
    }
}
