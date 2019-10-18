package com.tencent.bkrepo.repository.model

/**
 * 仓库存储身份信息
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
data class TStorageCredentials(
    var type: String,
    var credentials: String
)
