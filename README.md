The LINQ expression 'DbSet<ScopeEntity>()
    .Where(s => EF.Property<int?>(StructuralTypeShaperExpression: 
        IkpReporting.Domain.Entities.Role
        ValueBufferExpression: 
            ProjectionBindingExpression: EmptyProjectionMember
        IsNullable: False
    , "Id") != null && object.Equals(
        objA: (object)EF.Property<int?>(StructuralTypeShaperExpression: 
            IkpReporting.Domain.Entities.Role
            ValueBufferExpression: 
                ProjectionBindingExpression: EmptyProjectionMember
            IsNullable: False
        , "Id"), 
        objB: (object)EF.Property<int?>(s, "RoleId")))
    .Any(s => s.UserId.ToString(__CurrentCulture_0) == __userId_1)' could not be translated. Additional information: Translation of method 'int.ToString' failed. If this method can be mapped to your custom function, see https://go.microsoft.com/fwlink/?linkid=2132413 for more information. Either rewrite the query in a form that can be translated, or switch to client evaluation explicitly by inserting a call to 'AsEnumerable', 'AsAsyncEnumerable', 'ToList', or 'ToListAsync'. See https://go.microsoft.com/fwlink/?linkid=2101038 for more information.








    
namespace IkpReporting.Domain.Specifications.Roles
{
    public class RoleInScopeById : Specification<Role>
    {
        public RoleInScopeById(string userId)
        {
              Query
                .Where(u => u.Scopes.Any(sc => sc.UserId.ToString(CultureInfo.CurrentCulture) == userId));

        }
    }
}
