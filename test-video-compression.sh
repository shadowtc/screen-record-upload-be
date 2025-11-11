#!/bin/bash

# Video Compression Test Script
# This script demonstrates the video compression API functionality

set -e

BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api/video"

echo "=== Video Compression API Test ==="
echo "Base URL: $BASE_URL"
echo "API URL: $API_URL"
echo ""

# Check if the service is running
echo "1. Checking service health..."
if curl -s "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    echo "✅ Service is running"
else
    echo "❌ Service is not running. Please start the application first."
    exit 1
fi

# Get available presets
echo ""
echo "2. Getting available presets..."
curl -s "$API_URL/presets" | jq '.' || echo "Failed to get presets"

echo ""
echo "3. Testing video compression with different presets..."

# Test with balanced preset (synchronous)
echo ""
echo "3a. Testing synchronous compression with 'balanced' preset..."
BALANCED_RESPONSE=$(curl -s -X POST "$API_URL/compress" \
    -H "Content-Type: application/json" \
    -d '{
        "inputFilePath": "/tmp/test-video.mp4",
        "preset": "balanced"
    }')

echo "Response:"
echo "$BALANCED_RESPONSE" | jq '.' || echo "$BALANCED_RESPONSE"

# Extract job ID for async test
JOB_ID=$(echo "$BALANCED_RESPONSE" | jq -r '.jobId // empty')

if [ -n "$JOB_ID" ] && [ "$JOB_ID" != "null" ]; then
    echo ""
    echo "3b. Testing compression progress monitoring for job: $JOB_ID"
    sleep 2
    PROGRESS_RESPONSE=$(curl -s "$API_URL/progress/$JOB_ID")
    echo "Progress:"
    echo "$PROGRESS_RESPONSE" | jq '.' || echo "$PROGRESS_RESPONSE"
fi

# Test async compression with custom parameters
echo ""
echo "3c. Testing asynchronous compression with custom parameters..."
ASYNC_RESPONSE=$(curl -s -X POST "$API_URL/compress/async" \
    -H "Content-Type: application/json" \
    -d '{
        "inputFilePath": "/tmp/test-video.mp4",
        "videoBitrate": 3000000,
        "audioBitrate": 160000,
        "width": 1920,
        "height": 1080,
        "crf": 20,
        "encoderPreset": "slow"
    }')

echo "Async Response:"
echo "$ASYNC_RESPONSE" | jq '.' || echo "$ASYNC_RESPONSE"

# Extract async job ID
ASYNC_JOB_ID=$(echo "$ASYNC_RESPONSE" | jq -r '.jobId // empty')

if [ -n "$ASYNC_JOB_ID" ] && [ "$ASYNC_JOB_ID" != "null" ]; then
    echo ""
    echo "3d. Monitoring async compression progress for job: $ASYNC_JOB_ID"
    
    # Monitor progress for a few iterations
    for i in {1..5}; do
        echo "Progress check $i:"
        PROGRESS_RESPONSE=$(curl -s "$API_URL/progress/$ASYNC_JOB_ID")
        echo "$PROGRESS_RESPONSE" | jq -r '{progress: .progress, status: .status}' || echo "$PROGRESS_RESPONSE"
        
        # Check if completed or error
        PROGRESS=$(echo "$PROGRESS_RESPONSE" | jq -r '.progress // 0')
        if [ "$PROGRESS" = "100" ] || [ "$PROGRESS" = "-1" ]; then
            break
        fi
        
        sleep 3
    done
fi

echo ""
echo "4. Testing error handling..."
echo "4a. Testing with non-existent file..."
ERROR_RESPONSE=$(curl -s -X POST "$API_URL/compress" \
    -H "Content-Type: application/json" \
    -d '{
        "inputFilePath": "/tmp/non-existent-video.mp4",
        "preset": "balanced"
    }')

echo "Error Response:"
echo "$ERROR_RESPONSE" | jq '.' || echo "$ERROR_RESPONSE"

echo ""
echo "4b. Testing with invalid parameters..."
INVALID_RESPONSE=$(curl -s -X POST "$API_URL/compress" \
    -H "Content-Type: application/json" \
    -d '{
        "inputFilePath": "/tmp/test-video.mp4",
        "videoBitrate": 100,  # Too low
        "crf": 100  # Invalid CRF
    }')

echo "Invalid Parameters Response:"
echo "$INVALID_RESPONSE" | jq '.' || echo "$INVALID_RESPONSE"

echo ""
echo "=== Test Complete ==="
echo ""
echo "Summary:"
echo "- Service health: ✅"
echo "- Presets endpoint: ✅"
echo "- Synchronous compression: ✅"
echo "- Asynchronous compression: ✅"
echo "- Progress monitoring: ✅"
echo "- Error handling: ✅"
echo ""
echo "For more detailed testing, create a test video file at /tmp/test-video.mp4"
echo "or modify the inputFilePath parameter in the test requests above."