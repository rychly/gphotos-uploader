package io.gitlab.rychly.gphotos_uploader.gphotos;

import com.google.common.collect.Lists;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient;
import com.google.photos.library.v1.proto.*;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.rpc.Code;
import io.gitlab.rychly.gphotos_uploader.i18n.Messages;
import io.gitlab.rychly.gphotos_uploader.i18n.ResourceBundleFactory;
import io.gitlab.rychly.gphotos_uploader.logger.LoggerFactory;
import io.gitlab.rychly.gphotos_uploader.tuples.Tuple2;
import io.gitlab.rychly.gphotos_uploader.tuples.Tuple3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A class providing a set of useful Google Photos operations.
 */
public class GPhotos {

    /**
     * Creating new media items in a batch is limited, see https://developers.google.com/photos/library/guides/upload-media#creating-media-item
     */
    public static final int CREATE_MEDIA_ITEMS_BATCH_LIMIT = 50;

    /**
     * List all albums in the user's library to be able to iterate over all the albums in this list (pagination is handled automatically).
     *
     * @param photosLibraryClient the photos library client
     * @return the iterator over the list of all albums
     */
    public static Iterable<Album> getAlbums(@NotNull PhotosLibraryClient photosLibraryClient) {
        final InternalPhotosLibraryClient.ListAlbumsPagedResponse listAlbumsPagedResponse = photosLibraryClient.listAlbums();
        return listAlbumsPagedResponse.iterateAll();

    }

    /**
     * Get an album of a given identifier.
     *
     * @param photosLibraryClient the photos library client
     * @param id                  the album identifier
     * @return the album
     */
    public static Album getAlbum(@NotNull PhotosLibraryClient photosLibraryClient, String id) {
        return photosLibraryClient.getAlbum(id);
    }

    /**
     * Get a stream of all albums of a given title.
     *
     * @param photosLibraryClient the photos library client
     * @param title               the album title to search
     * @return the stream of the albums
     */
    public static Stream<Album> getAlbumsStreamByTitle(@NotNull PhotosLibraryClient photosLibraryClient, String title) {
        return StreamSupport.stream(getAlbums(photosLibraryClient).spliterator(), false)
                .filter(album -> album.getTitle().equals(title));
    }

    /**
     * Get or create an album of a given title.
     *
     * @param photosLibraryClient the photos library client
     * @param title               the album title
     * @return the album
     */
    public static Album getOrCreateAlbum(@NotNull PhotosLibraryClient photosLibraryClient, String title) {
        // find the first album with the given title
        final Optional<Album> album = getAlbumsStreamByTitle(photosLibraryClient, title).findFirst();
        // (only) if absent create a new album with the given title
        return album.orElseGet(() -> photosLibraryClient.createAlbum(title));
    }

    /**
     * List all media items in a given album to be able to iterate over all the items in this list (pagination is handled automatically).
     *
     * @param photosLibraryClient the photos library client
     * @param album               the album
     * @return the iterator over the list of media items
     */
    public static Iterable<MediaItem> getMediaItems(@NotNull PhotosLibraryClient photosLibraryClient, @NotNull Album album) {
        final InternalPhotosLibraryClient.SearchMediaItemsPagedResponse searchMediaItemsPagedResponse =
                photosLibraryClient.searchMediaItems(album.getId());
        return searchMediaItemsPagedResponse.iterateAll();
    }

    /**
     * Get a media item of a given identifier.
     *
     * @param photosLibraryClient the photos library client
     * @param id                  the media item identifier
     * @return the media item
     */
    public static MediaItem getMediaItem(@NotNull PhotosLibraryClient photosLibraryClient, String id) {
        return photosLibraryClient.getMediaItem(id);
    }

    /**
     * Classify media items of a given album to those matching a given files, non-matching the files,
     * and also provide a collection of files missing in the media items.
     *
     * @param photosLibraryClient the photos library client
     * @param files               a stream of the media files
     * @param album               the album
     * @return a triplet of the collections of media items matching the files, non-matching the files, and the collection of files missing in the media items
     */
    @NotNull
    public static Tuple3<Collection<MediaItem>, Collection<MediaItem>, Collection<MediaFile>> classifyMediaItemsByFilesAndGetMissingFiles(
            @NotNull PhotosLibraryClient photosLibraryClient, Album album, @NotNull Stream<MediaFile> files) {
        // create a hash map for fast search of the files by their names
        final Map<String, MediaFile> stringMediaFileMap =
                files.collect(Collectors.toMap(MediaFile::getName, mediaFile -> mediaFile));
        // classify media items to two classes: matching and non-matching the files
        final Map<Boolean, List<MediaItem>> mediaItemsClassifiedByFiles =
                StreamSupport.stream(getMediaItems(photosLibraryClient, album).spliterator(), false)
                        .collect(Collectors.partitioningBy(mediaItem -> stringMediaFileMap.containsKey(mediaItem.getFilename())));
        // remove the file names of the matching media items from the input file-names collection
        for (MediaItem mediaItem : mediaItemsClassifiedByFiles.get(true)) {
            stringMediaFileMap.remove(mediaItem.getFilename());
        }
        return Tuple3.makeTuple(mediaItemsClassifiedByFiles.get(true), mediaItemsClassifiedByFiles.get(false),
                stringMediaFileMap.values());
    }

