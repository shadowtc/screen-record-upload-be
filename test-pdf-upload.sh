#!/bin/bash

# Test script for PDF upload and image conversion API

BASE_URL="http://localhost:8080/api/pdf"

echo "=== PDF Upload and Image Conversion Test ==="
echo ""

# Check if a PDF file is provided as argument
if [ -z "$1" ]; then
    echo "Usage: $0 <pdf-file-path> [imageDpi] [imageFormat]"
    echo "Example: $0 test.pdf 300 png"
    exit 1
fi

PDF_FILE="$1"
IMAGE_DPI="${2:-300}"
IMAGE_FORMAT="${3:-png}"

if [ ! -f "$PDF_FILE" ]; then
    echo "Error: File not found: $PDF_FILE"
    exit 1
fi

echo "Testing PDF: $PDF_FILE"
echo "Image DPI: $IMAGE_DPI"
echo "Image Format: $IMAGE_FORMAT"
echo ""

# Upload PDF
echo "Step 1: Uploading PDF file..."
UPLOAD_RESPONSE=$(curl -s -X POST \
  "$BASE_URL/upload" \
  -F "file=@$PDF_FILE" \
  -F "imageDpi=$IMAGE_DPI" \
  -F "imageFormat=$IMAGE_FORMAT")

echo "Upload Response:"
echo "$UPLOAD_RESPONSE" | jq '.'
echo ""

# Extract taskId
TASK_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.taskId')

if [ "$TASK_ID" == "null" ] || [ -z "$TASK_ID" ]; then
    echo "Error: Failed to get taskId from response"
    exit 1
fi

echo "Task ID: $TASK_ID"
echo ""

# Monitor progress
echo "Step 2: Monitoring conversion progress..."
MAX_ATTEMPTS=60
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    PROGRESS_RESPONSE=$(curl -s -X GET "$BASE_URL/upload/progress/$TASK_ID")
    STATUS=$(echo "$PROGRESS_RESPONSE" | jq -r '.status')
    PERCENTAGE=$(echo "$PROGRESS_RESPONSE" | jq -r '.progressPercentage')
    PHASE=$(echo "$PROGRESS_RESPONSE" | jq -r '.currentPhase')
    
    echo "[$ATTEMPT] Status: $STATUS | Progress: $PERCENTAGE% | Phase: $PHASE"
    
    if [ "$STATUS" == "COMPLETED" ]; then
        echo ""
        echo "Conversion completed successfully!"
        echo "Final Progress:"
        echo "$PROGRESS_RESPONSE" | jq '.'
        break
    fi
    
    if [ "$STATUS" == "FAILED" ]; then
        echo ""
        echo "Conversion failed!"
        echo "$PROGRESS_RESPONSE" | jq '.'
        exit 1
    fi
    
    ATTEMPT=$((ATTEMPT + 1))
    sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo "Timeout: Conversion did not complete in expected time"
    exit 1
fi

echo ""

# Get first page of images
echo "Step 3: Retrieving first 5 page images..."
IMAGES_RESPONSE=$(curl -s -X GET "$BASE_URL/images/$TASK_ID?startPage=1&pageSize=5")
echo "Images Response:"
echo "$IMAGES_RESPONSE" | jq '.'
echo ""

# Get second page of images
echo "Step 4: Retrieving next 5 page images..."
IMAGES_RESPONSE=$(curl -s -X GET "$BASE_URL/images/$TASK_ID?startPage=6&pageSize=5")
echo "Images Response:"
echo "$IMAGES_RESPONSE" | jq '.'
echo ""

echo "=== Test completed successfully! ==="
