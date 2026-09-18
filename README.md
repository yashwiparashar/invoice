using Ald.VehicleUpload.Business.BO.JatoOperation;
using Ald.VehicleUpload.Business.Interfaces.JatoOperation;
using Ald.VehicleUpload.Common.BusinessEntities.JatoOperation;
using Ald.VehicleUpload.Common.BusinessEntities.JobExecutor;
using Ald.VehicleUpload.Common.Constants;
using Ald.VehicleUpload.Common.Enums;
using Ald.VehicleUpload.DataAccess.DAO.JobExecutorDataService;
using Ald.VehicleUpload.DataAccess.DAO.Log;
using Ald.VehicleUpload.DataAccess.Interface.JatoOperationInterface;
using Ald.VehicleUpload.Shared.Components;
using Aspose.Cells;
using Quartz;
using Serilog;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using JatoExecutor = Ald.VehicleUpload.Common.BusinessEntities.JobExecutor;

namespace Ald.VehicleUpload.Business.BO.JobExecutor
{
    public class NWJatoProcess : IJob
    {
        private const string MODULENAMENW = "VehicleUpload Process";
        private ILog _logNW = null;
        private ILogDetail _iLogDetailNW = null;
        private const string BATCHLOGPATHNW = "BATCHLOGPATH";
        private const string DEFAULTBATCHLOGPATHNW = "BATCHLOG";
        private const string BATCH_FROMADDRNW = "BATCH_FROMADDR";
        private const string BATCH_SMTPSERVERNW = "BATCH_SMTPSERVER";
        private readonly static ILogger _logger = Log.Logger.ForContext<NWJatoProcess>();

        public string CountryCode { get; set; } = string.Empty;

        public string CountryName { get; set; } = string.Empty;
        public NWJatoProcess()
        {
            Aspose.Cells.License license = new Aspose.Cells.License();
            license.SetLicense("Aspose.Total.lic");
        }

        async Task IJob.Execute(IJobExecutionContext context)
        {
            try
            {
                JobDataMap _dataMap = context.JobDetail.JobDataMap;
                CountryCode = _dataMap.GetString("CODE");
                CountryName = _dataMap.GetString("NAME");
                RunProcessNW();
            }
            catch (Exception ex)
            {

                UpdateLog(ProcessStatus.Pending, string.Empty, $"Error in NWJatoProcess Execute Method - {CountryCode}:{DateTime.Now} {ex}");
                await Task.CompletedTask;
            }
            await Task.CompletedTask;
        }

