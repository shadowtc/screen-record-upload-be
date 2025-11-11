# MinIO Multipart Upload Demo

A Spring Boot demo service for resumable multipart uploads to local MinIO with metadata persistence.

## Features

- Multipart upload initialization with configurable chunk size
- Presigned URL generation for client-side uploads
- Upload progress tracking
- Upload completion with metadata persistence
- Upload abortion and cleanup
- **FFmpeg Video Compression Service** - NEW!
  - MP4 video compression with customizable parameters
  - Multiple preset configurations (high-quality, balanced, high-compression, screen-recording)
  - Synchronous and asynchronous processing
  - Real-time compression progress monitoring
  - Optimized for screen recording content
- H2 in-memory database for demo purposes
- CORS enabled for cross-origin requests

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MinIO server running (configured endpoint)

## Configuration

The application can be configured via environment variables or `application.yml`:

### Environment Variables

```bash
S3_ENDPOINT=http://192.168.0.245:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin123
S3_BUCKET=remote-consent
S3_REGION=us-east-1
S3_PATH_STYLE=true
UPLOAD_MAX_SIZE=2147483648
UPLOAD_CHUNK_SIZE=8388608
PRESIGNED_URL_EXPIRATION=60

# Video Compression Configuration
VIDEO_TEMP_DIR=/tmp/video-compression
VIDEO_MAX_JOBS=2
VIDEO_MAX_SIZE=1073741824
VIDEO_TIMEOUT=300
```

### Default Configuration

The following defaults are set in `application.yml`:

- **MinIO Endpoint**: http://192.168.0.245:9000
- **Access Key**: minioadmin
- **Secret Key**: minioadmin123
- **Bucket**: remote-consent
- **Region**: us-east-1
- **Path Style Access**: true
- **Max File Size**: 2GB (2147483648 bytes)
- **Default Chunk Size**: 8MB (8388608 bytes)
- **Presigned URL Expiration**: 60 minutes

## Running the Application

### Using Maven

```bash
mvn spring-boot:run
```

### Using Java

```bash
mvn clean package
java -jar target/minio-multipart-upload-1.0.0.jar
```

The application will start on port 8080 by default.

## API Endpoints

### 1. Initialize Multipart Upload

**Endpoint**: `POST /api/uploads/init`

Initializes a multipart upload and returns upload metadata.

**Request Body**:
```json
{
  "fileName": "video.mp4",
  "size": 524288000,
  "contentType": "video/mp4",
  "chunkSize": 8388608
}
```

**Response**:
```json
{
  "uploadId": "abc123xyz",
  "objectKey": "uploads/uuid-here/video.mp4",
  "partSize": 8388608,
  "minPartNumber": 1,
  "maxPartNumber": 63
}
```

**cURL Example**:
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

### 2. Get Presigned URLs for Parts

**Endpoint**: `GET /api/uploads/{uploadId}/parts`

Generates presigned PUT URLs for uploading specific parts.

**Query Parameters**:
- `objectKey`: The object key returned from init
- `startPartNumber`: Starting part number (inclusive)
- `endPartNumber`: Ending part number (inclusive)

**Response**:
```json
[
  {
    "partNumber": 1,
    "url": "https://...",
    "expiresAt": "2024-01-01T12:00:00Z"
  },
  {
    "partNumber": 2,
    "url": "https://...",
    "expiresAt": "2024-01-01T12:00:00Z"
  }
]
```

**cURL Example**:
```bash
curl -X GET "http://localhost:8080/api/uploads/abc123xyz/parts?objectKey=uploads/uuid/video.mp4&startPartNumber=1&endPartNumber=5"
```

### 3. Upload Part Using Presigned URL

Use the presigned URL to upload a part directly to MinIO:

**cURL Example**:
```bash
# Upload part 1
curl -X PUT "PRESIGNED_URL_HERE" \
  --upload-file part1.bin \
  -v
```

The response will include an `ETag` header which you need to save for completion.

### 4. Check Upload Status

**Endpoint**: `GET /api/uploads/{uploadId}/status`

Lists all successfully uploaded parts.

**Query Parameters**:
- `objectKey`: The object key returned from init

**Response**:
```json
[
  {
    "partNumber": 1,
    "etag": "abc123",
    "size": 8388608
  },
  {
    "partNumber": 2,
    "etag": "def456",
    "size": 8388608
  }
]
```

**cURL Example**:
```bash
curl -X GET "http://localhost:8080/api/uploads/abc123xyz/status?objectKey=uploads/uuid/video.mp4"
```

### 5. Complete Upload

**Endpoint**: `POST /api/uploads/complete`

