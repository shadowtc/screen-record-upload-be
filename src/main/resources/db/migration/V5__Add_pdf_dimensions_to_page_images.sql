-- V5: 添加PDF页面尺寸字段到pdf_page_image表
-- 用于存储PDF页面的原始尺寸（以PDF点为单位），用于准确的坐标转换

ALTER TABLE pdf_page_image
ADD COLUMN pdf_width DECIMAL(10,2) DEFAULT NULL COMMENT 'PDF页面宽度（点）' AFTER height,
ADD COLUMN pdf_height DECIMAL(10,2) DEFAULT NULL COMMENT 'PDF页面高度（点）' AFTER pdf_width,
ADD COLUMN rendering_dpi INT DEFAULT 300 COMMENT '渲染DPI（用于坐标转换）' AFTER pdf_height;

-- 为已有记录添加索引（如果需要按PDF尺寸查询）
CREATE INDEX idx_pdf_dimensions ON pdf_page_image(pdf_width, pdf_height);
