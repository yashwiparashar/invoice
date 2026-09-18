CREATE PROCEDURE dbo.P_GET_JTT_REPORT_MAILLIST
AS
BEGIN
    DECLARE @mailinglist VARCHAR(500) = '';

    SELECT @mailinglist =
        COALESCE(@mailinglist + ',', '') + jd.ParamValue1
    FROM JTT
    INNER JOIN JTTDETAIL jd
        ON JTT.JttId = jd.JttId
    WHERE JTT.JttCode = 'MAIL_REPORT_LIST'
      AND jd.Active = 'Y';

    SELECT @mailinglist AS EMAILLIST;
END
