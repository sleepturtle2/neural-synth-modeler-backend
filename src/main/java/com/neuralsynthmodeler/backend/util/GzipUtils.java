package com.neuralsynthmodeler.backend.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for GZIP compression and decompression operations using Apache Commons Compress
 * This wrapper provides a clean interface to Apache Commons Compress GZIP functionality
 */
public class GzipUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(GzipUtils.class);
    
    /**
     * Decompress GZIP compressed byte array using Apache Commons Compress
     * 
     * @param compressed The compressed byte array
     * @return The decompressed byte array
     * @throws IOException If decompression fails or data is not valid GZIP
     */
    public static byte[] decompress(byte[] compressed) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            gzipIn.transferTo(baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * Compress byte array using GZIP compression
     * 
     * @param data The data to compress
     * @return The compressed byte array
     * @throws IOException If compression fails
     */
    public static byte[] compress(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos)) {
            
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        }
    }
    
    /**
     * Check if the byte array is GZIP compressed using Apache Commons Compress
     * 
     * @param data The byte array to check
     * @return true if the data is valid GZIP compressed, false otherwise
     */
    public static boolean isGzipCompressed(byte[] data) {
        
        // Quick magic byte check first (performance optimization)
        if (data != null && data.length >= 2 && (data[0] & 0xFF) == 0x1f && (data[1] & 0xFF) == 0x8b) {
            // Only check the magic bytes, skip further validation
            // TODO: Also check for the third magic byte 0x08 (compression method) for stricter GZIP validation
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Attempt to decompress data, returning null if it's not GZIP compressed
     * This is a safer alternative that doesn't throw exceptions for non-GZIP data
     * 
     * @param data The byte array to attempt decompression on
     * @return The decompressed byte array, or null if not GZIP compressed
     */
    public static byte[] tryDecompress(byte[] data) {
        if (!isGzipCompressed(data)) {
            return null;
        }
        
        try {
            return decompress(data);
        } catch (IOException e) {
            // Log the error for debugging purposes
            logger.error("GZIP decompression failed: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if a filename has a GZIP extension using Apache Commons GzipUtils
     * 
     * @param fileName The filename to check
     * @return true if the filename has a GZIP extension, false otherwise
     */
    public static boolean isCompressedFileName(String fileName) {
        return org.apache.commons.compress.compressors.gzip.GzipUtils.isCompressedFileName(fileName);
    }
    
    /**
     * Get the compressed filename for a given file using Apache Commons GzipUtils
     * 
     * @param fileName The original filename
     * @return The compressed filename
     */
    public static String getCompressedFileName(String fileName) {
        return org.apache.commons.compress.compressors.gzip.GzipUtils.getCompressedFileName(fileName);
    }
    
    /**
     * Get the uncompressed filename for a given compressed file using Apache Commons GzipUtils
     * 
     * @param fileName The compressed filename
     * @return The uncompressed filename
     */
    public static String getUncompressedFileName(String fileName) {
        return org.apache.commons.compress.compressors.gzip.GzipUtils.getUncompressedFileName(fileName);
    }
} 