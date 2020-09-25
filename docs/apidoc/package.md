## Package包管理接口说明

包管理接口使用统一接口协议，公共部分请参照[通用接口协议说明](./common.md)

### 分页查询包列表

- API: GET /repository/api/package/page/{projectId}/{repoName}?packageName=xxx&pageNumber=0&pageSize=20

- API 名称: list_package_page

- 功能说明：

  - 中文：分页查询包列表
  - English：list package page

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段        | 类型   | 是否必须 | 默认值 | 说明                         | Description  |
  | ----------- | ------ | -------- | ------ | ---------------------------- | ------------ |
  | projectId   | string | 是       | 无     | 项目名称                     | project name |
  | repoName    | string | 是       | 无     | 仓库名称                     | repo name    |
  | packageName | string | 否       | 无     | 包名称，支持前缀匹配模糊搜索 | package name |
  | pageNumber  | Int    | 是       | 无     | 页码                         | page number  |
  | pageSize    | Int    | 是       | 无     | 每页数量                     | page size    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 1,
      "totalRecords": 18,
      "totalPages": 18,
      "records": [
        {
          "projectId" : "test",
          "repoName" : "generic-local",
          "name" : "test",
          "key" : "com.tencent.bkrepo",
          "type" : "MAVEN",
          "latest" : "0.0.9",
          "downloads" : 101,
          "versions": 9,
          "description": null,
          "createdBy" : "admin",
          "createdDate" : "2020-07-27T16:02:31.394",
          "lastModifiedBy" : "admin",
          "lastModifiedDate" : "2020-07-27T16:02:31.394"
        }
      ]
    },
    "traceId": ""
  }
  ```

- records字段说明

  | 字段             | 类型   | 说明             | Description         |
  | ---------------- | ------ | ---------------- | ------------------- |
  | projectId        | string | 节点所属项目     | project id          |
  | repoName         | string | 节点所属仓库     | repository name     |
  | name             | string | 包名称           | package name        |
  | key              | string | 包唯一key        | package unique key  |
  | type             | string | 包类别           | package type        |
  | latest           | string | 最新上传版本名称 | latest version name |
  | downloads        | Long   | 下载次数         | download times      |
  | versions         | Long   | 版本数量         | version count       |
  | description      | string | 简要描述         | brief description   |
  | createdBy        | string | 创建者           | create user         |
  | createdDate      | string | 创建时间         | create time         |
  | lastModifiedBy   | string | 上次修改者       | last modify user    |
  | lastModifiedDate | string | 上次修改时间     | last modify time    |



### 查询包信息

- API: GET /repository/api/package/info/{projectId}/{repoName}/{packageKey}

- API 名称: get_package_info

- 功能说明：

  - 中文：查询包信息
  - English：get package info

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段       | 类型   | 是否必须 | 默认值 | 说明      | Description        |
  | ---------- | ------ | -------- | ------ | --------- | ------------------ |
  | projectId  | string | 是       | 无     | 项目名称  | project name       |
  | repoName   | string | 是       | 无     | 仓库名称  | repo name          |
  | packageKey | string | 是       | 无     | 包唯一key | package unique key |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      {
        "projectId" : "test",
        "repoName" : "generic-local",
        "name" : "test",
        "key" : "com.tencent.bkrepo",
        "type" : "MAVEN",
        "latest" : "0.0.9",
        "downloads" : 101,
        "versions": 9,
        "description": null,
        "createdBy" : "admin",
        "createdDate" : "2020-07-27T16:02:31.394",
        "lastModifiedBy" : "admin",
        "lastModifiedDate" : "2020-07-27T16:02:31.394"
      }
    },
    "traceId": ""
  }
  ```

- record字段说明

  | 字段             | 类型   | 说明             | Description         |
  | ---------------- | ------ | ---------------- | ------------------- |
  | projectId        | string | 节点所属项目     | project id          |
  | repoName         | string | 节点所属仓库     | repository name     |
  | name             | string | 包名称           | package name        |
  | key              | string | 包唯一key        | package unique key  |
  | type             | string | 包类别           | package type        |
  | latest           | string | 最新上传版本名称 | latest version name |
  | downloads        | Long   | 下载次数         | download times      |
  | versions         | Long   | 版本数量         | version count       |
  | description      | string | 简要描述         | brief description   |
  | createdBy        | string | 创建者           | create user         |
  | createdDate      | string | 创建时间         | create time         |
  | lastModifiedBy   | string | 上次修改者       | last modify user    |
  | lastModifiedDate | string | 上次修改时间     | last modify time    |



