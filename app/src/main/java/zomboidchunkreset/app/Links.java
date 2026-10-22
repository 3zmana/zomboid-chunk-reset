package zomboidchunkreset.app;

import javafx.application.HostServices;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Opens web links in the user's browser (through JavaFX host services). */
public final class Links {

    private static volatile HostServices hostServices;

    private Links() {
    }

    /** Called once by the application at start-up. */
    static void init(HostServices services) {
        hostServices = services;
    }

    /**
     * Opens {@code url} in the browser. Without a browser (or before start-up), copies it to the
     * clipboard instead.
     *
     * @return {@code true} if the browser was asked to open it, {@code false} if it was copied
     */
    public static boolean open(String url) {
        HostServices services = hostServices;
        if (services != null) {
            try {
                services.showDocument(url);
                return true;
            } catch (RuntimeException e) {
                // fall back to the clipboard
            }
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        return false;
    }
}
