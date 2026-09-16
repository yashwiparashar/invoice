public Task Execute(IJobExecutionContext context)
{
    string batchLogPath = ConfigurationManager.AppSettings[BATCHLOGPATH].ToString();

    string dateFolder = DateTime.Now.ToString("dd-MMM-yyyy");

    string folderPath = Path.Combine(
        batchLogPath,
        CountryCode,
        dateFolder);

    string csvFilePath = Path.Combine(folderPath, "VehicleExport.csv");

    // send csvFilePath through email

    return Task.CompletedTask;
}
