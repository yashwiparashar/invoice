SELECT 
    ur.Id,
    ur.FirstName,
    ur.LastName,
    ur.EmailId,
    ur.Comment,
    ur.CreatedOn,
    urs.Name AS UserRequestStatusName,
    urt.Name AS UserRequestTypeName,
    t.TitleDescription AS TitleName,
    ur.TitleId
FROM UserRequest ur
LEFT JOIN Title t ON ur.TitleId = t.TitleId
LEFT JOIN UserRequestType urt ON ur.UserRequestTypeId = urt.Id
LEFT JOIN UserRequestStatus urs ON ur.UserRequestStatusId = urs.Id
ORDER BY ur.CreatedOn DESC;
