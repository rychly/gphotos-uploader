package io.gitlab.rychly.gphotos_uploader;

import com.google.api.gax.rpc.ApiException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.photos.library.sample.factories.PhotosLibraryClientFactory;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.Album;
import com.google.photos.library.v1.proto.MediaItem;
import com.google.photos.library.v1.proto.SharedAlbumOptions;
import io.gitlab.rychly.gphotos_uploader.config.Config;
import io.gitlab.rychly.gphotos_uploader.gphotos.GPhotos;
import io.gitlab.rychly.gphotos_uploader.gphotos.MediaFile;
import io.gitlab.rychly.gphotos_uploader.i18n.Messages;
import io.gitlab.rychly.gphotos_uploader.i18n.ResourceBundleFactory;
import io.gitlab.rychly.gphotos_uploader.logger.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.fusesource.jansi.AnsiConsole;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.stream.Stream;

@CommandLine.Command(name = "GPhotosUploader",
        versionProvider = GPhotosUploader.GradlePropertiesVersionProvider.class,
        description = "Uploads missing media files into Google Photos and control their sharing.",
        mixinStandardHelpOptions = true, // add --help and --version options
        showDefaultValues = true, // show default values of all non-null options and positional parameters
        sortOptions = false // display options in the order they are declared in your class
)
public class GPhotosUploader implements Runnable {
    private static final String CONFIG_KEY_CREDENTIALS_FILE = "google.api.credentials.client-secret.file";
    private static final String CONFIG_KEY_CREDENTIALS_DIRECTORY = "google.api.credentials.directory";
    /**
     * For names of the logger levels, see https://docs.oracle.com/javase/8/docs/api/java/util/logging/Level.html#field.summary
     */
    private static final String CONFIG_KEY_LOGGER_CONSOLE_LEVEL_NAME = "logger.console.levelName";
    private static final String CONFIG_FILE = GPhotosUploader.class.getSimpleName() + ".properties";
    private static final String CREDENTIALS_FILE = "client_secret.json";
    private static final String CREDENTIALS_DIRECTORY = "credentials";
    /**
     * For the scopes, see https://developers.google.com/photos/library/guides/authentication-authorization#OAuth2Authorizing
     * On modification, it is necessary to reinitialize the stored credentials by removing file StoredCredential in CONFIG_KEY_CREDENTIALS_DIRECTORY.
     */
    private static final List<String> REQUIRED_SCOPES = ImmutableList.of(
            // List items from the library and all albums, access all media items and list albums owned by the user, including those which have been shared with them.
            "https://www.googleapis.com/auth/photoslibrary.readonly",
            // Access to upload bytes, create media items, create albums, and add enrichment. Only allows new media to be created in the user's library and in albums created by the app.
            "https://www.googleapis.com/auth/photoslibrary.appendonly",
            // Access to create an album, share it, upload media items to it, and join a shared album.
            "https://www.googleapis.com/auth/photoslibrary.sharing"
    );
    private Config config;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Verbose mode; -v, -vv, or -vvv for FINE, FINER, or FINEST logging level.")
    private boolean[] verbose = new boolean[0];

    @CommandLine.Option(names = {"-q", "--quiet", "--silent"}, description = "Quiet/silent mode; -q, -qq, or -qqq for INFO, WARNING, SEVERE logging level.")
    private boolean[] quiet = new boolean[0];

    @CommandLine.Option(names = {"-c", "--config"}, description = "Name of or path to the configuration file.")
    private String configFile = CONFIG_FILE;

    @CommandLine.Option(names = {"-g", "--credentials-profile"}, arity = "0..*", description = "Profile name for Google API credentials configuration (the default value is empty). " +
            "In the case of multiple profile names (multiple parameter values), the actions specified by other parameters will be executed for all of them.")
    private String credentialsProfiles[];

    @CommandLine.Option(names = {"-l", "--list-albums"}, arity = "0..1", description = "List online albums matching particular regular expression in the alphabetical order (the empty expression matches all).")
    private String listAlbums;