        public void Execute(IJobExecutionContext context)
        {
            try
            {
                JobDataMap _dataMap = context.JobDetail.JobDataMap;
                CountryCode = _dataMap.GetString("CODE");
                CountryName = _dataMap.GetString("NAME");
                RunProcessNW();
            }
            catch (Exception ex)
            {
                UpdateLog(ProcessStatus.Pending, string.Empty, $"Error in NWJatoProcess Execute Method - {CountryCode}:{DateTime.Now} {ex}");
            }
        }
        /// <summary>
        /// The main entry point for the application. isConsoleMode Flag is to indicate whether the job is
        /// started by service or Manually(ConsoleMode) using Console. In case of console, it forcefully creates or updates
        /// the job/stage status to pending and start processing. Where as in case of Service, it creates the if not present and process it
        /// if there is any pending stage in queue.
        /// </summary>
        public void RunProcessNW()
        {
            RuntimeWorkObject workObjectNW = new RuntimeWorkObject();
            StageCollection stageCollectionNW = new StageCollection();
            NWJobExecutorDAO jobDAONW = new NWJobExecutorDAO(CountryCode);
            _logNW = new ProcessLog(CountryCode);
            UpdateLog(ProcessStatus.Processing, string.Empty, $"RunProcessNW started for Country code - {CountryCode}:{DateTime.Now}");

            try
            {
                JatoJob jobstillprocessing = jobDAONW.IsJobStillProcessing();
                JatoExecutor.Configuration configr = new JatoExecutor.Configuration
                {
                    ParameterList = jobDAONW.LoadConfigParamFromDB(),
                    CustomParamList = jobDAONW.LoadCustomConfigParamFromDB(RuntimeConstants.JT_PHOTO_PARAMLIST)
                };
                configr.SetParamValue(CommonConst.COUNTRYCODE, CountryCode);
                //Check if any JATO JOB is still PROCESSING, if yes send a mail and don,t start any JATO PROCESS
                if ((jobstillprocessing.JobId > 0) && (jobstillprocessing.JobStatus == ProcessStatus.Processing.ToString()))
                {
                    SendMailJobStillProcessingNW(jobstillprocessing.JobId, configr, jobDAONW.GetLogEMailList(), jobstillprocessing.CreatedDt);
                }
                else
                {
                    JatoJob jobNW = jobDAONW.IsJobExists(DateTime.Now);

                    if (jobNW.JobId < 0 || jobNW.JobStatus == ProcessStatus.Failed.ToString())
                    {
                        jobNW = CreateJobObject();
                        jobNW = jobDAONW.SaveJob(jobNW);
                    }
                    if ((jobNW.JobId > 0) && jobNW.JobStatus == ProcessStatus.Pending.ToString())
                    {
                        try
                        {
                            workObjectNW.StageInfo.Clear();
                            stageCollectionNW.Stages.Clear();
                            // Values are expected during execution at stages
                            workObjectNW.StageInfo[RuntimeConstants.USERCODE] = MODULENAMENW;
                            workObjectNW.StageInfo[RuntimeConstants.JOBID] = jobNW.JobId.ToString();

                            stageCollectionNW = jobDAONW.GetJobStages(Convert.ToInt32(jobNW.JobId));
                            // Update the Job Status to "Processing"
                            jobNW.JobStatus = ProcessStatus.Processing.ToString();
                            jobDAONW.UpdateJobStatus(jobNW);
                            UpdateLog(ProcessStatus.Processing, string.Empty, $"Initializing the Configuration for Country code - {CountryCode}:{DateTime.Now}");
                            string batchlogpath = CreateBatchLogPath();
                            ExecuteJobNW(CountryCode, workObjectNW, stageCollectionNW, jobDAONW, ref jobNW, batchlogpath);
                            if (jobNW.JobStatus == ProcessStatus.Completed.ToString())
                            {
                                UpdateLog(ProcessStatus.Completed, MODULENAMENW, $"jobNW Completed Successfully and log Files are exported at : {batchlogpath}");
                            }
                        }
                        catch (Exception ex)
                        {
                            UpdateLog(ProcessStatus.Failed, MODULENAMENW, $"jobNW Failed. Error Detail : {ex.Message}");
                        }
                        finally
                        {
                            jobDAONW.UpdateJobStatus(jobNW);
                        }
                    }
                    else
                    {
                        UpdateLog(ProcessStatus.Completed, string.Empty, $"No Jobs Available in Pending Stage for Country : {CountryCode}");
                    }
                    GC.Collect();
                }
            }
            catch
            {
                UpdateLog(ProcessStatus.Processing, string.Empty, $"Failed While Creating jobNW for Country : {CountryCode}");
            }
        }

