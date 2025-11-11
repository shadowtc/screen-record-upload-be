# FFmpeg Configuration Examples and Best Practices

This document provides detailed FFmpeg configuration examples and optimization strategies for different use cases.

## FFmpeg Command Line Examples

### Basic Compression Commands

#### 1. Simple H.264 Compression
```bash
ffmpeg -i input.mp4 -c:v libx264 -crf 23 -c:a aac -b:a 128k output.mp4
```

#### 2. Two-Pass Encoding (Higher Quality)
```bash
# First pass
ffmpeg -i input.mp4 -c:v libx264 -preset slow -b:v 2M -pass 1 -an -f mp4 /dev/null

# Second pass
ffmpeg -i input.mp4 -c:v libx264 -preset slow -b:v 2M -pass 2 -c:a aac -b:a 128k output.mp4
```

#### 3. Resolution Change
```bash
ffmpeg -i input.mp4 -vf scale=1280:720 -c:v libx264 -crf 23 -c:a aac output_720p.mp4
```

### Screen Recording Optimization

#### 1. Optimized for Screen Content
```bash
ffmpeg -i screen-recording.mp4 \
  -c:v libx264 \
  -preset medium \
  -crf 20 \
  -tune animation \
  -profile:v high \
  -level 4.0 \
  -pix_fmt yuv420p \
  -movflags +faststart \
  -c:a aac \
  -b:a 128k \
  output_optimized.mp4
```

#### 2. High Quality Screen Recording
```bash
ffmpeg -i screen-recording.mp4 \
  -c:v libx264 \
  -preset slow \
  -crf 18 \
  -tune animation \
  -profile:v high \
  -level 4.1 \
  -pix_fmt yuv420p \
  -movflags +faststart \
  -c:a aac \
  -b:a 192k \
  output_high_quality.mp4
```

### Advanced Configuration Examples

#### 1. H.265 (HEVC) Encoding
```bash
ffmpeg -i input.mp4 \
  -c:v libx265 \
  -preset medium \
  -crf 28 \
  -c:a aac \
  -b:a 128k \
  output_h265.mp4
```

#### 2. VP9 Encoding (Web Optimization)
```bash
ffmpeg -i input.mp4 \
  -c:v libvpx-vp9 \
  -b:v 2M \
  -deadline good \
  -cpu-used 2 \
  -c:a libopus \
  -b:a 128k \
  output.webm
```

#### 3. Constant Quality with Maximum Bitrate
```bash
ffmpeg -i input.mp4 \
  -c:v libx264 \
  -crf 23 \
  -maxrate 3M \
  -bufsize 6M \
  -c:a aac \
  -b:a 128k \
  output.mp4
```

## JavaCV Configuration Examples

### Basic Video Compression

```java
public void compressVideo(String inputPath, String outputPath) throws Exception {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
         FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 
                 grabber.getAudioChannels(), grabber.getVideoWidth(), grabber.getVideoHeight())) {
        
        grabber.start();
        
        // Configure video
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setVideoQuality(23); // CRF
        recorder.setVideoBitrate(2000000);
        recorder.setPixelFormat(avcodec.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(grabber.getVideoFrameRate());
        
        // Configure audio
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        recorder.setAudioBitrate(128000);
        recorder.setSampleRate(grabber.getSampleRate());
        
        // Set H.264 options
        recorder.setVideoOption("preset", "medium");
        recorder.setVideoOption("tune", "film");
        
        recorder.start();
        
        Frame frame;
        while ((frame = grabber.grab()) != null) {
            recorder.record(frame);
        }
    }
}
```

### Advanced Configuration with Custom Options

```java
public void compressVideoAdvanced(String inputPath, String outputPath, VideoCompressionConfig config) throws Exception {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
         FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 
                 grabber.getAudioChannels(), config.getWidth(), config.getHeight())) {
        
        grabber.start();
        
        // Video configuration
        recorder.setVideoCodec(config.getVideoCodec());
        recorder.setVideoQuality(config.getCrf());
        recorder.setVideoBitrate(config.getVideoBitrate());
        recorder.setPixelFormat(avcodec.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(config.getFrameRate() > 0 ? config.getFrameRate() : grabber.getVideoFrameRate());
        
        // Audio configuration
        recorder.setAudioCodec(config.getAudioCodec());
        recorder.setAudioBitrate(config.getAudioBitrate());
        recorder.setSampleRate(config.getSampleRate() > 0 ? config.getSampleRate() : grabber.getSampleRate());
        
        // Advanced options
        recorder.setVideoOption("preset", config.getPreset());
        recorder.setVideoOption("tune", config.getTune());
        recorder.setVideoOption("profile", config.getProfile());
        recorder.setVideoOption("level", config.getLevel());
        recorder.setVideoOption("movflags", "+faststart");
        
        // Multi-threading
        recorder.setVideoOption("threads", String.valueOf(config.getThreads()));
        
        // Two-pass encoding setup
        if (config.isTwoPass()) {
            recorder.setVideoOption("pass", "1");
            recorder.setVideoOption("an", "");
            recorder.setFormat("mp4");
        }
        
        recorder.start();
        
        Frame frame;
        while ((frame = grabber.grab()) != null) {
            recorder.record(frame);
        }
        
        // Second pass for two-pass encoding
        if (config.isTwoPass()) {
            try (FFmpegFrameRecorder recorder2 = new FFmpegFrameRecorder(outputPath, 
                    grabber.getAudioChannels(), config.getWidth(), config.getHeight())) {
                
                recorder2.setVideoCodec(config.getVideoCodec());
                recorder2.setVideoBitrate(config.getVideoBitrate());
                recorder2.setAudioCodec(config.getAudioCodec());
                recorder2.setAudioBitrate(config.getAudioBitrate());
                recorder2.setVideoOption("preset", config.getPreset());
                recorder2.setVideoOption("pass", "2");
                
                grabber.restart();
                recorder2.start();
                
                while ((frame = grabber.grab()) != null) {
                    recorder2.record(frame);
                }
            }
        }
    }
}
```