Completes the multipart upload and persists metadata.

**Request Body**:
```json
{
  "uploadId": "abc123xyz",
  "objectKey": "uploads/uuid/video.mp4",
  "parts": [
    {
      "partNumber": 1,
      "eTag": "abc123"
    },
    {
      "partNumber": 2,
      "eTag": "def456"
    }
  ]
}
```

**Response**:
```json
{
  "id": 1,
  "filename": "video.mp4",
  "size": 524288000,
  "objectKey": "uploads/uuid/video.mp4",
  "status": "COMPLETED",
  "downloadUrl": "https://...",
  "createdAt": "2024-01-01T10:00:00"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/uploads/complete \
  -H "Content-Type: application/json" \
  -d '{
    "uploadId": "abc123xyz",
    "objectKey": "uploads/uuid/video.mp4",
    "parts": [
      {"partNumber": 1, "eTag": "abc123"},
      {"partNumber": 2, "eTag": "def456"}
    ]
  }'
```

### 6. Abort Upload

**Endpoint**: `POST /api/uploads/abort`

Aborts an in-progress multipart upload and cleans up temporary data.

**Request Body**:
```json
{
  "uploadId": "abc123xyz",
  "objectKey": "uploads/uuid/video.mp4"
}
```

**Response**: 204 No Content

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/uploads/abort \
  -H "Content-Type: application/json" \
  -d '{
    "uploadId": "abc123xyz",
    "objectKey": "uploads/uuid/video.mp4"
  }'
```

## Complete Upload Workflow Example

Here's a complete workflow for uploading a 500MB video file:

### Step 1: Initialize Upload

```bash
INIT_RESPONSE=$(curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "test-video.mp4",
    "size": 524288000,
    "contentType": "video/mp4",
    "chunkSize": 8388608
  }')

echo $INIT_RESPONSE
```

Extract `uploadId`, `objectKey`, and `maxPartNumber` from the response.

### Step 2: Split File into Chunks

```bash
# Split the file into 8MB chunks
split -b 8388608 test-video.mp4 part_
```

### Step 3: Get Presigned URLs and Upload Parts

```bash
UPLOAD_ID="your-upload-id"
OBJECT_KEY="your-object-key"

# Get presigned URLs for parts 1-10
URLS=$(curl -X GET "http://localhost:8080/api/uploads/${UPLOAD_ID}/parts?objectKey=${OBJECT_KEY}&startPartNumber=1&endPartNumber=10")

# Upload each part and capture ETags
ETAG_1=$(curl -X PUT "PRESIGNED_URL_PART_1" \
  --upload-file part_aa \
  -v 2>&1 | grep -i "etag" | cut -d'"' -f2)

ETAG_2=$(curl -X PUT "PRESIGNED_URL_PART_2" \
  --upload-file part_ab \
  -v 2>&1 | grep -i "etag" | cut -d'"' -f2)

# Repeat for all parts...
```

### Step 4: Check Upload Status

```bash
curl -X GET "http://localhost:8080/api/uploads/${UPLOAD_ID}/status?objectKey=${OBJECT_KEY}"
```

### Step 5: Complete Upload

```bash
curl -X POST http://localhost:8080/api/uploads/complete \
  -H "Content-Type: application/json" \
  -d '{
    "uploadId": "'${UPLOAD_ID}'",
    "objectKey": "'${OBJECT_KEY}'",
    "parts": [
      {"partNumber": 1, "eTag": "'${ETAG_1}'"},
      {"partNumber": 2, "eTag": "'${ETAG_2}'"}
    ]
  }'
```

## Testing with Postman

1. **Import the Collection**: Create a new collection in Postman

2. **Environment Variables**: Set up the following variables:
   - `base_url`: http://localhost:8080
   - `upload_id`: (will be set from init response)
   - `object_key`: (will be set from init response)

3. **Test Sequence**:
   - Call `POST /api/uploads/init`
   - Save `uploadId` and `objectKey` to variables
   - Call `GET /api/uploads/{uploadId}/parts`
   - Use returned URLs to upload parts
   - Call `GET /api/uploads/{uploadId}/status` to verify
   - Call `POST /api/uploads/complete` with all part ETags

## Video Compression API

### 1. Compress Video (Synchronous)

**Endpoint**: `POST /api/video/compress`

Compresses a video file synchronously and returns the result.

**Request Body**:
```json
{
  "inputFilePath": "/path/to/input/video.mp4",
  "preset": "balanced",
  "videoBitrate": 2500000,
  "audioBitrate": 128000,
  "width": 1280,
  "height": 720,
  "crf": 23,
  "encoderPreset": "medium",
  "outputFormat": "mp4",
  "twoPass": false,
  "deleteOriginal": false
}
```

**Response**:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "outputFilePath": "/tmp/video-compression/video_compressed_balanced.mp4",
  "originalSize": 104857600,
  "compressedSize": 52428800,
  "compressionRatio": 50.0,
  "originalDuration": 120.5,
  "compressionTimeMs": 15000,
  "status": "completed",
  "timestamp": 1699123456789
}
```

