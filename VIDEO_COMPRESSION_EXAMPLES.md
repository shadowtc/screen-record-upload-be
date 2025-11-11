# Video Compression Usage Examples

This document provides practical examples for using the video compression service.

## Quick Start Examples

### 1. Basic Compression with Preset

#### Request
```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/screen-recording.mp4",
    "preset": "balanced"
  }'
```

#### Response
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "outputFilePath": "/tmp/video-compression/screen-recording_compressed_balanced.mp4",
  "originalSize": 104857600,
  "compressedSize": 52428800,
  "compressionRatio": 50.0,
  "originalDuration": 120.5,
  "compressionTimeMs": 15000,
  "status": "completed",
  "timestamp": 1699123456789
}
```

### 2. Custom Compression Parameters

#### Request
```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/presentation.mp4",
    "videoBitrate": 3000000,
    "audioBitrate": 160000,
    "width": 1920,
    "height": 1080,
    "crf": 20,
    "encoderPreset": "slow",
    "outputFormat": "mp4",
    "twoPass": true
  }'
```

#### Response
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440001",
  "success": true,
  "outputFilePath": "/tmp/video-compression/presentation_compressed.mp4",
  "originalSize": 209715200,
  "compressedSize": 78643200,
  "compressionRatio": 62.5,
  "originalDuration": 300.0,
  "compressionTimeMs": 45000,
  "settings": {
    "videoCodec": "libx264",
    "audioCodec": "aac",
    "videoBitrate": 3000000,
    "audioBitrate": 160000,
    "width": 1920,
    "height": 1080,
    "crf": 20,
    "preset": "slow",
    "twoPass": true,
    "threads": 0
  },
  "videoInfo": {
    "duration": 300.0,
    "width": 1920,
    "height": 1080,
    "frameRate": 30.0,
    "videoBitrate": 2980000,
    "audioBitrate": 128000,
    "videoCodec": "h264",
    "audioCodec": "aac",
    "fileSize": 78643200,
    "format": "mp4"
  },
  "status": "completed",
  "timestamp": 1699123456789
}
```

### 3. Asynchronous Compression with Progress Monitoring

#### Start Async Compression
```bash
curl -X POST http://localhost:8080/api/video/compress/async \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/uploads/long-video.mp4",
    "preset": "high-quality"
  }'
```

#### Initial Response
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440002",
  "success": true,
  "status": "processing",
  "timestamp": 1699123456789
}
```

#### Monitor Progress
```bash
curl http://localhost:8080/api/video/progress/550e8400-e29b-41d4-a716-446655440002
```

#### Progress Response
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440002",
  "progress": 45.5,
  "status": "Compressing...",
  "timestamp": 1699123490123
}
```

## Programming Language Examples

### Java/Spring Boot Integration

#### Service Integration
```java
@Service
@RequiredArgsConstructor
public class VideoProcessingService {
    
    private final RestTemplate restTemplate;
    private final String compressionServiceUrl = "http://localhost:8080/api/video";
    
    public CompletableFuture<String> compressVideoAsync(String inputPath, String preset) {
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath(inputPath);
        request.setPreset(preset);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<VideoCompressionResponse> response = restTemplate.postForEntity(
                    compressionServiceUrl + "/compress/async",
                    request,
                    VideoCompressionResponse.class
                );
                
                if (response.getBody().isSuccess()) {
                    return response.getBody().getJobId();
                } else {
                    throw new RuntimeException("Compression failed: " + response.getBody().getErrorMessage());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to start compression", e);
            }
        });
    }
    
    public CompressionProgress getProgress(String jobId) {
        try {
            ResponseEntity<CompressionProgress> response = restTemplate.getForEntity(
                compressionServiceUrl + "/progress/" + jobId,
                CompressionProgress.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get progress", e);
        }
    }
    
    @Scheduled(fixedDelay = 5000) // Check every 5 seconds
    public void monitorCompressionJobs() {
        // Implement logic to monitor active jobs
    }
}
```

