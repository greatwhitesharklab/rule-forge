package com.ruleforge.console.storage;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.storage.impl.GitStorageServiceImpl;
import com.ruleforge.console.storage.model.FileDiff;
import com.ruleforge.console.storage.model.MergeResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD tests for GitStorageServiceImpl.
 *
 * Feature: Git-based version storage
 *
 * Rule: Each project gets its own Git repository
 * Rule: Files can be written, committed, and read back from any branch/tag
 * Rule: Branches can be created, merged, and deleted
 * Rule: Diffs can be computed between any two revisions
 */
class GitStorageServiceImplTest {

    @TempDir
    static Path tempDir;

    private static GitStorageService gitStorage;

    @BeforeAll
    static void setUp() {
        GitConfig config = new GitConfig();
        config.setBase(tempDir.toString());
        gitStorage = new GitStorageServiceImpl(config);
    }

    @Nested
    @DisplayName("Repository lifecycle")
    class RepoLifecycleTests {

        @Test
        @DisplayName("Given no repo exists, When initRepo, Then repo is created with main branch")
        void initRepoCreatesMainBranch() {
            // Given
            assertFalse(gitStorage.repoExists("test-project"));

            // When
            gitStorage.initRepo("test-project");

            // Then
            assertTrue(gitStorage.repoExists("test-project"));
            List<String> branches = gitStorage.listBranches("test-project");
            assertTrue(branches.contains("main"));
        }

        @Test
        @DisplayName("Given repo exists, When initRepo again, Then no error")
        void initRepoIdempotent() {
            gitStorage.initRepo("idempotent-test");
            assertDoesNotThrow(() -> gitStorage.initRepo("idempotent-test"));
        }
    }

    @Nested
    @DisplayName("File write, commit, and read")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FileOpsTests {

        private static final String PROJECT = "file-ops-test";

        @BeforeAll
        static void init() {
            gitStorage.initRepo(PROJECT);
        }

        @Test
        @Order(1)
        @DisplayName("When write + commit + read on main, Then content matches")
        void writeReadOnMain() {
            // Given
            String content = "<decision-table><cell row=\"0\" col=\"0\"/></decision-table>";

            // When
            gitStorage.writeFile(PROJECT, "main", "folder/file.xml", content);
            String sha = gitStorage.commit(PROJECT, "main", "Add file.xml", "testuser");

            // Then
            assertNotNull(sha);
            String readBack = gitStorage.readFile(PROJECT, "main", "folder/file.xml");
            assertEquals(content, readBack);
        }

        @Test
        @Order(2)
        @DisplayName("When read from non-existent file, Then returns null")
        void readNonExistentFile() {
            assertNull(gitStorage.readFile(PROJECT, "main", "no/such/file.xml"));
        }

        @Test
        @Order(3)
        @DisplayName("When write same file twice + commit, Then read returns latest content")
        void overwriteFile() {
            gitStorage.writeFile(PROJECT, "main", "overwrite.xml", "v1");
            gitStorage.commit(PROJECT, "main", "v1", "testuser");

            gitStorage.writeFile(PROJECT, "main", "overwrite.xml", "v2");
            gitStorage.commit(PROJECT, "main", "v2", "testuser");

            assertEquals("v2", gitStorage.readFile(PROJECT, "main", "overwrite.xml"));
        }

        @Test
        @Order(4)
        @DisplayName("When delete a file + commit, Then readFile returns null")
        void deleteFile() {
            gitStorage.writeFile(PROJECT, "main", "to-delete.xml", "content");
            gitStorage.commit(PROJECT, "main", "add to-delete", "testuser");

            gitStorage.deleteFile(PROJECT, "main", "to-delete.xml");
            gitStorage.commit(PROJECT, "main", "delete to-delete", "testuser");

            assertNull(gitStorage.readFile(PROJECT, "main", "to-delete.xml"));
        }
    }

    @Nested
    @DisplayName("Branch operations")
    class BranchTests {

        private static final String PROJECT = "branch-test";

        @BeforeAll
        static void init() {
            gitStorage.initRepo(PROJECT);
            // Write a file on main
            gitStorage.writeFile(PROJECT, "main", "shared.xml", "main-content");
            gitStorage.commit(PROJECT, "main", "initial", "testuser");
        }

        @Test
        @DisplayName("When create branch from main, Then branch exists and has same content")
        void createBranchHasMainContent() {
            // When
            gitStorage.createBranch(PROJECT, "user/testuser", "main");

            // Then
            List<String> branches = gitStorage.listBranches("branch-test");
            assertTrue(branches.contains("user/testuser"));

            String content = gitStorage.readFile(PROJECT, "user/testuser", "shared.xml");
            assertEquals("main-content", content);
        }

        @Test
        @DisplayName("Given branch exists, When write different content, Then branches diverge")
        void branchesDiverge() {
            // Given
            gitStorage.createBranch(PROJECT, "user/alice", "main");

            // When
            gitStorage.writeFile(PROJECT, "user/alice", "shared.xml", "alice-content");
            gitStorage.commit(PROJECT, "user/alice", "alice edit", "alice");

            // Then
            assertEquals("main-content", gitStorage.readFile(PROJECT, "main", "shared.xml"));
            assertEquals("alice-content", gitStorage.readFile(PROJECT, "user/alice", "shared.xml"));
        }

