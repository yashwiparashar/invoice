 public IEnumerable<string> GetPartnershipIds(string country, string parameterKey, string parameterValue)
 {
     _logger.Debug($"GetPartnershipIds method called with country: {country}, parameterKey: {parameterKey}, parameterValue: {parameterValue}");
     IEnumerable<string> result = Enumerable.Empty<string>();

     try
     {
         using (var db = new PartnershipQuoterModel())
         {
             result = db.Parameters
                         .AsNoTracking()
                         .Where(p => p.Country == country && (p.ParameterKey == null || p.ParameterKey == parameterKey )&& (p.ParameterValue == null || p.ParameterValue == parameterValue))
                         .Select(p => p.PartnershipId)
                         .ToList();
         }
     }
     catch (Exception ex)
     {
         _logger.Error($"GetPartnershipIds exception - Stack trace {ex.StackTrace}, message {ex.Message}");
         throw;
     }

     return result;
 }
