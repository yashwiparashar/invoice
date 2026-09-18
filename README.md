using Ald.VehicleUpload.Common.BusinessEntities.JobExecutor;
using Ald.VehicleUpload.DataAccess.DAO.JobExecutorDataService;
using Ald.VehicleUpload.Shared.Components;
using Quartz;
using Serilog;
using System;
using System.Configuration;
using System.IO;
using System.Threading.Tasks;

using JatoExecutor = Ald.VehicleUpload.Common.BusinessEntities.JobExecutor;

namespace Ald.VehicleUpload.Business.BO.JobExecutor
{
    public class EmailSendProcess : IJob
    {
        private readonly static ILogger _logger =
            Log.Logger.ForContext<EmailSendProcess>();

        private const string BATCHLOGPATH = "BATCHLOGPATH";
        private const string BATCH_FROMADDR = "BATCH_FROMADDR";
        private const string BATCH_SMTPSERVER = "BATCH_SMTPSERVER";

        public string CountryCode { get; set; } = string.Empty;

        public string CountryName { get; set; } = string.Empty;


        async Task IJob.Execute(IJobExecutionContext context)
        {
            try
            {
                JobDataMap dataMap = context.JobDetail.JobDataMap;

                CountryCode = dataMap.GetString("CODE");
                CountryName = dataMap.GetString("NAME");

                RunEmailProcess();
            }
            catch (Exception ex)
            {
                _logger.Error(
                    ex,
                    $"An error occurred while executing EmailSendProcess - {CountryCode}");
            }

            await Task.CompletedTask;
        }


        public void RunEmailProcess()
        {
            try
            {
                JobExecutorDAO jobDAO = new JobExecutorDAO(CountryCode);

                // Load configuration values such as
                // BATCH_FROMADDR and BATCH_SMTPSERVER from DB
                JatoExecutor.Configuration config =
                    new JatoExecutor.Configuration
                    {
                        ParameterList = jobDAO.LoadConfigParamFromDB()
                    };

                // Get today's VehicleExport.csv
                string filePath = GetCsvFilePath(CountryCode);

                if (!File.Exists(filePath))
                {
                    _logger.Information(
                        $"VehicleExport.csv not found for {CountryCode}. " +
                        $"Expected path: {filePath}");

                    return;
                }

                // Get recipient email from JTT configuration
                string emailId = GetEmailId(CountryCode);

                if (string.IsNullOrWhiteSpace(emailId))
                {
                    _logger.Information(
                        $"No email ID configured for {CountryCode}");

                    return;
                }

                // Send email
                SendEmail(emailId, filePath, CountryCode, config);
            }
            catch (Exception ex)
            {
                _logger.Error(
                    ex,
                    $"Error occurred in RunEmailProcess for {CountryCode}");
            }
        }


        private void SendEmail(
            string emailId,
            string filePath,
            string countryCode,
            JatoExecutor.Configuration config)
        {
            try
            {
                EmailEntities entities = new EmailEntities
                {
                    ToAddress = emailId,

                    FromAddress =
                        config.GetParamValue(BATCH_FROMADDR),

                    SmtpHost =
                        config.GetParamValue(BATCH_SMTPSERVER),

                    Subject =
                        countryCode + " - Vehicle Export",

                    Body =
                        "Please find attached the Vehicle Export file.",

                    DocumentPath = filePath,

                    IsAttachmentAvailable = true
                };

                if (string.IsNullOrWhiteSpace(entities.ToAddress))
                {
                    _logger.Information(
                        $"To address is empty for {countryCode}");

                    return;
                }

                if (string.IsNullOrWhiteSpace(entities.FromAddress))
                {
                    _logger.Information(
                        $"From address is empty for {countryCode}");

                    return;
                }

                if (string.IsNullOrWhiteSpace(entities.SmtpHost))
                {
                    _logger.Information(
                        $"SMTP server is empty for {countryCode}");

                    return;
                }

                Emailing.SendMail(entities);

                _logger.Information(
                    $"Mail sent successfully to {entities.ToAddress} " +
                    $"for {countryCode}");
            }
            catch (Exception ex)
            {
                _logger.Error(
                    ex,
                    $"Failed while sending mail for {countryCode}");
            }
        }


        private string GetCsvFilePath(string countryCode)
        {
            string batchLogPath =
                ConfigurationManager.AppSettings[BATCHLOGPATH];

            if (string.IsNullOrWhiteSpace(batchLogPath))
            {
                batchLogPath = "BATCHLOG";
            }

            string dateFolder =
                DateTime.Now.ToString("dd-MMM-yyyy");

            return Path.Combine(
                batchLogPath,
                countryCode,
                dateFolder,
                "VehicleExport.csv");
        }


        private string GetEmailId(string countryCode)
        {
            /*
             * TODO:
             * Call P_GET_JTT_REPORT_MAILLIST through your DAO.
             *
             * The stored procedure returns:
             *
             *     EMAILIST
             *
             * from JTT/JTTDETAIL where
             * JTT.JttCode = 'MAIL_REPORT_LIST'
             * and JTTDETAIL.Active = 'Y'.
             *
             * Since HR and SI use different country databases,
             * JobExecutorDAO(CountryCode) should execute the
             * procedure against the appropriate country DB.
             */

            JobExecutorDAO jobDAO = new JobExecutorDAO(countryCode);

            // Replace the following line with the DAO method
            // you create for P_GET_JTT_REPORT_MAILLIST.
            //
            // Example:
            // return jobDAO.GetJttReportMailList();

            return string.Empty;
        }
    }
}
