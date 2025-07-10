package com.neuralsynthmodeler.backend.model;

/**
 * Enum representing different synthesizer types supported by the system
 */
public enum SynthType {
    VITAL("vital"),
    DEXED("dexed"),
    SERUM("serum"),
    PHASE_PLANT("phase_plant"),
    PIGMENTS("pigments");
    
    private final String value;
    
    SynthType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static SynthType fromString(String text) {
        for (SynthType synth : SynthType.values()) {
            if (synth.value.equalsIgnoreCase(text)) {
                return synth;
            }
        }
        throw new IllegalArgumentException("Unknown synth type: " + text);
    }
    
    @Override
    public String toString() {
        return value;
    }
} 