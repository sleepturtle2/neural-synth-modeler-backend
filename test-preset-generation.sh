#!/bin/bash

# Simple test script for preset generation
# This script tests the complete pipeline from audio upload to preset download

set -e

echo "=== Testing Preset Generation Pipeline ==="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }

# Check if services are running
echo "1. Checking services..."

if curl -s http://localhost:8080/v1/health/live > /dev/null; then
    print_success "Java backend is running"
else
    print_error "Java backend is not running"
    exit 1
fi

if curl -s http://localhost:3000/healthz > /dev/null; then
    print_success "BentoML service is running"
else
    print_error "BentoML service is not running"
    exit 1
fi

echo ""
echo "2. Creating test audio..."

# Create test audio file
TEST_AUDIO="test_sine_440.wav"
if command -v ffmpeg >/dev/null 2>&1; then
    ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -ar 16000 "$TEST_AUDIO" -y > /dev/null 2>&1
    print_success "Created test audio: $TEST_AUDIO"
else
    print_warning "ffmpeg not found, using existing test file if available"
    if [ ! -f "$TEST_AUDIO" ]; then
        print_error "No test audio file available"
        exit 1
    fi
fi

# Compress audio
gzip -c "$TEST_AUDIO" > "${TEST_AUDIO}.gz"
print_success "Compressed audio file"

echo ""
echo "3. Uploading audio for inference..."

# Upload audio
RESPONSE=$(curl -s -X POST \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@${TEST_AUDIO}.gz" \
    http://localhost:8080/v1/models/vital/infer)

if [ $? -eq 0 ]; then
    print_success "Audio uploaded successfully"
    echo "Response: $RESPONSE"
    
    # Extract request ID
    REQUEST_ID=$(echo "$RESPONSE" | grep -o '"request_id":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$REQUEST_ID" ]; then
        print_success "Request ID: $REQUEST_ID"
    else
        print_error "Could not extract request ID"
        exit 1
    fi
else
    print_error "Failed to upload audio"
    exit 1
fi

echo ""
echo "4. Monitoring inference progress..."

# Poll for status
for i in {1..60}; do
    STATUS_RESPONSE=$(curl -s "http://localhost:8080/v1/infer-audio/status/$REQUEST_ID")
    STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    
    echo "Status: $STATUS (attempt $i/60)"
    
    if [ "$STATUS" = "DONE" ]; then
        print_success "Inference completed!"
        break
    elif [ "$STATUS" = "ERROR" ]; then
        print_error "Inference failed"
        exit 1
    fi
    
    if [ $i -eq 60 ]; then
        print_error "Inference timed out after 2 minutes"
        exit 1
    fi
    
    sleep 2
done

echo ""
echo "5. Downloading preset..."

# Download preset
curl -s -o "preset_${REQUEST_ID}.vital" \
    "http://localhost:8080/v1/preset/$REQUEST_ID"

if [ -f "preset_${REQUEST_ID}.vital" ]; then
    PRESET_SIZE=$(stat -c%s "preset_${REQUEST_ID}.vital" 2>/dev/null || stat -f%z "preset_${REQUEST_ID}.vital")
    print_success "Preset downloaded: preset_${REQUEST_ID}.vital ($PRESET_SIZE bytes)"
    
    if [ "$PRESET_SIZE" -lt 100 ]; then
        print_warning "Preset file is very small, may be invalid"
    fi
else
    print_error "Failed to download preset"
    exit 1
fi

echo ""
echo "6. Validating preset file..."

# Check if it's valid JSON
if command -v jq >/dev/null 2>&1; then
    if jq empty "preset_${REQUEST_ID}.vital" 2>/dev/null; then
        print_success "Preset is valid JSON"
        
        # Check for required fields
        if jq -e '.settings' "preset_${REQUEST_ID}.vital" >/dev/null 2>&1; then
            print_success "Preset contains 'settings' field"
        else
            print_warning "Preset missing 'settings' field"
        fi
        
        if jq -e '.settings.wavetables' "preset_${REQUEST_ID}.vital" >/dev/null 2>&1; then
            print_success "Preset contains 'wavetables' field"
        else
            print_warning "Preset missing 'wavetables' field"
        fi
        
    else
        print_error "Preset is not valid JSON"
    fi
else
    # Simple check without jq
    if grep -q '"settings"' "preset_${REQUEST_ID}.vital"; then
        print_success "Preset appears to contain 'settings' field"
    else
        print_warning "Preset may be missing 'settings' field"
    fi
fi

echo ""
echo "7. Preset file preview:"
echo "First 10 lines of preset file:"
head -10 "preset_${REQUEST_ID}.vital"

echo ""
echo "=== Test Summary ==="
print_success "Pipeline test completed successfully!"
echo "Preset file: preset_${REQUEST_ID}.vital"
echo "File size: $PRESET_SIZE bytes"
echo ""
echo "Next steps:"
echo "1. Try loading the preset in Vital"
echo "2. Check if Vital crashes or shows errors"
echo "3. If issues persist, run the full debug script: ./debug-preset.sh"
echo "4. Check application logs for detailed information"

# Cleanup
rm -f "${TEST_AUDIO}.gz" 