    @CommandLine.Option(names = {"-p", "---list-shared-albums"}, arity = "0..1", description = "List shared/public online albums matching particular regular expression in the alphabetical order (the empty expression matches all).")
    private String listSharedAlbums;

    @CommandLine.Option(names = {"-u", "--unshare-albums"}, arity = "0..1", description = "Unshare shared online albums matching particular regular expression in the alphabetical order (the empty expression matches all).")
    private String unshareAlbums;

    @CommandLine.Option(names = {"-s", "--share-albums"}, arity = "0..1", description = "Share (by URL) online albums matching particular regular expression in the alphabetical order (the empty expression matches all).")
    private String shareAlbums;

    @CommandLine.Option(names = {"-o", "--collaborative-sharing"}, description = "When sharing albums, enable the collaborative sharing (i.e., other user can contribute their media files into the album).")
    private boolean collaborativeSharing = false;

    @CommandLine.Option(names = {"-m", "--commentable-sharing"}, description = "When sharing albums, enable the commentable sharing (i.e., other user can create comments on media files in the album).")
    private boolean commentableSharing = false;

    @CommandLine.Option(names = {"-f", "--import-export-file"}, arity = "0..1", description = "File to import from or export into for the share tokens import/export.")
    private File importExportFile = new File("gphotos-share-tokens.txt");

    @CommandLine.Option(names = {"-e", "--export-share-tokens"}, arity = "0..1", description = "Export share tokens of shared online albums matching particular regular expression into the alphabetical order in a file (the empty expression matches all; also see the import-export file option).")
    private String exportSharedAlbums;

    @CommandLine.Option(names = {"-i", "--import-share-tokens"}, arity = "0..2", description = "Import share tokens from a file and join their shared online albums (see the import-export file option). " +
            "All the tokens will be imported (no parameter values), or the first N tokens will be imported (one parameter value), or tokens from the N-th to M-th token will be imported (two parameter values). " +
            "The first token has number 1 (not zero).")
    private int importShareTokens[];

    @CommandLine.Option(names = {"-d", "--leave-share-tokens"}, description = "Use share tokens from a file to leave their shared online albums (see the import-export file option).")
    private boolean leaveShareTokens;

    @CommandLine.Parameters(arity = "0..*", paramLabel = "media-directory", description = "Directory(ies) of media files to process (recursively; the album name will be a plain directory name, without its parent path).")
    private File[] inputDirectories;

    private GPhotosUploader(Config config) {
        super();
        this.config = config;
    }

