package eu.europeana.cloud.service.mcs.rest.exceptionmappers;

import eu.europeana.cloud.service.mcs.exception.CannotPersistEmptyRepresentationException;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


@Provider
public class CannotPersistEmptyRepresentationExceptionMapper extends UnitedExceptionMapper
        implements ExceptionMapper<CannotPersistEmptyRepresentationException> {
}