        private void ExecuteJobNW(string countryCode, RuntimeWorkObject workObjectNW, StageCollection stageCollectionNW,
                                                                NWJobExecutorDAO jobDAONW, ref JatoJob jobNW, string batchlogpath)
        {
            string nextStep = string.Empty;
            DateTime starttime;
            DateTime endtime;
            JatoExecutor.Configuration config = new JatoExecutor.Configuration();
            var _workObject = workObjectNW;
            try
            {
                var customParmeterList = new List<string> { RuntimeConstants.JT_PHOTO_PARAMLIST, RuntimeConstants.MILES_TEMPLATE_CONFIGURATION };

                _iLogDetailNW = new LogDetailBO(_logNW);
                config.ParameterList = jobDAONW.LoadConfigParamFromDB();
                config.CustomParamList = jobDAONW.LoadCustomConfigParamFromDB(string.Join(",", customParmeterList));
                // Add the country code to the parameter collection
                config.SetParamValue(CommonConst.COUNTRYCODE, countryCode);
                UpdateLog(ProcessStatus.Processing, MODULENAMENW, "Successfully collected configurations from Vehicle Technical Tables.");
                if (stageCollectionNW == null || stageCollectionNW.Stages.Count == 0)
                {
                    UpdateLog(ProcessStatus.Processing, MODULENAMENW, "Could not find any ACTIVE stage configuration in JATO_STAGES.");
                    throw (new Exception("Could not find any ACTIVE stage configuration in JATO_STAGES."));
                }
                else
                {
                    nextStep = stageCollectionNW.FirstStep;
                    while (!string.IsNullOrEmpty(nextStep) && stageCollectionNW.Stages.ContainsKey(nextStep))
                    {
                        string currentStage = nextStep;
                        Stage stage = stageCollectionNW.Stages[nextStep];
                        IExecutor stageExecutor = GetExecutorInstance(stage.AssemblyName, stage.ClassName);
                        if (stageExecutor == null)
                        {
                            UpdateLog(ProcessStatus.Failed, stage.Name, $"Could not Initialize the Stage - {stage.Name}:{stage.ClassName}");
                            throw (new Exception($"Could not Initialize the Stage - {stage.Name}:{stage.ClassName}"));
                        }
                        else
                        {
                            starttime = DateTime.Now.AddMinutes(-1);
                            UpdateStageStatus(jobDAONW, jobNW, nextStep, ProcessStatus.Processing.ToString());
                            UpdateLog(ProcessStatus.Processing, stage.Name, $"{stage.Name} Stage Started successfully.");
                            _workObject = stageExecutor.Execute(_workObject, config);
                            if (_workObject.StageInfo[RuntimeConstants.STAGE_STATUS] == ProcessStatus.Completed.ToString())
                            {
                                UpdateStageStatus(jobDAONW, jobNW, nextStep, ProcessStatus.Completed.ToString());
                                UpdateLog(ProcessStatus.Processing, stage.Name, $"{stage.Name} Stage Completed successfully.");
                                endtime = DateTime.Now.AddMinutes(1);
                                nextStep = stage.NextStep;
                            }
                            else
                            {
                                UpdateStageStatus(jobDAONW, jobNW, nextStep, ProcessStatus.Failed.ToString());
                                jobNW.JobStatus = ProcessStatus.Failed.ToString();
                                UpdateLog(ProcessStatus.Failed, stage.Name, $"Process Failed on {stage.Name}:{stage.ClassName}");
                                throw (new Exception($"Process Failed on {stage.Name}:{stage.ClassName}"));
                            }
                            string logfile = $"{batchlogpath}\\Stage{stage.StageSequence}-{currentStage}.txt";
                            ExportLog(_iLogDetailNW, currentStage, starttime, endtime, batchlogpath, logfile);
                        }
                    }
                    jobNW.JobStatus = ProcessStatus.Completed.ToString();
                    UpdateLog(ProcessStatus.Processing, string.Empty, " Mailing Process Log for Vehicle Upload.");
                    SendMailNW(batchlogpath, config, jobDAONW.GetLogEMailList());
                }
            }
            catch (Exception ex)
            {
                // Update the Job Status to "Processing"
                UpdateStageStatus(jobDAONW, jobNW, nextStep, ProcessStatus.Failed.ToString());
                jobNW.JobStatus = ProcessStatus.Failed.ToString();
                UpdateLog(ProcessStatus.Failed, MODULENAMENW, $"Application Failed. Error Details: {ex.Message}");
            }
        }
        /// <summary>
        /// Exports Log from Vehicle_upload_log into a separate file. Rejected Vehicles are exported separately if the it is LOAH20.
        /// </summary>
        /// <param name="logobject"></param>
        /// <param name="currentStage"></param>
        /// <param name="starttime"></param>
        /// <param name="endtime"></param>
        /// <param name="batchlogpath"></param>
        private void ExportLog(ILogDetail logobject, string currentStage, DateTime starttime,
            DateTime endtime, string batchlogpath, string logfile)
        {
            if (currentStage == JatoStage.LOAH20.ToString())
            {
                var REJECTEDVEHICLECATEGORYNW = new string[] { "RV", "FV" };
                string rejectedLogFile = $"{batchlogpath}\\RejectedVehicles.txt";
                logobject.ExportLogDetail(currentStage, starttime, endtime, logfile, rejectedLogFile, REJECTEDVEHICLECATEGORYNW);
            }
            else
            {
                logobject.ExportLogDetail(currentStage, starttime, endtime, logfile);
            }
        }

        private string CreateBatchLogPath()
        {
            string batchlogpath;
            try
            {
                batchlogpath = ConfigurationManager.AppSettings[BATCHLOGPATHNW].ToString();
            }
            catch
            {
                batchlogpath = DEFAULTBATCHLOGPATHNW;
            }
            if (!string.IsNullOrEmpty(CountryName))
            {
                batchlogpath = $"{batchlogpath}\\{CountryCode}";
            }

            batchlogpath = $"{batchlogpath}\\{DateTime.Now:dd-MMM-yyyy}";

            if (!Directory.Exists(batchlogpath))
            {
                Directory.CreateDirectory(batchlogpath);
            }
            return batchlogpath;
        }

        private void UpdateStageStatus(NWJobExecutorDAO jobDAONW, JatoJob jobNW, string nextStep, string stageStatus)
        {
            JatoJobDetail jobDetailNW = new JatoJobDetail
            {
                JobId = jobNW.JobId,
                StageCode = nextStep,
                StageStatus = stageStatus
            };
            jobDAONW.UpdateStageStatus(jobDetailNW);
        }

