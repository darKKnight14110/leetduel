package com.leetduel.problem.problem;

import com.leetduel.problem.dto.ProblemDetailDto;
import com.leetduel.problem.dto.InternalProblemDetailDto;
import com.leetduel.problem.exception.ProblemNotFoundException;
import com.leetduel.problem.signature.FunctionSignature;
import com.leetduel.problem.signature.FunctionSignatureRepository;
import com.leetduel.problem.signature.LanguageStubRepository;
import com.leetduel.problem.signature.ParameterRepository;
import com.leetduel.problem.tag.ProblemTagRepository;
import com.leetduel.problem.tag.TagRepository;
import com.leetduel.problem.testcase.TestCase;
import com.leetduel.problem.testcase.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private ProblemTagRepository problemTagRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private FunctionSignatureRepository functionSignatureRepository;
    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private LanguageStubRepository languageStubRepository;

    private ProblemService problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(problemRepository, tagRepository, problemTagRepository,
                testCaseRepository, functionSignatureRepository, parameterRepository, languageStubRepository);
    }

    @Test
    void getPublicDetail_returnsOnlySampleTestCases_evenWhenHiddenCasesExist() {
        // Arrange
        UUID problemId = UUID.randomUUID();
        Problem problem = buildProblem(problemId);
        FunctionSignature signature = buildSignature(problemId);
        when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
        when(functionSignatureRepository.findByProblemId(problemId)).thenReturn(Optional.of(signature));
        when(parameterRepository.findByFunctionSignatureIdOrderByOrdinalAsc(signature.getId()))
                .thenReturn(List.of());
        when(languageStubRepository.findByProblemId(problemId)).thenReturn(List.of());
        // Repository call here is scoped to is_sample=true - the mock only
        // ever returns the one sample case, proving the service asks for
        // exactly that filtered query rather than filtering a full list
        // in memory (where a bug could leak hidden cases).
        when(testCaseRepository.findByProblemIdAndIsSampleTrueOrderByOrdinalAsc(problemId))
                .thenReturn(List.of(buildTestCase(problemId, 0, true)));

        // Act
        ProblemDetailDto result = problemService.getPublicDetail(problemId);

        // Assert
        assertThat(result.sampleTestCases()).hasSize(1);
        assertThat(result.sampleTestCases().get(0).ordinal()).isZero();
    }

    @Test
    void getInternalDetail_returnsEveryTestCaseIncludingHidden() {
        // Arrange
        UUID problemId = UUID.randomUUID();
        Problem problem = buildProblem(problemId);
        FunctionSignature signature = buildSignature(problemId);
        when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
        when(functionSignatureRepository.findByProblemId(problemId)).thenReturn(Optional.of(signature));
        when(parameterRepository.findByFunctionSignatureIdOrderByOrdinalAsc(signature.getId()))
                .thenReturn(List.of());
        when(testCaseRepository.findByProblemIdOrderByOrdinalAsc(problemId)).thenReturn(List.of(
                buildTestCase(problemId, 0, true),
                buildTestCase(problemId, 1, false)));

        // Act
        InternalProblemDetailDto result = problemService.getInternalDetail(problemId);

        // Assert - the internal, judge-facing fetch is unfiltered.
        assertThat(result.testCases()).hasSize(2);
    }

    @Test
    void getPublicDetail_throws_whenProblemNotFound() {
        // Arrange
        UUID problemId = UUID.randomUUID();
        when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> problemService.getPublicDetail(problemId))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    private Problem buildProblem(UUID id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setSlug("two-sum");
        problem.setTitle("Two Sum");
        problem.setDescription("desc");
        problem.setDifficulty(Difficulty.EASY);
        return problem;
    }

    private FunctionSignature buildSignature(UUID problemId) {
        FunctionSignature signature = new FunctionSignature();
        signature.setId(UUID.randomUUID());
        signature.setProblemId(problemId);
        signature.setFunctionName("twoSum");
        signature.setReturnType("int[]");
        return signature;
    }

    private TestCase buildTestCase(UUID problemId, int ordinal, boolean isSample) {
        TestCase testCase = new TestCase();
        testCase.setProblemId(problemId);
        testCase.setOrdinal(ordinal);
        testCase.setInput("[[2,7,11,15],9]");
        testCase.setExpectedOutput("[0,1]");
        testCase.setSample(isSample);
        return testCase;
    }
}
