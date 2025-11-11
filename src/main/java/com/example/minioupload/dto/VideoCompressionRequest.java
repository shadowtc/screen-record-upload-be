package com.example.minioupload.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 视频压缩请求DTO
 * 
 * 封装客户端向服务器发送的视频压缩请求参数。
 * 所有参数都是可选的，除了inputFilePath是必需的。
 * 不指定的参数将使用服务器默认配置或选定预设的参数。
 * 
 * 参数优先级：
 * 1. 显式指定的参数（最高）
 * 2. 预设参数
 * 3. 服务器默认参数（最低）
 */
@Data
public class VideoCompressionRequest {
    
    /**
     * 输入视频文件的绝对路径
     * 必需参数，服务器必须能访问此路径
     */
    @NotNull(message = "Input file path is required")
    private String inputFilePath;
    
    /**
     * 压缩预设名称
     * 
     * 可用值：
     * - high-quality：最高质量预设，适合需要保留最多细节的场景
     * - balanced：平衡预设，质量与文件大小的中间方案
     * - high-compression：最大压缩预设，适合网络传输或存储空间有限
     * - screen-recording：屏幕录制优化预设，针对文字和界面内容优化
     * 
     * 如果指定此参数，预设中的所有参数（码率、分辨率、CRF等）将应用。
     * 随后指定的个别参数会覆盖预设中的对应值。
     */
    private String preset;
    
    /**
     * 视频比特率（单位：bps - bits per second）
     * 
     * 范围限制：500k - 5000k
     * 常见值：
     * - 1M (1000000)：低质量，快速编码
     * - 2.5M (2500000)：中等质量，平衡方案
     * - 5M (5000000)：高质量，大文件
     * 
     * 此值越高，输出视频质量越好，但文件越大，编码时间越长。
     * 具体选择应考虑分辨率、帧率和内容类型。
     */
    @Min(value = 500000, message = "Video bitrate must be at least 500k")
    @Max(value = 5000000, message = "Video bitrate must not exceed 5000k")
    private Integer videoBitrate;
    
    /**
     * 音频比特率（单位：bps - bits per second）
     * 
     * 范围限制：64k - 320k
     * 常见值：
     * - 64k (64000)：低质量，适合语音
     * - 128k (128000)：通常选择，平衡质量和大小
     * - 192k (192000)：高质量，流式传输
     * - 320k (320000)：最高质量，仅在特殊情况
     * 
     * 默认值：128000
     */
    @Min(value = 64000, message = "Audio bitrate must be at least 64k")
    @Max(value = 320000, message = "Audio bitrate must not exceed 320k")
    private Integer audioBitrate;
    
    /**
     * 输出视频宽度（像素）
     * 
     * 可选参数。如果指定，视频会被缩放到此宽度。
     * 常见值：640, 854, 1280, 1920, 2560, 3840
     * 对应分辨率：480p, 720p, 1080p, 1440p, 4K
     * 
     * 如果只指定宽度不指定高度（或反之），系统会按原始宽高比计算另一个维度。
     * 如果不指定，保持原始宽度。
     */
    private Integer width;
    
    /**
     * 输出视频高度（像素）
     * 
     * 可选参数。配合width使用。
     * 常见值：480, 720, 1080, 1440, 2160
     * 
     * 如果不指定，保持原始高度。
     */
    private Integer height;
    
    /**
     * 恒定质量因子（CRF - Constant Rate Factor）
     * 
     * 范围限制：0 - 51
     * 
     * CRF值说明：
     * - 0-17：无损/近似无损质量
     *          不推荐（文件极大，对大多数应用无意义）
     * - 18-24：高质量（存档用途）
     *          18-20：最佳质量，用于重要存档
     *          21-23：高质量，用于一般需求
     *          24：接近无损的高质量
     * - 25-28：平衡方案（推荐）
     *          25-26：平衡质量和文件大小
     *          27-28：更多压缩，轻微质量损失
     * - 29-51：低质量（不推荐）
     *          29-34：可见的质量下降
     *          35-51：严重的质量损失
     * 
     * 默认值：23（通常为视觉上无损的最小值）
     * 
     * 注意：CRF和videoBitrate是两个互斥的质量控制机制。
     * 使用CRF时会忽略videoBitrate的约束。
     */
    @Min(value = 0, message = "CRF must be between 0 and 51")
    @Max(value = 51, message = "CRF must be between 0 and 51")
    private Integer crf;
    
    /**
     * 编码器预设（只有在preset不是预定义预设时才有效）
     * 
     * 可用值（编码时间和压缩效果的平衡）：
     * - ultrafast：编码最快，压缩效果最差（仅2-3%）
     * - superfast：更快编码，较差压缩效果
     * - veryfast：快速编码，一般压缩效果
     * - faster：相对快速编码
     * - fast：快速编码
     * - medium：平衡方案（默认），编码和压缩效果均衡
     * - slow：较慢编码，更好压缩效果
     * - slower：慢速编码，很好的压缩效果
     * - veryslow：最慢编码，最好的压缩效果（对大多数应用过度）
     * 
     * 注意：编码器预设仅影响编码速度和最终文件大小，不影响质量。
     * 质量由CRF或videoBitrate控制。
     */
    private String encoderPreset;
    
    /**
     * 输出视频格式
     * 
     * 可选参数，通常使用默认格式mp4。
     * 其他支持的格式：mkv, webm等
     * 
     * 默认值：mp4
     */
    private String outputFormat;
    
    /**
     * 是否启用两遍编码
     * 
     * 两遍编码会先扫描整个视频文件统计信息，然后进行编码。
     * 
     * 优点：获得更好的压缩效果
     * 缺点：编码时间约2倍增加
     * 
     * 推荐场景：
     * - 对文件大小要求严格
     * - 对编码时间不敏感
     * - 压缩率比处理时间更重要
     * 
     * 默认值：false（大多数应用不使用）
     */
    private Boolean twoPass;
    
    /**
     * 压缩完成后是否删除原始文件
     * 
     * 如果为true，压缩成功后原始文件会被删除。
     * 使用此选项前应确保有备份或不需要原始文件。
     * 
     * 默认值：false（不删除原始文件）
     */
    private Boolean deleteOriginal;
}