## Configuration Classes

### Video Compression Configuration

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoCompressionConfig {
    
    // Codec settings
    private int videoCodec = avcodec.AV_CODEC_ID_H264;
    private int audioCodec = avcodec.AV_CODEC_ID_AAC;
    
    // Quality settings
    private Integer crf = 23; // Constant Rate Factor
    private Integer videoBitrate = 2000000; // 2 Mbps
    private Integer audioBitrate = 128000; // 128 kbps
    
    // Resolution
    private Integer width;
    private Integer height;
    private Double frameRate;
    
    // H.264 specific
    private String preset = "medium"; // ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
    private String tune = "film"; // film, animation, stillimage, fastdecode, zerolatency
    private String profile = "high"; // baseline, main, high, high10, high422, high444
    private String level = "4.0"; // 3.0, 3.1, 3.2, 4.0, 4.1, 4.2, 5.0, 5.1, 5.2
    
    // Performance
    private Integer threads = 0; // 0 = auto
    private boolean twoPass = false;
    
    // Audio
    private Integer sampleRate = 44100;
    private int audioChannels = 2;
    
    // Output format
    private String format = "mp4";
    
    // Additional options
    private Map<String, String> extraOptions = new HashMap<>();
    
    // Preset configurations
    public static VideoCompressionConfig highQuality() {
        return VideoCompressionConfig.builder()
            .crf(18)
            .videoBitrate(5000000)
            .audioBitrate(192000)
            .preset("slow")
            .tune("film")
            .profile("high")
            .level("4.1")
            .twoPass(true)
            .build();
    }
    
    public static VideoCompressionConfig balanced() {
        return VideoCompressionConfig.builder()
            .crf(23)
            .videoBitrate(2500000)
            .audioBitrate(128000)
            .preset("medium")
            .tune("film")
            .profile("high")
            .level("4.0")
            .twoPass(false)
            .build();
    }
    
    public static VideoCompressionConfig highCompression() {
        return VideoCompressionConfig.builder()
            .crf(28)
            .videoBitrate(1000000)
            .audioBitrate(96000)
            .preset("fast")
            .tune("fastdecode")
            .profile("main")
            .level("3.1")
            .twoPass(false)
            .build();
    }
    
    public static VideoCompressionConfig screenRecording() {
        return VideoCompressionConfig.builder()
            .crf(20)
            .videoBitrate(2000000)
            .audioBitrate(128000)
            .preset("medium")
            .tune("animation")
            .profile("high")
            .level("4.0")
            .twoPass(false)
            .extraOptions(Map.of(
                "movflags", "+faststart",
                "pix_fmt", "yuv420p"
            ))
            .build();
    }
}
```

## Performance Optimization

### Thread Configuration

```java
public class ThreadOptimizedCompression {
    
    public void compressWithOptimalThreads(String inputPath, String outputPath) throws Exception {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int optimalThreads = Math.min(availableCores - 1, 4); // Reserve 1 core, max 4 threads
        
        VideoCompressionConfig config = VideoCompressionConfig.builder()
            .threads(optimalThreads)
            .preset("medium") // Balance between speed and quality
            .build();
        
        compressVideoAdvanced(inputPath, outputPath, config);
    }
}
```

### Memory Optimization

```java
public class MemoryOptimizedCompression {
    
    public void compressWithMemoryLimits(String inputPath, String outputPath) throws Exception {
        VideoCompressionConfig config = VideoCompressionConfig.builder()
            .preset("fast") // Faster encoding uses less memory
            .threads(2) // Limit threads to reduce memory usage
            .extraOptions(Map.of(
                "x264opts", "no-scenecut" // Disable scene detection to save memory
            ))
            .build();
        
        compressVideoAdvanced(inputPath, outputPath, config);
    }
}
```

## Error Handling and Validation

### Input Validation

```java
public class VideoCompressionValidator {
    