### 删除包

- API: GET /repository/api/package/delete/{projectId}/{repoName}/{packageKey}

- API 名称: delete_package

- 功能说明：

  - 中文：删除包
  - English：delete package

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段       | 类型   | 是否必须 | 默认值 | 说明      | Description        |
  | ---------- | ------ | -------- | ------ | --------- | ------------------ |
  | projectId  | string | 是       | 无     | 项目名称  | project name       |
  | repoName   | string | 是       | 无     | 仓库名称  | repo name          |
  | packageKey | string | 是       | 无     | 包唯一key | package unique key |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- record字段说明

  请求成功无返回数据



### 分页查询包版本

- API: GET /repository/api/version/page/{projectId}/{repoName}/{packageKey}?pageNumber=0&pageSize=20

- API 名称: list_version_page

- 功能说明：

  - 中文：分页查询版本列表
  - English：list version page

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段       | 类型   | 是否必须 | 默认值 | 说明      | Description        |
  | ---------- | ------ | -------- | ------ | --------- | ------------------ |
  | projectId  | string | 是       | 无     | 项目名称  | project name       |
  | repoName   | string | 是       | 无     | 仓库名称  | repo name          |
  | packageKey | string | 否       | 无     | 包唯一key | package unique key |
  | pageNumber | Int    | 是       | 无     | 页码      | page number        |
  | pageSize   | Int    | 是       | 无     | 每页数量  | page size          |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 1,
      "totalRecords": 18,
      "totalPages": 18,
      "records": [
        {
          "name" : "0.0.9",
          "size" : 1024,
          "downloads" : 18,
          "stageTag" : ["@prerelease", "@release"],
          "metadata" : {
            "key": "value"
          },
          "createdBy" : "admin",
          "createdDate" : "2020-07-27T16:02:31.394",
          "lastModifiedBy" : "admin",
          "lastModifiedDate" : "2020-07-27T16:02:31.394"
        }
      ]
    },
    "traceId": ""
  }
  ```

- records字段说明

  | 字段             | 类型         | 说明                    | Description      |
  | ---------------- | ------------ | ----------------------- | ---------------- |
  | name             | string       | 版本名称                | version name     |
  | size             | long         | 版本大小                | version size     |
  | downloads        | long         | 版本下载次数            | download times   |
  | stageTag         | list[string] | 晋级阶段标签列表        | stage tag list   |
  | metadata         | object       | 元数据，key-value键值对 | metadata         |
  | createdBy        | string       | 创建者                  | create user      |
  | createdDate      | string       | 创建时间                | create time      |
  | lastModifiedBy   | string       | 上次修改者              | last modify user |
  | lastModifiedDate | string       | 上次修改时间            | last modify time |



### 删除版本

- API: GET /repository/api/version/delete/{projectId}/{repoName}/{packageKey}/{version}

- API 名称: delete_version

- 功能说明：

  - 中文：删除版本
  - English：delete version

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段       | 类型   | 是否必须 | 默认值 | 说明      | Description        |
  | ---------- | ------ | -------- | ------ | --------- | ------------------ |
  | projectId  | string | 是       | 无     | 项目名称  | project name       |
  | repoName   | string | 是       | 无     | 仓库名称  | repo name          |
  | packageKey | string | 是       | 无     | 包唯一key | package unique key |
  | version    | string | 是       | 无     | 版本名称  | version name       |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- record字段说明

  请求成功无返回数据



### 相关公共说明

#### 1. 包类型枚举

> 用于标识构件包类型

| 枚举值   | 说明       |
| -------- | ---------- |
| DOCKER   | Docker包   |
| MAVEN    | Maven包    |
| PYPI     | Pypi包     |
| NPM      | Npm包      |
| HELM     | Helm包     |
| COMPOSER | Composer包 |
| RPM      | Rpm包      |

#### 2. package key 格式

因为同个仓库下包名称不唯一， 所以使用package key来确定包的唯一性。

pakcage key的格式为: {type}://{value}

| type类型              | value格式                      | 例子                          |
| --------------------- | ------------------------------ | ----------------------------- |
| gav (maven采用此格式) | groupId:artifactId             | gav://com.tencent.bkrepo:test |
| docker                | name(包名称，docker中名称唯一) | docker://test                 |
| npm                   | name                           | npm://test                    |