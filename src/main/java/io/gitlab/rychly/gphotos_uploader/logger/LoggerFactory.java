package io.gitlab.rychly.gphotos_uploader.logger;

import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public final class LoggerFactory {
    public static final Level DEFAULT_CONSOLE_LOG_LEVEL = Level.CONFIG;
    public static final Level DEFAULT_FILE_LOG_LEVEL = Level.ALL;
    public static final String TEMP_LOG_FILE_PATTERN = "%t/{}.log";
    public static final Logger TOP_LOGGER = Logger.getLogger("");
    public static final String HANDLER_ENCODING = "UTF-8";
    public static final DateFormat TEMP_LOG_FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static Logger logger = TOP_LOGGER;

    /**
     * Create a new top logger with a given name and ALL log messages level.
     *
     * @param loggerName a name of the new logger (the last part of the logger hierarchy)
     * @return <code>true</code> if the logger has been created, <code>false</code> the logger cloud not be created as there is already a top logger
     */
    public static boolean init(String loggerName) {
        if (logger == TOP_LOGGER) {
            appendLogger(loggerName);
            return true;
        }
        return false;
    }

    /**
     * Create a new logger with a given name and ALL log messages level and append it to the logger hierarchy as the new bottom logger.
     *
     * @param loggerName a name of the new logger (the last part of the logger hierarchy)
     * @return the new logger
     */
    public static Logger appendLogger(String loggerName) {
        // sanitize the logger name to prevent a hierarchical expansion
        loggerName = loggerName.replace('.', '-');
        // create a new logger
        final Logger newLogger;
        if (logger.getName().isEmpty()) {
            // the new logger is just below the top logger
            newLogger = Logger.getLogger(loggerName);
            // messages wont be propagated from the new logger to the top logger
            newLogger.setUseParentHandlers(false);
        } else {
            // the new logger is below another logger managed by the factory
            newLogger = Logger.getLogger(logger.getName() + "." + loggerName);
        }
        // the logger will accept messages of all log levels
        newLogger.setLevel(Level.ALL);
        // it will be the new bottom logger
        logger = newLogger;
        return newLogger;
    }

    /**
     * Get the getLogger.
     *
     * @return the getLogger
     */
    @Contract(pure = true)
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Add a given log handler to the current bottom logger and set a given log messages level to the handler.
     *
     * @param handler the handler to add
     * @param level   the log message level to set to the handler
     * @return the added handler
     */
    @Contract("_, _ -> param1")
    public static Handler addHandler(Handler handler, Level level) {
        if (level != null) {
            handler.setLevel(level);
        }
        try {
            handler.setEncoding(HANDLER_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.WARNING, "Cannot set UTF-8 encoding on the handler.", e);
        }
        logger.addHandler(handler);
        return handler;
    }

    /**
     * Create a new ANSI console handler with the default log messages level and add it to the bottom logger.
     *
     * @return the new handler
     */
    public static Handler addAnsiConsoleHandler() {
        return addAnsiConsoleHandler(DEFAULT_CONSOLE_LOG_LEVEL);
    }

    /**
     * Create a new ANSI console handler with a given log messages level and add it to the bottom logger.
     *
     * @param level the level of log messages for the handler
     * @return the new handler
     */
    public static Handler addAnsiConsoleHandler(Level level) {
        final Handler handler = new AnsiConsoleHandler();
        handler.setFormatter(new ConsoleFormatter());
        return addHandler(handler, level);
    }

    /**
     * Create a new file handler with the default log messages level and add it to the bottom logger.
     *
     * @param fileNamePattern the name pattern of the output file
     * @return the new handler or <code>null</code> if the handler could not be added
     */
    public static Handler addFileHandler(String fileNamePattern) {
        return addFileHandler(fileNamePattern, DEFAULT_FILE_LOG_LEVEL);
    }

    /**
     * Create a new file handler with a given log messages level and add it to the bottom logger.
     *
     * @param fileNamePattern the name pattern of the output file
     * @param level           the level of log messages for the handler
     * @return the new handler
     */
    @Nullable
    public static Handler addFileHandler(String fileNamePattern, Level level) {
        try {
            final Handler handler = new FileHandler(fileNamePattern);
            return addHandler(handler, level);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot create and add the file handler to the bottom logger.", e);
        }
        return null;
    }

    /**
     * Translate a given numerical verbosity level into the log level.
     *
     * @param verbosity the verbosity level number
     * @return the corresponding log level
     */
    @Contract(pure = true)
    public static Level loggingLevelForVerbosity(int verbosity) {
        switch (verbosity) {
            case -3:
                return Level.SEVERE;
            case -2:
                return Level.WARNING;
            case -1:
                return Level.INFO;
            case 0:
                return Level.CONFIG;
            case 1:
                return Level.FINE;
            case 2:
                return Level.FINER;
            case 3:
                return Level.FINEST;
            default:
                return verbosity < -3
                        // levelNumber < -3
                        ? Level.OFF
                        // levelNumber > 3
                        : Level.ALL;
        }
    }

    /**
     * Get the temp log file pattern for a given base name.
     *
     * @param baseName the base name
     * @return the resulting temp log file pattern
     */
    @NotNull
    @Contract(pure = true)
    public static String tempLogFilePatternForName(String baseName) {
        return TEMP_LOG_FILE_PATTERN.replace("{}",
                baseName + "." + TEMP_LOG_FILE_DATE_FORMAT.format(new Date()));
    }

    /**
     * Recursively dump object's attributes to a string.
     *
     * @param object the object to dump
     * @return the resulting string dump
     */
    public static String dumpObjectToString(Object object) {
        return new ReflectionToStringBuilder(object, new RecursiveToStringStyle()).toString();
    }

    public static void demoLog(@NotNull Logger logger) {
        logger.log(Level.SEVERE, "Severe message.");
        logger.log(Level.WARNING, "Warning message.");
        logger.log(Level.INFO, "Info message.");
        logger.log(Level.CONFIG, "Config message.");
        logger.log(Level.FINE, "Fine message.");
        logger.log(Level.FINER, "Finer message.");
        logger.log(Level.FINEST, "Finest message.");
    }

    public static void main(String[] args) throws IOException {
        // test factory
        init(LoggerFactory.class.getCanonicalName());
        addAnsiConsoleHandler(LoggerFactory.loggingLevelForVerbosity(0));
        addFileHandler(tempLogFilePatternForName(LoggerFactory.class.getCanonicalName()));
        demoLog(getLogger());
    }

    public static class ConsoleFormatter extends Formatter {
        /**
         * Format the given log record and return the formatted string.
         * <p>
         * The resulting formatted String will normally include a
         * localized and formatted version of the LogRecord's message field.
         * It is recommended to use the {@link Formatter#formatMessage}
         * convenience method to localize and format the message field.
         *
         * @param record the log record to be formatted.
         * @return the formatted log record
         */
        @Override
        public String format(LogRecord record) {
            return record.getLevel() + ":\t" + formatMessage(record) + "\n";
        }
    }

}
