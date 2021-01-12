package com.tencent.bkrepo.helm.pojo.fixtool

/**
 * 包管理包装数据返回
 */
data class PackageManagerResponse (
    val projectId: String = "",
    val repoName: String = "",
    val totalCount: Long,
    val successCount: Long,
    val failedCount: Long,
    val failedSet: Set<String>
)