#### Controller Example
```java
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class VideoUploadController {
    
    private final VideoProcessingService videoProcessingService;
    
    @PostMapping("/compress")
    public ResponseEntity<Map<String, String>> uploadAndCompress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "balanced") String preset) {
        
        try {
            // Save uploaded file
            String inputPath = saveUploadedFile(file);
            
            // Start compression
            CompletableFuture<String> jobIdFuture = videoProcessingService.compressVideoAsync(inputPath, preset);
            String jobId = jobIdFuture.get();
            
            return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "processing",
                "message", "Video compression started"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to process video: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/compress/{jobId}/status")
    public ResponseEntity<CompressionProgress> getCompressionStatus(@PathVariable String jobId) {
        CompressionProgress progress = videoProcessingService.getProgress(jobId);
        
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    private String saveUploadedFile(MultipartFile file) throws IOException {
        String uploadDir = "/uploads";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, fileName);
        Files.copy(file.getInputStream(), filePath);
        
        return filePath.toString();
    }
}
```

### Python Integration

#### Using requests library
```python
import requests
import time
import json

class VideoCompressionClient:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
        self.api_url = f"{base_url}/api/video"
    
    def compress_video_sync(self, input_path, preset="balanced", **kwargs):
        """Compress video synchronously"""
        url = f"{self.api_url}/compress"
        
        payload = {
            "inputFilePath": input_path,
            "preset": preset,
            **kwargs
        }
        
        response = requests.post(url, json=payload)
        response.raise_for_status()
        
        return response.json()
    
    def compress_video_async(self, input_path, preset="balanced", **kwargs):
        """Compress video asynchronously"""
        url = f"{self.api_url}/compress/async"
        
        payload = {
            "inputFilePath": input_path,
            "preset": preset,
            **kwargs
        }
        
        response = requests.post(url, json=payload)
        response.raise_for_status()
        
        return response.json()
    
    def get_progress(self, job_id):
        """Get compression progress"""
        url = f"{self.api_url}/progress/{job_id}"
        
        response = requests.get(url)
        if response.status_code == 404:
            return None
        
        response.raise_for_status()
        return response.json()
    
    def wait_for_completion(self, job_id, check_interval=5, timeout=300):
        """Wait for compression to complete"""
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            progress = self.get_progress(job_id)
            
            if progress is None:
                raise Exception(f"Job {job_id} not found")
            
            if progress.isComplete():
                return progress
            
            if progress.isError():
                raise Exception(f"Compression failed: {progress.status}")
            
            print(f"Progress: {progress.progress:.1f}% - {progress.status}")
            time.sleep(check_interval)
        
        raise TimeoutError(f"Compression timed out after {timeout} seconds")
    
    def compress_and_wait(self, input_path, preset="balanced", **kwargs):
        """Compress video and wait for completion"""
        # Start async compression
        result = self.compress_video_async(input_path, preset, **kwargs)
        job_id = result["jobId"]
        
        # Wait for completion
        final_progress = self.wait_for_completion(job_id)
        
        return {
            "jobId": job_id,
            "success": True,
            "message": "Compression completed successfully"
        }

# Usage example
if __name__ == "__main__":
    client = VideoCompressionClient()
    
    # Example 1: Synchronous compression
    try:
        result = client.compress_video_sync(
            input_path="/uploads/video.mp4",
            preset="high-quality"
        )
        print("Compression completed:", result)
    except Exception as e:
        print("Compression failed:", e)
    
    # Example 2: Asynchronous compression with monitoring
    try:
        result = client.compress_and_wait(
            input_path="/uploads/long-video.mp4",
            preset="balanced",
            videoBitrate=2500000,
            audioBitrate=128000
        )
        print("Final result:", result)
    except Exception as e:
        print("Compression failed:", e)
```

### JavaScript/Node.js Integration

