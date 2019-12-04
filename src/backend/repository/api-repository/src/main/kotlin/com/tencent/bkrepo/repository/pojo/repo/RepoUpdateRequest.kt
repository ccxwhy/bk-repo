package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import com.tencent.bkrepo.repository.pojo.repo.configuration.RepositoryConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 更新仓库请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("更新仓库请求")
data class RepoUpdateRequest(
    @ApiModelProperty("所属项目id", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val name: String,
    @ApiModelProperty("仓库类别", required = false)
    val category: RepositoryCategory? = null,
    @ApiModelProperty("是否公开", required = false)
    val public: Boolean? = null,
    @ApiModelProperty("简要描述", required = false)
    val description: String? = null,
    @ApiModelProperty("扩展信息", required = false)
    val configuration: RepositoryConfiguration? = null,

    @ApiModelProperty("操作用户", required = true)
    val operator: String
)
