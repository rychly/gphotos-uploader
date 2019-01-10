package io.gitlab.rychly.gphotos_uploader;

import com.google.common.collect.ImmutableList;
import com.google.photos.library.sample.factories.PhotosLibraryClientFactory;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.Album;
import com.google.photos.library.v1.proto.MediaItem;
import io.gitlab.rychly.gphotos_uploader.config.Config;
import io.gitlab.rychly.gphotos_uploader.gphotos.GPhotos;
import io.gitlab.rychly.gphotos_uploader.gphotos.MediaFile;
import io.gitlab.rychly.gphotos_uploader.i18n.Messages;
import io.gitlab.rychly.gphotos_uploader.i18n.ResourceBundleFactory;
import io.gitlab.rychly.gphotos_uploader.logger.LoggerFactory;
import io.gitlab.rychly.gphotos_uploader.tuples.Tuple3;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;

@CommandLine.Command(name = "GPhotosUploader",
        versionProvider = GPhotosUploader.GradlePropertiesVersionProvider.class,
        description = "Uploads missing media files into Google Photos.",
        mixinStandardHelpOptions = true, // add --help and --version options
        showDefaultValues = true // show default values of all non-null options and positional parameters
)
public class GPhotosUploader implements Runnable {
    private static final String CONFIG_KEY_CREDENTIALS_FILE = "google.api.credentials.client-secret.file";
    private static final String CONFIG_KEY_CREDENTIALS_DIRECTORY = "google.api.credentials.directory";
    // for names of the logger levels, see https://docs.oracle.com/javase/8/docs/api/java/util/logging/Level.html#field.summary
    private static final String CONFIG_KEY_LOGGER_CONSOLE_LEVEL_NAME = "logger.console.levelName";
    private static final String CONFIG_FILE = GPhotosUploader.class.getSimpleName() + ".properties";
    private static final String CREDENTIALS_FILE = "client_secret.json";
    private static final String CREDENTIALS_DIRECTORY = "credentials";
    // for the scopes, see https://developers.google.com/photos/library/guides/authentication-authorization#OAuth2Authorizing
    private static final List<String> REQUIRED_SCOPES = ImmutableList.of(
            // List items from the library and all albums, access all media items and list albums owned by the user, including those which have been shared with them.
            "https://www.googleapis.com/auth/photoslibrary.readonly",
            // Access to upload bytes, create media items, create albums, and add enrichment. Only allows new media to be created in the user's library and in albums created by the app.
            "https://www.googleapis.com/auth/photoslibrary.appendonly"
    );
    private Config config;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Verbose mode; -v, -vv, or -vvv for FINE, FINER, or FINEST logging level.")
    private boolean[] verbose = new boolean[0];

    @CommandLine.Option(names = {"-q", "--quiet", "--silent"}, description = "Quiet/silent mode; -q, -qq, or -qqq for INFO, WARNING, SEVERE logging level.")
    private boolean[] quiet = new boolean[0];

    @CommandLine.Option(names = {"-c", "--config"}, description = "Name of or path to the configuration file.")
    private String configFile = CONFIG_FILE;

    @CommandLine.Parameters(arity = "0..*", paramLabel = "media-directory", description = "Directory(ies) of media files to process.")
    private File[] inputDirectories;

    private GPhotosUploader(Config config) throws IOException {
        super();
        this.config = config;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        CommandLine.run(new GPhotosUploader(new Config(GPhotosUploader.class.getSimpleName())), args);
    }

