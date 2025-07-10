# Debugging Guide for Preset Download Issues

This guide helps you troubleshoot issues with preset generation and download in the Neural Synth Modeler system.

## Quick Start

1. **Run the debug script**:
   ```bash
   ./debug-preset.sh
   ```

2. **Start with debug logging**:
   ```bash
   # Set debug profile
   export SPRING_PROFILES_ACTIVE=debug
   
   # Or run with debug profile
   java -jar target/neural-synthmodeler-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=debug
   ```

## Common Issues and Solutions

### 1. Preset File is Empty or Invalid

**Symptoms**:
- Preset downloads but Vital crashes when loading
- Preset file size is very small (< 100 bytes)
- Preset file is not valid JSON

**Debugging Steps**:

1. **Check BentoML service logs**:
   ```bash
   # If running in Docker
   docker logs neural-synth-modeler
   
   # If running locally
   tail -f /path/to/bentoml/logs
   ```

2. **Verify BentoML is generating valid files**:
   ```bash
   # Test BentoML directly
   curl -X POST http://localhost:3000/predict \
     -H "Content-Type: application/json" \
     -d '{"audio":"base64_encoded_audio_here"}'
   ```

3. **Check the preset validation in logs**:
   Look for these log messages:
   ```
   [INFO] Validating preset data for request ID: xxx
   [INFO] Preset is valid JSON for request ID: xxx
   [INFO] Preset contains 'settings' field for request ID: xxx
   ```

### 2. BentoML Service Not Responding

**Symptoms**:
- Inference requests timeout
- Status stays "PROCESSING" indefinitely
- Connection refused errors

**Debugging Steps**:

1. **Check if BentoML is running**:
   ```bash
   curl http://localhost:3000/healthz
   ```

2. **Check BentoML container status**:
   ```bash
   docker ps | grep neural-synth
   ```

3. **Check BentoML logs for errors**:
   ```bash
   docker logs neural-synth-modeler --tail 50
   ```

4. **Verify model files exist**:
   ```bash
   # Check if model checkpoint exists
   docker exec neural-synth-modeler ls -la /app/neural_synth_modeler/inferencer/vital/checkpoints/
   ```

### 3. Audio Processing Issues

**Symptoms**:
- Audio upload fails
- GZIP decompression errors
- Audio format not supported

**Debugging Steps**:

1. **Check audio format**:
   ```bash
   file your_audio.wav
   ```

2. **Verify audio requirements**:
   - Sample rate: 16kHz
   - Format: WAV
   - Duration: 2-4 seconds recommended

3. **Test with known good audio**:
   ```bash
   # Use the test audio from the debug script
   ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -ar 16000 test_audio.wav
   ```

### 4. Database Issues

**Symptoms**:
- Request status not persisting
- Audio storage failures
- Connection timeouts

**Debugging Steps**:

1. **Check database connectivity**:
   ```bash
   # MySQL
   mysql -h localhost -u readwrite -preadwrite -e "SELECT 1;"
   
   # MongoDB
   mongo --eval "db.runCommand('ping')"
   ```

2. **Check database logs**:
   ```bash
   # MySQL logs
   docker logs mysql
   
   # MongoDB logs
   docker logs mongodb
   ```

## Enhanced Logging

### Enable Debug Logging

1. **Set debug profile**:
   ```bash
   export SPRING_PROFILES_ACTIVE=debug
   ```

2. **Or modify application.properties**:
   ```properties
   logging.level.com.neuralsynthmodeler=DEBUG
   logging.level.org.springframework.web.reactive.function.client=DEBUG
   ```

### Key Log Messages to Watch

**Successful Flow**:
```
[INFO] Starting inference for request ID: xxx
[INFO] Detected GZIP compressed data, decompressing...
[INFO] Audio stored in MongoDB with reference: xxx
[INFO] Sending audio to BentoML for request ID: xxx
[INFO] Received response from BentoML for request ID: xxx, size: xxx bytes
[INFO] Validating preset data for request ID: xxx
[INFO] Preset is valid JSON for request ID: xxx
[INFO] Preset contains 'settings' field for request ID: xxx
[INFO] Inference completed successfully for request ID: xxx
```

