package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import com.example.minioupload.config.UploadConfigProperties;
import com.example.minioupload.dto.*;
import com.example.minioupload.model.VideoRecording;
import com.example.minioupload.repository.VideoRecordingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultipartUploadServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3ConfigProperties s3Config;

    @Mock
    private UploadConfigProperties uploadConfig;

    @Mock
    private VideoRecordingRepository videoRecordingRepository;

    private MultipartUploadService multipartUploadService;

    @BeforeEach
    void setUp() {
        when(s3Config.getBucket()).thenReturn("test-bucket");
        when(uploadConfig.getMaxFileSize()).thenReturn(2147483648L);
        when(uploadConfig.getDefaultChunkSize()).thenReturn(8388608L);
        when(uploadConfig.getPresignedUrlExpirationMinutes()).thenReturn(60);

        multipartUploadService = new MultipartUploadService(
                s3Client,
                s3Presigner,
                s3Config,
                uploadConfig,
                videoRecordingRepository
        );
    }

    @Test
    void testInitializeUpload() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-video.mp4");
        request.setSize(100000000L);
        request.setContentType("video/mp4");
        request.setChunkSize(8388608L);

        CreateMultipartUploadResponse mockResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(mockResponse);

        InitUploadResponse response = multipartUploadService.initializeUpload(request);

        assertNotNull(response);
        assertEquals("test-upload-id", response.getUploadId());
        assertNotNull(response.getObjectKey());
        assertEquals(8388608L, response.getPartSize());
        assertEquals(1, response.getMinPartNumber());
        assertEquals(12, response.getMaxPartNumber());

        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void testInitializeUpload_InvalidContentType() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-file.txt");
        request.setSize(100000L);
        request.setContentType("text/plain");

        assertThrows(IllegalArgumentException.class, () -> {
            multipartUploadService.initializeUpload(request);
        });
    }

    @Test
    void testInitializeUpload_FileTooLarge() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-video.mp4");
        request.setSize(3000000000L);
        request.setContentType("video/mp4");

        assertThrows(IllegalArgumentException.class, () -> {
            multipartUploadService.initializeUpload(request);
        });
    }

    @Test
    void testGeneratePresignedUrls() throws MalformedURLException {
        PresignedUploadPartRequest mockPresignedRequest = mock(PresignedUploadPartRequest.class);
        when(mockPresignedRequest.url()).thenReturn(new URL("https://test-bucket.s3.amazonaws.com/test"));

        when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        List<PresignedUrlResponse> presignedUrls = multipartUploadService.generatePresignedUrls(
                "test-upload-id",
                "test-object-key",
                1,
                3
        );

        assertNotNull(presignedUrls);
        assertEquals(3, presignedUrls.size());
        assertEquals(1, presignedUrls.get(0).getPartNumber());
        assertEquals(2, presignedUrls.get(1).getPartNumber());
        assertEquals(3, presignedUrls.get(2).getPartNumber());

        verify(s3Presigner, times(3)).presignUploadPart(any(UploadPartPresignRequest.class));
    }

    @Test
    void testGetUploadStatus() {
        Part part1 = Part.builder()
                .partNumber(1)
                .eTag("etag1")
                .size(8388608L)
                .build();

        Part part2 = Part.builder()
                .partNumber(2)
                .eTag("etag2")
                .size(8388608L)
                .build();

        ListPartsResponse mockResponse = ListPartsResponse.builder()
                .parts(Arrays.asList(part1, part2))
                .build();

        when(s3Client.listParts(any(ListPartsRequest.class))).thenReturn(mockResponse);

        List<UploadPartInfo> uploadedParts = multipartUploadService.getUploadStatus(
                "test-upload-id",
                "test-object-key"
        );

        assertNotNull(uploadedParts);
        assertEquals(2, uploadedParts.size());
        assertEquals(1, uploadedParts.get(0).getPartNumber());
        assertEquals("etag1", uploadedParts.get(0).getEtag());
        assertEquals(2, uploadedParts.get(1).getPartNumber());
        assertEquals("etag2", uploadedParts.get(1).getEtag());

        verify(s3Client, times(1)).listParts(any(ListPartsRequest.class));
    }

    @Test
    void testCompleteUpload() {
        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setUploadId("test-upload-id");
        request.setObjectKey("uploads/test-uuid/test-video.mp4");

        PartETag part1 = new PartETag(1, "etag1");
        PartETag part2 = new PartETag(2, "etag2");
        request.setParts(Arrays.asList(part1, part2));

        CompleteMultipartUploadResponse mockCompleteResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        HeadObjectResponse mockHeadResponse = HeadObjectResponse.builder()
                .contentLength(100000000L)
                .build();

        VideoRecording savedRecording = new VideoRecording();
        savedRecording.setId(1L);
        savedRecording.setFilename("test-video.mp4");
        savedRecording.setSize(100000000L);
        savedRecording.setObjectKey("uploads/test-uuid/test-video.mp4");
        savedRecording.setStatus("COMPLETED");

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(mockCompleteResponse);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(mockHeadResponse);
        when(videoRecordingRepository.save(any(VideoRecording.class)))
                .thenReturn(savedRecording);

        CompleteUploadResponse response = multipartUploadService.completeUpload(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test-video.mp4", response.getFilename());
        assertEquals(100000000L, response.getSize());
        assertEquals("COMPLETED", response.getStatus());
        assertNotNull(response.getDownloadUrl());

        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
        verify(videoRecordingRepository, times(1)).save(any(VideoRecording.class));
    }

    @Test
    void testAbortUpload() {
        AbortUploadRequest request = new AbortUploadRequest();
        request.setUploadId("test-upload-id");
        request.setObjectKey("test-object-key");

        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenReturn(AbortMultipartUploadResponse.builder().build());

        multipartUploadService.abortUpload(request);

        verify(s3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }
}
