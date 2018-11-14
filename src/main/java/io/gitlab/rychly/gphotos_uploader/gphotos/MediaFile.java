package io.gitlab.rychly.gphotos_uploader.gphotos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Media file with abilities required for syncing with Google Photos.
 */
public class MediaFile extends File {
    /**
     * Default regular expression matching the photo files.
     */
    public static final String PHOTO_FILENAME_REGEXP_EXTENSIONS = "jpg|png";
    public static final String PHOTO_FILENAME_REGEXP = ".*\\.(" + PHOTO_FILENAME_REGEXP_EXTENSIONS + ")";

    /**
     * Default regular expression matching the video files.
     */
    public static final String VIDEO_FILENAME_REGEXP_EXTENSIONS = "mp4|avi";
    public static final String VIDEO_FILENAME_REGEXP = ".*\\.(" + VIDEO_FILENAME_REGEXP_EXTENSIONS + ")";

    /**
     * Default regular expression matching the media files.
     */
    public static final String MEDIA_FILENAME_REGEXP =
            ".*\\.(" + PHOTO_FILENAME_REGEXP_EXTENSIONS + "|" + VIDEO_FILENAME_REGEXP_EXTENSIONS + ")";

    /**
     * Algorithm of the content checksum.
     */
    public static final String CHECKSUM_ALGORITHM = "SHA-1";

    /**
     * Separator of parsed items in the media items descriptions.
     */
    public static final String DESCRIPTION_ITEMS_SEPARATOR = ";";

    private byte[] checksum;

    /**
     * Creates a new <code>File</code> instance by converting the given
     * pathname string into an abstract pathname.  If the given string is
     * the empty string, then the result is the empty abstract pathname.
     *
     * @param pathname A pathname string
     * @throws NullPointerException If the <code>pathname</code> argument is <code>null</code>
     */
    public MediaFile(@NotNull String pathname) {
        super(pathname);
    }

    /**
     * Creates a new <code>MediaFile</code> instance from a parent pathname string
     * and a child pathname string.
     *
     * @param parent The parent pathname string
     * @param child  The child pathname string
     * @throws NullPointerException If <code>child</code> is <code>null</code>
     */
    public MediaFile(String parent, @NotNull String child) {
        super(parent, child);
    }

    /**
     * Creates a new <code>MediaFile</code> instance from a parent abstract
     * pathname and a child pathname string.
     *
     * @param parent The parent abstract pathname
     * @param child  The child pathname string
     * @throws NullPointerException If <code>child</code> is <code>null</code>
     */
    public MediaFile(File parent, @NotNull String child) {
        super(parent, child);
    }

    /**
     * Creates a new <code>MediaFile</code> instance by converting the given
     * <code>file:</code> URI into an abstract pathname.
     *
     * @param uri An absolute, hierarchical URI with a scheme equal to
     *            "file", a non-empty path component, and undefined
     *            authority, query, and fragment components
     * @throws NullPointerException     If <code>uri</code> is <code>null</code>
     * @throws IllegalArgumentException If the preconditions on the parameter do not hold
     */
    public MediaFile(@NotNull URI uri) {
        super(uri);
    }

    /**
     * Creates a new <code>MediaFile</code> instance from the absolute path of the given <code>File</code> object.
     *
     * @param file a file to use
     */
    public MediaFile(@NotNull File file) {
        this(file.getAbsolutePath());
    }

    /**
     * Get a stream all media files in a given directory.
     * The media files are those matching the default regular expression for media files.
     *
     * @param directory the directory to search for the media files
     * @return the stream of media files
     * @throws FileNotFoundException cannot find the directory
     */
    public static Stream<MediaFile> fileFinder(File directory) throws FileNotFoundException {
        return fileFinder(directory, MEDIA_FILENAME_REGEXP);
    }

    /**
     * Get a stream of all media files in a given directory.
     *
     * @param directory      the directory to search for the media files
     * @param fileNameRegExp a regular expression matching the media files
     * @return the stream of media files
     * @throws FileNotFoundException cannot find the directory
     */
    public static Stream<MediaFile> fileFinder(File directory, String fileNameRegExp) throws FileNotFoundException {
        final File[] files = directory.listFiles((dir, name) -> name.matches(fileNameRegExp));
        if (files == null) {
            throw new FileNotFoundException("Cannot find directory " + directory);
        }
        return Arrays.stream(files).map(MediaFile::new);
    }

