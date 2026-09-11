



private void SendMailForCroatia(string filePath, JatoExecutor.Configuration config, string emailList)
{
    try
    {
        string subject = CountryCode + " - " + CountryName;

        EmailEntities entities = new EmailEntities
        {
            ToAddress = emailList,
            FromAddress = config.GetParamValue(BATCH_FROMADDRESS),
            SmtpHost = config.GetParamValue(BATCH_SMTPSEVER),
            Subject = subject + " - " + GetAppSettingsValue(CurrentConstants.EMAILSUBJECT),
            Body = GetAppSettingsValue(CurrentConstants.EMAILCONTENT),
            Disclaimer = GetAppSettingsValue(CurrentConstants.EMAILDISCLAIMER),
            DocumentPath = filePath
        };

        if (!string.IsNullOrEmpty(filePath) && File.Exists(filePath))
        {
            Emailing.SendMail(entities);

            UpdateLog(
                ProcessStatus.Processing,
                string.Empty,
                "Vehicle export mail sent to Croatia recipients"
            );
        }
    }
    catch (Exception ex)
    {
        UpdateLog(
            ProcessStatus.Processing,
            string.Empty,
            "Failed while sending vehicle export mail to Croatia"
        );
    }
}