### 2. Compress Video (Asynchronous)

**Endpoint**: `POST /api/video/compress/async`

Starts video compression asynchronously and returns a job ID for monitoring.

**Request Body**: Same as synchronous compression

**Response**:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440001",
  "success": true,
  "status": "processing",
  "timestamp": 1699123456789
}
```

### 3. Get Compression Progress

**Endpoint**: `GET /api/video/progress/{jobId}`

Returns the current progress of an asynchronous compression job.

**Response**:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440001",
  "progress": 45.5,
  "status": "Compressing...",
  "timestamp": 1699123490123
}
```

### 4. Get Available Presets

**Endpoint**: `GET /api/video/presets`

Returns available compression presets and options.

**Response**:
```json
{
  "presets": ["high-quality", "balanced", "high-compression", "screen-recording"],
  "resolutions": ["480p", "720p", "1080p", "1440p", "4k"],
  "encoderPresets": ["ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow"]
}
```

### Video Compression Examples

#### Basic Compression with Preset
```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/video.mp4",
    "preset": "screen-recording"
  }'
```

#### Custom Parameters
```bash
curl -X POST http://localhost:8080/api/video/compress/async \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/presentation.mp4",
    "videoBitrate": 3000000,
    "audioBitrate": 160000,
    "width": 1920,
    "height": 1080,
    "crf": 20,
    "encoderPreset": "slow"
  }'
```

#### Monitor Progress
```bash
JOB_ID="your-job-id"
curl http://localhost:8080/api/video/progress/$JOB_ID
```

### Compression Presets

| Preset | Video Bitrate | Audio Bitrate | Resolution | Quality | Use Case |
|--------|---------------|---------------|------------|---------|----------|
| high-quality | 5 Mbps | 192 kbps | 1080p | Excellent | Archival purposes |
| balanced | 2.5 Mbps | 128 kbps | 720p | Good | General use |
| high-compression | 1 Mbps | 96 kbps | 480p | Fair | Storage optimization |
| screen-recording | 2 Mbps | 128 kbps | 1080p | Good | Screen content |

For detailed documentation, see [VIDEO_COMPRESSION_GUIDE.md](./VIDEO_COMPRESSION_GUIDE.md)

## Database

The application uses H2 in-memory database for demo purposes. You can access the H2 console at:

```
http://localhost:8080/h2-console
```

**Connection Details**:
- JDBC URL: `jdbc:h2:mem:uploaddb`
- Username: `sa`
- Password: (empty)

## Running Tests

```bash
mvn test
```

## Validation Rules

- **Content Type**: Must start with `video/`
- **File Size**: Maximum 2GB (configurable)
- **Chunk Size**: Defaults to 8MB if not specified
- **Part Numbers**: Must be positive integers
- **ETags**: Required for completion

## Error Handling

The API returns appropriate HTTP status codes and error messages:

- **400 Bad Request**: Validation errors or invalid input
- **500 Internal Server Error**: S3/MinIO operation failures or unexpected errors

Example error response:
```json
{
  "error": "Only video files are allowed"
}
```

## Security Notes

This is a **demo application** with the following limitations:

- No authentication/authorization
- CORS allows all origins
- Credentials in configuration files
- H2 in-memory database (data lost on restart)

For production use, implement:
- Proper authentication (JWT, OAuth2, etc.)
- Restricted CORS policies
- Secure credential management (AWS Secrets Manager, etc.)
- Persistent database (PostgreSQL, MySQL, etc.)
- Rate limiting and request validation
- File scanning and virus detection

## Troubleshooting

### Connection Issues

If you cannot connect to MinIO:

1. Verify MinIO is running: `curl http://192.168.0.245:9000`
2. Check bucket exists: `mc ls myminio/remote-consent`
3. Verify credentials are correct
4. Ensure path-style access is enabled

### Upload Failures

- Check part numbers are sequential
- Verify ETags match exactly (including quotes if present)
- Ensure file size matches declared size
- Check presigned URLs haven't expired (60 min default)

### Database Issues

- H2 console: http://localhost:8080/h2-console
- Check schema auto-creation is enabled
- Verify JPA entity mapping

## License

This is a demo application for educational purposes.
