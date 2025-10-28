package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import com.example.minioupload.config.UploadConfigProperties;
import com.example.minioupload.dto.*;
import com.example.minioupload.model.VideoRecording;
import com.example.minioupload.repository.VideoRecordingRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MultipartUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ConfigProperties s3Config;
    private final UploadConfigProperties uploadConfig;
    private final VideoRecordingRepository videoRecordingRepository;

    public MultipartUploadService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3ConfigProperties s3Config,
            UploadConfigProperties uploadConfig,
            VideoRecordingRepository videoRecordingRepository) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Config = s3Config;
        this.uploadConfig = uploadConfig;
        this.videoRecordingRepository = videoRecordingRepository;
    }

    public InitUploadResponse initializeUpload(InitUploadRequest request) {
        if (!request.getContentType().startsWith("video/")) {
            throw new IllegalArgumentException("Only video files are allowed");
        }

        if (request.getSize() > uploadConfig.getMaxFileSize()) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }

        long chunkSize = request.getChunkSize() != null && request.getChunkSize() > 0
                ? request.getChunkSize()
                : uploadConfig.getDefaultChunkSize();

        String objectKey = "uploads/" + UUID.randomUUID() + "/" + request.getFileName();

        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .contentType(request.getContentType())
                .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);

        int maxPartNumber = (int) Math.ceil((double) request.getSize() / chunkSize);

        return new InitUploadResponse(
                createResponse.uploadId(),
                objectKey,
                chunkSize,
                1,
                maxPartNumber
        );
    }

    public List<PresignedUrlResponse> generatePresignedUrls(
            String uploadId,
            String objectKey,
            int startPartNumber,
            int endPartNumber) {

        List<PresignedUrlResponse> presignedUrls = new ArrayList<>();
        Duration expiration = Duration.ofMinutes(uploadConfig.getPresignedUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(expiration);

        for (int partNumber = startPartNumber; partNumber <= endPartNumber; partNumber++) {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(expiration)
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);

            presignedUrls.add(new PresignedUrlResponse(
                    partNumber,
                    presignedRequest.url().toString(),
                    expiresAt
            ));
        }

        return presignedUrls;
    }

    public List<UploadPartInfo> getUploadStatus(String uploadId, String objectKey) {
        ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .uploadId(uploadId)
                .build();

        ListPartsResponse listPartsResponse = s3Client.listParts(listPartsRequest);

        List<UploadPartInfo> uploadedParts = new ArrayList<>();
        for (Part part : listPartsResponse.parts()) {
            uploadedParts.add(new UploadPartInfo(
                    part.partNumber(),
                    part.eTag(),
                    part.size()
            ));
        }

        return uploadedParts;
    }

    public CompleteUploadResponse completeUpload(CompleteUploadRequest request) {
        List<CompletedPart> completedParts = new ArrayList<>();
        for (PartETag partETag : request.getParts()) {
            completedParts.add(CompletedPart.builder()
                    .partNumber(partETag.getPartNumber())
                    .eTag(partETag.getETag())
                    .build());
        }

        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .uploadId(request.getUploadId())
                .multipartUpload(completedUpload)
                .build();

        CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);

        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .build();

        HeadObjectResponse headResponse = s3Client.headObject(headRequest);

        VideoRecording recording = new VideoRecording();
        recording.setFilename(request.getObjectKey().substring(request.getObjectKey().lastIndexOf("/") + 1));
        recording.setSize(headResponse.contentLength());
        recording.setObjectKey(request.getObjectKey());
        recording.setStatus("COMPLETED");
        recording.setChecksum(completeResponse.eTag());

        VideoRecording savedRecording = videoRecordingRepository.save(recording);

        String downloadUrl = generateDownloadUrl(request.getObjectKey());

        return new CompleteUploadResponse(
                savedRecording.getId(),
                savedRecording.getFilename(),
                savedRecording.getSize(),
                savedRecording.getObjectKey(),
                savedRecording.getStatus(),
                downloadUrl,
                savedRecording.getCreatedAt()
        );
    }

    public void abortUpload(AbortUploadRequest request) {
        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .uploadId(request.getUploadId())
                .build();

        s3Client.abortMultipartUpload(abortRequest);
    }

    private String generateDownloadUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(uploadConfig.getPresignedUrlExpirationMinutes()))
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }
}