    public static void main(String[] args) {
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
            if (credentialsProfiles == null) {
                runForCredentialsProfile(configProperties, null);
            } else {
                for (String credentialsProfile : credentialsProfiles) {
                    runForCredentialsProfile(configProperties, credentialsProfile);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.UNKNOWN_ERROR_1, e.getMessage()),
                    e);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private void runForCredentialsProfile(Properties configProperties, String credentialsProfile) throws IOException, GeneralSecurityException {
        // login credentials filename
        final String credentialsFile = this.config.getConfigFile(
                configProperties.getProperty(CONFIG_KEY_CREDENTIALS_FILE + (credentialsProfile != null ? ".profile-" + credentialsProfile : ""), CREDENTIALS_FILE)).getAbsolutePath();
        final String credentialsDirectory = this.config.getConfigFile(
                configProperties.getProperty(CONFIG_KEY_CREDENTIALS_DIRECTORY + (credentialsProfile != null ? ".profile-" + credentialsProfile : ""), CREDENTIALS_DIRECTORY)
                        + File.separator).getAbsolutePath();
        // login/connect into Google Photos
        LoggerFactory.getLogger().fine(
                ResourceBundleFactory.msg(Messages.CONNECTING_TO_GPHOTOS_1, Strings.nullToEmpty(credentialsProfile)));
        final PhotosLibraryClient photosLibraryClient = PhotosLibraryClientFactory.createClient(
                credentialsFile, REQUIRED_SCOPES, new File(credentialsDirectory));
        // actions
        boolean actionPerformed = false;
        if (listAlbums != null) {
            // list albums by regex
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.LISTING_ALBUMS_1, listAlbums));
            listAlbums(photosLibraryClient, listAlbums);
            actionPerformed = true;
        }
        if (listSharedAlbums != null) {
            // list shared albums by regex
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.LISTING_SHARED_ALBUMS_1, listSharedAlbums));
            listSharedAlbums(photosLibraryClient, listSharedAlbums);
            actionPerformed = true;
        }
        if (unshareAlbums != null) {
            // unshare albums by regex
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.UNSHARING_ALBUMS_1, unshareAlbums));
            unshareAlbums(photosLibraryClient, unshareAlbums);
            actionPerformed = true;
        }
        if (shareAlbums != null) {
            // share albums by regex
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.SHARING_ALBUMS_1, shareAlbums));
            shareAlbums(photosLibraryClient, shareAlbums, collaborativeSharing, commentableSharing);
            actionPerformed = true;
        }
        if (exportSharedAlbums != null) {
            // export share tokens
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.EXPORTING_TOKENS_2, exportSharedAlbums, importExportFile));
            exportShareTokens(photosLibraryClient, importExportFile, exportSharedAlbums);
            actionPerformed = true;
        }
        if (importShareTokens != null) {
            // import share tokens
            final int tokensFromNo = importShareTokens.length >= 2 ? importShareTokens[0] - 1 : 0; // N if (N,M) else 0
            final int tokensToNo = importShareTokens.length >= 2 ? importShareTokens[1] - 1 // M if (N,M) else
                    : importShareTokens.length == 1 ? importShareTokens[0] - 1 : Integer.MAX_VALUE - 1; // N if (N) else +INF
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.IMPORTING_TOKENS_3, tokensFromNo + 1, tokensToNo + 1, importExportFile));
            importShareTokens(photosLibraryClient, importExportFile, tokensFromNo, tokensToNo);
            actionPerformed = true;
        }
        if (leaveShareTokens) {
            // leave share tokens
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.LEAVING_TOKENS_1, importExportFile));
            leaveShareTokens(photosLibraryClient, importExportFile);
            actionPerformed = true;
        }
        if (inputDirectories != null) {
            // process directories
            LoggerFactory.getLogger().fine(
                    ResourceBundleFactory.msg(Messages.SCANNING_DIRECTORIES));
            processMediaDirectories(photosLibraryClient, inputDirectories);
            actionPerformed = true;
        }
        if (!actionPerformed) {
            // print usage help
            CommandLine.usage(this, System.out);
        }
    }

    private void listAlbums(PhotosLibraryClient photosLibraryClient, String regexPattern) {
        GPhotos.getAlbumsStreamByTitle(photosLibraryClient, regexPattern, true)
                .sorted(Comparator.comparing(Album::getTitle))
                .forEach(album -> LoggerFactory.getLogger().info(
                        // BUG: cannot print album.getShareInfo().getShareableUrl() for shared albums, i.e., album.hasShareInfo() == true, as the sharable URL is not available here (it is available in the list of shared albums)
                        ResourceBundleFactory.msg(album.hasShareInfo() ? Messages.LIST_SHARED_ALBUM_2 : Messages.LIST_ALBUM_2,
                                album.getTitle(), album.getProductUrl())));
    }

    private void listSharedAlbums(PhotosLibraryClient photosLibraryClient, String regexPattern) {
        GPhotos.getSharedAlbumsStreamByTitle(photosLibraryClient, regexPattern, true)
                .sorted(Comparator.comparing(Album::getTitle))
                .forEach(album -> LoggerFactory.getLogger().info(
                        ResourceBundleFactory.msg(Messages.LIST_ALBUM_2, album.getTitle(),
                                album.hasShareInfo() ? album.getShareInfo().getShareableUrl() : album.getProductUrl())));
    }

    private void shareAlbums(PhotosLibraryClient photosLibraryClient, String regexPattern, boolean isCollaborative, boolean isCommentable) {
        final SharedAlbumOptions sharedAlbumOptions = SharedAlbumOptions.newBuilder()
                .setIsCollaborative(isCollaborative).setIsCommentable(isCommentable).build();
        GPhotos.getAlbumsStreamByTitle(photosLibraryClient, regexPattern, true)
                .sorted(Comparator.comparing(Album::getTitle))
                .flatMap(album -> {
                    try {
                        return Stream.of(Pair.of(album, photosLibraryClient.shareAlbum(album.getId(), sharedAlbumOptions)));
                    } catch (ApiException e) { // e.g., io.grpc.StatusRuntimeException: PERMISSION_DENIED: Request had insufficient authentication scopes.
                        LoggerFactory.getLogger().log(Level.SEVERE,
                                ResourceBundleFactory.msg(Messages.SKIPPING_SHARE_2,
                                        album.getTitle(), album.getProductUrl(), e.getMessage()),
                                e);
                        return Stream.empty();
                    }
                })
                .forEach(pair -> LoggerFactory.getLogger().info(
                        ResourceBundleFactory.msg(Messages.SHARED_ALBUM_2, pair.getLeft().getTitle(),
                                pair.getRight().getShareInfo().getShareableUrl())));
    }

    private void unshareAlbums(PhotosLibraryClient photosLibraryClient, String regexPattern) {
        GPhotos.getSharedAlbumsStreamByTitle(photosLibraryClient, regexPattern, true)
                .sorted(Comparator.comparing(Album::getTitle))
                .flatMap(album -> {
                    try {
                        return Stream.of(Pair.of(album, photosLibraryClient.unshareAlbum(album.getId())));
                    } catch (ApiException e) { // e.g., io.grpc.StatusRuntimeException: PERMISSION_DENIED: Request had insufficient authentication scopes.
                        LoggerFactory.getLogger().log(Level.SEVERE,
                                ResourceBundleFactory.msg(Messages.SKIPPING_UNSHARE_2,
                                        album.getTitle(), album.getProductUrl(), e.getMessage()),
                                e);
                        return Stream.empty();
                    }
                })
                .forEach(pair -> LoggerFactory.getLogger().info(
                        ResourceBundleFactory.msg(Messages.UNSHARED_ALBUM_2, pair.getLeft().getTitle(),
                                pair.getLeft().getProductUrl())));
    }

    private void exportShareTokens(PhotosLibraryClient photosLibraryClient, File exportedFile, String regexPattern) {
        final Stream<String> exportLinesStream = GPhotos.getSharedAlbumsStreamByTitle(photosLibraryClient, regexPattern, true)
                .sorted(Comparator.comparing(Album::getTitle))
                .map(album -> {
                    LoggerFactory.getLogger().info(
                            ResourceBundleFactory.msg(Messages.EXPORTING_TOKEN_2, album.getTitle(), album.getShareInfo().getShareableUrl()));
                    return album.getShareInfo().getShareToken() + " # " + album.getTitle() + "; " + album.getShareInfo().getShareableUrl();
                });
        try {
            Files.write(exportedFile.toPath(), (Iterable<String>) exportLinesStream::iterator, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.EXPORT_ERROR_2, exportedFile.getAbsolutePath(), e.getMessage()),
                    e);
        }
    }

    private void importShareTokens(PhotosLibraryClient photosLibraryClient, File importedFile, int tokensFromNo, int tokensToNo) {
        final AtomicInteger atomicInteger = new AtomicInteger(1); // indices start with 1 to be more user-friendly
        try {
            Files.lines(importedFile.toPath())
                    .flatMap(line -> {
                        final String token = line.split("#", 2)[0].trim();
                        return token.isEmpty() ? Stream.empty() : Stream.of(token);
                    })
                    .map(token -> Pair.of(atomicInteger.getAndIncrement(), token)) // add an index number to each token
                    .limit(tokensToNo + 1).skip(tokensFromNo) // first limit, then skip, must be in this order
                    .flatMap(indexTokenPair -> {
                        try {
                            return Stream.of(Pair.of(indexTokenPair.getLeft(), photosLibraryClient.joinSharedAlbum(indexTokenPair.getRight()).getAlbum()));
                        } catch (ApiException | NullPointerException e) {
                            LoggerFactory.getLogger().log(Level.SEVERE,
                                    ResourceBundleFactory.msg(Messages.SKIPPING_IMPORT_4,
                                            importedFile.getAbsolutePath(), indexTokenPair.getLeft(), indexTokenPair.getRight(), e.getMessage()),
                                    e);
                            return Stream.empty();
                        }
                    })
                    .forEach(indexAlbumPair -> LoggerFactory.getLogger().info(
                            ResourceBundleFactory.msg(Messages.IMPORTED_TOKEN_3, indexAlbumPair.getLeft(),
                                    indexAlbumPair.getRight().getTitle(), indexAlbumPair.getRight().getProductUrl())));
        } catch (IOException e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.IMPORT_ERROR_2, importedFile.getAbsolutePath(), e.getMessage()),
                    e);
        }
    }

    private void leaveShareTokens(PhotosLibraryClient photosLibraryClient, File importedFile) {
        try {
            Files.lines(importedFile.toPath())
                    .flatMap(line -> {
                        final String token = line.split("#", 2)[0].trim();
                        return token.isEmpty() ? Stream.empty() : Stream.of(token);
                    })
                    .flatMap(token -> {
                        try {
                            return Stream.of(Pair.of(token, photosLibraryClient.leaveSharedAlbum(token)));
                        } catch (ApiException | NullPointerException e) {
                            LoggerFactory.getLogger().log(Level.SEVERE,
                                    ResourceBundleFactory.msg(Messages.SKIPPING_LEAVE_3,
                                            importedFile.getAbsolutePath(), token, e.getMessage()),
                                    e);
                            return Stream.empty();
                        }
                    })
                    .forEach(pair -> LoggerFactory.getLogger().info(
                            ResourceBundleFactory.msg(Messages.LEFT_TOKEN_1, pair.getLeft())));
        } catch (IOException e) {
            LoggerFactory.getLogger().log(Level.SEVERE,
                    ResourceBundleFactory.msg(Messages.LEAVE_ERROR_2, importedFile.getAbsolutePath(), e.getMessage()),
                    e);
        }
    }

    private void processMediaDirectories(PhotosLibraryClient photosLibraryClient, @NotNull File[] directories) {
        for (File directory : directories) {
            // process media files in the directory
            LoggerFactory.getLogger().info(
                    ResourceBundleFactory.msg(Messages.PROCESSING_DIRECTORY_1, directory.getAbsolutePath()));
            try {
                processMediaDirectory(photosLibraryClient, directory, directory.getName());
            } catch (IOException | NoSuchAlgorithmException e) {
                LoggerFactory.getLogger().log(Level.SEVERE,
                        ResourceBundleFactory.msg(Messages.PROCESSING_DIRECTORY_ERROR_2, directory.getAbsolutePath(), e.getMessage()),
                        e);
            }
            // process sub-directories in the directory
            final File[] subDirectories = directory.listFiles(pathname -> pathname.isDirectory() && !pathname.isHidden());
            if (subDirectories != null) {
                processMediaDirectories(photosLibraryClient, subDirectories);
            }
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
        final Triple<Collection<MediaItem>, Collection<MediaItem>, Collection<MediaFile>> triple =
                GPhotos.classifyMediaItemsByFilesAndGetMissingFiles(photosLibraryClient, album, MediaFile.fileFinder(directory));
        final Collection<MediaItem> matchingMediaItems = triple.getLeft();
        final Collection<MediaItem> nonMatchingMediaItems = triple.getMiddle();
        final Collection<MediaFile> mediaFilesOfMissingMediaItems = triple.getRight();
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
                .forEach(mediaItem -> LoggerFactory.getLogger().info(
                        ResourceBundleFactory.msg(Messages.UPLOADED_MEDIA_ITEM_2,
                                mediaItem.getFilename(), mediaItem.getProductUrl())));
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
