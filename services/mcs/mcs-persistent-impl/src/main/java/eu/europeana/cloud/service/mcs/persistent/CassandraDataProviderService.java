package eu.europeana.cloud.service.mcs.persistent;

import eu.europeana.cloud.common.response.ResultSlice;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eu.europeana.cloud.common.model.DataProvider;
import eu.europeana.cloud.common.model.DataProviderProperties;
import eu.europeana.cloud.service.mcs.DataProviderService;
import eu.europeana.cloud.service.mcs.exception.ProviderHasDataSetsException;
import eu.europeana.cloud.service.mcs.exception.ProviderHasRecordsException;
import eu.europeana.cloud.service.mcs.exception.ProviderNotExistsException;

/**
 * CassandraDataProviderService
 */
@Service
public class CassandraDataProviderService implements DataProviderService {

    @Autowired
    private CassandraDataProviderDAO dataProviderDAO;


    @Override
    public ResultSlice<DataProvider> getProviders(String thresholdProviderId, int limit) {
		String nextProvider = null;
        List<DataProvider> providers = dataProviderDAO.getProviders(thresholdProviderId, limit + 1);
		if (providers.size() == limit + 1) {
			nextProvider = providers.get(limit).getId();
			providers.remove(limit);
		}
		return new ResultSlice(nextProvider, providers);
    }


    @Override
    public DataProvider getProvider(String providerId)
            throws ProviderNotExistsException {
        return dataProviderDAO.getProvider(providerId);
    }


    @Override
    public DataProvider createProvider(String providerId, DataProviderProperties properties) {
        return dataProviderDAO.createOrUpdateProvider(providerId, properties);
    }


    @Override
    public void deleteProvider(String providerId)
            throws ProviderNotExistsException, ProviderHasDataSetsException, ProviderHasRecordsException {
        dataProviderDAO.deleteProvider(providerId);
    }
}