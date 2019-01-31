package io.gitlab.rychly.gphotos_uploader.i18n;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public final class Messages {
    public static final String CONNECTING_TO_GPHOTOS = "ConnectingToGPhotos";
    public static final String LISTING_ALBUMS_1 = "ListingAlbums(%s)";
    public static final String LISTING_SHARED_ALBUMS_1 = "ListingSharedAlbums(%s)";
    public static final String LIST_ALBUM_2 = "ListAlbum(%s,%s)";
    public static final String LIST_SHARED_ALBUM_2 = "ListSharedAlbum(%s,%s)";
    public static final String SHARING_ALBUMS_1 = "SharingAlbums(%s)";
    public static final String SKIPPING_SHARE_2 = "SkippingShare(%s,%s,%s)";
    public static final String SHARED_ALBUM_2 = "SharedAlbum(%s,%s)";
    public static final String UNSHARING_ALBUMS_1 = "UnsharingAlbums(%s)";
    public static final String SKIPPING_UNSHARE_2 = "SkippingUnshare(%s,%s,%s)";
    public static final String UNSHARED_ALBUM_2 = "UnsharedAlbum(%s,%s)";
    public static final String EXPORTING_TOKENS_2 = "ExportingTokens(%s,%s)";
    public static final String EXPORTING_TOKEN_2 = "ExportingToken(%s,%s)";
    public static final String EXPORT_ERROR_2 = "ExportError(%s,%s)";
    public static final String IMPORTING_TOKENS_1 = "ImportingTokens(%s)";
    public static final String SKIPPING_IMPORT_3 = "SkippingImport(%s,%s,%s)";
    public static final String IMPORTED_TOKEN_2 = "ImportedToken(%s,%s)";
    public static final String IMPORT_ERROR_2 = "ImportError(%s,%s)";
    public static final String LEAVING_TOKENS_1 = "LeavingTokens(%s)";
    public static final String SKIPPING_LEAVE_3 = "SkippingLeave(%s,%s,%s)";
    public static final String LEFT_TOKEN_1 = "LeftToken(%s)";
    public static final String LEAVE_ERROR_2 = "LeaveError(%s,%s)";
    public static final String SCANNING_DIRECTORIES = "ScanningDirectories";
    public static final String PROCESSING_DIRECTORY_1 = "ProcessingDirectory(%s)";
    public static final String PROCESSING_DIRECTORY_ERROR_2 = "ProcessingDirectoryError(%s,%s)";
    public static final String OPENING_ALBUM_1 = "OpeningAlbum(%s)";
    public static final String ALBUM_URL_1 = "AlbumUrl(%s)";
    public static final String MATCHING_MEDIA_ITEMS_1 = "MatchingMediaItems(%d)";
    public static final String MATCHING_MEDIA_ITEM_ACTION_2 = "MatchingMediaItemAction(%s,%s)";
    public static final String NON_MATCHING_MEDIA_ITEMS_1 = "NonMatchingMediaItems(%d)";
    public static final String NON_MATCHING_MEDIA_ITEM_ACTION_2 = "NonMatchingMediaItemAction(%s,%s)";
    public static final String MISSING_MEDIA_ITEMS_1 = "MissingMediaItems(%d)";
    public static final String MISSING_MEDIA_ITEM_ACTION_1 = "MissingMediaItemAction(%s)";
    public static final String UPLOADING_MEDIA_ITEMS = "UploadingMediaItems";
    public static final String UPLOADED_MEDIA_ITEM_2 = "UploadedMediaItem(%s,%s)";
    public static final String MEDIA_ITEM_FILE_4 = "MediaItemFile(%s,%s,%s,%s)";
    public static final String UPLOADING_FILE_1 = "UploadingFile(%s)";
    public static final String SKIPPING_FILE_UPLOAD_2 = "SkippingFileUpload(%s,%s)";
    public static final String CREATING_MEDIA_ITEMS_1 = "CreatingMediaItems(%d)";
    public static final String SKIPPING_MEDIA_ITEM_CREATION_2 = "SkippingMediaItemCreation(%s,%s)";
    public static final String CANNOT_LOAD_PROPERTIES_1 = "CannotLoadProperties(%s)";
    public static final String CANNOT_UPLOAD_FILE_2 = "CannotUploadFile(%s,%s)";
    public static final String UNKNOWN_ERROR_1 = "UnknownError(%s)";
    public static final String MISSING = "Missing";

    public static Stream<String> getMessageKeysStream() {
        return Arrays.stream(Messages.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()) && field.getType().isAssignableFrom(String.class))
                .flatMap(field -> {
                    try {
                        return Stream.of((String) field.get(null));
                    } catch (IllegalAccessException e) {
                        return Stream.empty();
                    }
                });
    }

    public static void createMessagesResourceFile(String fileName) throws IOException {
        Files.write(Paths.get(fileName), (Iterable<String>) getMessageKeysStream().map(key -> key + "=")::iterator);
    }

    public static void main(String[] args) throws IOException {
        createMessagesResourceFile(ResourceBundleFactory.MESSAGES_RESOURCE_BUNDLE_NAME
                + "_" + ResourceBundleFactory.getMessagesLocale().toString()
                + "." + ResourceBundleFactory.RESOURCE_SUFFIX);
    }

}
