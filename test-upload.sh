#!/bin/bash

# Test script for multipart upload workflow
# This script demonstrates a complete upload process

BASE_URL="http://localhost:8080"
FILE_PATH="${1:-test-video.mp4}"
CHUNK_SIZE=8388608  # 8MB

if [ ! -f "$FILE_PATH" ]; then
    echo "Error: File $FILE_PATH not found"
    echo "Usage: $0 <path-to-video-file>"
    exit 1
fi

FILE_NAME=$(basename "$FILE_PATH")
FILE_SIZE=$(stat -f%z "$FILE_PATH" 2>/dev/null || stat -c%s "$FILE_PATH")
CONTENT_TYPE="video/mp4"

echo "=== MinIO Multipart Upload Test ==="
echo "File: $FILE_NAME"
echo "Size: $FILE_SIZE bytes"
echo ""

# Step 1: Initialize upload
echo "Step 1: Initializing upload..."
INIT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/uploads/init" \
  -F "file=@$FILE_PATH" \
  -F "chunkSize=$CHUNK_SIZE")

if [ $? -ne 0 ]; then
    echo "Error: Failed to initialize upload"
    exit 1
fi

echo "Response: $INIT_RESPONSE"

UPLOAD_ID=$(echo "$INIT_RESPONSE" | grep -o '"uploadId":"[^"]*"' | cut -d'"' -f4)
OBJECT_KEY=$(echo "$INIT_RESPONSE" | grep -o '"objectKey":"[^"]*"' | cut -d'"' -f4)
MAX_PART_NUMBER=$(echo "$INIT_RESPONSE" | grep -o '"maxPartNumber":[0-9]*' | cut -d':' -f2)

echo "Upload ID: $UPLOAD_ID"
echo "Object Key: $OBJECT_KEY"
echo "Max Parts: $MAX_PART_NUMBER"
echo ""

if [ -z "$UPLOAD_ID" ] || [ -z "$OBJECT_KEY" ]; then
    echo "Error: Failed to parse init response"
    exit 1
fi

# Step 2: Split file into chunks
echo "Step 2: Splitting file into chunks..."
TMP_DIR=$(mktemp -d)
split -b $CHUNK_SIZE "$FILE_PATH" "$TMP_DIR/part_"
PARTS=($(ls "$TMP_DIR"/part_* | sort))
echo "Created ${#PARTS[@]} parts"
echo ""

# Step 3: Upload parts
echo "Step 3: Uploading parts..."
COMPLETED_PARTS="["

for i in "${!PARTS[@]}"; do
    PART_NUMBER=$((i + 1))
    PART_FILE="${PARTS[$i]}"
    
    # Get presigned URL for this part
    echo "  Getting presigned URL for part $PART_NUMBER..."
    PRESIGNED_RESPONSE=$(curl -s -X GET \
        "$BASE_URL/api/uploads/$UPLOAD_ID/parts?objectKey=$OBJECT_KEY&startPartNumber=$PART_NUMBER&endPartNumber=$PART_NUMBER")
    
    # 检查是否成功获取响应
    if [ $? -ne 0 ]; then
        echo "Error: Failed to get presigned URL response for part $PART_NUMBER"
        exit 1
    fi
    
    PRESIGNED_URL=$(echo "$PRESIGNED_RESPONSE" | grep -o '"url":"[^"]*"' | cut -d'"' -f4 | sed 's/\\//g')
    
    if [ -z "$PRESIGNED_URL" ]; then
        echo "Error: Failed to get presigned URL for part $PART_NUMBER"
        echo "Response was: $PRESIGNED_RESPONSE"
        exit 1
    fi
    
    # Upload part
    echo "  Uploading part $PART_NUMBER..."
    UPLOAD_RESPONSE=$(curl -s -X PUT "$PRESIGNED_URL" \
        --upload-file "$PART_FILE" \
        -w "\nHTTP_CODE:%{http_code}" \
        -D -)
    
    ETAG=$(echo "$UPLOAD_RESPONSE" | grep -i "ETag:" | cut -d' ' -f2 | tr -d '\r\n' | tr -d '"')
    HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | grep "HTTP_CODE:" | cut -d':' -f2)
    
    if [ "$HTTP_CODE" != "200" ]; then
        echo "Error: Failed to upload part $PART_NUMBER (HTTP $HTTP_CODE)"
        echo "Upload response: $UPLOAD_RESPONSE"
        exit 1
    fi
    
    if [ -z "$ETAG" ]; then
        echo "Error: No ETag received for part $PART_NUMBER"
        echo "Upload response: $UPLOAD_RESPONSE"
        exit 1
    fi
    
    echo "  Part $PART_NUMBER uploaded (ETag: $ETAG)"
    
    # Add to completed parts list
    if [ $PART_NUMBER -gt 1 ]; then
        COMPLETED_PARTS+=","
    fi
    COMPLETED_PARTS+="{\"partNumber\":$PART_NUMBER,\"eTag\":\"$ETAG\"}"
done

COMPLETED_PARTS+="]"
echo ""

# Step 4: Check upload status
echo "Step 4: Checking upload status..."
STATUS_RESPONSE=$(curl -s -X GET \
    "$BASE_URL/api/uploads/$UPLOAD_ID/status?objectKey=$OBJECT_KEY")
echo "Status: $STATUS_RESPONSE"
echo ""

# Step 5: Complete upload
echo "Step 5: Completing upload..."
COMPLETE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/uploads/complete" \
  -H "Content-Type: application/json" \
  -d '{
    "uploadId": "'"$UPLOAD_ID"'",
    "objectKey": "'"$OBJECT_KEY"'",
    "parts": '"$COMPLETED_PARTS"'
  }')

echo "Complete Response: $COMPLETE_RESPONSE"
echo ""

DOWNLOAD_URL=$(echo "$COMPLETE_RESPONSE" | grep -o '"downloadUrl":"[^"]*"' | cut -d'"' -f4)

if [ -n "$DOWNLOAD_URL" ]; then
    echo "=== Upload Successful ==="
    echo "Download URL: $DOWNLOAD_URL"
else
    echo "=== Upload may have failed ==="
fi

# Cleanup
rm -rf "$TMP_DIR"
echo ""
echo "Temporary files cleaned up"