#### Using axios
```javascript
const axios = require('axios');

class VideoCompressionClient {
    constructor(baseUrl = 'http://localhost:8080') {
        this.baseUrl = baseUrl;
        this.apiUrl = `${baseUrl}/api/video`;
    }

    async compressVideoSync(inputPath, preset = 'balanced', options = {}) {
        const payload = {
            inputFilePath: inputPath,
            preset,
            ...options
        };

        try {
            const response = await axios.post(`${this.apiUrl}/compress`, payload);
            return response.data;
        } catch (error) {
            throw new Error(`Compression failed: ${error.response?.data?.errorMessage || error.message}`);
        }
    }

    async compressVideoAsync(inputPath, preset = 'balanced', options = {}) {
        const payload = {
            inputFilePath: inputPath,
            preset,
            ...options
        };

        try {
            const response = await axios.post(`${this.apiUrl}/compress/async`, payload);
            return response.data;
        } catch (error) {
            throw new Error(`Failed to start compression: ${error.response?.data?.errorMessage || error.message}`);
        }
    }

    async getProgress(jobId) {
        try {
            const response = await axios.get(`${this.apiUrl}/progress/${jobId}`);
            return response.data;
        } catch (error) {
            if (error.response?.status === 404) {
                return null;
            }
            throw new Error(`Failed to get progress: ${error.message}`);
        }
    }

    async waitForCompletion(jobId, checkInterval = 5000, timeout = 300000) {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            const progress = await this.getProgress(jobId);

            if (!progress) {
                throw new Error(`Job ${jobId} not found`);
            }

            if (progress.isComplete) {
                return progress;
            }

            if (progress.isError) {
                throw new Error(`Compression failed: ${progress.status}`);
            }

            console.log(`Progress: ${progress.progress.toFixed(1)}% - ${progress.status}`);
            await new Promise(resolve => setTimeout(resolve, checkInterval));
        }

        throw new Error(`Compression timed out after ${timeout}ms`);
    }

    async compressAndWait(inputPath, preset = 'balanced', options = {}) {
        const result = await this.compressVideoAsync(inputPath, preset, options);
        const finalProgress = await this.waitForCompletion(result.jobId);

        return {
            jobId: result.jobId,
            success: true,
            message: 'Compression completed successfully'
        };
    }
}

// Usage example
async function main() {
    const client = new VideoCompressionClient();

    try {
        // Example 1: Synchronous compression
        const result1 = await client.compressVideoSync(
            '/uploads/video.mp4',
            'screen-recording'
        );
        console.log('Sync compression result:', result1);

        // Example 2: Asynchronous compression with monitoring
        const result2 = await client.compressAndWait(
            '/uploads/presentation.mp4',
            'high-quality',
            {
                videoBitrate: 4000000,
                audioBitrate: 192000,
                crf: 18
            }
        );
        console.log('Async compression result:', result2);

    } catch (error) {
        console.error('Error:', error.message);
    }
}

if (require.main === module) {
    main();
}

module.exports = VideoCompressionClient;
```

## Frontend Integration Examples

### HTML Form Example

```html
<!DOCTYPE html>
<html>
<head>
    <title>Video Compression Demo</title>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
</head>
<body>
    <h1>Video Compression Service</h1>
    
    <form id="compressionForm">
        <div>
            <label for="fileInput">Select Video File:</label>
            <input type="file" id="fileInput" accept="video/*" required>
        </div>
        
        <div>
            <label for="presetSelect">Compression Preset:</label>
            <select id="presetSelect">
                <option value="high-quality">High Quality</option>
                <option value="balanced" selected>Balanced</option>
                <option value="high-compression">High Compression</option>
                <option value="screen-recording">Screen Recording</option>
            </select>
        </div>
        
        <div>
            <label for="customBitrate">Custom Video Bitrate (bps):</label>
            <input type="number" id="customBitrate" placeholder="e.g., 2500000">
        </div>
        
        <button type="submit">Compress Video</button>
    </form>
    
    <div id="progressContainer" style="display: none;">
        <h3>Compression Progress</h3>
        <div id="progressBar" style="width: 0%; height: 20px; background-color: #4CAF50;"></div>
        <p id="progressText">0% - Starting...</p>
    </div>
    
    <div id="resultContainer" style="display: none;">
        <h3>Compression Result</h3>
        <pre id="resultText"></pre>
        <a id="downloadLink" download>Download Compressed Video</a>
    </div>

    <script>
        const compressionForm = document.getElementById('compressionForm');
        const progressContainer = document.getElementById('progressContainer');
        const progressBar = document.getElementById('progressBar');
        const progressText = document.getElementById('progressText');
        const resultContainer = document.getElementById('resultContainer');
        const resultText = document.getElementById('resultText');
        const downloadLink = document.getElementById('downloadLink');

        compressionForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const fileInput = document.getElementById('fileInput');
            const presetSelect = document.getElementById('presetSelect');
            const customBitrate = document.getElementById('customBitrate');
            
            if (!fileInput.files[0]) {
                alert('Please select a video file');
                return;
            }
            
            // First upload the file
            const formData = new FormData();
            formData.append('file', fileInput.files[0]);
            
            try {
                // Upload file (this would be to your upload endpoint)
                const uploadResponse = await axios.post('/api/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                });
                
                const inputFilePath = uploadResponse.data.filePath;
                
                // Start compression
                const compressionRequest = {
                    inputFilePath: inputFilePath,
                    preset: presetSelect.value
                };
                
                if (customBitrate.value) {
                    compressionRequest.videoBitrate = parseInt(customBitrate.value);
                }
                
                const compressionResponse = await axios.post('/api/video/compress/async', compressionRequest);
                const jobId = compressionResponse.data.jobId;
                
                // Show progress and monitor
                progressContainer.style.display = 'block';
                resultContainer.style.display = 'none';
                
                await monitorProgress(jobId);
                
            } catch (error) {
                console.error('Error:', error);
                alert('Compression failed: ' + (error.response?.data?.errorMessage || error.message));
            }
        });
        
        async function monitorProgress(jobId) {
            const checkProgress = async () => {
                try {
                    const response = await axios.get(`/api/video/progress/${jobId}`);
                    const progress = response.data;
                    
                    if (progress.isError) {
                        throw new Error(progress.status);
                    }
                    
                    // Update progress bar
                    progressBar.style.width = `${progress.progress}%`;
                    progressText.textContent = `${progress.progress.toFixed(1)}% - ${progress.status}`;
                    
                    if (progress.isComplete) {
                        // Get final result
                        const resultResponse = await axios.get(`/api/video/result/${jobId}`);
                        const result = resultResponse.data;
                        
                        resultContainer.style.display = 'block';
                        resultText.textContent = JSON.stringify(result, null, 2);
                        
                        // Set download link
                        downloadLink.href = `/api/download/${result.outputFilePath}`;
                        downloadLink.style.display = 'block';
                        
                        return;
                    }
                    
                    // Continue monitoring
                    setTimeout(checkProgress, 2000);
                    
                } catch (error) {
                    console.error('Progress monitoring error:', error);
                    progressText.textContent = 'Error: ' + error.message;
                }
            };
            
            checkProgress();
        }
    </script>
</body>
</html>
```

