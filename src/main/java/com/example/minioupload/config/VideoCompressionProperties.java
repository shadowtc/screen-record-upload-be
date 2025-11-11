package com.example.minioupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 视频压缩配置属性类
 * 
 * 绑定配置文件中video.compression前缀的所有配置项。
 * 包含视频压缩功能的全局配置、预设定义和编码默认值。
 * 
 * 配置来源（优先级从高到低）：
 * 1. 环境变量（VIDEO_开头）
 * 2. 系统属性（-D参数）
 * 3. 配置文件（application.yml）
 * 4. 硬编码默认值
 * 
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Data
@Component
@ConfigurationProperties(prefix = "video.compression")
public class VideoCompressionProperties {
    
    /**
     * 是否启用视频压缩功能
     * 默认值：true
     * 当设置为false时，所有压缩端点返回功能禁用错误
     */
    private boolean enabled = true;
    
    /**
     * 临时目录路径
     * 存储压缩中和已完成的视频文件
     * 
     * 推荐配置：
     * - Linux：/tmp/video-compression
     * - Docker：/tmp/video-compression（已挂载为卷）
     * - Windows：C:\temp\video-compression
     * 
     * 默认值：/tmp/video-compression
     * 
     * 注意：需要确保此目录有足够的磁盘空间和读写权限。
     * 推荐大小：至少2倍最大输入视频文件大小
     */
    private String tempDirectory = "/tmp/video-compression";
    
    /**
     * 最大并发压缩任务数
     * 超过此数值的新请求将排队等待
     * 
     * 调整建议：
     * - 2CPU + 4GB内存：2-3个任务
     * - 4CPU + 8GB内存：4-6个任务
     * - 8CPU + 16GB内存：8-10个任务
     * 
     * 默认值：2
     * 
     * 注意：每个任务消耗1-2个CPU核心，增加并发数会显著增加内存消耗
     */
    private int maxConcurrentJobs = 2;
    
    /**
     * 最大文件大小限制（字节）
     * 超过此大小的视频文件将被拒绝
     * 
     * 默认值：1073741824（1GB）
     * 
     * 常见值：
     * - 100MB (104857600)：适合网络应用
     * - 500MB (524288000)：适合一般场景
     * - 1GB (1073741824)：适合大型文件
     * - 2GB (2147483648)：适合专业应用
     */
    private long maxFileSize = 1073741824L;
    
    /**
     * 单个压缩任务的超时时间（秒）
     * 如果压缩超过此时间未完成，任务将被终止
     * 
     * 计算建议：
     * 根据最大文件大小和硬件性能估算
     * 公式：超时秒数 = (最大文件大小 / 预期压缩速度) * 1.5（冗余系数）
     * 
     * 默认值：300（5分钟）
     * 
     * 常见值：
     * - 60：1GB文件的快速压缩
     * - 300：1GB文件的一般压缩
     * - 600：1GB文件的高质量压缩或2CPU情况
     * - 1200：2GB文件或更复杂的处理
     */
    private long timeoutSeconds = 300L;
    
    /** 压缩预设配置，包含所有预定义的压缩策略 */
    private PresetConfig presets = new PresetConfig();
    
    /** 编码默认配置，为未指定参数提供默认值 */
    private EncodingConfig encoding = new EncodingConfig();
    
    /** 分辨率预设配置，包含常用分辨率定义 */
    private ResolutionConfig resolution = new ResolutionConfig();
    
    /**
     * 压缩预设配置
     * 
     * 包含多个命名的预设，每个预设定义一套完整的压缩参数。
     * 客户端可通过preset参数选择预设，所有参数自动应用。
     */
    @Data
    public static class PresetConfig {
        /**
         * 预设映射表
         * key：预设名称（如"balanced"、"high-quality"）
         * value：预设配置对象
         * 
         * 典型预设：
         * - high-quality：适合存档和高质量需求
         * - balanced：适合日常使用
         * - high-compression：适合网络传输
         * - screen-recording：适合屏幕录制内容
         */
        private Map<String, Preset> profiles;
        
        /**
         * 单个压缩预设配置
         * 
         * 预设定义了一套参数组合，用于特定场景。
         * 例如"high-quality"预设包含高码率、低CRF等参数。
         */
        @Data
        public static class Preset {
            /** 预设显示名称，用于UI展示 */
            private String name;
            
            /** 预设描述，说明其适用场景和特点 */
            private String description;
            
            /**
             * 目标视频比特率（bps）
             * 此值直接用于编码，影响输出文件大小
             */
            private int videoBitrate;
            
