#!/bin/bash

# ============================================================
# 异步上传断点续传测试脚本
# ============================================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="http://localhost:8080/api/uploads"
TEST_VIDEO="test-video.mp4"

# 函数：打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# 函数：检查jq是否安装
check_jq() {
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed. Please install jq first."
        echo "Ubuntu/Debian: sudo apt-get install jq"
        echo "CentOS/RHEL: sudo yum install jq"
        echo "macOS: brew install jq"
        exit 1
    fi
}

# 函数：检查测试视频文件
check_test_video() {
    if [ ! -f "$TEST_VIDEO" ]; then
        print_warning "Test video file not found: $TEST_VIDEO"
        print_info "Creating a test video file (10MB)..."
        dd if=/dev/urandom of=$TEST_VIDEO bs=1M count=10 2>/dev/null
        if [ $? -eq 0 ]; then
            print_success "Test video file created: $TEST_VIDEO"
        else
            print_error "Failed to create test video file"
            exit 1
        fi
    else
        print_success "Test video file found: $TEST_VIDEO"
    fi
}

# 函数：提交上传任务
submit_upload() {
    print_info "Submitting async upload task..."
    
    RESPONSE=$(curl -s -X POST "$API_BASE_URL/async" \
        -F "file=@$TEST_VIDEO" \
        -F "chunkSize=5242880")
    
    if [ $? -ne 0 ]; then
        print_error "Failed to submit upload task"
        return 1
    fi
    
    JOB_ID=$(echo $RESPONSE | jq -r '.jobId')
    
    if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
        print_error "Failed to get job ID from response"
        echo "Response: $RESPONSE"
        return 1
    fi
    
    print_success "Upload task submitted successfully"
    print_info "Job ID: $JOB_ID"
    
    echo $JOB_ID
}

# 函数：查询上传进度
get_progress() {
    local job_id=$1
    
    curl -s "$API_BASE_URL/async/$job_id/progress"
}

# 函数：监控上传进度（有限时间）
monitor_progress_limited() {
    local job_id=$1
    local max_checks=10
    local check_count=0
    
    print_info "Monitoring upload progress (max $max_checks checks)..."
    
    while [ $check_count -lt $max_checks ]; do
        PROGRESS=$(get_progress $job_id)
        STATUS=$(echo $PROGRESS | jq -r '.status')
        PERCENT=$(echo $PROGRESS | jq -r '.progress')
        MESSAGE=$(echo $PROGRESS | jq -r '.message')
        UPLOADED=$(echo $PROGRESS | jq -r '.uploadedParts')
        TOTAL=$(echo $PROGRESS | jq -r '.totalParts')
        
        echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} Status: ${YELLOW}$STATUS${NC}, Progress: ${GREEN}$PERCENT%${NC}, Parts: $UPLOADED/$TOTAL"
        echo "         Message: $MESSAGE"
        
        if [ "$STATUS" == "COMPLETED" ]; then
            print_success "Upload completed successfully!"
            return 0
        fi
        
        if [ "$STATUS" == "FAILED" ]; then
            print_error "Upload failed!"
            return 1
        fi
        
        check_count=$((check_count + 1))
        sleep 2
    done
    
    print_info "Reached max checks limit, upload still in progress"
    return 2
}

# 函数：恢复上传
resume_upload() {
    local job_id=$1
    
    print_info "Resuming upload task: $job_id"
    
    RESPONSE=$(curl -s -X POST "$API_BASE_URL/async/$job_id/resume")
    
    if [ $? -ne 0 ]; then
        print_error "Failed to resume upload"
        return 1
    fi
    
    STATUS=$(echo $RESPONSE | jq -r '.status')
    
    if [ "$STATUS" == "null" ] || [ -z "$STATUS" ]; then
        print_error "Failed to resume upload"
        echo "Response: $RESPONSE"
        return 1
    fi
    
    print_success "Upload resumed successfully"
    print_info "Status: $STATUS"
    
    return 0
}

# 函数：监控上传直到完成
monitor_until_complete() {
    local job_id=$1
    
    print_info "Monitoring upload until completion..."
    
    while true; do
        PROGRESS=$(get_progress $job_id)
        STATUS=$(echo $PROGRESS | jq -r '.status')
        PERCENT=$(echo $PROGRESS | jq -r '.progress')
        MESSAGE=$(echo $PROGRESS | jq -r '.message')
        UPLOADED=$(echo $PROGRESS | jq -r '.uploadedParts')
        TOTAL=$(echo $PROGRESS | jq -r '.totalParts')
        
        echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} Status: ${YELLOW}$STATUS${NC}, Progress: ${GREEN}$PERCENT%${NC}, Parts: $UPLOADED/$TOTAL"
        
        if [ "$STATUS" == "COMPLETED" ]; then
            print_success "Upload completed successfully!"
            echo ""
            print_info "Upload Response:"
            echo $PROGRESS | jq '.uploadResponse'
            return 0
        fi
        
        if [ "$STATUS" == "FAILED" ]; then
            print_error "Upload failed!"
            echo "Message: $MESSAGE"
            return 1
        fi
        
        if [ "$STATUS" == "PAUSED" ]; then
            print_warning "Upload paused, attempting to resume..."
            resume_upload $job_id
        fi
        
        sleep 3
    done
}