    @Override
    public void run() {
        AnsiConsole.systemInstall();
        LoggerFactory.init(GPhotosUploader.class.getCanonicalName());
        final Handler ansiConsoleHandler = LoggerFactory.addAnsiConsoleHandler(LoggerFactory.loggingLevelForVerbosity(verbose.length - quiet.length));
        LoggerFactory.addFileHandler(LoggerFactory.tempLogFilePatternForName(GPhotosUploader.class.getCanonicalName()));
        try {
            final Properties configProperties = this.config.loadPropertiesFromConfigFileOrEmpty(configFile);
            // logging verbosity from the config file if not set by args
            if ((verbose.length == 0) && (quiet.length == 0)) {
                final String consoleLevelName = configProperties.getProperty(CONFIG_KEY_LOGGER_CONSOLE_LEVEL_NAME);
                if ((consoleLevelName != null) && !consoleLevelName.isEmpty()) {
                    ansiConsoleHandler.setLevel(Level.parse(consoleLevelName));
                }
            }
            // login credentials filename
            final String credentialsFile = this.config.getConfigFile(
                    configProperties.getProperty(CONFIG_KEY_CREDENTIALS_FILE, CREDENTIALS_FILE)).getAbsolutePath();
            final String credentialsDirectory = this.config.getConfigFile(
                    configProperties.getProperty(CONFIG_KEY_CREDENTIALS_DIRECTORY, CREDENTIALS_DIRECTORY)
                            + File.separator).getAbsolutePath();
            // login/connect into Google Photos
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.CONNECTING_TO_GPHOTOS));
            final PhotosLibraryClient photosLibraryClient = PhotosLibraryClientFactory.createClient(
                    credentialsFile, REQUIRED_SCOPES, new File(credentialsDirectory));
            // process directories
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.SCANNING_DIRECTORIES));
            if (inputDirectories != null) {
                for (File inputDirectory : inputDirectories) {
                    LoggerFactory.getLogger().info(
                            ResourceBundleFactory.msg(Messages.PROCESSING_DIRECTORY_1, inputDirectory.getAbsolutePath()));
                    processMediaDirectory(photosLibraryClient, inputDirectory, inputDirectory.getName());
                }
            } else {
                LoggerFactory.getLogger().warning(
                        ResourceBundleFactory.msg(Messages.NOTHING_TO_PROCESS));
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private void processMediaDirectory(PhotosLibraryClient photosLibraryClient, File directory, String albumTitle) throws
            IOException, NoSuchAlgorithmException {
        // album
        LoggerFactory.getLogger().info(
                ResourceBundleFactory.msg(Messages.OPENING_ALBUM_1, albumTitle));
        final Album album = GPhotos.getOrCreateAlbum(photosLibraryClient, albumTitle);
        LoggerFactory.getLogger().fine(
                ResourceBundleFactory.msg(Messages.ALBUM_URL_1, album.getProductUrl()));
        // files
        final Tuple3<Collection<MediaItem>, Collection<MediaItem>, Collection<MediaFile>> tuple3 =
                GPhotos.classifyMediaItemsByFilesAndGetMissingFiles(photosLibraryClient, album, MediaFile.fileFinder(directory));
        final Collection<MediaItem> matchingMediaItems = tuple3.i1();
        final Collection<MediaItem> nonMatchingMediaItems = tuple3.i2();
        final Collection<MediaFile> mediaFilesOfMissingMediaItems = tuple3.i3();
        // matching media item
        LoggerFactory.getLogger().info(
                ResourceBundleFactory.msg(Messages.MATCHING_MEDIA_ITEMS_1, matchingMediaItems.size()));
        for (MediaItem mediaItem : matchingMediaItems) {
            final MediaFile mediaFile = new MediaFile(directory, mediaItem.getFilename());
            final String mediaItemDescription = mediaItem.getDescription();
            LoggerFactory.getLogger().finer(
                    ResourceBundleFactory.msg(Messages.MEDIA_ITEM_FILE_4,
                            mediaFile.getAbsolutePath(), mediaFile.generateDescription(),
                            mediaItem.getProductUrl(), mediaItemDescription));
            if (!mediaFile.isChecksumStringMatching(MediaFile.extractChecksumStringFromDescription(mediaItemDescription))) {
                LoggerFactory.getLogger().warning(
                        ResourceBundleFactory.msg(Messages.MATCHING_MEDIA_ITEM_ACTION_2,
                                mediaFile.getAbsolutePath(), mediaItem.getProductUrl()));
            }
        }
        // non-matching media items
        LoggerFactory.getLogger().info(
                ResourceBundleFactory.msg(Messages.NON_MATCHING_MEDIA_ITEMS_1, nonMatchingMediaItems.size()));
        for (MediaItem mediaItem : nonMatchingMediaItems) {
            final MediaFile mediaFile = new MediaFile(directory, mediaItem.getFilename());
            final String mediaItemDescription = mediaItem.getDescription();
            LoggerFactory.getLogger().finer(
                    ResourceBundleFactory.msg(Messages.MEDIA_ITEM_FILE_4,
                            mediaFile.getAbsolutePath(), Messages.MISSING,
                            mediaItem.getProductUrl(), mediaItemDescription));
            LoggerFactory.getLogger().warning(
                    ResourceBundleFactory.msg(Messages.NON_MATCHING_MEDIA_ITEM_ACTION_2,
                            mediaFile.getAbsolutePath(), mediaItem.getProductUrl()));
        }
        // missing media items
        LoggerFactory.getLogger().info(
                ResourceBundleFactory.msg(Messages.MISSING_MEDIA_ITEMS_1, mediaFilesOfMissingMediaItems.size()));
        for (MediaFile mediaFile : mediaFilesOfMissingMediaItems) {
            LoggerFactory.getLogger().finer(
                    ResourceBundleFactory.msg(Messages.MEDIA_ITEM_FILE_4,
                            mediaFile.getAbsolutePath(), mediaFile.generateDescription(),
                            Messages.MISSING, Messages.MISSING));
            LoggerFactory.getLogger().warning(
                    ResourceBundleFactory.msg(Messages.MISSING_MEDIA_ITEM_ACTION_1,
                            mediaFile.getAbsolutePath()));
        }
        LoggerFactory.getLogger().info(
                ResourceBundleFactory.msg(Messages.UPLOADING_MEDIA_ITEMS));
        GPhotos.createMediaItems(photosLibraryClient, album, mediaFilesOfMissingMediaItems)
                .forEach(mediaItem -> ResourceBundleFactory.msg(Messages.UPLOADED_MEDIA_ITEM_2,
                        mediaItem.getFilename(), mediaItem.getProductUrl()));
    }

    /**
     * {@link CommandLine.IVersionProvider} implementation that returns version information from {@code gradle.properties} file in the classpath.
     */
    static class GradlePropertiesVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            final String implementationTitle = GPhotosUploader.class.getPackage().getImplementationTitle();
            final String implementationVersion = GPhotosUploader.class.getPackage().getImplementationVersion();
            return (implementationTitle != null) && (implementationVersion != null)
                    ? new String[]{implementationTitle + " version " + implementationVersion}
                    : new String[]{GPhotosUploader.class.getName() + " version UNKNOWN (not in a jar file)"
            };
        }
    }
}
