package com.neuralsynthmodeler.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility class for validating and processing Vital preset files
 */
public class VitalPresetUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(VitalPresetUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Validates if the given data represents a valid Vital preset file
     * 
     * @param presetData The preset data as bytes
     * @return true if valid Vital preset, false otherwise
     */
    public static boolean isValidVitalPreset(byte[] presetData) {
        if (presetData == null || presetData.length == 0) {
            logger.debug("Preset data is null or empty");
            return false;
        }
        
        try {
            // Convert bytes to string
            String jsonString = new String(presetData, StandardCharsets.UTF_8);
            
            // Try to parse as JSON
            JsonNode rootNode = objectMapper.readTree(jsonString);
            
            // Check for required Vital preset fields
            return hasRequiredVitalFields(rootNode);
            
        } catch (IOException e) {
            logger.debug("Failed to parse preset data as JSON: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.debug("Unexpected error validating Vital preset: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts metadata from a valid Vital preset file
     * 
     * @param presetData The preset data as bytes
     * @return Optional containing VitalPresetMetadata if valid, empty otherwise
     */
    public static Optional<VitalPresetMetadata> extractMetadata(byte[] presetData) {
        if (!isValidVitalPreset(presetData)) {
            return Optional.empty();
        }
        
        try {
            String jsonString = new String(presetData, StandardCharsets.UTF_8);
            JsonNode rootNode = objectMapper.readTree(jsonString);
            
            VitalPresetMetadata metadata = new VitalPresetMetadata();
            
            // Extract basic metadata
            metadata.setAuthor(getStringValue(rootNode, "author"));
            metadata.setComments(getStringValue(rootNode, "comments"));
            metadata.setPresetStyle(getStringValue(rootNode, "preset_style"));
            metadata.setPresetStyles(getStringValue(rootNode, "preset_styles"));
            metadata.setSynthVersion(getStringValue(rootNode, "synth_version"));
            
            // Extract macro controls
            metadata.setMacro1(getStringValue(rootNode, "macro1"));
            metadata.setMacro2(getStringValue(rootNode, "macro2"));
            metadata.setMacro3(getStringValue(rootNode, "macro3"));
            metadata.setMacro4(getStringValue(rootNode, "macro4"));
            
            // Extract oscillator information
            metadata.setOsc1On(getBooleanValue(rootNode, "osc_1_on"));
            metadata.setOsc2On(getBooleanValue(rootNode, "osc_2_on"));
            metadata.setOsc3On(getBooleanValue(rootNode, "osc_3_on"));
            
            // Extract effect information
            metadata.setChorusOn(getBooleanValue(rootNode, "chorus_on"));
            metadata.setDelayOn(getBooleanValue(rootNode, "delay_on"));
            metadata.setDistortionOn(getBooleanValue(rootNode, "distortion_on"));
            metadata.setFlangerOn(getBooleanValue(rootNode, "flanger_on"));
            metadata.setPhaserOn(getBooleanValue(rootNode, "phaser_on"));
            metadata.setReverbOn(getBooleanValue(rootNode, "reverb_on"));
            
            // Extract filter information
            metadata.setFilter1On(getBooleanValue(rootNode, "filter_1_on"));
            metadata.setFilter2On(getBooleanValue(rootNode, "filter_2_on"));
            metadata.setFilterFxOn(getBooleanValue(rootNode, "filter_fx_on"));
            
            // Extract LFO information
            metadata.setLfo1Sync(getBooleanValue(rootNode, "lfo_1_sync"));
            metadata.setLfo2Sync(getBooleanValue(rootNode, "lfo_2_sync"));
            metadata.setLfo3Sync(getBooleanValue(rootNode, "lfo_3_sync"));
            metadata.setLfo4Sync(getBooleanValue(rootNode, "lfo_4_sync"));
            
            // Extract modulation count
            JsonNode modulationsNode = rootNode.get("modulations");
            if (modulationsNode != null && modulationsNode.isArray()) {
                metadata.setModulationCount(modulationsNode.size());
            }
            
            // Extract LFO count
            JsonNode lfosNode = rootNode.get("lfos");
            if (lfosNode != null && lfosNode.isArray()) {
                metadata.setLfoCount(lfosNode.size());
            }
            
            return Optional.of(metadata);
            
        } catch (Exception e) {
            logger.warn("Failed to extract metadata from Vital preset: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Checks if the JSON contains required Vital preset fields
     */
    private static boolean hasRequiredVitalFields(JsonNode rootNode) {
        // Check for essential Vital fields: preset_styles, settings, and synth_version
        String[] requiredFields = {
            "preset_styles",      // Preset styles/categories
            "settings",           // Main settings object
            "synth_version"       // Synth version information
        };
        
        for (String field : requiredFields) {
            if (!rootNode.has(field)) {
                logger.debug("Missing required field: {}", field);
                return false;
            }
        }
        
        // Check if settings is an object
        JsonNode settings = rootNode.get("settings");
        if (settings == null || !settings.isObject()) {
            logger.debug("Settings field is not a valid object");
            return false;
        }
        
        return true;
    }
    
    /**
     * Safely extracts a string value from JSON node
     */
    private static String getStringValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode != null && fieldNode.isTextual()) ? fieldNode.asText() : null;
    }
    
    /**
     * Safely extracts a boolean value from JSON node
     */
    private static Boolean getBooleanValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode != null && fieldNode.isBoolean()) ? fieldNode.asBoolean() : null;
    }
    
    /**
     * Metadata class for Vital preset information
     */
    public static class VitalPresetMetadata {
        private String author;
        private String comments;
        private String presetStyle;
        private String presetStyles;
        private String synthVersion;
        private String macro1;
        private String macro2;
        private String macro3;
        private String macro4;
        private Boolean osc1On;
        private Boolean osc2On;
        private Boolean osc3On;
        private Boolean chorusOn;
        private Boolean delayOn;
        private Boolean distortionOn;
        private Boolean flangerOn;
        private Boolean phaserOn;
        private Boolean reverbOn;
        private Boolean filter1On;
        private Boolean filter2On;
        private Boolean filterFxOn;
        private Boolean lfo1Sync;
        private Boolean lfo2Sync;
        private Boolean lfo3Sync;
        private Boolean lfo4Sync;
        private Integer modulationCount;
        private Integer lfoCount;
        
        // Getters and setters
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        
        public String getPresetStyle() { return presetStyle; }
        public void setPresetStyle(String presetStyle) { this.presetStyle = presetStyle; }
        
        public String getPresetStyles() { return presetStyles; }
        public void setPresetStyles(String presetStyles) { this.presetStyles = presetStyles; }
        
        public String getSynthVersion() { return synthVersion; }
        public void setSynthVersion(String synthVersion) { this.synthVersion = synthVersion; }
        
        public String getMacro1() { return macro1; }
        public void setMacro1(String macro1) { this.macro1 = macro1; }
        
        public String getMacro2() { return macro2; }
        public void setMacro2(String macro2) { this.macro2 = macro2; }
        
        public String getMacro3() { return macro3; }
        public void setMacro3(String macro3) { this.macro3 = macro3; }
        
        public String getMacro4() { return macro4; }
        public void setMacro4(String macro4) { this.macro4 = macro4; }
        
        public Boolean getOsc1On() { return osc1On; }
        public void setOsc1On(Boolean osc1On) { this.osc1On = osc1On; }
        
        public Boolean getOsc2On() { return osc2On; }
        public void setOsc2On(Boolean osc2On) { this.osc2On = osc2On; }
        
        public Boolean getOsc3On() { return osc3On; }
        public void setOsc3On(Boolean osc3On) { this.osc3On = osc3On; }
        
        public Boolean getChorusOn() { return chorusOn; }
        public void setChorusOn(Boolean chorusOn) { this.chorusOn = chorusOn; }
        
        public Boolean getDelayOn() { return delayOn; }
        public void setDelayOn(Boolean delayOn) { this.delayOn = delayOn; }
        
        public Boolean getDistortionOn() { return distortionOn; }
        public void setDistortionOn(Boolean distortionOn) { this.distortionOn = distortionOn; }
        
        public Boolean getFlangerOn() { return flangerOn; }
        public void setFlangerOn(Boolean flangerOn) { this.flangerOn = flangerOn; }
        
        public Boolean getPhaserOn() { return phaserOn; }
        public void setPhaserOn(Boolean phaserOn) { this.phaserOn = phaserOn; }
        
        public Boolean getReverbOn() { return reverbOn; }
        public void setReverbOn(Boolean reverbOn) { this.reverbOn = reverbOn; }
        
        public Boolean getFilter1On() { return filter1On; }
        public void setFilter1On(Boolean filter1On) { this.filter1On = filter1On; }
        
        public Boolean getFilter2On() { return filter2On; }
        public void setFilter2On(Boolean filter2On) { this.filter2On = filter2On; }
        
        public Boolean getFilterFxOn() { return filterFxOn; }
        public void setFilterFxOn(Boolean filterFxOn) { this.filterFxOn = filterFxOn; }
        
        public Boolean getLfo1Sync() { return lfo1Sync; }
        public void setLfo1Sync(Boolean lfo1Sync) { this.lfo1Sync = lfo1Sync; }
        
        public Boolean getLfo2Sync() { return lfo2Sync; }
        public void setLfo2Sync(Boolean lfo2Sync) { this.lfo2Sync = lfo2Sync; }
        
        public Boolean getLfo3Sync() { return lfo3Sync; }
        public void setLfo3Sync(Boolean lfo3Sync) { this.lfo3Sync = lfo3Sync; }
        
        public Boolean getLfo4Sync() { return lfo4Sync; }
        public void setLfo4Sync(Boolean lfo4Sync) { this.lfo4Sync = lfo4Sync; }
        
        public Integer getModulationCount() { return modulationCount; }
        public void setModulationCount(Integer modulationCount) { this.modulationCount = modulationCount; }
        
        public Integer getLfoCount() { return lfoCount; }
        public void setLfoCount(Integer lfoCount) { this.lfoCount = lfoCount; }
        
        @Override
        public String toString() {
            return String.format("VitalPresetMetadata{author='%s', presetStyle='%s', osc1On=%s, osc2On=%s, osc3On=%s, " +
                               "modulationCount=%d, lfoCount=%d}", 
                               author, presetStyle, osc1On, osc2On, osc3On, modulationCount, lfoCount);
        }
    }
} 