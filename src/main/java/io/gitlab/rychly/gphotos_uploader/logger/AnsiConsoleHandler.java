package io.gitlab.rychly.gphotos_uploader.logger;

import org.fusesource.jansi.AnsiConsole;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Color ConsoleHandler using ANSI sequences.
 */
public class AnsiConsoleHandler extends ConsoleHandler {
    private static final String COLOR_RESET = "\u001b[0m";
    private static final String COLOR_SEVERE = "\u001b[91m";
    private static final String COLOR_WARNING = "\u001b[93m";
    private static final String COLOR_INFO = "\u001b[32m";
    private static final String COLOR_CONFIG = "\u001b[94m";
    private static final String COLOR_FINE = "\u001b[36m";
    private static final String COLOR_FINER = "\u001b[35m";
    private static final String COLOR_FINEST = "\u001b[90m";

    /**
     * Publish a <tt>LogRecord</tt>.
     * <p>
     * The logging request was made initially to a <tt>Logger</tt> object,
     * which initialized the <tt>LogRecord</tt> and forwarded it here.
     * <p>
     *
     * @param record description of the log event. A null record is
     *               silently ignored and is not published
     */
    @Override
    public void publish(LogRecord record) {
        if (isLoggable(record)) {
            final String formattedRecord = getFormatter().format(record);
            final Level recordLevel = record.getLevel();
            final String colorPrefix = recordLevel == Level.SEVERE ? COLOR_SEVERE
                    : recordLevel == Level.WARNING ? COLOR_WARNING
                    : recordLevel == Level.INFO ? COLOR_INFO
                    : recordLevel == Level.CONFIG ? COLOR_CONFIG
                    : recordLevel == Level.FINE ? COLOR_FINE
                    : recordLevel == Level.FINER ? COLOR_FINER
                    : recordLevel == Level.FINEST ? COLOR_FINEST
                    : COLOR_RESET;
            AnsiConsole.err.print(colorPrefix + formattedRecord + COLOR_RESET);
        }
    }

    /**
     * Flush any buffered messages.
     */
    @Override
    public synchronized void flush() {
        AnsiConsole.err.flush();
        super.flush();
    }
}
