# Video Compression FAQ

## General Questions

### Q: What video formats are supported for input?
A: The service supports any video format that FFmpeg can handle, including:
- MP4, AVI, MOV, MKV, WMV, FLV, WebM
- H.264, H.265, VP9, AV1 encoded videos
- Various container formats

### Q: What is the output format?
A: By default, the service outputs MP4 format with H.264 video and AAC audio, which provides maximum compatibility across devices and platforms.

### Q: How long does compression take?
A: Compression time depends on:
- Video length and resolution
- Selected preset and quality settings
- Available CPU resources
- File size

Typical compression times:
- 1 minute 1080p video: 10-30 seconds (medium preset)
- 10 minute 1080p video: 2-5 minutes (medium preset)
- Using "slow" preset can increase time by 2-3x but improves quality

## Configuration and Presets

### Q: What preset should I use?
A: Choose based on your needs:
- **high-quality**: Best quality, larger files (archival, professional use)
- **balanced**: Good quality/size balance (general use, sharing)
- **high-compression**: Smallest files, lower quality (storage optimization)
- **screen-recording**: Optimized for screen content (presentations, tutorials)

### Q: What is CRF and what value should I use?
A: CRF (Constant Rate Factor) controls quality:
- 0-17: Excellent quality, large files
- 18-23: Good quality, reasonable size (recommended: 23)
- 24-28: Fair quality, smaller files
- 29-51: Poor quality, very small files

Lower CRF = higher quality but larger files.

### Q: What's the difference between presets (ultrafast, fast, medium, slow)?
A: Presets balance encoding speed vs compression efficiency:
- **ultrafast/superfast**: Fastest encoding, larger files
- **fast/medium**: Good balance (recommended: medium)
- **slow/slower/veryslow**: Best compression, slowest encoding

## Performance and Resources

### Q: How much CPU and memory does compression need?
A: Recommended resources:
- **Minimum**: 2 CPU cores, 2GB RAM
- **Recommended**: 4+ CPU cores, 4GB+ RAM
- **For 4K video**: 8+ CPU cores, 8GB+ RAM

### Q: Can I run multiple compressions simultaneously?
A: Yes, the service supports concurrent jobs. By default:
- Maximum concurrent jobs: 2
- Configurable via `VIDEO_MAX_JOBS` environment variable
- Each job uses approximately 1-2 CPU cores

### Q: How can I improve compression speed?
A: Options to speed up compression:
1. Use faster presets (fast, veryfast)
2. Reduce video bitrate
3. Lower resolution
4. Use hardware acceleration (if available)
5. Increase CPU allocation
6. Disable two-pass encoding

## Quality and File Size

### Q: How much can I reduce file size?
A: Typical compression ratios:
- **High-quality preset**: 20-40% reduction
- **Balanced preset**: 40-60% reduction
- **High-compression preset**: 60-80% reduction
- **Screen recording**: 30-50% reduction

Results vary based on original video content and quality.

### Q: Will compression reduce video quality?
A: Yes, compression reduces quality to achieve smaller file sizes. However:
- Properly configured compression maintains good visual quality
- Higher CRF values = more quality loss
- Screen recordings often compress well with minimal quality loss
- Use the "screen-recording" preset for optimal results

### Q: What's the best bitrate for my resolution?
A: Recommended bitrates:
- **480p**: 1-2 Mbps
- **720p**: 2-5 Mbps
- **1080p**: 5-10 Mbps
- **1440p**: 8-15 Mbps
- **4K**: 15-25 Mbps

## Technical Issues

### Q: Why is my compression failing?
A: Common causes:
1. Input file doesn't exist or isn't readable
2. Invalid video format
3. Insufficient disk space
4. Corrupted video file
5. Insufficient system resources

Check the application logs for detailed error messages.

### Q: Why is the output file larger than expected?
A: Possible reasons:
1. Original video had very efficient encoding
2. Settings are too high (low CRF, high bitrate)
3. Audio bitrate is too high
4. Resolution wasn't reduced
5. Two-pass encoding with high target bitrate

### Q: Can I compress audio separately from video?
A: Yes, you can specify different audio settings:
- Set audioBitrate independently
- Different codecs for audio (AAC, MP3, etc.)
- Audio can be compressed more aggressively than video

## Integration and API

### Q: How do I integrate this into my application?
A: Integration options:
1. **HTTP API**: Call REST endpoints directly
2. **Java Integration**: Use the service classes directly
3. **Docker**: Deploy as a microservice
4. **Async Processing**: Use async endpoints for long videos