    @Nullable
    private static String extractDescriptionItem(@NotNull String description, int itemNumber) {
        final String[] splitDescription = description.trim().split("\\s*" + DESCRIPTION_ITEMS_SEPARATOR + "\\s*");
        return (itemNumber < splitDescription.length) ? splitDescription[itemNumber] : null;
    }

    /**
     * Extract a string representation of the checksum from a given media item description string.
     *
     * @param description the media item description string
     * @return the string representation of the checksum or <code>null</code> if cannot be extracted
     */
    @Nullable
    public static String extractChecksumStringFromDescription(@NotNull String description) {
        return extractDescriptionItem(description, 0);
    }

    /**
     * Extract a zoned date and time of the last modification of the media file from a given media item description string.
     *
     * @param description the media item description string
     * @return zoned date and time of the last modification or <code>null</code> if cannot be extracted
     */
    @Nullable
    public static ZonedDateTime extractLastModifiedDateFromDescription(@NotNull String description) {
        final String formattedZonedDateTime = extractDescriptionItem(description, 1);
        return (formattedZonedDateTime == null) ? null : ZonedDateTime.parse(formattedZonedDateTime);
    }

    /**
     * Checks if the the media file is a photo.
     *
     * @return true iff the media file is a photo
     */
    public boolean isPhoto() {
        return this.getName().matches(PHOTO_FILENAME_REGEXP);
    }

    /**
     * Checks if the the media file is a video.
     *
     * @return true iff the media file is a video
     */
    public boolean isVideo() {
        return this.getName().matches(VIDEO_FILENAME_REGEXP);
    }

    /**
     * Compute and set a checksum of the content of the media file.
     *
     * @throws NoSuchAlgorithmException cannot find the checksum algorithm
     * @throws IOException              cannot access the file
     */
    public void setContentChecksum() throws NoSuchAlgorithmException, IOException {
        final MessageDigest messageDigest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
        try (final InputStream fileInputStream = new FileInputStream(this)) {
            final byte[] buffer = new byte[8192];
            int n = 0;
            while (n != -1) {
                n = fileInputStream.read(buffer);
                if (n > 0) {
                    messageDigest.update(buffer, 0, n);
                }
            }
            this.checksum = messageDigest.digest();
        }
    }

    /**
     * Get a checksum of the content of the media file.
     *
     * @return the checksum in bytes of the content of the media file
     * @throws NoSuchAlgorithmException cannot find the checksum algorithm
     * @throws IOException              cannot access the file
     */
    public byte[] getContentChecksum() throws NoSuchAlgorithmException, IOException {
        if (checksum == null) {
            setContentChecksum();
        }
        return checksum;
    }

    /**
     * Get a checksum of the content of the media file as a string representation of a hexadecimal number.
     *
     * @return the checksum hex-string of the content of the media file
     * @throws NoSuchAlgorithmException cannot find the checksum algorithm
     * @throws IOException              cannot access the file
     */
    public String getContentChecksumString() throws NoSuchAlgorithmException, IOException {
        final int checksumSizeInHalfBytes = getContentChecksum().length * 2;
        return String.format(CHECKSUM_ALGORITHM + ":%0" + checksumSizeInHalfBytes + "x",
                new BigInteger(1, getContentChecksum()));
    }

    /**
     * Check whether a given checksum string is matching the content checksum.
     *
     * @param checksumString the checksum string to check
     * @return <code>true</code> iff the checksum string is matching
     * @throws NoSuchAlgorithmException cannot find the checksum algorithm
     * @throws IOException              cannot access the file
     */
    public boolean isChecksumStringMatching(String checksumString) throws IOException, NoSuchAlgorithmException {
        return getContentChecksumString().equalsIgnoreCase(checksumString);
    }

    /**
     * Get a zoned date and time of the last modification of the media file.
     *
     * @return the zoned date and time of the last modification of the media file
     * @throws IOException cannot access the file
     */
    public ZonedDateTime getLastModifiedDate() throws IOException {
        return Files.getLastModifiedTime(toPath()).toInstant().atZone(ZoneId.systemDefault());
    }

    /**
     * Generate a media item description string.
     *
     * @return the media item description string
     * @throws NoSuchAlgorithmException cannot find the checksum algorithm
     * @throws IOException              cannot access the file
     */
    public String generateDescription() throws IOException, NoSuchAlgorithmException {
        return getContentChecksumString()
                + DESCRIPTION_ITEMS_SEPARATOR + " "
                + getLastModifiedDate().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
}