    /**
     * Create new media items in a given album by uploading given files and return a stream of resulting media items.
     *
     * @param photosLibraryClient the photos library client
     * @param album               the album
     * @param files               the files
     * @return the stream of the successfully created media items
     */
    public static Stream<MediaItem> createMediaItems(
            @NotNull PhotosLibraryClient photosLibraryClient, @NotNull Album album, @NotNull Collection<MediaFile> files) {
        // upload media files and prepare corresponding new media items
        final List<NewMediaItem> newMediaItemList = files.stream()
                .flatMap(mediaFile -> {
                    try {
                        LoggerFactory.getLogger().fine(
                                ResourceBundleFactory.msg(Messages.UPLOADING_FILE_1, mediaFile.getAbsolutePath()));
                        return Stream.of(Tuple2.makeTuple(mediaFile, uploadMedia(photosLibraryClient, mediaFile)));
                    } catch (IOException e) {
                        LoggerFactory.getLogger().log(Level.SEVERE,
                                ResourceBundleFactory.msg(Messages.SKIPPING_FILE_UPLOAD_2,
                                        mediaFile.getAbsolutePath(), e.getMessage()),
                                e);
                        return Stream.empty();
                    }
                }).flatMap(tuple2 -> {
                    final MediaFile mediaFile = tuple2.i1();
                    final String uploadedContentToken = tuple2.i2();
                    try {
                        return Stream.of(NewMediaItemFactory.createNewMediaItem(uploadedContentToken, mediaFile.generateDescription()));
                    } catch (IOException | NoSuchAlgorithmException e) {
                        LoggerFactory.getLogger().log(Level.SEVERE,
                                ResourceBundleFactory.msg(Messages.SKIPPING_MEDIA_ITEM_CREATION_2,
                                        mediaFile.getAbsolutePath(), e.getMessage()),
                                e);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        // getLogger a list of streams of new media items created in batches of limited size
        LoggerFactory.getLogger().fine(
                ResourceBundleFactory.msg(Messages.CREATING_MEDIA_ITEMS_1, newMediaItemList.size()));
        final List<Stream<MediaItem>> streamList = new LinkedList<>();
        for (List<NewMediaItem> chunk : Lists.partition(newMediaItemList, CREATE_MEDIA_ITEMS_BATCH_LIMIT)) {
            final BatchCreateMediaItemsResponse batchCreateMediaItemsResponse =
                    photosLibraryClient.batchCreateMediaItems(album.getId(), chunk);
            // getLogger resulting media items as a stream
            final Stream<MediaItem> mediaItemStream = batchCreateMediaItemsResponse.getNewMediaItemResultsList().stream()
                    .filter(newMediaItemResult -> newMediaItemResult.getStatus().getCode() == Code.OK_VALUE)
                    .map(NewMediaItemResult::getMediaItem);
            // add the stream into the list of streams
            streamList.add(mediaItemStream);
        }
        // return a flatten stream of the resulting media items
        return streamList.stream().flatMap(mediaItemStream -> mediaItemStream);
    }

    /**
     * Upload a media content from a given file.
     *
     * @param photosLibraryClient the photos library client
     * @param file                the file to upload
     * @return the resulting token of the uploaded content
     * @throws IOException the file cannot be found or uploaded
     */
    @NotNull
    public static String uploadMedia(@NotNull PhotosLibraryClient photosLibraryClient, @NotNull File file) throws IOException {
        try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            final UploadMediaItemRequest uploadMediaItemRequest = UploadMediaItemRequest.newBuilder()
                    .setFileName(file.getName()).setDataFile(randomAccessFile).build();
            final UploadMediaItemResponse uploadMediaItemResponse = photosLibraryClient.uploadMediaItem(uploadMediaItemRequest);
            if (uploadMediaItemResponse.getError().isPresent() || !uploadMediaItemResponse.getUploadToken().isPresent()) {
                throw new IOException(ResourceBundleFactory.msg(Messages.CANNOT_UPLOAD_FILE_2,
                        file.getAbsolutePath(), uploadMediaItemResponse.getError().toString()));
            } else {
                return uploadMediaItemResponse.getUploadToken().get();
            }
        }
    }

}
