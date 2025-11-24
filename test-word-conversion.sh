#!/bin/bash

# Word to PDF Conversion Test Script
# Tests different conversion modes for Word documents

set -e

BASE_URL="http://localhost:8080"
API_ENDPOINT="$BASE_URL/api/pdf/convert"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}Word to PDF Conversion Test${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""

# Check if file exists
if [ ! -f "$1" ]; then
    echo -e "${RED}Error: Please provide a Word document as argument${NC}"
    echo "Usage: $0 <word-file.docx>"
    echo ""
    echo "Example:"
    echo "  $0 test-document.docx"
    exit 1
fi

WORD_FILE="$1"
FILENAME=$(basename "$WORD_FILE")
echo -e "${YELLOW}Testing file: $FILENAME${NC}"
echo ""

# Test function
test_conversion() {
    local convert_to_images=$1
    local test_name=$2
    
    echo -e "${YELLOW}Test: $test_name${NC}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_ENDPOINT" \
        -F "file=@$WORD_FILE" \
        -F "convertToImages=$convert_to_images")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" -eq 200 ]; then
        echo -e "${GREEN}✓ Conversion submitted successfully${NC}"
        
        JOB_ID=$(echo "$BODY" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
        echo "Job ID: $JOB_ID"
        
        # Poll for progress
        echo -n "Progress: "
        while true; do
            PROGRESS=$(curl -s "$BASE_URL/api/pdf/progress/$JOB_ID")
            STATUS=$(echo "$PROGRESS" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            PERCENTAGE=$(echo "$PROGRESS" | grep -o '"progressPercentage":[0-9]*' | cut -d':' -f2)
            
            echo -n "$PERCENTAGE% "
            
            if [ "$STATUS" = "COMPLETED" ]; then
                echo ""
                echo -e "${GREEN}✓ Conversion completed!${NC}"
                
                # Show result details
                RESULT=$(curl -s "$BASE_URL/api/pdf/result/$JOB_ID")
                echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
                break
            elif [ "$STATUS" = "FAILED" ]; then
                echo ""
                echo -e "${RED}✗ Conversion failed${NC}"
                echo "$PROGRESS" | python3 -m json.tool 2>/dev/null || echo "$PROGRESS"
                break
            fi
            
            sleep 2
        done
    else
        echo -e "${RED}✗ HTTP $HTTP_CODE - Conversion failed${NC}"
        echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    fi
    
    echo ""
    echo "---"
    echo ""
}

# Test 1: Basic conversion without images
test_conversion "false" "Basic PDF conversion (no images)"

# Test 2: Conversion with images
test_conversion "true" "PDF conversion with page images"

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo "Note: The conversion method used depends on your configuration:"
echo "  - LIBREOFFICE_FIRST (default): Uses LibreOffice if available, falls back to image rendering"
echo "  - LIBREOFFICE_ONLY: Requires LibreOffice, fails if not available"
echo "  - IMAGE_ONLY: Always uses image rendering (larger PDF, no text selection)"
echo "  - LEGACY_TEXT: Uses text extraction (may lose formatting)"
echo ""
echo "To change the conversion mode, set in application.yml:"
echo "  pdf.conversion.word-conversion-mode: LIBREOFFICE_FIRST"
