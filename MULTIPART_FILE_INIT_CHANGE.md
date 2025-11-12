# MinIO Upload Init - MultipartFile Change

## Overview

This document describes the change to the MinIO multipart upload initialization API to use `MultipartFile` instead of individual JSON request parameters.

## Change Summary

### Before (deprecated)
The `/api/uploads/init` endpoint accepted a JSON request body with file metadata:

```bash
curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "test-video.mp4",
    "size": 524288000,
    "contentType": "video/mp4",
    "chunkSize": 8388608
  }'
```

### After (current)
The `/api/uploads/init` endpoint now accepts a multipart form request with the actual file:

```bash
curl -X POST http://localhost:8080/api/uploads/init \
  -F "file=@test-video.mp4" \
  -F "chunkSize=8388608"
```

## Benefits

1. **Automatic Metadata Extraction**: File name, size, and content type are automatically extracted from the uploaded file instead of requiring client-side computation.

2. **Simplified API**: Clients no longer need to calculate file size and content type separately.

3. **Reduced Errors**: Eliminates mismatches between declared and actual file metadata.

4. **Better Security**: Content type validation is based on the actual uploaded file, not client declaration.

5. **Cleaner API**: Single file upload parameter instead of separate metadata fields.

## Modified Files

### DTOs
- **InitUploadRequest.java**: Changed from `String fileName`, `Long size`, `String contentType` to `MultipartFile file` and optional `Long chunkSize`

### Controllers
- **MultipartUploadController.java**: 
  - Changed endpoint from `@RequestBody` to `@RequestPart("file")` and `@RequestParam("chunkSize")`
  - Now accepts multipart form data instead of JSON

### Services
- **MultipartUploadService.java**: 
  - Updated `initializeUpload()` to extract metadata from `MultipartFile`
  - Added validation for file name, content type, and size

## Migration Guide

### For Java Clients

```java
// Old way (deprecated)
InitUploadRequest request = new InitUploadRequest();
request.setFileName("video.mp4");
request.setSize(524288000L);
request.setContentType("video/mp4");
request.setChunkSize(8388608L);

// New way
// Use RestTemplate or WebClient with multipart upload
RestTemplate restTemplate = new RestTemplate();
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource("video.mp4"));
body.add("chunkSize", 8388608);
HttpEntity<MultiValueMap<String, Object>> entity = 
    new HttpEntity<>(body, new HttpHeaders());
InitUploadResponse response = restTemplate.postForObject(
    "http://localhost:8080/api/uploads/init",
    entity,
    InitUploadResponse.class
);
```

### For JavaScript/TypeScript Clients

```javascript
// Old way (deprecated)
const response = await fetch('http://localhost:8080/api/uploads/init', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    fileName: 'video.mp4',
    size: 524288000,
    contentType: 'video/mp4',
    chunkSize: 8388608
  })
});

// New way
const formData = new FormData();
formData.append('file', videoFile);
formData.append('chunkSize', 8388608);
const response = await fetch('http://localhost:8080/api/uploads/init', {
  method: 'POST',
  body: formData
});
```

### For cURL

```bash
# Old way (deprecated)
curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "video.mp4",
    "size": 524288000,
    "contentType": "video/mp4",
    "chunkSize": 8388608
  }'

# New way
curl -X POST http://localhost:8080/api/uploads/init \
  -F "file=@video.mp4" \
  -F "chunkSize=8388608"
```

## Request Parameters

### Form Data (multipart/form-data)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| file | File | Yes | The video file to upload |
| chunkSize | Long | No | Custom chunk size in bytes (min: 5MB, max: 5GB) |

## Response

Same as before - returns an `InitUploadResponse` object:

```json
{
  "uploadId": "abc123xyz",
  "objectKey": "uploads/uuid-here/video.mp4",
  "partSize": 8388608,
  "minPartNumber": 1,
  "maxPartNumber": 63
}
```

## File Type Validation

Only video files are accepted (content type must start with `video/`):
- ✅ video/mp4
- ✅ video/x-msvideo
- ✅ video/quicktime
- ❌ audio/mp3
- ❌ text/plain

## Error Handling

If the file is invalid, you'll receive a `400 Bad Request` with one of these messages:
- "File name cannot be empty"
- "Only video files are allowed"
- "File size exceeds maximum allowed size"
- "Chunk size must be at least 5MB (5242880 bytes)"
- "Chunk size cannot exceed 5GB (5368709120 bytes)"

## Documentation Updates

The following documentation files have been updated to reflect this change:

- README.md
- QUICK_START.md
- IMPLEMENTATION_SUMMARY.md
- postman-collection.json
- test-upload.sh

## Testing

Use the provided test script:

```bash
./test-upload.sh /path/to/video.mp4
```

Or import the updated Postman collection:

1. Open Postman
2. Import `postman-collection.json`
3. Set the file path in the "1. Initialize Upload" request
4. Click Send

## Backward Compatibility

⚠️ **Breaking Change**: The old JSON-based API is no longer supported. All clients must be updated to use the new multipart form data format.

## Additional Notes

- File validation is performed server-side based on content type and file extension
- The API automatically extracts metadata from the uploaded file
- Chunk size must be between 5MB and 5GB as required by S3/MinIO
- If no chunk size is specified, the server default (8MB) is used
