package io.gitlab.rychly.gphotos_uploader.config;

import io.gitlab.rychly.gphotos_uploader.i18n.Messages;
import io.gitlab.rychly.gphotos_uploader.i18n.ResourceBundleFactory;
import io.gitlab.rychly.gphotos_uploader.logger.LoggerFactory;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

public class Config {
    /**
     * Base directory relative to which user-specific configuration files should be written.
     */
    private static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";

    /**
     * Set of preference ordered base directories relative to which configuration files should be searched.
     * The directories are separated by by {@link File#pathSeparator}
     */
    private static final String XDG_CONFIG_DIRS = "XDG_CONFIG_DIRS";

    private static Map<String, String> environment = System.getenv();

    private String applicationDirectoryName;

    /**
     * Create configuration file loader/saver for configuration files in a given application directory.
     *
     * @param applicationDirectoryName the application directory with the configuration files
     */
    public Config(String applicationDirectoryName) {
        this.applicationDirectoryName = applicationDirectoryName;
    }

    private static String getEnvironmentVariableValueOrDefault(String variableName, String defaultValue) {
        final String variableValue = environment.get(variableName);
        return (variableValue == null || variableValue.trim().length() == 0) ? defaultValue : variableValue;
    }

    /**
     * Load properties from a given <code>InputStream</code> object or, in the case of error, get an empty properties.
     *
     * @param inputStream the <code>InputStream</code> object to load properties from
     * @return the <code>Properties</code> object which was loaded or which is empty
     */
    public static Properties loadPropertiesFromInputStreamOrGetEmpty(InputStream inputStream) {
        final Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (Exception e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.CANNOT_LOAD_PROPERTIES_1, e.getMessage()),
                    e);
        }
        return properties;
    }

    /**
     * Load properties from a config file given by its filename in class-path or, in the case of error, get an empty properties.
     *
     * @param fileName the config file name
     * @return the <code>Properties</code> object which was loaded or which is empty
     */
    public static Properties loadPropertiesFromClassPathOrEmpty(String fileName) {
        try (final InputStream inputStream = Config.class.getClassLoader().getResourceAsStream(fileName)) {
            return loadPropertiesFromInputStreamOrGetEmpty(inputStream);
        } catch (Exception e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.CANNOT_LOAD_PROPERTIES_1, e.getMessage()),
                    e);
            return new Properties();
        }
    }

    @NotNull
    private String[] getConfigDirs() {
        return getConfigDirs(".");
    }

    @NotNull
    private String[] getConfigDirs(String firstConfigDir) {
        final String defaultCfgHome = environment.get("HOME") + File.separator + ".config";
        final String defaultCfgSystem = File.separator + "etc" + File.separator + "xdg";
        final String allConfigDirs = firstConfigDir
                + File.pathSeparator
                + getEnvironmentVariableValueOrDefault(XDG_CONFIG_HOME, defaultCfgHome) // home cfg dir
                + File.separator + applicationDirectoryName
                + File.pathSeparator
                + getEnvironmentVariableValueOrDefault(XDG_CONFIG_DIRS, defaultCfgSystem) // system cfg dirs
                + File.separator + applicationDirectoryName;
        return allConfigDirs.split(File.pathSeparator);
    }

    /**
     * Get an existing config file of a particular name (or create a new one if the file is missing).
     * The new file, if needed, will be create in a home configuration directory, not in the working directory.
     *
     * @param fileName the config file name (absolute, relative, or base/without path)
     * @return the File object
     * @throws IOException cannot create the file
     */
    public File getConfigFile(String fileName) throws IOException {
        return getConfigFile(fileName, false);
    }

    /**
     * Get an existing config file of a particular name (or create a new one if the file is missing).
     * Create a configuration directory (not a file) if the file name ends with directory separator (e.g. "/").
     *
     * @param fileName                             the config file name (absolute, relative, or base/without path)
     * @param createInWorkingAndNotInHomeConfigDir whether create the new config file in a working directory
     * @return the File object
     * @throws IOException cannot create the file
     */
    public File getConfigFile(String fileName, boolean createInWorkingAndNotInHomeConfigDir) throws IOException {
        // return this config file for absolute paths
        final File file = new File(fileName);
        if (file.isAbsolute()) {
            return file;
        } else {
            // search config directories otherwise
            final String[] configDirs = getConfigDirs();
            // return existing and readable config file
            for (String path : configDirs) {
                final File configFile = new File(path, fileName);
                if (configFile.canRead()) {
                    return configFile;
                }
            }
            // return new config file otherwise, for defaultCfgDirIndex see @link Config#getConfigDirs()
            final int defaultCfgDirIndex = createInWorkingAndNotInHomeConfigDir ? 0 : 1;
            final File configFile = new File(configDirs[defaultCfgDirIndex], fileName);
            final File configFileParentDirectory = configFile.getParentFile();
            if (!configFileParentDirectory.exists()) {
                Files.createDirectory(configFileParentDirectory.toPath());
            }
            if (fileName.endsWith(File.separator)) {
                Files.createDirectory(configFile.toPath());
            } else {
                configFile.createNewFile();
            }
            return configFile;
        }
    }

    /**
     * Load properties from a config file given by its basename or, in the case of error, get an empty properties.
     * The new file, if needed, will be create in a home configuration directory, not in the working directory.
     *
     * @param fileName the config file name (absolute, relative, or base/without path)
     * @return the <code>Properties</code> object which was loaded or which is empty
     */
    public Properties loadPropertiesFromConfigFileOrEmpty(String fileName) {
        return loadPropertiesFromConfigFileOrEmpty(fileName, false);
    }

    /**
     * Load properties from a config file given by its basename or, in the case of error, get an empty properties.
     *
     * @param fileName                             the config file name (absolute, relative, or base/without path)
     * @param createInWorkingAndNotInHomeConfigDir whether create the new config file in a working directory
     * @return the <code>Properties</code> object which was loaded or which is empty
     */
    public Properties loadPropertiesFromConfigFileOrEmpty(String fileName, boolean createInWorkingAndNotInHomeConfigDir) {
        try (final InputStream inputStream = new FileInputStream(getConfigFile(fileName, createInWorkingAndNotInHomeConfigDir))) {
            return loadPropertiesFromInputStreamOrGetEmpty(inputStream);
        } catch (Exception e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.CANNOT_LOAD_PROPERTIES_1, e.getMessage()),
                    e);
            return new Properties();
        }
    }

    /**
     * Store properties into a config file given by its basename.
     * The new file, if needed, will be create in a home configuration directory, not in the working directory.
     * Moreover, an existing file will be overwritten (the original properties in the file will be lost).
     *
     * @param fileName   the config file name (absolute, relative, or base/without path)
     * @param properties the properties to store
     * @throws IOException cannot create or write the file
     */
    public void storePropertiesIntoConfigFile(String fileName, @NotNull Properties properties) throws IOException {
        storePropertiesIntoConfigFile(fileName, properties, false, false);
    }

    /**
     * Store properties into a config file given by its basename.
     *
     * @param fileName                             the config file name (absolute, relative, or base/without path)
     * @param properties                           the properties to store
     * @param appendNotOverwrite                   whether append to the config file (not checking if the properties are unique)
     * @param createInWorkingAndNotInHomeConfigDir whether create the new config file in a working directory
     * @throws IOException cannot create or write the file
     */
    public void storePropertiesIntoConfigFile(
            String fileName, @NotNull Properties properties, boolean appendNotOverwrite,
            boolean createInWorkingAndNotInHomeConfigDir) throws IOException {
        try (final OutputStream outputStream = new FileOutputStream(
                getConfigFile(fileName, createInWorkingAndNotInHomeConfigDir), appendNotOverwrite)) {
            properties.store(outputStream, null);
        }
    }

}
