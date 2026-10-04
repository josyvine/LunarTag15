package com.lunartag.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.lunartag.app.model.AuditLog;

import java.util.List;

/**
 * Data Access Object (DAO) for the AuditLog entity.
 * This interface defines the database interactions for the 'audit_logs' table.
 */
@Dao
public interface AuditLogDao {

    /**
     * Inserts a new audit log record into the database.
     * @param auditLog The audit log object to insert.
     */
    @Insert
    void insertLog(AuditLog auditLog);

    /**
     * Retrieves all audit logs for a specific photo ID, ordered by the most recent first.
     * @param photoId The ID of the photo to get logs for.
     * @return A list of all AuditLog objects for the given photo.
     */
    @Query("SELECT * FROM audit_logs WHERE photoId = :photoId ORDER BY timestamp DESC")
    List<AuditLog> getLogsForPhoto(long photoId);

    /**
     * Retrieves all audit logs from the database, ordered by the most recent first.
     * @return A list of all AuditLog objects.
     */
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    List<AuditLog> getAllLogs();

}