## Testing Examples

### Unit Test Example

```java
@SpringBootTest
@AutoConfigureTestDatabase
class VideoCompressionServiceTest {
    
    @Autowired
    private VideoCompressionService videoCompressionService;
    
    @Test
    void testVideoCompressionWithPreset() {
        // Create test video file
        String testInputPath = createTestVideoFile();
        
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath(testInputPath);
        request.setPreset("balanced");
        
        VideoCompressionResponse response = videoCompressionService.compressVideo(request);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutputFilePath()).isNotEmpty();
        assertThat(response.getCompressionRatio()).isGreaterThan(0);
        
        // Clean up
        Files.deleteIfExists(Paths.get(response.getOutputFilePath()));
        Files.deleteIfExists(Paths.get(testInputPath));
    }
    
    @Test
    void testVideoCompressionWithCustomParameters() {
        String testInputPath = createTestVideoFile();
        
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath(testInputPath);
        request.setVideoBitrate(2000000);
        request.setAudioBitrate(128000);
        request.setCrf(22);
        
        VideoCompressionResponse response = videoCompressionService.compressVideo(request);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSettings().getVideoBitrate()).isEqualTo(2000000);
        assertThat(response.getSettings().getAudioBitrate()).isEqualTo(128000);
        assertThat(response.getSettings().getCrf()).isEqualTo(22);
        
        // Clean up
        Files.deleteIfExists(Paths.get(response.getOutputFilePath()));
        Files.deleteIfExists(Paths.get(testInputPath));
    }
    
    private String createTestVideoFile() {
        // Create a test video file for testing
        // This is a placeholder - implement actual test video creation
        return "/tmp/test-video.mp4";
    }
}
```

### Integration Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VideoCompressionControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testCompressVideoEndpoint() {
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath("/tmp/test-video.mp4");
        request.setPreset("balanced");
        
        ResponseEntity<VideoCompressionResponse> response = restTemplate.postForEntity(
            "/api/video/compress", 
            request, 
            VideoCompressionResponse.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }
    
    @Test
    void testCompressVideoAsyncEndpoint() {
        VideoCompressionRequest request = new VideoCompressionRequest();
        request.setInputFilePath("/tmp/test-video.mp4");
        request.setPreset("high-quality");
        
        ResponseEntity<VideoCompressionResponse> response = restTemplate.postForEntity(
            "/api/video/compress/async", 
            request, 
            VideoCompressionResponse.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getJobId()).isNotEmpty();
    }
}
```

These examples provide comprehensive guidance on how to integrate and use the video compression service in various scenarios and programming languages.