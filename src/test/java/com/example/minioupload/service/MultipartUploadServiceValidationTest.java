//package com.example.minioupload.service;
//
//import com.example.minioupload.dto.PartETag;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 专门测试MultipartUploadService中验证逻辑的单元测试
// */
//@ExtendWith(MockitoExtension.class)
//class MultipartUploadServiceValidationTest {
//
//    @InjectMocks
//    private MultipartUploadService multipartUploadService;
//
//    @Test
//    void testValidateParts_ValidSequentialParts() {
//        // 测试有效的连续分片
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(1, "etag1"),
//                new PartETag(2, "etag2"),
//                new PartETag(3, "etag3")
//        );
//
//        // 应该不抛出异常
//        assertDoesNotThrow(() -> {
//            // 使用反射调用私有方法进行测试
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//    }
//
//    @Test
//    void testValidateParts_EmptyList() {
//        // 测试空列表
//        List<PartETag> parts = Collections.emptyList();
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("cannot be null or empty"));
//    }
//
//    @Test
//    void testValidateParts_NegativePartNumber() {
//        // 测试负数分片编号
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(-1, "etag1"),
//                new PartETag(2, "etag2")
//        );
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("must be positive"));
//    }
//
//    @Test
//    void testValidateParts_EmptyETag() {
//        // 测试空ETag
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(1, ""),
//                new PartETag(2, "etag2")
//        );
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("ETag cannot be null or empty"));
//    }
//
//    @Test
//    void testValidateParts_DuplicatePartNumbers() {
//        // 测试重复的分片编号
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(1, "etag1"),
//                new PartETag(1, "etag2")
//        );
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("Duplicate part number"));
//    }
//
//    @Test
//    void testValidateParts_NonConsecutiveParts() {
//        // 测试不连续的分片编号
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(1, "etag1"),
//                new PartETag(3, "etag3"),
//                new PartETag(4, "etag4")
//        );
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("must be consecutive"));
//    }
//
//    @Test
//    void testValidateParts_NotStartingFromOne() {
//        // 测试不从1开始的分片编号
//        List<PartETag> parts = Arrays.asList(
//                new PartETag(2, "etag2"),
//                new PartETag(3, "etag3"),
//                new PartETag(4, "etag4")
//        );
//
//        Exception exception = assertThrows(Exception.class, () -> {
//            java.lang.reflect.Method validateParts = MultipartUploadService.class
//                    .getDeclaredMethod("validateParts", List.class);
//            validateParts.setAccessible(true);
//            validateParts.invoke(multipartUploadService, parts);
//        });
//
//        assertTrue(exception.getCause() instanceof IllegalArgumentException);
//        assertTrue(exception.getCause().getMessage().contains("must be consecutive"));
//    }
//}