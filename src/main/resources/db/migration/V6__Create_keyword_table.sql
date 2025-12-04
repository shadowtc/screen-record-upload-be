-- Create keyword table for keyword filtering
CREATE TABLE IF NOT EXISTS `keyword` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `keyword` VARCHAR(200) NOT NULL COMMENT 'Keyword content',
    `category` VARCHAR(50) DEFAULT NULL COMMENT 'Keyword category (optional)',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Enable status: 1=enabled, 0=disabled',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword` (`keyword`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Keyword table for content filtering';

-- Insert some sample keywords for testing
INSERT INTO `keyword` (`keyword`, `category`, `enabled`) VALUES
('敏感词1', 'politics', 1),
('测试关键字', 'test', 1),
('违禁内容', 'prohibited', 1),
('赌博', 'illegal', 1),
('色情', 'illegal', 1);