    public void validateInputFile(String filePath) throws Exception {
        File file = new File(filePath);
        
        if (!file.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + filePath);
        }
        
        if (!file.canRead()) {
            throw new IllegalArgumentException("Cannot read input file: " + filePath);
        }
        
        // Check file size
        long maxSize = 2L * 1024 * 1024 * 1024; // 2GB
        if (file.length() > maxSize) {
            throw new IllegalArgumentException("File too large: " + file.length() + " bytes (max: " + maxSize + ")");
        }
        
        // Validate video format
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            
            if (grabber.getVideoCodec() == 0) {
                throw new IllegalArgumentException("No video stream found in file: " + filePath);
            }
            
            if (grabber.getVideoWidth() <= 0 || grabber.getVideoHeight() <= 0) {
                throw new IllegalArgumentException("Invalid video dimensions in file: " + filePath);
            }
        }
    }
    
    public void validateConfig(VideoCompressionConfig config) {
        if (config.getCrf() != null && (config.getCrf() < 0 || config.getCrf() > 51)) {
            throw new IllegalArgumentException("CRF must be between 0 and 51");
        }
        
        if (config.getVideoBitrate() != null && config.getVideoBitrate() < 500000) {
            throw new IllegalArgumentException("Video bitrate must be at least 500 kbps");
        }
        
        if (config.getAudioBitrate() != null && (config.getAudioBitrate() < 64000 || config.getAudioBitrate() > 320000)) {
            throw new IllegalArgumentException("Audio bitrate must be between 64 and 320 kbps");
        }
    }
}
```

### Comprehensive Error Handling

```java
public class RobustVideoCompression {
    
    private static final Logger logger = LoggerFactory.getLogger(RobustVideoCompression.class);
    
    public VideoCompressionResponse compressWithRetry(String inputPath, String outputPath, 
                                                     VideoCompressionConfig config, int maxRetries) {
        
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                logger.info("Attempting compression (attempt {}/{})", retryCount + 1, maxRetries);
                
                VideoCompressionValidator validator = new VideoCompressionValidator();
                validator.validateInputFile(inputPath);
                validator.validateConfig(config);
                
                compressVideoAdvanced(inputPath, outputPath, config);
                
                // Verify output
                if (!verifyOutput(outputPath)) {
                    throw new RuntimeException("Output verification failed");
                }
                
                logger.info("Compression successful on attempt {}", retryCount + 1);
                return VideoCompressionResponse.success(outputPath);
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                logger.warn("Compression attempt {} failed: {}", retryCount, e.getMessage());
                
                // Clean up partial output
                new File(outputPath).delete();
                
                // Wait before retry
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        logger.error("Compression failed after {} attempts", maxRetries, lastException);
        return VideoCompressionResponse.failure("Compression failed: " + lastException.getMessage());
    }
    
    private boolean verifyOutput(String outputPath) {
        File outputFile = new File(outputPath);
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            return false;
        }
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(outputPath)) {
            grabber.start();
            return grabber.getVideoCodec() != 0 && grabber.getVideoWidth() > 0;
        } catch (Exception e) {
            logger.warn("Output verification failed: {}", e.getMessage());
            return false;
        }
    }
}
```

## Monitoring and Metrics

### Compression Metrics Collection

```java
@Component
public class CompressionMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer compressionTimer;
    private final Counter compressionSuccessCounter;
    private final Counter compressionFailureCounter;
    private final Gauge compressionRatioGauge;
    
    public CompressionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.compressionTimer = Timer.builder("video.compression.duration")
            .description("Video compression duration")
            .register(meterRegistry);
        this.compressionSuccessCounter = Counter.builder("video.compression.success")
            .description("Successful video compressions")
            .register(meterRegistry);
        this.compressionFailureCounter = Counter.builder("video.compression.failure")
            .description("Failed video compressions")
            .register(meterRegistry);
        this.compressionRatioGauge = Gauge.builder("video.compression.ratio")
            .description("Compression ratio")
            .register(meterRegistry, this, CompressionMetrics::getCurrentCompressionRatio);
    }
    
    public VideoCompressionResponse recordCompression(String inputPath, String outputPath, 
                                                     Supplier<VideoCompressionResponse> compression) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            VideoCompressionResponse response = compression.get();
            
            if (response.isSuccess()) {
                compressionSuccessCounter.increment();
                recordCompressionRatio(response.getCompressionRatio());
            } else {
                compressionFailureCounter.increment();
            }
            
            return response;
            
        } catch (Exception e) {
            compressionFailureCounter.increment();
            throw e;
        } finally {
            sample.stop(compressionTimer);
        }
    }
    
    private double currentCompressionRatio = 0.0;
    
    private void recordCompressionRatio(double ratio) {
        this.currentCompressionRatio = ratio;
    }
    
    public double getCurrentCompressionRatio() {
        return currentCompressionRatio;
    }
}
```

These configuration examples provide a comprehensive foundation for implementing robust video compression with FFmpeg and JavaCV.