# ============================================================
# 测试场景
# ============================================================

# 测试场景1：正常上传流程
test_normal_upload() {
    echo ""
    echo "============================================================"
    echo "Test Case 1: Normal Upload Flow"
    echo "============================================================"
    
    JOB_ID=$(submit_upload)
    if [ $? -ne 0 ]; then
        print_error "Test case 1 failed: Failed to submit upload"
        return 1
    fi
    
    monitor_until_complete $JOB_ID
    
    if [ $? -eq 0 ]; then
        print_success "Test case 1 passed: Normal upload completed"
        return 0
    else
        print_error "Test case 1 failed: Upload did not complete"
        return 1
    fi
}

# 测试场景2：模拟中断后恢复
test_resume_upload() {
    echo ""
    echo "============================================================"
    echo "Test Case 2: Resume Upload After Interruption"
    echo "============================================================"
    
    JOB_ID=$(submit_upload)
    if [ $? -ne 0 ]; then
        print_error "Test case 2 failed: Failed to submit upload"
        return 1
    fi
    
    # 监控一段时间后模拟中断
    print_info "Monitoring upload for a short period..."
    monitor_progress_limited $JOB_ID
    
    PROGRESS=$(get_progress $JOB_ID)
    STATUS=$(echo $PROGRESS | jq -r '.status')
    
    if [ "$STATUS" == "COMPLETED" ]; then
        print_warning "Upload already completed, cannot test resume"
        return 0
    fi
    
    print_info "Current status: $STATUS"
    print_warning "Simulating interruption..."
    print_info "In a real scenario, you would:"
    print_info "  1. Restart the application (tasks will be marked as PAUSED)"
    print_info "  2. Or wait for the task to fail"
    print_info "  3. Then call the resume API"
    
    # 检查当前状态
    if [ "$STATUS" == "PAUSED" ] || [ "$STATUS" == "FAILED" ]; then
        print_info "Task is in $STATUS state, attempting to resume..."
        resume_upload $JOB_ID
        
        if [ $? -eq 0 ]; then
            print_info "Resume successful, monitoring until completion..."
            monitor_until_complete $JOB_ID
            
            if [ $? -eq 0 ]; then
                print_success "Test case 2 passed: Resume upload completed"
                return 0
            else
                print_error "Test case 2 failed: Resume upload did not complete"
                return 1
            fi
        else
            print_error "Test case 2 failed: Failed to resume upload"
            return 1
        fi
    else
        print_info "Task is still $STATUS, cannot resume at this time"
        print_info "Continuing to monitor..."
        monitor_until_complete $JOB_ID
        
        if [ $? -eq 0 ]; then
            print_success "Test case 2 completed (no resume needed)"
            return 0
        else
            print_error "Test case 2 failed"
            return 1
        fi
    fi
}

# 测试场景3：查询不存在的任务
test_query_nonexistent() {
    echo ""
    echo "============================================================"
    echo "Test Case 3: Query Non-existent Task"
    echo "============================================================"
    
    FAKE_JOB_ID="00000000-0000-0000-0000-000000000000"
    print_info "Querying non-existent job: $FAKE_JOB_ID"
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_BASE_URL/async/$FAKE_JOB_ID/progress")
    
    if [ "$HTTP_CODE" == "404" ]; then
        print_success "Test case 3 passed: Got expected 404 response"
        return 0
    else
        print_error "Test case 3 failed: Expected 404, got $HTTP_CODE"
        return 1
    fi
}

# ============================================================
# 主程序
# ============================================================

main() {
    echo "============================================================"
    echo "Async Upload Resume Test Script"
    echo "============================================================"
    
    # 检查依赖
    check_jq
    check_test_video
    
    # 显示菜单
    echo ""
    echo "Select test case:"
    echo "1) Normal upload flow"
    echo "2) Resume upload after interruption"
    echo "3) Query non-existent task"
    echo "4) Run all tests"
    echo "5) Exit"
    echo ""
    read -p "Enter your choice [1-5]: " choice
    
    case $choice in
        1)
            test_normal_upload
            ;;
        2)
            test_resume_upload
            ;;
        3)
            test_query_nonexistent
            ;;
        4)
            test_normal_upload
            echo ""
            sleep 2
            test_query_nonexistent
            echo ""
            sleep 2
            print_info "Test case 2 (resume) requires manual application restart"
            print_info "Please restart the application and run test case 2 separately"
            ;;
        5)
            print_info "Exiting..."
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac
    
    echo ""
    echo "============================================================"
    echo "Test completed"
    echo "============================================================"
}

# 运行主程序
main
