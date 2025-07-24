USE [PARTNERS_IKP_REPORTING]
GO

/****** Object:  StoredProcedure [dbo].[Scopes_Split]    Script Date: 7/24/2025 11:55:01 PM ******/
SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO


CREATE PROCEDURE [dbo].[Scopes_Split]      
 @userid varchar(max)      
AS      
BEGIN      

Declare @AldAminProfileId int =(select id from Profile where ProfileName='Ayvens Admin')     
Declare @CountryTotalCount int= (select count(id) from CLD_Country where Isvisible=1)
Declare @TotalSalesTypeHoldingId varchar(5) = (SELECT Id FROM SalesTypeHolding where SalesTypeHoldingName='Total')
Declare @DirectSalesTypeHoldingId varchar(5) = (SELECT Id FROM SalesTypeHolding where SalesTypeHoldingName='Direct')
Declare @InDirectSalesTypeHoldingId varchar(5) = (SELECT Id FROM SalesTypeHolding where SalesTypeHoldingName='InDirect')
select       
ROW_NUMBER() over(order by SC.UserId) as  Id,      
SC.UserId,  
SC.profileId,  
RD.WidgetMasterId AS RoleId,      
cast (SC.SubsidiaryId as nvarchar(max)) as Subsidiary,      
cast (SC.SubsidiaryIdBoth as nvarchar(max)) as SubsidiaryBoth,      
TID.CountryId as Country,      
TIDB.CountryId AS CountryBOTH,      
cast (SC.CustomerLegalTypeId as nvarchar)as cltvalue,      
cast (SC.SalesTypeHoldingId as nvarchar) as sthvalue,      
SC.PartnerTypeHoldingId,      
SC.PartnerGroupHoldingId,      
SC.PartnerNameHoldingId,      
SC.PartnerMakeId ,
SC.Procurement
INTO #tempScope      
FROM Scopes SC      
INNER JOIN RoleDetails RD ON SC.RoleId = RD.RoleId     
LEFT OUTER JOIN TerritoryDetails TID ON SC.TerritoryId = TID.TerritoryId       
LEFT OUTER JOIN TerritoryDetails TIDB ON SC.TerritoryIdBoth = TIDB.TerritoryId      
where UserId = @userid   
declare @profileId nvarchar(max)  
 set @profileId = (select ProfileId from #tempScope)  
 declare @countryId nvarchar(max)
set @countryId = (select LEN(Country) - LEN(REPLACE(Country, ',', ''))+1 from #tempScope)
  declare @countryBoth nvarchar(max)
set @countryBoth = (select LEN(CountryBOTH) - LEN(REPLACE(CountryBOTH, ',', ''))+1 from #tempScope)
 declare @IsProcurement nvarchar(max)  
 set @IsProcurement = (select Procurement from #tempScope)  
 declare @makeId nvarchar(max)  
set @makeId = (select PartnerMakeId from #tempScope)   
 declare @var1 nvarchar(max)  
 declare @var2 nvarchar(max)  
 declare @var3 nvarchar(max)  
 declare @var6 nvarchar(max)  
 declare @var7 nvarchar(max)
set @var1 = (select sthvalue from #tempScope)  
 set @var2 = (select Country from #tempScope)  
 set @var3 = (select Subsidiary from #tempScope)
set @var6 = (select CountryBOTH from #tempScope)  
 set @var7 = (select SubsidiaryBoth from #tempScope)

if(@profileId = @AldAminProfileId) 
begin
declare @temp table(  
 partnertype varchar(max),  
 partnergroup varchar(max),  
 partnername varchar(max),  
 partnermake varchar(max))  
   
end

if(@IsProcurement=1)
begin
insert into @temp  
    exec Aldadminscopeupdate @subsidiary =  @var3, @territory = @var2 ,@subsidiaryboth =  @var7, @territoryboth = @var6 ,@salestypeholding = @var1,@procurement=@IsProcurement,@procurementmake=@makeId
 
 UPDATE #tempScope  
SET PartnerTypeHoldingId = i.partnertype ,  PartnerGroupHoldingId = i.partnergroup , PartnerNameHoldingId = i.partnername , PartnerMakeId = i.partnermake  
   
FROM (  
   select partnertype,partnergroup,partnername,partnermake from @temp   
     
    ) i 
end

update #tempScope      
set cltvalue = '2,3'      
where cltvalue = 1      
      
update #tempScope      
set sthvalue = '4,5'      
where sthvalue = @TotalSalesTypeHoldingId    

Declare @tempTable TABLE (      
UserId nvarchar(max),      
Country nvarchar(max),      
CustomerLegalType nvarchar(max),      
SalesTypeHolding nvarchar(max),      
PartnerTypeHoldingId nvarchar(max),      
PartnerGroupHoldingId nvarchar(max),      
PartnerNameHoldingId nvarchar(max),      
PartnerMakeId nvarchar(max)      
)      
      
Insert into @tempTable      
select UserId,
tid.ID  as Countryboth,      
clt.ID,sth.ID,PTH.ID,PGH.ID,ri.ID as PartnerName , pm.ID as make from #tempScope      
cross apply dbo.splitlist(CountryBOTH) tid      
cross apply dbo.splitlist(cltvalue) clt      
cross apply dbo.splitlist(sthvalue) sth      
cross apply dbo.splitlist(PartnerTypeHoldingId) PTH      
cross apply dbo.splitlist(PartnerGroupHoldingId) PGH      
cross apply dbo.splitlist(PartnerNameHoldingId) ri      
cross apply dbo.splitlist(PartnerMakeId) pm      
      
union all      
      
select UserId,
pnh.ID  as Country,      
clt.ID,sth.ID,PTH.ID,PGH.ID,ri.ID as PartnerName , pm.ID as make from #tempScope      
cross apply dbo.splitlist(Country) pnh      
cross apply dbo.splitlist(cltvalue) clt      
cross apply dbo.splitlist(sthvalue) sth      
cross apply dbo.splitlist(PartnerTypeHoldingId) PTH      
cross apply dbo.splitlist(PartnerGroupHoldingId) PGH      
cross apply dbo.splitlist(PartnerNameHoldingId) ri      
cross apply dbo.splitlist(PartnerMakeId) pm      
      
union all      
      
select UserId,
Subsidiary,      
clt.ID,sth.ID,PTH.ID,PGH.ID,ri.ID as PartnerName , pm.ID as make from #tempScope      
cross apply dbo.splitlist(cltvalue) clt      
cross apply dbo.splitlist(sthvalue) sth      
cross apply dbo.splitlist(PartnerTypeHoldingId) PTH      
cross apply dbo.splitlist(PartnerGroupHoldingId) PGH      
cross apply dbo.splitlist(PartnerNameHoldingId) ri      
cross apply dbo.splitlist(PartnerMakeId) pm      
      
union all      
      
select UserId,  
SubsidiaryBoth,      
clt.ID,sth.ID,PTH.ID,PGH.ID,ri.ID as PartnerName , pm.ID as make from #tempScope      
cross apply dbo.splitlist(cltvalue) clt      
cross apply dbo.splitlist(sthvalue) sth      
cross apply dbo.splitlist(PartnerTypeHoldingId) PTH      
cross apply dbo.splitlist(PartnerGroupHoldingId) PGH      
cross apply dbo.splitlist(PartnerNameHoldingId) ri      
cross apply dbo.splitlist(PartnerMakeId) pm      
order by UserId      
if exists(select * from dbo.ScopeSplit where UserId =@userid)     
begin
Delete from dbo.ScopeSplit     
where UserId = @userid
end
      
select       
UserId ,      
Country ,      
CustomerLegalType ,      
SalesTypeHolding ,      
PartnerTypeHoldingId ,      
PartnerGroupHoldingId ,      
PartnerNameHoldingId ,      
PartnerMakeId,      
case when SalesTypeHolding = 2 then      
  Country + CustomerLegalType + SalesTypeHolding + PartnerTypeHoldingId + PartnerGroupHoldingId + PartnerNameHoldingId       
 when SalesTypeHolding = @DirectSalesTypeHoldingId then      
  Country + CustomerLegalType + SalesTypeHolding + PartnerMakeId      
when SalesTypeHolding = 4 then      
  Country + CustomerLegalType + @InDirectSalesTypeHoldingId + PartnerTypeHoldingId + PartnerGroupHoldingId + PartnerNameHoldingId       
 when SalesTypeHolding = 5 then      
  Country + CustomerLegalType + @DirectSalesTypeHoldingId +  PartnerMakeId      
end as BRIDGEKEY  into #tempbridegekey     
from @tempTable      
      
update Users
set IsScopeSync =1 
where Id = @userid

     

select ROW_NUMBER() OVER(ORDER BY userid ASC) AS Id,* into #tempscopesp from #tempbridegekey where userid=@userid
DELETE E
    FROM #tempscopesp E
         INNER JOIN
    (
       
	 SELECT *, 
               RANK() OVER(PARTITION BY userid,BridgeKey 
               ORDER BY Id) rank
        FROM #tempscopesp 
    ) T ON E.ID = t.ID
    WHERE rank > 1;


	insert ScopeSplit
	select userid,country,CustomerLegalType,SalesTypeHolding,PartnerTypeHoldingid,PartnerGroupHoldingid,PartnerNameHoldingid,PartnerMakeid,Bridgekey
	from #tempscopesp  where country!=0

	drop table #tempScope,#tempscopesp,#tempbridegekey
END     
      
      
      
GO


