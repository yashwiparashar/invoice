private void SendMailForCountries(
    JatoExecutor.Configuration config)
{
    try
    {
        string[] countries = { "HR", "SI" };

        foreach (string countryCode in countries)
        {
            string emailList;

            if (countryCode == "HR")
            {
                emailList = "HR_EMAIL_1;HR_EMAIL_2;HR_EMAIL_3";
            }
            else
            {
                emailList = "SI_EMAIL_1;SI_EMAIL_2;SI_EMAIL_3";
            }

            string folderPath = $@"{AppCodeConstants.TempPathAbsolute}\";

            ExportArgs exportArgs = new ExportArgs
            {
                ExcelFileName =
                    Utilities.GetUniqueFileName("VehicleExport") + ".xlsx",

                ExportType = "ALL",

                ModelYear = string.Empty,

                VehStatus = "A",

                WorkPath = folderPath,

                TemplatePath = GetTemplatePath(countryCode)
            };

            int exportCount =
                _importExportController.OnVehicleExportFile(exportArgs);

            if (exportCount > 0)
            {
                string[] files = Directory.GetFiles(
                    exportArgs.WorkPath,
                    "*" + exportArgs.ExcelFileName);

                if (files != null && files.Length > 0)
                {
                    foreach (string filePath in files)
                    {
                        string subject =
                            countryCode + " - " +
                            (countryCode == "HR" ? "Croatia" : "Slovenia");

                        EmailEntities entities = new EmailEntities
                        {
                            ToAddress = emailList,
                            FromAddress =
                                config.GetParamValue(BATCH_FROMADDR),
                            SmtpHost =
                                config.GetParamValue(BATCH_SMTPSERVER),
                            Subject =
                                subject + " - " +
                                GetAppSettingsValue(
                                    RuntimeConstants.EMAILSUBJECT),
                            Body =
                                GetAppSettingsValue(
                                    RuntimeConstants.EMAILCONTENT),
                            Disclaimer =
                                GetAppSettingsValue(
                                    RuntimeConstants.EMAILDISCLAIMER),
                            DocumentPath = filePath
                        };

                        Emailing.SendMail(entities);
                    }
                }
            }
        }
    }
    catch (Exception ex)
    {
        UpdateLog(
            ProcessStatus.Processing,
            string.Empty,
            "Failed while sending vehicle export mail");
    }
}
