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

package com.tencent.bkrepo.repository.service.file.impl.edge

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.cluster.EdgeNodeRedirectService
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.NodeShareClient
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.service.file.impl.ShareServiceImpl
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeShareServiceImpl(
    repositoryService: RepositoryService,
    nodeService: NodeService,
    mongoTemplate: MongoTemplate,
    clusterProperties: ClusterProperties,
    redirectService: EdgeNodeRedirectService
) : ShareServiceImpl(
    repositoryService,
    nodeService,
    mongoTemplate,
    redirectService
) {

    private val centerShareClient: NodeShareClient by lazy {
        FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name)
    }

    override fun create(
        userId: String,
        artifactInfo: ArtifactInfo,
        request: ShareRecordCreateRequest
    ): ShareRecordInfo {
        return centerShareClient.create(userId, artifactInfo, request).data!!
    }

    override fun checkToken(userId: String, token: String, artifactInfo: ArtifactInfo): ShareRecordInfo {
        return centerShareClient.checkToken(userId, token, artifactInfo).data!!
    }
}