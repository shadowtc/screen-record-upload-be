# Bug Fix: PDF Upload with Empty Image Format

## Issue Description

When uploading a PDF file with an empty `imageFormat` parameter (`""`), the system threw a `NoSuchFileException` because the generated image filenames had a trailing dot but no extension (e.g., `page_0001.` instead of `page_0001.png`).

### Error Message
```
java.nio.file.NoSuchFileException: D:\ruoyi\pdf-conversion\...\images\page_0001.
```

### Root Cause

The issue occurred in two places:

1. **PdfUploadService.java (Line 544)**: When `request.getImageFormat()` returned an empty string (`""`), the code treated it as non-null and used it instead of the default format from configuration.

```java
// OLD CODE (BUGGY)
String format = request.getImageFormat() != null ? request.getImageFormat() : 
    properties.getImageRendering().getFormat();
```

2. **PdfToImageService.java**: The service methods didn't validate the format parameter, so when an empty string was passed, it generated filenames like `page_0001.` (with trailing dot but no extension).

```java
// Generates invalid filename when format is empty: "page_0001."
String imageFileName = String.format("page_%04d.%s", pageNumber, format.toLowerCase());
```

## Solution

### 1. Fixed PdfUploadService (Line 544-545)

Added validation to check if `imageFormat` is not only non-null but also non-empty:

```java
// NEW CODE (FIXED)
String format = (request.getImageFormat() != null && !request.getImageFormat().trim().isEmpty()) 
    ? request.getImageFormat() : properties.getImageRendering().getFormat();
```

### 2. Added Defensive Checks in PdfToImageService

Added format validation at the beginning of all conversion methods:
- `convertPdfToImages()` (Line 74-77)
- `convertSpecificPagesToImages()` (Line 139-142)
- `convertPagesToImagesAndUpload()` (Line 214-217)

```java
if (format == null || format.trim().isEmpty()) {
    format = properties.getImageRendering().getFormat();
    log.warn("Format is null or empty, using default: {}", format);
}
```

## Impact

- **Before Fix**: Empty `imageFormat` parameter caused file creation to fail with `NoSuchFileException`
- **After Fix**: Empty `imageFormat` parameter automatically falls back to default format (PNG)

## Testing

To verify the fix:

1. **Test with empty format parameter:**
```bash
curl -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@test.pdf" \
  -F "businessId=TEST001" \
  -F "userId=user001" \
  -F "tenantId=tenant001" \
  -F "imageFormat="
```

Expected: Conversion succeeds using default PNG format

2. **Test with null format (not provided):**
```bash
curl -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@test.pdf" \
  -F "businessId=TEST001" \
  -F "userId=user001" \
  -F "tenantId=tenant001"
```

Expected: Conversion succeeds using default PNG format

3. **Test with valid format:**
```bash
curl -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@test.pdf" \
  -F "businessId=TEST001" \
  -F "userId=user001" \
  -F "tenantId=tenant001" \
  -F "imageFormat=JPG"
```

Expected: Conversion succeeds using JPG format

## Files Modified

1. `/src/main/java/com/example/minioupload/service/PdfUploadService.java`
   - Line 544-545: Enhanced format validation

2. `/src/main/java/com/example/minioupload/service/PdfToImageService.java`
   - Line 74-77: Added format validation in `convertPdfToImages()`
   - Line 139-142: Added format validation in `convertSpecificPagesToImages()`
   - Line 214-217: Added format validation in `convertPagesToImagesAndUpload()`

## Related Configuration

Default format is configured in `application.yml`:
```yaml
pdf:
  conversion:
    image-rendering:
      format: PNG  # Default format used when imageFormat is null or empty
      dpi: 300
```

## Defensive Programming Best Practices Applied

1. ✅ Validate string parameters for both null AND empty values
2. ✅ Use trim() to handle whitespace-only strings
3. ✅ Add defensive checks at service method boundaries
4. ✅ Log warnings when falling back to default values
5. ✅ Provide clear error messages for troubleshooting