See [VIDEO_COMPRESSION_EXAMPLES.md](./VIDEO_COMPRESSION_EXAMPLES.md) for code examples.

### Q: How do I handle large files?
A: For large files:
1. Use async endpoints to avoid timeouts
2. Monitor progress with the progress endpoint
3. Ensure sufficient temporary storage
4. Consider chunking very large files first
5. Use appropriate timeout settings

### Q: Can I cancel a running compression?
A: Currently, the service doesn't support cancellation. To implement:
1. Track running jobs in a database
2. Add a cancel endpoint
3. Implement job interruption logic
4. Clean up partial files

## Docker and Deployment

### Q: What are the Docker resource requirements?
A: Recommended Docker resources:
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

### Q: How do I persist compressed videos?
A: Options:
1. **Volume Mounting**: Mount host directories to container
2. **S3/MinIO**: Upload directly to object storage
3. **Database**: Store metadata and file paths
4. **Shared Storage**: Use network-attached storage

### Q: Can I use hardware acceleration?
A: Hardware acceleration isn't currently implemented but could be added:
- NVIDIA NVENC for GPU acceleration
- Intel Quick Sync Video
- VAAPI for Linux systems

## Troubleshooting

### Q: Compression is very slow, what can I do?
A: Speed optimization steps:
1. Check CPU utilization (may be throttled)
2. Use faster preset (fast, veryfast)
3. Reduce concurrent jobs
4. Check for I/O bottlenecks
5. Ensure sufficient RAM
6. Update FFmpeg to latest version

### Q: Output video has artifacts or poor quality
A: Quality improvement steps:
1. Lower CRF value (18-22)
2. Use slower preset (slow, medium)
3. Increase video bitrate
4. Use two-pass encoding
5. Check original video quality
6. Try different tune options

### Q: Audio is out of sync with video
A: Sync issue solutions:
1. Check original file for sync issues
2. Try different audio codecs
3. Ensure consistent frame rates
4. Use constant frame rate encoding
5. Check for variable frame rate issues

## Best Practices

### Q: What are the best practices for video compression?
A: Best practices:
1. **Choose appropriate preset**: Match to use case
2. **Test with sample content**: Different content compresses differently
3. **Monitor resources**: Don't overload system
4. **Clean up temporary files**: Manage disk space
5. **Use async for large files**: Avoid timeouts
6. **Validate inputs**: Check file existence and format
7. **Log everything**: Track performance and errors

### Q: How should I handle different types of content?
A: Content-specific recommendations:
- **Screen recordings**: Use screen-recording preset, CRF 20-22
- **Talking heads**: Balanced preset, CRF 23-25
- **Action/sports**: High-quality preset, CRF 18-20
- **Animations**: Screen-recording preset, tune animation
- **Presentations**: Screen-recording preset, maintain resolution

### Q: How do I optimize for web streaming?
A: Web optimization tips:
1. Use H.264 codec for maximum compatibility
2. Add `+faststart` flag for web playback
3. Use constant bitrate for streaming
4. Consider adaptive bitrate streaming
5. Optimize for target bandwidth
6. Test on various devices and browsers

## Security

### Q: Is the service secure?
A: Security considerations:
1. **File access**: Ensure proper file permissions
2. **Input validation**: Validate all input parameters
3. **Resource limits**: Prevent DoS attacks
4. **Temporary files**: Clean up sensitive data
5. **Network security**: Use HTTPS in production
6. **Access control**: Implement authentication as needed

### Q: How do I prevent abuse?
A: Abuse prevention:
1. Rate limiting on API endpoints
2. File size limits
3. Concurrent job limits
4. User authentication
5. Monitoring and logging
6. Resource quotas

## Monitoring and Maintenance

### Q: How do I monitor compression performance?
A: Monitoring recommendations:
1. Track compression success/failure rates
2. Monitor average processing time
3. Watch resource utilization
4. Log compression ratios
5. Alert on failures
6. Regular performance testing

### Q: How do I maintain the service?
A: Maintenance tasks:
1. Regular cleanup of temporary files
2. Monitor disk space usage
3. Update FFmpeg and dependencies
4. Review and optimize presets
5. Backup configuration
6. Performance tuning

---

For more detailed technical information, see:
- [VIDEO_COMPRESSION_GUIDE.md](./VIDEO_COMPRESSION_GUIDE.md)
- [VIDEO_COMPRESSION_EXAMPLES.md](./VIDEO_COMPRESSION_EXAMPLES.md)
- [FFMPEG_CONFIGURATION_EXAMPLES.md](./FFMPEG_CONFIGURATION_EXAMPLES.md)