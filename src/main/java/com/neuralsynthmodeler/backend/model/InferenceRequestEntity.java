package com.neuralsynthmodeler.backend.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "INFERENCE_REQUEST")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceRequestEntity {
    @Id
    private String id;

    private String model;
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "audio_ref")
    private String audioRef;

    @Column(name = "audio_size_gzipped")
    private int audioSizeGzipped;

    @Column(name = "audio_size_uncompressed")
    private int audioSizeUncompressed;

    @Column(name = "result_ref")
    private String resultRef;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "meta", columnDefinition = "JSON")
    private String meta;
} 