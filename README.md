{
    foreach (string country in new[] { "HR", "SI" })
    {
        string filePath = GetCsvFilePath(country);

        if (File.Exists(filePath))
        {
            string emailId = GetEmailId(country);

            SendEmail(emailId, filePath);
        }
    }
}



private void SendEmail(string emailId, string filePath)
{
    // existing project's email logic here
}


private string GetCsvFilePath(string countryCode)
{
    string batchLogPath =
        ConfigurationManager.AppSettings[BATCHLOGPATH].ToString();

    string dateFolder = DateTime.Now.ToString("dd-MMM-yyyy");

    return Path.Combine(
        batchLogPath,
        countryCode,
        dateFolder,
        "VehicleExport.csv");
}



private string GetEmailId(string countryCode)
{
    // Fetch from your config table
}
