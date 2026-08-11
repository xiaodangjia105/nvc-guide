package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NvcPracticeSessionValidatorTest {

    private NvcPracticeSessionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NvcPracticeSessionValidator();
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, IN_PROGRESS",
        "CREATED, COMPLETED",
        "IN_PROGRESS, PAUSED",
        "IN_PROGRESS, COMPLETED",
        "PAUSED, IN_PROGRESS",
        "PAUSED, COMPLETED",
        "COMPLETED, EVALUATED"
    })
    void validTransitions_shouldNotThrow(NvcSessionPhase from, NvcSessionPhase to) {
        assertDoesNotThrow(() -> validator.validatePhaseTransition(from, to, 1L));
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, PAUSED",
        "CREATED, EVALUATED",
        "IN_PROGRESS, CREATED",
        "PAUSED, CREATED",
        "COMPLETED, IN_PROGRESS",
        "EVALUATED, COMPLETED"
    })
    void invalidTransitions_shouldThrow(NvcSessionPhase from, NvcSessionPhase to) {
        assertThrows(BusinessException.class,
            () -> validator.validatePhaseTransition(from, to, 1L));
    }
}
