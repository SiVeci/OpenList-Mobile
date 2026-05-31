package com.openlistmobile.app.domain.sync

import com.openlistmobile.app.data.local.ConflictStrategy
import com.openlistmobile.app.data.local.SyncMode
import com.openlistmobile.app.data.local.SyncRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    private fun rule(
        mode: SyncMode = SyncMode.TWO_WAY,
        conflict: ConflictStrategy = ConflictStrategy.NEWEST_WINS,
        mirror: Boolean = false,
        ignoreTime: Boolean = false
    ) = SyncRule(
        id = 1,
        profileId = 0,
        ruleName = "t",
        remotePath = "/root",
        localUri = "content://tree/root",
        syncMode = mode,
        isMirrorDeleteEnabled = mirror,
        conflictStrategy = conflict,
        ignoreModifiedTime = ignoreTime
    )

    private fun rNode(rel: String, size: Long = 10, modified: String = "2026-01-01T00:00:00+00:00") =
        RemoteNode(relativePath = rel, name = rel.substringAfterLast('/'), size = size, modified = modified, sign = "s")

    private fun lNode(rel: String, size: Long = 10, lastModified: Long = 0L) =
        LocalNode(relativePath = rel, name = rel.substringAfterLast('/'), uri = "content://$rel", size = size, lastModified = lastModified)

    @Test
    fun download_only_pulls_remote_only_files_including_subdirs() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.DOWNLOAD_ONLY),
            remote = listOf(rNode("a.txt"), rNode("sub/b.txt"), rNode("sub/deep/c.txt")),
            local = emptyList()
        )
        assertEquals(3, report.toDownload.size)
        assertTrue(report.toDownload.any { it.relativePath == "sub/deep/c.txt" })
        assertEquals(0, report.toUpload.size)
    }

    @Test
    fun upload_only_pushes_local_only_files() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.UPLOAD_ONLY),
            remote = emptyList(),
            local = listOf(lNode("x.txt"), lNode("sub/y.txt"))
        )
        assertEquals(2, report.toUpload.size)
        assertTrue(report.toUpload.any { it.relativePath == "sub/y.txt" })
    }

    @Test
    fun two_way_complements_both_sides() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.TWO_WAY),
            remote = listOf(rNode("only_remote.txt")),
            local = listOf(lNode("only_local.txt"))
        )
        assertEquals(1, report.toDownload.size)
        assertEquals(1, report.toUpload.size)
    }

    @Test
    fun same_size_and_time_is_skipped() {
        val ts = "2026-01-01T00:00:00+00:00"
        val epoch = java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
        val report = DiffEngine.computeDiff(
            rule(),
            remote = listOf(rNode("a.txt", size = 100, modified = ts)),
            local = listOf(lNode("a.txt", size = 100, lastModified = epoch))
        )
        assertEquals(1, report.skipped.size)
        assertEquals(0, report.transferCount)
    }

    @Test
    fun ignore_time_skips_when_size_matches() {
        val report = DiffEngine.computeDiff(
            rule(ignoreTime = true),
            remote = listOf(rNode("a.txt", size = 100, modified = "2026-01-01T00:00:00+00:00")),
            local = listOf(lNode("a.txt", size = 100, lastModified = 999999L))
        )
        assertEquals(1, report.skipped.size)
        assertEquals(0, report.transferCount)
    }

    @Test
    fun different_size_triggers_conflict_cloud_wins() {
        val report = DiffEngine.computeDiff(
            rule(conflict = ConflictStrategy.CLOUD_WINS),
            remote = listOf(rNode("a.txt", size = 200)),
            local = listOf(lNode("a.txt", size = 100))
        )
        assertEquals(1, report.toDownload.size)
        assertTrue(report.toDownload[0].reason.contains("云端"))
    }

    @Test
    fun conflict_skip_strategy_skips() {
        val report = DiffEngine.computeDiff(
            rule(conflict = ConflictStrategy.SKIP),
            remote = listOf(rNode("a.txt", size = 200)),
            local = listOf(lNode("a.txt", size = 100))
        )
        assertEquals(0, report.transferCount)
        assertEquals(1, report.skipped.size)
    }

    @Test
    fun mirror_delete_local_in_download_mode() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.DOWNLOAD_ONLY, mirror = true),
            remote = emptyList(),
            local = listOf(lNode("orphan.txt"), lNode("sub/orphan2.txt"))
        )
        assertEquals(2, report.toDeleteLocal.size)
        assertTrue(report.toDeleteLocal.any { it.relativePath == "sub/orphan2.txt" })
    }

    @Test
    fun mirror_delete_disabled_keeps_orphan_in_download_mode() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.DOWNLOAD_ONLY, mirror = false),
            remote = emptyList(),
            local = listOf(lNode("orphan.txt"))
        )
        assertEquals(0, report.toDeleteLocal.size)
    }

    @Test
    fun two_way_never_produces_deletes() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.TWO_WAY, mirror = true),
            remote = listOf(rNode("only_remote.txt")),
            local = listOf(lNode("only_local.txt"))
        )
        assertEquals(0, report.toDeleteLocal.size)
        assertEquals(0, report.toDeleteRemote.size)
    }

    @Test
    fun remote_full_path_joins_relative_path() {
        val report = DiffEngine.computeDiff(
            rule(mode = SyncMode.DOWNLOAD_ONLY),
            remote = listOf(rNode("sub/deep/c.txt")),
            local = emptyList()
        )
        assertEquals("/root/sub/deep/c.txt", report.toDownload[0].remoteFullPath)
    }
}