            /**
             * 目标音频比特率（bps）
             * 默认值：128000（128k，标准值）
             */
            private int audioBitrate = 128000;
            
            /**
             * 输出分辨率预设名称
             * 引用resolution配置中的预设（如"720p"）
             */
            private String resolution;
            
            /**
             * 视频编码器名称
             * 默认值：libx264（H.264编码）
             * 其他值：libx265（H.265），libvpx（VP8）等
             */
            private String codec = "libx264";
            
            /**
             * 编码器速度预设
             * 默认值：medium（平衡）
             * 快速值：ultrafast到fast
             * 质量值：slow到veryslow
             */
            private String preset = "medium";
            
            /**
             * 恒定质量因子（CRF）
             * 默认值：23
             * 范围：0-51，越低质量越好
             * 
             * CRF与videoBitrate的关系：
             * - 如果指定了CRF，videoBitrate会被忽略
             * - CRF通常提供更一致的主观质量
             */
            private int crf = 23;
            
            /**
             * 是否启用两遍编码
             * 默认值：false
             * 两遍编码能提高压缩效果但编码时间加倍
             */
            private boolean twoPass = false;
        }
    }
    
    /**
     * 编码默认配置
     * 
     * 为压缩参数未指定时提供默认值。
     * 这些值在参数优先级最低，会被预设和请求参数覆盖。
     */
    @Data
    public static class EncodingConfig {
        /**
         * 默认视频编码器
         * 默认值：libx264（H.264编码）
         * 
         * 其他常用编码器：
         * - libx265：H.265/HEVC编码，更高压缩率但兼容性较差
         * - libvpx：VP8编码，WebM格式
         * - libvpx-vp9：VP9编码，更新的网络格式
         */
        private String defaultCodec = "libx264";
        
        /**
         * 默认编码器预设
         * 默认值：medium（平衡方案）
         * 
         * 预设说明（从快到慢）：
         * - ultrafast < superfast < veryfast < faster < fast
         * - medium（默认）
         * - slow < slower < veryslow
         * 
         * 每级预设差异约10-15%的编码时间和5-10%的输出大小
         */
        private String defaultPreset = "medium";
        
        /**
         * 默认CRF值
         * 默认值：23
         * 范围：0-51
         * 
         * 推荐值：
         * - 18：高质量（存档用）
         * - 23：通用默认值
         * - 28：中等质量
         */
        private int defaultCRF = 23;
        
        /**
         * 默认音频比特率（bps）
         * 默认值：128000（128kbps）
         * 
         * 常见值：
         * - 64000：低质量（语音）
         * - 128000：标准质量（推荐）
         * - 192000：高质量（音乐）
         * - 320000：最高质量（仅特殊情况）
         */
        private int defaultAudioBitrate = 128000;
        
        /**
         * 默认音频编码器
         * 默认值：aac（高效且广泛支持）
         * 
         * 其他编码器：
         * - mp3：较旧但兼容性好
         * - libopus：新编码器，更高效
         * - libvorbis：开源编码器
         */
        private String defaultAudioCodec = "aac";
        
        /**
         * 默认编码线程数
         * 默认值：0（自动检测）
         * 
         * 配置建议：
         * - 0：自动检测，推荐使用
         * - 1：单线程，调试用
         * - 2-8：固定线程数
         * - CPU核心数：最大化性能
         */
        private int defaultThreads = 0;
    }
    
    /**
     * 分辨率预设配置
     * 
     * 定义常用的输出分辨率及其推荐参数。
     * 客户端可通过分辨率预设快速设置宽高度。
     */
    @Data
    public static class ResolutionConfig {
        /**
         * 分辨率预设映射表
         * key：分辨率名称（如"720p"、"1080p"）
         * value：具体的分辨率配置
         */
        private Map<String, Resolution> presets;
        
        /**
         * 单个分辨率预设
         * 
         * 定义一个标准分辨率及其相关信息。
         * 例如720p预设定义为1280x720像素，推荐2.5M码率。
         */
        @Data
        public static class Resolution {
            /** 分辨率显示名称，用于UI呈现 */
            private String name;
            
            /** 视频宽度（像素） */
            private int width;
            
            /** 视频高度（像素） */
            private int height;
            
            /**
             * 推荐视频码率（bps）
             * 基于这个分辨率实现良好视觉质量的参考码率
             * 实际使用可根据需求调整
             */
            private int recommendedBitrate;
            
            /** 分辨率说明和特点 */
            private String description;
        }
    }
}