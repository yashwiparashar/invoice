
using AutoMapper;
using ClosedXML.Excel;
using IkpReporting.Abstractions.Constant;
using IkpReporting.Abstractions.EnumConstant;
using IkpReporting.Abstractions.Extensions;
using IkpReporting.Abstractions.Interfaces;
using IkpReporting.Abstractions.Model.Models;
using IkpReporting.Abstractions.Model.User;
using IkpReporting.SharedUtilities.Cryptography;
using IkpReporting.SharedUtilities.DateTime;
using IkpReporting.Domain.Interfaces.Repositories;
using Microsoft.Extensions.Configuration;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net.Mail;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using IkpReporting.SharedUtilities.Mail;

namespace IkpReporting.Application.Services
{
    public class UserService : IUserService
    {
        private readonly IUserRepository _userRepository;
        private readonly IConfiguration _configuration;
        private readonly IHashService _hashService;
        private readonly IReferentialRepository _referentialRepository;
        private readonly IMapper _mapper;
        private readonly IMailService _mailService;
        private readonly IDateTimeService _dateTimeService;


        public UserService(IUserRepository userRepository, IConfiguration configuration
                , IHashService hashService, IReferentialRepository referentialRepository,
            IMapper mapper, IMailService mailService, IDateTimeService dateTimeService
            )
        {
            _userRepository = userRepository;
            _configuration = configuration;
            _hashService = hashService;
            _referentialRepository = referentialRepository;
            _mapper = mapper;
            _mailService = mailService;
            _dateTimeService = dateTimeService;
        }
        public async Task<ReturnValue<ResultUserList>> GetUsersAsync(UserExportDto userContext)
        {
            var result = await _userRepository.GetUsersAsync(userContext).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<ResultUserList>> GetAllUsersAsync()
        {
            var result = await _userRepository.GetAllUsersAsync().ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<UserDto>> GetUserAsync(UserListContextDto userContext)
        {
            var user = await _userRepository.GetUserAsync(userContext).ConfigureAwait(false);
            if (user.IsSuccess)
            {
                var result = _mapper.Map<UserDto>(user.Value);
                return ReturnValue<UserDto>.Success(result);
            }
            return ReturnValue<UserDto>.Failure(user.ErrorMessage);
        }
        public async Task<ReturnValue<ResultLoginUserDto>> GetLoginUserAsync(string userId)
        {
            var user = await _userRepository.GetLoginUserAsync(userId)
                .BindAsync(userDetails => _userRepository.SaveLoginInfoDetailAsync(userId)
                .BindAsync(isSaved => SetPartnerNameAsync(userDetails))).ConfigureAwait(false);
            return user;
        }
        private async Task<ReturnValue<ResultLoginUserDto>> SetPartnerNameAsync(ResultUserDetailsDto user)
        {
            string partnerName = "default";
            if (user?.ScopeDto?.PartnerGroupHoldingId == null)
            {
                return ReturnValue<ResultLoginUserDto>.Failure(_configuration[IkpConstant.NoUser]);
            }
            var pghIds = user.ScopeDto.PartnerGroupHoldingId;
            if (user.ScopeDto.PartnerGroupHoldingId != null && pghIds.Count() == 1)
            {
                var partnerGroupHoldingDto = await _referentialRepository.GetPartnerGroupHoldingByIdAsync(pghIds.FirstOrDefault()).ConfigureAwait(false);
                partnerName = Regex.Replace(partnerGroupHoldingDto.Value.PartnerGroupHoldingName, "[^a-zA-z0-9]", "", RegexOptions.None, TimeSpan.FromSeconds(5));
            }
            user.PartnerName = partnerName;
            var result = _mapper.Map<ResultLoginUserDto>(user);
            return ReturnValue<ResultLoginUserDto>.Success(result);

        }
        public async Task<ReturnValue<bool>> ResetPasswordAsync(ResetDetails resetDetails)
        {
            if (resetDetails == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:resetDetailRequired"]);

            }
            var userDetails = await _userRepository.GetLoginUserDetailByUserIdAsync(resetDetails.UserId).ConfigureAwait(false);
            var IsValid = _hashService.Verify(resetDetails.CurrentPassword, userDetails.Value.Password);
            if (!IsValid.IsSuccess)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordMisMatch"]);
            }
            var result = await _userRepository.UpdatePasswordAsync(userDetails.Value, _hashService.Generate(resetDetails.NewPassword).Value).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<bool>> SetPasswordAsync(LoginDetail loginDetail)
        {
            if (loginDetail == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:loginRequired"]);
            }
            var result = await _userRepository.GetLoginUserDetailByPasswordChangeCodeAsync(loginDetail.PasswordChangeCode)
                .BindAsync(userDetails => _userRepository.UpdatePasswordAsync(userDetails, _hashService.Generate(loginDetail.Password).Value)).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<bool>> SendForgetNotificationLinkAsync(string email)
        {
            var userDetails = await _userRepository.GetLoginUserbyEmailAsync(email).ConfigureAwait(false);
            if (!userDetails.IsSuccess)
            {
                return ReturnValue<bool>.Success(true);
            }
            var result = await _userRepository.UpdatePasswordChangeCodeAsync(userDetails.Value)
                .BindAsync(IsUpdated => _userRepository.GetLoginUserbyEmailAsync(email)).ConfigureAwait(false);
            if (!result.IsSuccess)
            {
                return ReturnValue<bool>.Failure(result.ErrorMessage);

            }
            var mailMessageContext = new MailMessageContext
            {
                SmtpServer = _configuration["MailDetail:Server"],
                SmtpServerPort = int.Parse(_configuration["MailDetail:Port"], CultureInfo.InvariantCulture),
                IsSslEnabled = bool.Parse(_configuration["CommonSettings:IsSslEnabled"]),
                BodyEncodingFormat = Encoding.UTF8,
                IsBodyHtml = true,
                Subject = _configuration["MailDetail:ResetPasswordSubject"],
                From = _configuration["MailDetail:From"],
                Body = ResetPasswordMailContent(result.Value),
                To = new[] { result.Value.Email }
            };
            await _mailService.SendMailAsync(mailMessageContext).ConfigureAwait(false);
            return ReturnValue<bool>.Success(true);
        }

        public async Task<ReturnValue<bool>> IsPasswordChangeCodeValidAsync(string passwordChangeCode)
        {
            var result = await _userRepository.IsPasswordChangeCodeValidAsync(passwordChangeCode).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<bool>> CreateUserAsync(UserDto userDetails)
        {
            if (userDetails != null)
            {
                var IsUserExists = await IsUserEmailExistsAsync(userDetails.Email).ConfigureAwait(false);
                if (!IsUserExists.Value)
                {
                    var result = await _userRepository.CreateOrUpdateUserAsync(userDetails).ConfigureAwait(false);
                    return result;
                }
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:EmailExists"]);

            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:UserDetailRequired"]);
        }

        public async Task<ReturnValue<bool>> SendUsersListAsync(UserExportDto userContext)
        {
            var userDetails = await _userRepository.GetExportUsersAsync(userContext).ConfigureAwait(false);
            if (userDetails.IsSuccess)
            {
                const string fileName = "ExportAllIKPUsers.xlsx";
                using (var workbook = new XLWorkbook())
                {
                    var worksheet = workbook.Worksheets.Add("IKP Users");
                    var currentRow = 1;
                    worksheet.Cell(currentRow, (int)CellNumber.One).Value = "FirstName";
                    worksheet.Cell(currentRow, (int)CellNumber.Two).Value = "LastName";
                    worksheet.Cell(currentRow, (int)CellNumber.Three).Value = "Email";
                    worksheet.Cell(currentRow, (int)CellNumber.Four).Value = "Profile";
                    worksheet.Cell(currentRow, (int)CellNumber.Five).Value = "Sales Type";
                    worksheet.Cell(currentRow, (int)CellNumber.Six).Value = "Customer Legal Type";
                    worksheet.Cell(currentRow, (int)CellNumber.Seven).Value = "Role";
                    worksheet.Cell(currentRow, IkpConstant.ExportUserStatusColumn).Value = "Status";
                    worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameBothColumn).Value = "Direct Subsidiary";
                    worksheet.Cell(currentRow, IkpConstant.ExportUserTerritoryNameBothColumn).Value = "Direct Territory";
                    worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameColumn).Value = "Indirect Subsidiary";
                    worksheet.Cell(currentRow, IkpConstant.ExportTerritoryNameColumn).Value = "Indirect Territory";
                    worksheet.Rows(currentRow, (int)CellNumber.One).Style.Font.FontColor = XLColor.White;
                    worksheet.Rows(currentRow, (int)CellNumber.One).Style.Fill.BackgroundColor = XLColor.Blue;
                    worksheet.Columns().Width = IkpConstant.ExportColumnWidth;
                    worksheet.Columns().Style.Alignment.Horizontal = XLAlignmentHorizontalValues.Left;
                    foreach (var usr in userDetails.Value.UserListData)
                    {
                        currentRow++;
                        worksheet.Cell(currentRow, (int)CellNumber.One).Value = usr.FirstName;
                        worksheet.Cell(currentRow, (int)CellNumber.Two).Value = usr.LastName;
                        worksheet.Cell(currentRow, (int)CellNumber.Three).Value = usr.Email;
                        worksheet.Cell(currentRow, (int)CellNumber.Four).Value = usr.Profile;
                        worksheet.Cell(currentRow, (int)CellNumber.Five).Value = usr.SalesType;
                        worksheet.Cell(currentRow, (int)CellNumber.Six).Value = usr.CustomerLegalType;
                        worksheet.Cell(currentRow, (int)CellNumber.Seven).Value = usr.Role;
                        worksheet.Cell(currentRow, IkpConstant.ExportUserStatusColumn).Value = usr.UserStatusName;
                        if (usr.SalesType == IkpConstant.SalesTypeDirect)
                        {
                            usr.SubsidiaryNameBoth = usr.SubsidiaryName;
                            usr.SubsidiaryName = null;
                            usr.TerritoryNameBoth = usr.TerritoryName;
                            usr.TerritoryName = null;
                        }
                        worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameBothColumn).Value = usr.SubsidiaryNameBoth;
                        worksheet.Cell(currentRow, IkpConstant.ExportUserTerritoryNameBothColumn).Value = usr.TerritoryNameBoth;
                        worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameColumn).Value = usr.SubsidiaryName;
                        worksheet.Cell(currentRow, IkpConstant.ExportTerritoryNameColumn).Value = usr.TerritoryName;
                    }

                    using MemoryStream stream = new();
                    workbook.SaveAs(stream);
                    byte[] byteArr = stream.ToArray();
                    MemoryStream streamFromByte = new(byteArr, true);
                    await streamFromByte.WriteAsync(byteArr).ConfigureAwait(false);
                    streamFromByte.Position = 0;
                    var IsSslEnabled = bool.Parse(_configuration["CommonSettings:IsSslEnabled"]);
                    using SmtpClient client = new(_configuration["MailDetail:Server"],
                        int.Parse(_configuration["MailDetail:Port"], CultureInfo.InvariantCulture))
                    {
                        EnableSsl = true
                    };
                    client.EnableSsl = IsSslEnabled;
                    using MailMessage msg = new();
                    msg.Subject = string.Concat("IKP",_configuration["MailDetail:SubjectEnvironmentName"]," User Lists");
                    msg.Body = ExportMailContent();
                    msg.To.Add(_configuration["MailDetail:To"]);
                    msg.From = new MailAddress(_configuration["MailDetail:From"]);
                    msg.BodyEncoding = Encoding.UTF8;
                    msg.IsBodyHtml = true;
                    msg.Attachments.Add(new Attachment(streamFromByte, fileName));
                    client.Send(msg);
                }
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);

        }
        private string ResetPasswordMailContent(ResultUserDetailsDto userDetails)
        {
            var mailContent = _configuration["MailDetail:MailTemplate"];
            var url = _configuration["MailDetail:ChangeLink"] + IkpConstant.ChangePasswordUrlLink + userDetails.PasswordChangeCode;
            var resetBody = _configuration["MailDetail:ResetBody"];
            var restPassword = _configuration["MailDetail:RestPassword"];
            var result = String.Format(CultureInfo.InvariantCulture, mailContent, userDetails.FirstName, userDetails.LastName, resetBody, url, restPassword, CultureInfo.InvariantCulture);
            return result;
        }
        private string ExportMailContent()
        {
            var exportMailContent = _configuration["MailDetail:ExportMailConent"];
            var result = String.Format(CultureInfo.InvariantCulture, exportMailContent, _configuration["MailDetail:TeamName"], CultureInfo.InvariantCulture);
            return result;
        }
        public async Task<ReturnValue<bool>> UpdateUserDisclaimerStatusAsync(string userId)
        {
            if (userId != null)
            {

                var userDetail = await _userRepository.GetLoginUserAsync(userId).ConfigureAwait(false);
                if (userDetail.IsSuccess)
                {
                    userDetail.Value.FirstConnection = _dateTimeService.Now();
                    userDetail.Value.LastConnection = _dateTimeService.Now();
                    var result = await _userRepository.UpdateUserStatusAsync(userDetail.Value).ConfigureAwait(false);
                    return result;
                }
            }
            return ReturnValue<bool>.Failure(_configuration["LogConstants:UserStatusUpdateFail"]);
        }
        public async Task<ReturnValue<bool>> IsUserEmailExistsAsync(string emailId)
        {
            var result = await _userRepository.IsUserEmailExistsAsync(emailId).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<bool>> UpdateUserAsync(UserDto userDetails)
        {
            if (userDetails != null)
            {
                UserListContextDto userListContext = new UserListContextDto();
                userListContext.UserId = userDetails.Id;
                var existingUser = await _userRepository.GetUserAsync(userListContext).ConfigureAwait(false);
                if (existingUser.IsSuccess)
                {
                    userDetails.Email = existingUser.Value.Email;
                    var result = await _userRepository.CreateOrUpdateUserAsync(userDetails).ConfigureAwait(false);
                    return result;
                }
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:UserNotSaved"]);

        }
        public async Task<ReturnValue<bool>> DeleteUserAsync(string userId)
        {
            UserListContextDto userListContext = new UserListContextDto();
            userListContext.UserId = userId;
            var result = await _userRepository.DeleteUserAsync(userListContext).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<bool>> LogOutAsync(string userId)
        {
            var result = await _userRepository.LogOutAsync(userId).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<UserScopeDto>> GetUserScopeDetailsAsync(string userId)
        {
            if (userId != null)
            {
                return await _userRepository.GetUserScopeDetailsAsync(userId).ConfigureAwait(false);
            }
            return ReturnValue<UserScopeDto>.Failure(_configuration["LogConstants:UserStatusUpdateFail"]);
        }
        public async Task<ReturnValue<List<UserScopeDto>>> GetUsersScopeDetailsAsync()
        {
            return await _userRepository.GetUsersScopeDetailsAsync().ConfigureAwait(false);
        }
    }
}












































































































































 USER REPOSITORY 


 
using AutoMapper;
using IkpReporting.Abstractions.Constant;
using IkpReporting.Abstractions.EnumConstant;
using IkpReporting.Abstractions.Model.Models;
using IkpReporting.Abstractions.Model.User;
using IkpReporting.Abstractions.Model.UserProperties;
using IkpReporting.SharedUtilities.DateTime;
using IkpReporting.SharedUtilities.EncodeDecode;
using IkpReporting.Domain.Entities;
using IkpReporting.Domain.Interfaces.Repositories;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using System;
using System.Collections.Generic;
using System.Data;
using System.Data.SqlClient;
using System.Globalization;
using System.Linq;
using System.Linq.Expressions;
using System.Threading.Tasks;
using IkpReporting.SharedUtilities.ApplicationContext;

namespace IkpReporting.Infra.Data.Repositories
{
    public class UserRepository : IUserRepository
    {
        private readonly RepositoryContext _context;
        private readonly IMapper _mapper;
        private readonly IConfiguration _configuration;
        private readonly IReferentialRepository _referentialStorage;
        private readonly IApplicationContext _applicationContext;
        private readonly IDateTimeService _dateTimeService;
        private readonly IEncodeDecodeService _encodeDecodeService;

        public UserRepository(RepositoryContext context, IMapper mapper, IConfiguration configuration,
            IReferentialRepository referentialStorage, IApplicationContext applicationContext,
            IDateTimeService dateTimeService, IEncodeDecodeService encodeDecodeService)
        {
            _context = context;
            _mapper = mapper;
            _configuration = configuration;
            _referentialStorage = referentialStorage;
            _applicationContext = applicationContext;
            _dateTimeService = dateTimeService;
            _encodeDecodeService = encodeDecodeService;
        }
        public async Task<ReturnValue<ResultUserList>> GetAllUsersAsync()
        {
            var resultUserList = new ResultUserList();

            var users = await _context.Users.ToListAsync().ConfigureAwait(false);
            var activeUsers = users.Where(t => t.UserStatusId == (int)UserStatusEnumConstant.Active || t.UserStatusId == (int)UserStatusEnumConstant.Disclaimer).ToList();
            var result = _mapper.Map<List<UserListDto>>(activeUsers);
            if (result != null)
            {
                resultUserList.UserListData = result;
                return ReturnValue<ResultUserList>.Success(resultUserList);
            }
            return ReturnValue<ResultUserList>.Failure(_configuration["ErrorConstants:NoUsers"]);
        }
        public async Task<ReturnValue<ResultUserList>> GetUsersAsync(UserExportDto userContext)
        {
            ResultUserList result = null;
            var users = await (from d in _context.Scopes
                               join salescope in _context.SalesTypeHolding
                               on d.SalesTypeHoldingId equals salescope.Id into salesGroup
                               from m in salesGroup.DefaultIfEmpty()
                               join CLTscope in _context.CustomerLegalType
                               on d.CustomerLegalTypeId equals CLTscope.Id into CLTGroup
                               from p in CLTGroup.DefaultIfEmpty()
                               join trscope in _context.Territory
                               on d.TerritoryId equals trscope.Id into trgroup
                               from n in trgroup.DefaultIfEmpty()
                               join trbothscope in _context.Territory
                               on d.TerritoryIdBoth equals trbothscope.Id into trbothgroup
                               from nb in trbothgroup.DefaultIfEmpty()
                               join subscope in _context.Country
                               on d.SubsidiaryId equals subscope.Id into subgroup
                               from q in subgroup.DefaultIfEmpty()
                               join subbothscope in _context.Country
                               on d.SubsidiaryIdBoth equals subbothscope.Id into subbothscope
                               from qb in subbothscope.DefaultIfEmpty()
                               where ((userContext.ProfileName == ProfileNameConstants.SuperAdmin)
                                  || userContext.ProfileName != ProfileNameConstants.User
                                  && d.AdminUserId == userContext.UserId)
                               orderby d.UserId
                               select new UserListDto
                               {
                                   UserId = _encodeDecodeService.EncodeToString(d.UserId),
                                   FirstName = d.UserEntity.FirstName,
                                   LastName = d.UserEntity.LastName,
                                   Profile = d.Profile.ProfileName,
                                   Email = d.UserEntity.Email,
                                   SalesType = m.SalesTypeHoldingName,
                                   UserStatusName = d.UserEntity.UserStatus.Status,
                                   CustomerLegalType = p.CustomerLegalTypeName,
                                   Role = d.Role.RoleName,
                                   TerritoryName = n.TerritoryName,
                                   TerritoryNameBoth = nb.TerritoryName,
                                   SubsidiaryName = q.CountryName,
                                   SubsidiaryNameBoth = qb.CountryName
                               }).ToListAsync().ConfigureAwait(false);
            if (users != null)
            {
                var filteredUser = FilterUserData(users, userContext);
                result = new ResultUserList
                {
                    UserListData = filteredUser,
                    TotalUserCount = filteredUser.Count
                };
            }
            if (result != null)
            {
                return ReturnValue<ResultUserList>.Success(result);
            }
            return ReturnValue<ResultUserList>.Failure(_configuration["ErrorConstants:NoUsers"]);
        }
        private static List<UserListDto> FilterUserData(IEnumerable<UserListDto> users, UserExportDto userContext)
        {
            if (userContext != null && userContext.UserStatusName != null && userContext.UserStatusName == UserStatusNameConstants.All)
            {
                userContext.UserStatusName = null;
            }
            IQueryable<UserListDto> query = users.AsQueryable();
            Dictionary<Func<UserExportDto, bool>, Expression<Func<UserListDto, bool>>>
            applyFilter = new Dictionary<Func<UserExportDto, bool>, Expression<Func<UserListDto, bool>>>
            {
                {V => !string.IsNullOrEmpty(userContext.UserStatusName), n => n.UserStatusName == userContext.UserStatusName },
                {V => !string.IsNullOrEmpty(userContext.SalesTypeName), n => n.SalesType == userContext.SalesTypeName },
                {V => !string.IsNullOrEmpty(userContext.RoleName), n => n.Role == userContext.RoleName },
                {V => !string.IsNullOrEmpty(userContext.CustomerLegalTypeName), n => n.CustomerLegalType == userContext.CustomerLegalTypeName },
                {V => !string.IsNullOrEmpty(userContext.SelectedProfile), n => n.Profile == userContext.SelectedProfile },
                {V => !string.IsNullOrEmpty(userContext.TerritoryName), n => n.TerritoryName == userContext.TerritoryName },
                {v => !string.IsNullOrEmpty(userContext.TerritoryNameBoth), n => n.TerritoryNameBoth == userContext.TerritoryNameBoth },
                {v => !string.IsNullOrEmpty(userContext.SubsidiaryName), n => n.SubsidiaryName == userContext.SubsidiaryName },
                {v => !string.IsNullOrEmpty(userContext.SubsidiaryNameBoth), n => n.SubsidiaryNameBoth == userContext.SubsidiaryNameBoth },
                {v => !string.IsNullOrEmpty(userContext.SearchString), n =>
                n.FirstName.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase) ||
                n.LastName.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase) ||
                n.Email.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase)
                }
            };
            query = applyFilter.Where(vn => vn.Key(userContext)).Aggregate(query, (newQuery, vn) => newQuery.Where(vn.Value));
            return query.ToList();
        }
        public async Task<ReturnValue<ResultUserList>> GetExportUsersAsync(UserExportDto userContext)
        {
            var result = await GetUsersAsync(userContext).ConfigureAwait(false);
            return result;
        }
        public async Task<ReturnValue<ResultUserDetailsDto>> GetUserAsync(UserListContextDto userContext)
        {
            var userDetail = _mapper.Map<UserExportDto>(userContext);
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Id == userDetail.UserId).ConfigureAwait(false);
            var result = _mapper.Map<ResultUserDetailsDto>(user);
            var statusName = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
            result.UserStatusName = statusName.Value;
            result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
            var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
            result.ScopeDto.Procurement = procurement.Value;
            var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
            result.ScopeDto.ProfileName = profileName.Value;
            var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
            result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
            if (result.IsSuccess)
            {
                return ReturnValue<ResultUserDetailsDto>.Success(result);
            }

            return ReturnValue<ResultUserDetailsDto>.Failure(_configuration["ErrorConstants:NoUsers"]);
        }
        public async Task<ReturnValue<LoginUserDetailsDto>> GetLoginUserDetailAsync(string email)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Email == email).ConfigureAwait(false);
            if (user != null)
            {
                var status = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
                var result = _mapper.Map<LoginUserDetailsDto>(user);
                result.UserStatusName = status.Value;
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
                result.ScopeDto.Procurement = procurement.Value;
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
                result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
                return ReturnValue<LoginUserDetailsDto>.Success(result);
            }

