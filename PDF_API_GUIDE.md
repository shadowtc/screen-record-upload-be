# PDF Conversion and Upload API Guide

## Overview

This guide covers three main features:
1. **Improved DOCX to PDF conversion** with better format preservation
2. **PDF Upload API** for uploading PDF files and converting them to images
3. **Paginated Image Query API** for retrieving PDF page images by task ID

---

## 1. Improved DOCX to PDF Conversion

### What's New

The DOCX to PDF conversion has been improved to better preserve document formatting:
- **Paragraph alignment** (left, center, right, justified)
- **Text styles** (headings, titles with bold and larger font)
- **Tables** (formatted with pipe separators)
- **Embedded images** (automatically extracted and included)

### API Endpoint

```
POST /api/pdf/convert
```

### Example Request

```bash
curl -X POST http://localhost:8080/api/pdf/convert \
  -F "file=@document.docx" \
  -F 'request={"convertToImages": true, "imageDpi": 300, "imageFormat": "png"}'
```

### Response

```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PROCESSING",
  "message": "Conversion started. Use jobId to check progress."
}
```

---

## 2. PDF Upload and Image Conversion API

### Description

Upload a PDF file and automatically convert all pages to high-quality images asynchronously.

### API Endpoint

```
POST /api/pdf/upload
```

### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| file | MultipartFile | Yes | - | PDF file to upload (must be .pdf extension) |
| imageDpi | Integer | No | 300 | Image resolution (72-600) |
| imageFormat | String | No | "png" | Image format (png, jpg, bmp) |

### Example Request

```bash
curl -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@document.pdf" \
  -F "imageDpi=300" \
  -F "imageFormat=png"
```

### Response

```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "message": "PDF upload successful. Converting to images in background."
}
```

### Error Response Examples

**Invalid File Type:**
```json
{
  "status": "ERROR",
  "message": "Only PDF files are allowed"
}
```

**File Too Large:**
```json
{
  "status": "ERROR",
  "message": "File size exceeds limit. Max: 100 MB, Actual: 150.25 MB"
}
```

---

## 3. Check Upload Progress

### API Endpoint

```
GET /api/pdf/upload/progress/{taskId}
```

### Example Request

```bash
curl -X GET http://localhost:8080/api/pdf/upload/progress/abc12345-6789-0def-1234-567890abcdef
```

### Response

```json
{
  "jobId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "currentPhase": "Converting PDF pages to images",
  "progressPercentage": 65,
  "message": null,
  "startTime": 1703001234567,
  "elapsedTimeMs": 15234,
  "totalPages": 10,
  "processedPages": 6,
  "errorMessage": null
}
```

### Status Values

- `SUBMITTED` - Task has been submitted
- `PROCESSING` - Currently converting
- `COMPLETED` - Conversion completed successfully
- `FAILED` - Conversion failed
- `NOT_FOUND` - Task ID not found

---

## 4. Paginated Image Query API

### Description

Retrieve PDF page images by task ID with pagination support.

### API Endpoint

```
GET /api/pdf/images/{taskId}
```

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| startPage | Integer | No | 1 | Starting page number (1-based) |
| pageSize | Integer | No | 10 | Number of pages to return |

### Example Requests

**Get first 10 pages:**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=1&pageSize=10"
```

**Get pages 11-20:**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=11&pageSize=10"
```

**Get all remaining pages:**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=1&pageSize=1000"
```

### Response

```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "totalPages": 25,
  "startPage": 1,
  "pageSize": 10,
  "returnedPages": 10,
  "status": "SUCCESS",
  "message": "Successfully retrieved page images",
  "images": [
    {
      "pageNumber": 1,
      "imagePath": "/path/to/temp/abc12345-6789-0def-1234-567890abcdef/images/page_0001.png",
      "fileSize": 524288,
      "width": 2480,
      "height": 3508
    },
    {
      "pageNumber": 2,
      "imagePath": "/path/to/temp/abc12345-6789-0def-1234-567890abcdef/images/page_0002.png",
      "fileSize": 498765,
      "width": 2480,
      "height": 3508
    }
    // ... more pages
  ]
}
```

### Error Response Examples

**Task Not Found:**
```json
{
  "taskId": "invalid-task-id",
  "status": "NOT_FOUND",
  "message": "Task not found"
}
```

**Task Not Completed:**
```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "message": "Task is not completed yet. Current status: PROCESSING",
  "totalPages": 25
}
```

**Start Page Exceeds Total:**
```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "totalPages": 25,
  "startPage": 30,
  "pageSize": 10,
  "returnedPages": 0,
  "status": "SUCCESS",
  "message": "Start page exceeds total pages",
  "images": []
}
```

---

## Complete Workflow Example

### 1. Upload PDF and Convert to Images

```bash
#!/bin/bash