**Error Indicators**:
```
[ERROR] BentoML request failed for request ID: xxx
[ERROR] Preset data is null or empty for request ID: xxx
[ERROR] Preset is not valid JSON for request ID: xxx
[WARN] Preset missing 'settings' field for request ID: xxx
```

## Testing the Pipeline

### 1. Test Each Component Separately

**Test Java Backend**:
```bash
curl http://localhost:8080/v1/health/live
```

**Test BentoML Service**:
```bash
curl http://localhost:3000/healthz
```

**Test Audio Upload**:
```bash
# Create test audio
ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -ar 16000 test.wav
gzip test.wav

# Upload
curl -X POST http://localhost:8080/v1/models/vital/infer \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@test.wav.gz"
```

### 2. Test Preset Download

```bash
# After getting request ID from upload
curl -o preset.vital http://localhost:8080/v1/preset/{REQUEST_ID}

# Validate preset
file preset.vital
head -20 preset.vital
```

### 3. Test with Vital

1. **Load the preset in Vital**:
   - Open Vital
   - Go to File → Load Preset
   - Select the downloaded .vital file

2. **Check for errors**:
   - Look for error messages in Vital
   - Check if the preset loads without crashing

## Common Fixes

### 1. BentoML Model Issues

**Problem**: Model checkpoint missing or corrupted
**Solution**:
```bash
# Rebuild BentoML container
docker build -t neural-synth-modeler:latest /path/to/neural-synth-modeler
docker-compose up -d neural-synth-modeler
```

### 2. Memory Issues

**Problem**: Out of memory errors
**Solution**:
```bash
# Increase memory limits in docker-compose.yml
services:
  neural-synth-modeler:
    deploy:
      resources:
        limits:
          memory: 8G
```

### 3. Network Issues

**Problem**: Services can't communicate
**Solution**:
```bash
# Check network connectivity
docker network ls
docker network inspect neural-synth-network

# Restart services
docker-compose restart
```

### 4. File Permission Issues

**Problem**: Can't write preset files
**Solution**:
```bash
# Fix permissions
sudo chown -R $USER:$USER /path/to/output/directory
chmod 755 /path/to/output/directory
```

## Advanced Debugging

### 1. Enable HTTP Request Logging

Add to `application-debug.properties`:
```properties
logging.level.org.springframework.web.reactive.function.client.WebClient=TRACE
logging.level.reactor.netty=DEBUG
```

### 2. Monitor System Resources

```bash
# Monitor CPU and memory
htop

# Monitor disk usage
df -h

# Monitor network
netstat -tulpn | grep :3000
netstat -tulpn | grep :8080
```

### 3. Check Docker Resource Usage

```bash
# Check container resource usage
docker stats

# Check container logs
docker logs -f neural-synth-backend
docker logs -f neural-synth-modeler
```

## Getting Help

If you're still having issues:

1. **Collect logs**:
   ```bash
   # Backend logs
   docker logs neural-synth-backend > backend.log
   
   # BentoML logs
   docker logs neural-synth-modeler > bentoml.log
   
   # System logs
   dmesg | tail -50 > system.log
   ```

2. **Collect debug information**:
   ```bash
   ./debug-preset.sh > debug_output.log 2>&1
   ```

3. **Include in your report**:
   - Debug script output
   - Application logs
   - Error messages
   - Steps to reproduce
   - System information

## Prevention

To avoid future issues:

1. **Regular testing**: Run the debug script regularly
2. **Monitor logs**: Set up log monitoring
3. **Backup models**: Keep backups of trained models
4. **Version control**: Track changes to configuration
5. **Documentation**: Keep notes of working configurations 