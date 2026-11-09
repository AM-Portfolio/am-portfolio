package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.document.stress.IndexSleeveDocument;
import com.am.common.amcommondata.document.stress.StressScenarioDocument;
import com.am.common.amcommondata.repository.stress.IndexSleeveRepository;
import com.am.common.amcommondata.repository.stress.StressScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StressConfigServiceTest {

    @Mock
    private StressScenarioRepository scenarioRepo;

    @Mock
    private IndexSleeveRepository sleeveRepo;

    private StressProperties properties;
    private StressConfigService service;

    @BeforeEach
    void setUp() {
        properties = new StressProperties();
        properties.setRawIndices(List.of("NIFTY", "SENSEX"));
        properties.setBroadNiftyEtfs(List.of("NIFTYBEES"));
        properties.setScenarios(Map.of(
                "TEST_CRASH", StressProperties.ScenarioConfig.builder()
                        .type(StressProperties.ScenarioType.UNIFORM)
                        .shockPct(-15.0)
                        .description("Test -15%")
                        .build()
        ));
    }

    @Test
    void initialize_upsertsMissingDocs_andLoadsFromMongo() {
        when(scenarioRepo.findByScenarioCode(anyString())).thenReturn(Optional.empty());
        when(sleeveRepo.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(scenarioRepo.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(
                StressScenarioDocument.builder()
                        .scenarioCode("TEST_CRASH")
                        .scenarioType("UNIFORM")
                        .shockPct(-15.0)
                        .description("Test -15%")
                        .active(true)
                        .build()
        ));
        when(sleeveRepo.findByActiveTrue()).thenReturn(List.of(
                IndexSleeveDocument.builder().symbol("NIFTY").sleeveType("RAW_INDEX").active(true).build(),
                IndexSleeveDocument.builder().symbol("NIFTYBEES").sleeveType("BROAD_NIFTY_ETF").active(true).build()
        ));

        service = new StressConfigService(scenarioRepo, sleeveRepo, properties);
        service.initialize();

        verify(scenarioRepo, atLeastOnce()).save(any(StressScenarioDocument.class));
        verify(sleeveRepo, atLeast(2)).save(any(IndexSleeveDocument.class));

        assertThat(service.getScenarios()).containsKey("TEST_CRASH");
        assertThat(service.isRawIndex("NIFTY")).isTrue();
        assertThat(service.isBroadNiftyEtf("NIFTYBEES")).isTrue();
        assertThat(service.isRawIndex("UNKNOWN")).isFalse();
    }

    @Test
    void initialize_withoutRepositories_loadsFromPropertiesDirectly() {
        service = new StressConfigService(null, null, properties);
        service.initialize();

        assertThat(service.getScenarios()).containsKey("TEST_CRASH");
        assertThat(service.isRawIndex("NIFTY")).isTrue();
        assertThat(service.isBroadNiftyEtf("NIFTYBEES")).isTrue();
    }

    @Test
    void reloadCache_refreshesDataFromMongo() {
        when(scenarioRepo.findByScenarioCode(anyString())).thenReturn(Optional.empty());
        when(sleeveRepo.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(scenarioRepo.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(
                StressScenarioDocument.builder()
                        .scenarioCode("DYNAMIC_SCENARIO")
                        .scenarioType("SECTOR")
                        .targetSector("Banking")
                        .shockPct(-25.0)
                        .active(true)
                        .build()
        ));
        when(sleeveRepo.findByActiveTrue()).thenReturn(List.of(
                IndexSleeveDocument.builder().symbol("CUSTOM_INDEX").sleeveType("RAW_INDEX").active(true).build()
        ));

        service = new StressConfigService(scenarioRepo, sleeveRepo, properties);
        service.initialize();

        assertThat(service.getScenarios()).containsKey("DYNAMIC_SCENARIO");
        assertThat(service.isRawIndex("CUSTOMINDEX")).isTrue();
    }
}
