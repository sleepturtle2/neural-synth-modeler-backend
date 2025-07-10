package com.neuralsynthmodeler.backend.repository;

import com.neuralsynthmodeler.backend.model.InferenceRequestEntity;
import com.neuralsynthmodeler.backend.model.SynthType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class InferenceRequestRepository {

    private final DataSource dataSource;

    @Autowired
    public InferenceRequestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public InferenceRequestEntity save(InferenceRequestEntity entity) {
        String sql = """
            INSERT INTO INFERENCE_REQUEST 
            (id, model, synth, status, created_at, updated_at, audio_ref, audio_size_gzipped, audio_size_uncompressed, result_ref, error, meta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            status = VALUES(status),
            updated_at = VALUES(updated_at),
            result_ref = VALUES(result_ref),
            error = VALUES(error),
            meta = VALUES(meta)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set all parameters using prepared statement
            stmt.setString(1, entity.getId());
            stmt.setString(2, entity.getModel());
            stmt.setString(3, entity.getSynth() != null ? entity.getSynth() : SynthType.VITAL.getValue());
            stmt.setString(4, entity.getStatus());
            stmt.setTimestamp(5, Timestamp.from(entity.getCreatedAt()));
            stmt.setTimestamp(6, Timestamp.from(entity.getUpdatedAt()));
            stmt.setString(7, entity.getAudioRef());
            stmt.setInt(8, entity.getAudioSizeGzipped());
            stmt.setInt(9, entity.getAudioSizeUncompressed());
            stmt.setString(10, entity.getResultRef());
            stmt.setString(11, entity.getError());
            stmt.setString(12, entity.getMeta());
            
            stmt.executeUpdate();
            return entity;
        } catch (SQLException e) {
            throw new RuntimeException("Error saving inference request", e);
        }
    }

    public Optional<InferenceRequestEntity> findById(String id) {
        String sql = "SELECT * FROM INFERENCE_REQUEST WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inference request by id", e);
        }
    }

    public List<InferenceRequestEntity> findAll() {
        String sql = "SELECT * FROM INFERENCE_REQUEST ORDER BY created_at DESC";
        List<InferenceRequestEntity> entities = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }
            return entities;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all inference requests", e);
        }
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM INFERENCE_REQUEST WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting inference request", e);
        }
    }
    
    public List<InferenceRequestEntity> findByStatus(String status) {
        String sql = "SELECT * FROM INFERENCE_REQUEST WHERE status = ? ORDER BY created_at DESC";
        List<InferenceRequestEntity> entities = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }
            return entities;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inference requests by status", e);
        }
    }
    
    public List<InferenceRequestEntity> findByModel(String model) {
        String sql = "SELECT * FROM INFERENCE_REQUEST WHERE model = ? ORDER BY created_at DESC";
        List<InferenceRequestEntity> entities = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, model);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }
            return entities;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inference requests by model", e);
        }
    }
    
    public List<InferenceRequestEntity> findBySynth(String synth) {
        String sql = "SELECT * FROM INFERENCE_REQUEST WHERE synth = ? ORDER BY created_at DESC";
        List<InferenceRequestEntity> entities = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, synth);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }
            return entities;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inference requests by synth", e);
        }
    }
    
    public int updateStatus(String id, String status) {
        String sql = "UPDATE INFERENCE_REQUEST SET status = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setString(3, id);
            
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating inference request status", e);
        }
    }
    
    public int updateResultRef(String id, String resultRef) {
        String sql = "UPDATE INFERENCE_REQUEST SET result_ref = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, resultRef);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setString(3, id);
            
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating inference request result_ref", e);
        }
    }

    private InferenceRequestEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return InferenceRequestEntity.builder()
                .id(rs.getString("id"))
                .model(rs.getString("model"))
                .synth(rs.getString("synth"))
                .status(rs.getString("status"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .audioRef(rs.getString("audio_ref"))
                .audioSizeGzipped(rs.getInt("audio_size_gzipped"))
                .audioSizeUncompressed(rs.getInt("audio_size_uncompressed"))
                .resultRef(rs.getString("result_ref"))
                .error(rs.getString("error"))
                .meta(rs.getString("meta"))
                .build();
    }
} 