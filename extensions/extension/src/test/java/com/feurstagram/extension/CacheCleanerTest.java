package com.feurstagram.extension;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CacheCleanerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cleanupDeletesContentsOnlyInsideTheApprovedTemporaryRoot() throws IOException {
        File root = temporaryFolder.newFolder("cache");
        File nested = new File(root, "subdir/video.cache");
        assertTrue(nested.getParentFile().mkdirs());
        assertTrue(nested.createNewFile());
        CacheCleaner.CleanupResult result = new CacheCleaner.CleanupResult();

        CacheCleaner.deleteContents(root, result);

        assertTrue(root.isDirectory());
        assertFalse(nested.exists());
        assertEquals(2, result.deleted);
        assertEquals(0, result.failures);
    }

    @Test
    public void refusesPathsOutsideTheApprovedRoot() throws IOException {
        File root = temporaryFolder.newFolder("approved");
        File outside = temporaryFolder.newFile("outside.cache");
        CacheCleaner.CleanupResult result = new CacheCleaner.CleanupResult();

        CacheCleaner.deleteRecursive(outside, root, result);

        assertTrue(outside.exists());
        assertEquals(1, result.failures);
        assertEquals(0, result.deleted);
    }

    @Test
    public void removesAnInternalSymlinkWithoutFollowingItsTarget() throws IOException {
        Path root = temporaryFolder.newFolder("symlink-root").toPath();
        Path target = Files.write(root.resolve("target.cache"), new byte[]{1});
        Path link = Files.createSymbolicLink(root.resolve("link.cache"), target.getFileName());
        CacheCleaner.CleanupResult result = new CacheCleaner.CleanupResult();

        CacheCleaner.deleteRecursive(link.toFile(), root.toFile(), result);

        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(target));
        assertEquals(0, result.failures);
    }

    @Test
    public void refusesExternalSymlinksWithoutTouchingTheirTargets() throws IOException {
        Path root = temporaryFolder.newFolder("link-root").toPath();
        Path outside = temporaryFolder.newFile("external.cache").toPath();
        Path link = Files.createSymbolicLink(root.resolve("external-link.cache"), outside);
        CacheCleaner.CleanupResult result = new CacheCleaner.CleanupResult();

        CacheCleaner.deleteRecursive(link.toFile(), root.toFile(), result);

        assertTrue(Files.exists(outside));
        assertTrue(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        assertEquals(1, result.failures);
        assertEquals(0, result.deleted);
    }

    @Test
    public void reportsDeletionErrors() throws IOException {
        File root = temporaryFolder.newFolder("delete-error-root");
        File existing = new File(root, "undeletable.cache");
        assertTrue(existing.createNewFile());
        File undeletable = new File(existing.getAbsolutePath()) {
            @Override
            public boolean delete() {
                return false;
            }
        };
        CacheCleaner.CleanupResult result = new CacheCleaner.CleanupResult();

        CacheCleaner.deleteRecursive(undeletable, root, result);

        assertTrue(existing.exists());
        assertEquals(1, result.failures);
        assertEquals(0, result.deleted);
    }

    @Test
    public void matchesOnlyKnownReelsDatabaseNamesAndSidecars() {
        assertTrue(CacheCleaner.isReelsDatabaseFileName("delivery_media_room_db"));
        assertTrue(CacheCleaner.isReelsDatabaseFileName("user_reel_medias_room_db-wal"));
        assertTrue(CacheCleaner.isReelsDatabaseFileName("clips_user-123.db-shm"));
        assertFalse(CacheCleaner.isReelsDatabaseFileName("instagram.db"));
        assertFalse(CacheCleaner.isReelsDatabaseFileName("../delivery_media_room_db"));
        assertFalse(CacheCleaner.isReelsDatabaseFileName(null));
    }
}