            return ReturnValue<LoginUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<LoginUserDetailsDto>> GetLoginUserDetailByPasswordChangeCodeAsync(string passwordChangeCode)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.PasswordChangeCode == passwordChangeCode).ConfigureAwait(false);
            if (user != null)
            {
                var status = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
                var result = _mapper.Map<LoginUserDetailsDto>(user);
                result.UserStatusName = status.Value;
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
                result.ScopeDto.Procurement = procurement.Value;
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
                result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
                return ReturnValue<LoginUserDetailsDto>.Success(result);
            }
            return ReturnValue<LoginUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<bool>> SaveLoginInfoDetailAsync(string userId)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Id == int.Parse(userId, CultureInfo.CurrentCulture)).ConfigureAwait(false);
            if (user != null)
            {
                user.PasswordChangeCode = null;
                user.UpdatedOn = _dateTimeService.Now();
                user.UpdatedBy = user.CreatedBy;
                user.LastConnection = _dateTimeService.Now();
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }

            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoChanges"]);
        }
        public async Task<ReturnValue<bool>> SaveRefreshTokenDetailAsync(int userId, string refreshToken, DateTime expiryDate)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Id == userId).ConfigureAwait(false);
            if (user != null)
            {
                user.UpdatedOn = _dateTimeService.Now();
                user.RefreshToken = refreshToken;
                user.RefreshTokenExpiry = expiryDate;
                user.UpdatedBy = user.CreatedBy;
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }

            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoChanges"]);
        }

        public async Task<ReturnValue<ResultUserDetailsDto>> GetLoginUserAsync(string userId)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Id == int.Parse(userId, CultureInfo.CurrentCulture)).ConfigureAwait(false);
            if (user != null)
            {
                var status = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
                var result = _mapper.Map<ResultUserDetailsDto>(user);
                result.UserStatusName = status.Value;
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
                result.ScopeDto.Procurement = procurement.Value;
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
                result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
                return ReturnValue<ResultUserDetailsDto>.Success(result);

            }

            return ReturnValue<ResultUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<ResultUserDetailsDto>> GetLoginUserbyEmailAsync(string emailId)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Email == emailId).ConfigureAwait(false);
            if (user != null)
            {
                var status = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
                var result = _mapper.Map<ResultUserDetailsDto>(user);
                result.UserStatusName = status.Value;
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
                result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
                var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
                result.ScopeDto.Procurement = procurement.Value;
                return ReturnValue<ResultUserDetailsDto>.Success(result);
            }

            return ReturnValue<ResultUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<bool>> IsUserEmailExistsAsync(string emailId)
        {
            var result = await _context.Users.AnyAsync(a => a.Email == emailId).ConfigureAwait(false);
            return ReturnValue<bool>.Success(result);
        }
        public async Task<ReturnValue<bool>> UpdatePasswordAsync(LoginUserDetailsDto userDetails, string password)
        {

            var existUser = await _context.Users.
                   FirstOrDefaultAsync(x => x.Id == userDetails.Id).ConfigureAwait(false);
            if (existUser != null)
            {
                existUser.Password = password;
                existUser.PasswordChangeCode = null;
                existUser.PasswordChangeCodeExpiry = null;
                existUser.UpdatedOn = _dateTimeService.Now();
                existUser.UpdatedBy = existUser.CreatedBy;
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);

            }

            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordUpdateFailed"]);
        }
        public async Task<ReturnValue<bool>> IsPasswordChangeCodeValidAsync(string passwordChangeCode)
        {
            var existUser = await _context.Users.FirstOrDefaultAsync(x => x.PasswordChangeCode == passwordChangeCode).ConfigureAwait(false);
            if (existUser != null)
            {
                if (existUser.PasswordChangeCodeExpiry != null && existUser.PasswordChangeCodeExpiry < _dateTimeService.Now())
                {
                    return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeExpired"]);
                }
                var user = _mapper.Map<UserEntity>(existUser);
                user.UpdatedOn = _dateTimeService.Now();
                user.UpdatedBy = existUser.CreatedBy;
                this._context.Entry(existUser).CurrentValues.SetValues(user);
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeInvalid"]);

        }
        public async Task<ReturnValue<bool>> UpdatePasswordChangeCodeAsync(ResultUserDetailsDto userDTO)
        {

            var existUser = await _context.Users.
                   FirstOrDefaultAsync(x => x.Id == userDTO.Id).ConfigureAwait(false);
            if (existUser != null)
            {
                existUser.PasswordChangeCode = Guid.NewGuid().ToString();
                double hours = Double.Parse(_configuration["MailDetail:ExpiryTime"], CultureInfo.CurrentCulture);
                existUser.PasswordChangeCodeExpiry = _dateTimeService.Now().AddHours(hours);
                existUser.UpdatedOn = _dateTimeService.Now();
                existUser.UpdatedBy = existUser.CreatedBy;
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeUpdateFailed"]);
        }
        public async Task<ReturnValue<bool>> CreateOrUpdateUserAsync(UserDto userDetails)
        {
            if (userDetails == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:UserDetailRequired"]);
            }
            var user = _mapper.Map<UserEntity>(userDetails);
            var procurement = await _referentialStorage.GetProcurementIdByNameAsync(userDetails.ScopeDto.Procurement).ConfigureAwait(false);
            userDetails.ScopeDto.Procurement = procurement.Value.ToString(CultureInfo.InvariantCulture);
            var scope = _mapper.Map<ScopeEntity>(userDetails.ScopeDto);
            user.Scope = scope;
            var salestypeId = await _referentialStorage.GetSalesTypeIdByNameAsync(userDetails.ScopeDto.SalesTypeHoldingName).ConfigureAwait(false);
            user.Scope.SalesTypeHoldingId = salestypeId.Value;
            var profileId = await _referentialStorage.GetProfileIdByNameAsync(userDetails.ScopeDto.ProfileName).ConfigureAwait(false);
            user.Scope.ProfileId = profileId.Value;

            if (user.Id == 0)
            {
                user.IsScopeSync = false;
                user.FirstConnection = DateTime.MinValue;
                user.LastConnection = DateTime.MinValue;
                user.CreatedOn = _dateTimeService.Now();
                user.CreatedBy = _applicationContext.GetValue(IkpConstant.JwtUserId);
                user.Scope.AdminUserId = int.Parse(_applicationContext.GetValue(IkpConstant.JwtUserId), CultureInfo.InvariantCulture);
                user.UpdatedOn = _dateTimeService.Now();
                user.UserStatusId = (int)UserStatusEnumConstant.Registered;
                user.PasswordChangeCode = Guid.NewGuid().ToString();
                await _context.Users.AddAsync(user).ConfigureAwait(false);
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }
            else
            {
                var existUser = await _context.Users.Include(u => u.Scope).
                   FirstOrDefaultAsync(x => x.Id == user.Id).ConfigureAwait(false);
                if (existUser != null)
                {
                    existUser.Scope.ProfileId = user.Scope.ProfileId;
                    existUser.Scope.RoleId = user.Scope.RoleId;
                    existUser.Scope.TerritoryId = user.Scope.TerritoryId;
                    existUser.Scope.SubsidiaryId = user.Scope.SubsidiaryId;
                    existUser.Scope.TerritoryIdBoth = user.Scope.TerritoryIdBoth;
                    existUser.Scope.SubsidiaryIdBoth = user.Scope.SubsidiaryIdBoth;
                    existUser.Scope.SalesTypeHoldingId = user.Scope.SalesTypeHoldingId;
                    existUser.Scope.CustomerLegalTypeId = user.Scope.CustomerLegalTypeId;
                    existUser.Scope.PartnerTypeHoldingId = user.Scope.PartnerTypeHoldingId;
                    existUser.Scope.PartnerGroupHoldingId = user.Scope.PartnerGroupHoldingId;
                    existUser.Scope.PartnerNameHoldingId = user.Scope.PartnerNameHoldingId;
                    existUser.Scope.PartnerMakeId = user.Scope.PartnerMakeId;
                    existUser.Scope.Procurement = user.Scope.Procurement;

                    existUser.UpdatedOn = _dateTimeService.Now();
                    existUser.UpdatedBy = existUser.CreatedBy;
                    var statusId = await _referentialStorage.GetStatusIdByNameAsync(userDetails.UserStatusName).ConfigureAwait(false);
                    existUser.UserStatusId = statusId.Value;
                    if (this._context.Entry(existUser.Scope).State == EntityState.Modified)
                    {
                        existUser.IsScopeSync = false;
                    }
                    await _context.SaveChangesAsync().ConfigureAwait(false);
                    return ReturnValue<bool>.Success(true);
                }


            }
            return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);

        }
        public async Task<ReturnValue<bool>> UpdateUserStatusAsync(ResultUserDetailsDto userDetails)
        {
            var existUser = await _context.Users.Include(u => u.Scope).
                  FirstOrDefaultAsync(x => x.Id == userDetails.Id).ConfigureAwait(false);
            if (existUser != null)
            {
                existUser.UserStatusId = (int)UserStatusEnumConstant.Active;
                existUser.UpdatedOn = _dateTimeService.Now();
                existUser.UpdatedBy = existUser.CreatedBy;
                await _context.SaveChangesAsync().ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<bool>> DeleteUserAsync(UserListContextDto userContext)
        {
            var userDetail = _mapper.Map<UserExportDto>(userContext);
            var userEntity = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(a => a.Id == userDetail.UserId).ConfigureAwait(false);
            if (userEntity == null)
            {
                return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
            }

            _context.Users.Remove(userEntity);
            await _context.SaveChangesAsync().ConfigureAwait(false);
            return ReturnValue<bool>.Success(true);

        }
        public async Task<ReturnValue<bool>> LogOutAsync(string userId)
        {
            int uId = int.Parse(userId, CultureInfo.InvariantCulture);
            var userEntity = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(a => a.Id == uId).ConfigureAwait(false);
            if (userEntity == null)
            {
                return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
            }
            userEntity.LastConnection = _dateTimeService.Now();
            userEntity.RefreshToken = null;
            userEntity.RefreshTokenExpiry = null;
            this._context.Users.Attach(userEntity);
            this._context.Entry(userEntity).Property(x => x.LastConnection).IsModified = true;
            this._context.Entry(userEntity.Scope).State = EntityState.Unchanged;
            await _context.SaveChangesAsync().ConfigureAwait(false);
            return ReturnValue<bool>.Success(true);
        }
        public async Task<ReturnValue<LoginUserDetailsDto>> GetLoginUserDetailByUserIdAsync(int UserId)
        {

            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.Id == UserId).ConfigureAwait(false);
            if (user != null)
            {
                var status = await _referentialStorage.GetStatusNameByIdAsync(user.UserStatusId).ConfigureAwait(false);
                var result = _mapper.Map<LoginUserDetailsDto>(user);
                result.UserStatusName = status.Value;
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                var procurement = await _referentialStorage.GetProcurementNameByIdAsync(user.Scope.Procurement).ConfigureAwait(false);
                result.ScopeDto.Procurement = procurement.Value;
                var Salestype = await _referentialStorage.GetSalesTypeNameByIdAsync(user.Scope.SalesTypeHoldingId).ConfigureAwait(false);
                result.ScopeDto.SalesTypeHoldingName = Salestype.Value;
                return ReturnValue<LoginUserDetailsDto>.Success(result);
            }
            return ReturnValue<LoginUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<LoginUserDetailsDto>> GetLoginUserDetailByRefreshTokenAsync(string refreshToken)
        {
            var user = await _context.Users.Include(s => s.Scope).FirstOrDefaultAsync(y => y.RefreshToken == refreshToken).ConfigureAwait(false);
            if (user != null)
            {
                var result = _mapper.Map<LoginUserDetailsDto>(user);
                result.ScopeDto = _mapper.Map<ScopeDto>(user.Scope);
                var profileName = await _referentialStorage.GetProfileNameByIdAsync(user.Scope.ProfileId).ConfigureAwait(false);
                result.ScopeDto.ProfileName = profileName.Value;
                return ReturnValue<LoginUserDetailsDto>.Success(result);
            }
            return ReturnValue<LoginUserDetailsDto>.Failure(_configuration[IkpConstant.NoUser]);
        }
        public async Task<ReturnValue<UserScopeDto>> GetUserScopeDetailsAsync(string userId)
        {
            using SqlConnection con = new(_configuration.GetConnectionString("DefaultConnection"));
            using SqlCommand sqlCommand = new("GetUserScope", con);
            sqlCommand.CommandType = CommandType.StoredProcedure;
            sqlCommand.Parameters.Add("@userId", SqlDbType.Int).Value = userId;
            await con.OpenAsync().ConfigureAwait(false);
            using SqlDataAdapter sda = new(sqlCommand);
            using DataSet ds = new();
            ds.Locale = CultureInfo.InvariantCulture;
            await Task.Run(() => sda.Fill(ds)).ConfigureAwait(false);
            await con.CloseAsync().ConfigureAwait(false);
            if (ds.Tables.Count == 0)
            {
                return ReturnValue<UserScopeDto>.Failure(IkpConstant.NoUserScopeFound);
            }
            var userScopeDto = BuildUserScope(ds);
            return ReturnValue<UserScopeDto>.Success(userScopeDto);
        }
        private static UserScopeDto BuildUserScope(DataSet ds)
        {
            var userScopeDto = BuildUserScopeDefault(ds);

            var partnertypeDt = ds.Tables[4];
            var partnerTypeList = new List<PartnerTypeHoldingDto>();

            foreach (DataRow dr in partnertypeDt.Rows)
            {
                partnerTypeList.Add(new PartnerTypeHoldingDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    PartnerTypeHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            var partnerGroupDt = ds.Tables[5];
            var partnerGroupList = new List<PartnerGroupHoldingDto>();

            foreach (DataRow dr in partnerGroupDt.Rows)
            {
                partnerGroupList.
                    Add(new PartnerGroupHoldingDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        PartnerGroupHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                        PartnerTypeName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                    });
            }

            var partnerNameDt = ds.Tables[6];
            var partnerNameList = new List<PartnerNameHoldingDto>();

            foreach (DataRow dr in partnerNameDt.Rows)
            {
                partnerNameList.Add(new PartnerNameHoldingDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    PartnerNameHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    PartnerGroupHoldingName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }
            var makeDt = ds.Tables[7];
            var makelList = new List<PartnerMakeDto>();

            foreach (DataRow dr in makeDt.Rows)
            {
                makelList.Add(new PartnerMakeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    MakeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    PartnerName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }

            var modelDt = ds.Tables[8];
            var modelList = new List<PartnerModelDto>();

            foreach (DataRow dr in modelDt.Rows)
            {
                modelList.Add(new PartnerModelDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    ModelName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    MakeName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }

            var contractyTypeDt = ds.Tables[9];
            var contractTypeList = new List<ContractTypeDto>();

            foreach (DataRow dr in contractyTypeDt.Rows)
            {
                contractTypeList.Add(new ContractTypeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    ContractTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            var vehcileBodyTypeDt = ds.Tables[10];
            var vehcileBodyTypeList = new List<BodyTypeDto>();

            foreach (DataRow dr in vehcileBodyTypeDt.Rows)
            {
                vehcileBodyTypeList.Add(new BodyTypeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    BodyTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }
            var fuelTypeDt = ds.Tables[11];
            var fuelTypeList = new List<FuelType>();

            foreach (DataRow dr in fuelTypeDt.Rows)
            {
                fuelTypeList.Add(new FuelType
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    FuelTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            userScopeDto.PartnerType = partnerTypeList;
            userScopeDto.PartnerGroup = partnerGroupList;
            userScopeDto.PartnerName = partnerNameList;
            userScopeDto.PartnerMake = makelList;
            userScopeDto.PartnerModel = modelList;
            userScopeDto.ContractType = contractTypeList;
            userScopeDto.VehicleBodyType = vehcileBodyTypeList;
            userScopeDto.FuelType = fuelTypeList;

            return userScopeDto;
        }
        private static UserScopeDto BuildUserScopeDefault(DataSet ds)
        {
            var userScopeDto = new UserScopeDto();
            var userInfoDt = ds.Tables[0];
            userScopeDto.Name = Convert.ToString(userInfoDt.Rows[0]["Name"], CultureInfo.InvariantCulture);
            userScopeDto.Email = Convert.ToString(userInfoDt.Rows[0]["Email"], CultureInfo.InvariantCulture);
            userScopeDto.Profile = Convert.ToString(userInfoDt.Rows[0]["UserProfileName"], CultureInfo.InvariantCulture);

            var salesTypeDt = ds.Tables[1];
            var salesTypesList = new List<SalesTypeHoldingDto>();
            foreach (DataRow dr in salesTypeDt.Rows)
            {
                salesTypesList.Add(
                    new SalesTypeHoldingDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        SalesTypeHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                    });
            }
            var customerLegalTypesDt = ds.Tables[2];
            var customerLegalTypeList = new List<CustomerLegalTypeDto>();

            foreach (DataRow dr in customerLegalTypesDt.Rows)
            {
                customerLegalTypeList.Add(
                    new CustomerLegalTypeDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        CustomerLegalTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                    });
            }
            var countryDt = ds.Tables[3];
            var countryList = new List<CountryDto>();

            foreach (DataRow dr in countryDt.Rows)
            {
                countryList.Add(new CountryDto
                {
                    CountryId = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    CountryName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    CountryNameOriginal = Convert.ToString(dr["subsidary"], CultureInfo.InvariantCulture)
                });
            }
            userScopeDto.SalesType = salesTypesList;
            userScopeDto.CustomerLegalType = customerLegalTypeList;
            userScopeDto.Country = countryList;
            return userScopeDto;
        }
        public async Task<ReturnValue<List<UserScopeDto>>> GetUsersScopeDetailsAsync()
        {
            var users = await _context.Users.ToListAsync().ConfigureAwait(false);
            var activeUsers = users.Where(t => t.UserStatusId == (int)UserStatusEnumConstant.Active).ToList();

            var userScopeList = new List<UserScopeDto>();
            if (activeUsers != null)
            {
                foreach (var user in activeUsers)
                {
                    var userScope = await GetUserScopeDetailsAsync(user.Id.ToString(CultureInfo.InvariantCulture)).ConfigureAwait(false);
                    userScopeList.Add(userScope.Value);
                }
                return ReturnValue<List<UserScopeDto>>.Success(userScopeList);
            }
            return ReturnValue<List<UserScopeDto>>.Failure(IkpConstant.NoUserScopeFound);
        }
    }
}







































































NEW USER SERVICE


using AutoMapper;
using ClosedXML.Excel;
using IkpReporting.Abstractions.Constant;
using IkpReporting.Abstractions.EnumConstant;
using IkpReporting.Abstractions.Extensions;
using IkpReporting.Abstractions.Interfaces;
using IkpReporting.Abstractions.Model.Models;
using IkpReporting.Abstractions.Model.User;
using IkpReporting.Application.Extensions;
using IkpReporting.Domain.Entities;
using IkpReporting.Domain.Interfaces.Repositories;
using IkpReporting.Domain.Specifications.User;
using IkpReporting.SharedUtilities.Cryptography;
using IkpReporting.SharedUtilities.DateTime;
using IkpReporting.SharedUtilities.Mail;
using Microsoft.Extensions.Configuration;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Linq.Expressions;
using System.Net.Mail;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace IkpReporting.Application.Services
{
    public class UserService : IUserService
    {
        private readonly IUserRepository _userRepository;
        private readonly IConfiguration _configuration;
        private readonly IHashService _hashService;
        private readonly IReferentialRepository _referentialRepository;
        private readonly IMapper _mapper;
        private readonly IMailService _mailService;
        private readonly IDateTimeService _dateTimeService;


        public UserService(IUserRepository userRepository, IConfiguration configuration
                , IHashService hashService, IReferentialRepository referentialRepository,
            IMapper mapper, IMailService mailService, IDateTimeService dateTimeService
            )
        {
            _userRepository = userRepository;
            _configuration = configuration;
            _hashService = hashService;
            _referentialRepository = referentialRepository;
            _mapper = mapper;
            _mailService = mailService;
            _dateTimeService = dateTimeService;
        }
        public async Task<ReturnValue<ResultUserList>> GetUsersAsync(UserExportDto userContext, CancellationToken cancellationToken)
        {
            if (userContext == null)
            {
                return ReturnValue<ResultUserList>.Failure(IkpConstant.NoUser);
            }
            var specificaiton = new UserListByUserContext(userContext.UserId, userContext.ProfileName);
            var users = await _userRepository.ListAsync(specificaiton, cancellationToken).ConfigureAwait(false);
            var userList = _mapper.Map<List<UserListDto>>(users);
            ResultUserList result = null;
            if (userList != null)
            {
                var filteredUser = FilterUserData(userList, userContext);
                result = new ResultUserList
                {
                    UserListData = filteredUser,
                    TotalUserCount = filteredUser.Count()
                };
            }
            if (result != null)
            {
                return ReturnValue<ResultUserList>.Success(result);
            }
            return ReturnValue<ResultUserList>.Failure(IkpConstant.NoUser);
        }
        private static IEnumerable<UserListDto> FilterUserData(IEnumerable<UserListDto> users, UserExportDto userContext)
        {
            if (userContext != null && userContext.UserStatusName != null && userContext.UserStatusName == UserStatusNameConstants.All)
            {
                userContext.UserStatusName = null;
            }
            IQueryable<UserListDto> query = users.AsQueryable();
            Dictionary<Func<UserExportDto, bool>, Expression<Func<UserListDto, bool>>>
            applyFilter = new()
            {
                {V => !string.IsNullOrEmpty(userContext.UserStatusName), n => n.UserStatusName == userContext.UserStatusName },
                {V => !string.IsNullOrEmpty(userContext.SalesTypeName), n => n.SalesType == userContext.SalesTypeName },
                {V => !string.IsNullOrEmpty(userContext.RoleName), n => n.Role == userContext.RoleName },
                {V => !string.IsNullOrEmpty(userContext.CustomerLegalTypeName), n => n.CustomerLegalType == userContext.CustomerLegalTypeName },
                {V => !string.IsNullOrEmpty(userContext.SelectedProfile), n => n.Profile == userContext.SelectedProfile },
                {V => !string.IsNullOrEmpty(userContext.TerritoryName), n => n.TerritoryName == userContext.TerritoryName },
                {v => !string.IsNullOrEmpty(userContext.TerritoryNameBoth), n => n.TerritoryNameBoth == userContext.TerritoryNameBoth },
                {v => !string.IsNullOrEmpty(userContext.SubsidiaryName), n => n.SubsidiaryName == userContext.SubsidiaryName },
                {v => !string.IsNullOrEmpty(userContext.SubsidiaryNameBoth), n => n.SubsidiaryNameBoth == userContext.SubsidiaryNameBoth },
                {v => !string.IsNullOrEmpty(userContext.SearchString), n =>
                n.FirstName.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase) ||
                n.LastName.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase) ||
                n.Email.Contains(userContext.SearchString, StringComparison.OrdinalIgnoreCase)
                }
            };
            query = applyFilter.Where(vn => vn.Key(userContext)).Aggregate(query, (newQuery, vn) => newQuery.Where(vn.Value));
            return [.. query];
        }
        public async Task<ReturnValue<ResultUserList>> GetAllUsersAsync(CancellationToken cancellationToken )
        {
            var sactiveDisclaimerStatusSpec = new ActiveAndDisclaimStatusUser();
            var result = await _userRepository.ListAsync(sactiveDisclaimerStatusSpec, cancellationToken).ConfigureAwait(false);
            if (!result.Any())
            {
                return ReturnValue<ResultUserList>.Failure(IkpConstant.NoUser);
            }
            var resultDto = _mapper.Map<List<UserListDto>>(result);
            var resultUserList = new ResultUserList { UserListData = resultDto };
            return ReturnValue<ResultUserList>.Success(resultUserList);
        }
        public async Task<ReturnValue<UserDto>> GetUserAsync(UserListContextDto userContext, CancellationToken cancellationToken)
        {
            if (userContext == null)
            {
                return ReturnValue<UserDto>.Failure(_configuration["ErrorConstants:UserDetailRequired"]);
            }
            var userIdSpec = new UserWithFullScopeByUserId(userContext.UserId.ToIntWithCulture());
            var userWithSpec = await _userRepository.FirstOrDefaultAsync(userIdSpec, cancellationToken).ConfigureAwait(false);
            if (userWithSpec == null)
            {
                return ReturnValue<UserDto>.Failure(IkpConstant.NoUser);
            }
            var result = _mapper.Map<UserDto>(userWithSpec);
            return ReturnValue<UserDto>.Success(result);
        }
        public async Task<ReturnValue<ResultLoginUserDto>> GetLoginUserAsync(string userId, CancellationToken cancellationToken)
        {
            var userIdSpec = new UserWithFullScopeByUserId(userId.ToIntWithCulture());
            var user = await _userRepository.FirstOrDefaultAsync(userIdSpec, cancellationToken).ConfigureAwait(false);
            if (user == null)
            {
                return ReturnValue<ResultLoginUserDto>.Failure(IkpConstant.NoUser);
            }
            return await SaveLoginInfoDetailAsync(user, cancellationToken)
                         .BindAsync(isSaved => SetPartnerNameAsync(user)).ConfigureAwait(false);
        }
        private async Task<ReturnValue<bool>> SaveLoginInfoDetailAsync(UserEntity user, CancellationToken cancellationToken)
        {
            if (user != null)
            {
                user.PasswordChangeCode = null;
                user.UpdatedOn = _dateTimeService.Now();
                user.UpdatedBy = user.CreatedBy;
                user.LastConnection = _dateTimeService.Now();
                await _userRepository.UpdateAsync(user, cancellationToken).ConfigureAwait(false);
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoChanges"]);
        }
        private async Task<ReturnValue<ResultLoginUserDto>> SetPartnerNameAsync(UserEntity user)
        {
            string partnerName = "default";

            var pghIds = user.Scope.PartnerGroupHoldingId;

            if (pghIds == null)
            {
                return ReturnValue<ResultLoginUserDto>.Failure(_configuration[IkpConstant.NoUser]);
            }
            if (pghIds.Length == 1 && pghIds != "0")
            {
                var partnerGroupHolding = await _referentialRepository.GetPartnerGroupHoldingByIdAsync(pghIds.ToIntWithCulture()).ConfigureAwait(false);
                partnerName = Regex.Replace(partnerGroupHolding.PartnerGroupHoldingName, "[^a-zA-z0-9]", "", RegexOptions.None, TimeSpan.FromSeconds(5));
            }
            var result = _mapper.Map<ResultLoginUserDto>(user);
            result.PartnerName = partnerName;
            return ReturnValue<ResultLoginUserDto>.Success(result);

        }
        public async Task<ReturnValue<bool>> ResetPasswordAsync(ResetDetails resetDetails, CancellationToken cancellationToken)
        {
            if (resetDetails == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:resetDetailRequired"]);
            }
            var passwordCodeChangeSpec = new UserByUserId(resetDetails.UserId);
            var existUser = await _userRepository.FirstOrDefaultAsync(passwordCodeChangeSpec, cancellationToken).ConfigureAwait(false);
            var IsValid = _hashService.Verify(resetDetails.CurrentPassword, existUser.Password);
            if (!IsValid.IsSuccess)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordMisMatch"]);
            }

            return await UpdatePasswordAsync(existUser, _hashService.Generate(resetDetails.NewPassword).Value, cancellationToken).ConfigureAwait(false);
        }
        public async Task<ReturnValue<bool>> SetPasswordAsync(LoginDetail loginDetail, CancellationToken cancellationToken)
        {
            if (loginDetail == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:loginRequired"]);
            }
            var passwordCodeChangeSpec = new UserByPasswordChangeCode(loginDetail.PasswordChangeCode);
            var existUser = await _userRepository.FirstOrDefaultAsync(passwordCodeChangeSpec, cancellationToken).ConfigureAwait(false);
            if (existUser == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:loginRequired"]);
            }
            return await UpdatePasswordAsync(existUser, _hashService.Generate(loginDetail.Password).Value, cancellationToken).ConfigureAwait(false);
        }
        private async Task<ReturnValue<bool>> UpdatePasswordAsync(UserEntity user, string password, CancellationToken cancellationToken)
        {
            user.Password = password;
            user.PasswordChangeCode = null;
            user.PasswordChangeCodeExpiry = null;
            user.UpdatedOn = _dateTimeService.Now();
            user.UpdatedBy = user.CreatedBy;
            var result = await _userRepository.UpdateAsync(user, cancellationToken).ConfigureAwait(false);
            if (!result)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordUpdateFailed"]);
            }
            return ReturnValue<bool>.Success(result);
        }
        public async Task<ReturnValue<bool>> SendForgetNotificationLinkAsync(string email, CancellationToken cancellationToken)
        {
            var emailIdSepc = new UserAndScopeByEmailId(email);
            var existUser = await _userRepository.FirstOrDefaultAsync(emailIdSepc, cancellationToken).ConfigureAwait(false);
            if (existUser == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoUse"]);
            }
            existUser.PasswordChangeCode = Guid.NewGuid().ToString();
            double hours = Double.Parse(_configuration["MailDetail:ExpiryTime"], CultureInfo.CurrentCulture);
            existUser.PasswordChangeCodeExpiry = _dateTimeService.Now().AddHours(hours);
            existUser.UpdatedOn = _dateTimeService.Now();
            existUser.UpdatedBy = existUser.CreatedBy;
            var result = await _userRepository.UpdateAsync(existUser, cancellationToken).ConfigureAwait(false);

            if (!result)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeUpdateFailed"]);
            }
            var mailMessageContext = new MailMessageContext
            {
                SmtpServer = _configuration["MailDetail:Server"],
                SmtpServerPort = int.Parse(_configuration["MailDetail:Port"], CultureInfo.InvariantCulture),
                IsSslEnabled = bool.Parse(_configuration["CommonSettings:IsSslEnabled"]),
                BodyEncodingFormat = Encoding.UTF8,
                IsBodyHtml = true,
                Subject = _configuration["MailDetail:ResetPasswordSubject"],
                From = _configuration["MailDetail:From"],
                Body = ResetPasswordMailContent(existUser),
                To = new[] { existUser.Email }
            };
            await _mailService.SendMailAsync(mailMessageContext).ConfigureAwait(false);
            return ReturnValue<bool>.Success(true);
        }
        public async Task<ReturnValue<bool>> IsPasswordChangeCodeValidAsync(string passwordChangeCode, CancellationToken cancellationToken)
        {
            if (string.IsNullOrEmpty(passwordChangeCode))
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeInvalid"]);
            }
            var passwordCodeChangeSpec = new UserByPasswordChangeCode(passwordChangeCode);
            var existUser = await _userRepository.FirstOrDefaultAsync(passwordCodeChangeSpec, cancellationToken).ConfigureAwait(false);

            if (existUser == null)
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoUser"]);
            }
            if (existUser.PasswordChangeCodeExpiry != null && existUser.PasswordChangeCodeExpiry < _dateTimeService.Now())
            {
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:passwordChangeCodeExpired"]);
            }

            return ReturnValue<bool>.Success(false);
        }
        public async Task<ReturnValue<bool>> CreateUserAsync(UserDto userDetails, CancellationToken cancellationToken)
        {
            if (userDetails != null)
            {
                var IsUserExists = await IsUserEmailExistsAsync(userDetails.Email, cancellationToken).ConfigureAwait(false);
                if (!IsUserExists.Value)
                {
                    var result = await _userRepository.SaveUserAsync(userDetails, cancellationToken).ConfigureAwait(false);
                    if (!result)
                    {
                        return ReturnValue<bool>.Failure(_configuration["ErrorConstants:NoChanges"]);
                    }
                    return ReturnValue<bool>.Success(result);
                }
                return ReturnValue<bool>.Failure(_configuration["ErrorConstants:EmailExists"]);
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:UserDetailRequired"]);
        }
        public async Task<ReturnValue<bool>> SendUsersListAsync(UserExportDto userContext, CancellationToken cancellationToken)
        {
            var userDetails = await GetUsersAsync(userContext, cancellationToken).ConfigureAwait(false);
            if (userDetails.IsSuccess)
            {
                const string fileName = "ExportAllIKPUsers.xlsx";
                using (var workbook = new XLWorkbook())
                {
                    var worksheet = workbook.Worksheets.Add("IKP Users");
                    var currentRow = 1;
                    worksheet.Cell(currentRow, (int)CellNumber.One).Value = "FirstName";
                    worksheet.Cell(currentRow, (int)CellNumber.Two).Value = "LastName";
                    worksheet.Cell(currentRow, (int)CellNumber.Three).Value = "Email";
                    worksheet.Cell(currentRow, (int)CellNumber.Four).Value = "Profile";
                    worksheet.Cell(currentRow, (int)CellNumber.Five).Value = "Sales Type";
                    worksheet.Cell(currentRow, (int)CellNumber.Six).Value = "Customer Legal Type";
                    worksheet.Cell(currentRow, (int)CellNumber.Seven).Value = "Role";
                    worksheet.Cell(currentRow, IkpConstant.ExportUserStatusColumn).Value = "Status";
                    worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameBothColumn).Value = "Direct Subsidiary";
                    worksheet.Cell(currentRow, IkpConstant.ExportUserTerritoryNameBothColumn).Value = "Direct Territory";
                    worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameColumn).Value = "Indirect Subsidiary";
                    worksheet.Cell(currentRow, IkpConstant.ExportTerritoryNameColumn).Value = "Indirect Territory";
                    worksheet.Rows(currentRow, (int)CellNumber.One).Style.Font.FontColor = XLColor.White;
                    worksheet.Rows(currentRow, (int)CellNumber.One).Style.Fill.BackgroundColor = XLColor.Blue;
                    worksheet.Columns().Width = IkpConstant.ExportColumnWidth;
                    worksheet.Columns().Style.Alignment.Horizontal = XLAlignmentHorizontalValues.Left;
                    foreach (var usr in userDetails.Value.UserListData)
                    {
                        currentRow++;
                        worksheet.Cell(currentRow, (int)CellNumber.One).Value = usr.FirstName;
                        worksheet.Cell(currentRow, (int)CellNumber.Two).Value = usr.LastName;
                        worksheet.Cell(currentRow, (int)CellNumber.Three).Value = usr.Email;
                        worksheet.Cell(currentRow, (int)CellNumber.Four).Value = usr.Profile;
                        worksheet.Cell(currentRow, (int)CellNumber.Five).Value = usr.SalesType;
                        worksheet.Cell(currentRow, (int)CellNumber.Six).Value = usr.CustomerLegalType;
                        worksheet.Cell(currentRow, (int)CellNumber.Seven).Value = usr.Role;
                        worksheet.Cell(currentRow, IkpConstant.ExportUserStatusColumn).Value = usr.UserStatusName;
                        if (usr.SalesType == IkpConstant.SalesTypeDirect)
                        {
                            usr.SubsidiaryNameBoth = usr.SubsidiaryName;
                            usr.SubsidiaryName = null;
                            usr.TerritoryNameBoth = usr.TerritoryName;
                            usr.TerritoryName = null;
                        }
                        worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameBothColumn).Value = usr.SubsidiaryNameBoth;
                        worksheet.Cell(currentRow, IkpConstant.ExportUserTerritoryNameBothColumn).Value = usr.TerritoryNameBoth;
                        worksheet.Cell(currentRow, IkpConstant.ExportSubsidiaryNameColumn).Value = usr.SubsidiaryName;
                        worksheet.Cell(currentRow, IkpConstant.ExportTerritoryNameColumn).Value = usr.TerritoryName;
                    }

                    using MemoryStream stream = new();
                    workbook.SaveAs(stream);
                    byte[] byteArr = stream.ToArray();
                    MemoryStream streamFromByte = new(byteArr, true);
                    await streamFromByte.WriteAsync(byteArr).ConfigureAwait(false);
                    streamFromByte.Position = 0;
                    var IsSslEnabled = bool.Parse(_configuration["CommonSettings:IsSslEnabled"]);
                    using SmtpClient client = new(_configuration["MailDetail:Server"],
                        int.Parse(_configuration["MailDetail:Port"], CultureInfo.InvariantCulture))
                    {
                        EnableSsl = true
                    };
                    client.EnableSsl = IsSslEnabled;
                    using MailMessage msg = new();
                    msg.Subject = string.Concat("IKP", _configuration["MailDetail:SubjectEnvironmentName"], " User Lists");
                    msg.Body = ExportMailContent();
                    msg.To.Add(_configuration["MailDetail:To"]);
                    msg.From = new MailAddress(_configuration["MailDetail:From"]);
                    msg.BodyEncoding = Encoding.UTF8;
                    msg.IsBodyHtml = true;
                    msg.Attachments.Add(new Attachment(streamFromByte, fileName));
                    client.Send(msg);
                }
                return ReturnValue<bool>.Success(true);
            }
            return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);

        }
        private string ResetPasswordMailContent(UserEntity user)
        {
            var mailContent = _configuration["MailDetail:MailTemplate"];
            var url = _configuration["MailDetail:ChangeLink"] + IkpConstant.ChangePasswordUrlLink + user.PasswordChangeCode;
            var resetBody = _configuration["MailDetail:ResetBody"];
            var restPassword = _configuration["MailDetail:RestPassword"];
            var result = String.Format(CultureInfo.InvariantCulture, mailContent, user.FirstName, user.LastName, resetBody, url, restPassword, CultureInfo.InvariantCulture);
            return result;
        }
        private string ExportMailContent()
        {
            var exportMailContent = _configuration["MailDetail:ExportMailConent"];
            var result = String.Format(CultureInfo.InvariantCulture, exportMailContent, _configuration["MailDetail:TeamName"], CultureInfo.InvariantCulture);
            return result;
        }
        public async Task<ReturnValue<bool>> UpdateUserDisclaimerStatusAsync(string userId, CancellationToken cancellationToken)
        {
            if (userId != null)
            {
                var userSpec = new UserByUserId(userId.ToIntWithCulture());
                var existingUser = await _userRepository.FirstOrDefaultAsync(userSpec, cancellationToken).ConfigureAwait(false);
                if (existingUser == null)
                {
                    return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
                }
                existingUser.FirstConnection = _dateTimeService.Now();
                existingUser.LastConnection = _dateTimeService.Now();
                existingUser.UpdatedOn = _dateTimeService.Now();
                existingUser.UpdatedBy = existingUser.CreatedBy;
                existingUser.UserStatusId = (int)UserStatusEnumConstant.Active;

                var isUpdate = await _userRepository.UpdateAsync(existingUser, cancellationToken).ConfigureAwait(false);
                return ReturnValue<bool>.Success(isUpdate);
            }
            return ReturnValue<bool>.Failure(_configuration["LogConstants:UserStatusUpdateFail"]);
        }
        public async Task<ReturnValue<bool>> IsUserEmailExistsAsync(string emailId, CancellationToken cancellationToken)
        {
            var emailIdSepc = new UserAndScopeByEmailId(emailId);
            var result = await _userRepository.FirstOrDefaultAsync(emailIdSepc, cancellationToken).ConfigureAwait(false);
            if (result == null)
            {
                return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
            }
            return ReturnValue<bool>.Success(true);
        }
        public async Task<ReturnValue<bool>> UpdateUserAsync(UserDto userDetails, CancellationToken cancellationToken)
        {
            var result = await _userRepository.SaveUserAsync(userDetails, cancellationToken).ConfigureAwait(false);
            if (result)
            {
                return ReturnValue<bool>.Success(result);
            }
            return ReturnValue<bool>.Failure(_configuration["ErrorConstants:UserNotSaved"]);
        }
        public async Task<ReturnValue<bool>> DeleteUserAsync(string userId, CancellationToken cancellationToken)
        {
            var userSpec = new UserByUserId(userId.ToIntWithCulture());
            var user = await _userRepository.FirstOrDefaultAsync(userSpec, cancellationToken).ConfigureAwait(false);
            if (user == null)
            {
                return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
            }
            var result = await _userRepository.DeleteAsync(user, cancellationToken).ConfigureAwait(false);
            return ReturnValue<bool>.Success(result);
        }
        public async Task<ReturnValue<bool>> LogOutAsync(string userId, CancellationToken cancellationToken)
        {
            var userSpec = new UserByUserId(userId.ToIntWithCulture());
            var userExist = await _userRepository.FirstOrDefaultAsync(userSpec, cancellationToken).ConfigureAwait(false);
            if (userExist == null)
            {
                return ReturnValue<bool>.Failure(_configuration[IkpConstant.NoUser]);
            }
            userExist.LastConnection = _dateTimeService.Now();
            userExist.RefreshToken = null;
            userExist.RefreshTokenExpiry = null;
            var resultUpdate = await _userRepository.UpdateAsync(userExist, cancellationToken).ConfigureAwait(false);
            if (!resultUpdate)
            {
                return ReturnValue<bool>.Failure(_configuration["LogConstants:UserUpdateFailed"]);
            }
            return ReturnValue<bool>.Success(true);
        }
        public async Task<ReturnValue<UserScopeDto>> GetUserScopeDetailsAsync(string userId, CancellationToken cancellationToken)
        {
            if (userId != null)
            {
                return await _userRepository.GetUserScopeDetailsAsync(userId, cancellationToken).ConfigureAwait(false);
            }
            return ReturnValue<UserScopeDto>.Failure(_configuration["LogConstants:UserStatusUpdateFail"]);
        }
        public async Task<ReturnValue<List<UserScopeDto>>> GetUsersScopeDetailsAsync(CancellationToken cancellationToken )
        {
            return await _userRepository.GetUsersScopeDetailsAsync(cancellationToken).ConfigureAwait(false);
        }
    }
}







































































