package com.neuralsynthmodeler.backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class VitalPresetUtilsTest {

    @Test
    public void testValidVitalPreset() {
        // Create a minimal valid Vital preset JSON with required fields: preset_styles, settings, synth_version
        String validPreset = "{\"preset_styles\":\"Bass\",\"synth_version\":\"1.0\",\"author\":\"Test Author\",\"comments\":\"Test preset\",\"preset_style\":\"Bass\",\"macro1\":\"MACRO1\",\"macro2\":\"MACRO2\",\"macro3\":\"MACRO3\",\"macro4\":\"MACRO4\",\"osc_1_on\":true,\"osc_2_on\":false,\"osc_3_on\":false,\"filter_1_on\":true,\"filter_2_on\":false,\"filter_fx_on\":false,\"env_1_attack\":0.1,\"settings\":{\"beats_per_minute\":120.0,\"bypass\":0.0},\"modulations\":[{\"destination\":\"osc_1_level\",\"source\":\"lfo_1\"}],\"lfos\":[{\"name\":\"Triangle\",\"num_points\":3,\"points\":[0.0,1.0,1.0,0.0]}]}";
        
        byte[] presetData = validPreset.getBytes(StandardCharsets.UTF_8);
        
        assertTrue(VitalPresetUtils.isValidVitalPreset(presetData), "Valid Vital preset should be recognized");
        
        Optional<VitalPresetUtils.VitalPresetMetadata> metadata = VitalPresetUtils.extractMetadata(presetData);
        assertTrue(metadata.isPresent(), "Metadata should be extracted from valid preset");
        
        VitalPresetUtils.VitalPresetMetadata meta = metadata.get();
        assertEquals("Test Author", meta.getAuthor());
        assertEquals("Test preset", meta.getComments());
        assertEquals("Bass", meta.getPresetStyle());
        assertEquals("Bass", meta.getPresetStyles());
        assertEquals("1.0", meta.getSynthVersion());
        assertEquals("MACRO1", meta.getMacro1());
        assertTrue(meta.getOsc1On());
        assertFalse(meta.getOsc2On());
        assertTrue(meta.getFilter1On());
        assertEquals(1, meta.getModulationCount());
        assertEquals(1, meta.getLfoCount());
    }
    
    @Test
    public void testInvalidVitalPreset() {
        // Test with invalid JSON
        String invalidJson = "{ invalid json }";
        byte[] invalidData = invalidJson.getBytes(StandardCharsets.UTF_8);
        
        assertFalse(VitalPresetUtils.isValidVitalPreset(invalidData), "Invalid JSON should not be recognized as Vital preset");
        assertFalse(VitalPresetUtils.extractMetadata(invalidData).isPresent(), "No metadata should be extracted from invalid preset");
    }
    
    @Test
    public void testMissingRequiredFields() {
        // Test with JSON missing required Vital fields
        String incompletePreset = "{\"author\":\"Test Author\",\"comments\":\"Test preset\"}";
        
        byte[] presetData = incompletePreset.getBytes(StandardCharsets.UTF_8);
        
        assertFalse(VitalPresetUtils.isValidVitalPreset(presetData), "Preset missing required fields should not be valid");
        assertFalse(VitalPresetUtils.extractMetadata(presetData).isPresent(), "No metadata should be extracted from invalid preset");
    }
    
    @Test
    public void testNullAndEmptyData() {
        // Test with null data
        assertFalse(VitalPresetUtils.isValidVitalPreset(null), "Null data should not be valid");
        assertFalse(VitalPresetUtils.extractMetadata(null).isPresent(), "No metadata should be extracted from null data");
        
        // Test with empty data
        byte[] emptyData = new byte[0];
        assertFalse(VitalPresetUtils.isValidVitalPreset(emptyData), "Empty data should not be valid");
        assertFalse(VitalPresetUtils.extractMetadata(emptyData).isPresent(), "No metadata should be extracted from empty data");
    }
} 