package com.tencent.bkrepo.common.storage.s3

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.internal.Constants
import com.amazonaws.services.s3.model.DeleteObjectRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.S3Credentials
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor
import javax.annotation.Resource

open class S3Storage : AbstractFileStorage<S3Credentials, S3Client>() {

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    private var defaultTransferManager: TransferManager? = null

    override fun store(path: String, filename: String, file: File, client: S3Client) {
        val transferManager = getTransferManager(client)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        shutdownTransferManager(transferManager)
    }

    override fun store(path: String, filename: String, inputStream: InputStream, size: Long, client: S3Client) {
        val metadata = ObjectMetadata().apply { contentLength = size }
        client.s3Client.putObject(client.bucketName, filename, inputStream, metadata)
    }

    override fun load(path: String, filename: String, range: Range, client: S3Client): InputStream? {
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        getObjectRequest.setRange(range.start, range.end)
        return client.s3Client.getObject(getObjectRequest).objectContent
    }

    override fun delete(path: String, filename: String, client: S3Client) {
        if (exist(path, filename, client)) {
            val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
            client.s3Client.deleteObject(deleteObjectRequest)
        }
    }

    override fun exist(path: String, filename: String, client: S3Client): Boolean {
        return try {
            client.s3Client.doesObjectExist(client.bucketName, filename)
        } catch (ignored: Exception) {
            false
        }
    }

    override fun onCreateClient(credentials: S3Credentials): S3Client {
        require(credentials.accessKey.isNotBlank())
        require(credentials.secretKey.isNotBlank())
        require(credentials.endpoint.isNotBlank())
        require(credentials.region.isNotBlank())
        require(credentials.bucket.isNotBlank())

        val config = ClientConfiguration().apply {
            socketTimeout = 60 * 1000 // millsSecond
            maxConnections = 2048
        }
        val endpointConfig = EndpointConfiguration(credentials.endpoint, credentials.region)
        val awsCredentials = BasicAWSCredentials(credentials.accessKey, credentials.secretKey)
        val awsCredentialsProvider = AWSStaticCredentialsProvider(awsCredentials)

        val amazonS3 = AmazonS3Client.builder()
            .withEndpointConfiguration(endpointConfig)
            .withClientConfiguration(config)
            .withCredentials(awsCredentialsProvider)
            .disableChunkedEncoding()
            .withPathStyleAccessEnabled(true)
            .build()

        return S3Client(credentials.bucket, amazonS3)
    }

    private fun getTransferManager(client: S3Client): TransferManager {
        return if (client == defaultClient) {
            if (defaultTransferManager == null) {
                defaultTransferManager = createTransferManager(defaultClient)
            }
            defaultTransferManager!!
        } else {
            createTransferManager(client)
        }
    }

    private fun createTransferManager(client: S3Client): TransferManager {
        val executorService = (taskAsyncExecutor as ThreadPoolTaskExecutor).threadPoolExecutor
        return TransferManagerBuilder.standard()
            .withS3Client(client.s3Client)
            .withMultipartUploadThreshold(10L * Constants.MB)
            .withMinimumUploadPartSize(5L * Constants.MB)
            .withExecutorFactory { executorService }
            .withShutDownThreadPools(false)
            .build()
    }

    private fun shutdownTransferManager(transferManager: TransferManager) {
        if (transferManager != defaultTransferManager) {
            transferManager.shutdownNow(true)
        }
    }
}
