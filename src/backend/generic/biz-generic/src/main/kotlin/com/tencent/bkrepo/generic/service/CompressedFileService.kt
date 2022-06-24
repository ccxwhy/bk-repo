/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.pojo.CompressedFileInfo
import com.tencent.bkrepo.repository.api.NodeClient
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.InputStream

@Service
class CompressedFileService(
    private val nodeClient: NodeClient,
    private val storageManager: StorageManager,
    private val artifactResourceWriter: ArtifactResourceWriter
) : ArtifactService() {

    /**
     * 压缩文件列表
     */
    fun listCompressedFile(artifactInfo: GenericArtifactInfo): List<CompressedFileInfo> {
        val entryList = mutableListOf<CompressedFileInfo>()
        val inputStream = getArchiveInputStream(artifactInfo)
        var entry: ArchiveEntry?
        while (inputStream.nextEntry.also { entry = it } != null) {
            if (entry!!.isDirectory) {
                continue
            }
            entryList.add(
                CompressedFileInfo(
                    entry!!.name,
                    entry!!.size,
                    entry!!.lastModifiedDate
                )
            )
        }
        return entryList
    }

    /**
     * 预览压缩文件
     */
    fun previewCompressedFile(artifactInfo: GenericArtifactInfo, filePath: String) {
        val inputStream = getArchiveInputStream(artifactInfo)
        var entry: ArchiveEntry?
        val artifactResource: ArtifactResource
        while (inputStream.nextEntry.also { entry = it } != null) {
            if (entry!!.name == filePath) {
                var byteArray = ByteArray(PREVIEW_FILE_SIZE_LIMIT)
                val size = inputStream.read(byteArray, 0, PREVIEW_FILE_SIZE_LIMIT)
                byteArray = byteArray.sliceArray(IntRange(0, size - 1))
                artifactResource = ArtifactResource(
                    inputStream = ArtifactInputStream(byteArray.inputStream(), Range.full(size.toLong())),
                    artifactName = entry!!.name
                )
                artifactResourceWriter.write(artifactResource)
                return
            }
        }
        throw NodeNotFoundException(filePath)
    }

    private fun getArchiveInputStream(artifactInfo: GenericArtifactInfo): ArchiveInputStream {
        with(artifactInfo) {
            val fileExtension = PathUtils.resolveExtension(getArtifactName())
            if (!Regex(COMPRESSED_FILE_TYPE_PATTERN).matches(fileExtension)) {
                throw ErrorCodeException(ArtifactMessageCode.ARTIFACT_TYPE_UNSUPPORTED, fileExtension)
            }
            val node = nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath()).data
                ?: throw NodeNotFoundException(getArtifactFullPath())
            if (node.size > COMPRESSED_FILE_SIZE_LIMIT) {
                throw ErrorCodeException(ArtifactMessageCode.ARTIFACT_SIZE_TOO_LARGE, COMPRESSED_FILE_SIZE_LIMIT_DESC)
            }
            val context = ArtifactDownloadContext()
            var inputStream: InputStream = storageManager.loadArtifactInputStream(node, context.storageCredentials)
                ?: throw ArtifactNotFoundException(getArtifactFullPath())
            if (fileExtension == GZ_FILE_TYPE || fileExtension == TGZ_FILE_TYPE) {
                inputStream = GzipCompressorInputStream(inputStream)
            }
            return ArchiveStreamFactory().createArchiveInputStream(BufferedInputStream(inputStream))
        }
    }

    companion object {
        private const val COMPRESSED_FILE_TYPE_PATTERN = "(rar|zip|gz|tgz|tar|jar)\$"
        private const val COMPRESSED_FILE_SIZE_LIMIT = 1024 * 1024 * 1024
        private const val COMPRESSED_FILE_SIZE_LIMIT_DESC = "1GB"
        private const val PREVIEW_FILE_SIZE_LIMIT = 50 * 1024 * 1024
        private const val GZ_FILE_TYPE = "gz"
        private const val TGZ_FILE_TYPE = "tgz"
    }
}
