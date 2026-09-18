using Ald.VehicleUpload.Common.BusinessEntities.JatoMaster;
using Ald.VehicleUpload.DataAccess.DAO.JobExecutorDataService;
using Ald.VehicleUpload.DataAccess.Interface.ImportUtilitiesInterface;
using Ald.VehicleUpload.DataAccess.Interface.JatoMasterInterface;
using Ald.VehicleUpload.DataAccess.Interface.MCRVTableInterface;
using Ald.VehicleUpload.Shared.Components;
using Aspose.Cells;
using Quartz;
using Serilog;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using JatoExecutor = Ald.VehicleUpload.Common.BusinessEntities.JobExecutor;

namespace Ald.VehicleUpload.Business.BO.JobExecutor
{
    public class EmailSendProcess : IJob
    {
        private readonly static ILogger _logger = Log.Logger.ForContext<EmailSendProcess>();
        private const string BATCHLOGPATH = "BATCHLOGPATH";
        private const string BATCH_FROMADDRES = "BATCH_FROMADDR";
        private const string BATCH_SMTPSERVERES = "BATCH_SMTPSERVER";
        private readonly IJttDetailCommon _iJttDetailCommon = null;

        public string CountryCode { get; set; } = string.Empty;

        public string CountryName { get; set; } = string.Empty;

        async Task IJob.Execute(IJobExecutionContext context)
        {
            try
            {
                JobDataMap _dataMap = context.JobDetail.JobDataMap;
                CountryCode = _dataMap.GetString("CODE");
                CountryName = _dataMap.GetString("NAME");
                RunEmailProcess();
            }
            catch (Exception ex)
            {
                _logger.Error(ex.InnerException, $"An error occurred while Scheduling/Triggering EmailSendProcess-Excecute() : {CountryCode}");
                await Task.CompletedTask;

            }
            await Task.CompletedTask;
        }
        public void RunEmailProcess()
        {
            JobExecutorDAO jobDAO = new JobExecutorDAO(CountryCode);
     
                string filePath = GetCsvFilePath(CountryCode);

                if (File.Exists(filePath))
                {
                    string emailId = GetEmailId(CountryCode);

                    SendEmail(emailId, filePath, CountryCode);
                }
            
        }


        private void SendEmail(string emailId, string filePath, string countryCode)
        {
            EmailEntities entities = new EmailEntities
            {
                ToAddress = emailId,
                FromAddress = config.GetParamValue(BATCH_FROMADDR),
                SmtpHost = config.GetParamValue(BATCH_SMTPSERVER),
                Subject = countryCode + " - Vehicle Export",
                Body = "Please find attached the Vehicle Export file.",
                //Disclaimer = "",
                DocumentPath = filePath,
                IsAttachmentAvailable = true
            };

            Emailing.SendMail(entities);
        }
        private string GetCsvFilePath(string countryCode)
        {
            string batchLogPath = ConfigurationManager.AppSettings[BATCHLOGPATH].ToString();

            string dateFolder = DateTime.Now.ToString("dd-MMM-yyyy");

            return Path.Combine(
                batchLogPath,
                countryCode,
                dateFolder,
                "VehicleExport.csv");
        }

         private string GetEmailId(string countryCode)
        {



        }
    }
}
