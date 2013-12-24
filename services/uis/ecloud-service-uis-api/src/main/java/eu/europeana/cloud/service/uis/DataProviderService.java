package eu.europeana.cloud.service.uis;

import eu.europeana.cloud.common.exceptions.ProviderDoesNotExistException;
import eu.europeana.cloud.common.model.DataProvider;
import eu.europeana.cloud.common.model.DataProviderProperties;
import eu.europeana.cloud.common.response.ResultSlice;
import eu.europeana.cloud.service.uis.exception.ProviderAlreadyExistsException;

/**
 * Service for data provider operations.
 * 
 */
public interface DataProviderService {

    /**
     * Returns all providers (in slices).
     * 
     * @param thresholdProviderId
     *            if null - will return first result slice. Result slices contain token for next pages, which should be
     *            provided in this parameter.
     * @param limit
     *            max number of results in one slice.
     * @return list of providers as a result slice.
     */
    ResultSlice<DataProvider> getProviders(String thresholdProviderId, int limit);


    /**
     * Returns data provider for its id.
     * 
     * @param providerId
     *            provider id.
     * @return data provider with given id.
     * @throws ProviderNotExistsException
     *             if threre is no provider with such id.
     */
    DataProvider getProvider(String providerId)
            throws ProviderDoesNotExistException;


    /**
     * Creates a new data provider.
     * 
     * @param providerId
     *            id of provider.
     * @param properties
     *            properties of provider.
     * @return created provider.
     * @throws eu.europeana.cloud.service.mcs.exception.ProviderAlreadyExistsException
     *             provider with this id already exists
     */
    DataProvider createProvider(String providerId, DataProviderProperties properties)
            throws ProviderAlreadyExistsException;


    /**
     * Updates properties for a data provider.
     * 
     * @param providerId
     *            id of provider.
     * @param properties
     *            new properties of provider.
     * @return updated provider.
     * @throws eu.europeana.cloud.common.exceptions.mcs.exception.ProviderNotExistsException
     *             if threre is no provider with such id.
     */
    DataProvider updateProvider(String providerId, DataProviderProperties properties)
            throws ProviderDoesNotExistException;


  
}