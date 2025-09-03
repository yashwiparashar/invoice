USE PARTNERS_IKP_REPORTING;
GO

SELECT  
    DB_NAME(st.dbid) AS DatabaseName,
    OBJECT_NAME(st.objectid, st.dbid) AS StoredProcedureName,
    qs.execution_count,
    qs.last_execution_time
FROM sys.dm_exec_query_stats qs
CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
WHERE st.objectid = OBJECT_ID('dbo.DataExistanCyCheck')
ORDER BY qs.last_execution_time DESC;