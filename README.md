COUNTRY ENTITY 


using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class Country
    {
        public int Id { get; set; }
        public string CountryCode { get; set; }
        public string CountryName { get; set; }
        public int IsVisible { get; set; }
        public ICollection<ScopeEntity> Scopes { get; }
        public ICollection<ScopeEntity> BothScopes { get; }

    }
}








CUSTOMERLEGALTYPE ENTITY :



using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class CustomerLegalType
    {
        public int Id { get; set; }
        public string CustomerLegalTypeName { get; set; }
        public ICollection<ScopeEntity> Scopes { get; }
    }
}





ScopeEntity :

using System.ComponentModel.DataAnnotations;

namespace IkpReporting.Domain.Entities

{
    public class ScopeEntity
    {
        [Key]
        public int Id { get; set; }
        public int UserId { get; set; }
        public int? AdminUserId { get; set; }
        public int ProfileId { get; set; }
        public int RoleId { get; set; }   
        public int? TerritoryId { get; set; } 
        public int? SubsidiaryId { get; set; }
        public int? TerritoryIdBoth { get; set; }  
        public int? SubsidiaryIdBoth { get; set; }
        public int CustomerLegalTypeId { get; set; }
        public bool? DisplayType { get; set; }
        public int SalesTypeHoldingId { get; set; }
        public string PartnerTypeHoldingId { get; set; }
        public string PartnerGroupHoldingId { get; set; }
        public string PartnerNameHoldingId { get; set; }
        public string PartnerMakeId { get; set; }
        public bool BudgetReport { get; set; }
        public int Procurement { get; set; }
        public UserEntity User { get; set; }
        public Profile Profile { get; set; }
        public Role Role { get; set; }
        public CustomerLegalType CustomerLegalType { get; set; }
        public SalesTypeHolding SalesType { get; set; }
        public Territory Territory { get; set; }
        public Country Subsidary { get; set; }
        public Territory TerritoryBoth { get; set; }
        public Country SubsidaryBoth { get; set; }
    }
}








SalesTypeHolding Entity




using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class SalesTypeHolding
    {
        public int Id { get; set; }
        public string SalesTypeHoldingName { get; set; }
        public ICollection<ScopeEntity> Scopes { get; }
    }
}








Territory ENTITY


using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class Territory
    {
        public int Id { get; set; }
        public string TerritoryName { get; set; }

        public string TerritoryDescription { get; set; }

        public TerritoryDetails TerritoryDetails { get; set; }

        public ICollection<ScopeEntity> Scopes { get; }
        public ICollection<ScopeEntity> BothScopes { get; }
    }
}












UserEntity:


using System;

namespace IkpReporting.Domain.Entities

{
    public class UserEntity
    {
        public int Id { get; set; }
        public string UserName { get; set; }
        public int TitleId { get; set; }
        public int UserStatusId { get; set; }
        public bool IsScopeSync { get; set; }
        public string FirstName { get; set; }
        public string LastName { get; set; }
        public string Email { get; set; }
        public string CreatedBy { get; set; }
        public string UpdatedBy { get; set; }
        public string Password { get; set; }    
        public DateTime FirstConnection { get; set; }
        public DateTime LastConnection { get; set; }
        public DateTime CreatedOn { get; set; }
        public string PasswordChangeCode { get; set; }
        public string RefreshToken { get; set; }
        public DateTime? RefreshTokenExpiry { get; set; }
        public DateTime? PasswordChangeCodeExpiry { get; set; }
        public DateTime UpdatedOn { get; set; }
        public ScopeEntity Scope { get; set; }
        public Title Title { get; set; }
        public UserStatus UserStatus { get; set; }
    }
}



Profile:

using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class Profile
    {
        public int Id { get; set; }
        public string ProfileName { get; set; }
        public ICollection<ScopeEntity> Scopes { get; }
    }
}





UserStatus:


using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class UserStatus
    {
        public int Id { get; set; }
        public string Status { get; set; }
        public string Discription { get; set; }
        public bool Visible { get; set; }
        public ICollection<UserEntity> Users { get;}
    }
}








Role:



using System.Collections.Generic;

namespace IkpReporting.Domain.Entities
{
    public class Role
    {
        public int Id { get; set; }
        public string RoleName { get; set; }
        public string RoleDescription { get; set; }
        public RoleDetails RoleDetails { get; set; }
        public ICollection<ScopeEntity> Scopes { get; }

    }
}














