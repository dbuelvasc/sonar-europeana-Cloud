package eu.europeana.cloud.service.mcs.persistent;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import eu.europeana.cloud.service.mcs.Storage;
import eu.europeana.cloud.service.mcs.exception.FileNotExistsException;
import eu.europeana.cloud.service.mcs.persistent.exception.ContentDaoNotFoundException;
import eu.europeana.cloud.service.mcs.persistent.swift.ContentDAO;
import eu.europeana.cloud.service.mcs.persistent.swift.SwiftContentDAO;
import java.util.Map;
import org.junit.Test;

/**
 * @author krystian.
 */
public class DynamicContentProxyTest {

  @Test(expected = ContentDaoNotFoundException.class)
  public void shouldThrowExceptionOnNonExistingDAO() throws FileNotExistsException {
    //given
    final DynamicContentProxy instance = new DynamicContentProxy(prepareDAOMap(
        mock(SwiftContentDAO.class)
    ));

    //then
    instance.deleteContent("exampleFileName", Storage.DATA_BASE);
  }

  @Test
  public void shouldProperlySelectDataBaseDeleteContent() throws FileNotExistsException {
    //given
    SwiftContentDAO daoMock = mock(SwiftContentDAO.class);
    final DynamicContentProxy instance = new DynamicContentProxy(prepareDAOMap(daoMock));

    //when
    instance.deleteContent("exampleFileName", Storage.OBJECT_STORAGE);

    //then
    verify(daoMock).deleteContent(anyString());

  }

  private Map<Storage, ContentDAO> prepareDAOMap(final ContentDAO dao) {
    return ImmutableMap.of(
        Storage.OBJECT_STORAGE, dao
    );
  }
}