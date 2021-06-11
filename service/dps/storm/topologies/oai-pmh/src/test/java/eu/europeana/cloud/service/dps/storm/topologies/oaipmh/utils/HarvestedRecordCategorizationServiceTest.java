package eu.europeana.cloud.service.dps.storm.topologies.oaipmh.utils;

import eu.europeana.cloud.service.dps.storm.utils.HarvestedRecord;
import eu.europeana.cloud.service.dps.storm.dao.HarvestedRecordsDAO;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

public class HarvestedRecordCategorizationServiceTest {

    @Test
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfNoDefinitionInDB() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        Instant recordDateStamp =
                LocalDateTime.of(1990,1,20,10,15).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,19,10,15).toInstant(ZoneOffset.UTC);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).insertHarvestedRecord(
                argThat(samePropertyValuesAs(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .latestHarvestDate(Date.from(dateOfHarvesting))
                                .build()
                )));
        assertTrue(categorizationResult.shouldBeProcessed());
    }

    @Test
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfExistingDefinitionInDBAndNewHarvestedRecord() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);

        Instant previewHarvestDate =
                LocalDateTime.of(1990,1,30,10,16).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,20,10,15).toInstant(ZoneOffset.UTC);
        Instant publishedHarvestDate =
                LocalDateTime.of(1990,1,19,10,15).toInstant(ZoneOffset.UTC);

        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(Date.from(previewHarvestDate))
                                .publishedHarvestDate(Date.from(publishedHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(dateOfHarvesting)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(
                eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any());
        assertTrue(categorizationResult.shouldBeProcessed());
    }

    @Test
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfRecordThatIsNotChangedButFallsIntoBuffer() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
        Instant previewHarvestDate =
                LocalDateTime.of(1990,1,30,10,15).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,20,10,15).toInstant(ZoneOffset.UTC);
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,18,10,16).toInstant(ZoneOffset.UTC);

        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .publishedHarvestDate(Date.from(dateOfHarvesting))
                                .previewHarvestDate(Date.from(previewHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(
                eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any()
        );
        assertTrue(categorizationResult.shouldBeProcessed());
    }

    @Test
    public void shouldCategorizeRecordAsNotReadyForProcessingInCaseOfExistingDefinitionInDBAndOldRecord() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
//        Date dateOfHarvesting = new Date();
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,10,10,15).toInstant(ZoneOffset.UTC);
        Instant previousHarvestDate =
                LocalDateTime.of(1990,1,18,10,16).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,25,10,15).toInstant(ZoneOffset.UTC);
        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(Date.from(previousHarvestDate))
                                .publishedHarvestDate(Date.from(previousHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any());
        assertTrue(categorizationResult.shouldBeDropped());
    }

    @Test
    //This could only occur when record was deleted in source, published on preview but not on publish and later recreated
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfPreviewDateIsNull() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
//        Date dateOfHarvesting = new Date();
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,10,10,15).toInstant(ZoneOffset.UTC);
        Instant publishedHarvestDate =
                LocalDateTime.of(1990,1,18,10,16).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,25,10,15).toInstant(ZoneOffset.UTC);
        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(null)
                                .publishedHarvestDate(Date.from(publishedHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any());
        assertTrue(categorizationResult.shouldBeProcessed());
    }

    @Test
    //It could only occur when source decrease would record Datestamp
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfPreviewDateIsOld() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,10,10,15).toInstant(ZoneOffset.UTC);
        Instant previewHarvestDate =
                LocalDateTime.of(1990,1,11,10,16).toInstant(ZoneOffset.UTC);
        Instant publishedHarvestDate =
                LocalDateTime.of(1990,1,18,10,16).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,25,10,15).toInstant(ZoneOffset.UTC);
        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(Date.from(previewHarvestDate))
                                .publishedHarvestDate(Date.from(publishedHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any());
        assertTrue(categorizationResult.shouldBeProcessed());
    }



    @Test
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfEqualsRecordDatestampAndPublishedHarvestDate() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,18,10,15).toInstant(ZoneOffset.UTC);
        Instant previewHarvestDate =
                LocalDateTime.of(1990,1,30,10,16).toInstant(ZoneOffset.UTC);
        Instant publishedHarvestDate =
                LocalDateTime.of(1990,1,18,10,15).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,25,10,15).toInstant(ZoneOffset.UTC);
        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(Date.from(previewHarvestDate))
                                .publishedHarvestDate(Date.from(publishedHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(
                eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any()
        );
        assertTrue(categorizationResult.shouldBeProcessed());
    }

    @Test
    public void shouldCategorizeRecordAsReadyForProcessingInCaseOfExistingDefinitionInDBAndOldRecordButFullHarvest() {
        //given
        HarvestedRecordsDAO harvestedRecordsDAO = Mockito.mock(HarvestedRecordsDAO.class);
        Instant recordDateStamp =
                LocalDateTime.of(1990,1,10,10,15).toInstant(ZoneOffset.UTC);
        Instant previousHarvestDate =
                LocalDateTime.of(1990,1,18,10,16).toInstant(ZoneOffset.UTC);
        Instant dateOfHarvesting =
                LocalDateTime.of(1990,1,25,10,15).toInstant(ZoneOffset.UTC);
        when(harvestedRecordsDAO.findRecord(anyString(), anyString())).thenReturn(
                Optional.of(
                        HarvestedRecord.builder()
                                .metisDatasetId("exampleDatasetId")
                                .recordLocalId("exampleRecordId")
                                .previewHarvestDate(Date.from(previousHarvestDate))
                                .publishedHarvestDate(Date.from(previousHarvestDate))
                                .build()
                ));
        HarvestedRecordCategorizationService harvestedRecordCategorizationService = new HarvestedRecordCategorizationService(harvestedRecordsDAO);

        //when
        CategorizationResult categorizationResult = harvestedRecordCategorizationService.categorize(
                CategorizationParameters.builder()
                        .datasetId("exampleDatasetId")
                        .recordId("exampleRecordId")
                        .recordDateStamp(recordDateStamp)
                        .currentHarvestDate(dateOfHarvesting)
                        .fullHarvest(true)
                        .build());
        //then
        verify(harvestedRecordsDAO, times(1)).findRecord(eq("exampleDatasetId"), eq("exampleRecordId"));
        verify(harvestedRecordsDAO, times(1)).updateLatestHarvestDate(eq("exampleDatasetId"),
                eq("exampleRecordId"),
                any());
        assertTrue(categorizationResult.shouldBeProcessed());
    }

}