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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.npm.async

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.LENGTH
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmMetaData
import com.tencent.bkrepo.npm.utils.BeanUtils
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PackageHandler {
    @Autowired
    private lateinit var packageClient: PackageClient

    /**
     * 创建包版本
     */
    @Async
    fun createVersion(userId: String, artifactInfo: NpmArtifactInfo, npmMetaDataJsonObject: JsonObject, attributes: Map<String, Any>) {
        npmMetaDataJsonObject.apply {
            val name = this[NAME].asString
            val description = this[DESCRIPTION]?.asString
            val version = getLatestVersion(this.getAsJsonObject(DISTTAGS))
            val size = attributes[LENGTH] as Long
            val manifestPath = getManifestPath(name, version)
            val contentPath = getContentPath(name, version)
            val metadata = buildMetaData(this.getAsJsonObject(VERSIONS).getAsJsonObject(version))
            with(artifactInfo) {
                val packageVersionCreateRequest = PackageVersionCreateRequest(
                    projectId,
                    repoName,
                    name,
                    PackageKeys.ofNpm(name),
                    PackageType.NPM,
                    description,
                    version,
                    size,
                    manifestPath,
                    contentPath,
                    null,
                    metadata,
                    false,
                    userId
                )
                packageClient.createVersion(packageVersionCreateRequest).apply {
                    logger.info("user: [$userId] create package version [$packageVersionCreateRequest] success!")
                }
            }
        }
    }

    /**
     * 删除包
     */
    @Async
    fun deletePackage(userId: String, name: String, artifactInfo: NpmArtifactInfo) {
        val packageKey = PackageKeys.ofNpm(name)
        with(artifactInfo) {
            packageClient.deletePackage(projectId, repoName, packageKey).apply {
                logger.info("user: [$userId] delete package [$name] in repo [$projectId/$repoName] success!")
            }
        }
    }

    /**
     * 删除版本
     */
    @Async
    fun deleteVersion(userId: String, name: String, version: String, artifactInfo: NpmArtifactInfo) {
        val packageKey = PackageKeys.ofNpm(name)
        with(artifactInfo) {
            packageClient.deleteVersion(projectId, repoName, packageKey, version).apply {
                logger.info("user: [$userId] delete package [$name] with version [$version] in repo [$projectId/$repoName] success!")
            }
        }
    }

    fun getLatestVersion(distTags: JsonObject): String {
        val iterator = distTags.entrySet().iterator()
        if (iterator.hasNext()) {
            return iterator.next().value.asString
        }
        return distTags[LATEST].asString
    }

    fun getManifestPath(name: String, version: String): String {
        return NpmUtils.getVersionPackageMetadataPath(name, version)
    }

    fun getContentPath(name: String, version: String): String {
        return String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version)
    }

    fun buildMetaData(versionJsonObj: JsonObject): Map<String, String> {
        val metaData = GsonUtils.gson.fromJson(versionJsonObj, NpmMetaData::class.java)
        return BeanUtils.beanToMap(metaData)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PackageHandler::class.java)
    }
}
