#!/bin/bash

# Debug script for preset download issues
# This script helps identify where the problem occurs in the preset generation pipeline

set -e

echo "=== Neural Synth Preset Debug Script ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if services are running
print_status "Checking service status..."

# Check Java backend
if curl -s http://localhost:8080/v1/health/live > /dev/null; then
    print_success "Java backend is running on port 8080"
else
    print_error "Java backend is not running on port 8080"
fi

# Check BentoML service
if curl -s http://localhost:3000/healthz > /dev/null; then
    print_success "BentoML service is running on port 3000"
else
    print_warning "BentoML service is not running on port 3000"
fi

# Check MySQL
if mysql -h localhost -u readwrite -preadwrite -e "SELECT 1;" > /dev/null 2>&1; then
    print_success "MySQL is running and accessible"
else
    print_warning "MySQL is not accessible"
fi

# Check MongoDB
if curl -s http://localhost:27017 > /dev/null; then
    print_success "MongoDB is running on port 27017"
else
    print_warning "MongoDB is not running on port 27017"
fi

echo ""
print_status "Testing preset generation pipeline..."

# Create a test audio file if it doesn't exist
TEST_AUDIO="test_audio.wav"
if [ ! -f "$TEST_AUDIO" ]; then
    print_status "Creating test audio file..."
    # Generate a simple sine wave using ffmpeg
    ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -ar 16000 "$TEST_AUDIO" -y > /dev/null 2>&1 || {
        print_warning "ffmpeg not available, using existing test file if available"
    }
fi

if [ -f "$TEST_AUDIO" ]; then
    print_success "Test audio file ready: $TEST_AUDIO"
    
    # Compress the audio
    print_status "Compressing test audio..."
    gzip -c "$TEST_AUDIO" > "${TEST_AUDIO}.gz"
    
    # Test the inference endpoint
    print_status "Testing inference endpoint..."
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/octet-stream" \
        --data-binary "@${TEST_AUDIO}.gz" \
        http://localhost:8080/v1/models/vital/infer)
    
    if [ $? -eq 0 ]; then
        print_success "Inference request sent successfully"
        echo "Response: $RESPONSE"
        
        # Extract request ID
        REQUEST_ID=$(echo "$RESPONSE" | grep -o '"request_id":"[^"]*"' | cut -d'"' -f4)
        if [ -n "$REQUEST_ID" ]; then
            print_success "Request ID: $REQUEST_ID"
            
            # Poll for status
            print_status "Polling for status updates..."
            for i in {1..30}; do
                STATUS_RESPONSE=$(curl -s "http://localhost:8080/v1/infer-audio/status/$REQUEST_ID")
                STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
                print_status "Status: $STATUS"
                
                if [ "$STATUS" = "DONE" ]; then
                    print_success "Inference completed successfully!"
                    
                    # Try to download the preset
                    print_status "Downloading preset..."
                    curl -s -o "preset_$REQUEST_ID.vital" \
                        "http://localhost:8080/v1/preset/$REQUEST_ID"
                    
                    if [ -f "preset_$REQUEST_ID.vital" ]; then
                        PRESET_SIZE=$(stat -c%s "preset_$REQUEST_ID.vital")
                        print_success "Preset downloaded: preset_$REQUEST_ID.vital ($PRESET_SIZE bytes)"
                        
                        # Validate preset file
                        print_status "Validating preset file..."
                        if file "preset_$REQUEST_ID.vital" | grep -q "JSON"; then
                            print_success "Preset appears to be valid JSON"
                            
                            # Check for Vital-specific fields
                            if grep -q '"settings"' "preset_$REQUEST_ID.vital"; then
                                print_success "Preset contains 'settings' field"
                            else
                                print_warning "Preset missing 'settings' field"
                            fi
                            
                            if grep -q '"wavetables"' "preset_$REQUEST_ID.vital"; then
                                print_success "Preset contains 'wavetables' field"
                            else
                                print_warning "Preset missing 'wavetables' field"
                            fi
                            
                        else
                            print_error "Preset is not valid JSON"
                            echo "File type: $(file preset_$REQUEST_ID.vital)"
                        fi
                        
                        # Show first few lines
                        echo ""
                        print_status "Preset file preview (first 10 lines):"
                        head -10 "preset_$REQUEST_ID.vital"
                        
                    else
                        print_error "Failed to download preset"
                    fi
                    break
                elif [ "$STATUS" = "ERROR" ]; then
                    print_error "Inference failed with error"
                    break
                fi
                
                sleep 2
            done
            
            if [ "$STATUS" != "DONE" ] && [ "$STATUS" != "ERROR" ]; then
                print_warning "Inference did not complete within timeout"
            fi
            
        else
            print_error "Could not extract request ID from response"
        fi
    else
        print_error "Failed to send inference request"
    fi
    
    # Cleanup
    rm -f "${TEST_AUDIO}.gz"
    
else
    print_warning "No test audio file available"
fi

echo ""
print_status "Checking application logs..."

# Check if running in Docker
if [ -f /proc/1/cgroup ] && grep -q docker /proc/1/cgroup; then
    print_status "Running in Docker container"
    echo "To view logs: docker logs <container_name>"
else
    print_status "Running locally"
    echo "Check application logs in the console or log files"
fi

echo ""
print_status "Debug information:"

# System info
echo "Java version: $(java -version 2>&1 | head -1)"
echo "Python version: $(python3 --version 2>&1 || echo 'Python3 not found')"
echo "Docker version: $(docker --version 2>&1 || echo 'Docker not found')"

# Network connectivity
echo ""
print_status "Network connectivity test:"
if curl -s --connect-timeout 5 http://localhost:8080 > /dev/null; then
    print_success "Backend (8080) is reachable"
else
    print_error "Backend (8080) is not reachable"
fi

if curl -s --connect-timeout 5 http://localhost:3000 > /dev/null; then
    print_success "BentoML (3000) is reachable"
else
    print_error "BentoML (3000) is not reachable"
fi

echo ""
print_status "Debug script completed!"
echo ""
echo "Next steps:"
echo "1. Check the application logs for detailed error messages"
echo "2. Verify the BentoML service is generating valid .vital files"
echo "3. Test with a known working audio file"
echo "4. Check if Vital can load the generated preset file" 