        @Test
        @DisplayName("When createBranch on existing name, Then no error (idempotent)")
        void createBranchIdempotent() {
            gitStorage.createBranch(PROJECT, "user/idempotent", "main");
            assertDoesNotThrow(() -> gitStorage.createBranch(PROJECT, "user/idempotent", "main"));
        }
    }

    @Nested
    @DisplayName("Merge operations")
    class MergeTests {

        private static final String PROJECT = "merge-test";

        @BeforeAll
        static void init() {
            gitStorage.initRepo(PROJECT);
            gitStorage.writeFile(PROJECT, "main", "base.xml", "base");
            gitStorage.commit(PROJECT, "main", "initial", "testuser");
        }

        @Test
        @DisplayName("When merge clean branch into main, Then merge succeeds and main has new content")
        void cleanMerge() {
            // Given: create branch and add a file
            gitStorage.createBranch(PROJECT, "user/merger", "main");
            gitStorage.writeFile(PROJECT, "user/merger", "new-file.xml", "merged-content");
            gitStorage.commit(PROJECT, "user/merger", "add new file", "merger");

            // When
            MergeResult result = gitStorage.merge(PROJECT, "user/merger", "main");

            // Then
            assertNotEquals(MergeResult.Status.CONFLICTING, result.getStatus());
            assertNotNull(result.getMergeCommitSha());
            assertEquals("merged-content", gitStorage.readFile(PROJECT, "main", "new-file.xml"));
            assertEquals("base", gitStorage.readFile(PROJECT, "main", "base.xml"));
        }

        @Test
        @DisplayName("When merge with conflict, Then result is CONFLICTING and main is unchanged")
        void conflictingMerge() {
            // Given: main and branch both modify the same file
            gitStorage.createBranch(PROJECT, "user/conflicter", "main");
            gitStorage.writeFile(PROJECT, "user/conflicter", "conflict.xml", "branch-version");
            gitStorage.commit(PROJECT, "user/conflicter", "branch edit", "conflicter");

            gitStorage.writeFile(PROJECT, "main", "conflict.xml", "main-version");
            gitStorage.commit(PROJECT, "main", "main edit", "mainuser");

            // When
            MergeResult result = gitStorage.merge(PROJECT, "user/conflicter", "main");

            // Then
            assertEquals(MergeResult.Status.CONFLICTING, result.getStatus());
            assertFalse(result.getConflictingFiles().isEmpty());
            // Main should be unchanged after aborted merge
            assertEquals("main-version", gitStorage.readFile(PROJECT, "main", "conflict.xml"));
        }
    }

    @Nested
    @DisplayName("Tag operations")
    class TagTests {

        private static final String PROJECT = "tag-test";

        @BeforeAll
        static void init() {
            gitStorage.initRepo(PROJECT);
            gitStorage.writeFile(PROJECT, "main", "tagged.xml", "tagged-content");
            gitStorage.commit(PROJECT, "main", "tagged commit", "testuser");
        }

        @Test
        @DisplayName("When create tag and read by tag, Then content is from tagged commit")
        void readByTag() {
            // When
            gitStorage.createTag(PROJECT, "pkg/pkg1/1.0.0", "main");

            // Then
            String content = gitStorage.readFile(PROJECT, "pkg/pkg1/1.0.0", "tagged.xml");
            assertEquals("tagged-content", content);

            String sha = gitStorage.getRevisionSha(PROJECT, "pkg/pkg1/1.0.0");
            assertNotNull(sha);
        }

        @Test
        @DisplayName("When create same tag twice, Then no error (idempotent)")
        void createTagIdempotent() {
            gitStorage.createTag(PROJECT, "pkg/pkg1/1.0.1", "main");
            assertDoesNotThrow(() -> gitStorage.createTag(PROJECT, "pkg/pkg1/1.0.1", "main"));
        }
    }

    @Nested
    @DisplayName("Diff operations")
    class DiffTests {

        private static final String PROJECT = "diff-test";

        @BeforeAll
        static void init() {
            gitStorage.initRepo(PROJECT);
            // v1
            gitStorage.writeFile(PROJECT, "main", "unchanged.xml", "same");
            gitStorage.writeFile(PROJECT, "main", "changed.xml", "v1");
            gitStorage.commit(PROJECT, "main", "v1", "testuser");
            gitStorage.createTag(PROJECT, "v1", "main");

            // v2
            gitStorage.writeFile(PROJECT, "main", "changed.xml", "v2");
            gitStorage.writeFile(PROJECT, "main", "added.xml", "new");
            gitStorage.commit(PROJECT, "main", "v2", "testuser");
            gitStorage.createTag(PROJECT, "v2", "main");
        }

        @Test
        @DisplayName("When diff two tags, Then returns modified and added files")
        void diffBetweenTags() {
            List<FileDiff> diffs = gitStorage.diff(PROJECT, "v1", "v2");

            assertFalse(diffs.isEmpty());
            assertTrue(diffs.stream().anyMatch(d ->
                    d.getFilePath().equals("changed.xml") && d.getDiffType() == FileDiff.DiffType.MODIFIED));
            assertTrue(diffs.stream().anyMatch(d ->
                    d.getFilePath().equals("added.xml") && d.getDiffType() == FileDiff.DiffType.ADDED));
        }
    }
}
