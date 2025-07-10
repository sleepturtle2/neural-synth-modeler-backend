package com.neuralsynthmodeler.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * Utility class for preset-related operations
 */
public class PresetUtils {

    private static final Logger logger = LoggerFactory.getLogger(PresetUtils.class);

    /**
     * Validates preset data based on synth type
     */
    public static boolean validatePresetData(byte[] presetData, String synthType) {
        if (presetData == null || presetData.length == 0) {
            logger.info("Preset data is null or empty");
            return false;
        }
        try {
            switch (synthType.toLowerCase()) {
                case "vital":
                    boolean isValidVital = VitalPresetUtils.isValidVitalPreset(presetData);
                    if (isValidVital) {
                        Optional<VitalPresetUtils.VitalPresetMetadata> metadata = VitalPresetUtils.extractMetadata(presetData);
                        if (metadata.isPresent()) {
                            logger.info("Valid Vital preset detected: {}", metadata.get());
                        }
                    } else {
                        logger.warn("Invalid Vital preset data received");
                    }
                    return isValidVital;
                case "dexed":
                    // TODO: Add Dexed preset validation when implemented
                    logger.warn("Dexed preset validation not yet implemented");
                    return true; // For now, accept all Dexed presets
                default:
                    logger.warn("Unknown synth type for preset validation: {}", synthType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("Error validating preset data for synth type {}: {}", synthType, e.getMessage());
            return false;
        }
    }
} 