        /// <summary>
        /// Call The mail component to send mail to the configured address.
        /// </summary>
        /// <param name="batchlogPath"></param>
        private void SendMailNW(string batchlogPath, JatoExecutor.Configuration config, string emailist)
        {
            EmailEntities NWentities = new EmailEntities
            {
                ToAddress = emailist, // Default Assignment if there is no CC present
                FromAddress = config.GetParamValue(BATCH_FROMADDRNW),
                SmtpHost = config.GetParamValue(BATCH_SMTPSERVERNW),
                Subject = $"{CountryCode} : {CountryName}" + " - " + GetAppSettingsValue(RuntimeConstants.EMAILSUBJECT),
                Body = GetAppSettingsValue(RuntimeConstants.EMAILCONTENT),
                Discliamer = GetAppSettingsValue(RuntimeConstants.EMAILDISCLIMER)
            };
            string[] files = Directory.GetFiles(batchlogPath, "*.txt");

            var txtFilelist = string.Join(";", files);
            files = Directory.GetFiles(batchlogPath, "*.csv");
            var csvFilelist = string.Join(";", files);
            var filelisNW = txtFilelist + ";" + csvFilelist;

            filelisNW = filelisNW.Remove(filelisNW.Length - 1);
            NWentities.DocumentPath = filelisNW;
            try
            {
                if (NWentities.ToAddress.Length > 0 && NWentities.FromAddress.Length > 0 && NWentities.SmtpHost.Length > 0)
                {
                    Emailing.SendMail(NWentities);
                    UpdateLog(ProcessStatus.Processing, string.Empty, $"Mail sent to {NWentities.ToAddress} from {NWentities.FromAddress} successfully.");
                }
            }
            catch (Exception ex)
            {
                UpdateLog(ProcessStatus.Processing, string.Empty, $"Failed while mailing  to {NWentities.ToAddress} from {NWentities.FromAddress} successfully. " +
                    $"Error Detail : {ex.Message} ");
            }
        }

        /// <summary>
        /// SENDMAIL IF THE JATO JOB is STILL in PROCESSING
        /// </summary>
        /// <param name="jobId"></param>
        /// <param name="config"></param>
        /// <param name="emailist"></param>
        private void SendMailJobStillProcessingNW(int jobId, JatoExecutor.Configuration config, string emailist, DateTime? crtdt)
        {
            string subject = $"{CountryCode} : {CountryName}";
            EmailEntities NWentities = new EmailEntities
            {
                ToAddress = emailist,
                FromAddress = config.GetParamValue(BATCH_FROMADDRNW),
                SmtpHost = config.GetParamValue(BATCH_SMTPSERVERNW),
                Subject = $"{subject} - Warning JATO PROCESS is not triggered on {DateTime.Today.ToShortDateString()}",
                //JATO Process for Job ID :1715 is still in progress due to that today's ( )  process is not started.
                Body = $"NETWHEELS Process for Job ID :{jobId} is still in progress from {crtdt}, due to that today's process is not started.",
                Discliamer = GetAppSettingsValue(RuntimeConstants.EMAILDISCLIMER)
            };
            try
            {
                if (NWentities.ToAddress.Length > 0 && NWentities.FromAddress.Length > 0 && NWentities.SmtpHost.Length > 0)
                {
                    Emailing.SendProcessingMail(NWentities);
                    UpdateLog(ProcessStatus.Processing, string.Empty,
                        $"Mail sent to {NWentities.ToAddress} from {NWentities.FromAddress} successfully.");
                }
            }
            catch (Exception ex)
            {
                UpdateLog(ProcessStatus.Processing, string.Empty, $"Failed while mailing Job Still Processing  to {NWentities.ToAddress} from {NWentities.FromAddress} successfully. Error Detail : {ex.Message} ");
            }
        }

        private JatoJob CreateJobObject()
        {
            JatoJob jobNW = new JatoJob
            {
                JobStatus = ProcessStatus.Pending.ToString(),
                CreatedBy = "System",
                JobDescription = $"Vehicle Upload Process {DateTime.Now.Date:dd-MM-yyyy}"
            };
            return jobNW;
        }

        private string GetAppSettingsValue(string key)
        {
            try
            {
                AppSettingsReader apr = new AppSettingsReader();
                return apr.GetValue(key, typeof(string)).ToString();
            }
            catch
            {
                return string.Empty;
            }
        }

        private IExecutor GetExecutorInstance(string assemblyName, string className)
        {
            IExecutor executor = null;
            try
            {
                Assembly asm = Assembly.LoadFrom($"{AppDomain.CurrentDomain.BaseDirectory}\\{assemblyName}");
                if (asm != null)
                {
                    executor = (IExecutor)asm.CreateInstance(className);
                }
            }
            catch (Exception ex)
            {
                _logger.Error(ex, $"Exception in GetExecutorInstance Assembly Could not be loaded for : {className}");
                UpdateLog(ProcessStatus.Processing, $"Assembly Could not be loaded for : {className}", "");
            }
            return executor;
        }

        private void UpdateLog(ProcessStatus status, string stageid, string message)
        {
            LogDetail logDetail = new LogDetail(string.Empty, string.Empty, status.ToString(), stageid, message);
            _logNW.InsertIntoProcessLog(logDetail);
        }
    }
}
