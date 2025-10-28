# Quick Start Guide

## Prerequisites

1. **Java 17** or higher installed
2. **Maven 3.6+** installed (or use the setup script)
3. **MinIO** server running and accessible

## Verify MinIO Setup

Ensure your MinIO server is running and the bucket exists:

```bash
# Check MinIO is accessible
curl http://192.168.0.245:9000

# Verify bucket using mc CLI (if available)
mc ls myminio/remote-consent
```

## Running the Application

### Option 1: Using Maven

```bash
# Build and run
mvn spring-boot:run
```

### Option 2: Using the setup script

```bash
# Run setup (downloads Maven if needed)
./setup.sh

# Start the application
mvn spring-boot:run
```

### Option 3: Build JAR and run

```bash
# Package the application
mvn clean package

# Run the JAR
java -jar target/minio-multipart-upload-1.0.0.jar
```

## Quick Test

Once the application is running (default port 8080):

### Test 1: Initialize Upload

```bash
curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "sample.mp4",
    "size": 100000000,
    "contentType": "video/mp4",
    "chunkSize": 8388608
  }'
```

Expected response:
```json
{
  "uploadId": "abc123...",
  "objectKey": "uploads/uuid/sample.mp4",
  "partSize": 8388608,
  "minPartNumber": 1,
  "maxPartNumber": 12
}
```

### Test 2: Get Presigned URLs

```bash
# Replace UPLOAD_ID and OBJECT_KEY from previous response
curl -X GET "http://localhost:8080/api/uploads/UPLOAD_ID/parts?objectKey=OBJECT_KEY&startPartNumber=1&endPartNumber=1"
```

### Test 3: Complete Workflow

Use the provided test script with a real video file:

```bash
./test-upload.sh /path/to/your/video.mp4
```

## Testing with Postman

1. Import `postman-collection.json` into Postman
2. Ensure base_url variable is set to `http://localhost:8080`
3. Run "1. Initialize Upload" - this will auto-populate upload_id and object_key
4. Run "2. Get Presigned URLs"
5. Use the returned URLs to upload parts (use PUT request with binary body)
6. Run "3. Get Upload Status" to verify uploaded parts
7. Run "4. Complete Upload" with the correct part ETags

## Configuration

### Using Environment Variables

```bash
# Set environment variables
export S3_ENDPOINT=http://192.168.0.245:9000
export S3_ACCESS_KEY=minioadmin
export S3_SECRET_KEY=minioadmin123
export S3_BUCKET=remote-consent

# Run application
mvn spring-boot:run
```

### Using application.yml

Edit `src/main/resources/application.yml` directly with your MinIO configuration.

### Using .env file (for Docker)

Copy `.env.example` to `.env` and modify as needed.

## Accessing H2 Console

The H2 database console is available at:

```
http://localhost:8080/h2-console
```

Connection details:
- **JDBC URL**: `jdbc:h2:mem:uploaddb`
- **Username**: `sa`
- **Password**: (leave empty)

## Common Issues

### Connection Refused

- Verify MinIO is running: `curl http://192.168.0.245:9000`
- Check firewall settings
- Verify the endpoint in configuration

### Bucket Not Found

Create the bucket in MinIO:

```bash
# Using mc CLI
mc mb myminio/remote-consent
```

Or through MinIO web console at http://192.168.0.245:9000

### Invalid Credentials

Verify access key and secret key match your MinIO configuration.

## Next Steps

- Read the full [README.md](README.md) for detailed API documentation
- Review [application.yml](src/main/resources/application.yml) for configuration options
- Check [test-upload.sh](test-upload.sh) for a complete upload workflow example
- Import [postman-collection.json](postman-collection.json) for interactive testing

## Production Considerations

This is a **demo application**. For production use:

1. Implement authentication and authorization
2. Use a persistent database (PostgreSQL, MySQL)
3. Add request rate limiting
4. Implement proper logging and monitoring
5. Secure credential management (vault, secrets manager)
6. Restrict CORS to specific origins
7. Add file validation and virus scanning
8. Implement proper error handling and recovery
9. Add metrics and health checks
10. Use HTTPS for all communications