# Upload PDF
RESPONSE=$(curl -s -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@document.pdf" \
  -F "imageDpi=300" \
  -F "imageFormat=png")

TASK_ID=$(echo $RESPONSE | jq -r '.taskId')
echo "Task ID: $TASK_ID"
```

### 2. Monitor Progress

```bash
# Poll progress every 2 seconds
while true; do
  PROGRESS=$(curl -s -X GET "http://localhost:8080/api/pdf/upload/progress/$TASK_ID")
  STATUS=$(echo $PROGRESS | jq -r '.status')
  PERCENTAGE=$(echo $PROGRESS | jq -r '.progressPercentage')
  
  echo "Status: $STATUS - Progress: $PERCENTAGE%"
  
  if [ "$STATUS" == "COMPLETED" ]; then
    break
  fi
  
  if [ "$STATUS" == "FAILED" ]; then
    echo "Conversion failed!"
    exit 1
  fi
  
  sleep 2
done
```

### 3. Retrieve Images in Batches

```bash
# Get first 5 pages
curl -s -X GET "http://localhost:8080/api/pdf/images/$TASK_ID?startPage=1&pageSize=5" | jq '.'

# Get next 5 pages
curl -s -X GET "http://localhost:8080/api/pdf/images/$TASK_ID?startPage=6&pageSize=5" | jq '.'
```

### 4. Use the Test Script

A ready-to-use test script is provided:

```bash
./test-pdf-upload.sh document.pdf 300 png
```

---

## Configuration

PDF conversion settings can be configured in `application.yml`:

```yaml
pdf:
  conversion:
    enabled: true
    temp-directory: D://ruoyi/pdf-conversion
    max-concurrent-jobs: 3
    max-file-size: 104857600  # 100MB
    image-rendering:
      dpi: 300
      format: png
      quality: 0.95
      antialiasing: true
```

---

## Best Practices

1. **DPI Selection:**
   - 72 DPI: Screen viewing (smallest file size)
   - 150 DPI: Basic printing
   - 300 DPI: High-quality printing (recommended)
   - 600 DPI: Professional printing (largest file size)

2. **Image Format:**
   - PNG: Best quality, lossless, larger files (recommended)
   - JPG: Good quality, lossy compression, smaller files
   - BMP: Uncompressed, very large files

3. **Pagination:**
   - Use reasonable page sizes (10-50 pages per request)
   - Don't request all pages at once for large PDFs
   - Implement client-side pagination for better UX

4. **Error Handling:**
   - Always check the `status` field in responses
   - Poll progress endpoint regularly (every 1-2 seconds)
   - Implement timeout logic for long-running conversions

5. **File Management:**
   - Conversion results are stored in temporary directories
   - Clean up old tasks periodically
   - Consider implementing automatic cleanup after 24 hours

---

## Troubleshooting

### Problem: "Only PDF files are allowed"
**Solution:** Ensure the uploaded file has a `.pdf` extension.

### Problem: "File size exceeds limit"
**Solution:** Compress the PDF or increase `pdf.conversion.max-file-size` in configuration.

### Problem: Images are too large
**Solution:** Reduce the DPI value (try 150 instead of 300).

### Problem: Task not found after server restart
**Solution:** Task progress is stored in memory. After restart, you need to re-upload the PDF.

### Problem: Poor image quality
**Solution:** Increase DPI to 300 or 600, and use PNG format.

---

## API Summary

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/pdf/convert` | POST | Convert any supported format to PDF with images |
| `/api/pdf/upload` | POST | Upload PDF and convert to images |
| `/api/pdf/upload/progress/{taskId}` | GET | Check PDF upload and conversion progress |
| `/api/pdf/images/{taskId}` | GET | Get paginated PDF page images |
| `/api/pdf/progress/{jobId}` | GET | Check file conversion progress |
| `/api/pdf/result/{jobId}` | GET | Get conversion result |
| `/api/pdf/formats` | GET | List supported file formats |