NEW USER REPOSITORY 


using Ardalis.Specification;
using Ardalis.Specification.EntityFrameworkCore;
using AutoMapper;
using IkpReporting.Abstractions.Constant;
using IkpReporting.Abstractions.EnumConstant;
using IkpReporting.Abstractions.Model.Models;
using IkpReporting.Abstractions.Model.User;
using IkpReporting.Abstractions.Model.UserProperties;
using IkpReporting.Domain.Entities;
using IkpReporting.Domain.Interfaces.Repositories;
using IkpReporting.Domain.Specifications.User;
using IkpReporting.Infra.Data.Context;
using IkpReporting.SharedUtilities.ApplicationContext;
using IkpReporting.SharedUtilities.DateTime;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using System;
using System.Collections.Generic;
using System.Data;
using System.Data.SqlClient;
using System.Globalization;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace IkpReporting.Infra.Data.Repositories
{
    public class UserRepository : IUserRepository
    {
        private readonly RepositoryContext _dbContext;
        private readonly IMapper _mapper;
        private readonly IConfiguration _configuration;
        private readonly IApplicationContext _applicationContext;
        private readonly IDateTimeService _dateTimeService;
        public UserRepository(RepositoryContext context, IMapper mapper, IConfiguration configuration,
            IApplicationContext applicationContext,
            IDateTimeService dateTimeService)
        {
            _dbContext = context;
            _mapper = mapper;
            _configuration = configuration;

            _applicationContext = applicationContext;
            _dateTimeService = dateTimeService;
        }

        public async Task<bool> SaveUserAsync(UserDto userDetails, CancellationToken cancellationToken)
        {
            if (userDetails == null)
            {
                return false;
            }
            var user = _mapper.Map<UserEntity>(userDetails);

            if (user.Id == 0)
            {
                user.IsScopeSync = false;
                user.FirstConnection = DateTime.MinValue;
                user.LastConnection = DateTime.MinValue;
                user.CreatedOn = _dateTimeService.Now();
                user.CreatedBy = _applicationContext.GetValue(IkpConstant.JwtUserId);
                user.Scope.AdminUserId = int.Parse(_applicationContext.GetValue(IkpConstant.JwtUserId), CultureInfo.InvariantCulture);
                user.UpdatedOn = _dateTimeService.Now();
                user.PasswordChangeCode = Guid.NewGuid().ToString();
                var saveResult = await AddAsync(user, cancellationToken).ConfigureAwait(false);
                if (saveResult == null)
                {
                    return false;
                }
                return true;
            }
            var userIdSepc = new UserAndScopeByUserId(user.Id);
            var existUser = await FirstOrDefaultAsync(userIdSepc, cancellationToken).ConfigureAwait(false);
            if (existUser != null)
            {
                //User Entity Update
                existUser.TitleId = user.TitleId;
                existUser.FirstName = user.FirstName;
                existUser.LastName = user.LastName;
                existUser.UpdatedOn = _dateTimeService.Now();
                existUser.UpdatedBy = existUser.CreatedBy;
                existUser.IsScopeSync = false;
                existUser.UserStatusId = user.UserStatusId;

                //Use Scope Entity Update
                existUser.Scope.ProfileId = user.Scope.ProfileId;
                existUser.Scope.RoleId = user.Scope.RoleId;
                existUser.Scope.TerritoryId = user.Scope.TerritoryId;
                existUser.Scope.SubsidiaryId = user.Scope.SubsidiaryId;
                existUser.Scope.TerritoryIdBoth = user.Scope.TerritoryIdBoth;
                existUser.Scope.SubsidiaryIdBoth = user.Scope.SubsidiaryIdBoth;
                existUser.Scope.SalesTypeHoldingId = user.Scope.SalesTypeHoldingId;
                existUser.Scope.CustomerLegalTypeId = user.Scope.CustomerLegalTypeId;
                existUser.Scope.PartnerTypeHoldingId = user.Scope.PartnerTypeHoldingId;
                existUser.Scope.PartnerGroupHoldingId = user.Scope.PartnerGroupHoldingId;
                existUser.Scope.PartnerNameHoldingId = user.Scope.PartnerNameHoldingId;
                existUser.Scope.PartnerMakeId = user.Scope.PartnerMakeId;
                existUser.Scope.Procurement = user.Scope.Procurement;
                var updateResult = await UpdateAsync(existUser, cancellationToken).ConfigureAwait(false);
                return updateResult;
            }
            return false;
        }
        // user repository specific methods 
        public async Task<ReturnValue<UserScopeDto>> GetUserScopeDetailsAsync(string userId, CancellationToken cancellationToken)
        {
            using SqlConnection con = new(_configuration.GetConnectionString("DefaultConnection"));
            using SqlCommand sqlCommand = new("GetUserScope", con);
            sqlCommand.CommandType = CommandType.StoredProcedure;
            sqlCommand.Parameters.Add("@userId", SqlDbType.Int).Value = userId;
            await con.OpenAsync(cancellationToken).ConfigureAwait(false);
            using SqlDataAdapter sda = new(sqlCommand);
            using DataSet ds = new();
            ds.Locale = CultureInfo.InvariantCulture;
            await Task.Run(() => sda.Fill(ds)).ConfigureAwait(false);
            await con.CloseAsync().ConfigureAwait(false);
            if (ds.Tables.Count == 0)
            {
                return ReturnValue<UserScopeDto>.Failure(IkpConstant.NoUserScopeFound);
            }
            var userScopeDto = BuildUserScope(ds);
            return ReturnValue<UserScopeDto>.Success(userScopeDto);
        }
        private static UserScopeDto BuildUserScope(DataSet ds)
        {
            var userScopeDto = BuildUserScopeDefault(ds);

            var partnertypeDt = ds.Tables[4];
            var partnerTypeList = new List<PartnerTypeHoldingDto>();

            foreach (DataRow dr in partnertypeDt.Rows)
            {
                partnerTypeList.Add(new PartnerTypeHoldingDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    PartnerTypeHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            var partnerGroupDt = ds.Tables[5];
            var partnerGroupList = new List<PartnerGroupHoldingDto>();

            foreach (DataRow dr in partnerGroupDt.Rows)
            {
                partnerGroupList.
                    Add(new PartnerGroupHoldingDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        PartnerGroupHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                        PartnerTypeName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                    });
            }

            var partnerNameDt = ds.Tables[6];
            var partnerNameList = new List<PartnerNameHoldingDto>();

            foreach (DataRow dr in partnerNameDt.Rows)
            {
                partnerNameList.Add(new PartnerNameHoldingDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    PartnerNameHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    PartnerGroupHoldingName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }
            var makeDt = ds.Tables[7];
            var makelList = new List<PartnerMakeDto>();

            foreach (DataRow dr in makeDt.Rows)
            {
                makelList.Add(new PartnerMakeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    MakeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    PartnerName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }

            var modelDt = ds.Tables[8];
            var modelList = new List<PartnerModelDto>();

            foreach (DataRow dr in modelDt.Rows)
            {
                modelList.Add(new PartnerModelDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    ModelName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    MakeName = Convert.ToString(dr[IkpConstant.ScopeParentColumnName], CultureInfo.InvariantCulture)
                });
            }

            var contractyTypeDt = ds.Tables[9];
            var contractTypeList = new List<ContractTypeDto>();

            foreach (DataRow dr in contractyTypeDt.Rows)
            {
                contractTypeList.Add(new ContractTypeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    ContractTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            var vehcileBodyTypeDt = ds.Tables[10];
            var vehcileBodyTypeList = new List<BodyTypeDto>();

            foreach (DataRow dr in vehcileBodyTypeDt.Rows)
            {
                vehcileBodyTypeList.Add(new BodyTypeDto
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    BodyTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }
            var fuelTypeDt = ds.Tables[11];
            var fuelTypeList = new List<FuelType>();

            foreach (DataRow dr in fuelTypeDt.Rows)
            {
                fuelTypeList.Add(new FuelType
                {
                    Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    FuelTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                });
            }

            userScopeDto.PartnerType = partnerTypeList;
            userScopeDto.PartnerGroup = partnerGroupList;
            userScopeDto.PartnerName = partnerNameList;
            userScopeDto.PartnerMake = makelList;
            userScopeDto.PartnerModel = modelList;
            userScopeDto.ContractType = contractTypeList;
            userScopeDto.VehicleBodyType = vehcileBodyTypeList;
            userScopeDto.FuelType = fuelTypeList;

            return userScopeDto;
        }
        private static UserScopeDto BuildUserScopeDefault(DataSet ds)
        {
            var userScopeDto = new UserScopeDto();
            var userInfoDt = ds.Tables[0];
            userScopeDto.Name = Convert.ToString(userInfoDt.Rows[0]["Name"], CultureInfo.InvariantCulture);
            userScopeDto.Email = Convert.ToString(userInfoDt.Rows[0]["Email"], CultureInfo.InvariantCulture);
            userScopeDto.Profile = Convert.ToString(userInfoDt.Rows[0]["UserProfileName"], CultureInfo.InvariantCulture);

            var salesTypeDt = ds.Tables[1];
            var salesTypesList = new List<SalesTypeHoldingDto>();
            foreach (DataRow dr in salesTypeDt.Rows)
            {
                salesTypesList.Add(
                    new SalesTypeHoldingDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        SalesTypeHoldingName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                    });
            }
            var customerLegalTypesDt = ds.Tables[2];
            var customerLegalTypeList = new List<CustomerLegalTypeDto>();

            foreach (DataRow dr in customerLegalTypesDt.Rows)
            {
                customerLegalTypeList.Add(
                    new CustomerLegalTypeDto
                    {
                        Id = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                        CustomerLegalTypeName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture)
                    });
            }
            var countryDt = ds.Tables[3];
            var countryList = new List<CountryDto>();

            foreach (DataRow dr in countryDt.Rows)
            {
                countryList.Add(new CountryDto
                {
                    CountryId = Convert.ToString(dr[IkpConstant.ScopeColumnId], CultureInfo.InvariantCulture),
                    CountryName = Convert.ToString(dr[IkpConstant.ScopeColumnName], CultureInfo.InvariantCulture),
                    CountryNameOriginal = Convert.ToString(dr["subsidary"], CultureInfo.InvariantCulture)
                });
            }
            userScopeDto.SalesType = salesTypesList;
            userScopeDto.CustomerLegalType = customerLegalTypeList;
            userScopeDto.Country = countryList;
            return userScopeDto;
        }
        public async Task<ReturnValue<List<UserScopeDto>>> GetUsersScopeDetailsAsync(CancellationToken cancellationToken )
        {
            var userStatus = new UsersByStatus(UserStatusEnumConstant.Active);
            var activeUsers = await ListAsync(userStatus, cancellationToken).ConfigureAwait(false);
            var userScopeList = new List<UserScopeDto>();
            if (activeUsers != null)
            {
                foreach (var user in activeUsers)
                {
                    var userScope = await GetUserScopeDetailsAsync(user.Id.ToString(CultureInfo.InvariantCulture), cancellationToken).ConfigureAwait(false);
                    userScopeList.Add(userScope.Value);
                }
                return ReturnValue<List<UserScopeDto>>.Success(userScopeList);
            }
            return ReturnValue<List<UserScopeDto>>.Failure(IkpConstant.NoUserScopeFound);
        }
        //from IRepository
        public async Task<UserEntity> AddAsync(UserEntity entity, CancellationToken cancellationToken)
        {
            _dbContext.Set<UserEntity>().Add(entity);

            await SaveChangesAsync(cancellationToken).ConfigureAwait(false);

            return entity;
        }
        public async Task<bool> UpdateAsync(UserEntity entity, CancellationToken cancellationToken)
        {
            _dbContext.Set<UserEntity>().Update(entity);

            return await SaveChangesAsync(cancellationToken).ConfigureAwait(false) != 0;
        }
        public async Task<bool> DeleteAsync(UserEntity entity, CancellationToken cancellationToken)
        {
            _dbContext.Set<UserEntity>().Remove(entity);

            return await SaveChangesAsync(cancellationToken).ConfigureAwait(false) != 0;
        }
        public async Task<int> SaveChangesAsync(CancellationToken cancellationToken )
        {
            return await _dbContext.SaveChangesAsync(cancellationToken).ConfigureAwait(false);
        }
        public async Task<IEnumerable<UserEntity>> ListAsync(CancellationToken cancellationToken )
        {
            return await _dbContext.Set<UserEntity>().ToListAsync(cancellationToken).ConfigureAwait(false);
        }
        public async Task<IEnumerable<UserEntity>> ListAsync(ISpecification<UserEntity> specification, CancellationToken cancellationToken)
        {
            if (specification == null) 
            {
                return [];
            }
            var queryResult = await ApplySpecification(specification).ToListAsync(cancellationToken).ConfigureAwait(false);

            return specification.PostProcessingAction == null ? queryResult : specification.PostProcessingAction(queryResult).ToList();
        }
        public async Task<UserEntity> FirstOrDefaultAsync(ISpecification<UserEntity> specification, CancellationToken cancellationToken)
        {
            return await ApplySpecification(specification).FirstOrDefaultAsync(cancellationToken).ConfigureAwait(false);
        }
        protected virtual IQueryable<UserEntity> ApplySpecification(ISpecification<UserEntity> specification)
        {
            return SpecificationEvaluator.Default.GetQuery(_dbContext.Set<UserEntity>(), specification);
        }

    }
}
