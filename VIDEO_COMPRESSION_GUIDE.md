# FFmpeg Video Compression Service Guide

## Overview

This guide provides comprehensive documentation for the FFmpeg video compression service implemented in the screen-record-upload-be backend project. The service supports MP4 video compression with customizable parameters and optimized settings for screen recordings.

## Features

- **FFmpeg Integration**: Uses JavaCV with FFmpeg for high-performance video processing
- **Flexible Configuration**: Multiple preset configurations and custom parameter support
- **Async Processing**: Supports both synchronous and asynchronous video compression
- **Progress Monitoring**: Real-time compression progress tracking
- **Docker Support**: Complete containerization with FFmpeg pre-installed
- **Error Handling**: Comprehensive error handling and logging

## API Endpoints

### 1. Compress Video (Synchronous)

```http
POST /api/video/compress
Content-Type: application/json

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

### 2. Compress Video (Asynchronous)

```http
POST /api/video/compress/async
Content-Type: application/json

{
  "inputFilePath": "/path/to/input/video.mp4",
  "preset": "high-quality"
}
```

### 3. Get Compression Progress

```http
GET /api/video/progress/{jobId}
```

### 4. Get Available Presets

```http
GET /api/video/presets
```

## Configuration Presets

### 1. High Quality
- **Video Bitrate**: 5 Mbps
- **Audio Bitrate**: 192 kbps
- **Resolution**: 1080p
- **Encoder Preset**: slow
- **CRF**: 18
- **Two-pass**: Yes
- **Use Case**: Best quality output for archival purposes

### 2. Balanced (Recommended)
- **Video Bitrate**: 2.5 Mbps
- **Audio Bitrate**: 128 kbps
- **Resolution**: 720p
- **Encoder Preset**: medium
- **CRF**: 23
- **Two-pass**: No
- **Use Case**: Good balance between quality and file size

### 3. High Compression
- **Video Bitrate**: 1 Mbps
- **Audio Bitrate**: 96 kbps
- **Resolution**: 480p
- **Encoder Preset**: fast
- **CRF**: 28
- **Two-pass**: No
- **Use Case**: Maximum compression for storage optimization

### 4. Screen Recording Optimized
- **Video Bitrate**: 2 Mbps
- **Audio Bitrate**: 128 kbps
- **Resolution**: 1080p
- **Encoder Preset**: medium
- **CRF**: 20
- **Two-pass**: No
- **Use Case**: Optimized for screen recording content

## FFmpeg Parameter Recommendations

### Video Codecs

| Codec | Quality | Speed | Compatibility | Recommended Use |
|-------|---------|-------|---------------|-----------------|
| libx264 (H.264) | Excellent | Fast | Universal | Default choice |
| libx265 (H.265) | Better | Slower | Modern devices | Future-proofing |
| libvpx-vp9 | Good | Slow | Web browsers | Web optimization |

### Encoder Preset Speed Levels

| Preset | Encoding Speed | Compression Efficiency | Quality |
|--------|---------------|----------------------|---------|
| ultrafast | Very Fast | Low | Lower |
| superfast | Very Fast | Low-Medium | Lower |
| veryfast | Fast | Medium | Medium |
| faster | Fast | Medium | Medium |
| fast | Medium | Medium-Good | Good |
| medium | Medium | Good | Good (Default) |
| slow | Slow | Good-Excellent | Excellent |
| slower | Slow | Excellent | Excellent |
| veryslow | Very Slow | Best | Best |

### CRF (Constant Rate Factor) Values

| CRF Range | Quality | File Size | Recommended Use |
|-----------|---------|-----------|-----------------|
| 0-17 | Excellent | Large | High-quality archival |
| 18-23 | Good | Medium | General use (Default: 23) |
| 24-28 | Fair | Small | Web/streaming |
| 29-35 | Poor | Very Small | Low bandwidth |
| 36-51 | Very Poor | Minimal | Testing only |

### Recommended Bitrates by Resolution

| Resolution | Recommended Bitrate | Minimum | Maximum |
|------------|---------------------|---------|---------|
| 480p (854x480) | 1-2 Mbps | 500 kbps | 3 Mbps |
| 720p (1280x720) | 2-5 Mbps | 1 Mbps | 8 Mbps |
| 1080p (1920x1080) | 5-10 Mbps | 2 Mbps | 15 Mbps |
| 1440p (2560x1440) | 8-15 Mbps | 4 Mbps | 25 Mbps |
| 4K (3840x2160) | 15-25 Mbps | 8 Mbps | 40 Mbps |

## Screen Recording Optimization

For screen recordings, use these optimized parameters:

- **Codec**: libx264
- **Preset**: medium
- **CRF**: 20
- **Tune**: animation (if available)
- **Profile**: high
- **Level**: 4.0

### Special Parameters for Screen Content

```bash
# Additional FFmpeg parameters for screen content
-tune animation
-crf 20
-preset medium
-profile:v high
-level 4.0
-movflags +faststart
-pix_fmt yuv420p
```

## Configuration Files

### application.yml

```yaml
video:
  compression:
    enabled: true
    temp-directory: /tmp/video-compression
    max-concurrent-jobs: 2
    max-file-size: 1073741824  # 1GB
    timeout-seconds: 300      # 5 minutes
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| VIDEO_TEMP_DIR | /tmp/video-compression | Temporary directory for processing |
| VIDEO_MAX_JOBS | 2 | Maximum concurrent compression jobs |
| VIDEO_MAX_SIZE | 1073741824 | Maximum file size (1GB) |
| VIDEO_TIMEOUT | 300 | Timeout in seconds |

