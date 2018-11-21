package io.gitlab.rychly.gphotos_uploader.i18n;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public final class Messages {
    public static final String CONNECTING_TO_GPHOTOS = "ConnectingToGPhotos";
    public static final String SCANNING_DIRECTORIES = "ScanningDirectories";
    public static final String NOTHING_TO_PROCESS = "NothingToProcess";
    public static final String PROCESSING_DIRECTORY_1 = "ProcessingDirectory(%s)";
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