## Docker Deployment

### Build and Run

```bash
# Build the Docker image
docker build -t screen-record-upload-be .

# Run with docker-compose
docker-compose up -d
```

### Resource Allocation

For optimal video compression performance:

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 4G
    reservations:
      cpus: '1.0'
      memory: 2G
```

### Performance Optimization

1. **CPU**: Allocate at least 2 CPU cores for concurrent processing
2. **Memory**: Minimum 2GB RAM, recommended 4GB
3. **Storage**: Use SSD for temporary directory I/O performance
4. **Network**: Ensure sufficient bandwidth for file transfers

## Usage Examples

### Example 1: Basic Compression with Preset

```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/video.mp4",
    "preset": "balanced"
  }'
```

### Example 2: Custom Parameters

```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/video.mp4",
    "videoBitrate": 3000000,
    "audioBitrate": 160000,
    "width": 1920,
    "height": 1080,
    "crf": 20,
    "encoderPreset": "slow"
  }'
```

### Example 3: Async Compression with Progress Monitoring

```bash
# Start async compression
curl -X POST http://localhost:8080/api/video/compress/async \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/video.mp4",
    "preset": "high-quality"
  }'

# Monitor progress (replace {jobId} with actual job ID)
curl http://localhost:8080/api/video/progress/{jobId}
```

## Error Handling

### Common Errors and Solutions

1. **File Not Found**: Ensure input file path is correct and accessible
2. **Memory Issues**: Increase JVM heap size or reduce concurrent jobs
3. **Timeout**: Increase timeout value for large files
4. **Format Not Supported**: Ensure input is a valid video format

### Error Response Format

```json
{
  "jobId": "uuid",
  "success": false,
  "errorMessage": "Input file does not exist: /path/to/file.mp4"
}
```

## Monitoring and Logging

### Log Levels

- **INFO**: Basic operation information
- **DEBUG**: Detailed processing information
- **ERROR**: Error messages and stack traces

### Key Metrics to Monitor

1. **Compression Success Rate**: Percentage of successful compressions
2. **Average Processing Time**: Time per compression job
3. **Resource Utilization**: CPU and memory usage
4. **Queue Length**: Number of pending jobs

## Best Practices

1. **File Management**: Clean up temporary files regularly
2. **Resource Limits**: Set appropriate limits for concurrent jobs
3. **Error Recovery**: Implement retry logic for transient failures
4. **Validation**: Validate input files before processing
5. **Monitoring**: Track performance metrics and error rates

## Troubleshooting

### Performance Issues

1. **Slow Compression**: Check CPU utilization and consider reducing concurrent jobs
2. **High Memory Usage**: Monitor JVM heap size and adjust if needed
3. **Disk Space**: Ensure sufficient space in temporary directory

### Quality Issues

1. **Poor Video Quality**: Lower CRF value or increase bitrate
2. **Audio Sync Issues**: Check original file integrity
3. **Artifacts**: Try different encoder presets or codecs

## Integration Examples

### Java Client Example

```java
@RestController
public class VideoUploadController {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public void compressUploadedVideo(String filePath) {
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath(filePath);
        request.setPreset("screen-recording");
        
        ResponseEntity<VideoCompressionResponse> response = restTemplate.postForEntity(
            "http://localhost:8080/api/video/compress/async", 
            request, 
            VideoCompressionResponse.class
        );
        
        if (response.getBody().isSuccess()) {
            String jobId = response.getBody().getJobId();
            // Monitor progress
            monitorProgress(jobId);
        }
    }
}
```

## Future Enhancements

1. **Batch Processing**: Support for multiple file compression
2. **Custom Profiles**: User-defined compression profiles
3. **Cloud Storage Integration**: Direct integration with S3/MinIO
4. **Web Interface**: Web-based video compression interface
5. **Advanced Filtering**: Video filtering and enhancement options

## Support

For issues and questions:

1. Check application logs for detailed error messages
2. Verify FFmpeg installation and version compatibility
3. Ensure sufficient system resources
4. Validate input file formats and permissions